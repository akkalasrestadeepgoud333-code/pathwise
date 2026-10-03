package app.pathwise.web.content;

/**
 * HTML that has passed the sanitiser. It is the only kind of value the {@code pw:html} tag will print
 * unescaped, and it can only be created by {@link Markdown}, so unsanitised HTML cannot reach a page by
 * accident.
 */
public final class SafeHtml {
    private final String html;

    SafeHtml(String html) {
        this.html = html;
    }

    public String getHtml() {
        return html;
    }

    @Override
    public String toString() {
        return html;
    }
}
