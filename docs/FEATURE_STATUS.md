# Feature status

Statuses follow blueprint §44:
- **IMPLEMENTED:** merged, tested by tests that actually ran, and reachable in the UI or API.
- **PARTIALLY IMPLEMENTED:** some behaviour works; the gaps are listed.
- **PROPOSED:** designed, not built.
- **FUTURE:** intentionally out of scope for v1.

Last updated: **2026-10-03** (end of M0, Foundations).

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

## Partially implemented

| Feature | Works | Missing |
|---|---|---|
| Learner profile | stored per user; the theme preference is used | timezone editing, goals and modalities UI (M2) |
| Today page | greeting, account card, honest empty state | recommendations, progress and map (M1–M2) |
| Teacher area | access control, empty state | rooms (M5) |
| Security tooling | headers, CSRF, rate limits, audit | OWASP Dependency-Check run (planned for M11) |

## Proposed (designed in the blueprint, not built)

Catalogue and topic graph (M1) · content packs and module player (M1) · Learning Map (M1) · evidence pipeline, learner model and recommendation engine (M2) · assessments (M3) · games framework and games (M4) · rooms, assignments and teacher analytics (M5) · doubts, community, moderation and knowledge base (M6) · XP, streaks, badges and notifications (M7) · AI tutor with Guided Help fallback (M8) · Android app (M9) · projects, live doubt sessions and full admin console (M10).

## Future (out of scope for v1)

Knowledge tracing / ML models · embeddings-based retrieval · server-side code judging · iOS · audio/video sessions · LTI/xAPI.

## Deliberate deviations from the blueprint (recorded 2026-10-03)

- **Rate limiting** uses a small built-in token bucket (`pathwise-core/RateLimiter`) instead of Bucket4j. It has the same behaviour for a single process and one fewer dependency.
- **Background jobs** are scheduled in-process (`AppBootstrap`). The durable `jobs` table will be added in M5, when deadline reminders need it.
- **Theme preference** is stored on `learner_profiles.theme`.
- **View models:** JSP EL in Tomcat 10.1 cannot read Java record accessors, so controllers pass records to views as read-only maps (`ViewModels`).
- **Admin statistics** use a read-only reporting DAO (`PlatformStatsDao`) that aggregates across modules. Writes still go only through each module's own DAO.
