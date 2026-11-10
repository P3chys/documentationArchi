package io.github.p3chys.documentationarchi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.p3chys.documentationarchi.doc.Assets;
import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.doc.Link;
import io.github.p3chys.documentationarchi.doc.Picture;
import io.github.p3chys.documentationarchi.render.Exporter;
import io.github.p3chys.documentationarchi.render.Exporter.Format;
import io.github.p3chys.documentationarchi.text.KeywordRules;

/** The single-file HTML mode: one self-contained file per page, same pages and names as the normal mode. */
class SingleFileHtmlTest {

    @TempDir
    Path folder;

    private byte[] viewPng;
    private byte[] assetPng;
    private byte[] contract;
    private Doc doc;

    @BeforeEach
    void doc(@TempDir Path model) throws Exception {
        viewPng = png(300, 200);
        assetPng = png(40, 30);
        contract = "the contract".getBytes(StandardCharsets.UTF_8);
        Path assets = Files.createDirectories(model.resolve("Assets"));
        Files.write(assets.resolve("flow.png"), assetPng);
        Files.write(assets.resolve("sla.pdf"), contract);
        DocPage overview = new DocPage("model", "Shop");
        DocPage view = new DocPage("v1", "Pricing");
        view.image = Picture.of(viewPng);
        view.imageWidth = 300;
        view.imageHeight = 200;
        view.documentation = "Flow: [[asset:flow.png]]\nContract: [[asset:sla.pdf]]";
        view.entries.add(new Entry("e1", "Abacus", "Application Component", "", List.of(), List.of(), List.of()));
        overview.contents.add(new Link(view.id, view.title));
        doc = new Doc("Shop", List.of(overview, view), Assets.in(model));
    }

    private static byte[] png(int width, int height) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private void export(KeywordRules rules) throws Exception {
        new Exporter(doc, rules).export(folder, EnumSet.of(Format.HTML));
    }

    private String html(String file) throws Exception {
        return Files.readString(folder.resolve("html").resolve(file));
    }

    @Test
    void everyPageIsOneFileWithItsStyleAndPicturesInside() throws Exception {
        export(KeywordRules.parse("html single-file"));

        String pricing = html("pricing.html");
        assertThat(pricing).contains("<style>").doesNotContain("rel=\"stylesheet\"").doesNotContain("<script")
                .doesNotContain("<aside").doesNotContain("class=\"site\"");
        Matcher pictures = Pattern.compile("src=\"data:image/png;base64,([^\"]+)\"").matcher(pricing);
        List<byte[]> inside = new java.util.ArrayList<>();
        pictures.reset();
        while (pictures.find()) {
            inside.add(Base64.getDecoder().decode(pictures.group(1)));
        }
        assertThat(inside).containsExactlyInAnyOrder(viewPng, assetPng);
        assertThat(pricing).doesNotContain("images/pricing.png").doesNotContain("assets/flow.png");
        assertThat(html("index.html")).contains("<a href=\"pricing.html\">Pricing</a>");
        assertThat(html("elements_a-z.html")).contains("<style>").contains("<a href=\"pricing.html#");
        assertThat(html("elements_by-type.html")).contains("<style>");
    }

    @Test
    void whatIsNotAPictureStaysAFileNextToThePages() throws Exception {
        export(KeywordRules.parse("html single-file"));

        assertThat(html("pricing.html")).contains("<a class=\"asset\" href=\"assets/sla.pdf\">");
        assertThat(Files.readAllBytes(folder.resolve("html/assets/sla.pdf"))).isEqualTo(contract);
        assertThat(folder.resolve("html/assets/flow.png")).doesNotExist();
        assertThat(folder.resolve("html/style.css")).doesNotExist();
        assertThat(folder.resolve("html/nav.js")).doesNotExist();
        assertThat(folder.resolve("html/search.js")).doesNotExist();
        assertThat(folder.resolve("html/images")).doesNotExist();
    }

    @Test
    void pagesStillLeadToEachOther() throws Exception {
        export(KeywordRules.parse("html single-file"));

        assertThat(html("pricing.html")).contains("<a href=\"index.html\">Shop</a>")
                .contains("<a href=\"elements_a-z.html\">").contains("<a href=\"elements_by-type.html\">");
        assertThat(html("index.html")).contains("<a href=\"elements_a-z.html\">");
    }

    @Test
    void sameFilesAsTheNormalMode() throws Exception {
        export(KeywordRules.defaults());
        List<String> normal = pages();
        assertThat(html("pricing.html")).contains("<aside class=\"sidebar\">").contains("href=\"style.css\"")
                .contains("src=\"images/pricing.png\"");
        assertThat(folder.resolve("html/style.css")).exists();

        export(KeywordRules.parse("html single-file"));
        assertThat(pages()).isEqualTo(normal);
    }

    private List<String> pages() throws Exception {
        try (java.util.stream.Stream<Path> files = Files.list(folder.resolve("html"))) {
            return files.map(f -> f.getFileName().toString()).filter(n -> n.endsWith(".html")).sorted()
                    .collect(java.util.stream.Collectors.toList());
        }
    }

    @Test
    void theRuleIsParsedAndWrittenBack() {
        KeywordRules on = KeywordRules.parse("html single-file");
        assertThat(on.htmlSingleFile()).isTrue();
        assertThat(on.rules()).extracting(KeywordRules.Rule::line).containsExactly("html single-file");
        assertThat(KeywordRules.parse("html single-file on").htmlSingleFile()).isTrue();
        assertThat(KeywordRules.parse("html single-file off").htmlSingleFile()).isFalse();
        assertThat(KeywordRules.defaults().htmlSingleFile()).isFalse();
        assertThat(KeywordRules.defaults().withHtmlSingleFile().htmlSingleFile()).isTrue();
        assertThatThrownBy(() -> KeywordRules.parse("html fancy")).hasMessageContaining("html single-file");
    }
}
