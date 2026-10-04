package app.pathwise.core.security;

import app.pathwise.core.identity.PlatformRole;

/** Default-deny platform policy. Anything not explicitly allowed here is forbidden. */
public final class PlatformPolicy {
    private PlatformPolicy() {}

    public static boolean can(Actor actor, PlatformAction action) {
        if (actor == null || !actor.isAuthenticated()) {
            return false;
        }
        return switch (action) {
            // Every signed-in person can learn; teachers and admins are learners too.
            case USE_STUDENT_AREA, MANAGE_OWN_ACCOUNT -> true;
            case USE_TEACHER_AREA, CREATE_ROOM -> actor.hasRole(PlatformRole.TEACHER) || actor.hasRole(PlatformRole.ADMIN);
            case USE_ADMIN_AREA, MANAGE_USERS, GRANT_ROLES, VIEW_AUDIT_LOG, MODERATE_COMMUNITY, MANAGE_PLATFORM -> actor.hasRole(PlatformRole.ADMIN);
            case HOST_LIVE_SESSION -> actor.hasRole(PlatformRole.TEACHER) || actor.hasRole(PlatformRole.MENTOR) || actor.hasRole(PlatformRole.ADMIN);
        };
    }

    /** Throws {@link AccessDeniedException} (or {@link NotAuthenticatedException} for visitors). */
    public static void require(Actor actor, PlatformAction action) {
        if (actor == null || !actor.isAuthenticated()) {
            throw new NotAuthenticatedException();
        }
        if (!can(actor, action)) {
            throw new AccessDeniedException(action.name());
        }
    }
}
