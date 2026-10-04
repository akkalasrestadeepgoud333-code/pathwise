package app.pathwise.web.ai;

import app.pathwise.core.ai.AnswerGuard;
import app.pathwise.core.ai.Prompts;
import app.pathwise.core.ai.Prompts.Message;
import app.pathwise.core.ai.Prompts.Mode;
import app.pathwise.core.questions.Grader;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.NotAuthenticatedException;
import app.pathwise.core.security.RateLimiter;
import app.pathwise.data.Tx;
import app.pathwise.data.ai.AiDao;
import app.pathwise.data.ai.AiDao.ConversationRow;
import app.pathwise.data.ai.AiDao.MessageRow;
import app.pathwise.data.community.DoubtDao;
import app.pathwise.data.content.ContentDao;
import app.pathwise.data.learner.AttemptDao;
import app.pathwise.data.questions.QuestionDao.OptionRow;
import app.pathwise.data.questions.QuestionDao.QuestionRow;
import app.pathwise.web.content.Markdown;
import app.pathwise.web.content.SafeHtml;
import app.pathwise.web.platform.Json;
import app.pathwise.web.platform.view.TimeLabels;
import app.pathwise.web.questions.QuestionService;
import app.pathwise.web.questions.QuestionService.Response;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The tutor (blueprint §20): Pathwise Tutor, Hint, Explain my mistake and Resources. With a local model it
 * answers from Pathwise's own content (passed as data); with AI off, or if the model is unavailable or a hint
 * would leak the answer, it answers with Guided Help built from that same content. Respects assessment AI
 * policies, keeps conversations private to the student, and lets them delete them.
 */
public final class TutorService {
    private final Tx tx;
    private final AiProvider provider;
    private final QuestionService questions;
    private final Clock clock;
    private final AiDao dao = new AiDao();
    private final AttemptDao attempts = new AttemptDao();
    private final ContentDao content = new ContentDao();
    private final DoubtDao doubts = new DoubtDao();
    private final RateLimiter limiter;

    public TutorService(Tx tx, AiProvider provider, QuestionService questions, Clock clock) {
        this.tx = tx;
        this.provider = provider;
        this.questions = questions;
        this.clock = clock;
        this.limiter = new RateLimiter(20, 600_000, clock); // 20 messages per 10 minutes
    }

    public static final class TutorRuleException extends RuntimeException {
        public TutorRuleException(String message) {
            super(message);
        }
    }

    public static final class ConversationNotFoundException extends RuntimeException {
        public ConversationNotFoundException() {
            super("Not found");
        }
    }

    // ------------------------------------------------------------------ views

    public record MessageView(String role, SafeHtml html, String sourceLabel, String when) {}

    public record Chat(UUID id, String mode, String modeLabel, String title, String providerLabel, boolean aiOn,
                       List<MessageView> messages, boolean canAsk, String placeholder) {}

    public record Summary(UUID id, String modeLabel, String title, String when) {}

    public record Home(String providerLabel, boolean aiOn, List<Summary> conversations, String policyNote) {}

    public Home home(Actor actor) {
        requireUser(actor);
        Instant now = clock.instant();
        return tx.read(c -> {
            List<Summary> list = dao.list(c, actor.userId()).stream().map(r -> new Summary(r.id(), modeLabel(r.mode()), r.title(),
                    TimeLabels.relative(r.updatedAt(), now, TimeLabels.DEFAULT_ZONE))).toList();
            String policy = dao.activeAssessmentPolicy(c, actor.userId(), now).map(TutorService::policyNote).orElse(null);
            return new Home(provider.label(), provider.enabled(), list, policy);
        });
    }

