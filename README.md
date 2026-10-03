# Pathwise

**Adaptive Personalised Learning Ecosystem.** Every student deserves a different path to understanding.

Pathwise is a learning platform built as a modular monolith. The server is Java 21 with Servlets, JSP and JDBC on Tomcat 10.1, backed by PostgreSQL. A native Android app is planned. It runs at **₹0 cost**: every dependency is free and open source, and no paid API or service is required.

> **Status: early development (milestone M0, Foundations).** Accounts, security, the design system and the app shell work today. Learning features come next. See [docs/FEATURE_STATUS.md](docs/FEATURE_STATUS.md) for exactly what exists, and [docs/MASTER_BLUEPRINT.md](docs/MASTER_BLUEPRINT.md) for the full design.

## Quick start (Windows)

You need a **JDK 21 or newer**. That's all. Maven and PostgreSQL are fetched automatically.

```powershell
.\scripts\setup.ps1 -Demo     # build, create config + database, add demo accounts
.\scripts\dev.ps1             # run at http://localhost:8080
```

- Demo account passwords are random. They are printed by setup and saved in `config\demo-accounts.txt`, which is git-ignored.
- Create an administrator with `java -jar pathwise-app\target\pathwise-app.jar create-admin`.
- To check your machine, run `.\scripts\doctor.ps1`.

## Project layout

| Module | What it contains |
|---|---|
| `pathwise-core` | Pure domain logic: IDs, password hashing, policies, rate limiting. No servlet or JDBC code. |
| `pathwise-data` | JDBC DAOs, connection pool, Flyway migrations (`src/main/resources/db/migration`) |
| `pathwise-web` | Servlets (controllers, JSON API), services, filters, JSP views, the `pw:` tag-file design system, static assets |
| `pathwise-app` | Launcher (embedded Tomcat + embedded PostgreSQL) and CLI (`serve`, `migrate`, `create-admin`, `seed-demo`, `doctor`) |
| `docs/` | Blueprint, feature status, testing record |
| `scripts/` | `setup.ps1`, `dev.ps1`, `doctor.ps1` |

## Common commands

```powershell
.\mvnw.cmd verify                       # build + run every test
.\mvnw.cmd -q install -DskipTests       # fast build
java -jar pathwise-app\target\pathwise-app.jar help
```

Changes to JSP, CSS and JS show up when you refresh the browser. Java changes need `.\scripts\dev.ps1 -Build`.

## Configuration

`config\pathwise.local.properties` is generated on first run with a random secret, and you don't need to edit it for development. Any key can be overridden with an environment variable; for example, `pathwise.db.url` becomes `PATHWISE_DB_URL`.

| Key | Default | Meaning |
|---|---|---|
| `pathwise.env` | `dev` | `dev`, `test` or `production` (production requires a 32+ character secret and uses secure cookies) |
| `pathwise.db.mode` | `embedded` | `embedded` (PostgreSQL under `data\pg`) or `external` |
| `pathwise.db.url` / `.user` / `.password` | (embedded) | JDBC settings for `external` mode |
| `pathwise.http.host` / `.port` | `127.0.0.1` / `8080` | where the server listens |
| `pathwise.trust.proxy` | `false` | set `true` behind Cloudflare Tunnel so client IPs are read correctly |

## Licences

Pathwise uses only free, open-source components. Fonts are under the SIL OFL 1.1 (Inter, Bricolage Grotesque, JetBrains Mono), icons under ISC (Lucide), and htmx under 0BSD. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
