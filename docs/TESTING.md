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

### 2026-10-03: M1 Curriculum & content

Command: `.\mvnw.cmd -q install`. Result: **117 tests, 117 passed, 0 failed**.
New: GraphsAndLayoutTest, PackValidatorTest (core) · CatalogContentDaoTest (data) · ContentTest (web; validates the real bundled pack) · LearningFlowTest (end to end: import → map → topic → full module completion → events → Today → API → admin).

Manual: `import-content` imported 13 topics / 13 modules / 87 sections into the dev database; headless Edge screenshots of Today, Learn, Map (light/dark, desktop/mobile), Topic, Player (overview, traversal visual, flashcards), BST/BFS/hash/stack visuals. Defects found and fixed: SVG attributes broken by Jasper whitespace trimming (regression test added); left-to-right map overflowed (replaced with top-down layout + waypoints); 0% ring dot; card stretching; singular/plural narration.

### 2026-10-04: M2 Evidence → learner model → engine

Command: `.\mvnw.cmd -q install`. Result: **146 tests, 146 passed, 0 failed**.
New: LearnerCoreTest (grader + golden mastery values), RecommenderTest (9 persona fixtures), QuestionServiceTest (shuffle, no answer leakage), PersonalisationFlowTest (end to end: failed check → Strengthen suggestion quoting "2 of your last 2 answers" → practice with hints and final answers → model and reason change; Not now / Already know / goals; privacy; API).

Manual: pack v2 imported (114 questions, 100 sections). Headless Edge screenshots of Today (with "Why this?"), Next steps, a practice question with a hint, feedback, summary, progress, practice hub and topic. No console errors. Defects found and fixed: correct answer always shown first (now shuffled per student); "Getting started → Needs practice" was labelled an improvement (levels are now ranked by meaning); a SQL type error in the suppressions query (caught by the tests).

### 2026-10-04: M3 Official assessments

Command: `.\mvnw.cmd install` (run per module while developing, then in full). Result: **160 tests, 160 passed, 0 failed**.
New: AssessmentCoreTest (start rules, deadline and grace, late, feedback visibility, item analysis), PackValidatorTest assessment rules and the rule that keeps assessment-only items out of checks, ContentTest (bundled assessments use unseen items), AssessmentFlowTest. AssessmentFlowTest runs end to end and covers:
- no answers or rationales in the page or API before grading
- save and prefill, plus confirmation for unanswered questions
- grading exactly once, official evidence and model update
- attempt limit and intruder 404s
- a forced past deadline: late save refused, auto-submit on open and through the background sweep
- the diagnostic seeding Progress
- admin item analysis (students get 403)
- the API flow

These DB constraints are tested directly: official evidence only from assessments, and one open attempt.

Manual: pack v3 imported (126 questions, 3 assessments). Headless Edge screenshots of the list, rules, attempt (desktop, mobile, dark), confirmation, result and admin analysis. No console or CSP errors. Defects found and fixed: the sticky timer bar slid under the top bar; the timer didn't align right; the average showed as "3.0%"; analysis flags from very few attempts were presented without caution. One existing test had assumed no official evidence existed anywhere, which would have failed depending on test order, so it was rescoped.

### 2026-10-04: M4 Games

Command: `.\mvnw.cmd install`. Result: **184 tests, 184 passed, 0 failed, 0 skipped**. Node 24 was present, so the JS parity check ran.

New tests:
- **GamesTest (core):** 12 shared-vector cases across the three games, plus deterministic generation with valid BSTs, a perfect play of a generated puzzle, tamper rejection and config validation. Tamper rejection covers too-fast moves, more time than has passed, unfinished games, moves after the end, timestamps going backwards and an empty log. SplitMix64 outputs are pinned.
- **GameParityTest (web):** runs `node --test` on the same vectors against the browser's rules (12/12).
- **GameFlowTest (app):** covers the following:
  - every game is played, verified and turned into informal evidence
  - too-fast, unfinished and forged logs are rejected with nothing written
  - a rejected game can't be re-finished, and finishing twice counts once
  - intruders get 404s
  - the daily cap holds (13 plays → 12 rows for preorder)
  - the API flow works

Manual: pack v4 imported (4 games). All three games were played in headless Edge through the real JS, with real timing and one deliberate mistake each. The server verified each game: Traversal Race scored 224/230, exactly the cost of the one miss. Screenshots: gallery, intro, each board, results (light/dark, desktop/mobile). No console or CSP errors. Defects found and fixed:
- the root's order badge was clipped
- wrong-guess highlights stayed on screen
- "in preorder order" wording, fixed in both Java and JS
- Bug Hunt focused line 1 on load, which looked like a pre-selection
- the Node parity runner used the spec reporter, so the check had been looking for the wrong summary line
- one test was rejected by CSRF before it reached the ownership check, so it now loads a page first

### 2026-10-04: M5 part 1 Rooms

Command: `.\mvnw.cmd install`. Result: **196 tests, 196 passed, 0 failed, 0 skipped**.

