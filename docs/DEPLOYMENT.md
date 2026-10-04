# Deployment (₹0)

> **Status:** this guide is written but has not been carried out. No public deployment of Pathwise exists. Each step uses free software or a free tier, as of October 2026.

Pathwise is one Java process plus PostgreSQL, so the guaranteed ₹0 path is to **self-host on a computer you have and expose it through a free tunnel**. The site is then available only while that computer is on and running Pathwise.

## 1. Prepare the machine

1. Install a JDK 21+.
2. Install **PostgreSQL 16**, which is free. The embedded database is for development and demos. Then create a database and user:

   ```sql
   create user pathwise with password '<a long random password>';
   create database pathwise owner pathwise;
   ```

3. Build Pathwise: `.\mvnw.cmd -q install -DskipTests`.

## 2. Configure production

In `config\pathwise.local.properties`, or as environment variables:

```properties
pathwise.env=production
pathwise.secret=<64 random characters>
pathwise.db.mode=external
pathwise.db.url=jdbc:postgresql://127.0.0.1:5432/pathwise
pathwise.db.user=pathwise
pathwise.db.password=<the password above>
pathwise.http.host=127.0.0.1
pathwise.http.port=8080
pathwise.trust.proxy=true
```

What production mode changes:
- It refuses to start with a short secret.
- Cookies are `Secure` and HSTS is sent.
- Demo seeding is refused (unless forced).

Keep `http.host=127.0.0.1`: the tunnel connects locally, so nothing else needs to reach the port.

Then set up the database and the first administrator:

```powershell
java -jar pathwise-app\target\pathwise-app.jar migrate
java -jar pathwise-app\target\pathwise-app.jar import-content --pack data-structures
java -jar pathwise-app\target\pathwise-app.jar create-admin --email you@example.com --name "Your Name"
java -jar pathwise-app\target\pathwise-app.jar serve
```

## 3. Expose it with Cloudflare Tunnel (free)

1. Install `cloudflared`. `pathwise doctor` reports whether it is present.
2. Choose a tunnel type:
   - **A quick tunnel** needs no account. Run:

     ```powershell
     cloudflared tunnel --url http://localhost:8080
     ```

     It prints an `https://<random>.trycloudflare.com` address. That address changes every time, so it suits a demo or a class session.
   - **A named tunnel** needs a free Cloudflare account and your own domain. It keeps a stable address. Follow Cloudflare's "Create a tunnel" guide.

Either way, visitors get https. `pathwise.trust.proxy=true` makes rate limits and audit entries use the visitor's real IP address, not the tunnel's.

**Before sharing the link:**
- Pathwise is now reachable from the internet. Only share it with the people who should use it.
- Consider closing sign-ups (**Admin → Settings**) and creating accounts for your class instead.
- Keep the machine and its JDK updated.

## 4. Point the Android app at it

On the app's sign-in screen, enter the tunnel's https address. The app works with any reachable server, and warns about plain http to non-local addresses.

## 5. Running it as a service (optional)

To keep Pathwise running after you sign out of Windows, wrap the `serve` command with a free service wrapper (for example WinSW), or run it from Task Scheduler at start-up. Do the same for `cloudflared` (`cloudflared service install` for named tunnels). Neither has been set up or tested here.

## 6. Backups and updates

- **Back up** with `pg_dump` on a schedule; see [DATABASE.md](DATABASE.md).
- **To update:**
  1. Pull the new code and rebuild.
  2. Stop Pathwise.
  3. Back up the database.
  4. Run `migrate`.
  5. Start Pathwise again.

  Migrations only move forward.

## Other free options, and their limits

- **A Linux VM on a free cloud tier** runs the same jar with an installed PostgreSQL. Free tiers change their terms, need a card on file, or sleep idle apps, so check the current terms. Pathwise has no vendor-specific code.
- **`serve --war path\to\pathwise-web.war`** runs the packaged WAR on the embedded Tomcat. The WAR can also go into any servlet container that supports Jakarta EE 10 (Tomcat 10.1+).
