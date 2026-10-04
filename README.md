# Pathwise

**Adaptive Personalised Learning Ecosystem.** Every student deserves a different path to understanding.

Pathwise is a learning platform for a subject taught as a graph of topics and skills (the bundled pack is Data Structures). It has three parts:
- **A modular-monolith web app:** Java 21 with Servlets, JSP and JDBC on Tomcat 10.1, backed by PostgreSQL.
- **A native Android app**, written in Java with XML layouts.
- **Small, framework-free browser JavaScript.**

It runs at **₹0 cost**. Every dependency is free and open source, and no paid API or service is needed.

> **Status: v1 milestones M0–M11 are done; some parts are partial and others are only proposed.**
> - **What exists, item by item, with the test that shows it:** [docs/FEATURE_STATUS.md](docs/FEATURE_STATUS.md). Each item is marked Implemented, Partially implemented, Proposed or Future.
> - **What has actually been tested:** [docs/TESTING.md](docs/TESTING.md).
> - **The full design:** [docs/MASTER_BLUEPRINT.md](docs/MASTER_BLUEPRINT.md).

## What it does

- **Learn:**
  - A Learning Map of topics and prerequisites
  - Modules with visuals, flashcards and checks
  - Practice with hint ladders and feedback
- **Personalisation (rule-based, not ML):**
  - A learner model per skill, built from evidence
  - Next steps, each with a plain-language reason
- **Official assessments:** a diagnostic, quizzes and topic tests. They are kept separate from practice, use server-side deadlines, and offer item analysis.
- **Games:** Traversal Race, Match Pairs and Bug Hunt. The server replays every game before trusting the score.
- **Rooms:** classes with a curriculum, assignments, room assessments and teacher analytics.
- **Community:**
  - An anonymous doubt network
  - Private doubts to teachers
  - Moderation and a knowledge base
  - Live doubt sessions
- **Motivation:** XP, streaks, badges and in-app notifications.
- **AI tutor:** an optional local model through Ollama. When no model runs, it falls back to Guided Help built from Pathwise's own content, and it always says which one answered.
- **Admin console:** people, roles, suspension, settings, the game registry, rooms and the audit log.
- **Android app:** Today, practice, games (the same JavaScript, server-verified), rooms, doubts and notifications.

## Quick start (Windows)

You need a **JDK 21 or newer**. That's all: Maven and PostgreSQL are fetched automatically.

```powershell
.\scripts\setup.ps1 -Demo     # build, create config + database, import content, add demo accounts
.\scripts\dev.ps1             # run at http://localhost:8080
```

- Demo passwords are random. Setup prints them and saves them in `config\demo-accounts.txt`, which is git-ignored.
- To create an administrator, run `java -jar pathwise-app\target\pathwise-app.jar create-admin --email you@example.com --name "Your Name"`.
- The Android app is built with `.\scripts\build-apk.ps1` and needs Android Studio, which is free. See [docs/SETUP.md](docs/SETUP.md).

## Documentation

| Document | What's in it |
|---|---|
| [SETUP](docs/SETUP.md) | Install, configure, run, build the APK, troubleshoot |
| [ARCHITECTURE](docs/ARCHITECTURE.md) | Modules, request flow, events, how personalisation works |
| [DATABASE](docs/DATABASE.md) | Schema by migration, invariants, backups |
| [API](docs/API.md) | The `/api/v1` JSON API used by the Android app |
| [SECURITY](docs/SECURITY.md) | Controls as built, and the M11 review |
| [TESTING](docs/TESTING.md) | Every test run that actually happened |
| [DEMO](docs/DEMO.md) | The 21-step demonstration and how to run it |
| [DEPLOYMENT](docs/DEPLOYMENT.md) | Running for others at ₹0 (self-host and tunnel) |
| [LIMITATIONS](docs/LIMITATIONS.md) | What Pathwise does not do, plainly |
| [FEATURE_STATUS](docs/FEATURE_STATUS.md) | Implemented / partial / proposed, item by item |

## Project layout

| Path | What it contains |
|---|---|
| `pathwise-core` | Pure domain logic with no servlets or JDBC: policies, learner model, recommender, game rules, assessment rules, rewards |
| `pathwise-data` | JDBC DAOs, the connection pool, Flyway migrations (`src/main/resources/db/migration`) |
| `pathwise-web` | Controllers, the JSON API, services, filters, JSP views, the `pw:` tag library, static assets, content packs |
| `pathwise-app` | Launcher (embedded Tomcat + embedded PostgreSQL), CLI, end-to-end tests |
| `android/` | The Android app (Gradle) |
| `docs/` | Documentation |
| `scripts/` | `setup.ps1`, `dev.ps1`, `doctor.ps1`, `build-apk.ps1` |

## Common commands

```powershell
.\mvnw.cmd verify                       # build + run every test
.\mvnw.cmd -q install -DskipTests       # fast build
java -jar pathwise-app\target\pathwise-app.jar help
```

## Licences

Pathwise uses only free, open-source components:
- Fonts are under the SIL OFL 1.1 (Inter, Bricolage Grotesque, JetBrains Mono).
- Icons are under ISC (Lucide).
- htmx is under 0BSD.

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