New tests:
- **RoomPolicyTest:** the whole permission matrix, including each co-teacher toggle on its own, archived rooms, pending and removed members, and join codes.
- **AssessmentCoreTest:** manual release.
- **RoomFlowTest:** six end-to-end tests:
  - approval, then the curriculum each student sees (dated units hidden), the next step moving after a submission, and analytics and CSV showing it
  - strangers (404), students on teacher pages (403), and a co-teacher invite: a student can't use it, it's single-use, and it's shown once
  - co-teacher permissions turned off, and a removed student unable to rejoin by code
  - invite-only rooms and links that aren't used up by members already in the room
  - a teacher-made assessment: a draft hidden from students, an outsider blocked, results held then released, item analysis, and archiving and restoring
  - class statistics hidden until 5 students
  - the rooms API

Manual: headless Edge screenshots of the teacher home, cockpit (with requests), curriculum (desktop and mobile), people, assessment builder, analytics and the student room (desktop and mobile). No console or CSP errors. Defects found and fixed:
- **Form posts were decoded as ISO-8859-1**, so non-ASCII text was garbled. A room named "DSA – Section A" showed it. The fix is UTF-8 request and response encoding in `web.xml`, and RoomFlowTest now covers it.
- Table row headers were rendered as uppercase column headings.
- Curriculum titles were truncated on phones.

### 2026-10-04: M5 part 2 Assignments and room context

Command: `.\mvnw.cmd install`. Result: **198 tests, 198 passed, 0 failed, 0 skipped**.

New: **AssignmentFlowTest** (two end-to-end tests):
- a draft hidden from students, then published and shown in the curriculum and in Next steps and Today
- save a draft, hand in, and edits locked after hand-in
- a grade out of range refused, and sending back without a comment refused
- sending back with a comment, a resubmission (version 2), then a grade of 8/10, which gives official evidence (weight 0.9, outcome 0.8) for both linked skills and updates the model
- re-grading does nothing, and analytics and the student's progress show the grade
- late work refused when not allowed and flagged as late when allowed
- the missed deadline in the attention list
- outsiders get 404, and students on teacher pages get 403

PersonalisationFlowTest's check that practice is never official now names the allowed official sources explicitly, since graded assignments are official too.

Manual: headless Edge screenshots of the assignments list, grading, the student's handed-in and graded views (desktop and mobile), and "From your rooms" on Next steps. No console or CSP errors. Defect fixed: a room's next item was listed twice when it was also due soon.

### 2026-10-04: M6 Doubts, community and moderation

Command: `.\mvnw.cmd install`, then the app module re-run after two test fixes. Result: every suite passed. The new tests are DoubtRulesTest (core) and DoubtFlowTest (3 end-to-end tests). Exact totals are in the M7 record below, which ran the whole build again.

DoubtFlowTest covers:
- the anonymity leak scan across the thread page, list and API, using a cookie-less API client
- votes, acceptance, and verification by a Verified Educator, but not by the asker
- private doubts: classmates and strangers get 404, the teacher's room tab and cockpit count, a teacher can't verify their own answer, and private doubts never appear publicly
- reports, deduplicated; the moderation queue (403 for students); hiding with a required reason, logged
- identity reveal: refused without a proper reason, then logged
- blocking by alias, KB promotion, and duplicate deflection finding the new article

Note: this run took 48 minutes. The machine slept or ran out of memory partway (the test JVM used about 10 s of CPU over that time). No test was at fault.

### 2026-10-04: M7 XP, streaks, badges and notifications

Command: `.\mvnw.cmd install` (core, then data, web and app). Result: **213 tests, 213 passed, 0 failed** (core 100, data 14, web 31, app 68).

New tests:
- **RewardsCoreTest:** the level curve; streaks, including freezes covering one and two missed days and today never breaking a streak; game XP and the improvement threshold; badge progress text for unmet criteria.
- **RewardsFlowTest:** three end-to-end tests:
  - four perfect games earn XP only three times, the day counts toward the streak, and five Bug Hunts award Debug Detective with badge XP and a notification
  - the achievements page, Today strip and bell; "mark all read" clears the bell
  - assessment XP awarded once; re-inserting the same outbox event changes nothing; opening a notification redirects and marks it read, and other people's notifications go nowhere
  - helper XP for an accepted answer, none for asking, and the XP reversed when a moderator removes the answer

The ArchitectureTest caught an anonymous class using DAOs. It became a named `*Service` class.

### 2026-10-04: M8 AI tutor

Command: `.\mvnw.cmd install`, then TutorFlowTest re-run after a fix. Result: core 103, data 14 and web 33 passed; app 73 with TutorFlowTest 5/5 after the fix.

New tests:
- **AiCoreTest:** context delimiting and injection, history trimming, and the leak guard's near-misses ("17" isn't "7", "1.5" isn't "5").
- **AiProviderTest:** a stub HTTP server checks the request shape, reply parsing, HTTP 500, an unreachable host, and AI off.
- **TutorFlowTest:**
  - Guided Help for a module and for resources, with follow-ups
  - the hint ladder, then explain refused before answering and working after a wrong answer
  - no hints on answered questions, and other people's attempts give 404
  - conversations are private and deletable
  - a blocked assessment turns the tutor off until submission
  - a stub local model that leaks the answer twice is replaced by the authored hint, and a safe hint is kept and labelled as AI

Defect found and fixed: the tutor read the wrong practice question. Pages number questions from 1, but stored positions start at 0.

Not verified: a real model. Ollama isn't installed on this machine, so the AI path has only been exercised against stubs.

Not yet done: accessibility audit with axe-core, Android tests, load tests and the dependency vulnerability scan. All of these are planned (see the blueprint, §35).
