package io.github.p3chys.documentationarchi;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.p3chys.documentationarchi.doc.Assets;
import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.render.Exporter;
import io.github.p3chys.documentationarchi.render.Exporter.Format;
import io.github.p3chys.documentationarchi.text.KeywordRules;

/** The look of HaF (the rule line {@code type style haf}): what the page and its descriptions look like. */
class HafLookTest {

    private static final String HAF = "type style haf";

    @TempDir
    Path folder;

    private Doc doc(String text) throws Exception {
        Path model = Files.createTempDirectory(folder, "model");
        Path nested = Files.createDirectories(model.resolve("Assets").resolve("sub"));
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB), "png", png);
        Files.write(nested.resolve("nested.png"), png.toByteArray());
        DocPage page = new DocPage("v", "Pricing");
        page.documentation = text;
        return new Doc("Shop", List.of(page), Assets.in(model));
    }

    private String html(String text, String rules) throws Exception {
        new Exporter(doc(text), KeywordRules.parseFile(rules)).export(folder, EnumSet.of(Format.HTML));
        return Files.readString(folder.resolve("html/index.html"));
    }

    private String body(String text, String rules) throws Exception {
        String html = html(text, rules);
        return html.substring(html.indexOf("<main>"));
    }

    @Test
    void theTabTitleIsTheViewNameAndDocumentation() throws Exception {
        assertThat(html("x", HAF)).contains("<title>Pricing – Documentation</title>");
        assertThat(html("x", "type style default")).contains("<title>Pricing</title>");
    }

    @Test
    void singleFilePagesToo() throws Exception {
        String html = html("x", HAF + "\nhtml single-file");

        assertThat(html).contains("<title>Pricing – Documentation</title>").contains("<body class=\"haf\">");
    }

    @Test
    void theBodyHasTheHafClassOnlyInHafMode() throws Exception {
        assertThat(html("x", HAF)).contains("<body class=\"site haf\">");
        assertThat(html("x", "type style default")).contains("<body class=\"site\">").doesNotContain("haf");
    }

    @Test
    void theStyleSheetHasTheLinesAndSizes() throws Exception {
        html("x", HAF);

        assertThat(Files.readString(folder.resolve("html/style.css")))
                .contains(".haf h1 { font-size: 20px; }")
                .contains(".haf hr { border-top: 1px solid #e8e8e4; margin: 3rem 0 1.5rem; }")
                .contains(".haf main > hr:first-of-type { border-top: 2px solid #e8e8e4;")
                .contains("background: #f8f8f0;").contains("border: 1px solid #dde0d8; border-radius: 4px; padding: 8px;")
                .contains(".haf pre.sql strong { color: #003d99; font-size: 11px; }")
                .contains(".haf p.tag-line { color: #999; font-style: italic; font-size: 14px; }")
                .contains(".haf table.md th { background: #f0f0ec; font-size: 11px; font-weight: bold; }")
                .contains(".haf .note-box { border: 1px solid #a5adba; padding: 8px 12px;");
    }

    @Test
    void everyLineIsAParagraph() throws Exception {
        assertThat(body("one\ntwo\n\nthree", HAF)).contains("<p>one</p><p>two</p><p>three</p>");
        assertThat(body("one\ntwo", "type style default")).contains("<p>one<br/>two</p>");
    }

    @Test
    void listsTablesAndSqlStayTogether() throws Exception {
        String html = body("- a\n- b\n\n| x | y |\n|---|---|\n| 1 | 2 |\n\n<sql>select 1\nfrom dual</sql>", HAF);

        assertThat(html).contains("<ul><li>a</li><li>b</li></ul>").contains("<table class=\"md\">")
                .contains("<pre class=\"sql\"><strong>select</strong> 1\n<strong>from</strong> dual</pre>");
    }

    @Test
    void theColonOfAKeywordIsNotBold() throws Exception {
        assertThat(body("Technically: it works", HAF)).contains("<strong>Technically</strong>: it works");
        assertThat(body("Description: it works", HAF)).contains("<strong>Description</strong>: it works");
        assertThat(body("Technically: it works", "type style default")).contains("<strong>Technically:</strong>");
    }

    @Test
    void codeInsideALineStaysInTheSentence() throws Exception {
        String html = body("Set <code>ok</code> and <code><script>x</script></code> here", "type style default");

        assertThat(html).contains("<p>Set <code>ok</code> and <code>&lt;script&gt;x&lt;/script&gt;</code> here</p>")
                .doesNotContain("<pre").doesNotContain("<script>");
        assertThat(body("Set <code>ok</code> and <code><script>x</script></code> here", HAF)).contains("<code>ok</code>");
    }

    @Test
    void aCodeAloneOnItsLineOrWithALanguageIsABlock() throws Exception {
        assertThat(body("<code>ok</code>", "type style default")).contains("<pre class=\"code\">ok</pre>");
        assertThat(body("text <code lang=\"java\">int a;</code>", "type style default")).contains("<pre class=\"code\">int a;</pre>");
        assertThat(body("<code>\nline 1\nline 2\n</code>", "type style default")).contains("<pre class=\"code\">line 1\nline 2</pre>");
        assertThat(body("```\nplain\n```", HAF)).contains("<pre class=\"code\">plain</pre>");
    }

    @Test
    void variablesConstantsAndObjectsHaveHafColours() throws Exception {
        String html = body("Use {limit}, 'MAX_SIZE' and [table ORDERS].", HAF);

        assertThat(html).contains("{<code><span style=\"color: rgb(33,110,78);\"><em>limit</em></span></code>}")
                .contains("'<span style=\"color: rgb(174,71,135);\">MAX_SIZE</span>'")
                .contains("<code><span style=\"color: rgb(0,85,204);\">[table ORDERS]</span></code>");
    }

    @Test
    void aTokenRuleStillWins() throws Exception {
        String html = body("Use {limit} and 'MAX'", HAF + "\ntoken variable color=#ff0000");

        assertThat(html).contains("<span style=\"color: rgb(255,0,0);\">{limit}</span>")
                .contains("'<span style=\"color: rgb(174,71,135);\">MAX</span>'");
        assertThat(body("Use {limit}", "type style default")).contains("<span style=\"color: rgb(101,84,192);\">{limit}</span>");
    }

    @Test
    void onlyTheFirstWordOfASqlLineIsAKeyword() throws Exception {
        String html = body("<sql>select a\nfrom t\nwhere x = 1\n  and y = 2\n  or z = 3\norder by a</sql>", HAF);

        assertThat(html).contains("<strong>select</strong> a").contains("<strong>from</strong> t")
                .contains("<strong>where</strong> x = 1\n  and y = 2\n  or z = 3\n<strong>order</strong> by a");
        assertThat(body("<sql>select a\n  and b</sql>", "type style default")).contains("<strong>and</strong> b");
    }

    @Test
    void nestingTagsAreLabelsAtTheLeftEdge() throws Exception {
        String html = body("<for each order>\n<for each item>\nprice\n</for each item>\n</for each order>", HAF);

        assertThat(html).contains("<p class=\"tag-line\" style=\"color: #999999; font-style: italic;\">&lt;for each order&gt;</p>")
                .contains("<p class=\"tag-line\" style=\"color: #999999; font-style: italic;\">&lt;for each item&gt;</p>")
                .contains("<div style=\"margin-left: 40px\"><p>price</p></div>")
                .contains("&lt;/for each item&gt;</p>").doesNotContain("margin-left: 20px\"><p class=\"tag-line\"");
        assertThat(body("<loop>\nx\n</loop>", "type style default")).doesNotContain("tag-line");
    }

    @Test
    void tablesInDescriptionsAreCompact() throws Exception {
        String html = body("<tbl>a | b |\n1 | 2</tbl>", HAF);

        assertThat(html).contains("<table class=\"md\"><tbody><tr><th>a</th><th>b</th></tr><tr><td>1</td><td>2</td></tr>");
        assertThat(body("<tbl>a | b |\n1 | 2</tbl>", "type style default")).contains("<table><tbody>");
    }

    @Test
    void twoTablesInARowAreApart() throws Exception {
        String text = "<tbl>a | b |\n1 | 2</tbl>\n<tbl>c | d |\n3 | 4</tbl>";

        assertThat(body(text, "type style default")).contains("</table><p></p><table>");
        assertThat(body(text, HAF)).contains("</table><p></p><table class=\"md\">");
    }

    @Test
    void aHashNoteIsAPlainBoxAndANotePanelStaysYellow() throws Exception {
        String html = body("<#>remember this</#>\n\nNOTE: careful", HAF);

        assertThat(html).contains("<div class=\"note-box\"><p>remember this</p></div>")
                .contains("<div class=\"panel note\"><p class=\"panel-title\">NOTE</p><p>careful</p></div>");
        assertThat(body("<#>\none\n\n<sql>select 1</sql>\n</#>", HAF)).contains("<div class=\"note-box\"><p>one</p><pre class=\"sql\">");
        assertThat(body("<#>remember this</#>", "type style default")).contains("<div class=\"panel note\">")
                .doesNotContain("note-box");
    }

    @Test
    void confluenceKeepsTheNoteAsAStyledDiv() throws Exception {
        new Exporter(doc("<#>remember this</#>\n\nNOTE: careful"), KeywordRules.parseFile(HAF))
                .export(folder, EnumSet.of(Format.CONFLUENCE));

        assertThat(Files.readString(folder.resolve("confluence/pages/index.xml")))
                .contains("<div style=\"border: 1px solid rgb(165,173,186);")
                .contains("<p>remember this</p></div>").contains("ac:name=\"note\"");
    }

    @Test
    void aPicturesAltTextIsTheReferenceAsWritten() throws Exception {
        assertThat(body("[[asset:sub/nested.png]]", "type style default")).contains("alt=\"sub/nested.png\"");
        assertThat(body("[[asset:sub/nested.png|the flow]]", "type style default")).contains("alt=\"the flow\"");
    }

    @Test
    void aMissingAssetHasACapitalInHafMode() throws Exception {
        assertThat(body("[[asset:gone.png]]", HAF)).contains("[Missing asset: gone.png]");
        assertThat(body("[[asset:gone.png]]", "type style default")).contains("[missing asset: gone.png]");
    }

    @Test
    void thePdfHasTheHafClass() throws Exception {
        new Exporter(doc("<sql>select 1</sql>\n\n<#>note</#>"), KeywordRules.parseFile(HAF)).export(folder, EnumSet.of(Format.PDF));

        assertThat(folder.resolve("shop.pdf")).exists();
    }
}
