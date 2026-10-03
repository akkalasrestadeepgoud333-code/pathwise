package app.pathwise.web.content;

import app.pathwise.core.security.Tokens;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Markdown → sanitised HTML. Raw HTML in the source is escaped by the renderer, and the output then
 * passes an allow-list sanitiser (defence in depth for teacher-authored content later). Links open
 * safely; only https/http/mailto URLs survive.
 */
public final class Markdown {
    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
            .extensions(EXTENSIONS)
            .escapeHtml(true)
            .sanitizeUrls(true)
            .build();

    private static final PolicyFactory POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.TABLES)
            .and(new HtmlPolicyBuilder()
                    .allowElements("pre", "code", "hr", "br", "kbd", "sup", "sub")
                    .allowAttributes("class").matching(Pattern.compile("language-[a-z0-9+#-]{1,20}")).onElements("code")
                    .allowElements("a")
                    .allowAttributes("href").onElements("a")
                    .allowUrlProtocols("https", "http", "mailto")
                    .requireRelsOnLinks("noopener", "noreferrer")
                    .toFactory());

    private static final int CACHE_SIZE = 512;
    private static final Map<String, SafeHtml> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, SafeHtml> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    private Markdown() {}

    public static SafeHtml render(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return new SafeHtml("");
        }
        String key = Tokens.sha256Hex(markdown);
        synchronized (CACHE) {
            SafeHtml cached = CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }
        String html = RENDERER.render(PARSER.parse(markdown));
        SafeHtml safe = new SafeHtml(POLICY.sanitize(html));
        synchronized (CACHE) {
            CACHE.put(key, safe);
        }
        return safe;
    }

    /** Plain-text inline rendering for short strings (escaped by the view), e.g. flashcards. */
    public static SafeHtml inline(String text) {
        SafeHtml block = render(text);
        String html = block.getHtml().strip();
        if (html.startsWith("<p>") && html.endsWith("</p>") && html.indexOf("<p>", 3) < 0) {
            html = html.substring(3, html.length() - 4);
        }
        return new SafeHtml(html);
    }
}
