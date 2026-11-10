package io.github.p3chys.documentationarchi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.p3chys.documentationarchi.doc.Catalogs;
import io.github.p3chys.documentationarchi.doc.DataIndex;
import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.DocTable;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.doc.Generated;
import io.github.p3chys.documentationarchi.doc.Property;
import io.github.p3chys.documentationarchi.doc.Relation;
import io.github.p3chys.documentationarchi.doc.Link;
import io.github.p3chys.documentationarchi.render.Exporter;
import io.github.p3chys.documentationarchi.render.Exporter.Format;
import io.github.p3chys.documentationarchi.text.KeywordRules;
import io.github.p3chys.documentationarchi.text.KeywordRules.Token;

/** The {@code index} rule: a page of the tables, variables and constants the descriptions mark. */
class DataIndexTest {

    @TempDir
    Path folder;

    private final KeywordRules rules = KeywordRules.defaults();

    private static Doc doc() {
        DocPage view = new DocPage("v", "Orders view");
        view.documentation = "Reads [orders] and {client_id}.";
        view.notes.add("Status is 'ACTIVE' here");
        view.properties.add(new Property("Source", "[Customers]"));
        view.entries.add(new Entry("a", "Loader", "Process",
                "Joins [ORDERS] with [items].\n\n<sql>select * from [Orders] o join [Items] i on i.x = 'NOT' where id = {not_counted}</sql>",
                List.of(new Property("Key", "{Client_ID}")), List.of(new Relation(true, "Flow", "", "Copies [Archive]",
                        new Link("b", "Sink"), false)), List.of()));
        view.entries.add(new Entry("b", "Sink", "Node", "Plain text, no tokens", List.of(), List.of(), List.of()));
        return new Doc("Model", List.of(new DocPage("overview", "Model"), view));
    }

    private static List<String> tokens(DocPage page) {
        return page.tables.get(0).rows.stream().map(r -> r.get(0).text).collect(Collectors.toList());
    }

    private static List<String> places(DocPage page, int row) {
        return page.tables.get(0).rows.get(row).get(1).links.stream().map(l -> l.label).collect(Collectors.toList());
    }

    @Test
    void theRuleIsReadAndWrittenBack() {
        KeywordRules parsed = KeywordRules.parse("index tables\nindex Variables\nindex constants");

        assertThat(parsed.indexes()).containsExactly(Token.TABLE, Token.VARIABLE, Token.CONSTANT);
        assertThat(KeywordRules.parse(parsed.format()).rules()).isEqualTo(parsed.rules());
        assertThatThrownBy(() -> KeywordRules.parse("bold X\nindex columns")).hasMessageContaining("Line 2")
                .hasMessageContaining("index tables");
        assertThat(rules.indexes()).isEmpty();
    }

    @Test
    void tablesAreGroupedWithoutCaseSortedAndLinkedToWhereTheyAreUsed() {
        DocPage page = DataIndex.build(doc(), List.of(Token.TABLE), rules).pages.get(0);

        assertThat(page.title).isEqualTo("Database tables");
        assertThat(tokens(page)).containsExactly("Archive", "Customers", "items", "orders");
        assertThat(places(page, 0)).containsExactly("Loader");
        assertThat(places(page, 1)).containsExactly("Orders view");
        assertThat(places(page, 2)).containsExactly("Loader"); // [items] and the SQL's [Items]
        assertThat(places(page, 3)).containsExactly("Orders view", "Loader");
    }

    @Test
    void variablesAndConstantsAreFoundOutsideSqlBlocksOnly() {
        Catalogs.Result result = DataIndex.build(doc(), List.of(Token.VARIABLE, Token.CONSTANT), rules);

        assertThat(tokens(result.pages.get(0))).containsExactly("client_id");
        assertThat(places(result.pages.get(0), 0)).containsExactly("Orders view", "Loader");
        assertThat(tokens(result.pages.get(1))).containsExactly("ACTIVE");
        assertThat(result.pages.get(1).title).isEqualTo("Constants");
    }

    @Test
    void anIndexThatFindsNothingIsLeftOutWithANote() {
        DocPage plain = new DocPage("v", "View");
        plain.documentation = "Nothing marked";
        Catalogs.Result result = DataIndex.build(new Doc("M", List.of(plain)), List.of(Token.TABLE), rules);

        assertThat(result.pages).isEmpty();
        assertThat(result.notes).singleElement().asString().contains("tables").contains("left out");
    }

    @Test
    void theTitleFollowsTheLanguage() {
        KeywordRules czech = KeywordRules.parse("language cs");
        assertThat(DataIndex.build(doc(), List.of(Token.TABLE), czech).pages.get(0).title).isEqualTo("Databázové tabulky");
    }

    @Test
    void thePageIsListedOnTheOverviewAndRenderedInAllFormats() throws Exception {
        Doc doc = Generated.with(doc(), DataIndex.build(doc(), List.of(Token.TABLE), rules).pages);
        new Exporter(doc, rules).export(folder, EnumSet.allOf(Format.class));

        assertThat(Files.readString(folder.resolve("html/index.html"))).contains("<a href=\"database-tables.html\">Database tables</a>");
        String html = Files.readString(folder.resolve("html/database-tables.html"));
        assertThat(html).contains("<th>Token</th><th>Used in</th>")
                .contains("<td>orders</td><td><a href=\"orders-view.html\">Orders view</a>, "
                        + "<small><span style=\"color: rgb(107,119,140);\">&lt;&lt;Process&gt;&gt;</span></small> "
                        + "<a href=\"orders-view.html#e-a\">Loader</a></td>");
        assertThat(Files.readString(folder.resolve("confluence/pages/database-tables.xml"))).contains("<td>Archive</td>");
        assertThat(Files.size(folder.resolve("model.pdf"))).isPositive();
    }

    @Test
    void tableCellsKeepTheirKind() {
        DocTable.Cell cell = DocTable.Cell.value("x");
        assertThat(cell.links).isEmpty();
        assertThat(cell.description).isFalse();
    }
}
