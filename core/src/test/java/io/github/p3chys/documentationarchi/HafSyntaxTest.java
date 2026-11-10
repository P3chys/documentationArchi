package io.github.p3chys.documentationarchi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.p3chys.documentationarchi.doc.Assets;
import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.doc.Link;
import io.github.p3chys.documentationarchi.doc.Relation;
import io.github.p3chys.documentationarchi.render.Exporter;
import io.github.p3chys.documentationarchi.render.Exporter.Format;
import io.github.p3chys.documentationarchi.text.KeywordRules;
import io.github.p3chys.documentationarchi.text.RichText;
import io.github.p3chys.documentationarchi.text.RichText.Block;
import io.github.p3chys.documentationarchi.text.RichText.Inline;
import io.github.p3chys.documentationarchi.text.RichText.InlineKind;

/** The description syntax shared with HaF: tbl, nesting tags, multi-line notes, ass, the assets folder, SQL colours. */
class HafSyntaxTest {

    private KeywordRules rules = KeywordRules.defaults();

    @TempDir
    Path folder;

    private static List<InlineKind> kinds(List<Inline> inlines) {
        return inlines.stream().map(i -> i.kind).collect(Collectors.toList());
    }

    private static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private Exporter export(String description, Assets assets, Format format) throws Exception {
        DocPage page = new DocPage("v", "View");
        page.documentation = description;
        Exporter exporter = new Exporter(new Doc("Model", List.of(page), assets), rules);
        exporter.export(folder.resolve("out"), EnumSet.of(format));
        return exporter;
    }

    private String html(String description, Assets assets) throws Exception {
        export(description, assets, Format.HTML);
        return Files.readString(folder.resolve("out/html/index.html"));
    }

    private String storage(String description, Assets assets) throws Exception {
        export(description, assets, Format.CONFLUENCE);
        return Files.readString(folder.resolve("out/confluence/pages/index.xml"));
    }

    // ---- <tbl>

    @Test
    void tblIsATableWithoutTheLineOfDashesAndCellsGoOnOnTheNextLine() {
        List<Block> blocks = RichText.parse("before\n<tbl>\n| Name | Note |\n|---|---|\n| a | first\nsecond |\n| b | |\n</tbl>\nafter", rules);

        assertThat(blocks).hasSize(3);
        Block table = blocks.get(1);
        assertThat(table.rows).hasSize(3);
        assertThat(table.rows.get(0)).hasSize(2);
        assertThat(kinds(table.rows.get(1).get(1))).containsExactly(InlineKind.TEXT, InlineKind.BREAK, InlineKind.TEXT);
        assertThat(table.rows.get(1).get(1).get(2).text).isEqualTo("second"); // the | ends the row
        assertThat(blocks.get(0).inlines.get(0).text).isEqualTo("before");
        assertThat(blocks.get(2).inlines.get(0).text).isEqualTo("after");
    }

    @Test
    void tblContinuationLineCanStartTheNextCells() {
        Block table = RichText.parse("<tbl>\n|a|b|c|\n|1|two\nlines|3|\n</tbl>", rules).get(0);

        assertThat(table.rows.get(1)).hasSize(3);
        assertThat(table.rows.get(1).get(2).get(0).text).isEqualTo("3");
    }

    @Test
    void tblCellsKeepTheirTokensAndAMarkdownTableStaysAsItWas() {
        Block table = RichText.parse("<tbl>\n|a|b|\n|[orders]|'NEW'|\n</tbl>", rules).get(0);

        assertThat(table.rows.get(1).get(0).get(0).kind).isEqualTo(InlineKind.TABLE);
        assertThat(table.rows.get(1).get(1).get(0).kind).isEqualTo(InlineKind.CONSTANT);
        assertThat(RichText.parse("|a|b|\n|1|2|", rules)).singleElement().satisfies(b -> assertThat(b.rows).isNull());
    }

