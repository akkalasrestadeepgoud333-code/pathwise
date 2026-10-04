package app.pathwise.core.rooms;

/**
 * Default-deny permissions inside a room (blueprint §18). Only active memberships count; platform admins have
 * no special access to a room's people or data.
 */
public final class RoomPolicy {
    private RoomPolicy() {}

    public enum Role {
        OWNER, CO_TEACHER, STUDENT;

        public String dbValue() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public static Role fromDb(String v) {
            return valueOf(v.toUpperCase(java.util.Locale.ROOT));
        }
    }

    /** Toggles a room owner grants each co-teacher. */
    public record Perms(boolean author, boolean publish, boolean grade, boolean moderate, boolean analytics) {
        public static final Perms NONE = new Perms(false, false, false, false, false);
        public static final Perms DEFAULT_CO_TEACHER = new Perms(true, false, true, true, true);
    }

    /** The caller's membership in the room, or null if they have none. {@code active} is false for pending/removed. */
    public record Membership(Role role, boolean active, Perms perms) {}

    /** Room facts that change what is allowed. */
    public record RoomState(boolean archived, boolean studentsSeeClassStats) {}

    public enum Action {
        VIEW_ROOM,
        EDIT_SETTINGS,
        ARCHIVE,
        MANAGE_STUDENTS,
        MANAGE_CO_TEACHERS,
        AUTHOR,
        PUBLISH,
        GRADE,
        MODERATE,
        VIEW_INDIVIDUAL_ANALYTICS,
        VIEW_CLASS_AGGREGATES,
        POST_ANNOUNCEMENT,
        DO_ROOM_WORK,
        USE_TEACHER_VIEW
    }

    public static boolean can(Membership m, RoomState room, Action action) {
        if (m == null || !m.active()) {
            return false;
        }
        boolean teacher = m.role() == Role.OWNER || m.role() == Role.CO_TEACHER;
        Perms p = m.perms() == null ? Perms.NONE : m.perms();
        // An archived room is read-only for everyone; only the owner may change settings (e.g. to restore it).
        if (room.archived()) {
            return switch (action) {
                case VIEW_ROOM -> true;
                case USE_TEACHER_VIEW -> teacher;
                case VIEW_CLASS_AGGREGATES -> teacher || room.studentsSeeClassStats();
                case VIEW_INDIVIDUAL_ANALYTICS -> m.role() == Role.OWNER || (m.role() == Role.CO_TEACHER && p.analytics());
                case EDIT_SETTINGS -> m.role() == Role.OWNER;
                default -> false;
            };
        }
        return switch (action) {
            case VIEW_ROOM -> true;
            case USE_TEACHER_VIEW, MANAGE_STUDENTS, POST_ANNOUNCEMENT -> teacher;
            case EDIT_SETTINGS, ARCHIVE, MANAGE_CO_TEACHERS -> m.role() == Role.OWNER;
            case AUTHOR -> m.role() == Role.OWNER || (m.role() == Role.CO_TEACHER && p.author());
            case PUBLISH -> m.role() == Role.OWNER || (m.role() == Role.CO_TEACHER && p.publish());
            case GRADE -> m.role() == Role.OWNER || (m.role() == Role.CO_TEACHER && p.grade());
            case MODERATE -> m.role() == Role.OWNER || (m.role() == Role.CO_TEACHER && p.moderate());
            case VIEW_INDIVIDUAL_ANALYTICS -> m.role() == Role.OWNER || (m.role() == Role.CO_TEACHER && p.analytics());
            case VIEW_CLASS_AGGREGATES -> teacher || room.studentsSeeClassStats();
            case DO_ROOM_WORK -> m.role() == Role.STUDENT;
        };
    }

    /** Class statistics shown to students are hidden below this many students, so no one can be singled out. */
    public static final int MIN_GROUP_FOR_AGGREGATES = 5;
}
