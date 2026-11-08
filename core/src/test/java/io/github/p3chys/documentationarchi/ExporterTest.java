package io.github.p3chys.documentationarchi;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.doc.Link;
import io.github.p3chys.documentationarchi.doc.Property;
import io.github.p3chys.documentationarchi.doc.Relation;
import io.github.p3chys.documentationarchi.render.Exporter;
import io.github.p3chys.documentationarchi.render.Exporter.Format;
import io.github.p3chys.documentationarchi.text.KeywordRules;

class ExporterTest {

    private static final String TYPE_ACTOR =
            "<small><span style=\"color: rgb(107,119,140);\">&lt;&lt;Business Actor&gt;&gt;</span></small> ";

    @TempDir
    Path folder;

    private Doc doc;

    @BeforeEach
    void model() throws Exception {
        DocPage root = new DocPage("model", "Žluťoučký model");
        root.documentation = "Purpose of the model";
        root.contents.add(new Link("view1", "Přehled & <souhrn>"));
        root.contents.add(new Link("view2", "Přehled"));

        Entry client = new Entry("e1", "Klient", "Business Actor", "WARNING: Pozor na #risk\n\nSee https://example.com/x?a=1&b=2",
                List.of(new Property("owner", "Adam")),
                List.of(new Relation(true, "Serving Relationship", "uses", "NOTE: why", new Link("e2", "Portal", "Application Component"), false),
                        new Relation(false, "Association Relationship", "", "", new Link("gone", "Not exported", "Node"), true)),
                List.of(new Link("view1", "Přehled & <souhrn>"), new Link("view2", "Přehled")));
        Entry portal = new Entry("e2", "Portal", "Application Component", "", List.of(), List.of(), List.of());

        DocPage view1 = new DocPage("view1", "Přehled & <souhrn>");
        view1.image = png();
        view1.imageWidth = 300;
        view1.entries.add(client);
        view1.notes.add("A note on the view");
        view1.relatedViews.add(new Link("view2", "Přehled"));

        DocPage view2 = new DocPage("view2", "Přehled");
        view2.entries.add(portal);
        view2.contents.add(new Link("e1", "Klient", "Business Actor"));

        doc = new Doc("Žluťoučký model", List.of(root, view1, view2));
    }

    private void export(Format format) throws Exception {
        new Exporter(doc, KeywordRules.defaults()).export(folder, EnumSet.of(format));
    }

    @Test
    void htmlLinksPagesAndElements() throws Exception {
        export(Format.HTML);

        String index = Files.readString(folder.resolve("html/index.html"));
        assertThat(index).contains("href=\"prehled-souhrn.html\"").contains("href=\"prehled.html\"");

        String view1 = Files.readString(folder.resolve("html/prehled-souhrn.html"));
        assertThat(view1).contains("<h3 id=\"e-e1\">" + TYPE_ACTOR + "Klient</h3>")
                .contains("href=\"prehled.html#e-e2\">Portal</a>")
                .contains("<li>\u2190 <small><span style=\"color: rgb(107,119,140);\">&lt;&lt;Node&gt;&gt;</span></small> Not exported</li>")
                .contains("class=\"panel warning\"").contains("class=\"tag red\">RISK</span>")
                .contains("href=\"https://example.com/x?a=1&amp;b=2\"")
                .contains("src=\"images/prehled-souhrn.png\"");
        assertThat(folder.resolve("html/images/prehled-souhrn.png")).exists();
        assertThat(Files.readString(folder.resolve("html/prehled.html"))).contains("href=\"prehled-souhrn.html#e-e1\">Klient</a>");
        assertWellFormed(view1.substring(view1.indexOf("<html")));
    }

