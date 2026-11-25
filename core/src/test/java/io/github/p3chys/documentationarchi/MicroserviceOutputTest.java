package io.github.p3chys.documentationarchi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocFolder;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.doc.Link;
import io.github.p3chys.documentationarchi.doc.Relation;
import io.github.p3chys.documentationarchi.render.Exporter;
import io.github.p3chys.documentationarchi.render.Exporter.Format;
import io.github.p3chys.documentationarchi.text.KeywordRules;

/**
 * {@code structure microservices}: a microservice's page describes its elements, its views describe them again, other
 * views show them in brief, and Confluence writes the microservice's page as its folder's.
 */
class MicroserviceOutputTest {

    @TempDir
    Path folder;

    private Doc doc;
    private DocPage ordersApi;
    private DocPage checkout;

    @BeforeEach
    void doc() {
        DocFolder orders = new DocFolder("f-orders", "Orders", null);
        DocFolder ordersBe = new DocFolder("f-orders-be", "BE", orders);
        DocFolder shop = new DocFolder("f-shop", "Web Shop", null);
        DocFolder shopFe = new DocFolder("f-shop-fe", "FE", shop);
        DocFolder shopBe = new DocFolder("f-shop-be", "BE", shop);

        DocPage overview = new DocPage("model", "Shop");
        DocPage service = new DocPage("ms-orders", "Orders");
        service.documentation = "The order microservice.";
        service.folder = orders;
        orders.page = service;
        service.entries.add(entry("getOrder", "API orders/getOrder", "GET /orders/{id}. Returns one order.", Entry.Form.FULL, null));
        service.contents.add(new Link("getOrder", "API orders/getOrder"));

        ordersApi = new DocPage("v1", "Orders API");
        ordersApi.folder = ordersBe;
        ordersApi.entries.add(entry("getOrder", "API orders/getOrder", "GET /orders/{id}. Returns one order.", Entry.Form.COPY, "Orders"));
        ordersApi.contents.add(new Link("getOrder", "API orders/getOrder"));

        DocPage shopService = new DocPage("ms-shop", "Web Shop");
        shopService.folder = shop;
        shop.page = shopService;
        shopService.entries.add(entry("webshop", "Web Shop", "The front end.", Entry.Form.FULL, null));

        checkout = new DocPage("v2", "Checkout");
        checkout.folder = shopFe;
        Entry brief = new Entry("getOrder", "API orders/getOrder", "Application Interface", "GET /orders/{id}.", List.of(),
                List.of(new Relation(true, "Serving", "order detail", "", new Link("webshop", "Web Shop"), false),
                        new Relation(false, "Realization", "", "", new Link("orders", "Order Service"), true)),
                List.of());
        brief.form = Entry.Form.BRIEF;
        brief.home = "Orders";
        checkout.entries.add(brief);
        checkout.contents.add(new Link("getOrder", "API orders/getOrder"));

        DocPage shopApi = new DocPage("v3", "Shop API");
        shopApi.folder = shopBe;

        List<DocPage> pages = new ArrayList<>(List.of(overview, service, ordersApi, shopService, checkout, shopApi));
        pages.subList(1, pages.size()).forEach(p -> overview.contents.add(new Link(p.id, p.title)));
        doc = new Doc("Shop", pages);
    }

    private static Entry entry(String id, String name, String text, Entry.Form form, String home) {
        Entry entry = new Entry(id, name, "Application Interface", text, List.of(), List.of(), List.of());
        entry.form = form;
        entry.home = home;
        return entry;
    }

    private void export(Format format) throws Exception {
        new Exporter(doc, KeywordRules.defaults()).export(folder, EnumSet.of(format));
    }

    @Test
    void linksLeadToTheFullDescriptionContentsToTheOneOnThePage() {
        assertThat(doc.locate("getOrder").page.title).isEqualTo("Orders");
        assertThat(doc.locate("getOrder").anchor).isEqualTo("e-getOrder");
        assertThat(doc.locate("getOrder", checkout).page).isSameAs(checkout);
        assertThat(doc.locate("getOrder", checkout).anchor).isEqualTo("e-getOrder-checkout");
        assertThat(doc.pages.get(1).described()).hasSize(1);
        assertThat(checkout.described()).isEmpty();
    }

