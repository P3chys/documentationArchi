package io.github.p3chys.documentationarchi.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IServingRelationship;

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.text.KeywordRules;

/** {@code structure microservices}: a page per microservice, its views describe its elements again, others in brief. */
class MicroservicesTest {

    private static final IArchimateFactory F = IArchimateFactory.eINSTANCE;
    private static final KeywordRules RULES = KeywordRules.parseFile("structure microservices");

    private IArchimateModel model;
    private IArchimateElement orderService;
    private IArchimateElement getOrder;
    private IArchimateElement webShop;
    private IArchimateElement idp;
    private IFolder ordersViews;

    @BeforeEach
    void model() {
        model = F.createArchimateModel();
        model.setDefaults();
        model.setName("Shop");
        IFolder app = model.getFolder(FolderType.APPLICATION);
        IFolder orders = folder(app, "Orders");
        IFolder shop = folder(app, " web shop "); // the same microservice as the views' "Web Shop"
        orderService = element(orders, F.createApplicationComponent(), "Order Service", "Owns orders.");
        getOrder = element(orders, F.createApplicationInterface(), "API orders/getOrder",
                "GET /orders/{id}. Returns one order.\n\nErrors: 404.");
        webShop = element(shop, F.createApplicationComponent(), "Web Shop", "The front end.");
        idp = element(app, F.createApplicationComponent(), "Identity Provider", "Single sign-on.");
        IServingRelationship serving = F.createServingRelationship();
        serving.setName("order detail");
        serving.connect(getOrder, webShop);
        model.getFolder(FolderType.RELATIONS).getElements().add(serving);

        IFolder views = model.getFolder(FolderType.DIAGRAMS);
        view(views, "Landscape", webShop, orderService, idp);
        ordersViews = folder(views, "Orders");
        ordersViews.setDocumentation("The order microservice.");
        view(folder(ordersViews, "BE"), "Orders API", orderService, getOrder);
        IFolder shopViews = folder(views, "Web Shop");
        view(folder(shopViews, "FE"), "Checkout", webShop, getOrder, idp);
    }

    private static IFolder folder(IFolder in, String name) {
        IFolder folder = F.createFolder();
        folder.setName(name);
        in.getFolders().add(folder);
        return folder;
    }

    private static IArchimateElement element(IFolder in, IArchimateElement element, String name, String documentation) {
        element.setName(name);
        element.setDocumentation(documentation);
        in.getElements().add(element);
        return element;
    }

    private static IArchimateDiagramModel view(IFolder in, String name, IArchimateElement... elements) {
        IArchimateDiagramModel view = F.createArchimateDiagramModel();
        view.setName(name);
        in.getElements().add(view);
        for (IArchimateElement e : elements) {
            IDiagramModelArchimateObject o = F.createDiagramModelArchimateObject();
            o.setArchimateElement(e);
            view.getChildren().add(o);
        }
        return view;
    }

    private Doc read(KeywordRules rules, Object selected) {
        ModelReader.Selection selection = ModelReader.Selection.of(List.of(selected));
        return ModelReader.read(selection, (v, page) -> { }, new ArrayList<>(), rules, selection.exclusion(rules));
    }

    private static DocPage page(Doc doc, String title) {
        return doc.pages.stream().filter(p -> p.title.equals(title)).findFirst().orElseThrow();
    }

    private static Entry entry(DocPage page, IArchimateElement element) {
        return page.entries.stream().filter(e -> e.id.equals(element.getId())).findFirst().orElseThrow();
    }

    @Test
    void eachMicroserviceHasAPageBeforeItsViews() {
        Doc doc = read(RULES, model);

        assertThat(doc.pages.stream().map(p -> p.title).collect(Collectors.toList()))
                .containsExactly("Shop", "Landscape", "Orders", "Orders API", "Web Shop", "Checkout", "Shared");
        DocPage orders = page(doc, "Orders");
        assertThat(orders.entries).extracting(e -> e.name).containsExactly("Order Service", "API orders/getOrder");
        assertThat(orders.entries).allMatch(e -> e.form == Entry.Form.FULL);
        assertThat(orders.groups.get(getOrder.getId())).isEqualTo("Application Interface");
        assertThat(orders.documentation).isEqualTo("The order microservice.");
        assertThat(orders.folder.page).isSameAs(orders);
        assertThat(page(doc, "Orders API").folder.parent).isSameAs(orders.folder);
        assertThat(page(doc, "Web Shop").folder.name).isEqualTo("Web Shop");
    }

    @Test
    void linksLeadToTheMicroservicesPage() {
        Doc doc = read(RULES, model);

        assertThat(doc.locate(getOrder.getId()).page.title).isEqualTo("Orders");
        assertThat(doc.locate(idp.getId()).page.title).isEqualTo("Shared");
        DocPage checkout = page(doc, "Checkout");
        assertThat(doc.locate(getOrder.getId(), checkout).page).isSameAs(checkout); // its contents jump within it
    }

    @Test
    void ownViewsDescribeTheirElementsAgain() {
        Doc doc = read(RULES, model);

        Entry copy = entry(page(doc, "Orders API"), getOrder);
        assertThat(copy.form).isEqualTo(Entry.Form.COPY);
        assertThat(copy.home).isEqualTo("Orders");
        assertThat(copy.documentation).contains("Errors: 404.");
        assertThat(entry(page(doc, "Checkout"), webShop).form).isEqualTo(Entry.Form.COPY);
    }

    @Test
    void otherViewsShowTheElementInBrief() {
        Doc doc = read(RULES, model);

        DocPage checkout = page(doc, "Checkout");
        Entry brief = entry(checkout, getOrder);
        assertThat(brief.form).isEqualTo(Entry.Form.BRIEF);
        assertThat(brief.home).isEqualTo("Orders");
        assertThat(brief.documentation).isEqualTo("GET /orders/{id}.");
        assertThat(brief.relations).filteredOn(r -> !r.outside).extracting(r -> r.name).containsExactly("order detail");
        assertThat(entry(checkout, idp).home).isEqualTo("Shared");
        assertThat(entry(page(doc, "Landscape"), orderService).form).isEqualTo(Entry.Form.BRIEF);
    }

    @Test
    void anExportedFolderBringsItsMicroservicesElements() {
        Doc doc = read(RULES, ordersViews);

        assertThat(doc.pages.stream().map(p -> p.title).collect(Collectors.toList()))
                .containsExactly("Shop", "Orders", "Orders API");
        assertThat(page(doc, "Orders").entries).hasSize(2);
    }

    @Test
    void withoutTheRuleTheFirstViewDescribes() {
        Doc doc = read(KeywordRules.defaults(), model);

        assertThat(doc.pages.stream().map(p -> p.title).collect(Collectors.toList()))
                .containsExactly("Shop", "Landscape", "Orders API", "Checkout");
        assertThat(doc.pages.stream().flatMap(p -> p.entries.stream())).allMatch(e -> e.form == Entry.Form.FULL);
    }

    @Test
    void firstSentenceStopsAtTheFirstFullStopOfTheFirstParagraph() {
        assertThat(ModelReader.firstSentence("Starts it. Then more.\n\nOther")).isEqualTo("Starts it.");
        assertThat(ModelReader.firstSentence("GET /x/{id}\n\nOne.")).isEqualTo("GET /x/{id}");
        assertThat(ModelReader.firstSentence("Version 1.2 is used")).isEqualTo("Version 1.2 is used");
        assertThat(ModelReader.firstSentence("<sql>select 1</sql>")).isEmpty();
        assertThat(ModelReader.firstSentence(null)).isEmpty();
    }
}
