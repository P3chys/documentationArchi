package io.github.p3chys.documentationarchi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.p3chys.documentationarchi.text.KeywordRules;
import io.github.p3chys.documentationarchi.text.KeywordRules.PanelStyle;
import io.github.p3chys.documentationarchi.text.KeywordRules.TagColour;
import io.github.p3chys.documentationarchi.text.RichText;
import io.github.p3chys.documentationarchi.text.RichText.Block;
import io.github.p3chys.documentationarchi.text.RichText.Inline;
import io.github.p3chys.documentationarchi.text.RichText.InlineKind;

class RichTextTest {

    private final KeywordRules rules = KeywordRules.defaults();

    @Test
    void blankLinesSplitParagraphsAndLineBreaksStay() {
        List<Block> blocks = RichText.parse("one\ntwo\n\nthree", rules);

        assertThat(blocks).hasSize(2);
        assertThat(kinds(blocks.get(0))).containsExactly(InlineKind.TEXT, InlineKind.BREAK, InlineKind.TEXT);
    }

    @Test
    void lineWithKeywordBecomesPanelUntilBlankLine() {
        List<Block> blocks = RichText.parse("before\nwarning: careful\nstill careful\n\nafter", rules);

        assertThat(blocks).hasSize(3);
        assertThat(blocks.get(0).style).isNull();
        assertThat(blocks.get(1).style).isEqualTo(PanelStyle.WARNING);
        assertThat(blocks.get(1).title).isEqualTo("warning");
        assertThat(blocks.get(1).inlines.get(0).text).isEqualTo("careful");
        assertThat(blocks.get(1).inlines.get(2).text).isEqualTo("still careful");
        assertThat(blocks.get(2).style).isNull();
    }

    @Test
    void nextKeywordStartsNewPanel() {
        List<Block> blocks = RichText.parse("NOTE: a\nTIP: b", rules);

        assertThat(blocks).extracting(b -> b.style).containsExactly(PanelStyle.NOTE, PanelStyle.TIP);
    }

    @Test
    void unknownPrefixIsPlainText() {
        assertThat(RichText.parse("Owner: Alice", rules).get(0).style).isNull();
    }

    @ParameterizedTest
    @CsvSource({"#risk here", "[[risk]] here", "#RISK here", "[[ Risk ]] here"})
    void bothTagSyntaxesWork(String text) {
        Inline tag = RichText.inlines(text, rules).get(0);

        assertThat(tag.kind).isEqualTo(InlineKind.TAG);
        assertThat(tag.colour).isEqualTo(TagColour.RED);
    }

    @ParameterizedTest
    @CsvSource({"see #unknown tag", "[[unknown]]", "issue#risk", "a &#risk"})
    void otherHashesStayText(String text) {
        assertThat(RichText.inlines(text, rules)).allMatch(i -> i.kind == InlineKind.TEXT);
    }

    @Test
    void urlsBecomeLinksWithoutTrailingPunctuation() {
        List<Inline> inlines = RichText.inlines("See https://example.com/a#risk, then", rules);

        assertThat(inlines).extracting(i -> i.kind).containsExactly(InlineKind.TEXT, InlineKind.URL, InlineKind.TEXT);
        assertThat(inlines.get(1).text).isEqualTo("https://example.com/a#risk");
        assertThat(inlines.get(2).text).isEqualTo(", then");
    }

    @Test
    void sqlTagBecomesCodeBlockKeepingItsTextAsItIs() {
        List<Block> blocks = RichText.parse("Query:\n<SQL>\nselect *\n\n  from [t] -- #risk\n</SQL>\nafter", rules);

        assertThat(blocks).hasSize(3);
        assertThat(blocks.get(1).sql).isEqualTo("select *\n\n  from [t] -- #risk");
        assertThat(blocks.get(2).inlines.get(0).text).isEqualTo("after");
    }

    @Test
    void sqlWithoutClosingTagRunsToTheEnd() {
        assertThat(RichText.parse("<sql>select 1\n\nselect 2", rules).get(0).sql).isEqualTo("select 1\n\nselect 2");
    }

    @Test
    void noteTagBecomesNotePanelWithoutHeading() {
        List<Block> blocks = RichText.parse("before <#>about {x}\nmore</#> after", rules);

        assertThat(blocks).hasSize(3);
        assertThat(blocks.get(1).style).isEqualTo(PanelStyle.NOTE);
        assertThat(blocks.get(1).title).isNull();
        assertThat(blocks.get(1).inlines).extracting(i -> i.kind)
                .containsExactly(InlineKind.TEXT, InlineKind.VARIABLE, InlineKind.BREAK, InlineKind.TEXT);
    }

