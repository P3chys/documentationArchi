package io.github.p3chys.documentationarchi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.xml.sax.InputSource;

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.render.Exporter;
import io.github.p3chys.documentationarchi.render.Exporter.Format;
import io.github.p3chys.documentationarchi.text.KeywordRules;
import io.github.p3chys.documentationarchi.text.KeywordRules.CodeTheme;
import io.github.p3chys.documentationarchi.text.KeywordRules.Kind;
import io.github.p3chys.documentationarchi.text.KeywordRules.PanelStyle;
import io.github.p3chys.documentationarchi.text.KeywordRules.Rule;
import io.github.p3chys.documentationarchi.text.KeywordRules.TagColour;
import io.github.p3chys.documentationarchi.text.KeywordRules.TextStyle;
import io.github.p3chys.documentationarchi.text.KeywordRules.Token;
import io.github.p3chys.documentationarchi.text.RichText;
import io.github.p3chys.documentationarchi.text.RichText.Inline;
import io.github.p3chys.documentationarchi.text.RichText.InlineKind;

class KeywordStyleTest {

    private static final String RULES = "style color=#BF2600 background=#fe0 size=120% bold italic underline strike Due date\n"
            + "style size=80% Fine print\n"
            + "style italic \"bold\"\n";

    @TempDir
    Path folder;

    @Test
    void styleRuleReadsEveryOption() {
        Rule rule = KeywordRules.parse(RULES).rules().get(0);

        assertThat(rule.kind).isEqualTo(Kind.STYLE);
        assertThat(rule.keyword).isEqualTo("Due date");
        assertThat(rule.style).isEqualTo(new TextStyle("#bf2600", "#ffee00", 120, true, true, true, true));
    }

    @Test
    void quotedTextMayLookLikeAnOption() {
        Rule rule = KeywordRules.parse(RULES).rules().get(2);

        assertThat(rule.keyword).isEqualTo("bold");
        assertThat(rule.style.italic).isTrue();
        assertThat(rule.style.bold).isFalse();
        assertThat(rule.line()).isEqualTo("style italic \"bold\"");
    }

    @Test
    void boldRuleIsABoldStyleAndIsWrittenBackTheSameWay() {
        Rule rule = KeywordRules.parse("bold Description:").rules().get(0);

        assertThat(rule.style).isEqualTo(TextStyle.BOLD);
        assertThat(rule.line()).isEqualTo("bold Description:");
    }

    @Test
    void formattedRulesReadBackTheSame() {
        KeywordRules custom = KeywordRules.parse(KeywordRules.DEFAULTS + RULES + "panel tip Open question\n");

        assertThat(KeywordRules.parse(custom.format()).rules()).isEqualTo(custom.rules());
        assertThat(KeywordRules.parse(KeywordRules.defaults().format()).rules()).isEqualTo(KeywordRules.defaults().rules());
    }

