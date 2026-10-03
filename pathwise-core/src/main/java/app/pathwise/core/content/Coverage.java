package app.pathwise.core.content;

/**
 * How much of a topic's learning material a student has studied. This is coverage, not mastery:
 * finishing the reading does not mean the skill is learned. Mastery comes from evidence (M2).
 */
public enum Coverage {
    /** The topic has no published modules yet. */
    NO_CONTENT,
    NOT_STARTED,
    IN_PROGRESS,
    STUDIED;

    public static Coverage of(int modules, int started, int completed) {
        if (modules == 0) {
            return NO_CONTENT;
        }
        if (completed >= modules) {
            return STUDIED;
        }
        if (started > 0 || completed > 0) {
            return IN_PROGRESS;
        }
        return NOT_STARTED;
    }

    public String label() {
        return switch (this) {
            case NO_CONTENT -> "Coming soon";
            case NOT_STARTED -> "Not started";
            case IN_PROGRESS -> "In progress";
            case STUDIED -> "Studied";
        };
    }

    public String cssClass() {
        return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
