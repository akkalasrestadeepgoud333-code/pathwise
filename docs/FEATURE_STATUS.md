# Feature status

Statuses follow blueprint §44:
- **IMPLEMENTED:** merged, tested by tests that actually ran, and reachable in the UI or API.
- **PARTIALLY IMPLEMENTED:** some behaviour works; the gaps are listed.
- **PROPOSED:** designed, not built.
- **FUTURE:** intentionally out of scope for v1.

Last updated: **2026-10-04** (end of M2, Evidence → learner model → engine).

## Implemented (M0)

| Feature | Where | Evidence |
|---|---|---|
| Account sign-up (student or teacher) with server-side validation | `/register`, `POST /api/v1/auth/register` | `WebAuthFlowTest`, `ApiAuthTest` |
| Sign-in / sign-out, with sessions stored in the database (revocable, sliding expiry) | `/login`, `/logout`, `/api/v1/auth/*` | `WebAuthFlowTest`, `ApiAuthTest`, `IdentityDaoTest` |
| Argon2id password hashing (OWASP parameters), timing-equalised failures, rehash on parameter upgrade | `PasswordHasher`, `IdentityService` | `PasswordHasherTest`, `AdminAndPlatformTest` |
| Password policy (length, common-password and email checks) | `PasswordPolicy` | `PasswordPolicyTest`, `ApiAuthTest` |
| Bearer tokens for API/mobile clients (only SHA-256 hashes stored) | `/api/v1/auth/login`, `/api/v1/me` | `ApiAuthTest`, `AdminAndPlatformTest` |
| "Where you're signed in" list, sign out one or all other sessions (takes effect immediately) | `/app/settings` | `WebAuthFlowTest`, `ApiAuthTest` |
| Platform role-based access control (student/teacher/mentor/admin), default-deny | `PlatformPolicy` | `PlatformPolicyTest` (full matrix), `WebAuthFlowTest` |
| CSRF protection (synchronizer token; bearer and anonymous-API exempt) | `CsrfFilter` | `WebAuthFlowTest`, `ApiAuthTest` |
| Session fixation defence, open-redirect protection, login/sign-up rate limiting | `AuthController`, `RateLimiter` | `WebAuthFlowTest`, `SafeNextTest`, `RateLimiterTest` |
| Security headers (strict CSP, nosniff, frame-ancestors none, no-store for pages) | `SecurityHeadersFilter` | `WebAuthFlowTest` |
| XSS defences: all view output escaped; build check bans unescaped EL, scriptlets, inline scripts/styles | JSPs, `JspSafetyTest` | `JspSafetyTest`, `WebAuthFlowTest` |
| Error handling: friendly HTML pages and JSON envelope with request id; no stack traces | `ErrorServlet`, `Errors` | `WebAuthFlowTest`, `ApiAuthTest` |
| Append-only audit log (DB trigger) for registration, login, failures, session changes, admin grants | `audit_log`, `AuditDao` | `MigrationTest`, `AdminAndPlatformTest` |
| Transactional outbox with background delivery, retries and failure marking | `EventBus`, `OutboxWorker`, `OutboxDao` | `OutboxDaoTest`, `AdminAndPlatformTest` |
| Admin console: live platform counts and recent audit entries | `/admin` | `AdminAndPlatformTest` |
| CLI: `serve`, `migrate`, `create-admin`, `seed-demo`, `doctor` | `pathwise-app` | `CliTest`, plus `setup.ps1` run manually on 2026-10-03 |
| Automatic setup: generated config with random secret, embedded PostgreSQL, migrations on boot | `LocalConfig`, `EmbeddedDatabase`, `Database.migrate` | `CliTest`, `MigrationTest`, manual run |
| Design system: tokens, light/dark themes (saved per user, applied before paint), components, app shell, responsive layout with mobile tabs | `static/css/*`, `WEB-INF/tags/pw/*` | `WebAuthFlowTest` (theme), screenshots reviewed manually at 1440 and 390 px |
| Pages: landing, sign-in, sign-up, Today (empty state), Teaching (empty state), Admin, Settings, error | `WEB-INF/views/*` | `WebAuthFlowTest`, manual review |
| Module boundaries enforced by tests | `ArchitectureTest` | `ArchitectureTest` |

## Implemented (M1)

