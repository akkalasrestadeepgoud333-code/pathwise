package app.pathwise.core.security;

/** Platform-level actions. Room- and resource-level actions get their own policies in later modules. */
public enum PlatformAction {
    USE_STUDENT_AREA,
    USE_TEACHER_AREA,
    CREATE_ROOM,
    USE_ADMIN_AREA,
    MANAGE_USERS,
    GRANT_ROLES,
    VIEW_AUDIT_LOG,
    MODERATE_COMMUNITY,
    /** Settings, the game registry and room oversight in the admin console. */
    MANAGE_PLATFORM,
    /** Hosting live doubt sessions: teachers, mentors and admins. */
    HOST_LIVE_SESSION,
    MANAGE_OWN_ACCOUNT
}