    @Test
    void laterRuleForTheSameKeywordReplacesTheEarlierOne() {
        KeywordRules rules = KeywordRules.of(List.of(Rule.tag("risk", TagColour.RED), Rule.panel("risk", PanelStyle.NOTE),
                Rule.tag("RISK", TagColour.BLUE)));

        assertThat(rules.rules()).extracting(Rule::line).containsExactly("tag blue RISK", "panel note risk");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "style color=red x|not a colour",
        "style size=400% x|size must be",
        "style size=big x|size is a percentage",
        "style shadow=1 x|unknown option",
        "style bold|expected style options",
        "style|expected style options"})
    void invalidStyleRulesNameTheirLine(String rule, String message) {
        assertThatThrownBy(() -> KeywordRules.parse("# comment\n" + rule))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Line 2").hasMessageContaining(message);
    }

    @Test
    void styledTextIsFoundAnywhereIgnoringCaseAndTheLongestTextWins() {
        KeywordRules rules = KeywordRules.parse("style color=#ff0000 Due\n" + RULES);

        List<Inline> inlines = RichText.inlines("the DUE DATE is due, not overdue", rules);

        assertThat(inlines).filteredOn(i -> i.kind == InlineKind.STYLED).extracting(i -> i.text).containsExactly("DUE DATE", "due");
        assertThat(inlines.get(1).style.size).isEqualTo(120);
        assertThat(inlines.get(3).style.colour).isEqualTo("#ff0000");
    }

    @Test
    void stylesReachEveryOutput() throws Exception {
        DocPage page = new DocPage("v", "View");
        page.documentation = "Due date: Friday. Fine print here.";
        new Exporter(new Doc("Model", List.of(page)), KeywordRules.parse(RULES)).export(folder, EnumSet.allOf(Format.class));

        String html = Files.readString(folder.resolve("html/index.html"));
        assertThat(html).contains("<span style=\"color: rgb(191,38,0); background-color: rgb(255,238,0); padding: 0 2px; "
                        + "font-size: 120%; text-decoration: line-through;\"><strong><em><u>Due date</u></em></strong></span>")
                .contains("<span style=\"font-size: 80%;\">Fine print</span>");
        assertWellFormed(html.substring(html.indexOf("<html")));

        String storage = Files.readString(folder.resolve("confluence/pages/index.xml"));
        assertThat(storage).contains("<span style=\"color: rgb(191,38,0);\"><span style=\"background-color: rgb(255,238,0);\">"
                        + "<span style=\"font-size: 120%;\"><strong><em><u><span style=\"text-decoration: line-through;\">"
                        + "Due date</span></u></em></strong></span></span></span>")
                .contains("<small>Fine print</small>");
        assertWellFormed("<root xmlns:ac=\"urn:ac\" xmlns:ri=\"urn:ri\">" + storage + "</root>");

        try (PDDocument pdf = Loader.loadPDF(folder.resolve("model.pdf").toFile())) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("Due date").contains("Fine print");
        }
    }

    @Test
    void plainBoldStaysAStrongTag() throws Exception {
        DocPage page = new DocPage("v", "View");
        page.documentation = "Description: x";
        new Exporter(new Doc("Model", List.of(page)), KeywordRules.defaults()).export(folder, EnumSet.of(Format.HTML, Format.CONFLUENCE));

        assertThat(Files.readString(folder.resolve("html/index.html"))).contains("<p><strong>Description:</strong> x</p>");
        assertThat(Files.readString(folder.resolve("confluence/pages/index.xml"))).contains("<p><strong>Description:</strong> x</p>");
    }

    private static final String ELEMENTS = "token variable color=#ff0000 italic\n"
            + "token table\n"
            + "heading 1 color=#00ff00\n"
            + "heading 2 color=#0052cc size=120%\n"
            + "heading 3 italic\n"
            + "sql color=#ffffff background=#000000 size=90% theme=midnight linenumbers\n";

    @Test
    void elementRulesReadAndWriteBack() {
        KeywordRules rules = KeywordRules.parse(ELEMENTS);

        assertThat(rules.token(Token.VARIABLE)).isEqualTo(new TextStyle("#ff0000", null, 100, false, true, false, false));
        assertThat(rules.token(Token.TABLE)).isEqualTo(TextStyle.PLAIN);
        assertThat(rules.token(Token.CONSTANT)).isEqualTo(Token.CONSTANT.builtIn);
        assertThat(rules.heading(2).size).isEqualTo(120);
        assertThat(rules.heading(4)).isNull();
        assertThat(rules.sql().theme).isEqualTo(CodeTheme.MIDNIGHT);
        assertThat(rules.sql().lineNumbers).isTrue();
        assertThat(rules.rules()).extracting(Rule::line).containsExactly(ELEMENTS.split("\n"));
        assertThat(KeywordRules.parse(rules.format()).rules()).isEqualTo(rules.rules());
    }

    @Test
    void tokensKeepTheirBuiltInColoursWithoutRules() {
        assertThat(KeywordRules.parse("").token(Token.TABLE).colour).isEqualTo("#0747a6");
        assertThat(KeywordRules.parse("").sql()).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "token row color=#fff|token must be table, constant or variable",
        "heading 7 bold|heading level must be 1-4",
        "heading x|heading level must be",
        "heading 2 bold extra|unknown option \"extra\"",
        "sql theme=neon|unknown theme",
        "token table theme=midnight|unknown option",
        "frame red x|must start with panel"})
    void invalidElementRulesNameTheirLine(String rule, String message) {
        assertThatThrownBy(() -> KeywordRules.parse("# comment\n" + rule))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Line 2").hasMessageContaining(message);
    }

    @Test
    void elementStylesReachEveryOutput() throws Exception {
        DocPage page = new DocPage("v", "View");
        page.documentation = "<sql>select 1</sql>\n{user} in [orders]";
        page.notes.add("note");
        page.entries.add(new Entry("e", "Klient", "Node", "", List.of(), List.of(), List.of()));
        new Exporter(new Doc("Model", List.of(page)), KeywordRules.parse(ELEMENTS)).export(folder, EnumSet.allOf(Format.class));

        String html = Files.readString(folder.resolve("html/index.html"));
        assertThat(html).contains("<h1><span style=\"color: rgb(0,255,0);\">View</span></h1>")
                .contains("<h2><span style=\"color: rgb(0,82,204); font-size: 120%;\">Notes</span></h2>")
                .contains("<h3 id=\"e-e\"><em><small>")
                .contains("<pre class=\"sql\" style=\"background-color: rgb(0,0,0);\">"
                        + "<span style=\"color: rgb(255,255,255); font-size: 90%;\"><strong>select</strong> 1</span></pre>")
                .contains("<span style=\"color: rgb(255,0,0);\"><em>{user}</em></span> in [orders]");
        assertWellFormed(html.substring(html.indexOf("<html")));

        String storage = Files.readString(folder.resolve("confluence/pages/index.xml"));
        assertThat(storage).doesNotContain("<h1>")
                .contains("<h2><span style=\"color: rgb(0,82,204);\"><span style=\"font-size: 120%;\">Notes</span></span></h2>")
                .contains("<ac:parameter ac:name=\"language\">sql</ac:parameter><ac:parameter ac:name=\"theme\">Midnight"
                        + "</ac:parameter><ac:parameter ac:name=\"linenumbers\">true</ac:parameter>")
                .contains("<span style=\"color: rgb(255,0,0);\"><em>{user}</em></span> in [orders]");
        assertWellFormed("<root xmlns:ac=\"urn:ac\" xmlns:ri=\"urn:ri\">" + storage + "</root>");

        try (PDDocument pdf = Loader.loadPDF(folder.resolve("model.pdf").toFile())) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("select").contains("{user}").contains("Notes");
        }
    }

    private static void assertWellFormed(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(xml.getBytes("UTF-8"))));
    }
}
