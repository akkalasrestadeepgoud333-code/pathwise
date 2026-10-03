package app.pathwise.web.identity;

import app.pathwise.core.identity.PlatformRole;
import app.pathwise.core.security.Actor;
import app.pathwise.data.identity.UserRecord;
import app.pathwise.web.platform.http.ApiServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** {@code GET /api/v1/me}: the signed-in user's own account summary. */
public class MeApiServlet extends ApiServlet {

    public record Me(UUID id, String displayName, String email, List<String> roles) {
        static Me of(Actor actor, String email) {
            List<String> roles = actor.roles().stream().map(PlatformRole::dbValue).sorted().toList();
            return new Me(actor.userId(), actor.displayName(), email.toLowerCase(Locale.ROOT), roles);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        Actor actor = requireActor(req);
        UserRecord user = app().identity().findUser(actor.userId()).orElseThrow();
        writeJson(res, 200, Me.of(actor, user.email()));
    }
}