    @Test
    void tblWorksInARelationshipDescription() throws Exception {
        DocPage page = new DocPage("v", "View");
        page.entries.add(new Entry("a", "Loader", "Process", "", List.of(), List.of(new Relation(true, "Flow", "",
                "Maps:\n<tbl>\n|from|to|\n|x|y\nmore|\n</tbl>\ndone", new Link("a", "Loader"), false)), List.of()));
        new Exporter(new Doc("Model", List.of(page)), rules).export(folder.resolve("out"), EnumSet.of(Format.HTML));

        assertThat(Files.readString(folder.resolve("out/html/index.html")))
                .contains("<table><tbody><tr><th>from</th><th>to</th></tr><tr><td>x</td><td>y<br/>more</td></tr></tbody></table>")
                .contains("<p>Maps:</p>").contains("<p>done</p>");
    }

    // ---- nesting tags

    @Test
    void aLineOfOnlyATagIndentsWhatFollowsAndClosingGoesBack() {
        List<Block> blocks = RichText.parse("<loop>\ntext\n<if a <> b>\ndeep\n</if a <> b>\n</loop>\n</loop>\nout\n<x>\n<y>\n<section end>\nreset", rules);

        assertThat(blocks.stream().map(b -> b.indent).collect(Collectors.toList()))
                .containsExactly(0, 1, 1, 2, 1, 0, 0, 0, 0, 1, 0);
        assertThat(blocks.get(0).inlines.get(0).text).isEqualTo("<loop>");
        assertThat(blocks.get(2).inlines.get(0).text).isEqualTo("<if a <> b>");
        assertThat(blocks.get(blocks.size() - 1).inlines.get(0).text).isEqualTo("reset");
    }

    @ParameterizedTest
    @ValueSource(strings = {"<sql>select 1</sql>", "<#>note</#>", "<code>x</code>", "<ass>a.png</ass>", "<tbl>\n|a|\n</tbl>", "text <b> text", "<3>"})
    void knownTagsAndTagsInsideTextDoNotNest(String text) {
        assertThat(RichText.parse(text + "\nafter", rules)).allMatch(b -> b.indent == 0);
    }

    @Test
    void sqlListsAndTablesInsideAnIndentedRegionAreIndented() throws Exception {
        String text = "<loop>\n<sql>select 1</sql>\n- a\n- b\n<tbl>\n|h|\n|c|\n</tbl>\n</loop>\nafter";
        List<Block> blocks = RichText.parse(text, rules);
        assertThat(blocks.stream().map(b -> b.indent).collect(Collectors.toList())).containsExactly(0, 1, 1, 1, 0, 0);

        String html = html(text, Assets.none());
        assertThat(html).contains("<div style=\"margin-left: 20px\"><pre class=\"sql\"><strong>select</strong> 1</pre></div>")
                .contains("<div style=\"margin-left: 20px\"><ul><li>a</li><li>b</li></ul></div>")
                .contains("<div style=\"margin-left: 20px\"><table>").contains("<p>&lt;/loop&gt;</p><p>after</p>");
        assertThat(storage(text, Assets.none())).contains("<div style=\"margin-left: 20px\"><ac:structured-macro ac:name=\"code\">");
    }

    @Test
    void indentGrowsByTwentyPixelsPerLevel() throws Exception {
        assertThat(html("<a>\n<b>\ntext", Assets.none())).contains("<div style=\"margin-left: 40px\"><p>text</p></div>");
    }

    // ---- multi-line note

    @Test
    void aNoteBetweenLinesOfItsOwnHoldsBlankLinesSqlAndTables() {
        List<Block> blocks = RichText.parse("<#>\nfirst {var}\n\n<sql>select 1</sql>\n\n<tbl>\n|a|\n|b|\n</tbl>\n- x\n</#>\nafter", rules);

        assertThat(blocks).hasSize(2);
        Block note = blocks.get(0);
        assertThat(note.style).isEqualTo(KeywordRules.PanelStyle.NOTE);
        assertThat(note.children).hasSize(4);
        assertThat(note.children.get(0).inlines.get(1).kind).isEqualTo(InlineKind.VARIABLE);
        assertThat(note.children.get(1).sql).isEqualTo("select 1");
        assertThat(note.children.get(2).rows).hasSize(2);
        assertThat(note.children.get(3).items).hasSize(1);
        assertThat(blocks.get(1).inlines.get(0).text).isEqualTo("after");
    }

