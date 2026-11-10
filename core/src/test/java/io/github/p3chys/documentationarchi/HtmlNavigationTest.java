package io.github.p3chys.documentationarchi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocFolder;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.doc.Link;
import io.github.p3chys.documentationarchi.render.Exporter;
import io.github.p3chys.documentationarchi.render.Exporter.Format;
import io.github.p3chys.documentationarchi.text.KeywordRules;

/** The sidebar, the element indexes and the search data of the HTML export. */
class HtmlNavigationTest {

    @TempDir
    Path folder;

    private Doc doc;

    @BeforeEach
    void doc() {
        DocFolder sales = new DocFolder("f1", "Sales", null);
        DocFolder support = new DocFolder("f2", "Support", null);
        List<DocPage> pages = new ArrayList<>();
        DocPage overview = new DocPage("model", "Shop");
        pages.add(overview);
        DocPage pricing = new DocPage("v1", "Pricing");
        pricing.folder = sales;
        pricing.image = new byte[] {1};
        pricing.documentation = "How prices are made\nfor <customers>";
        pricing.entries.add(new Entry("e1", "Žluťák", "Business Actor", "Buys \"things\"", List.of(), List.of(), List.of()));
        pricing.entries.add(new Entry("e2", "Abacus", "Application Component", "", List.of(), List.of(), List.of()));
        pages.add(pricing);
        DocPage tickets = new DocPage("v2", "Tickets");
        tickets.folder = support;
        tickets.entries.add(new Entry("e3", "Ada", "Business Actor", "", List.of(), List.of(), List.of()));
        pages.add(tickets);
        pages.subList(1, pages.size()).forEach(p -> overview.contents.add(new Link(p.id, p.title)));
        doc = new Doc("Shop", pages);
    }

    private void export() throws Exception {
        new Exporter(doc, KeywordRules.defaults()).export(folder, EnumSet.of(Format.HTML));
    }

    private String html(String file) throws Exception {
        return Files.readString(folder.resolve("html").resolve(file));
    }

    @Test
    void everyPageHasTheSidebarWithItselfHighlightedAndItsFolderOpen() throws Exception {
        export();

        String pricing = html("pricing.html");
        assertThat(pricing).contains("<aside class=\"sidebar\">")
                .contains("<li class=\"folder\"><details open=\"open\"><summary>Sales</summary><ul>"
                        + "<li class=\"current\"><a href=\"pricing.html\" aria-current=\"page\">Pricing</a></li>")
                .contains("<li class=\"folder\"><details><summary>Support</summary><ul><li><a href=\"tickets.html\">Tickets</a></li>")
                .contains("<li><a href=\"index.html\">Shop</a></li>")
                .contains("<a href=\"elements_a-z.html\">Elements A–Z</a>")
                .contains("<script src=\"search.js\"></script><script src=\"nav.js\"></script>");
        assertThat(html("index.html")).contains("<li class=\"current\"><a href=\"index.html\" aria-current=\"page\">Shop</a></li>");
        assertWellFormed(pricing);
        assertThat(folder.resolve("html/nav.js")).exists();
    }

    @Test
    void searchBoxIsHiddenUntilTheScriptShowsIt() throws Exception {
        export();

        assertThat(html("tickets.html")).contains("<form class=\"search\" hidden=\"hidden\"");
        assertThat(Files.readString(folder.resolve("html/nav.js"))).contains("form.hidden = false").doesNotContain("fetch(");
    }

    @Test
    void aToZListsTheDescribedElementsUnderTheirLetterWithTypes() throws Exception {
        export();

        String index = html("elements_a-z.html");
        assertThat(index).containsSubsequence("<h2 id=\"letter-0\">A</h2>", "Abacus", "Ada", "<h2 id=\"letter-1\">Z</h2>",
                        "&lt;&lt;Business Actor&gt;&gt;", "<a href=\"pricing.html#e-e1\">Žluťák</a>")
                .contains("<li class=\"current\"><a href=\"elements_a-z.html\" aria-current=\"page\">");
        assertWellFormed(index);
    }

    @Test
    void byTypeGroupsTheElementsUnderTheirTypes() throws Exception {
        export();

        String index = html("elements_by-type.html");
        assertThat(index).containsSubsequence("<h2>Application Component <small>(1)</small></h2>", "Abacus",
                "<h2>Business Actor <small>(2)</small></h2>", "<a href=\"tickets.html#e-e3\">Ada</a>",
                "<a href=\"pricing.html#e-e1\">Žluťák</a>");
        assertWellFormed(index);
    }

    @Test
    void searchDataHoldsViewsAndElementsAsAScriptThatCannotBreakOut() throws Exception {
        export();

        String data = Files.readString(folder.resolve("html/search.js"), StandardCharsets.UTF_8);
        assertThat(data).startsWith("window.DOC_SEARCH = [")
                .contains("{n:\"Pricing\",t:\"View\",u:\"pricing.html\",d:\"How prices are made for \\u003ccustomers\\u003e\"}")
                .contains("{n:\"Shop\",t:\"Page\",u:\"index.html\"")
                .contains("{n:\"Žluťák\",t:\"Business Actor\",u:\"pricing.html#e-e1\",d:\"Buys \\\"things\\\"\"}");
    }

    @Test
    void textsFollowTheLanguage() throws Exception {
        new Exporter(doc, KeywordRules.parse("language cs")).export(folder, EnumSet.of(Format.HTML));

        assertThat(html("index.html")).contains("Prvky A–Z").contains("placeholder=\"Hledat\"");
    }

    private static void assertWellFormed(String html) throws Exception {
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(html.substring(html.indexOf("<html")).getBytes(StandardCharsets.UTF_8)));
    }
}
