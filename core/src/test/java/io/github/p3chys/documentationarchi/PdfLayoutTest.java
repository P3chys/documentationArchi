package io.github.p3chys.documentationarchi;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocFolder;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Picture;
import io.github.p3chys.documentationarchi.doc.Link;
import io.github.p3chys.documentationarchi.render.Exporter;
import io.github.p3chys.documentationarchi.render.Exporter.Format;
import io.github.p3chys.documentationarchi.text.KeywordRules;

/** The PDF's cover, table of contents, running header and footer, and landscape pages for wide pictures. */
class PdfLayoutTest {

    @TempDir
    Path folder;

    private Doc doc;

    @BeforeEach
    void doc() throws Exception {
        DocFolder sales = new DocFolder("f1", "Sales", null);
        List<DocPage> pages = new ArrayList<>();
        DocPage overview = new DocPage("model", "Shop");
        overview.documentation = "First paragraph of the purpose.\n\nSecond one.\n\nThird one.\n\nFourth, not on the cover.";
        pages.add(overview);
        DocPage tall = new DocPage("v1", "Tall view");
        picture(tall, 400, 300);
        pages.add(tall);
        DocPage wide = new DocPage("v2", "Wide view");
        wide.folder = sales;
        picture(wide, 1600, 500);
        pages.add(wide);
        pages.subList(1, pages.size()).forEach(p -> overview.contents.add(new Link(p.id, p.title)));
        doc = new Doc("Shop", pages);
    }

    private static void picture(DocPage page, int width, int height) throws Exception {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", png);
        page.image = Picture.of(png.toByteArray());
        page.imageWidth = width;
        page.imageHeight = height;
    }

    private PDDocument export(String rules) throws Exception {
        new Exporter(doc, KeywordRules.parse(rules)).version("Shop", null, LocalDateTime.of(2026, 11, 10, 14, 3))
                .export(folder, EnumSet.of(Format.PDF));
        return Loader.loadPDF(folder.resolve("shop.pdf").toFile());
    }

    private static String text(PDDocument pdf, int page) throws Exception {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        return stripper.getText(pdf);
    }

    @Test
    void coverThenContentsWithPageNumbersThenThePages() throws Exception {
        try (PDDocument pdf = export("")) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(5);
            String cover = text(pdf, 1);
            assertThat(cover).contains("Shop").contains("Generated 2026-11-10 14:03 from Shop")
                    .contains("First paragraph of the purpose.").contains("Third one.").doesNotContain("Fourth")
                    .doesNotContain("Page 1 of");
            String contents = text(pdf, 2);
            assertThat(contents).containsPattern("Contents").containsPattern("Shop\\W*3")
                    .containsPattern("Tall view\\W*4").contains("Sales").containsPattern("Wide view\\W*5");
            assertThat(text(pdf, 4)).contains("Tall view").contains("Page 4 of 5");
        }
    }

    @Test
    void runningHeaderHasThePageTitleAndFooterTheDocument() throws Exception {
        try (PDDocument pdf = export("")) {
            String page = text(pdf, 4);
            assertThat(page.split("Tall view", -1)).hasSizeGreaterThan(2); // the header and the heading
            assertThat(page).contains("Shop").contains("Page 4 of 5");
        }
    }

    @Test
    void wideViewGetsALandscapePage() throws Exception {
        try (PDDocument pdf = export("")) {
            PDRectangle portrait = pdf.getPage(3).getMediaBox();
            PDRectangle wide = pdf.getPage(4).getMediaBox();
            assertThat(portrait.getHeight()).isGreaterThan(portrait.getWidth());
            assertThat(wide.getWidth()).isGreaterThan(wide.getHeight());
            assertThat(text(pdf, 5)).contains("Wide view");
        }
    }

    @Test
    void rulesTurnEachPartOff() throws Exception {
        try (PDDocument pdf = export("pdf cover off\npdf contents off\npdf landscape off")) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(3);
            assertThat(text(pdf, 1)).contains("Generated 2026-11-10 14:03 from Shop").contains("Fourth");
            PDRectangle last = pdf.getPage(2).getMediaBox();
            assertThat(last.getHeight()).isGreaterThan(last.getWidth());
        }
    }

    @Test
    void footerFollowsTheLanguage() throws Exception {
        try (PDDocument pdf = export("language cs")) {
            assertThat(text(pdf, 4)).contains("Strana 4 z 5");
            assertThat(text(pdf, 2)).contains("Obsah");
        }
    }

    @Test
    void ruleLinesReadBack() {
        KeywordRules rules = KeywordRules.parse("pdf Cover OFF\npdf landscape on");
        assertThat(rules.pdf("cover")).isFalse();
        assertThat(rules.pdf("landscape")).isTrue();
        assertThat(rules.pdf("contents")).isTrue();
        assertThat(KeywordRules.parse(rules.rules().get(0).line()).pdf("cover")).isFalse();
    }
}