    @Test
    void theInlineNoteFormWorksAsBefore() {
        assertThat(RichText.parse("<#>a remark</#>\nnext", rules)).hasSize(2);
        List<Block> ended = RichText.parse("<#> text\nsecond\n\nother", rules);
        assertThat(ended).hasSize(2);
        assertThat(ended.get(0).children).isEmpty();
        assertThat(RichText.parse("<#>\nunclosed note\n\nother", rules)).hasSize(2); // no </#> line: up to the blank line
    }

    @Test
    void aMultiLineNoteRendersAsOnePanel() throws Exception {
        assertThat(html("<#>\none\n\ntwo\n</#>", Assets.none())).contains("<div class=\"panel note\"><p>one</p><p>two</p></div>");
        assertThat(storage("<#>\none\n\ntwo\n</#>", Assets.none()))
                .contains("<ac:structured-macro ac:name=\"note\"><ac:rich-text-body><p>one</p><p>two</p></ac:rich-text-body>");
    }

    // ---- <ass>

    @Test
    void assTagIsTheSameAsAnAssetReference() throws Exception {
        Path model = Files.createDirectories(folder.resolve("model"));
        Files.createDirectories(model.resolve("Assets/docs"));
        Files.write(model.resolve("Assets/x.png"), png());
        Files.write(model.resolve("Assets/docs/SLA.pdf"), new byte[] {1});

        for (String path : List.of("x.png", "docs/SLA.pdf", "missing.png")) {
            String tag = html("See <ass>" + path + "</ass>.", Assets.in(model));
            String reference = html("See [[asset:" + path + "]].", Assets.in(model));
            assertThat(tag).isEqualTo(reference);
        }
        assertThat(html("<ass>x.png</ass>", Assets.in(model))).contains("<img class=\"asset\" src=\"assets/x.png\"");
        assertThat(html("<ass>missing.png</ass>", Assets.in(model))).contains("[missing asset: missing.png]");
        assertThat(storage("<ass>x.png</ass>", Assets.in(model))).contains("<ri:attachment ri:filename=\"x.png\"/>");
    }

    @Test
    void anAssPathMayBeBrokenOverLinesAndIsFoundInCellsAndNotes() throws Exception {
        Path model = Files.createDirectories(folder.resolve("model"));
        Files.createDirectories(model.resolve("Assets/docs"));
        Files.write(model.resolve("Assets/docs/flow.png"), png());

        List<Inline> inlines = RichText.inlines("<ass> docs/\n  flow.png </ass>", rules);
        assertThat(inlines).singleElement().satisfies(i -> {
            assertThat(i.kind).isEqualTo(InlineKind.ASSET);
            assertThat(i.text).isEqualTo("docs/flow.png");
        });
        String html = html("<#><ass>docs/flow.png</ass></#>\n\n<tbl>\n|a|\n|<ass>docs/\nflow.png</ass>|\n</tbl>", Assets.in(model));
        assertThat(html.split("src=\"assets/flow.png\"", -1)).hasSize(3);
    }

    @Test
    void assUrlsAreLinksOrPictures() throws Exception {
        assertThat(kinds(RichText.inlines("<ass>https://example.org/a.pdf</ass>", rules))).containsExactly(InlineKind.URL);
        assertThat(kinds(RichText.inlines("<ass>https://example.org/a.PNG?x=1&y=2</ass>", rules))).containsExactly(InlineKind.IMAGE_URL);
        assertThat(kinds(RichText.inlines("<ass>http://example.org/a.svg</ass>", rules))).containsExactly(InlineKind.IMAGE_URL);

        assertThat(html("<ass>https://example.org/a.pdf</ass>", Assets.none()))
                .contains("<a href=\"https://example.org/a.pdf\">https://example.org/a.pdf</a>");
        assertThat(html("<ass>https://example.org/a.png?x=1&y=2</ass>", Assets.none()))
                .contains("<img class=\"asset\" src=\"https://example.org/a.png?x=1&amp;y=2\"");
        assertThat(storage("<ass>https://example.org/a.png?x=1&y=2</ass>", Assets.none()))
                .contains("<ac:image><ri:url ri:value=\"https://example.org/a.png?x=1&amp;y=2\"/></ac:image>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/etc/passwd", "C:\\secret.png", "..\\secret.png", "a/../../secret.png", "../x.png"})
    void absoluteAndEscapingPathsAreUnsupportedAndReported(String path) throws Exception {
        assertThat(kinds(RichText.inlines("<ass>" + path + "</ass>", rules))).containsExactly(InlineKind.BAD_ASSET);

        Exporter exporter = export("<ass>" + path + "</ass>", Assets.none(), Format.HTML);
        assertThat(Files.readString(folder.resolve("out/html/index.html")))
                .contains("<span style=\"color: rgb(191,38,0);\">[Unsupported asset: " + path.replace("&", "&amp;") + "]</span>");
        assertThat(exporter.problems()).singleElement().asString().contains("not supported").contains("\"View\"");
    }

