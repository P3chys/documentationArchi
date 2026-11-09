package io.github.p3chys.documentationarchi;

import static org.assertj.core.api.Assertions.assertThat;

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
import io.github.p3chys.documentationarchi.doc.ObjectText;
import io.github.p3chys.documentationarchi.doc.Relation;
import io.github.p3chys.documentationarchi.render.Exporter;
import io.github.p3chys.documentationarchi.render.Exporter.Format;
import io.github.p3chys.documentationarchi.text.KeywordRules;

/** What the pages say about junctions, sketches, specializations, groups, nesting and labels. */
class PageContentTest {

    @TempDir
    Path folder;

    private String html(DocPage... pages) throws Exception {
        new Exporter(new Doc("Model", List.of(pages)), KeywordRules.defaults()).export(folder, EnumSet.of(Format.HTML));
        return Files.readString(folder.resolve("html/index.html"));
    }

    private String storage(DocPage... pages) throws Exception {
        new Exporter(new Doc("Model", List.of(pages)), KeywordRules.defaults()).export(folder, EnumSet.of(Format.CONFLUENCE));
        return Files.readString(folder.resolve("confluence/pages/index.xml"));
    }

    @Test
    void relationshipThroughJunctionNamesAllElementsWithAndOrOr() throws Exception {
        DocPage page = new DocPage("v", "View");
        page.entries.add(new Entry("a", "A", "Process", "", List.of(), List.of(
                new Relation(true, "Triggering Relationship", "", "", List.of(new Link("b", "B"), new Link("c", "C")), "or", false),
                new Relation(false, "Flow Relationship", "", "", List.of(new Link("x", "X"), new Link("y", "Y")), "and", true)),
                List.of()));
        page.entries.add(new Entry("b", "B", "Process", "", List.of(), List.of(), List.of()));
        page.entries.add(new Entry("c", "C", "Process", "", List.of(), List.of(), List.of()));

        assertThat(html(page)).contains("<td><a href=\"#e-b\">B</a> or <a href=\"#e-c\">C</a></td>")
                .contains("<li>← X and Y");
    }

    @Test
    void textsOfSketchObjectsAreListedWithTheNotes() throws Exception {
        DocPage page = new DocPage("v", "Sketch");
        page.texts.add(new ObjectText("Goal", "Sell more #todo"));
        page.texts.add(new ObjectText("", "No name"));

        assertThat(html(page)).contains("<h2>Notes</h2><p><strong>Goal</strong></p><p>Sell more <span class=\"tag yellow\">"
                + "TODO</span></p><p>No name</p>");
    }

    @Test
    void groupsOfTheViewHeadTheirElementsInContentsAndElements() throws Exception {
        DocPage page = new DocPage("v", "View");
        for (String name : List.of("A", "B", "C")) {
            page.contents.add(new Link(name, name));
            page.entries.add(new Entry(name, name, "Node", "", List.of(), List.of(), List.of()));
        }
        page.groups.put("B", "Zone");
        page.groups.put("C", "Zone");
        page.entries.get(0).partOf.add(new Link("B", "B"));
        page.entries.get(1).contains.add(new Link("A", "A"));

        String html = html(page);

        assertThat(html).contains("<h2>Contents</h2><ul><li><a href=\"#e-A\">A</a></li></ul><h3>Zone</h3><ul>"
                + "<li><a href=\"#e-B\">B</a></li><li><a href=\"#e-C\">C</a></li></ul>")
                .contains("<p>Part of: <a href=\"#e-B\">B</a></p>").contains("<p>Contains: <a href=\"#e-A\">A</a></p>");
        assertThat(html.split("<h3>Zone</h3>", -1)).hasSize(3); // once in the contents, once above B's entry
        assertThat(html).containsSubsequence("<h3>Zone</h3>", "<h3>Zone</h3><h3 id=\"e-B\">", "<h3 id=\"e-C\">");
    }
}
