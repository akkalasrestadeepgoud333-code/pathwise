# Security

This file describes the security controls in Pathwise as built, and the review done in M11 (2026-10-04). It lists only what is in the code. Where something was not done, it says so.

## Controls

### Accounts and sessions
- **Passwords** are hashed with Argon2id (m = 19 MiB, t = 2, p = 1, the OWASP baseline) as PHC strings. Hashes are upgraded on sign-in if the parameters are raised. The password policy refuses short or overlong passwords, common passwords, passwords made of too few different characters, and passwords containing the name part of the user's email.
- **Unknown emails** still run a hash check against a dummy hash. That way response time doesn't reveal which accounts exist. Failed sign-ins are audited.
- **Web sessions:**
  - The session id is changed on sign-in, which prevents session fixation.
  - The cookie is `HttpOnly`. It is `Secure` in production; `pathwise.cookies.secure` overrides this.
  - Sessions expire after 12 hours idle (`pathwise.session.web.hours`).
- **App sessions** use opaque 256-bit bearer tokens.
  - Only their SHA-256 hash is stored.
  - They last 30 days (`pathwise.session.bearer.days`).
  - On the phone, the token is kept in the app's private preferences.
- **Every request re-checks the account.** A suspended or deleted account is refused even with a valid session or token. Suspension also revokes all open sessions.
- **Sensitive admin actions** need the password re-entered within the last 15 minutes. These are role changes, suspensions, and revealing who is behind a doubt alias.