    public Chat chat(Actor actor, UUID id) {
        requireUser(actor);
        Instant now = clock.instant();
        return tx.read(c -> {
            ConversationRow conv = dao.find(c, id, actor.userId()).orElseThrow(ConversationNotFoundException::new);
            List<MessageView> msgs = dao.messages(c, id).stream().map(m -> new MessageView(m.role(), Markdown.render(m.content()),
                    "assistant".equals(m.role()) ? ("ai".equals(m.source()) ? "AI tutor · " + m.model() + " · may be wrong, check with your notes"
                            : "Guided Help · from Pathwise content") : null,
                    TimeLabels.relative(m.createdAt(), now, TimeLabels.DEFAULT_ZONE))).toList();
            Mode mode = Prompts.mode(conv.mode());
            return new Chat(conv.id(), conv.mode(), modeLabel(conv.mode()), conv.title(), provider.label(), provider.enabled(), msgs,
                    true, switch (mode) {
                        case HINT -> "Ask for another hint, or say what you've tried";
                        case EXPLAIN -> "Ask a follow-up question";
                        default -> "Ask about this topic";
                    });
        });
    }

    // ------------------------------------------------------------------ starting and asking

    /** What a conversation is about: a module or topic (tutor), a practice question (hint, explain), or nothing (resource). */
    public record Ref(String moduleKey, UUID attemptId, Integer position) {}

    public UUID start(Actor actor, String modeValue, Ref ref, String firstQuestion) {
        requireUser(actor);
        Mode mode = Prompts.mode(modeValue);
        Instant now = clock.instant();
        UUID id = tx.call(c -> {
            policyCheck(c, actor.userId(), mode, now);
            String title = switch (mode) {
                case HINT, EXPLAIN -> (mode == Mode.HINT ? "Hint: " : "Explain: ") + plain(question(c, actor.userId(), ref, mode == Mode.EXPLAIN).stem());
                case TUTOR -> ref.moduleKey() != null ? "About " + content.findPublishedModule(c, ref.moduleKey())
                        .map(ContentDao.ModuleRow::title).orElseThrow(() -> new TutorRuleException("That module isn't available."))
                        : clean(firstQuestion);
                case RESOURCE -> "Resources: " + clean(firstQuestion);
            };
            Map<String, Object> ctx = new LinkedHashMap<>();
            if (ref.moduleKey() != null) {
                ctx.put("moduleKey", ref.moduleKey());
            }
            if (ref.attemptId() != null) {
                ctx.put("attemptId", ref.attemptId().toString());
                ctx.put("position", ref.position());
            }
            return dao.create(c, actor.userId(), mode.name().toLowerCase(java.util.Locale.ROOT), title, Json.write(ctx), now);
        });
        String q = firstQuestion == null || firstQuestion.isBlank()
                ? switch (mode) {
                    case HINT -> "Can I have a hint?";
                    case EXPLAIN -> "Why was my answer wrong?";
                    case TUTOR -> "Can you explain the main idea of this module?";
                    case RESOURCE -> "What should I read?";
                }
                : firstQuestion;
        ask(actor, id, q);
        return id;
    }

    public void ask(Actor actor, UUID conversationId, String text) {
        requireUser(actor);
        String question = text == null ? "" : text.strip();
        if (question.length() < 2 || question.length() > 2000) {
            throw new TutorRuleException("Ask something between 2 and 2000 characters.");
        }
        if (!limiter.tryAcquire(actor.userId().toString())) {
            throw new TutorRuleException("That's a lot of questions in a few minutes. Take a short break, then try again.");
        }
        Instant now = clock.instant();
        // Gather everything in one read, call the model outside any transaction, then save.
        Prepared p = tx.read(c -> prepare(c, actor.userId(), conversationId, question, now));
        Reply reply = answer(p, question);
        tx.run(c -> {
            dao.addMessage(c, conversationId, "user", question, null, null, now);
            dao.addMessage(c, conversationId, "assistant", reply.text(), reply.ai() ? "ai" : "guided", reply.ai() ? reply.model() : null, clock.instant());
        });
    }

    public void delete(Actor actor, UUID id) {
        requireUser(actor);
        if (!tx.call(c -> dao.delete(c, id, actor.userId(), clock.instant()))) {
            throw new ConversationNotFoundException();
        }
    }

