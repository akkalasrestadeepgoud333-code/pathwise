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
 * Build-time XSS guard for views. JSP EL does not escape output, so every dynamic value must pass
 * through {@code <c:out>}, {@code fn:escapeXml} or a {@code pw:} tag (which escape internally).
 * Also bans scriptlets in tag files (web.xml already bans them in JSPs).
 */
class JspSafetyTest {
    private static final Path WEBAPP = Path.of("src/main/webapp");
    private static final Pattern COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);
    private static final Pattern DIRECTIVE = Pattern.compile("<%@.*?%>", Pattern.DOTALL);
    /** Custom-tag markup, including its attribute values (evaluated by the tag, not printed raw). */
    private static final Pattern TAG = Pattern.compile("</?(c|fn|fmt|pw|jsp):[^>]*?>", Pattern.DOTALL);
    private static final Pattern SCRIPTLET = Pattern.compile("<%(?![@-])");

    private static List<Path> views() throws IOException {
        try (Stream<Path> files = Files.walk(WEBAPP)) {
            return files.filter(p -> {
                String n = p.getFileName().toString();
                return n.endsWith(".jsp") || n.endsWith(".jspf") || n.endsWith(".tag");
            }).toList();
        }
    }

    @Test
    void viewsExist() throws IOException {
        assertThat(views()).hasSizeGreaterThan(10);
    }

    @Test
    void noScriptlets() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path p : views()) {
            String src = COMMENT.matcher(Files.readString(p, StandardCharsets.UTF_8)).replaceAll("");
            if (SCRIPTLET.matcher(src).find()) {
                violations.add(WEBAPP.relativize(p).toString());
            }
        }
        assertThat(violations).as("Java code in views").isEmpty();
    }

    @Test
    void noUnescapedExpressions() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path p : views()) {
            String src = Files.readString(p, StandardCharsets.UTF_8);
            src = COMMENT.matcher(src).replaceAll("");
            src = DIRECTIVE.matcher(src).replaceAll("");
            src = TAG.matcher(src).replaceAll("");
            Matcher m = Pattern.compile("[$#]\\{").matcher(src);
            while (m.find()) {
                String rest = src.substring(m.start(), Math.min(src.length(), m.start() + 60));
                if (!rest.startsWith("${fn:escapeXml(")) {
                    violations.add(WEBAPP.relativize(p) + ": " + rest.replace('\n', ' '));
                }
            }
        }
        assertThat(violations).as("Unescaped EL output (wrap in <c:out> or fn:escapeXml)").isEmpty();
    }

    @Test
    void noInlineStylesOrScripts() throws IOException {
        // The Content-Security-Policy blocks them, so they would silently break in the browser.
        List<String> violations = new ArrayList<>();
        Pattern inlineStyle = Pattern.compile("\\sstyle\\s*=", Pattern.CASE_INSENSITIVE);
        Pattern inlineScript = Pattern.compile("<script(?![^>]*\\bsrc=)[^>]*>", Pattern.CASE_INSENSITIVE);
        Pattern handler = Pattern.compile("\\son[a-z]+\\s*=", Pattern.CASE_INSENSITIVE);
        for (Path p : views()) {
            // Custom-tag attributes (e.g. pw:logo onInk) are not HTML attributes, so strip those tags first.
            String src = TAG.matcher(Files.readString(p, StandardCharsets.UTF_8)).replaceAll("");
            if (inlineStyle.matcher(src).find() || inlineScript.matcher(src).find() || handler.matcher(src).find()) {
                violations.add(WEBAPP.relativize(p).toString());
            }
        }
        assertThat(violations).isEmpty();
    }
}
