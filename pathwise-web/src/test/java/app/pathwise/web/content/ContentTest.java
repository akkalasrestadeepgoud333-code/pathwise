package app.pathwise.web.content;

import app.pathwise.core.content.ContentPack;
import app.pathwise.core.content.PackValidator;
import app.pathwise.core.content.SectionKind;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The bundled content itself is tested: a broken pack must fail the build, not a classroom demo. */
class ContentTest {
    private final ContentPackLoader loader = new ContentPackLoader(ContentTest.class.getClassLoader());

    @Test
    void bundledPacksAreListedAndValid() {
        assertThat(loader.available()).contains("data-structures");
        for (String key : loader.available()) {
            ContentPackLoader.Loaded loaded = loader.load(key);
            assertThat(PackValidator.validate(loaded.pack())).as("problems in pack " + key).isEmpty();
            assertThat(loaded.checksum()).hasSize(64);
            assertThat(loader.load(key).checksum()).as("checksum is stable").isEqualTo(loaded.checksum());
        }
    }

    @Test
    void dataStructuresPackCoversEveryTopicWithARichModule() {
        ContentPack pack = loader.load("data-structures").pack();
        Set<String> topicsWithModules = new HashSet<>();
        for (ContentPack.ModuleDef m : pack.modules()) {
            topicsWithModules.add(m.topic());
            List<String> kinds = m.sectionsOrEmpty().stream()
                    .map(s -> SectionKind.parse(s.kind()).orElseThrow()).map(SectionKind::dbValue).toList();
            assertThat(kinds).as(m.key()).contains("overview", "summary", "flashcards");
            assertThat(m.objectivesOrEmpty()).as(m.key() + " objectives").isNotEmpty();
            assertThat(m.skillsOrEmpty()).as(m.key() + " skills").isNotEmpty();
        }
        assertThat(topicsWithModules).containsAll(pack.topics().stream().map(ContentPack.TopicDef::key).toList());
        // The Trees path is the polished demo path: it must include interactive visuals.
        assertThat(pack.modules()).filteredOn(m -> m.key().equals("tree-traversals")).singleElement()
                .satisfies(m -> assertThat(m.sectionsOrEmpty()).anyMatch(s -> "visual".equals(s.kind())));
    }

    @Test
    void unknownPacksAndBadKeysAreRejected() {
        assertThatThrownBy(() -> loader.load("no-such-pack")).isInstanceOf(ContentPackLoader.PackFormatException.class);
        assertThatThrownBy(() -> loader.load("../etc")).isInstanceOf(ContentPackLoader.PackFormatException.class);
    }

    @Test
    void markdownIsRenderedAndSanitised() {
        String html = Markdown.render("""
                # Title

                Some **bold** and `code`.

                <script>alert('x')</script>

                [safe](https://example.com) [evil](javascript:alert(1))

                ```java
                int x = 1;
                ```

                | a | b |
                |---|---|
                | 1 | 2 |
                """).getHtml();
        assertThat(html).contains("<h1>Title</h1>").contains("<strong>bold</strong>").contains("<code>code</code>");
        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
        assertThat(html).contains("href=\"https://example.com\"").contains("rel=\"noopener noreferrer\"");
        assertThat(html).doesNotContain("javascript:");
        assertThat(html).contains("<code class=\"language-java\">");
        assertThat(html).contains("<table>").contains("<td>1</td>");
    }

    @Test
    void inlineMarkdownDropsTheParagraphWrapper() {
        assertThat(Markdown.inline("A **queue** (FIFO).").getHtml()).isEqualTo("A <strong>queue</strong> (FIFO).");
        assertThat(Markdown.render("").getHtml()).isEmpty();
    }
}