    // ------------------------------------------------------------------ context and answers

    private record Prepared(Mode mode, String context, List<Message> history, String guided, List<String> forbidden) {}

    private record Reply(String text, boolean ai, String model) {}

    private Prepared prepare(Connection c, UUID user, UUID conversationId, String question, Instant now) throws SQLException {
        ConversationRow conv = dao.find(c, conversationId, user).orElseThrow(ConversationNotFoundException::new);
        Mode mode = Prompts.mode(conv.mode());
        policyCheck(c, user, mode, now);
        Ref ref = ref(conv.contextJson());
        List<MessageRow> history = dao.messages(c, conversationId);
        List<Message> turns = history.stream().map(m -> new Message(m.role(), m.content())).toList();
        StringBuilder ctx = new StringBuilder();
        StringBuilder guided = new StringBuilder();
        List<String> forbidden = List.of();
        switch (mode) {
            case HINT -> {
                QuestionRow q = question(c, user, ref, false);
                List<String> hints = QuestionService.hints(q);
                ctx.append("Practice question:\n").append(q.stem()).append('\n');
                q.options().forEach(o -> ctx.append("- option: ").append(o.label()).append('\n'));
                if (!hints.isEmpty()) {
                    ctx.append("Authored hints, in order (use them as a guide):\n");
                    hints.forEach(h -> ctx.append("- ").append(h).append('\n'));
                }
                forbidden = QuestionService.answerTexts(q);
                long given = history.stream().filter(m -> "assistant".equals(m.role())).count();
                if (given < hints.size()) {
                    guided.append("**Hint ").append(given + 1).append(" of ").append(hints.size()).append(":** ").append(hints.get((int) given));
                } else if (hints.isEmpty()) {
                    guided.append("There are no written hints for this one. Try ruling out options you can prove wrong, or sketch a tiny example.");
                } else {
                    guided.append("That's every hint for this question. Try your best answer: the feedback afterwards explains each option.");
                }
            }
            case EXPLAIN -> {
                QuestionRow q = question(c, user, ref, true);
                AttemptDao.ItemRow item = item(c, user, ref);
                Response r = Response.fromJson(item.responseJson());
                ctx.append("Question:\n").append(q.stem()).append('\n');
                for (OptionRow o : q.options()) {
                    ctx.append("- ").append(o.correct() ? "[correct] " : "").append(r.options().contains(o.id()) ? "[student chose] " : "")
                            .append(o.label()).append(o.rationale() == null ? "" : " (why: " + o.rationale() + ")").append('\n');
                }
                if (!r.text().isBlank()) {
                    ctx.append("Student's answer: ").append(r.text()).append('\n');
                    ctx.append("Accepted answers: ").append(String.join(" | ", QuestionService.answerTexts(q))).append('\n');
                }
                if (q.explanation() != null) {
                    ctx.append("Explanation: ").append(q.explanation()).append('\n');
                }
                guided.append(explainGuided(q, r));
            }
            case TUTOR, RESOURCE -> {
                if (ref.moduleKey() != null) {
                    content.findPublishedModule(c, ref.moduleKey()).ifPresent(m -> {
                        ctx.append("Module: ").append(m.title()).append('\n');
                        try {
                            for (ContentDao.SectionRow s : content.sectionsOf(c, m.id())) {
                                String md = markdownOf(s.bodyJson());
                                if (!md.isBlank() && ctx.length() < 6000) {
                                    ctx.append("## ").append(s.title()).append('\n').append(md, 0, Math.min(md.length(), 1500)).append('\n');
                                }
                            }
                        } catch (SQLException e) {
                            throw new IllegalStateException(e);
                        }
                    });
                }
                String search = question + " " + (ref.moduleKey() == null ? "" : ref.moduleKey().replace('-', ' '));
                List<AiDao.ModuleHit> modules = dao.searchModules(c, orQuery(search), 3);
                List<DoubtDao.Match> kb = doubts.similar(c, search, 3);
                if (!modules.isEmpty() || !kb.isEmpty()) {
                    ctx.append("Related Pathwise material:\n");
                    modules.forEach(m -> ctx.append("- module \"").append(m.title()).append("\" (").append(m.topicTitle()).append("): ")
                            .append(m.summary() == null ? "" : m.summary()).append('\n'));
                    kb.forEach(k -> ctx.append("- ").append("kb".equals(k.kind()) ? "article" : "resolved doubt").append(" \"")
                            .append(k.title()).append("\": ").append(k.snippet()).append('\n'));
                }
                guided.append(resourcesGuided(modules, kb, mode == Mode.TUTOR && ref.moduleKey() != null));
            }
        }
        return new Prepared(mode, ctx.toString(), turns, guided.toString(), forbidden);
    }

