package app.pathwise.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Static accessibility checks over every view, run on each build. They catch the commonest WCAG failures
 * (unlabelled form fields, images without alt, nameless buttons, missing page language). They are not a substitute
 * for an audit with real assistive technology or a rendered-page tool such as axe-core.
 */
class AccessibilityLintTest {
    private static final Path WEBAPP = Path.of("src/main/webapp");
    private static final Pattern COMMENT = Pattern.compile("<%--.*?--%>|<!--.*?-->", Pattern.DOTALL);
    private static final Pattern FIELD = Pattern.compile("<(input|select|textarea)\\b([^>]*)>", Pattern.DOTALL);
    private static final Pattern IMG = Pattern.compile("<img\\b([^>]*)>", Pattern.DOTALL);
    private static final Pattern BUTTON = Pattern.compile("<button\\b([^>]*)>(.*?)</button>", Pattern.DOTALL);
    private static final Pattern ID = Pattern.compile("\\bid=\"([^\"]+)\"");

    private static List<Path> views() throws IOException {
        try (Stream<Path> files = Files.walk(WEBAPP)) {
            return files.filter(p -> p.toString().endsWith(".jsp") || p.toString().endsWith(".tag")).toList();
        }
    }

    /** Self-closing JSP tags inside attribute values (e.g. {@code id="t-<c:out .../>"}), collapsed so attributes parse. */
    private static final Pattern INLINE_TAG = Pattern.compile("<(c|fn|fmt):[^<>]*?/>");

    private static String source(Path p) throws IOException {
        String src = COMMENT.matcher(Files.readString(p, StandardCharsets.UTF_8)).replaceAll("");
        return INLINE_TAG.matcher(src).replaceAll("§");
    }

    /** True if the position sits inside an open {@code <label>} element. */
    private static boolean insideLabel(String src, int pos) {
        String before = src.substring(0, pos);
        return before.lastIndexOf("<label") > before.lastIndexOf("</label>");
    }

    @Test
    void everyFormFieldHasALabel() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path p : views()) {
            String src = source(p);
            Matcher m = FIELD.matcher(src);
            while (m.find()) {
                String attrs = m.group(2);
                if (attrs.matches("(?s).*\\btype=\"(hidden|submit|button)\".*")) {
                    continue;
                }
                if (attrs.contains("aria-label") || insideLabel(src, m.start())) {
                    continue;
                }
                Matcher id = ID.matcher(attrs);
                if (id.find() && src.contains("for=\"" + id.group(1) + "\"")) {
                    continue;
                }
                violations.add(WEBAPP.relativize(p) + ": <" + m.group(1) + attrs.strip().replaceAll("\\s+", " ") + ">");
            }
        }
        assertThat(violations).as("form fields without a label, aria-label or aria-labelledby").isEmpty();
    }

    @Test
    void everyImageHasAltText() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path p : views()) {
            Matcher m = IMG.matcher(source(p));
            while (m.find()) {
                if (!m.group(1).contains("alt=")) {
                    violations.add(WEBAPP.relativize(p) + ": <img" + m.group(1) + ">");
                }
            }
        }
        assertThat(violations).as("images without alt").isEmpty();
    }

    @Test
    void everyButtonHasAName() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path p : views()) {
            Matcher m = BUTTON.matcher(source(p));
            while (m.find()) {
                String attrs = m.group(1);
                String content = m.group(2);
                boolean text = !content.replaceAll("<[^>]*>", "").isBlank();
                boolean labelledIcon = content.matches("(?s).*<pw:icon[^>]*\\blabel=.*");
                if (!text && !labelledIcon && !attrs.contains("aria-label")) {
                    violations.add(WEBAPP.relativize(p) + ": <button" + attrs.strip().replaceAll("\\s+", " ") + ">");
                }
            }
        }
        assertThat(violations).as("buttons with no accessible name").isEmpty();
    }

    @Test
    void pagesDeclareTheirLanguage() throws IOException {
        String document = Files.readString(WEBAPP.resolve("WEB-INF/tags/pw/document.tag"), StandardCharsets.UTF_8);
        assertThat(document).containsPattern("<html lang=\"[a-z]{2}");
    }
}