    @Test
    void anElementShownOnlyInPartLeadsToTheFirstPageShowingIt() {
        List<DocPage> pages = new ArrayList<>(doc.pages);
        pages.remove(1);
        Doc partial = new Doc("Shop", pages);

        assertThat(partial.locate("getOrder").page.title).isEqualTo("Orders API");
    }

    @Test
    void htmlCopyAndBriefSayWhereTheElementIsDescribed() throws Exception {
        export(Format.HTML);

        String api = Files.readString(folder.resolve("html/orders-api.html"));
        assertThat(api).contains("id=\"e-getOrder-orders-api\"").contains("Defined in: </span><a href=\"orders.html#e-getOrder\">Orders</a>")
                .contains("Returns one order.");
        String page = Files.readString(folder.resolve("html/checkout.html"));
        assertThat(page).contains("<a href=\"#e-getOrder-checkout\">API orders/getOrder</a>") // the contents
                .contains("<a href=\"orders.html#e-getOrder\">Orders</a>")
                .contains("order detail")
                .doesNotContain("Order Service") // a relationship off the view
                .doesNotContain("Defined in: </span><a href=\"web-shop.html") // none on the page that describes it
                .doesNotContain("Returns one order.");
        String aToZ = Files.readString(folder.resolve("html/elements_a-z.html"));
        assertThat(aToZ.split("API orders/getOrder", -1)).hasSize(2); // listed once, at its full description
    }

    @Test
    void pdfAnchorsAreUnique() throws Exception {
        export(Format.PDF);

        assertThat(folder.resolve("shop.pdf")).exists();
    }

    @Test
    void confluenceWritesTheMicroservicesPageAsItsFolders() throws Exception {
        export(Format.CONFLUENCE);

        String manifest = Files.readString(folder.resolve("confluence/manifest.json"));
        assertThat(manifest).containsSubsequence(
                "{\"title\": \"Orders\", \"parent\": \"Shop\", \"file\": \"pages/orders.xml\"",
                "{\"title\": \"Orders – BE\", \"parent\": \"Orders\"",
                "{\"title\": \"Web Shop\", \"parent\": \"Shop\"",
                "{\"title\": \"Web Shop – FE\", \"parent\": \"Web Shop\"",
                "{\"title\": \"Web Shop – BE\", \"parent\": \"Web Shop\"",
                "{\"title\": \"Orders API\", \"parent\": \"Orders – BE\"");
        assertThat(manifest).doesNotContain("Orders (2)").doesNotContain("ms-orders");
        String orders = Files.readString(folder.resolve("confluence/pages/orders.xml"));
        assertThat(orders).containsSubsequence("<p>The order microservice.</p>", "e-getOrder</ac:parameter>",
                "ri:content-title=\"Orders – BE\"");
        String checkoutPage = Files.readString(folder.resolve("confluence/pages/checkout.xml"));
        assertThat(checkoutPage).contains("<ac:link ac:anchor=\"e-getOrder\"><ri:page ri:content-title=\"Orders\"/>"
                + "<ac:link-body>Orders</ac:link-body></ac:link>");
    }

    @Test
    void structureRuleReadsBackAndIsOffByDefault() {
        assertThat(KeywordRules.defaults().microservices()).isFalse();
        KeywordRules rules = KeywordRules.parseFile("structure microservices");
        assertThat(rules.microservices()).isTrue();
        assertThat(KeywordRules.parseFile("structure microservices\nstructure views").microservices()).isFalse();
        assertThat(KeywordRules.Rule.structure("Microservices").line()).isEqualTo("structure microservices");
        assertThatThrownBy(() -> KeywordRules.parseFile("structure folders")).hasMessageContaining("structure microservices");
    }
}
