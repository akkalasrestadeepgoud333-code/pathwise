package app.pathwise.core.identity;

import java.util.Locale;

/** Global (platform-level) roles. Room roles (owner, co-teacher, student) are separate. */
public enum PlatformRole {
    STUDENT, TEACHER, MENTOR, ADMIN;

    /** Value stored in the database CHECK-constrained text column. */
    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static PlatformRole fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
