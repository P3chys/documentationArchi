package io.github.p3chys.documentationarchi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.doc.Link;
import io.github.p3chys.documentationarchi.doc.Property;
import io.github.p3chys.documentationarchi.doc.Relation;
import io.github.p3chys.documentationarchi.render.Exporter;
import io.github.p3chys.documentationarchi.render.Exporter.Format;
import io.github.p3chys.documentationarchi.text.KeywordRules;
import io.github.p3chys.documentationarchi.text.Texts;

/** The fixed texts of the output: built-in languages and single replacements. */
class LanguageTest {

    @TempDir
    Path folder;

    private String html(String rules) throws Exception {
        DocPage page = new DocPage("v", "View");
        page.notes.add("A note");
        page.properties.add(new Property("k", "v"));
        page.contents.add(new Link("a", "A"));
        page.entries.add(new Entry("a", "A", "Node", "", List.of(new Property("k", "v")), List.of(
                new Relation(true, "Serving Relationship", "", "", new Link("b", "B"), false)), List.of(new Link("v", "View"))));
        page.entries.add(new Entry("b", "B", "Node", "see [[asset:none.png]] [[ref:zzz]]", List.of(), List.of(), List.of()));
        new Exporter(new Doc("Model", List.of(page)), KeywordRules.parse(rules))
                .version("Model", null, LocalDateTime.of(2026, 1, 2, 3, 4)).export(folder, EnumSet.of(Format.HTML));
        return Files.readString(folder.resolve("html/index.html"));
    }

    @Test
    void englishIsTheDefault() throws Exception {
        assertThat(html("")).contains("<h2>Contents</h2>", "<h2>Notes</h2>", "<h2>Elements</h2>", "<th>Property</th><th>Value</th>",
                "<th>Relationship</th><th>Element</th><th>Description</th>", "Views: ", "[missing asset: none.png]",
                "[missing reference: zzz]", "Generated 2026-01-02 03:04 from Model");
    }

    @Test
    void czechIsChosenWithOneLine() throws Exception {
        assertThat(html("language cs")).contains("<h2>Obsah</h2>", "<h2>Poznámky</h2>", "<h2>Prvky</h2>",
                "<th>Vlastnost</th><th>Hodnota</th>", "<th>Vztah</th><th>Prvek</th><th>Popis</th>", "Pohledy: ",
                "[chybějící příloha: none.png]", "Vygenerováno 2026-01-02 03:04 z Model").doesNotContain("Contents");
    }

    @Test
    void aSingleTextCanBeReplacedInAnyLanguage() throws Exception {
        assertThat(html("language cs\ntext contents = Table of <contents>\ntext NOTES = Remarks")).contains(
                "<h2>Table of &lt;contents&gt;</h2>", "<h2>Remarks</h2>", "<h2>Prvky</h2>");
    }

    @Test
    void everyKeyHasAnEnglishAndACzechText() {
        for (String key : Texts.keys()) {
            assertThat(Texts.builtIn(key, "en")).isNotBlank();
            assertThat(Texts.builtIn(key, "cs")).isNotBlank();
        }
    }

    @Test
    void mistakesNameTheLineAndWhatIsKnown() {
        assertThatThrownBy(() -> KeywordRules.parse("bold X\nlanguage de")).hasMessageContaining("Line 2")
                .hasMessageContaining("unknown language").hasMessageContaining("en or cs");
        assertThatThrownBy(() -> KeywordRules.parse("text nothing = x")).hasMessageContaining("Line 1")
                .hasMessageContaining("unknown text").hasMessageContaining("contents");
        assertThatThrownBy(() -> KeywordRules.parse("text contents Table")).hasMessageContaining("text <key> = <replacement>");
        assertThatThrownBy(() -> KeywordRules.parse("text contents =")).hasMessageContaining("one line");
    }

    @Test
    void theRulesAreWrittenBackAndLaterLanguageWins() {
        KeywordRules rules = KeywordRules.parse("language cs\ntext contents = Index\nlanguage en");
        assertThat(rules.texts().get("notes")).isEqualTo("Notes");
        assertThat(rules.texts().get("contents")).isEqualTo("Index");
        assertThat(KeywordRules.parse(rules.format()).rules()).isEqualTo(rules.rules());
    }
}
