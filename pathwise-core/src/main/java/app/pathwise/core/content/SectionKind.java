package app.pathwise.core.content;

import java.util.Locale;
import java.util.Optional;

/** Module section types (M1 set). New kinds are added here, in the validator and as a JSP fragment. */
public enum SectionKind {
    OVERVIEW, EXPLANATION, EXAMPLE, VISUAL, FLASHCARDS, SUMMARY, RESOURCES, COMMON_DOUBTS;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean isText() {
        return this == OVERVIEW || this == EXPLANATION || this == EXAMPLE || this == SUMMARY;
    }

    public static Optional<SectionKind> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_')));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
