package io.github.p3chys.documentationarchi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.p3chys.documentationarchi.doc.Catalogs;
import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.doc.Generated;
import io.github.p3chys.documentationarchi.doc.Property;
import io.github.p3chys.documentationarchi.render.Exporter;
import io.github.p3chys.documentationarchi.render.Exporter.Format;
import io.github.p3chys.documentationarchi.text.Catalog;
import io.github.p3chys.documentationarchi.text.KeywordRules;

/** The {@code catalog} rule: a page with a table of the elements of a type. */
class CatalogTest {

    @TempDir
    Path folder;

    private static Entry entry(String id, String name, String type, String documentation, String owner) {
        return new Entry(id, name, type, documentation, List.of(new Property("Owner", owner)), List.of(), List.of());
    }

    private static Doc doc() {
        DocPage overview = new DocPage("overview", "Model");
        DocPage view = new DocPage("v", "View");
        view.entries.add(entry("b", "Billing", "Application Component", "Bills <everything>.\n\nSecond paragraph.", "Eva"));
        view.entries.add(entry("a", "alpha", "Application Component", "First #todo", "Tom"));
        view.entries.add(entry("s", "Pay", "Application Service", "Pays", "Tom"));
        view.entries.add(entry("n", "Server", "Node", "Metal", "Ann"));
        return new Doc("Model", List.of(overview, view));
    }

    @Test
    void theRuleIsReadAndWrittenBack() {
        KeywordRules rules = KeywordRules.parse(
                "catalog \"Applications\" type=Application Component|Application Service folder=Views/Core columns=name,Owner,description");
        Catalog c = rules.catalogs().get(0);

        assertThat(c.title).isEqualTo("Applications");
        assertThat(c.types).containsExactly("Application Component", "Application Service");
        assertThat(c.folder).isEqualTo("Views/Core");
        assertThat(c.columns).containsExactly("name", "Owner", "description");
        assertThat(KeywordRules.parse(rules.format()).rules()).isEqualTo(rules.rules());
        assertThat(c.matches("application component")).isTrue();
        assertThat(c.matches("Node")).isFalse();
        assertThat(Catalog.parse("\"All\" type=Application* columns=name").matches("Application Service")).isTrue();
    }

    @Test
    void mistakesNameTheLine() {
        assertThatThrownBy(() -> KeywordRules.parse("bold X\ncatalog Apps type=Node columns=name")).hasMessageContaining("Line 2");
        assertThatThrownBy(() -> KeywordRules.parse("catalog \"A\" columns=name")).hasMessageContaining("expected: catalog");
        assertThatThrownBy(() -> KeywordRules.parse("catalog \"A\" type=Node")).hasMessageContaining("expected: catalog");
        assertThatThrownBy(() -> KeywordRules.parse("catalog \"A\" type=Node type=Node columns=name")).hasMessageContaining("twice");
        assertThatThrownBy(() -> KeywordRules.parse("catalog \"A\" type= columns=name")).hasMessageContaining("expected type=");
    }

    @Test
    void rowsAreTheDescribedElementsOfTheTypeSortedByName() {
        Catalog c = Catalog.parse("\"Apps\" type=Application Component columns=name,Owner,description");
        Catalogs.Result result = Catalogs.build(doc(), List.of(c), KeywordRules.defaults().texts(), p -> Set.of());

        DocPage page = result.pages.get(0);
        assertThat(page.title).isEqualTo("Apps");
        assertThat(page.tables.get(0).header).containsExactly("Name", "Owner", "Description");
        assertThat(page.tables.get(0).rows).hasSize(2);
        assertThat(page.tables.get(0).rows.get(0).get(0).links.get(0).label).isEqualTo("alpha");
        assertThat(page.tables.get(0).rows.get(1).get(1).text).isEqualTo("Eva");
        assertThat(page.tables.get(0).rows.get(1).get(2).text).isEqualTo("Bills <everything>."); // the first paragraph
    }

    @Test
    void aFolderLimitsTheRowsAndAnEmptyCatalogIsLeftOutWithANote() {
        Catalog onlyAlpha = Catalog.parse("\"A\" type=Application* folder=Core columns=name");
        Catalog none = Catalog.parse("\"Nodes\" type=Business Actor columns=name");
        Catalogs.Result result = Catalogs.build(doc(), List.of(onlyAlpha, none), KeywordRules.defaults().texts(),
                path -> Set.of("a"));

        assertThat(result.pages).hasSize(1);
        assertThat(result.pages.get(0).tables.get(0).rows).hasSize(1);
        assertThat(result.notes).singleElement().asString().contains("\"Nodes\"").contains("left out");
        assertThatThrownBy(() -> Catalogs.build(doc(), List.of(onlyAlpha), KeywordRules.defaults().texts(), p -> {
            throw new IllegalArgumentException("folder not found");
        })).hasMessageContaining("catalog \"A\"").hasMessageContaining("folder not found");
    }

    @Test
    void thePageIsListedOnTheOverviewAndRenderedInAllFormats() throws Exception {
        Catalog c = Catalog.parse("\"Apps\" type=Application Component columns=name,Owner,description");
        Doc doc = Generated.with(doc(), Catalogs.build(doc(), List.of(c), KeywordRules.defaults().texts(), p -> Set.of()).pages);
        new Exporter(doc, KeywordRules.defaults()).export(folder, EnumSet.allOf(Format.class));

        assertThat(Files.readString(folder.resolve("html/index.html"))).contains("<a href=\"apps.html\">Apps</a>");
        String html = Files.readString(folder.resolve("html/apps.html"));
        assertThat(html).contains("<th>Name</th><th>Owner</th><th>Description</th>")
                .contains("<td><a href=\"view.html#e-b\">Billing</a></td><td>Eva</td><td><p>Bills &lt;everything&gt;.</p></td>");
        assertThat(html.indexOf("alpha")).isLessThan(html.indexOf("Billing"));
        assertThat(Files.readString(folder.resolve("confluence/pages/apps.xml")))
                .contains("<th>Owner</th>").contains("<td>Eva</td>");
        assertThat(Files.size(folder.resolve("model.pdf"))).isPositive();
    }
}
