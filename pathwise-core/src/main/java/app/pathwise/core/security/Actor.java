package app.pathwise.core.security;

import app.pathwise.core.identity.PlatformRole;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Who is making a request. Every authorisation decision takes an Actor, never a raw user id.
 *
 * @param userId      null for anonymous visitors
 * @param sessionId   the {@code sessions} row backing this request (web or bearer)
 */
public record Actor(UUID userId, String displayName, Set<PlatformRole> roles, UUID sessionId) {

    private static final Actor ANONYMOUS = new Actor(null, null, Set.of(), null);

    public Actor {
        roles = roles.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(roles));
    }

    public static Actor anonymous() {
        return ANONYMOUS;
    }

    public boolean isAuthenticated() {
        return userId != null;
    }

    public boolean hasRole(PlatformRole role) {
        return roles.contains(role);
    }

    /** The landing area for this actor after login. */
    public String homePath() {
        if (hasRole(PlatformRole.ADMIN)) {
            return "/admin";
        }
        if (hasRole(PlatformRole.TEACHER)) {
            return "/teach";
        }
        return "/app/today";
    }
}
