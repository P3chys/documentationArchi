package io.github.p3chys.documentationarchi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
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

/** The page and entry lines of the rules: which sections are printed, and in which order. */
class PageTemplateTest {

    @TempDir
    Path folder;

    private String html(String rules) throws Exception {
        DocPage page = new DocPage("v", "View");
        page.documentation = "VIEWDOC";
        page.notes.add("ANOTE");
        page.properties.add(new Property("pk", "pv"));
        page.contents.add(new Link("a", "A"));
        page.relatedViews.add(new Link("w", "Other view"));
        page.entries.add(new Entry("a", "A", "Node", "ELEMDOC", List.of(new Property("ek", "ev")), List.of(
                new Relation(true, "Serving Relationship", "", "", new Link("b", "B"), false),
                new Relation(true, "Flow Relationship", "", "", new Link("z", "Z"), true)), List.of(new Link("v", "View"))));
        page.entries.add(new Entry("b", "B", "Node", "", List.of(), List.of(), List.of()));
        new Exporter(new Doc("Model", List.of(page)), KeywordRules.parse(rules)).export(folder, EnumSet.of(Format.HTML));
        return Files.readString(folder.resolve("html/index.html"));
    }

    private static void inOrder(String text, String... parts) {
        int at = -1;
        for (String part : parts) {
            int next = text.indexOf(part, at + 1);
            assertThat(next).as(part).isGreaterThan(at);
            at = next;
        }
    }

    @Test
    void withoutTheLinesTheOutputIsAsBefore() throws Exception {
        String usual = html("");
        inOrder(usual, "VIEWDOC", "<h2>Contents</h2>", "<h2>Notes</h2>", "ANOTE", "<h2>Related views</h2>",
                "<h2>Elements</h2>", "ELEMDOC", "<strong>ek:</strong>", "Serving Relationship", "Views: ", "Other related elements");
        assertThat(usual).contains("<ul class=\"properties\"><li><strong>pk:</strong> pv</li></ul>");
    }

    @Test
    void pageSectionsAreFewerAndInTheOrderGiven() throws Exception {
        String page = html("page elements description notes");

        inOrder(page, "<h2>Elements</h2>", "VIEWDOC", "<h2>Notes</h2>");
        assertThat(page).doesNotContain("<h2>Contents</h2>", "Related views", "<strong>pk:</strong>");
    }

    @Test
    void entryPartsAreFewerAndInTheOrderGiven() throws Exception {
        String page = html("entry views description");

        inOrder(page, "Views: ", "ELEMDOC");
        assertThat(page).doesNotContain("<strong>ek:</strong>", "Serving Relationship", "Other related elements");
    }

    @Test
    void relationsAndOtherRelatedAreSeparateParts() throws Exception {
        assertThat(html("entry relations")).contains("Serving Relationship").doesNotContain("Other related elements");
        assertThat(html("entry other")).contains("Other related elements").doesNotContain("Serving Relationship");
    }

    @Test
    void mistakesAreReportedWithTheLine() {
        assertThatThrownBy(() -> KeywordRules.parse("bold X\npage description footer")).hasMessageContaining("Line 2")
                .hasMessageContaining("unknown section \"footer\"").hasMessageContaining("contents");
        assertThatThrownBy(() -> KeywordRules.parse("entry description tables")).hasMessageContaining("unknown part");
        assertThatThrownBy(() -> KeywordRules.parse("page notes notes")).hasMessageContaining("twice");
        assertThatThrownBy(() -> KeywordRules.parse("page")).hasMessageContaining("Line 1");
    }

    @Test
    void theLinesAreWrittenBackAndTheLastOneWins() {
        KeywordRules rules = KeywordRules.parse("page notes\nPAGE Picture Notes\nentry views");

        assertThat(rules.pageSections()).containsExactly("picture", "notes");
        assertThat(rules.entryParts()).containsExactly("views");
        assertThat(KeywordRules.parse(rules.format()).pageSections()).containsExactly("picture", "notes");
        assertThat(KeywordRules.defaults().pageSections()).isEqualTo(KeywordRules.PAGE_SECTIONS);
    }

    @Test
    void aPartCanBeLeftOutOfWhateverOrderTheRulesGive() {
        KeywordRules rules = KeywordRules.defaults().withoutEntryPart("views").withoutEntryPart("workpackages");
        assertThat(rules.entryParts()).containsExactly("description", "properties", "relations", "other");

        KeywordRules ordered = KeywordRules.parse("entry views description").withoutEntryPart("views");
        assertThat(ordered.entryParts()).containsExactly("description");
        // the last part stays: an element is never described with nothing
        assertThat(KeywordRules.parse("entry views").withoutEntryPart("views").entryParts()).containsExactly("views");
    }
}