    @Test
    void pathsThatStayInsideAreFine() {
        assertThat(kinds(RichText.inlines("<ass>a/../b.png</ass>", rules))).containsExactly(InlineKind.ASSET);
        assertThat(kinds(RichText.inlines("<ass>./b.png</ass>", rules))).containsExactly(InlineKind.ASSET);
    }

    @Test
    void theUnsupportedAssetTextIsTranslated() {
        assertThat(KeywordRules.parse("language cs").texts().get("unsupported-asset")).isEqualTo("Nepodporovaná příloha");
        assertThat(rules.texts().get("unsupported-asset")).isEqualTo("Unsupported asset");
    }

    // ---- the assets folder

    @Test
    void theAssetsRuleIsReadAndWrittenBack() {
        KeywordRules parsed = KeywordRules.parse("assets shared files\nassets \"My Files\"");

        assertThat(parsed.assetsFolder()).isEqualTo("My Files");
        assertThat(parsed.rules().stream().filter(r -> r.kind == KeywordRules.Kind.ASSETS)).singleElement()
                .satisfies(r -> assertThat(r.line()).isEqualTo("assets \"My Files\""));
        assertThat(KeywordRules.parse(parsed.format()).assetsFolder()).isEqualTo("My Files");
        assertThat(KeywordRules.parse("assets files").assetsFolder()).isEqualTo("files");
        assertThat(rules.assetsFolder()).isNull();
        assertThat(KeywordRules.parseFile("assets Files").withAssets("other").assetsFolder()).isEqualTo("other");
        assertThatThrownBy(() -> KeywordRules.parse("assets")).hasMessageContaining("Line 1").hasMessageContaining("assets <folder>");
    }

    @Test
    void aRulesFileKeepsTheAssetsInsideTheModelsFolder(@TempDir Path elsewhere) throws Exception {
        for (String outside : List.of("../shared", "/etc", "C:\\Users", "a/../../b", "file:/x")) {
            assertThatThrownBy(() -> KeywordRules.parse("assets " + outside)).hasMessageContaining("inside the model's folder");
        }
        assertThat(KeywordRules.parse("assets a/../b").assetsFolder()).isEqualTo("a/../b");
        // the command line may name any folder
        assertThat(KeywordRules.defaults().withAssets(elsewhere.toString()).assetsFolder()).isEqualTo(elsewhere.toString());
        Path model = Files.createDirectories(folder.resolve("m"));
        Files.write(elsewhere.resolve("secret.png"), png());
        Assets outside = Assets.in(model, model.relativize(elsewhere).toString());
        assertThat(outside.find("secret.png", "p")).isNull();
        assertThat(outside.problems()).anyMatch(p -> p.contains("outside the model's folder"));
    }