    private Reply answer(Prepared p, String question) {
        if (provider.enabled()) {
            List<Message> messages = Prompts.build(p.mode(), p.context(), p.history(), question);
            Optional<String> first = provider.chat(messages);
            if (first.isPresent() && (p.mode() != Mode.HINT || !AnswerGuard.leaks(first.get(), p.forbidden()))) {
                return new Reply(first.get(), true, provider.label());
            }
            if (first.isPresent()) {
                // The hint gave the answer away: ask once more for a smaller hint, then fall back to the authored one.
                List<Message> retry = new ArrayList<>(messages);
                retry.add(new Message("assistant", first.get()));
                retry.add(new Message("system", "That reply revealed the answer. Give a smaller hint that does not state or imply it."));
                Optional<String> second = provider.chat(retry);
                if (second.isPresent() && !AnswerGuard.leaks(second.get(), p.forbidden())) {
                    return new Reply(second.get(), true, provider.label());
                }
            }
        }
        return new Reply(p.guided().isBlank() ? "I couldn't find Pathwise material on that. Try the doubt network: someone may know." : p.guided(),
                false, null);
    }

    private static String explainGuided(QuestionRow q, Response r) {
        StringBuilder sb = new StringBuilder();
        Grader.Result result = QuestionService.grade(q, r);
        sb.append(result.correct() ? "Your answer was right. " : "Here's what the question's authors wrote about your answer.\n\n");
        for (OptionRow o : q.options()) {
            boolean chosen = r.options().contains(o.id());
            if (chosen || o.correct()) {
                sb.append("- **").append(chosen ? (o.correct() ? "Your choice (correct)" : "Your choice") : "Correct answer").append(":** ")
                        .append(o.label()).append(o.rationale() == null ? "" : " — " + o.rationale()).append('\n');
            }
        }
        if (!r.text().isBlank()) {
            sb.append("- **You wrote:** ").append(r.text()).append('\n');
            sb.append("- **Expected:** ").append(String.join(" or ", QuestionService.answerTexts(q))).append('\n');
        }
        if (q.explanation() != null && !q.explanation().isBlank()) {
            sb.append('\n').append(q.explanation());
        }
        return sb.toString();
    }

