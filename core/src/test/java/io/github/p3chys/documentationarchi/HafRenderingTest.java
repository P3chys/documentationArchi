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

        // one line each: verb, element, type and name small and grey, the business description in parentheses
        assertThat(html).contains("<li>→ triggers <a href=\"#e-b\">B</a> <small><span style=\"color: rgb(107,119,140);\">"
                + "Triggering Relationship: Sync · go</span></small> <em><span style=\"color: rgb(107,119,140);\">"
                + "(Signs the <em>contract</em>.)</span></em></li>")
                .contains("<li>← use X <em><span style=\"color: rgb(107,119,140);\">(Gives prices.)</span></em></li>");
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
    void aWorkPackageExportLinksWithinItselfFirstAndToConfluenceForTheRest() throws Exception {
        DocPage page = new DocPage("wp", "ABC-1");
        page.entries.add(new Entry("abc", "Process ABC", "Business Process", "See [[ref:def]].", List.of(), List.of(),
                List.of()));
        page.entries.add(new Entry("def", "Process DEF", "Business Process", "See [[ref:xyz]].", List.of(), List.of(),
                List.of()));
        Doc doc = new Doc("Model", List.of(page));
        doc.names.put("def", "Process DEF");
        doc.names.put("xyz", "Process XYZ");
        doc.external.put("def", "https://wiki.example.com/def");
        doc.external.put("xyz", "https://wiki.example.com/xyz");
        doc.localFirst = true;

        assertThat(html(doc)).contains("See <a href=\"#e-def\">Process DEF</a>.") // described here: no Confluence
                .contains("See <a href=\"https://wiki.example.com/xyz\">Process XYZ</a>.") // not in the export
                .doesNotContain("https://wiki.example.com/def");
    }

    private static final String PAGE = "https://wiki.example.com/display/ARCH/Processes";
    private static final String DEF = PAGE + "#id-Processes-%C2%ABProcess%C2%BBProcessDEF";

    /** ABC and DEF on one Confluence page, both in the export; XYZ on another, left out. */
    private static Doc processes(String abcText, boolean workPackage) {
        DocPage page = new DocPage("wp", "ABC-1");
        page.entries.add(new Entry("abc", "Process ABC", "Business Process", abcText, List.of(), List.of(), List.of()));
        page.entries.add(new Entry("def", "Process DEF", "Business Process", "", List.of(), List.of(), List.of()));
        Doc doc = new Doc("Model", List.of(page));
        doc.names.put("xyz", "Process XYZ");
        doc.external.put("abc", PAGE + "#id-Processes-%C2%ABProcess%C2%BBProcessABC");
        doc.external.put("def", DEF);
        doc.external.put("xyz", "https://wiki.example.com/display/ARCH/Other#id-Other-%C2%ABProcess%C2%BBProcessXYZ");
        doc.external.put("wp", "https://jira.example.com/browse/ABC-1");
        doc.localFirst = workPackage;
        return doc;
    }

    @Test
    void aWorkPackageExportTurnsAddressesOfWhatItDescribesIntoLinksWithinIt() throws Exception {
        String html = html(processes(String.join("\n\n",
                "Bare " + DEF,
                "Written another way http://WIKI.example.com/display/ARCH/Processes/#id-Processes-«Process»Process-DEF.",
                "Labelled [see DEF](" + DEF + ")",
                "Ticket https://jira.example.com/browse/ABC-1",
                "Page only " + PAGE,
                "By id https://wiki.example.com/pages/viewpage.action?pageId=12345",
                "Left out https://wiki.example.com/display/ARCH/Other#id-Other-%C2%ABProcess%C2%BBProcessXYZ"), true));

        assertThat(html).contains("Bare <a href=\"#e-def\">Process DEF</a>")
                .contains("Written another way <a href=\"#e-def\">Process DEF</a>.")
                .contains("Labelled <a href=\"#e-def\">see DEF</a>")
                .contains("Ticket <a href=\"#\">ABC-1</a>") // the work package's own page, the top of this one
                .contains("Page only <a href=\"" + PAGE + "\">" + PAGE + "</a>") // ABC and DEF both: which?
                .contains("By id <a href=\"https://wiki.example.com/pages/viewpage.action?pageId=12345\">")
                .contains("Left out <a href=\"https://wiki.example.com/display/ARCH/Other#id-Other-%C2%ABProcess");
    }

    @Test
    void otherExportsKeepTheAddressesAsWritten() throws Exception {
        String html = html(processes("Bare " + DEF + "\n\nLabelled [see DEF](" + DEF + ")", false));

        assertThat(html).contains("Bare <a href=\"" + DEF + "\">" + DEF + "</a>")
                .contains("Labelled <a href=\"" + DEF + "\">see DEF</a>");
    }

    /** An entry whose tokens wrote a sentence within a line, a sentence with a table, a section and a nested part. */
    private static DocPage tokenPage() {
        DocPage page = new DocPage("v", "View");
        Entry entry = new Entry("a", "A", "Application Function", "ignored when there is a body", List.of(), List.of(),
                List.of());
        entry.body.add(Entry.Part.text("Reads the orders " + Entry.Part.marker(0) + " then " + Entry.Part.marker(1),
                List.of(Entry.Part.sentence("read from", new Link("t", "[table ORDERS]"), "only active rows", null),
                        Entry.Part.sentence("triggers", new Link("b", "B", "Process"), "", "Signs it."))));
        entry.body.add(Entry.Part.sentence("insert into", new Link("t", "[table ORDERS]"),
                "Rows:\n<tbl>\nA | B |\n1 | 2 |\n</tbl>", null));
        entry.body.add(Entry.Part.section("Input parameters", "- id"));
        entry.body.add(Entry.Part.nested(new Link("p", "Part", "Function"), List.of(
                Entry.Part.text("Inner " + Entry.Part.marker(0), List.of(Entry.Part.recursive("A"))),
                Entry.Part.role("Available only for role {role} and higher.", "OPE"),
                Entry.Part.nested(new Link("q", "Deeper", "Function"), List.of(Entry.Part.text("Deep"))))));
        entry.body.add(Entry.Part.text("<sql>select\nID\nfrom [table ORDERS]</sql>"));
        page.entries.add(entry);
        page.entries.add(new Entry("b", "B", "Business Process", "", List.of(), List.of(), List.of()));
        return page;
    }

    @Test
    void tokensWriteSentencesWithinTheLineAndBlocksOfTheirOwn() throws Exception {
        String html = html(new Doc("Model", List.of(tokenPage())));
        String grey = "<span style=\"color: rgb(107,119,140);\">";
        String dark = "<span style=\"color: rgb(68,84,111);\">";

        assertThat(html).doesNotContain("ignored when")
                .contains("<p>Reads the orders read from [table ORDERS] " + dark + "only active rows</span> then triggers "
                        + "«Process»<a href=\"#e-b\">B</a> <em>" + grey + "(Signs it.)</span></em></p>")
                .contains("<p>insert into [table ORDERS]</p><div class=\"lead-in\"><p>" + grey + "Rows:</span></p></div>"
                        + "<table><thead><tr><th>A</th><th>B</th></tr></thead><tbody><tr><td>1</td><td>2</td></tr></tbody></table>")
                .contains("<h4>Input parameters</h4><ul><li>id</li></ul>")
                .contains("<div style=\"border-left: 3px solid rgb(0,85,204); background-color: rgb(247,248,255); "
                        + "padding: 10px 10px 10px 16px; margin: 10px 0;\"><div class=\"lead-in\"><p><strong>«Function»Part</strong></p></div>"
                        + "<p>Inner <em><span style=\"color: rgb(136,136,136);\">[recursive reference: A]</span></em></p>"
                        + "<p>Available only for role OPE and higher.</p>"
                        + "<div style=\"border-left: 3px solid rgb(0,119,170); background-color: rgb(245,255,253);")
                .contains("<strong>select</strong>");
    }

    @Test
    void hafLookWritesObjectsCountsAndNumberedContents() throws Exception {
        DocPage page = tokenPage();
        page.contents.add(new Link("a", "A", "Application Function"));
        page.contents.add(new Link("b", "B", "Business Process: Broker"));
        Entry a = page.entries.get(0);
        a.facts.add("Available only for role VIEW and higher.");
        a.factRoles.add("VIEW");
        Doc doc = new Doc("Model", List.of(page));
        doc.external.put("b", "https://wiki.example.com/b");
        new Exporter(doc, KeywordRules.parseFile("type style haf")).export(folder, EnumSet.of(Format.HTML));
        String html = Files.readString(folder.resolve("html/index.html"));
        String code = "<code style=\"color: rgb(0,85,204);\">";

        assertThat(html).contains("<p class=\"count\" style=\"color: rgb(136,136,136); font-size: 13px;\">2 elements</p>")
                .doesNotContain("<h2>Elements</h2>").doesNotContain("&lt;&lt;")
                // the contents jump within the page, the type grey inside the link
                .contains("<ol><li><a href=\"#e-a\"><span style=\"color: rgb(136,136,136); font-size: 12px; font-weight: "
                        + "normal; margin-right: 5px;\">«Function»</span>A</a></li><li><a href=\"#e-b\">")
                // a reference goes to the Confluence page, as one object
                .contains("triggers <a href=\"https://wiki.example.com/b\">" + code + "«Process»B</code></a>")
                .contains("read from " + code + "[table ORDERS]</code>")
                .contains("<p><strong><em>Input parameters</em></strong></p>")
                .contains("<p>Available only for role " + code + "VIEW</code> and higher.</p>")
                .contains("<h3><span style=\"color: rgb(136,136,136); font-size: 13px; font-weight: normal; margin-right: "
                        + "6px;\">«Function»</span>Part</h3>");
    }

    @Test
    void theConfluenceUrlPropertyIsNeverPrinted() {
        // the heading links there instead, in any look
        assertThat(KeywordRules.parseFile("type style haf").hidesProperty("confluenceUrl")).isTrue();
        assertThat(KeywordRules.defaults().hidesProperty("ConfluenceURL")).isTrue();
        assertThat(KeywordRules.defaults().hidesProperty("owner")).isFalse();
    }
}