| Feature | Where | Evidence |
|---|---|---|
| Catalogue: level → program → course → subject → topic (nested) → skill; parent kinds enforced by a DB trigger | `V002` migration, `CatalogDao` | `CatalogContentDaoTest` |
| Prerequisite DAG with cycle detection (validated before every import) | `Graphs`, `PackValidator` | `GraphsAndLayoutTest`, `PackValidatorTest` |
| Content packs (versioned YAML): validated, imported in one transaction, idempotent by checksum, progress-preserving re-import | `ContentPackService`, `content-packs/` | `ContentTest`, `LearningFlowTest`, `CatalogContentDaoTest` |
| Data Structures pack: 13 topics, 51 skills, 13 modules, 87 sections; the Trees path is polished | `content-packs/data-structures` | `ContentTest` (validity + coverage) |
| Learning Map: server-computed top-down layered layout (waypoints for long edges, crossing reduction, spread ports), SVG graph + accessible trail/list view; auto graph on desktop, trail on phones | `MapLayout`, `learn/map.jsp` | `GraphsAndLayoutTest`, `LearningFlowTest`, screenshots |
| Topic page: skills, needed/helpful prerequisites, what it leads to, modules with state | `/app/topics/*` | `LearningFlowTest` |
| Module player: outline, resume, sections (text, worked example, visual, flashcards, summary, resources), "done & continue", completion banner | `/app/modules/*` | `LearningFlowTest` |
| Safe Markdown (escape + allow-list sanitiser), single audited raw-HTML tag | `Markdown`, `pw:html` | `ContentTest`, `JspSafetyTest` |
| Interactive visuals: tree, traversal (4 orders), BST insertion, array, linked list, stack/queue, BFS/DFS, hash table | `static/js/visuals` | manual run, no console errors (2026-10-03) |
| Coverage progress (labelled "Studied", explicitly not mastery); `content.module_completed` event emitted exactly once | `ProgressDao`, `ContentService` | `CatalogContentDaoTest`, `LearningFlowTest` |
| Today: continue card, or a coverage-based suggested next step with a stated reason | `LearnService.today` | `LearningFlowTest` |
| Admin › Content: pack status, validation problems, import/re-import | `/admin/content`, CLI `import-content` | `LearningFlowTest` |
| Learning JSON API for the Android app: subjects, map, topic, module, complete section | `/api/v1/subjects*`, `/api/v1/modules/*` | `LearningFlowTest` |

## Implemented (M2)

| Feature | Where | Evidence |
|---|---|---|
| Question bank in content packs: single/multi choice, numeric, short text; hint ladders; a "why" for every option; 114 questions for Data Structures | `questions/*.yaml`, `QuestionDao` | `ContentTest`, `PackValidatorTest` |
| Server-side grading (partial credit for multi-choice, tolerance for numbers, normalised text); answers never sent before answering; options shuffled per student | `Grader`, `QuestionService` | `LearnerCoreTest`, `QuestionServiceTest`, `PersonalisationFlowTest` |
| Module check sections: graded together; cannot be skipped with "done"; results with explanations | `/app/modules/*/check` | `LearningFlowTest`, `PersonalisationFlowTest` |
| Practice: adaptive selection near the learner's level, avoids repeats, hints (with reduced credit), final answers, feedback, summary with level changes | `/app/practice/*`, `PracticeService` | `PersonalisationFlowTest` |
| Learning evidence: append-only (DB trigger), never official for practice/checks, weighted by source; repeats within 24 h count less | `learning_evidence`, `LearnerService` | `PersonalisationFlowTest` |
| Learner model: per-skill estimate, confidence and state (heuristic-v1, blueprint §12); recomputed in the same transaction as new evidence; spaced review dates; rebuildable | `MasteryModel`, `skill_mastery` | `LearnerCoreTest` (golden values) |
| Recommendation engine (rules-v1): Continue / Strengthen (with prerequisite root cause) / Challenge / Explore / Revise; named weighted factors; evidence-quoting reasons; goals; suppressions | `Recommender`, `RecommendationService` | `RecommenderTest` (persona fixtures), `PersonalisationFlowTest` |
| Student controls: Start, Not now (1 day), Not relevant (topic hidden 7 days), I already know this (short check instead of blind trust) | `/app/next/*` | `PersonalisationFlowTest` |
| Today and Recommendation Center with "Why this?" panels; goals; history | `/app/today`, `/app/next` | `LearningFlowTest`, `PersonalisationFlowTest`, screenshots |
| Progress page: every skill's level, evidence count and confidence in plain language | `/app/progress` | `PersonalisationFlowTest` |
| Mastery on the Learning Map and topic pages, with per-skill Practise buttons | map, topic | `LearningFlowTest`, screenshots |
| Learner API: recommendations + actions, practice start/view/answer/hint, mastery | `/api/v1/me/*`, `/api/v1/practice*`, `/api/v1/recommendations/*` | `PersonalisationFlowTest` |

