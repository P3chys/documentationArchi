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
import io.github.p3chys.documentationarchi.doc.Relation;
import io.github.p3chys.documentationarchi.render.Exporter;
import io.github.p3chys.documentationarchi.render.Exporter.Format;
import io.github.p3chys.documentationarchi.text.KeywordRules;

/** How the HaF conventions the model reader finds show on a page. */
class HafRenderingTest {

    @TempDir
    Path folder;

    private String html(Doc doc) throws Exception {
        new Exporter(doc, KeywordRules.defaults()).export(folder, EnumSet.of(Format.HTML));
        return Files.readString(folder.resolve("html/index.html"));
    }

    @Test
    void verbsKeepTheTypeAndShowTheOtherEndsBusinessDescription() throws Exception {
        DocPage page = new DocPage("v", "View");
        page.entries.add(new Entry("a", "A", "Business Process", "", List.of(), List.of(
                new Relation(true, "Triggering Relationship: Sync", "go", "", List.of(new Link("b", "B")), null, false,
                        "triggers", "Signs the *contract*."),
                new Relation(false, "Serving Relationship", "", "", List.of(new Link("x", "X")), null, true,
                        "use", "Gives prices.")), List.of()));
        page.entries.add(new Entry("b", "B", "Business Process", "", List.of(), List.of(), List.of()));

        String html = html(new Doc("Model", List.of(page)));

        assertThat(html).contains("<td>→ triggers<br/><span style=\"color: rgb(107,119,140);\">Triggering Relationship: "
                + "Sync</span><br/>go</td>")
                .contains("<p><em><span style=\"color: rgb(107,119,140);\">(Signs the <em>contract</em>.)</span></em></p>")
                .contains("<li>← use X<p><em><span style=\"color: rgb(107,119,140);\">(Gives prices.)</span></em></p></li>");
    }

    @Test
    void factsAndSectionsFollowTheDescription() throws Exception {
        DocPage page = new DocPage("v", "View");
        Entry entry = new Entry("a", "A", "Application Function", "Does it.", List.of(), List.of(), List.of());
        entry.facts.add("Available only for role DEV and higher");
        entry.sections.add(new Entry.Section("Input parameters", "- id"));
        page.entries.add(entry);

        assertThat(html(new Doc("Model", List.of(page)))).contains("<p>Does it.</p><p>Available only for role DEV and "
                + "higher</p><h4>Input parameters</h4><ul><li>id</li></ul>");
    }

    @Test
    void elementsWithAConfluencePageOrTicketLinkThere() throws Exception {
        DocPage page = new DocPage("v", "View");
        Entry ticket = new Entry("wp", "ABC-1", "Work Package", "See [[ref:c]].", List.of(), List.of(), List.of());
        ticket.headingUrl = "https://jira.example.com/browse/ABC-1";
        page.entries.add(ticket);
        page.entries.add(new Entry("c", "C", "Node", "", List.of(), List.of(
                new Relation(true, "Association Relationship", "", "", new Link("d", "D", "Node"), false)), List.of()));
        page.entries.add(new Entry("d", "D", "Node", "", List.of(), List.of(), List.of()));
        Doc doc = new Doc("Model", List.of(page));
        doc.names.put("c", "C");
        doc.external.put("c", "https://wiki.example.com/x#id-X-%C2%ABNode%C2%BBC");

        String html = html(doc);

        assertThat(html).contains("<a href=\"https://jira.example.com/browse/ABC-1\">ABC-1</a></h3>")
                .contains("See <a href=\"https://wiki.example.com/x#id-X-%C2%ABNode%C2%BBC\">C</a>.")
                .contains("<a href=\"#e-d\">D</a>"); // no link of its own: its description here
    }

    @Test
    void tokensWriteSentencesSectionsAndNestedPartsInPlace() throws Exception {
        DocPage page = new DocPage("v", "View");
        Entry entry = new Entry("a", "A", "Application Function", "ignored when there is a body", List.of(), List.of(),
                List.of());
        entry.body.add(Entry.Part.text("Before"));
        entry.body.add(Entry.Part.sentence("triggers", new Link("b", "B", "Process"), "Line one\nLine two",
                "Signs it."));
        entry.body.add(Entry.Part.sentence("insert into", new Link("t", "[table ORDERS]", "DataObject"),
                "Rows:\n<tbl>\nA | B |\n1 | 2 |\n</tbl>", null));
        entry.body.add(Entry.Part.section("Input parameters", "- id"));
        entry.body.add(Entry.Part.nested(new Link("p", "Part", "Function"), List.of(Entry.Part.text("Inner"),
                Entry.Part.plain("[recursive reference: A]"))));
        entry.body.add(Entry.Part.text("<sql>select\nID\nfrom [table ORDERS]</sql>"));
        page.entries.add(entry);
        page.entries.add(new Entry("b", "B", "Business Process", "", List.of(), List.of(), List.of()));

        String html = html(new Doc("Model", List.of(page)));
        String grey = "<span style=\"color: rgb(107,119,140);\">";

        assertThat(html).doesNotContain("ignored when")
                .contains("<p>Before</p><p>triggers «Process»<a href=\"#e-b\">B</a> <em>" + grey
                        + "(Signs it.)</span></em></p><p>" + grey + "Line one<br/>Line two</span></p>")
                .contains("<p>insert into «DataObject»[table ORDERS]</p><p>" + grey + "Rows:</span></p>"
                        + "<table><tbody><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2</td></tr></tbody></table>")
                .contains("<h4>Input parameters</h4><ul><li>id</li></ul>")
                .contains("<div style=\"margin-left: 20px; border-left: 2px solid rgb(223,225,230); padding-left: 10px;\">"
                        + "<p><strong>«Function»Part</strong></p><p>Inner</p><p>[recursive reference: A]</p></div>")
                .contains("<strong>select</strong>");
    }

    @Test
    void hafTypeStyleWritesTheShortTypeRightBeforeTheName() throws Exception {
        DocPage page = new DocPage("v", "View");
        page.contents.add(new Link("a", "Pay", "Business Process: Broker"));
        page.entries.add(new Entry("a", "Pay", "Business Process: Broker", "", List.of(), List.of(), List.of()));
        new Exporter(new Doc("Model", List.of(page)), KeywordRules.parseFile("type style haf"))
                .export(folder, EnumSet.of(Format.HTML));

        assertThat(Files.readString(folder.resolve("html/index.html")))
                .contains("<li>«Process»<a href=\"#e-a\">Pay</a></li>").doesNotContain("&lt;&lt;");
    }
}
