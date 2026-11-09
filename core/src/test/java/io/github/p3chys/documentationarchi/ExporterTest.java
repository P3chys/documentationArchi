package io.github.p3chys.documentationarchi;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationFileAttachment;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;

import io.github.p3chys.documentationarchi.doc.Assets;
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

    @Test
    void assetsAreCopiedIntoEveryOutputSoItNeverPointsBackToTheModelFolder(@TempDir Path model) throws Exception {
        Path assets = Files.createDirectories(model.resolve("assets")); // found ignoring case
        Files.write(assets.resolve("flow.png"), png(400, 100));
        Files.createDirectories(assets.resolve("docs"));
        byte[] contract = "the contract".getBytes(StandardCharsets.UTF_8);
        Files.write(assets.resolve("docs/SLA smlouva.pdf"), contract);
        Files.createDirectories(assets.resolve("a"));
        Files.createDirectories(assets.resolve("b"));
        Files.write(assets.resolve("a/logo.png"), png());
        Files.write(assets.resolve("b/logo.png"), png());
        Files.write(assets.resolve("index.png"), png()); // the name of the first page's view picture
        DocPage page = new DocPage("v", "View");
        page.image = png();
        page.imageWidth = 300;
        page.documentation = "Flow: [[asset:FLOW.png|Process flow]]\nContract: [[asset:docs\\SLA smlouva.pdf]]\n"
                + "Gone: asset:nothere.png\nAmbiguous: asset:logo.png, but asset:b/logo.png\nClash: asset:index.png";
        doc = new Doc("Model", List.of(page), Assets.in(model));
        Path out = folder.resolve("out");

        Exporter exporter = new Exporter(doc, KeywordRules.defaults());
        exporter.export(out, EnumSet.allOf(Format.class));

        assertThat(exporter.problems()).hasSize(2);
        assertThat(exporter.problems().get(0)).contains("\"nothere.png\"", "\"View\"", "not found");
        assertThat(exporter.problems().get(1)).contains("\"logo.png\"", "matches 2 files");

        String html = Files.readString(out.resolve("html/index.html"));
        assertThat(html).contains("<img class=\"asset\" src=\"assets/flow.png\" alt=\"Process flow\"/>")
                .contains("<a class=\"asset\" href=\"assets/SLA%20smlouva.pdf\">SLA smlouva.pdf</a>")
                .contains("src=\"assets/logo.png\"").contains("src=\"assets/index-2.png\"")
                .contains("[missing asset: nothere.png]").contains("[missing asset: logo.png]")
                .doesNotContain(model.toString()).doesNotContain(model.toUri().toString());
        assertWellFormed(html.substring(html.indexOf("<html")));
        assertThat(out.resolve("html/images/index.png")).exists();
        assertThat(Files.readAllBytes(out.resolve("html/assets/SLA smlouva.pdf"))).isEqualTo(contract);
        assertThat(out.resolve("html/assets/flow.png")).exists();

        String storage = Files.readString(out.resolve("confluence/pages/index.xml"));
        assertThat(storage).contains("<ac:image ac:alt=\"Process flow\"><ri:attachment ri:filename=\"flow.png\"/></ac:image>")
                .contains("<ac:link><ri:attachment ri:filename=\"SLA smlouva.pdf\"/><ac:link-body>SLA smlouva.pdf</ac:link-body></ac:link>")
                .doesNotContain(model.toString());
        assertWellFormed("<root xmlns:ac=\"urn:ac\" xmlns:ri=\"urn:ri\">" + storage + "</root>");
        assertThat(Files.readString(out.resolve("confluence/manifest.json")))
                .contains("\"attachments\": [\"index.png\", \"flow.png\", \"SLA smlouva.pdf\", \"logo.png\", \"index-2.png\"]");
        assertThat(Files.readAllBytes(out.resolve("confluence/attachments/SLA smlouva.pdf"))).isEqualTo(contract);

        // the PDF links to its own copy next to it (what Edge and Chrome open), pictures are drawn
        assertThat(Files.readAllBytes(out.resolve("assets/SLA smlouva.pdf"))).isEqualTo(contract);
        assertThat(out.resolve("assets/flow.png")).doesNotExist();
        // the originals are gone: the PDF still has the picture and, inside itself, the contract
        deleteTree(model);
        try (PDDocument pdf = Loader.loadPDF(out.resolve("model.pdf").toFile())) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("SLA smlouva.pdf").contains("missing asset: nothere.png");
            List<String> links = new ArrayList<>();
            for (PDPage p : pdf.getPages()) {
                for (PDAnnotation a : p.getAnnotations()) {
                    assertThat(a).isNotInstanceOf(PDAnnotationFileAttachment.class);
                    if (a instanceof PDAnnotationLink && ((PDAnnotationLink) a).getAction() instanceof PDActionURI) {
                        links.add(((PDActionURI) ((PDAnnotationLink) a).getAction()).getURI());
                    }
                }
            }
            assertThat(links).containsExactly("assets/SLA%20smlouva.pdf");
            Map<String, PDComplexFileSpecification> attached = pdf.getDocumentCatalog().getNames().getEmbeddedFiles().getNames();
            assertThat(attached).containsOnlyKeys("SLA smlouva.pdf");
            assertThat(attached.get("SLA smlouva.pdf").getEmbeddedFile().toByteArray()).isEqualTo(contract);
            // the view's picture and flow.png (the other pictures are the same image as the view's)
            assertThat(pdf.getPage(0).getResources().getXObjectNames()).hasSize(2);
        }
    }

    @Test
    void workPackagesListTheirElementsAndElementsTheirWorkPackages() throws Exception {
        DocPage page = new DocPage("v", "View");
        page.entries.add(new Entry("wp", "RBWDP-20025", "Work Package", "", List.of(), List.of(), List.of(),
                List.of(), List.of(new Link("e", "Portal", "Application Component"))));
        page.entries.add(new Entry("e", "Portal", "Application Component", "", List.of(), List.of(),
                List.of(new Link("v", "View")), List.of(new Link("wp", "RBWDP-20025")), List.of()));
        doc = new Doc("Model", List.of(page));

        export(Format.HTML);

        assertThat(Files.readString(folder.resolve("html/index.html")))
                .contains("<h4>Changed elements</h4><ul><li>" + TYPE_ACTOR.replace("Business Actor", "Application Component")
                        + "<a href=\"#e-e\">Portal</a></li></ul>")
                .contains("<p>Views: <a href=\"#\">View</a></p><p>Work packages: <a href=\"#e-wp\">RBWDP-20025</a></p>");
    }

    @Test
    void tallPicturesAreShownSmallerSoTheyFitOnePdfPage(@TempDir Path model) throws Exception {
        Path assets = Files.createDirectories(model.resolve("Assets"));
        Files.write(assets.resolve("tall.png"), png(300, 3000));
        Files.write(assets.resolve("wide.png"), png(2000, 500));
        Files.write(assets.resolve("small.png"), png(400, 100));
        DocPage page = new DocPage("v", "View");
        page.image = png(600, 6000);
        page.imageWidth = 300;
        page.imageHeight = 3000;
        page.documentation = "asset:tall.png\n\nasset:wide.png\n\nasset:small.png";
        doc = new Doc("Model", List.of(page), Assets.in(model));

        new Exporter(doc, KeywordRules.defaults()).export(folder, EnumSet.allOf(Format.class));

        // as high as the page allows, keeping the proportions; a picture that fits stays as it is
        String html = Files.readString(folder.resolve("html/index.html"));
        assertThat(html).contains("<a href=\"assets/tall.png\"><img class=\"asset\" src=\"assets/tall.png\" style=\"width:86px\"")
                .contains("<img class=\"asset\" src=\"assets/wide.png\" alt=\"wide.png\"/>")
                .contains("<img class=\"asset\" src=\"assets/small.png\" alt=\"small.png\"/>")
                .contains("style=\"width:300px\""); // the view's picture: a web page shows it at its size
        String storage = Files.readString(folder.resolve("confluence/pages/index.xml"));
        assertThat(storage).contains("<ac:image ac:alt=\"tall.png\" ac:width=\"86\">")
                .contains("<ac:image ac:alt=\"wide.png\" ac:width=\"1200\">")
                .contains("<ac:image ac:alt=\"small.png\"><ri:attachment");
        try (PDDocument pdf = Loader.loadPDF(folder.resolve("model.pdf").toFile())) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(3); // the view's picture, then the three assets
        }
    }

    @Test
    void assetsWithoutAnAssetsFolderAreReportedAndShownAsMissing(@TempDir Path model) throws Exception {
        DocPage page = new DocPage("v", "View");
        page.documentation = "[[asset:flow.png]]";
        doc = new Doc("Model", List.of(page), Assets.in(model));

        Exporter exporter = new Exporter(doc, KeywordRules.defaults());
        exporter.export(folder, EnumSet.of(Format.HTML));

        assertThat(exporter.problems()).singleElement().asString().contains("there is no Assets folder");
        assertThat(Files.readString(folder.resolve("html/index.html"))).contains("[missing asset: flow.png]");
        assertThat(folder.resolve("html/assets")).doesNotExist();
    }

    @Test
    void assetLinkingOutsideTheAssetsFolderIsNotExported(@TempDir Path model) throws Exception {
        Path secret = Files.writeString(model.resolve("secret.txt"), "private key");
        Path assets = Files.createDirectories(model.resolve("Assets"));
        try {
            Files.createSymbolicLink(assets.resolve("logo.png"), secret);
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("cannot create symbolic links here: " + e);
        }
        DocPage page = new DocPage("v", "View");
        page.documentation = "asset:logo.png";
        doc = new Doc("Model", List.of(page), Assets.in(model));

        Exporter exporter = new Exporter(doc, KeywordRules.defaults());
        exporter.export(folder, EnumSet.allOf(Format.class));

        assertThat(exporter.problems()).anyMatch(p -> p.contains("links to a file outside"));
        assertThat(Files.readString(folder.resolve("html/index.html"))).contains("[missing asset: logo.png]");
        assertThat(folder.resolve("html/assets")).doesNotExist();
        assertThat(Files.list(folder.resolve("confluence/attachments")).count()).isZero();
    }

    @Test
    void assetsFolderThatLinksElsewhereIsNotUsed(@TempDir Path model, @TempDir Path elsewhere) throws Exception {
        Files.writeString(elsewhere.resolve("id_rsa"), "private key");
        try {
            Files.createSymbolicLink(model.resolve("Assets"), elsewhere);
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("cannot create symbolic links here: " + e);
        }

        Assets assets = Assets.in(model);

        assertThat(assets.find("id_rsa", "View")).isNull();
        assertThat(assets.problems()).anyMatch(p -> p.contains("links to another folder"));
    }

    private static void deleteTree(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
                Files.delete(p);
            }
        }
    }

    private static void assertWellFormed(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(xml.getBytes("UTF-8"))));
    }

    private static byte[] png() throws Exception {
        return png(600, 200);
    }

    private static byte[] png(int width, int height) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
