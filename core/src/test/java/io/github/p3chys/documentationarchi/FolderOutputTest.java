package io.github.p3chys.documentationarchi;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocFolder;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Link;
import io.github.p3chys.documentationarchi.render.Exporter;
import io.github.p3chys.documentationarchi.render.Exporter.Format;
import io.github.p3chys.documentationarchi.text.KeywordRules;

/** Views under their folders: the overview, the PDF bookmarks and the Confluence page tree. */
class FolderOutputTest {

    @TempDir
    Path folder;

    private Doc doc;

    @BeforeEach
    void doc() {
        DocFolder sales = new DocFolder("f1", "Sales", null);
        sales.documentation = "What we sell";
        DocFolder orders = new DocFolder("f2", "Orders", sales);
        List<DocPage> pages = new ArrayList<>();
        DocPage overview = new DocPage("model", "Shop");
        pages.add(overview);
        pages.add(new DocPage("v1", "Alpha"));
        pages.add(page("v2", "Pricing", sales));
        pages.add(page("v3", "Order flow", orders));
        pages.add(new DocPage("elements", "Elements not in any view"));
        pages.subList(1, pages.size()).forEach(p -> overview.contents.add(new Link(p.id, p.title)));
        doc = new Doc("Shop", pages);
    }

    private static DocPage page(String id, String title, DocFolder in) {
        DocPage page = new DocPage(id, title);
        page.folder = in;
        return page;
    }

    private void export(Format format) throws Exception {
        new Exporter(doc, KeywordRules.defaults()).export(folder, EnumSet.of(format));
    }

    @Test
    void overviewListsViewsUnderFolderHeadings() throws Exception {
        export(Format.HTML);

        String index = Files.readString(folder.resolve("html/index.html"));
        String contents = index.substring(index.indexOf("Contents"));
        assertThat(contents).containsSubsequence("<li><a href=\"alpha.html\">Alpha</a></li></ul>", "<h3>Sales</h3>",
                "<p>What we sell</p>", "pricing.html", "<h4>Orders</h4>", "order-flow.html", "<h3>Other pages</h3>",
                "elements-not-in-any-view.html");
    }

    @Test
    void pagesOutsideFoldersStayFlat() throws Exception {
        List<DocPage> pages = new ArrayList<>(doc.pages);
        pages.forEach(p -> p.folder = null);
        doc = new Doc("Shop", pages);
        export(Format.HTML);

        String index = Files.readString(folder.resolve("html/index.html"));
        assertThat(index).doesNotContain("<h3>").contains("Alpha</a></li><li><a href=\"pricing.html\">");
    }

    @Test
    void pdfBookmarksNestAsTheFolders() throws Exception {
        export(Format.PDF);

        try (PDDocument pdf = Loader.loadPDF(folder.resolve("shop.pdf").toFile())) {
            List<String> top = new ArrayList<>();
            PDOutlineItem sales = null;
            for (PDOutlineItem item : pdf.getDocumentCatalog().getDocumentOutline().children()) {
                top.add(item.getTitle());
                sales = item.getTitle().equals("Sales") ? item : sales;
            }
            assertThat(top).containsExactly("Shop", "Alpha", "Sales", "Elements not in any view");
            assertThat(sales.getFirstChild().getTitle()).isEqualTo("Pricing");
            assertThat(sales.getLastChild().getTitle()).isEqualTo("Orders");
            assertThat(sales.getLastChild().getFirstChild().getTitle()).isEqualTo("Order flow");
        }
    }

    @Test
    void confluenceGetsAParentPagePerFolder() throws Exception {
        export(Format.CONFLUENCE);

        String manifest = Files.readString(folder.resolve("confluence/manifest.json"));
        assertThat(manifest).containsSubsequence(
                "{\"title\": \"Shop\", \"parent\": null",
                "{\"title\": \"Shop - Sales\", \"parent\": \"Shop\", \"file\": \"pages/sales.xml\"",
                "{\"title\": \"Shop - Orders\", \"parent\": \"Shop - Sales\", \"file\": \"pages/orders.xml\"",
                "{\"title\": \"Shop - Alpha\", \"parent\": \"Shop\"",
                "{\"title\": \"Shop - Pricing\", \"parent\": \"Shop - Sales\"",
                "{\"title\": \"Shop - Order flow\", \"parent\": \"Shop - Orders\"");
        String sales = Files.readString(folder.resolve("confluence/pages/sales.xml"));
        assertThat(sales).contains("<p>What we sell</p>")
                .contains("<li><ac:link><ri:page ri:content-title=\"Shop - Pricing\"/><ac:link-body>Pricing</ac:link-body></ac:link></li>")
                .contains("ri:content-title=\"Shop - Orders\"");
    }

    @Test
    void folderTitleTakenByAViewIsMadeUnique() throws Exception {
        List<DocPage> pages = new ArrayList<>(doc.pages);
        pages.add(new DocPage("v4", "Sales"));
        doc = new Doc("Shop", pages);
        export(Format.CONFLUENCE);

        assertThat(Files.readString(folder.resolve("confluence/manifest.json")))
                .contains("{\"title\": \"Shop - Sales (2)\", \"parent\": \"Shop\", \"file\": \"pages/sales-2.xml\"");
    }
}
