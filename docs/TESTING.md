# Testing record

This file records only test runs that actually happened.

## How to run

```powershell
.\mvnw.cmd verify          # all modules, all tests (needs only a JDK; PostgreSQL is embedded)
```

The HTTP tests in `pathwise-app` start a real embedded Tomcat that serves the JSPs from source, backed by a fresh embedded PostgreSQL database.

## Runs

### 2026-10-03: M0 Foundations

Command: `.\mvnw.cmd -q install` on Windows 11, Temurin JDK 25.0.2, Maven 3.9.9 (wrapper).

| Module | Test class | Tests | Result |
|---|---|---|---|
| core | IdsTest, PasswordHasherTest, PasswordPolicyTest, PlatformPolicyTest, RateLimiterTest, TokensTest | 29 | all passed |
| data | MigrationTest, IdentityDaoTest, OutboxDaoTest | 10 | all passed |
| web | ArchitectureTest, JspSafetyTest, SafeNextTest, WebUnitTest | 19 | all passed |
| app (HTTP, end to end) | WebAuthFlowTest, ApiAuthTest, AdminAndPlatformTest, CliTest | 28 | all passed |
| **Total** | | **86** | **86 passed, 0 failed** |

Manual checks on the same date:
- `scripts\setup.ps1 -Demo` ran end to end: build, config generation, embedded PostgreSQL, migration, demo accounts and doctor report.
- The server ran via `java -jar pathwise-app\target\pathwise-app.jar serve`, and `/healthz` returned `ok`.
- Screenshots were captured with headless Microsoft Edge at 1440×900 and 390×844, in light and dark mode, for the landing, sign-in (with error), sign-up, Today, Settings, Teaching and 404 pages. There were no JavaScript errors and no CSP violations; the only console messages were the expected 401 and 404 page statuses. Defects found and fixed in this pass:
  - mis-encoded "·" in an included fragment
  - the illustration overlapping the hero text
  - the `hidden` attribute being overridden by button styles
  - the sidebar background stopping at the viewport height
  - raw ISO timestamps

Not yet done: accessibility audit with axe-core, Android tests, load tests and the dependency vulnerability scan. All of these are planned (see the blueprint, §35).
