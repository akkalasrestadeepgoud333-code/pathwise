# Setup

Everything here is free. The commands are for Windows PowerShell. The Java parts also run on macOS and Linux with `./mvnw` and `java -jar`, but the helper scripts are PowerShell-only.

## 1. Requirements

| What | Needed for | Notes |
|---|---|---|
| JDK 21 or newer | everything | Tested with Temurin JDK 25. The code compiles to Java 21. |
| Git | recommended | |
| Node.js 18+ | optional | Lets the build run the JavaScript game-rule parity check. Without it, that test is skipped. |
| Android Studio | only for the Android app | Supplies the Android SDK (platform 34) and its own JDK, which the APK build uses. |
| Ollama | only for the optional AI tutor | Pull a small model, e.g. `ollama pull llama3.2:3b`. Without it the tutor uses Guided Help. |

Maven isn't installed separately; the Maven Wrapper (`mvnw.cmd`) downloads it. PostgreSQL isn't installed either: an embedded PostgreSQL is downloaded by the first build (about 30 MB) and kept under `data\pg`.

Run `.\scripts\doctor.ps1` at any time to check the machine. It changes nothing.

## 2. First run

```powershell
.\scripts\setup.ps1 -Demo
.\scripts\dev.ps1
```

Then open http://localhost:8080.

`setup.ps1` is safe to re-run. It does five things:
1. Builds every module.
2. Writes `config\pathwise.local.properties` with a random secret, only if the file is missing.
3. Starts the embedded database once.
4. Applies migrations and imports the bundled content pack.
5. With `-Demo`, creates two students and a teacher. Their random passwords are printed and saved to `config\demo-accounts.txt`, which is git-ignored.

To create an administrator, run:

```powershell
java -jar pathwise-app\target\pathwise-app.jar create-admin --email you@example.com --name "Your Name"
```

It asks for the password twice. For scripted use, set `PATHWISE_ADMIN_PASSWORD` instead. Demo seeding never creates admins.

## 3. Everyday commands

| Command | What it does |
|---|---|
| `.\scripts\dev.ps1` | Start at http://localhost:8080. JSP, CSS and JS changes apply on refresh. |
| `.\scripts\dev.ps1 -Build` | Rebuild Java code, then start |
| `.\scripts\dev.ps1 -Port 8081` | Start on another port |
| `.\mvnw.cmd verify` | Build and run every test (about 15 minutes on a laptop; most of that is the end-to-end tests) |
| `.\mvnw.cmd -q install -DskipTests` | Fast build |
| `java -jar pathwise-app\target\pathwise-app.jar import-content --pack data-structures` | Re-import the content pack after editing its YAML (`--force` re-imports an unchanged version) |
| `java -jar pathwise-app\target\pathwise-app.jar migrate` | Apply database migrations only |

## 4. Configuration

`config\pathwise.local.properties` is generated on first run. Any key can be overridden with an environment variable: upper-case it and replace dots with underscores, so `pathwise.db.url` becomes `PATHWISE_DB_URL`.

| Key | Default | Meaning |
|---|---|---|
| `pathwise.env` | `dev` | `dev`, `test` or `production`. Production requires a secret of 32+ characters and turns on secure cookies and HSTS. |
| `pathwise.secret` | random | Server secret |
| `pathwise.db.mode` | `embedded` | `embedded` (PostgreSQL under `data\pg`) or `external` |
| `pathwise.db.url` / `.user` / `.password` / `.pool` | embedded / 10 | JDBC settings and pool size for `external` mode |
| `pathwise.http.host` / `.port` | `127.0.0.1` / `8080` | Where the server listens |
| `pathwise.trust.proxy` | `false` | Set `true` behind Cloudflare Tunnel or another proxy, so client IPs are read from forwarded headers |
| `pathwise.cookies.secure` | `true` in production | Secure session cookie |
| `pathwise.session.web.hours` | `12` | Web session lifetime |
| `pathwise.session.bearer.days` | `30` | App token lifetime |
| `pathwise.ratelimit.login.per.minute` | `5` | Sign-in attempts per IP and email |
| `pathwise.ratelimit.register.per.hour` | `10` | Sign-ups per IP |
| `pathwise.storage.dir` | `storage` | Where uploaded PDFs are kept (git-ignored). Back it up together with the database. |
| `pathwise.ai.provider` | `none` | `none` (Guided Help only) or `ollama` |
| `pathwise.ai.model` | `llama3.2:3b` | Ollama model name |
| `pathwise.ai.url` | `http://127.0.0.1:11434` | Ollama address |
| `pathwise.ai.timeout.seconds` | `60` | Give up on the model and use Guided Help after this |

Whether new sign-ups are open is a live setting under **Admin → Settings**, not a config key.

## 5. Android app

1. Install Android Studio. In its SDK Manager, install **Android SDK Platform 34**.
2. Build the app and run its unit tests:

   ```powershell
   .\scripts\build-apk.ps1            # build the debug APK and run the unit tests
   .\scripts\build-apk.ps1 -Install   # also install on a running emulator or a USB-connected phone (adb)
   ```

   The script uses Android Studio's bundled JDK for Gradle, because Gradle 8.7 doesn't run on JDK 25. It writes `android\local.properties` (machine-specific, not committed).

3. Connect the app to a server:
   - **Emulator:** the app's default server is `http://10.0.2.2:8080`, which is how the emulator reaches your computer.
   - **Phone on the same Wi-Fi:** start the server with `.\scripts\dev.ps1` after setting `pathwise.http.host=0.0.0.0` in the config. Then enter `http://<your-PC's-LAN-IP>:8080` on the app's sign-in screen, and allow Java through Windows Firewall for private networks when asked.
   - **Anywhere else:** use an https tunnel address (see [DEPLOYMENT.md](DEPLOYMENT.md)).

   The app warns before signing in over plain http to a server that isn't local.

## 6. Troubleshooting

| Symptom | Fix |
|---|---|
| `doctor` says the JDK is too old | Install a JDK 21+ and make sure `java -version` shows it |
| The first build hangs at "Downloading embedded PostgreSQL" | The binaries come from Maven Central. Check the network or proxy; after one success they're cached in `~\.m2`. |
| Port 8080 is in use | `.\scripts\dev.ps1 -Port 8081` |
| A page shows old behaviour after a Java change | Restart with `.\scripts\dev.ps1 -Build`. The server runs the last built jar. |
| The tutor always says "Guided Help" | Expected unless `pathwise.ai.provider=ollama` and Ollama is running with the model pulled |
| The Android build says the SDK isn't found | Install platform 34 in Android Studio's SDK Manager, then re-run `build-apk.ps1` |
| The app can't reach the server from a phone | Check that the server listens on `0.0.0.0`, that the firewall allows it, and that phone and PC share a network |
