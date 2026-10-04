# Architecture

Pathwise is a **modular monolith**: one server process, one database, and code split into modules with enforced boundaries. The Android app is a separate client of the same JSON API. This document describes the system as built. The full design, including parts not built, is in [MASTER_BLUEPRINT.md](MASTER_BLUEPRINT.md).

## Maven modules

```
pathwise-core   pure Java: rules and models, no servlets, no JDBC
     ▲
pathwise-data   JDBC DAOs (one per area), Flyway migrations, connection pool (HikariCP)
     ▲
pathwise-web    services, controllers (JSP pages), API servlets, filters, views, static assets, content packs
     ▲
pathwise-app    launcher: embedded Tomcat 10.1 + embedded PostgreSQL; CLI; end-to-end tests
```

`pathwise-core` holds the logic that decides things. It is unit-tested without a database:
- `PlatformPolicy` and `RoomPolicy`: who may do what
- `MasteryModel`: mastery from evidence
- `Recommender`: next steps
- `AssessmentRules` and `ItemAnalysis`
- game rules with deterministic generation and replay (`SplitMix64`, `Replay`)
- `Levels`, `Streaks`, `Badges`
- `DoubtRules` and `LiveSessionRules`
- `Prompts` and `AnswerGuard`: AI safety

## Areas inside `pathwise-web`

| Package | Owns |
|---|---|
| `identity` | accounts, sign-in, sessions, bearer tokens, password re-entry |
| `admin` | overview, people, roles, settings, the game registry, rooms overview, audit, moderation |
| `content`, `learn` | content-pack import, catalogue, Learning Map, module player |
| `questions`, `practice` | question bank, grading, practice sets, hints |
| `learner`, `engine` | evidence → learner model; recommendations with reasons |
| `assessment` | official assessments, deadlines, grading, item analysis |
| `games` | game gallery, play, server-side replay verification |
| `rooms` | rooms, membership, curriculum, assignments, room assessments, teacher analytics |
| `community` | doubts, answers, votes, reports, knowledge base, live sessions |
| `rewards` | XP, streaks, badges, notifications |
| `ai` | tutor, Guided Help, Ollama provider |
| `platform` | config, bootstrap, filters, the event bus and outbox worker, view helpers |

Boundaries are enforced by `ArchitectureTest` (ArchUnit) on every build:
- Only classes named `…Service` (or data and platform code) may use DAOs.
- `identity` doesn't depend on `admin`, and `admin` doesn't use identity's DAOs.

## A request

```
browser / app
  → RequestIdFilter      (X-Request-Id, used in error pages and logs)
  → SecurityHeadersFilter (CSP, frame, referrer, permissions; HSTS in production)
  → AuthFilter           (resolves the Actor from the web session or the bearer token; refuses suspended accounts)
  → CsrfFilter           (non-GET cookie requests need the session token)
  → PageServlet / ApiServlet subclass (controller)
  → …Service             (policy check, one transaction via Tx)
  → …Dao                 (PreparedStatement SQL)
  → JSP view (with ViewModels maps) or JSON (Jackson)
```

- **Pages** are server-rendered JSP built from the `pw:` tag files: shell, form, icon, question, feedback and so on. JavaScript is small and optional: game boards, the assessment timer, theme switching, and htmx for the live-session queue. Every page works without JavaScript except the games. There are no inline scripts or styles, so a strict CSP applies.
- **The JSON API** (`/api/v1`) serves the Android app with bearer tokens. Errors share one envelope (see [API.md](API.md)).

## Events: the transactional outbox

A service that changes something important also writes a row to `domain_events` in the **same transaction**, for example `assessment.graded`, `game.completed`, `doubt.answer_verified` or `room.join_requested`. `OutboxWorker` reads pending events every second and calls the subscribed handlers.

The main consumer is `RewardsService`. From events it awards XP and streak days, checks badges, and sends notifications. Handlers run after commit and must be idempotent: every XP row has a unique key, and badges are unique per user. A redelivered event changes nothing, and `RewardsFlowTest` checks this.

## How personalisation works

There is no machine learning.
1. **Evidence:** every answer is a `learning_evidence` row for each skill it tests. That covers practice, module checks, games, assessments and graded assignments. Each row carries a source and a weight; games, for instance, are informal and capped. Only assessments and graded assignments are *official*.
2. **The learner model** (`MasteryModel`) turns a skill's evidence into an estimate, a confidence, a state and a review date. The states are Not started, Exploring, Needs support, Developing, Proficient and Mastered. "Mastered" needs recent official evidence.
3. **The recommender** (`Recommender`) builds suggestions in slots: Continue, Strengthen, Challenge, Explore, Revise, and Teacher (from rooms).
   - Each suggestion is scored from named factors: need, leverage, goal fit, curriculum fit, variety and recency.
   - It carries a plain-language reason, which the "Why this?" panel shows.
   - Root causes come first: if a skill struggles because a *required* prerequisite topic is weak, the prerequisite is suggested.
   - Suggestions are regenerated when there is newer evidence or activity, or after 6 hours.
4. **Students stay in control:** "Not now", "Already know" and goals feed back into the next run.

The reasoning is golden-tested with persona fixtures (`RecommenderTest`) and end to end (`PersonalisationFlowTest`, `DemoFlowTest`).

## Games: trust but verify

The server generates each puzzle from a seed and sends it to the browser, which plays it with JavaScript rules that mirror the Java rules. Shared JSON test vectors keep the two in step (`GamesTest` in Java, `GameParityTest` through Node).

When a game ends, the browser sends only the **action log**. The server replays it with the Java rules and checks the timing between moves. It rejects:
- impossible moves
- implausibly fast play
- timestamps that go backwards
- moves after the end

Only then does it record a score and informal evidence. The Android app runs the same JavaScript in a locked-down WebView and submits through the same endpoint.

## AI tutor

`AiProvider` is either **None** (the default) or **Ollama** (a local model over HTTP).
- With None, or when the model fails or times out, `TutorService` answers with **Guided Help**: links and explanations built from modules, the knowledge base, authored hint ladders and option rationales.
- Every reply is labelled with which one answered.
- Prompts put Pathwise content inside a delimited `<context>` block.
- `AnswerGuard` regenerates a hint once if it leaks the answer, then falls back to the authored hint.
- Assessments can block the tutor entirely, or allow hints only.

## Android app

The app is plain Java with framework classes only (no androidx), minSdk 26 and target 34.
- Each screen is an `Activity` built on `Screen`, a scrolling column of cards.
- `Api` wraps `HttpURLConnection` and `org.json` on a background executor.
- Games use the website's own JavaScript, copied into the APK's assets at build time and served from a private https origin to a WebView. File, content and remote access are off.
- Notifications are local. They are checked when the app opens, and shown only if the student allowed them.

## Background work

`AppBootstrap` schedules these in-process:
- the outbox worker (every second)
- the assessment overdue sweep (every 60 s), which auto-submits expired attempts
- housekeeping: purging expired sessions, outbox events processed more than 7 days ago, and notifications older than 180 days

## Deliberate deviations from the blueprint

These are listed with reasons in [FEATURE_STATUS.md](FEATURE_STATUS.md), under "Deliberate deviations". The main ones are:
- the Android app is in Java + XML, not Kotlin/Compose
- live sessions use polling, not WebSocket
- recommendations are regenerated on demand, not by the outbox
- there are no immutable question versions; graded answers keep a snapshot of the question instead