    @Test
    void storageFormatUsesConfluenceMacros() throws Exception {
        export(Format.CONFLUENCE);

        String view1 = Files.readString(folder.resolve("confluence/pages/prehled-souhrn.xml"));
        assertThat(view1)
                .contains("<ac:link ac:anchor=\"e-e2\"><ri:page ri:content-title=\"Žluťoučký model - Přehled\"/>"
                        + "<ac:link-body>Portal</ac:link-body></ac:link>")
                .contains("<ac:structured-macro ac:name=\"anchor\"><ac:parameter ac:name=\"\">e-e1</ac:parameter>")
                .contains("<ac:structured-macro ac:name=\"warning\"><ac:parameter ac:name=\"title\">WARNING</ac:parameter>"
                        + "<ac:rich-text-body><p>Pozor na ")
                .contains("<ac:parameter ac:name=\"colour\">Red</ac:parameter><ac:parameter ac:name=\"title\">RISK</ac:parameter>")
                .contains("<ac:image ac:width=\"300\"><ri:attachment ri:filename=\"prehled-souhrn.png\"/></ac:image>")
                .doesNotContain("<a href=\"prehled");
        assertWellFormed("<root xmlns:ac=\"urn:ac\" xmlns:ri=\"urn:ri\">" + view1 + "</root>");

        assertThat(folder.resolve("confluence/attachments/prehled-souhrn.png")).exists();
        assertThat(folder.resolve("confluence/upload_to_confluence.py")).exists();
        assertThat(Files.readString(folder.resolve("confluence/manifest.json")))
                .contains("{\"title\": \"Žluťoučký model\", \"parent\": null, \"file\": \"pages/index.xml\", \"attachments\": []}")
                .contains("{\"title\": \"Žluťoučký model - Přehled & <souhrn>\", \"parent\": \"Žluťoučký model\", "
                        + "\"file\": \"pages/prehled-souhrn.xml\", \"attachments\": [\"prehled-souhrn.png\"]}");
    }

    @Test
    void storageLinkOnSamePageHasNoPageReference() throws Exception {
        DocPage page = new DocPage("v", "View");
        page.entries.add(new Entry("a", "A", "T", "", List.of(), List.of(new Relation(true, "R", "", "", new Link("b", "B"), false)), List.of()));
        page.entries.add(new Entry("b", "B", "T", "", List.of(), List.of(), List.of()));
        doc = new Doc("Model", List.of(page));

        export(Format.CONFLUENCE);

        assertThat(Files.readString(folder.resolve("confluence/pages/index.xml")))
                .contains("<ac:link ac:anchor=\"e-b\"><ac:link-body>B</ac:link-body></ac:link>");
        assertThat(Files.readString(folder.resolve("confluence/manifest.json"))).contains("\"title\": \"Model - View\"");
    }

    @Test
    void confluenceTitlesStayUniqueIgnoringCase() throws Exception {
        doc = new Doc("M", List.of(new DocPage("r", "M"), new DocPage("a", "View"), new DocPage("b", "VIEW")));

        export(Format.CONFLUENCE);

        assertThat(Files.readString(folder.resolve("confluence/manifest.json")))
                .contains("\"title\": \"M - View\"").contains("\"title\": \"M - VIEW (2)\"");
        assertThat(folder.resolve("confluence/pages/view.xml")).exists();
        assertThat(folder.resolve("confluence/pages/view-2.xml")).exists();
    }

    @Test
    void pdfHasAllPagesWithUnicodeTextAndInternalLinks() throws Exception {
        export(Format.PDF);

        try (PDDocument pdf = Loader.loadPDF(folder.resolve("zlutoucky-model.pdf").toFile())) {
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains("Žluťoučký model").contains("Přehled & <souhrn>").contains("Pozor na").contains("RISK");
            assertThat(pdf.getNumberOfPages()).isEqualTo(3);
            long links = pdf.getPage(1).getAnnotations().stream().filter(a -> a instanceof PDAnnotationLink).count();
            assertThat(links).isGreaterThanOrEqualTo(3);
            assertThat(pdf.getDocumentCatalog().getDocumentOutline().children()).hasSize(3);
            assertThat(pdf.getPage(1).getResources().getXObjectNames()).isNotEmpty();
        }
    }

