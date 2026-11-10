package io.github.p3chys.documentationarchi;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.render.Exporter;
import io.github.p3chys.documentationarchi.render.Exporter.Format;
import io.github.p3chys.documentationarchi.text.KeywordRules;
import io.github.p3chys.documentationarchi.text.KeywordRules.PanelStyle;
import io.github.p3chys.documentationarchi.text.RichText;
import io.github.p3chys.documentationarchi.text.RichText.Block;
import io.github.p3chys.documentationarchi.text.RichText.Inline;
import io.github.p3chys.documentationarchi.text.RichText.InlineKind;

/** Lists, tables, emphasis, links, code and references, and the old syntax that must keep its meaning. */
class RichSyntaxTest {

    private final KeywordRules rules = KeywordRules.defaults();

    @TempDir
    Path folder;

    private static List<InlineKind> kinds(List<Inline> inlines) {
        return inlines.stream().map(i -> i.kind).collect(Collectors.toList());
    }

    @Test
    void bulletAndNumberedListsWithContinuationLines() {
        List<Block> blocks = RichText.parse("Steps:\n- one\n* two\n  goes on\n1. first\n2. second\nafter", rules);

        assertThat(blocks).hasSize(4);
        assertThat(blocks.get(1).items).hasSize(2);
        assertThat(blocks.get(1).numbered).isFalse();
        assertThat(kinds(blocks.get(1).items.get(1))).containsExactly(InlineKind.TEXT, InlineKind.BREAK, InlineKind.TEXT);
        assertThat(blocks.get(2).numbered).isTrue();
        assertThat(blocks.get(2).items).hasSize(2);
        assertThat(blocks.get(3).inlines.get(0).text).isEqualTo("after");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1 degrees", "-item", "1.5 million", "2026 was a year", "*note* first", "-- a comment"})
    void linesThatOnlyLookLikeItemsStayText(String text) {
        assertThat(RichText.parse(text, rules)).singleElement().satisfies(b -> assertThat(b.items).isNull());
    }

    @Test
    void pipeTableNeedsTheLineOfDashes() {
        List<Block> blocks = RichText.parse("| Name | File |\n|---|:---:|\n| a \\| b | [[asset:x.png|the x]] |\n| c |", rules);

        Block table = blocks.get(0);
        assertThat(table.rows).hasSize(3);
        assertThat(table.rows.get(0)).hasSize(2);
        assertThat(table.rows.get(1).get(0).get(0).text).isEqualTo("a | b");
        assertThat(table.rows.get(1).get(1).get(0).kind).isEqualTo(InlineKind.ASSET);
        assertThat(table.rows.get(1).get(1).get(0).label).isEqualTo("the x");
        assertThat(table.rows.get(2)).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a | b", "| a | b |", "a | b\nc | d", "Title\n---", "x | y\n--- no"})
    void pipesInOrdinaryTextAreNoTable(String text) {
        assertThat(RichText.parse(text, rules)).allSatisfy(b -> assertThat(b.rows).isNull());
    }

    @Test
    void boldAndItalicHoldOtherTokens() {
        List<Inline> inlines = RichText.inlines("**see [ORDERS]** and *only {user}* #risk", rules);

        assertThat(kinds(inlines)).containsExactly(InlineKind.BOLD, InlineKind.TEXT, InlineKind.ITALIC, InlineKind.TEXT,
                InlineKind.TAG);
        assertThat(kinds(inlines.get(0).children)).containsExactly(InlineKind.TEXT, InlineKind.TABLE);
        assertThat(kinds(inlines.get(2).children)).containsExactly(InlineKind.TEXT, InlineKind.VARIABLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"select * from t where a * b > 2", "count(*) and count(*)", "2*3*4", "x ** 2 ** 3",
            "a*b and c*d", "SELECT *, t.* FROM t", "* not a list", "***", "5 * 3 = 15 *"})
    void starsInSqlAndArithmeticStayStars(String text) {
        assertThat(RichText.inlines(text, rules)).noneMatch(i -> i.kind == InlineKind.BOLD || i.kind == InlineKind.ITALIC);
    }

    @Test
    void onlyBracketsFollowedByAParenthesisAreLinks() {
        List<Inline> link = RichText.inlines("the [spec](https://example.com/a?b=1) here", rules);
        assertThat(kinds(link)).containsExactly(InlineKind.TEXT, InlineKind.LINK, InlineKind.TEXT);
        assertThat(link.get(1).text).isEqualTo("https://example.com/a?b=1");
        assertThat(link.get(1).label).isEqualTo("spec");

        assertThat(kinds(RichText.inlines("[ORDERS] (the table)", rules))).containsExactly(InlineKind.TABLE, InlineKind.TEXT);
        assertThat(kinds(RichText.inlines("[ORDERS](id)", rules))).containsExactly(InlineKind.TABLE, InlineKind.TEXT);
    }

    @Test
    void referencesByIdWithOptionalLabelAndOldBracketsKeepTheirMeaning() {
        List<Inline> refs = RichText.inlines("[[ref:id-1]] [[ REF: id-2 | the portal ]] [[risk]] [[asset:a.png]]", rules);

        assertThat(kinds(refs)).containsExactly(InlineKind.REF, InlineKind.TEXT, InlineKind.REF, InlineKind.TEXT,
                InlineKind.TAG, InlineKind.TEXT, InlineKind.ASSET);
        assertThat(refs.get(0).text).isEqualTo("id-1");
        assertThat(refs.get(0).label).isNull();
        assertThat(refs.get(2).text).isEqualTo("id-2");
        assertThat(refs.get(2).label).isEqualTo("the portal");
    }

    @Test
    void codeBlocksKeepTheirTextIncludingBlankLinesAndStars() {
        List<Block> blocks = RichText.parse("Code:\n```java\nint a = 2 * 3;\n\n// **not bold**\n```\nafter\n"
                + "<code lang=\"xml\"><a>[x]</a></code>\n```sql\nselect *\n```", rules);

        assertThat(blocks).hasSize(5);
        assertThat(blocks.get(1).code).isEqualTo("int a = 2 * 3;\n\n// **not bold**");
        assertThat(blocks.get(1).language).isEqualTo("java");
        assertThat(blocks.get(2).inlines.get(0).text).isEqualTo("after");
        assertThat(blocks.get(3).code).isEqualTo("<a>[x]</a>");
        assertThat(blocks.get(3).language).isEqualTo("xml");
        assertThat(blocks.get(4).sql).isEqualTo("select *"); // ```sql looks like any SQL block
    }

    @Test
    void panelCanHoldAList() {
        Block panel = RichText.parse("WARNING: check\n- one\n- two", rules).get(0);

        assertThat(panel.style).isEqualTo(PanelStyle.WARNING);
        assertThat(panel.children).hasSize(2);
        assertThat(panel.children.get(1).items).hasSize(2);
        assertThat(RichText.parse("WARNING: just text", rules).get(0).children).isEmpty();
    }

    private String export(Format format, String text) throws Exception {
        DocPage page = new DocPage("v", "View");
        page.entries.add(new Entry("a", "Portal", "Node", text, List.of(), List.of(), List.of()));
        Doc doc = new Doc("Model", List.of(page));
        doc.names.put("gone", "Old System");
        new Exporter(doc, rules).export(folder, EnumSet.of(format));
        return Files.readString(folder.resolve(format == Format.HTML ? "html/index.html" : "confluence/pages/index.xml"));
    }

    private static final String TEXT = "- **one** and *two*\n1. [spec](https://x.org)\n\n| A | B |\n|---|---|\n| 1 | 2 |\n\n"
            + "```python\nprint(1)\n```\n[[ref:a]], [[ref:a|it]], [[ref:gone]], [[ref:nothing]]";

    @Test
    void everyFormatRendersTheNewSyntax() throws Exception {
        String html = export(Format.HTML, TEXT);
        assertThat(html).contains("<ul><li><strong>one</strong> and <em>two</em></li></ul>")
                .contains("<ol><li><a href=\"https://x.org\">spec</a></li></ol>")
                .contains("<table><tbody><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2</td></tr></tbody></table>")
                .contains("<pre class=\"code\">print(1)</pre>")
                .contains("<a href=\"#e-a\">Portal</a>, <a href=\"#e-a\">it</a>, Old System, ")
                .contains("[missing reference: nothing]");

        String storage = export(Format.CONFLUENCE, TEXT);
        assertThat(storage).contains("<ac:structured-macro ac:name=\"code\"><ac:parameter ac:name=\"language\">py"
                + "</ac:parameter><ac:plain-text-body><![CDATA[print(1)]]></ac:plain-text-body></ac:structured-macro>")
                .contains("<ac:link ac:anchor=\"e-a\"><ac:link-body>Portal</ac:link-body></ac:link>")
                .contains("<ul><li><strong>one</strong>");
        assertThat(export(Format.CONFLUENCE, "```cobol\nMOVE A\n```")).contains("<ac:structured-macro ac:name=\"code\">"
                + "<ac:plain-text-body>");

        new Exporter(new Doc("Model", List.of(new DocPage("v", "View"))), rules).export(folder, EnumSet.of(Format.PDF));
    }
}