    private static String resourcesGuided(List<AiDao.ModuleHit> modules, List<DoubtDao.Match> kb, boolean inModule) {
        if (modules.isEmpty() && kb.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(inModule ? "Here's where Pathwise covers this:\n\n" : "Pathwise has these on that:\n\n");
        modules.forEach(m -> sb.append("- [").append(m.title()).append("](/app/modules/").append(m.key()).append(") — ")
                .append(m.summary() == null ? m.topicTitle() : m.summary()).append('\n'));
        kb.forEach(k -> sb.append("- [").append(k.title()).append("](").append("kb".equals(k.kind()) ? "/app/kb/" : "/app/doubts/")
                .append(k.id()).append(") — ").append("kb".equals(k.kind()) ? "knowledge base" : "a resolved doubt").append('\n'));
        return sb.toString();
    }

    // ------------------------------------------------------------------ policy and lookups

    /** Blocked assessments stop the tutor; hints-only assessments allow Hint mode only. */
    private void policyCheck(Connection c, UUID user, Mode mode, Instant now) throws SQLException {
        Optional<String> policy = dao.activeAssessmentPolicy(c, user, now);
        if (policy.isPresent() && ("blocked".equals(policy.get()) || mode != Mode.HINT)) {
            throw new TutorRuleException(policyNote(policy.get()));
        }
    }

    static String policyNote(String policy) {
        return "blocked".equals(policy)
                ? "The tutor is off while you have an assessment in progress that doesn't allow help. It's back as soon as you submit."
                : "During your current assessment only step-by-step hints on practice questions are available.";
    }

    private AttemptDao.ItemRow item(Connection c, UUID user, Ref ref) throws SQLException {
        if (ref.attemptId() == null || ref.position() == null) {
            throw new TutorRuleException("Open the tutor from a practice question.");
        }
        AttemptDao.AttemptRow a = attempts.find(c, ref.attemptId(), user).orElseThrow(ConversationNotFoundException::new);
        // Pages number questions from 1; stored positions start at 0.
        return attempts.items(c, a.id()).stream().filter(i -> i.position() == ref.position() - 1).findFirst()
                .orElseThrow(ConversationNotFoundException::new);
    }

    private QuestionRow question(Connection c, UUID user, Ref ref, boolean mustBeAnswered) throws SQLException {
        AttemptDao.ItemRow item = item(c, user, ref);
        if (mustBeAnswered && item.answeredAt() == null) {
            throw new TutorRuleException("Answer the question first; then the tutor can explain it.");
        }
        if (!mustBeAnswered && item.answeredAt() != null) {
            throw new TutorRuleException("You've answered this one. Ask the tutor to explain it instead.");
        }
        return questions.byIds(List.of(item.questionId())).get(0);
    }

    private static Ref ref(String json) {
        try {
            JsonNode n = Json.mapper().readTree(json == null ? "{}" : json);
            return new Ref(n.hasNonNull("moduleKey") ? n.get("moduleKey").asText() : null,
                    n.hasNonNull("attemptId") ? UUID.fromString(n.get("attemptId").asText()) : null,
                    n.hasNonNull("position") ? n.get("position").asInt() : null);
        } catch (IOException e) {
            return new Ref(null, null, null);
        }
    }

    private static String markdownOf(String bodyJson) {
        try {
            return Json.mapper().readTree(bodyJson == null ? "{}" : bodyJson).path("markdown").asText("");
        } catch (IOException e) {
            return "";
        }
    }

    static String orQuery(String text) {
        StringBuilder sb = new StringBuilder();
        for (String w : text.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}\\s]", " ").trim().split("\\s+")) {
            if (w.length() >= 3) {
                sb.append(sb.isEmpty() ? "" : " or ").append(w);
            }
        }
        return sb.isEmpty() ? "zzzznomatch" : sb.toString();
    }

    private static String plain(String md) {
        String s = md.replaceAll("```[\\s\\S]*?```", " ").replaceAll("[`*_#>]", "").replaceAll("\\s+", " ").strip();
        return s.length() > 80 ? s.substring(0, 77) + "…" : s;
    }

    private static String clean(String q) {
        if (q == null || q.strip().length() < 2) {
            throw new TutorRuleException("Ask a question first.");
        }
        String s = q.strip().replaceAll("\\s+", " ");
        return s.length() > 80 ? s.substring(0, 77) + "…" : s;
    }

    static String modeLabel(String mode) {
        return switch (mode) {
            case "hint" -> "Hint";
            case "explain" -> "Explain my mistake";
            case "resource" -> "Resources";
            default -> "Pathwise Tutor";
        };
    }

    private static void requireUser(Actor actor) {
        if (actor == null || !actor.isAuthenticated()) {
            throw new NotAuthenticatedException();
        }
    }
}
