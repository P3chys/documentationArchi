package io.github.p3chys.documentationarchi;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.render.Exporter;
import io.github.p3chys.documentationarchi.render.Exporter.Format;
import io.github.p3chys.documentationarchi.render.Preview;
import io.github.p3chys.documentationarchi.text.KeywordRules;

class ClickableDiagramTest {

    @TempDir
    Path folder;

    private Doc doc;
    private DocPage sales;

    @BeforeEach
    void doc() throws Exception {
        sales = new DocPage("v1", "Sales");
        sales.image = png(2000, 1000);
        sales.imageWidth = 1000; // drawn at twice the size
        sales.imageHeight = 500;
        sales.areas.add(new DocPage.Area("e1", "Client & co", 10, 10, 200, 100));
        sales.areas.add(new DocPage.Area("e3", "Nowhere", 300, 10, 100, 50)); // described on no page
        sales.areas.add(new DocPage.Area("e2", "Portal", 500, 250, 100, 50));
        sales.areas.add(new DocPage.Area("e4", "Inside", 20, 20, 50, 40)); // drawn inside the client
        sales.entries.add(entry("e1", "Client & co"));
        sales.entries.add(entry("e4", "Inside"));
        DocPage back = new DocPage("v2", "Back office");
        back.entries.add(entry("e2", "Portal"));
        doc = new Doc("Model", List.of(sales, back));
    }

    private static Entry entry(String id, String name) {
        return new Entry(id, name, "Business Actor", "", List.of(), List.of(), List.of());
    }

    private static byte[] png(int width, int height) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void htmlPictureIsAnImageMapToTheDescriptions() throws Exception {
        new Exporter(doc, KeywordRules.defaults()).export(folder, EnumSet.of(Format.HTML));
        String html = Files.readString(folder.resolve("html/index.html"));

        assertThat(html).contains("<span class=\"diagram\"><img class=\"diagram\" src=\"images/index.png\" "
                + "style=\"width:1000px\" usemap=\"#map-index\" alt=\"Sales\"/><map name=\"map-index\" id=\"map-index\">"
                // the topmost first: the one drawn inside wins where both are
                + "<area shape=\"rect\" coords=\"20,20,70,60\" href=\"#e-e4\" alt=\"Inside\" title=\"Inside\"/>"
                + "<area shape=\"rect\" coords=\"500,250,600,300\" href=\"back-office.html#e-e2\" alt=\"Portal\" title=\"Portal\"/>"
                + "<area shape=\"rect\" coords=\"10,10,210,110\" href=\"#e-e1\" alt=\"Client &amp; co\" title=\"Client &amp; co\"/>"
                + "</map></span>")
                .doesNotContain("Nowhere");
        // shown at the width the areas are in, never shrunk to the window
        assertThat(Files.readString(folder.resolve("html/style.css"))).contains("span.diagram img.diagram { max-width: none; }");
    }

    @Test
    void pictureWithoutAreasStaysAsItWas() throws Exception {
        sales.areas.clear();
        new Exporter(doc, KeywordRules.defaults()).export(folder, EnumSet.of(Format.HTML));

        assertThat(Files.readString(folder.resolve("html/index.html")))
                .contains("<p><img class=\"diagram\" src=\"images/index.png\" style=\"width:1000px\" alt=\"Sales\"/></p>");
    }

    @Test
    void confluencePictureHasNoLinks() throws Exception {
        new Exporter(doc, KeywordRules.defaults()).export(folder, EnumSet.of(Format.CONFLUENCE));

        assertThat(Files.readString(folder.resolve("confluence/pages/index.xml"))).doesNotContain("<map").doesNotContain("<area");
    }

    @Test
    void previewPictureLinksShowTheElements() {
        assertThat(Preview.page(doc, sales, KeywordRules.defaults(), "picture.png", new HashSet<>()))
                .contains("usemap=\"#map-index\"").contains("coords=\"500,250,600,300\" href=\"#ref-e2\"")
                .contains("href=\"#ref-e1\"").doesNotContain("Nowhere");
    }

    @Test
    void pdfHasLinkBoxesOverThePictureAtItsFittedSize() throws Exception {
        // on a portrait page; the cover and contents would come first
        new Exporter(doc, KeywordRules.parse("pdf cover off\npdf contents off\npdf landscape off"))
                .export(folder, EnumSet.of(Format.PDF));

        try (PDDocument pdf = Loader.loadPDF(folder.resolve("model.pdf").toFile())) {
            List<PDRectangle> boxes = pdf.getPage(0).getAnnotations().stream().filter(PDAnnotationLink.class::isInstance)
                    .map(a -> a.getRectangle()).collect(Collectors.toList());
            // fitted to 680 of 1000 pixels: a 200 x 100 box is 136 x 68 CSS pixels, 102 x 51 points
            assertThat(boxes).hasSize(3);
            PDRectangle outer = boxes.stream().filter(r -> r.getWidth() > 90).findFirst().orElseThrow();
            assertThat(outer.getWidth()).isBetween(101f, 103f);
            assertThat(outer.getHeight()).isBetween(50f, 52f);
            assertThat(boxes).anySatisfy(r -> assertThat(r.getWidth()).isBetween(50f, 52f)); // the Portal, 100 x 50
            // the inner box sits inside the outer one, 10 pixels in: 5.1 points
            PDRectangle inner = boxes.stream().filter(r -> r.getWidth() < 30).findFirst().orElseThrow();
            assertThat(inner.getLowerLeftX() - outer.getLowerLeftX()).isBetween(4.5f, 5.7f);
            assertThat(outer.getUpperRightY() - inner.getUpperRightY()).isBetween(4.5f, 5.7f);
        }
    }
}
