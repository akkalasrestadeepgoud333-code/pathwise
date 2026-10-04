# Database

PostgreSQL 16. In development and tests this is an embedded PostgreSQL under `data\pg`; a long-running deployment should use an installed one (see [DEPLOYMENT.md](DEPLOYMENT.md)).
- **Migrations:** Flyway, in `pathwise-data/src/main/resources/db/migration`. Applied migrations are never edited; changes go in a new `V0NN__name.sql`.
- **Access:** only through the DAOs in `pathwise-data`, with plain JDBC and `PreparedStatement`. HikariCP pools the connections.

## Conventions

- **Primary keys** are `uuid`, generated in Java as time-ordered UUIDv7 (`Ids`). Logs use `bigint` identity keys.
- **Enumerations** are `text` with a `CHECK` constraint, not PostgreSQL enum types, so adding a value is a simple migration.
- **Times** are `timestamptz` everywhere. Days for streaks are computed in Asia/Kolkata.
- **Derived state can be rebuilt.** `skill_mastery` is derived from `learning_evidence` and can be recomputed at any time.
- **Append-only tables** have a trigger that rejects updates and deletes. These are `audit_log` and `learning_evidence`.
- **Soft deletion:** accounts become `status = 'deleted'` with `deleted_at`, and are never hard-deleted. Content is archived, not removed.

## Schema by migration

| Migration | Tables | Purpose |
|---|---|---|
| V001 baseline | `users`, `user_platform_roles`, `sessions`, `auth_tokens`, `learner_profiles`, `domain_events`, `audit_log`, `platform_settings` | accounts, roles, sessions (hashed tokens only), the outbox, the audit log, live settings |
| V002 catalog | `catalog_nodes`, `topics`, `skills`, `topic_prerequisites`, `modules`, `module_skills`, `module_sections`, `flashcards`, `resources`, `content_packs`, `module_progress`, `section_progress` | the subject graph and module content (from content packs), per-student progress |
| V003 learner | `questions`, `question_options`, `question_skills`, `activity_attempts`, `activity_attempt_items`, `learning_evidence`, `skill_mastery`, `learning_goals`, `recommendations` | the question bank, practice attempts, evidence, the learner model, goals, suggestions with reasons and factors |
| V004 assessments | `assessments`, `assessment_items`, `assessment_attempts`, `attempt_responses` | official assessments; each graded response keeps a snapshot of the question as graded |
| V005 | (alters `questions`) | `practice_enabled`: assessment-only items stay out of practice |
| V006 games | `game_templates`, `activities` | the game registry (admins can switch templates off) and game activities; plays reuse `activity_attempts` |
| V007 rooms | `rooms`, `room_memberships`, `room_invites`, `room_units`, `room_unit_items`, `announcements` | classes; the owner is a membership row; invite tokens are stored hashed |
| V008 assignments | `assignments`, `assignment_skills`, `submissions` | room assignments with versions, grading and returns |
| V009 community | `threads`, `posts`, `post_votes`, `anonymous_aliases`, `user_blocks`, `reports`, `moderation_actions`, `kb_articles` | doubts (public or private to a room's teachers), aliases per thread, moderation, the knowledge base (full-text `tsvector`) |
| V010 rewards | `xp_events`, `activity_days`, `badge_awards`, `mastery_transitions`, `notifications` | XP (unique key per award, so handlers are idempotent), streak days, badges, in-app notifications |
| V011 AI | `ai_conversations`, `ai_messages` | tutor conversations, private to their owner; each message records whether it came from the AI or Guided Help |
| V012 live | `live_sessions`, `session_participants`, `session_questions`, `session_question_votes` | live doubt sessions; participants are known by alias number only |
| V013 materials | `room_materials` | teachers' notes and PDFs per room. PDF bytes live on disk under `pathwise.storage.dir`; the table holds metadata and a server-generated file name. |

## Rules the database itself enforces

These hold even if application code has a bug:
- **Official evidence comes only from assessments and graded assignments:** `check (is_official = (source_kind in ('assessment', 'assignment')))`.
- **Evidence and the audit log can't be edited or deleted** (triggers).
- **One live account per email,** case-insensitively (a partial unique index on `lower(email)`).
- **One open attempt per student per assessment.**
- **XP rows have a unique key,** so a redelivered event can't award XP twice.
- **Notification links must be local paths** (`link ~ '^/'`).
- **Room codes** use an unambiguous alphabet; invite and session tokens are stored only as SHA-256 hashes.
- **Live-session aliases are unique within a session.** A live session can't be `live` without a start time, or `ended` without an end time.
- **Prerequisites form no cycles.** Cycles are rejected by the content-pack validator before import, not by SQL.

## Inspecting the embedded database

The embedded server listens only on localhost while Pathwise runs. Its port and credentials are in `config\pathwise.local.properties` (`pathwise.db.*`), and any PostgreSQL client can connect with them, for example `psql` or pgAdmin, both free.

## Backups

Uploaded PDFs live in the `storage` folder (`pathwise.storage.dir`); back it up at the same time as the database. For the embedded database, stop Pathwise and copy the `data\pg` folder. For an installed PostgreSQL:

```powershell
pg_dump -Fc -f pathwise-backup.dump <database>
pg_restore -d <database> pathwise-backup.dump
```

No automatic backups are configured.