## Implemented (M3)

| Feature | Where | Evidence |
|---|---|---|
| Official assessments from content packs: diagnostic, quiz, topic test (kinds practice_test and challenge are also supported); time limit, attempt limit, item shuffling per attempt, AI policy, feedback policy | `assessments`, `assessment_items`, `pack.yaml assessments:` | `PackValidatorTest`, `ContentTest`, `AssessmentFlowTest` |
| Assessment-only questions (`practice: false`): never served by practice, and module checks cannot use them | `questions.practice_enabled`, `PackValidator` | `AssessmentFlowTest`, `PackValidatorTest` |
| Rules page, then an attempt page with all questions, autosave every 30 s, a countdown, a leave warning and confirmation for unanswered questions; works without JavaScript (Save and Submit buttons) | `/app/assessments/*`, `attempt.js` | `AssessmentFlowTest`, screenshots |
| Server-kept time: a deadline is stored at start (the earlier of the time limit and the due date). Saves are refused after deadline + 30 s grace. Overdue attempts are submitted on next open and by a 60-second background sweep | `AssessmentRules`, `AssessmentService.sweepOverdue` | `AssessmentCoreTest`, `AssessmentFlowTest` |
| Grading exactly once, in a transaction that locks the attempt; each response stores a snapshot of what was graded | `AssessmentService.grade`, `attempt_responses.question_snapshot` | `AssessmentFlowTest` |
| Official evidence (weight 1.0, `is_official` enforced by a DB check to mean source = assessment); updates the learner model and lets skills reach Mastered; the diagnostic seeds the model | `EvidenceDao.insertForResponse` | `AssessmentFlowTest` |
| Result page: score, per-skill breakdown with the new level, feedback per question (only when the feedback policy allows), the next suggestion | `/app/assessments/result/*` | `AssessmentFlowTest`, screenshots |
| Item analysis for admins: facility, upper/lower 27% discrimination (from 4 or more attempts), option counts, plain-language flags, a small-sample caution | `/admin/assessments/*`, `ItemAnalysis` | `AssessmentCoreTest`, `AssessmentFlowTest` |
| Assessment API (same rules) | `/api/v1/assessments*` | `AssessmentFlowTest` |
| Today points new students to the diagnostic, or to resume an attempt in progress | `/app/today` | `AssessmentFlowTest` |

## Implemented (M4)

| Feature | Where | Evidence |
|---|---|---|
| Game framework: rule cores in Java (authoritative), deterministic puzzle generation (SplitMix64, pinned outputs), server replay with timing checks (minimum time between actions, no more time than has passed since the start, 30-minute limit) | `pathwise-core/games` | `GamesTest` |
| Java/JS parity: the browser's rules and the server's rules run the same shared vectors; the build fails if they disagree (runs `node --test`; reported as skipped, never as passed, if Node is missing) | `game-vectors/*.json`, `static/js/games/*/rules.js` | `GamesTest`, `GameParityTest` |
| Three games: Traversal Race (generated BSTs; pre/in/post/level order), Match Pairs (two sets), Bug Hunt (10 Java snippets, 6 bug classes) | content pack `games/*.yaml` | `GameFlowTest`, real-browser play-through |
| GameShell: progress, score, clock, pause, relaxed mode (hides clock and score), keyboard-operable boards, ignores double taps that would fail the replay | `shell.js`, `*/view.js` | screenshots |
| Only verified games create evidence; rejected logs are kept with a reason and produce nothing; finishing twice counts once | `GameService.finish` | `GameFlowTest` |
| Game evidence is informal: weight 0.3, never official, at most 12 rows per skill in any 24 hours | `GameService` | `GameFlowTest` (13 plays → 12 rows) |
| Results: verified score, what the game showed per skill, current levels with "Practise this", a look back at each round/pair/snippet with explanations | `/app/games/result/*` | `GameFlowTest`, screenshots |
| Games gallery and intros, linked from Practice | `/app/games*` | `GameFlowTest` |
| Games API (start, puzzle, finish, result) for the Android WebView | `/api/v1/games*` | `GameFlowTest` |