### Requests
- **CSRF:** a synchronizer token per session is checked on every non-GET request that uses a cookie. It travels as the `_csrf` form field or the `X-CSRF-Token` header. Bearer-token API calls are exempt, because browsers never attach bearer tokens automatically.
- **Response headers:**
  - `Content-Security-Policy`:
    - `default-src`, `script-src`, `style-src`, `font-src`, `connect-src` and `base-uri` are all `'self'`
    - `img-src 'self' data:`
    - `object-src 'none'`
    - `form-action 'self'`
    - `frame-ancestors 'none'`
    - There are no inline scripts or styles anywhere, and the build checks every JSP for this.
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: DENY`
  - `Referrer-Policy: strict-origin-when-cross-origin`
  - `Cross-Origin-Opener-Policy: same-origin`
  - A restrictive `Permissions-Policy`
  - HSTS in production
- **Output encoding:** every dynamic value in a JSP goes through `<c:out>` or `c:url`. `JspSafetyTest` fails the build on raw `${...}` in markup, on scriptlets and on inline handlers. Module content is authored as Markdown in content packs, and the HTML it produces passes through the OWASP Java HTML Sanitizer allow-list (`Markdown`). Doubts, answers, live-session questions and tutor messages are always shown as plain text.
- **SQL:** every query is a `PreparedStatement` with bound parameters. The M11 review searched for string-built SQL and found one case: a constant database name in the embedded-database bootstrap, not user input.
- **Redirects:** post-login and re-authentication destinations must be same-site relative paths (`safeNext`, `SafeNextTest`). Notification links must start with `/`; the database enforces this with a CHECK constraint.
- **CSV exports** quote every cell and neutralise leading `= + - @`, which blocks formula injection.
- **Rate limits** are in-process token buckets:

  | Action | Limit |
  |---|---|
  | Sign-in | 5 per minute per IP and email |
  | Password re-entry | 5 per minute per admin |
  | Registration | 10 per hour per IP |
  | New doubts | 10 per hour |
  | Doubt answers | 30 per 10 minutes |
  | Tutor messages | 20 per 10 minutes |
  | Live-session questions | at most 3 waiting per person |

### Authorization
- **Default-deny platform policy** (`PlatformPolicy`), with a test of the whole matrix.
- **Room permissions** come from a separate matrix (`RoomPolicy`), with per-permission co-teacher toggles.
- **Resource ownership** (attempts, conversations, notifications, drafts) is checked in services. Someone else's resource gives a 404, not a 403, so ids can't be probed. The flow tests cover these cases.
- **Administrators have no special view of a room's people or work.** The admin rooms page shows head counts only.
- **Anonymity:**
  - View records for doubts and live sessions carry no user ids or names.
  - Tests scan pages and the API for leaked names, emails and ids.
  - Revealing an alias is audited and needs a reason plus a password re-entry.

### Games, assessments and AI
- **Games:** the server replays every game's action log before recording anything. It rejects impossible moves, too-fast play and forged scores. Game evidence is informal and capped.
- **Assessments** never send answers or rationales to the browser before grading. Deadlines are enforced on the server, which auto-submits overdue attempts.
- **The AI tutor:**
  - Pathwise content goes inside a delimited `<context>` block as data.
  - Hints that leak an answer are replaced.
  - It is switched off during assessments whose policy blocks it.
  - It can only reach the model URL set in the server's config, and only the local Ollama by default.

### Android
- **No permission is requested without a reason.** Only `INTERNET` and `POST_NOTIFICATIONS` are declared. The notification permission is asked for once, after an in-app explanation. A "no" is respected.
- **The game WebView** loads only the APK's own assets from a private https origin. File and content access are off, and navigation away is blocked. The JavaScript bridge exposes four methods: puzzle, template, minimum gap, and finish.
- **Plain http** is allowed so a student can reach a server on their own computer or LAN. The sign-in screen warns when a non-local server isn't https.

### File uploads (teachers' PDFs)
- **Who can upload:** only a room's owner, or a co-teacher with the author permission. Any member can download; anyone else gets 404.
- **What is accepted:** the file must start with `%PDF-`, whatever its name or declared type, and be at most 10 MB. The bytes actually read are counted, so a false size is caught.
- **Storage:**
  - Files are written under `pathwise.storage.dir`, outside the web root, as `<uuid>.pdf`.
  - The uploaded name is only used for display and as the download's suggested name, after path parts and control characters are stripped.
  - Paths are resolved and checked to stay inside the storage folder.
- **Serving:** downloads go out as `application/pdf` with `Content-Security-Policy: sandbox` and `nosniff`, so a hostile PDF can't run as part of the site.
- **Not done:** no antivirus scan and no storage quota per room.

### Audit
- `audit_log` is append-only. A database trigger rejects updates and deletes.
- It records sign-ins and failures, password re-entries, role, status, trust and Verified Educator changes, settings, game templates, room archiving, moderation and live-session removals.
- Admins can browse it at `/admin/audit`.

## M11 review (2026-10-04)

What was checked, and how:
- **SQL construction:** searched all DAOs for concatenated SQL. None found (see above).
- **Output encoding and CSP:** `JspSafetyTest` passes over every JSP, including the new admin and live-session views.
- **Session fixation, cookie flags, headers, CSRF exemption rules:** read in code, and covered by `WebAuthFlowTest` and `ApiAuthTest`.
- **The new admin console:**
  - every page refuses students (403)
  - sensitive actions need re-authentication
  - the re-authentication redirect refuses off-site targets
  - an admin can't suspend themselves or remove their own admin role
  - the last administrator can't be removed
  - suspension ends both web and bearer sessions

  `AdminConsoleAndLiveFlowTest` covers all of these.
- **A bug found and fixed during M10:** an untyped null SQL parameter in the live-session queue query caused a server error (HTTP 500). It was caught by the flow test.

Not done:
- **OWASP Dependency-Check** was not run. It needs to download the NVD database, which wasn't possible on this machine.
- **External penetration test:** none.
- **Load or denial-of-service testing:** none.

The rate limits are per process and reset on restart. A multi-server deployment would need a shared store.

## Reporting a problem

Open an issue marked **security** on the project repository, or tell the project team directly. Please don't include working exploit details in a public issue.
