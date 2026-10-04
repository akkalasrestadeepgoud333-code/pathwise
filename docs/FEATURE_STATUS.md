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

## Partially implemented

| Feature | Works | Missing |
|---|---|---|
| Learner profile | stored per user; the theme preference is used | timezone editing, goals and modalities UI (M2) |
| "Ask" recommendation slot | not generated | needs the AI tutor (M8) and doubts (M6) |
| Teacher area | access control, empty state | rooms (M5) |
| Assessment AI policy | stored and shown to students | enforced when the AI tutor exists (M8). Pathwise cannot control outside tools, and says so |
| Assessment feedback policies | `immediate` and `manual_release` (score only until released) | the teacher release button and `after_due` (needs due dates from rooms, M5) |
| SQL-query questions | — | deferred until a DBMS pack exists; the grader has no SQL type yet |
| Security tooling | headers, CSRF, rate limits, audit | OWASP Dependency-Check run (planned for M11) |

## Proposed (designed in the blueprint, not built)

Teacher-made assessments (M5) · games framework and games (M4) · rooms, assignments and teacher analytics (M5) · doubts, community, moderation and knowledge base (M6) · XP, streaks, badges and notifications (M7) · AI tutor with Guided Help fallback (M8) · Android app (M9) · projects, live doubt sessions and full admin console (M10).

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
