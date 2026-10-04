# Limitations

These are the things Pathwise v1 does not do, or does only partly. The item-by-item list, with tests, is [FEATURE_STATUS.md](FEATURE_STATUS.md).

## Personalisation and assessment

1. **Personalisation is rule-based, not machine learning.** There is no knowledge tracing and no trained model.
   - The mastery estimates and thresholds are reasoned defaults.
   - They have not been calibrated on real students, and no study shows that they improve learning.
2. **The learner model is cautious.** A skill moves one level at a time, and "Mastered" needs recent official evidence. A student may feel the system is slow to recognise them.
3. **Code-writing and SQL questions aren't graded by the server.** The question bank is choice, numeric and short-text only. SQL-query grading is deferred until a databases pack exists.
4. **Assessment integrity is limited to what Pathwise controls.** Deadlines are enforced and the tutor's AI policy holds. Nothing stops a student from using outside tools or help. Item analysis from very few attempts is shown with a caution.
5. **Game anti-cheat** stops forged or impossible scores by replaying every game. It can't stop a student getting help from someone else. For instant feedback, the browser holds the puzzle, so a determined student could read answers from the page. That's why games are informal, low-weight evidence.

## Content

6. **There is one subject.** Data Structures, with 13 topics, 13 modules, about 126 questions, 4 games and 3 assessments, is bundled. Other subjects need authoring in the content-pack format.
7. **Teachers can't write their own questions or modules yet.** Room assessments draw on the existing bank.

## AI

8. **The AI tutor is optional, local and small.** With Ollama and a ~3B model it can be wrong, and it is labelled as such.
   - Without Ollama, the tutor is Guided Help: Pathwise's own content, not AI.
   - The AI path was tested only against a stub server, never a real model.
   - The Android app has no tutor screen.

## Community and live features

9. **Live doubt sessions are text only,** with no audio or video. They refresh by polling every 4 seconds, not instantly.
10. **There is no push of any kind.**
    - There are no e-mail, Web Push or Firebase notifications.
    - The website shows new things when a page loads.
    - The Android app checks once, when it opens.
11. **There is no password reset yet,** neither self-service (Pathwise sends no e-mail) nor by an administrator. The `auth_tokens` table is ready for it. Until then, a forgotten password means an administrator suspends the old account and the person signs up again. Their progress doesn't carry over.

## Android

12. **The Android app covers the core student loop:** Today, practice, games, rooms, doubts and notifications.
    - The Map, modules, assessments, tutor, achievements and teaching tools are web-only.
    - It needs a connection; there is no offline queue.
    - It was verified by hand on an emulator, not on a physical phone, and has no automated UI tests.
    - There is no Play Store listing (that costs money) and no iOS app.

## Hosting and operations

13. **Hosting at ₹0 means self-hosting.** The service is up only while the host computer is on. The deployment guide is written but hasn't been carried out.
14. **Single server only.**
    - Rate limits and background jobs live in one process.
    - Running several servers would need shared state, which isn't built.
    - The embedded PostgreSQL is for development and demos.
15. **Not yet done:**
    - **Security:** no external penetration test, no dependency-vulnerability scan (it needs a download that wasn't possible here), and no load testing. See [SECURITY.md](SECURITY.md).
    - **Accessibility:** no audit with automated tools such as axe-core and no screen-reader pass. The pages follow semantic HTML with labels, landmarks and focus styles.
    - **Language:** the interface is English-only; there is no translation.

## Deferred from v1 (designed, not built)

These are designed in the blueprint but not built:
- group projects with teams and contribution logs
- room custom badges and opt-in leaderboards
- the Android offline queue
- a catalogue editor and a badge builder in the admin console
- publishing a live session's answered questions to the knowledge base
