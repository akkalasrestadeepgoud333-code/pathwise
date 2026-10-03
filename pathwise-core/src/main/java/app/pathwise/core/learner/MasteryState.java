package app.pathwise.core.learner;

import java.util.Locale;

/** States of the learner model for one skill, in increasing order of demonstrated understanding. */
public enum MasteryState {
    NOT_STARTED("Not started"),
    EXPLORING("Getting started"),
    NEEDS_SUPPORT("Needs practice"),
    DEVELOPING("Developing"),
    PROFICIENT("Proficient"),
    MASTERED("Mastered");

    private final String label;

    MasteryState(String label) {
        this.label = label;
    }

    /** Student-facing label (blueprint §12). */
    public String label() {
        return label;
    }

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String cssClass() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static MasteryState fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    public boolean atLeast(MasteryState other) {
        return ordinal() >= other.ordinal();
    }
}
