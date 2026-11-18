package io.github.p3chys.documentationarchi;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.doc.Picture;
import io.github.p3chys.documentationarchi.render.Exporter;
import io.github.p3chys.documentationarchi.render.Exporter.Format;
import io.github.p3chys.documentationarchi.text.KeywordRules;

/**
 * Page breaks of the PDF: what leads in to what follows (a heading, a bold line, a line ending with a colon, an opening
 * nesting tag, a table's header row) never ends a page, and a table's header row is repeated on the pages it runs on.
 */
class PageBreakTest {

    @TempDir
    Path folder;

    private int run;

    /** The text inside the margins of each page, without the running header and footer, a line each. */
    private List<List<String>> pages(Doc doc, String rules) throws Exception {
        Path out = folder.resolve("run" + run++);
        new Exporter(doc, KeywordRules.parse(rules)).export(out, EnumSet.of(Format.PDF));
        try (PDDocument pdf = Loader.loadPDF(out.resolve(Doc.slug(doc.title) + ".pdf").toFile())) {
            List<List<String>> pages = new ArrayList<>();
            for (PDPage page : pdf.getPages()) {
                PDFTextStripperByArea stripper = new PDFTextStripperByArea();
                float width = page.getMediaBox().getWidth();
                float height = page.getMediaBox().getHeight();
                stripper.addRegion("content", new Rectangle2D.Float(0, 50, width, height - 100)); // 18 mm margins
                stripper.extractRegions(page);
                pages.add(stripper.getTextForRegion("content").lines().map(String::strip).filter(l -> !l.isEmpty())
                        .collect(Collectors.toList()));
            }
            return pages;
        }
    }

    private static Doc page(String documentation, Entry... entries) {
        DocPage page = new DocPage("v", "View");
        page.documentation = documentation;
        page.entries.addAll(List.of(entries));
        return new Doc("Model", List.of(page));
    }

    private static String filler(int lines) {
        return IntStream.rangeClosed(1, lines).mapToObj(i -> "filler " + i).collect(Collectors.joining("\n"));
    }

    /**
     * Puts what comes after the filler at every height near the end of the first page, a line of filler at a time,
     * and checks that no page ends with the lead-in.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("leadIns")
    void aLeadInNeverEndsAPage(String kind, String text, String leadIn) throws Exception {
        int perPage = linesOnFirstPage();
        boolean crossed = false;
        for (int filler = perPage - 7; filler <= perPage; filler++) {
            List<List<String>> pages = pages(page(filler(filler) + "\n\n" + text), "");
            for (int i = 0; i < pages.size() - 1; i++) {
                List<String> page = pages.get(i);
                assertThat(page.get(page.size() - 1)).as("%s, page %d with %d lines of filler", kind, i + 1, filler)
                        .isNotEqualTo(leadIn);
            }
            crossed |= pages.size() > 1 && !pages.get(0).contains(leadIn);
        }
        assertThat(crossed).as("the lead-in went to the next page at least once").isTrue();
    }

    /** What leads in, the description from it on, and the line it is in the PDF. */
    static Stream<Arguments> leadIns() {
        return Stream.of(
                Arguments.of("a bold line", "**Input parameters**\n- id\n- name\n- date", "Input parameters"),
                Arguments.of("a line with a colon", "The steps:\n- one\n- two\n- three", "The steps:"),
                Arguments.of("an opening tag", "<for each order>\n- one\n- two\n- three\n</for each order>",
                        "<for each order>"),
                Arguments.of("a table header row", "| Name | Value |\n|---|---|\n| a | 1 |\n| b | 2 |\n| c | 3 |",
                        "Name Value"));
    }

    /** How many lines of filler the first page holds under the title. */
    private int linesOnFirstPage() throws Exception {
        List<String> first = pages(page(filler(150)), "").get(0);
        return Integer.parseInt(first.get(first.size() - 1).replace("filler ", ""));
    }

    @Test
    void anEntryHeadingNeverEndsAPage() throws Exception {
        int perPage = linesOnFirstPage();
        for (int filler = perPage - 9; filler <= perPage; filler++) {
            List<List<String>> pages = pages(page(filler(filler),
                    new Entry("e", "Checkout", "Business Process", "Takes the order.\nThen pays.", List.of(), List.of(),
                            List.of())), "");
            for (int i = 0; i < pages.size() - 1; i++) {
                List<String> page = pages.get(i);
                assertThat(page.get(page.size() - 1)).as("page %d with %d lines of filler", i + 1, filler)
                        .doesNotContain("Checkout").isNotEqualTo("Elements");
            }
        }
    }

    @Test
    void aLongTableRepeatsItsHeaderOnEveryPage() throws Exception {
        String rows = IntStream.rangeClosed(1, 150).mapToObj(i -> "| row " + i + " | " + i + " |")
                .collect(Collectors.joining("\n"));
        List<List<String>> pages = pages(page("| Name | Value |\n|---|---|\n" + rows), "");

        assertThat(pages).hasSizeGreaterThan(2);
        assertThat(pages.subList(1, pages.size())).allSatisfy(p -> assertThat(p.get(0)).isEqualTo("Name Value"));
    }

    @Test
    void aLandscapePageAfterAnotherStartsAPageOfItsOwn() throws Exception {
        List<DocPage> list = new ArrayList<>();
        for (String title : List.of("Wide one", "Wide two")) {
            DocPage page = new DocPage(title, title);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(1600, 300, BufferedImage.TYPE_INT_RGB), "png", png);
            page.image = Picture.of(png.toByteArray());
            page.imageWidth = 1600;
            page.imageHeight = 300;
            list.add(page);
        }
        List<List<String>> pages = pages(new Doc("Model", list), "pdf cover off\npdf contents off");

        assertThat(pages).hasSize(2);
        assertThat(pages.get(1).get(0)).isEqualTo("Wide two");
    }

    @Test
    void onlyHtmlAndThePdfMarkLeadInsAndHeaderRows() throws Exception {
        Doc doc = page("**Input parameters**\n- id\n\n| A | B |\n|---|---|\n| 1 | 2 |");
        new Exporter(doc, KeywordRules.defaults()).export(folder, EnumSet.of(Format.HTML, Format.CONFLUENCE));

        assertThat(Files.readString(folder.resolve("html/index.html")))
                .contains("<div class=\"lead-in\"><p><strong>Input parameters</strong></p></div><ul>")
                .contains("<table><thead><tr><th>A</th><th>B</th></tr></thead><tbody><tr><td>1</td>");
        assertThat(Files.readString(folder.resolve("confluence/pages/index.xml"))).doesNotContain("lead-in", "thead")
                .contains("<p><strong>Input parameters</strong></p><ul>")
                .contains("<table><tbody><tr><th>A</th><th>B</th></tr><tr><td>1</td>");
    }
}
