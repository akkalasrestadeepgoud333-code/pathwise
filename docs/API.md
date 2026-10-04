# JSON API (`/api/v1`)

This API is what the Android app uses, and any other client can use it too. It covers the student side of Pathwise. Teaching, admin and moderation tools are web-only.

> No `openapi.yaml` has been written. This file is the reference, checked against the servlet code on 2026-10-04. The behaviour is exercised by `ApiAuthTest`, `PersonalisationFlowTest`, `AssessmentFlowTest`, `GameFlowTest`, `RoomFlowTest`, `DoubtFlowTest` and `RewardsFlowTest`.

## Conventions

- **Authentication:** `POST /api/v1/auth/login` (or `/register`) returns a bearer token. Send it on every call as `Authorization: Bearer <token>`.
  - Tokens last 30 days, and the expiry slides forward while the token is used.
  - Only a hash is stored on the server.
  - Bearer calls need no CSRF token.
- **Bodies** are JSON (`Content-Type: application/json`), UTF-8. Times are ISO-8601 UTC. Ids are UUIDs.
- **Errors** share one envelope:

  ```json
  { "error": { "code": "SESSION_NOT_FOUND", "message": "No such active session.", "details": { }, "requestId": "6b998510e9e0" } }
  ```

  | Status | Meaning |
  |---|---|
  | 400 | invalid input; `details` names fields |
  | 401 | no valid token (the app then signs out) |
  | 403 | not allowed |
  | 404 | not found, *or* not yours (Pathwise doesn't reveal which) |
  | 429 | rate-limited; `Retry-After` is in seconds |
- **Privacy:** anonymous community content never carries author ids, names or emails.

## Endpoints

### Account

| Method | Path | Notes |
|---|---|---|
| POST | `/auth/register` | `{displayName, email, password, teacher?}` → 201 `{token, expiresAt, user}`. Errors: 409 `EMAIL_TAKEN`, 400 `VALIDATION_FAILED`, 403 `REGISTRATION_CLOSED` (an admin closed sign-ups), 429. |
| POST | `/auth/login` | `{email, password}` → `{token, expiresAt, user}`. Rate-limited per IP and email. Suspended accounts are refused. |
| POST | `/auth/logout` | Ends this token's session → 204 |
| GET | `/auth/sessions` | Your signed-in devices |
| DELETE | `/auth/sessions/{id}` | Sign out one device (404 if it isn't yours) |
| POST | `/auth/sessions/revoke-others` | Sign out everywhere else → `{revoked}` |
| GET | `/me` | Your id, name and roles |

### Learning

| Method | Path | Notes |
|---|---|---|
| GET | `/subjects` | Subjects |
| GET | `/subjects/{subject}/map` | Topics, prerequisites, your state per topic |
| GET | `/subjects/{subject}/topics/{topic}` | A topic's skills and modules |
| GET | `/modules/{module}[/{section}]` | Module outline, or one section |
| POST | `/modules/{module}/{section}/complete` | Mark a reading section done |
| GET | `/me/recommendations` | Your next steps, each with a `reasonText` |
| POST | `/recommendations/{id}/{action}` | Respond to a suggestion ("not now", "already know"…) |
| GET | `/me/mastery` | Your level per skill |
| POST | `/practice` | Start a practice set (quick, or for a skill) → attempt |
| GET | `/practice/{id}` | The set and its current question. Answers are never included before you answer. |
| POST | `/practice/{id}/{n}/answer` | Answer question *n* (1-based) → feedback |
| POST | `/practice/{id}/{n}/hint` | Next hint for question *n* |

### Assessments

| Method | Path | Notes |
|---|---|---|
| GET | `/assessments` | Available assessments, with attempts used and left |
| GET | `/assessments/{key}` | Rules: time limit, attempts, AI policy |
| POST | `/assessments/{key}/start` | → attempt (refused when no attempts are left) |
| GET | `/assessments/attempts/{id}` | Questions and saved answers, plus the server's deadline |
| POST | `/assessments/attempts/{id}/save` | Save answers (refused after the deadline plus grace) |
| POST | `/assessments/attempts/{id}/submit` | Grade it. Overdue attempts are auto-submitted by the server anyway. |
| GET | `/assessments/attempts/{id}/result` | Score, per-skill breakdown, and feedback if the policy allows it |

### Games

| Method | Path | Notes |
|---|---|---|
| GET | `/games` | Gallery, with your plays and best scores |
| GET | `/games/{key}` | One game |
| POST | `/games/{key}/start` | → `{attemptId}` (404 if an admin switched the template off) |
| GET | `/games/attempts/{id}` | `{template, puzzle, minGapMillis, title}` |
| POST | `/games/attempts/{id}/finish` | `{actions: [...]}`, the action log. The server replays it, then returns `{status, score, maxScore, accuracyPercent}` or `{status: "rejected", rejectReason}`. Finishing twice counts once. |
| GET | `/games/attempts/{id}/result` | The verified result |

### Rooms

| Method | Path | Notes |
|---|---|---|
| GET | `/rooms` | Your rooms |
| POST | `/rooms/join` | `{code}` (dashes and case are ignored) → joined, or waiting for approval |
| GET | `/rooms/{id}` | The room's curriculum as you see it, plus your next step (404 for non-members) |

### Doubts

| Method | Path | Notes |
|---|---|---|
| GET | `/doubts[?q=]` | Public doubts (search with `q`) |
| GET | `/doubts/{id}` | A thread with answers, by alias |
| POST | `/doubts` | Ask: `{category, title, body, topic?, roomId?, showName?}` → 201 `{id}`. Anonymous unless `showName`. With `roomId`, the doubt is private to that room's teachers. |
| POST | `/doubts/{id}/answers` | Answer: `{body, showName?}` → 201, with the updated thread |

### Rewards and notifications

| Method | Path | Notes |
|---|---|---|
| GET | `/me/rewards` | `{summary: {level, xp, streak, activeToday, …}, longestStreak, freezes, recent: [...], earned: [...], locked: [...]}`. Locked badges include progress text. |
| GET | `/me/notifications` | `{items: [...], unread}` |
| POST | `/me/notifications/read-all` | → `{unread: 0}` |

## Not in the API (web only)

- teaching tools: rooms, assignments, analytics
- the admin console and moderation
- the AI tutor
- live doubt sessions
- accepting or verifying answers
- module checks
