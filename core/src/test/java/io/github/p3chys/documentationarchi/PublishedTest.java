package io.github.p3chys.documentationarchi;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Picture;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.doc.Link;
import io.github.p3chys.documentationarchi.doc.Property;
import io.github.p3chys.documentationarchi.doc.Published;
import io.github.p3chys.documentationarchi.doc.Published.Change;
import io.github.p3chys.documentationarchi.doc.Published.Kind;
import io.github.p3chys.documentationarchi.doc.Relation;
import io.github.p3chys.documentationarchi.render.Exporter;
import io.github.p3chys.documentationarchi.text.KeywordRules;

class PublishedTest {

    /** An overview and one view "Sales" with the elements given, each "id|name|description". */
    private static Doc doc(String... elements) {
        DocPage overview = new DocPage("model-1", "Model");
        overview.documentation = "What the model is for.";
        DocPage view = new DocPage("view-1", "Sales");
        view.image = Picture.of(new byte[] { 1 });
        view.documentation = "How we sell.";
        for (String e : elements) {
            String[] p = e.split("\\|", -1);
            List<Property> properties = new ArrayList<>(List.of(new Property("owner", "Sales")));
            view.contents.add(new Link(p[0], p[1], "Business Actor"));
            view.entries.add(new Entry(p[0], p[1], "Business Actor", p[2], properties, new ArrayList<>(), List.of()));
        }
        overview.contents.add(new Link(view.id, view.title));
        return new Doc("Model", List.of(overview, view));
    }

    private static Published published(Doc doc) {
        Published published = new Published();
        published.publish(doc, Map.of("a", "c1,l1", "rel-1", "c9,l9"), 1000, true);
        return published;
    }

    @Test
    void nothingChangedSincePublishing() {
        Published published = published(doc("a|Customer|Buys policies.", "b|Broker|Sells them."));
        assertThat(published.changesTo(doc("a|Customer|Buys policies.", "b|Broker|Sells them."), true)).isEmpty();
        assertThat(published.lastTime()).isEqualTo(1000);
        assertThat(published.item("a").print).isEqualTo("c1,l1");
        assertThat(published.item("rel-1").print).isEqualTo("c9,l9"); // a relationship: only its fingerprint is kept
    }

    @Test
    void addedChangedAndRemovedObjectsAreToldApartWithBeforeAndAfter() {
        Published published = published(doc("a|Customer|Buys policies.", "b|Broker|Sells them."));
        Doc now = doc("a|Client|Buys policies\nand renews them.", "c|Agent|New here.");
        now.pages.get(1).entries.get(0).relations.add(new Relation(true, "Serving", "", "", new Link("c", "Agent"), false));

        List<Change> changes = published.changesTo(now, true);
        assertThat(changes).extracting(c -> c.kind + " " + c.id)
                .containsExactly("CHANGED view-1", "CHANGED a", "ADDED c", "REMOVED b");
        assertThat(changes.get(0).fields).extracting(f -> f.field + ": " + f.before + " > " + f.after)
                .containsExactly("Elements: Customer, Broker > Client, Agent");
        assertThat(changes.get(1).fields).extracting(f -> f.field + ": " + f.before + " > " + f.after).containsExactly(
                "Name: Customer > Client", "Description: Buys policies. > Buys policies\nand renews them.",
                "Relationship → Serving Agent: null > (no description)");
        assertThat(changes.get(3).name).isEqualTo("Broker");

        // of a part of the model, what it doesn't show is not "removed"
        assertThat(published.changesTo(doc("a|Customer|Buys policies."), false)).extracting(c -> c.kind + " " + c.id)
                .containsExactly("CHANGED view-1");
    }

    @Test
    void publishingAPartKeepsTheRestAndPublishingAllDropsWhatIsGone() {
        Published published = published(doc("a|Customer|Buys policies.", "b|Broker|Sells them."));
        Doc part = doc("a|Client|Renamed.");
        published.publish(part, Map.of("a", "c2,l2"), 2000, false);
        assertThat(published.item("b").name).isEqualTo("Broker");
        assertThat(published.item("b").time).isEqualTo(1000);
        assertThat(published.item("a").name).isEqualTo("Client");
        assertThat(published.item("a").print).isEqualTo("c2,l2");
        assertThat(published.lastTime()).isEqualTo(2000);

        published.publish(doc("a|Client|Renamed."), Map.of("a", "c3,l3"), 3000, true);
        assertThat(published.item("b")).isNull();
        assertThat(published.item("rel-1")).isNull();
    }

    @Test
    void fileKeepsEveryTextAsItIsAndIsTheSameEachTime(@TempDir Path folder) throws Exception {
        Doc doc = doc("a|Tab\there \\ back|Line one\nline two\r\n\n#risk [T] 'C' {v}", "b|Broker|");
        doc.pages.get(1).entries.get(0).properties.add(new Property("key\twith tab", "value\nwith break"));
        Published published = published(doc);
        Path file = folder.resolve(Published.FILE);
        published.write(file);

        Published read = Published.read(file);
        assertThat(read.text()).isEqualTo(published.text());
        assertThat(read.changesTo(doc, true)).isEmpty();
        assertThat(read.item("a").name).isEqualTo("Tab\there \\ back");
        assertThat(read.item("a").fields).containsEntry("Property key\twith tab", "value\nwith break");
        assertThat(Files.readString(file)).startsWith("# documentationArchi").doesNotContain("\r");
        assertThat(published(doc).text()).isEqualTo(published.text());
    }

    @Test
    void unreadableLinesAndAMissingFileGiveNothingPublishedInsteadOfAnError(@TempDir Path folder) throws Exception {
        assertThat(Published.read(folder.resolve("none")).isEmpty()).isTrue();
        Published published = Published.parse("garbage\n\tx\ty\na\tname\tCustomer\na\ttime\tnot a number\na\tlater\tunknown\n");
        assertThat(published.item("a").name).isEqualTo("Customer");
        assertThat(published.item("a").time).isZero();
        assertThat(published.lastTime()).isZero();
    }

    @Test
    void changesPageListsWhatChangedAndLinksToWhereItIsDescribed(@TempDir Path out) throws Exception {
        Published published = published(doc("a|Customer|Buys policies.", "b|Broker|Sells them."));
        Doc now = doc("a|Client|Buys and renews.", "c|Agent|New here.");
        DocPage page = Published.page(published.changesTo(now, true), "2026-11-10 09:00");
        assertThat(page.title).isEqualTo("Changes since 2026-11-10 09:00");
        assertThat(page.documentation).contains("1 added, 2 changed, 1 removed");

        Doc shown = Published.with(now, page);
        assertThat(shown.pages).extracting(p -> p.id).containsExactly("model-1", "changes", "view-1");
        assertThat(shown.locate("a").page.id).isEqualTo("view-1"); // the element itself is still described on its view
        new Exporter(shown, KeywordRules.defaults()).export(out, java.util.EnumSet.of(Exporter.Format.HTML));
        String html = Files.readString(out.resolve("html").resolve(page.slug() + ".html"));
        assertThat(html).contains("Changed Business Actor", "Description – before", "Buys policies.",
                "Description – after", "Buys and renews.", "Added Business Actor", "Removed Business Actor", "Broker",
                "href=\"sales.html#e-a\"");
        assertThat(Files.readString(out.resolve("html/index.html"))).contains("Changes since 2026-11-10 09:00");

        assertThat(Published.page(List.of(), "2026-11-10 09:00").documentation).startsWith("Nothing changed since");
    }
}
