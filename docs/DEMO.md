# Demonstration

The demonstration follows one student, Asha, from sign-up to a changed learner model, with a teacher (Prof. Meera) and a mentor. It is the flow in [MASTER_BLUEPRINT.md §39a](MASTER_BLUEPRINT.md), adjusted to what Pathwise actually does.

## Run it automatically

```powershell
.\mvnw.cmd -q install -DskipTests
.\mvnw.cmd -o test -pl pathwise-app -Dtest=DemoFlowTest
```

`DemoFlowTest` runs all 21 steps over real HTTP, against a real server and a fresh database, and asserts each outcome. It writes a narrated transcript to `pathwise-app\target\demo-transcript.txt`. Here is the transcript from the run on 2026-10-04:

```
1      Asha registers; Today opens.
2      Joins DSA – Section A with code …; Prof. Meera gets a notification and approves.
3-4    Takes the starting-point check; answers become 16 official evidence rows.
5      Progress and the Learning Map update; preorder is now 'exploring'.
6      Next steps: explore: Arrays… (You've answered a few questions on Arrays; its module covers it properly.) | explore: Recursion…
7-8    Works through Tree traversals; the module check is recorded as evidence.
9-10   Plays Traversal Race; the server replays the moves, then records informal evidence.
11     Asks the tutor "why is preorder root-first?" and gets an answer labelled with its source.
12     Quick practice: 5 questions answered, each one evidence.
13-14  Posts an anonymous doubt; a mentor answers; Asha accepts; Prof. Meera verifies. Asha's name never appears.
15-16  Takes the trees topic test; preorder moves from 'exploring' to 'developing'.
17-18  Badges earned from evidence: first-steps (each with a notification).
19     Today shows the new XP, streak and unread notifications when (re)loaded.
20     Prof. Meera's room analytics list Asha with her progress.
21     New next steps: explore: Arrays… | explore: Recursion… | explore: Binary search trees…
```

The test leaves two answers of the diagnostic blank (preorder and recursion) and answers everything else correctly.

## Run it live, for an audience

1. Set up and start Pathwise:

   ```powershell
   .\scripts\setup.ps1 -Demo
   .\scripts\dev.ps1
   ```

2. Use two browser windows: one normal, one private.
3. Sign in as the demo teacher in one window and the demo student in the other. The passwords are in `config\demo-accounts.txt`.

| Step | In the student window | In the teacher window | What to point out |
|---|---|---|---|
| 1 | Register a new student (or use the demo one) | | Today, with Explore suggestions and their reasons |
| 2 | Rooms → join with the code | Teaching → create "DSA – Section A" (approval), show the code; approve the request | The teacher's bell shows the join request |
| 3–4 | Assessments → Data Structures starting-point check; skip the preorder and recursion questions | | Server timer; no answers in the page; result with per-skill breakdown |
| 5 | Progress, then Learn → Map | | Levels per skill; Map colours |
| 6 | Next steps → "Why this?" | | Every suggestion explains itself; "Not now" / "Already know" |
| 7–8 | Open Tree traversals; step through; answer the check | | Visuals, flashcards, the check feeding evidence |
| 9–10 | Games → Traversal Race | | Server-verified score; "light practice, never a grade" |
| 11 | Tutor → ask "why is preorder root-first?" | | The label: Guided Help (or AI, if Ollama runs) |
| 12 | Practice → Quick practice, use a hint | | Hint ladder, feedback with rationales |
| 13–14 | Doubts → ask anonymously | Answer as a mentor (a second account with the mentor role); accept as the student; verify as the teacher (needs Verified Educator, set in Admin → People) | Aliases only; "Accepted", "Teacher verified" |
| 15–16 | Assessments → Topic test: trees, traversals and BSTs | | Progress: preorder moves up a level |
| 17–18 | Achievements; the bell | | Badges come from recorded evidence, with progress on locked ones |
| 19 | Reload Today | | XP, streak, notifications |
| 20 | | Room → Analytics | The student's progress; CSV export |
| 21 | Next steps | | New suggestions follow from the new evidence |

Optional extras:
- **The Android app** (Today, a game, notifications). See [SETUP.md §5](SETUP.md).
- **A live doubt session:** the teacher hosts it, the student joins as "Participant 1".
- **The admin console.**

## Where reality differs from the blueprint's story

- **Step 16:** the blueprint expected "Developing → Proficient". With one diagnostic and one topic test, preorder moves one level, Exploring → Developing. Reaching Proficient needs more evidence over time, which is deliberate: the learner model is cautious.
- **Steps 17–18:** the blueprint named the Comeback and First Steps badges. The run earns **First Steps**. Comeback needs a return after a break, which a single sitting can't show.
- **Step 19:** the blueprint expected a live push update over WebSocket. Pathwise has no live push; pages show changes when loaded. Live doubt sessions refresh by polling.
- **Step 6:** this step exposed a real bug during M11, now fixed. Right after the diagnostic, the engine suggested nothing at all. It now offers the topics' modules, with a reason that says why.
