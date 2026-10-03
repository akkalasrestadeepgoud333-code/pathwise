package app.pathwise.web.platform.view;

import app.pathwise.core.identity.PlatformRole;
import app.pathwise.core.security.Actor;

import java.util.Locale;

/**
 * JavaBean exposed to JSPs as {@code currentUser}. Contains only what the shell needs to render;
 * it never carries ids, emails or tokens.
 */
public final class CurrentUser {
    private final String displayName;
    private final String firstName;
    private final String initials;
    private final boolean teacher;
    private final boolean admin;
    private final String theme;

    private CurrentUser(String displayName, boolean teacher, boolean admin, String theme) {
        this.displayName = displayName;
        String trimmed = displayName == null ? "" : displayName.trim();
        this.firstName = trimmed.isEmpty() ? "there" : trimmed.split("\\s+")[0];
        this.initials = initialsOf(trimmed);
        this.teacher = teacher;
        this.admin = admin;
        this.theme = theme;
    }

    public static CurrentUser of(Actor actor, String theme) {
        return new CurrentUser(actor.displayName(), actor.hasRole(PlatformRole.TEACHER),
                actor.hasRole(PlatformRole.ADMIN), theme);
    }

    static String initialsOf(String name) {
        if (name.isEmpty()) {
            return "?";
        }
        String[] parts = name.split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase(Locale.ROOT);
    }

    public String getDisplayName() { return displayName; }
    public String getFirstName() { return firstName; }
    public String getInitials() { return initials; }
    public boolean isTeacher() { return teacher; }
    public boolean isAdmin() { return admin; }
    /** "light", "dark" or "system". */
    public String getTheme() { return theme; }
}