    @Test
    void sqlNotesAndTokensReachEveryOutput() throws Exception {
        DocPage page = new DocPage("v", "View");
        page.documentation = "<sql>select a < b from t where x = ']]>'</sql>\n<#>Remark</#>\n[orders] 'NEW' {user}";
        doc = new Doc("Model", List.of(page));

        new Exporter(doc, KeywordRules.defaults()).export(folder, EnumSet.allOf(Format.class));

        String html = Files.readString(folder.resolve("html/index.html"));
        assertThat(html).contains("<pre class=\"sql\">select a &lt; b from t where x = ']]&gt;'</pre>")
                .contains("<div class=\"panel note\"><p>Remark</p></div>")
                .contains("<span style=\"color: rgb(7,71,166);\">[orders]</span>")
                .contains("<span style=\"color: rgb(0,102,68);\">'NEW'</span>")
                .contains("<span style=\"color: rgb(101,84,192);\">{user}</span>");
        assertWellFormed(html.substring(html.indexOf("<html")));

        String storage = Files.readString(folder.resolve("confluence/pages/index.xml"));
        assertThat(storage).contains("<ac:structured-macro ac:name=\"code\"><ac:parameter ac:name=\"language\">sql</ac:parameter>"
                        + "<ac:plain-text-body><![CDATA[select a < b from t where x = ']]]]><![CDATA[>']]></ac:plain-text-body>")
                .contains("<ac:structured-macro ac:name=\"note\"><ac:rich-text-body><p>Remark</p></ac:rich-text-body>")
                .contains("<span style=\"color: rgb(7,71,166);\">[orders]</span>");
        assertWellFormed("<root xmlns:ac=\"urn:ac\" xmlns:ri=\"urn:ri\">" + storage + "</root>");

        try (PDDocument pdf = Loader.loadPDF(folder.resolve("model.pdf").toFile())) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("select a < b").contains("Remark").contains("[orders]");
        }
    }

    @Test
    void viewStartsWithDescriptionThenContentsAndLinesDivideSections() throws Exception {
        DocPage page = new DocPage("v", "View");
        page.documentation = "About the view";
        page.image = png();
        page.imageWidth = 300;
        page.contents.add(new Link("a", "A", "Node"));
        page.contents.add(new Link("b", "B", "Node"));
        page.entries.add(new Entry("a", "A", "Node", "", List.of(), List.of(), List.of()));
        page.entries.add(new Entry("b", "B", "Node", "", List.of(), List.of(), List.of()));
        doc = new Doc("Model", List.of(page));

        export(Format.CONFLUENCE);

        String body = Files.readString(folder.resolve("confluence/pages/index.xml"));
        assertThat(body).containsSubsequence("<p>About the view</p>", "<hr/>", "<h2>Contents</h2>", "<hr/>", "<ac:image",
                "<hr/>", "<h2>Elements</h2>", "<h3>", "<hr/>", "<h3>");
        assertThat(body).doesNotStartWith("<hr/>").doesNotEndWith("<hr/>").doesNotContain("<hr/><hr/>");
    }

    @Test
    void relationsOutsideTheViewComeLastGroupedByType() throws Exception {
        DocPage page = new DocPage("v", "View");
        page.entries.add(new Entry("a", "A", "Node", "", List.of(), List.of(
                new Relation(true, "Serving", "", "", new Link("x", "X", "Node"), true),
                new Relation(true, "Flow", "data", "", new Link("y", "Y", "Node"), true),
                new Relation(false, "Serving", "", "", new Link("z", "Z", "Node"), true),
                new Relation(true, "Flow", "", "", new Link("b", "B", "Node"), false)),
                List.of(new Link("v", "View"))));
        page.entries.add(new Entry("b", "B", "Node", "", List.of(), List.of(), List.of()));
        doc = new Doc("Model", List.of(page));

        export(Format.HTML);

        assertThat(Files.readString(folder.resolve("html/index.html"))).containsSubsequence("<table>", ">B</a>", "</table>",
                "Views: ", "<h4>Other related elements</h4>", "<strong>Serving</strong>", "X", "Z",
                "<strong>Flow</strong>", "Y", " (data)");
    }

    @Test
    void forbiddenCharactersInModelTextDontBreakOutput() throws Exception {
        DocPage page = new DocPage("v", "View\u0001");
        page.documentation = "bad \u0008 char";
        doc = new Doc("Model", List.of(page));

        new Exporter(doc, KeywordRules.defaults()).export(folder, EnumSet.allOf(Format.class));

        String html = Files.readString(folder.resolve("html/index.html"));
        assertWellFormed(html.substring(html.indexOf("<html")));
    }

    private static void assertWellFormed(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(xml.getBytes("UTF-8"))));
    }

    private static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(600, 200, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