    @Test
    void noteTagWithoutClosingTagEndsAtBlankLine() {
        List<Block> blocks = RichText.parse("<#> a remark\nsecond line\n\nnormal text", rules);

        assertThat(blocks).extracting(b -> b.style).containsExactly(PanelStyle.NOTE, null);
    }

    @Test
    void tokensKeepTheirTextAndGetTheirKind() {
        List<Inline> inlines = RichText.inlines("[orders] = 'NEW' for {user}", rules);

        assertThat(inlines).extracting(i -> i.kind).containsExactly(InlineKind.TABLE, InlineKind.TEXT, InlineKind.CONSTANT,
                InlineKind.TEXT, InlineKind.VARIABLE);
        assertThat(inlines.get(0).text).isEqualTo("[orders]");
        assertThat(inlines.get(2).text).isEqualTo("'NEW'");
        assertThat(inlines.get(4).text).isEqualTo("{user}");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"don't and won't", "the '90s and rock'n'roll", "[[unknown]]"})
    void apostrophesAndUnknownTagsAreNotTokens(String text) {
        assertThat(RichText.inlines(text, rules)).allMatch(i -> i.kind == InlineKind.TEXT);
    }

    @Test
    void descriptionAndTechnicallyAreBoldAnywhereInTheText() {
        List<Inline> inlines = RichText.inlines("Description: one\nand technically: two, Undescription: no", rules);

        assertThat(inlines).filteredOn(i -> i.kind == InlineKind.STYLED).extracting(i -> i.text)
                .containsExactly("Description:", "technically:");
        assertThat(RichText.parse("Description: one", rules).get(0).style).isNull();
    }

    @Test
    void boldTextCanBeConfigured() {
        KeywordRules custom = KeywordRules.parse("bold Key point\n");

        assertThat(RichText.inlines("a key point here, key pointer", custom)).extracting(i -> i.kind)
                .containsExactly(InlineKind.TEXT, InlineKind.STYLED, InlineKind.TEXT);
    }

    @Test
    void customRulesAllowSpacesInPanelKeywords() {
        KeywordRules custom = KeywordRules.parse("panel note Open question\ntag blue idea\n");

        assertThat(RichText.parse("Open question: why?", custom).get(0).style).isEqualTo(PanelStyle.NOTE);
        assertThat(RichText.inlines("#idea", custom).get(0).colour).isEqualTo(TagColour.BLUE);
    }

    @ParameterizedTest
    @CsvSource({"panel purple WARNING,unknown style", "banner red x,must start with panel", "tag red,expected panel"})
    void invalidRulesNameTheirLine(String rule, String message) {
        assertThatThrownBy(() -> KeywordRules.parse("# comment\n" + rule))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Line 2").hasMessageContaining(message);
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
        "See [[asset:docs/SLA smlouva.pdf]] here;docs/SLA smlouva.pdf;",
        "See [[ Asset: flow.png | The flow ]] here;flow.png;The flow",
        "See asset:flow.png here;flow.png;",
        "See ASSET:diagrams/flow.png. here;diagrams/flow.png;",
        "See [[asset:flow.png|]] here;flow.png;"})
    void assetReferencesNameTheFileAndOptionallyALabel(String text, String file, String label) {
        List<Inline> inlines = RichText.inlines(text, rules);

        assertThat(inlines).extracting(i -> i.kind).containsExactly(InlineKind.TEXT, InlineKind.ASSET, InlineKind.TEXT);
        assertThat(inlines.get(1).text).isEqualTo(file);
        assertThat(inlines.get(1).label).isEqualTo(label);
        assertThat(inlines.get(0).text).isEqualTo("See ");
        assertThat(inlines.get(2).text).endsWith(" here");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"disasset:x.png", "https://example.com/asset:x.png", "asset: x.png", "asset:"})
    void otherAssetWordsStayText(String text) {
        assertThat(RichText.inlines(text, rules)).noneMatch(i -> i.kind == InlineKind.ASSET);
    }

    @Test
    void assetInSqlStaysSql() {
        assertThat(RichText.parse("<sql>select 'asset:x.png'</sql>", rules).get(0).sql).isEqualTo("select 'asset:x.png'");
    }

    private static List<InlineKind> kinds(Block block) {
        return block.inlines.stream().map(i -> i.kind).collect(Collectors.toList());
    }
}