    @Test
    void theFolderIsRelativeToTheModelOrAbsolute(@TempDir Path elsewhere) throws Exception {
        Path model = Files.createDirectories(folder.resolve("model"));
        Files.createDirectories(model.resolve("Assets"));
        Files.write(model.resolve("Assets/default.png"), png());
        Files.createDirectories(model.resolve("Pictures/sub"));
        Files.write(model.resolve("Pictures/sub/own.png"), png());
        Files.write(elsewhere.resolve("abs.png"), png());

        assertThat(Assets.in(model).find("default.png", "p")).isNotNull();
        Assets relative = Assets.in(model, "Pictures");
        assertThat(relative.find("sub/own.png", "p")).isNotNull();
        assertThat(relative.find("default.png", "p")).isNull(); // the Assets folder is no longer read
        assertThat(Assets.in(model, elsewhere.toString()).find("abs.png", "p")).isNotNull();
        assertThat(Assets.in(null, elsewhere.toString()).find("abs.png", "p")).isNotNull();
        assertThat(Assets.in(model, "Assets/../Pictures").find("sub/own.png", "p")).isNotNull();

        Assets none = Assets.in(model, "Nothing");
        assertThat(none.find("x.png", "p")).isNull();
        assertThat(none.problems()).singleElement().asString().contains("assets folder Nothing does not exist");
        assertThat(Assets.in(model, null).find("default.png", "p")).isNotNull();
    }

    @Test
    void theFolderOfTheRuleIsUsedForTheExport(@TempDir Path model) throws Exception {
        Files.createDirectories(model.resolve("Pictures"));
        Files.write(model.resolve("Pictures/x.png"), png());

        String html = html("<ass>x.png</ass>", Assets.in(model, KeywordRules.parse("assets Pictures").assetsFolder()));

        assertThat(html).contains("src=\"assets/x.png\"");
        assertThat(Files.exists(folder.resolve("out/html/assets/x.png"))).isTrue();
    }

    // ---- SQL colours

    @Test
    void sqlKeywordsAtTheStartOfALineAreBoldAndTokensAreColoured() throws Exception {
        String html = html("<sql>SELECT a, 'ACTIVE' FROM [orders] o\n  left join {other} x ON x.id = o.id\n"
                + "WHERE name = 'hello' and\n  order by a\nselected, andy, from_x</sql>", Assets.none());

        assertThat(html).contains("<pre class=\"sql\"><strong>SELECT</strong> a, <span style=\"color: rgb(0,102,68);\">'ACTIVE'</span> "
                + "FROM <span style=\"color: rgb(7,71,166);\">[orders]</span> o\n")
                .contains("  <strong>left join</strong> <span style=\"color: rgb(101,84,192);\">{other}</span> x ON x.id = o.id\n")
                .contains("<strong>WHERE</strong> name = 'hello' and\n")
                .contains("  <strong>order by</strong> a\nselected, andy, from_x</pre>");
    }

    @Test
    void sqlIsEscapedAndKeepsTheBlockStyleAndConfluenceItsCodeMacro() throws Exception {
        rules = KeywordRules.parse("sql background=#f4f5f7 size=90%");
        String text = "<sql>select a < b, '<X>' from t where {x&y} = 'A&B'</sql>";
        String html = html(text, Assets.none());

        assertThat(html).contains("<pre class=\"sql\" style=\"background-color: rgb(244,245,247);\"><span style=\"font-size: 90%;\">"
                + "<strong>select</strong> a &lt; b, '&lt;X&gt;' from t where <span style=\"color: rgb(101,84,192);\">{x&amp;y}</span> = 'A&amp;B'"
                + "</span></pre>");
        assertThat(storage(text, Assets.none())).contains("<![CDATA[select a < b, '<X>' from t where {x&y} = 'A&B']]>")
                .doesNotContain("<strong>select");
    }

    // ---- 'CONSTANT'

    @ParameterizedTest
    @ValueSource(strings = {"'ACTIVE'", "'TYPE_1'", "'NVL(X)'", "'A:B'", "'X'"})
    void constantsAreCapitalsDigitsAndFewSigns(String text) {
        assertThat(kinds(RichText.inlines("state " + text + " ok", rules))).contains(InlineKind.CONSTANT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"'hello'", "'Mixed'", "'2024'", "'A B'", "'a-B'", "'_'", "'()'"})
    void otherQuotedTextIsNoConstant(String text) {
        assertThat(kinds(RichText.inlines("state " + text + " ok", rules))).doesNotContain(InlineKind.CONSTANT);
    }
}