## Implemented (M5, part 1: rooms)

| Feature | Where | Evidence |
|---|---|---|
| Rooms with an owner membership, 8-character join codes (no look-alike characters; typed in any case, with or without a dash), new code on demand | `/teach`, `rooms`, `RoomCodes` | `RoomPolicyTest`, `RoomFlowTest` |
| Joining: open (straight in), approval (teacher approves or declines), or invite-only; invite links store only a hash, expire, have a use limit, can be turned off, and are shown once | `/app/rooms`, `/join/<token>` | `RoomFlowTest` |
| Room permission matrix (owner / co-teacher with five toggles / student; archived rooms read-only), tested cell by cell; non-members get 404, students on teacher pages get 403 | `RoomPolicy` | `RoomPolicyTest`, `RoomFlowTest` (cross-room attempts) |
| Curriculum builder: units with opening dates, items (modules, games, platform assessments, the room's own assessments), required/optional, reorder with buttons (keyboard-friendly), only offered items can be added | `/teach/rooms/<id>/curriculum` | `RoomFlowTest`, screenshots |
| Student room page: announcements, open units and items with my status, my next step (first unfinished required item), class average only if enabled and 5+ students | `/app/rooms/<id>` | `RoomFlowTest` |
| Teacher-made assessments from the question bank (test-only items marked), drafts, publishing, due dates, feedback: on submit / after the due date / when released; visible only to the room's active students | `/teach/rooms/<id>/assess` | `RoomFlowTest` |
| Room cockpit: join requests, students who may need attention (missed deadline, inactive with a deadline near, 2+ room skills needing practice), deadlines with submission counts, announcements | `/teach/rooms/<id>` | `RoomFlowTest`, screenshots |
| Teacher analytics v1: class totals, room-skill distribution, assessment results (best attempt, score bands), per-student table and CSV export (individual data needs the analytics permission), practice and play shown apart from grades | `/teach/rooms/<id>/analytics` | `RoomFlowTest` |
| Rooms API for students (list, join by code, room view) | `/api/v1/rooms*` | `RoomFlowTest` |
| Individual assignments: drafts, publishing, due dates, late work allowed or refused, Markdown instructions, linked skills; students save drafts and hand in (locked until sent back); teachers grade or send back with comments; each hand-in bumps a version | `/teach/rooms/<id>/assignments`, `/app/rooms/<id>/assignments/<aid>` | `AssignmentFlowTest` |
| A grade is official evidence (weight 0.9, outcome = grade fraction) for the linked skills, enforced by the schema (official ⇔ assessment or assignment) | `AssignmentService.grade` | `AssignmentFlowTest` |
| To-grade count on the cockpit; missed and upcoming assignment deadlines in the attention list; assignment status (handed in, late, missing, graded, average) in analytics | cockpit, analytics | `AssignmentFlowTest` |
| Room context in the student's next steps: per room, the next unfinished required item and what's due within a week (on Next steps and Today). This uses curriculum order and deadlines only; it doesn't change the rule-based recommender's scoring | `RoomService.roomNextSteps` | `AssignmentFlowTest` |

## Implemented (M6: doubts, community, moderation, knowledge base)

| Feature | Where | Evidence |
|---|---|---|
| Ask your teacher (L1): a private doubt to a room's teachers, with real names; only the author and the room's teachers can open it; unanswered count on the cockpit and a Doubts tab per room | `/app/doubts/ask`, `/teach/rooms/<id>/doubts` | `DoubtRulesTest`, `DoubtFlowTest` |
| Open doubt network (L3): anonymous by default with a random per-thread alias ("Anonymous asker #4821"), or show your name; search (Postgres full-text) | `/app/doubts` | `DoubtFlowTest` |
| Anonymity by construction: views and API responses are built from types with no user-id field; a leak scan checks pages, lists and the API for the author's id, name and email | `DoubtService` view records | `DoubtFlowTest` |
| Answers, helpful votes (not your own), the asker accepts an answer (resolves the doubt and offers practice on its topic), Teacher Verified by room teachers (private) or Verified Educators (public); never on your own answer | thread page | `DoubtFlowTest` |
| Duplicate deflection: "Check for similar answers" searches resolved doubts and KB articles before posting | ask page | `DoubtFlowTest` |
| Reports (one per person per item), moderation queue (hide, remove, restore, dismiss) with a required reason, logged to `moderation_actions` and the audit log; identity reveal needs a 10+ character reason and is logged | `/admin/moderation` | `DoubtFlowTest` |
| Blocking by alias: the blocked person's doubts and answers disappear for you | thread page | `DoubtFlowTest` |
| Knowledge base: moderators promote a resolved public doubt (or write an article); search; articles link back to the doubt | `/app/kb`, `/admin/moderation/promote/<id>` | `DoubtFlowTest` |
| Rate limits: 10 new doubts an hour, 30 answers per 10 minutes per person | `DoubtService` | code review (limits are in-process) |
| Doubts API (list, view, ask, answer) | `/api/v1/doubts*` | `DoubtFlowTest` |

## Implemented (M7: XP, streaks, badges, notifications)

| Feature | Where | Evidence |
|---|---|---|
| XP from learning only: module finished 50, correct first practice answer on an unmastered skill 5 (100/day cap), verified game at 60%+ 15–30 (first 3 per game per day), assessment submitted 30, improved by 10+ points 20, skill Proficient 40 / Mastered 100, answer accepted 25 / teacher verified 40, badges 25–400 by rarity. Nothing for logging in, opening pages or asking | `XpRules`, `RewardsService` | `RewardsCoreTest`, `RewardsFlowTest` |
| Idempotent by construction: every XP change has a unique key; re-delivered events change nothing; moderation removal reverses community XP with a negative event (learning XP is never taken back) | `xp_events` | `RewardsFlowTest` |
| Levels from cumulative XP (round(100·n^1.5)), computed not stored | `Levels` | `RewardsCoreTest` |
| Streaks of meaningful learning days (check passed, 5 practice answers, game at 50%+, assessment, challenge, accepted answer); a freeze per 7 days (max 2) covers a missed day; today never breaks a streak | `Streaks`, `activity_days` | `RewardsCoreTest`, `RewardsFlowTest` |
| Badge engine with typed criteria and progress for locked badges; 11 badges (First Steps, Tree Explorer, Concept Master, Debug Detective, Practice Pro, Assessment Ace, Comeback, Helpful Peer, Verified Helper, Learning Streak I/II); awards are unique per user and badge; in-house badge art (rarity shape × category colour × icon) | `Badges`, `/app/achievements` | `RewardsCoreTest`, `RewardsFlowTest` |
| In-app notifications: assessment results, doubt answers/acceptance/verification, badges, mastered skills, assignments graded, announcements, join requests, new work to grade, private doubts; grouped while unread; bell with unread count; opening one marks it read (local links only); purged after 180 days | `/app/notifications` | `RewardsFlowTest` |
| Today shows level, XP, streak and the closest next badge | `/app/today` | `RewardsFlowTest` |

## Implemented (M8: AI tutor with Guided Help)

| Feature | Where | Evidence |
|---|---|---|
| Tutor modes: Pathwise Tutor (about a module or any question), Hint (on a practice question), Explain my mistake (after a wrong answer), Resources | `/app/tutor`, buttons on modules, practice questions and feedback | `TutorFlowTest` |
| Optional local model through Ollama (free, on this machine; `pathwise.ai.provider=ollama`, model `llama3.2:3b` by default). Off by default: the tutor then answers with **Guided Help**, built from Pathwise's own modules, KB, authored hint ladders and option rationales, and says so | `AiProvider`, `TutorService` | `AiProviderTest`, `TutorFlowTest` |
| Honest labels on every reply ("AI tutor · may be wrong" or "Guided Help · from Pathwise content"); the page badge names what is answering | tutor pages | `TutorFlowTest` |
| Prompt safety: Pathwise content is passed inside `<context>` as data and can't close the block; only the last 8 turns are sent | `Prompts` | `AiCoreTest` |
| Hint answer-leak guard: a hint that contains a correct option, numeric answer or accepted text is regenerated once, then replaced by the authored hint | `AnswerGuard` | `AiCoreTest`, `TutorFlowTest` (stub model) |
| Assessment AI policy enforced: "blocked" turns the tutor off during a running attempt; "hints only" allows Hint mode only | `TutorService` | `TutorFlowTest` |
| Conversations are private to the student (404 for others, teachers can't read them) and deletable (messages are removed) | `ai_conversations` | `TutorFlowTest` |
| Model failures (down, error, timeout) fall back to Guided Help instead of erroring; 20 messages per 10 minutes | `AiProvider`, `TutorService` | `AiProviderTest` |

## Implemented (M9: Android app)

| Feature | Where | Evidence |
|---|---|---|
| Native Android app in Java with XML layouts (framework classes only, minSdk 26, target 34), built free with `scripts\build-apk.ps1`; talks to the same server through `/api/v1` with a bearer token | `android/` | built and run on the `Callora_Test` emulator (API 34) |
| Sign in and create an account; the server address is editable, with a warning when a non-local server uses plain http; a 401 clears the token and returns to sign-in | `LoginActivity`, `Api`, `ServerUrl` | `ServerUrlTest` (2), emulator |
| Today: level, XP and streak, then up to 4 suggestions with their reasons, then the way into each area | `MainActivity`, `GET /api/v1/me/rewards` | `RewardsFlowTest`, emulator |
| Quick practice: 5 questions (single choice, multiple choice, typed), checked by the server, with feedback | `PracticeActivity` | emulator |
| Games: the website's own game JavaScript is bundled into the APK at build time and runs in a WebView that loads only the app's assets (no file, content or remote access; navigation blocked). The finished action log goes to the server, which replays and verifies it exactly as on the web | `GamesActivity`, `GameActivity`, `assets/` | emulator: Match Pairs played to 60/60, verified, 30 XP and a streak day on Today |
| Rooms: list, join by code, view a room | `RoomsActivity`, `RoomActivity` | emulator |
| Doubts: list, ask (anonymous unless the student chooses to show their name), read a thread, answer | `DoubtsActivity`, `DoubtActivity` | emulator |
| Notifications: list and mark all read; on open, one summary system notification for new unread items, only if the student allowed notifications | `NotificationsActivity`, `GET/POST /api/v1/me/notifications` | `RewardsFlowTest`, emulator |
| Notification permission (Android 13+): asked once, after an in-app explanation; "Not now" and "Don't allow" are respected and never asked again by the app | `MainActivity` | emulator: rationale → Android's own dialog → Allow |
| Light and dark themes from the same colour tokens as the web | `res/values`, `res/values-night` | emulator (light) |

## Partially implemented

| Feature | Works | Missing |
|---|---|---|
| Android app (rest of the blueprint) | see the M9 table | the Learning Map trail, modules, flashcards, daily challenge, assessments, the tutor, assignments, achievements and settings (use the website for these); offline cache and queued offline game submissions; background push (Pathwise checks only when the app opens, so no FCM, no background service); teaching tools (web only); Photo Picker for doubts; automated UI tests (verified by hand on an emulator only); a signed release build |
| Learner profile | stored per user; the theme preference is used | timezone editing, goals and modalities UI (M2) |
| "Ask" recommendation slot | not generated | the tutor and doubts exist; the recommender doesn't suggest them yet |
| Rooms (rest of the blueprint) | see the M5 table | QR codes for join links; item gates (soft/hard prerequisites); duplicating a room; transferring ownership; the "3+ failed attempts on one item" attention rule; teachers writing their own questions and modules (room assessments use the bank) |
| Room context in the recommender | rooms add their own section to Next steps and Today | the recommender doesn't yet use room curriculum order as a factor or rank only room items in a room context; group projects (P2) |
| Assessment AI policy | stored and shown to students | enforced when the AI tutor exists (M8). Pathwise cannot control outside tools, and says so |
| Assessment feedback policies | `immediate` and `manual_release` (score only until released) | the teacher release button and `after_due` (needs due dates from rooms, M5) |
| Games in recommendations | games appear in the gallery, on Practice, and in Progress through their evidence | the engine does not yet suggest a game in the Explore slot |
| Community (rest of the blueprint) | see the M6 table | room discussion (L2), live doubt sessions, attaching a question attempt to a doubt, KB links in modules' Common doubts, image uploads, a separate moderator role (moderation is an admin power for now) |
| Gamification (rest of the blueprint) | see the M7 table | Web Push / email / Android push, notification preferences and quiet hours (only in-app notifications exist, and Pathwise sends no reminders), learner time zones (days use Asia/Kolkata), room leaderboards (opt-in, P2), room custom badges, challenge-pass and doubt-to-practice XP, Root Fixer / Curious Mind / Course Milestone / Team Player badges |
| Tutor (rest of the blueprint) | see the M8 table | streaming replies, Practice Mode (AI-generated questions), Study Coach, General Assistant, room i_mode, the Android app's tutor screen; the AI path was tested against a stub server, not a real model, because Ollama isn't installed on the build machine |
| Game administration | templates can be disabled in the database (`game_templates.enabled`) | an admin screen for it (M10); the teachers' "Practice and play" panel (M5) |
| Offline game submissions | the attempt id makes online finishing idempotent | queued offline logs on Android (the app needs a connection to play) |
| SQL-query questions | — | deferred until a DBMS pack exists; the grader has no SQL type yet |
| Security tooling | headers, CSRF, rate limits, audit | OWASP Dependency-Check run (planned for M11) |

## Proposed (designed in the blueprint, not built)

more games: Sequence Sort, True/False Sprint, Query Quest (P1) · rooms, assignments and teacher analytics (M5) · projects, live doubt sessions and full admin console (M10).

## Future (out of scope for v1)

Knowledge tracing / ML models · embeddings-based retrieval · server-side code judging · iOS · audio/video sessions · LTI/xAPI.

## Deliberate deviations from the blueprint (recorded 2026-10-03)

- **Rate limiting** uses a small built-in token bucket (`pathwise-core/RateLimiter`) instead of Bucket4j. It has the same behaviour for a single process and one fewer dependency.
- **Background jobs** are scheduled in-process (`AppBootstrap`). The durable `jobs` table will be added in M5, when deadline reminders need it.
- **Theme preference** is stored on `learner_profiles.theme`.
- **View models:** JSP EL in Tomcat 10.1 cannot read Java record accessors, so controllers pass records to views as read-only maps (`ViewModels`).
- **Learning Map** uses a server-rendered SVG layout (`MapLayout`) instead of Cytoscape.js: no 400 KB library, works without JavaScript, testable.
- **Section payloads** are validated by typed Java records + `PackValidator` instead of JSON Schema files.
- **Flashcards** use native `<details>` (accessible, works without JS); self-rating evidence arrives with M2.
- **Activities table** (blueprint §10.4) is not created yet: practice and checks are generated attempts. The table arrives with games (M4).
- **Recommendations** are regenerated on demand when evidence or progress is newer than the last run (or after 6 hours), not via the outbox. This is simpler and was fast enough at this scale.
- **Questions** are updated in place on re-import. Instead of immutable question versions, every graded assessment response stores a **snapshot** of the question as graded (stem, options, key, explanation), so later edits never change past results. An assessment's item list is only replaced on re-import while it has no attempts. (Recorded 2026-10-04, M3.)
- **Admin statistics** use a read-only reporting DAO (`PlatformStatsDao`) that aggregates across modules. Writes still go only through each module's own DAO.
- **Games (recorded 2026-10-04, M4):**
  - **Generation is server-only.** The server generates each puzzle and sends it to the browser. The browser's JS mirrors only the *rules and scoring*, so the parity vectors cover play, not generation. The puzzle includes what the browser needs for instant feedback, so a determined student could read answers from the page. Replay verification stops forged scores and impossible or implausibly fast play. Games are informal (weight 0.3, capped), so this trade-off is acceptable.
  - **Rule sessions** are small mutable objects, replayed from scratch, rather than a pure `apply(state) → state` function. The behaviour is the same, with less code.
  - **Game configs** are typed Java records checked by `PackValidator`, the same approach as the other pack payloads, instead of JSON Schema files.
  - **Scores never depend on speed**, so there are no lives or combos. Relaxed mode hides the clock and score. The daily cap is a rolling 24 hours.
  - **The activities table** holds only games for now. Practice sets are still generated attempts.
- **Android (recorded 2026-10-04, M9):** built in **Java + XML layouts** with framework classes only, not Kotlin + Compose, because the project uses Java, JSP and JS throughout (the user's request). The WebView serves assets through `shouldInterceptRequest` instead of androidx `WebViewAssetLoader`. Cleartext http is allowed so the app can reach a server on the student's own computer or LAN; the sign-in screen warns when a non-local server isn't https.
- **Rooms (recorded 2026-10-04, M5):**
  - **Platform admins have no special access to a room's people or data.** Rooms are private to their members; admin oversight of rooms arrives with moderation (M6/M10).
  - **Room assessment keys** start with `rm-`, a prefix packs may not use, so one lookup by key serves both kinds.
  - **Item gates** (`soft_prereq`, `hard_lock`) are not stored yet; units and items open by date only.
