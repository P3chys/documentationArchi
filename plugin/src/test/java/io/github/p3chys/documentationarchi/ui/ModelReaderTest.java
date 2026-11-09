package io.github.p3chys.documentationarchi.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.archimatetool.canvas.model.ICanvasFactory;
import com.archimatetool.canvas.model.ICanvasModel;
import com.archimatetool.canvas.model.ICanvasModelBlock;
import com.archimatetool.canvas.model.ICanvasModelSticky;
import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelNote;
import com.archimatetool.model.IDiagramModelReference;
import com.archimatetool.model.IJunction;
import com.archimatetool.model.IProfile;
import com.archimatetool.model.ISketchModel;
import com.archimatetool.model.ISketchModelActor;
import com.archimatetool.model.ISketchModelSticky;
import com.archimatetool.model.IProperty;

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.doc.Relation;

class ModelReaderTest {

    private static final IArchimateFactory F = IArchimateFactory.eINSTANCE;

    private IArchimateModel model;
    private IArchimateElement actor;
    private IArchimateElement portal;
    private IArchimateElement unused;
    private IArchimateDiagramModel first;
    private IArchimateDiagramModel second;

    @BeforeEach
    void model() {
        model = F.createArchimateModel();
        model.setDefaults();
        model.setName("Test model");
        model.setPurpose("Why it exists");

        actor = element(F.createBusinessActor(), "Client", FolderType.BUSINESS);
        actor.setDocumentation("Who buys");
        IProperty owner = F.createProperty();
        owner.setKey("owner");
        owner.setValue("Adam");
        actor.getProperties().add(owner);
        portal = element(F.createApplicationComponent(), "Portal", FolderType.APPLICATION);
        unused = element(F.createNode(), "", FolderType.TECHNOLOGY);

        IArchimateRelationship serving = F.createServingRelationship();
        serving.setName("serves");
        serving.connect(portal, actor);
        model.getFolder(FolderType.RELATIONS).getElements().add(serving);

        first = view("First");
        second = view("Second");
        add(first, portal);
        add(first, actor);
        add(second, actor);

        IDiagramModelNote note = F.createDiagramModelNote();
        note.setContent("Read me");
        first.getChildren().add(note);
        IDiagramModelReference reference = F.createDiagramModelReference();
        reference.setReferencedModel(second);
        first.getChildren().add(reference);
    }

    private IArchimateElement element(IArchimateElement element, String name, FolderType folder) {
        element.setName(name);
        model.getFolder(folder).getElements().add(element);
        return element;
    }

    private IArchimateDiagramModel view(String name) {
        IArchimateDiagramModel view = F.createArchimateDiagramModel();
        view.setName(name);
        model.getFolder(FolderType.DIAGRAMS).getElements().add(view);
        return view;
    }

    private static void add(IArchimateDiagramModel view, IArchimateElement element) {
        IDiagramModelArchimateObject object = F.createDiagramModelArchimateObject();
        object.setArchimateElement(element);
        view.getChildren().add(object);
    }

    private Doc read(Object... objects) {
        return ModelReader.read(ModelReader.Selection.of(List.of(objects)), (view, page) -> { });
    }

    @Test
    void wholeModelHasOverviewAPagePerViewAndElementsWithoutView() {
        Doc doc = read(model);

        assertThat(doc.pages).extracting(p -> p.title)
                .containsExactly("Test model", "First", "Second", "Elements not in any view");
        DocPage overview = doc.pages.get(0);
        assertThat(overview.documentation).isEqualTo("Why it exists");
        assertThat(overview.contents).extracting(l -> l.label).containsExactly("First", "Second", "Elements not in any view");
        assertThat(doc.pages.get(3).entries).extracting(e -> e.name).containsExactly("Node");
    }

    @Test
    void assetsComeFromTheAssetsFolderNextToTheModelFile(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("Assets"));
        Files.writeString(dir.resolve("Assets/flow.png"), "picture");
        model.setFile(dir.resolve("model.archimate").toFile());

        Doc doc = read(model);

        assertThat(doc.assets.find("flow.png", "First").file).isEqualTo(dir.resolve("Assets/flow.png").toRealPath());
        assertThat(doc.assets.problems()).isEmpty();
    }

    @Test
    void unsavedModelHasNoAssets() {
        Doc doc = read(model);

        assertThat(doc.assets.find("flow.png", "First")).isNull();
        assertThat(doc.assets.problems()).singleElement().asString().contains("there is no Assets folder");
    }

    @Test
    void elementIsDescribedOnFirstViewAndLinkedFromLater() {
        Doc doc = read(model);

        DocPage firstPage = doc.pages.get(1);
        DocPage secondPage = doc.pages.get(2);
        assertThat(firstPage.entries).extracting(e -> e.name).containsExactly("Client", "Portal");
        assertThat(secondPage.entries).isEmpty();
        assertThat(secondPage.contents).extracting(l -> l.label).containsExactly("Client");
        assertThat(doc.locate(actor.getId()).page).isSameAs(firstPage);
    }

    @Test
    void entryHoldsDescriptionPropertiesRelationsAndViews() {
        Entry client = read(model).pages.get(1).entries.get(0);

        assertThat(client.type).isEqualTo("Business Actor");
        assertThat(client.documentation).isEqualTo("Who buys");
        assertThat(client.properties).extracting(p -> p.key + "=" + p.value).containsExactly("owner=Adam");
        assertThat(client.views).extracting(l -> l.label).containsExactly("First", "Second");
        assertThat(client.relations).hasSize(1);
        assertThat(client.relations.get(0).outgoing).isFalse();
        assertThat(client.relations.get(0).type).isEqualTo("Serving Relationship");
        assertThat(client.relations.get(0).name).isEqualTo("serves");
        assertThat(client.relations.get(0).other.id).isEqualTo(portal.getId());
    }

    @Test
    void contentsListEveryElementOfTheViewWithItsType() {
        DocPage page = read(first).pages.get(0);

        assertThat(page.contents).extracting(l -> l.type + " " + l.label)
                .containsExactly("Business Actor Client", "Application Component Portal");
    }

    @Test
    void relationsToElementsNotInTheViewAreMarked() {
        assertThat(read(first).pages.get(0).entries.get(0).relations.get(0).outside).isFalse();
        assertThat(read(second).pages.get(0).entries.get(0).relations.get(0).outside).isTrue();
    }

    @Test
    void notesAndViewReferencesComeWithTheView() {
        DocPage page = read(model).pages.get(1);

        assertThat(page.notes).containsExactly("Read me");
        assertThat(page.relatedViews).extracting(l -> l.id).containsExactly(second.getId());
    }

    @Test
    void singleViewIsOnePageAndLinksLeavingItStayUnresolved() {
        Doc doc = read(second);

        assertThat(doc.pages).extracting(p -> p.title).containsExactly("Second");
        assertThat(doc.locate(portal.getId())).isNull();
    }

    @Test
    void selectedElementsGetOnePage() {
        Doc doc = read(actor, portal);

        assertThat(doc.pages).extracting(p -> p.title).containsExactly("Selected elements");
        assertThat(doc.pages.get(0).entries).extracting(e -> e.name).containsExactly("Client", "Portal");
    }

    @Test
    void folderMeansItsContents() {
        Doc doc = read(model.getFolder(FolderType.DIAGRAMS));

        assertThat(doc.pages).extracting(p -> p.title).containsExactly("Test model", "First", "Second");
    }

    @Test
    void diagramObjectsMeanTheirElementOrView() {
        IDiagramModelArchimateObject onDiagram = (IDiagramModelArchimateObject) second.getChildren().get(0);
        IDiagramModelNote note = (IDiagramModelNote) first.getChildren().get(2);

        assertThat(read(onDiagram).pages.get(0).entries).extracting(e -> e.name).containsExactly("Client");
        assertThat(read(note).pages).extracting(p -> p.title).containsExactly("First");
    }

    @Test
    void relationshipMeansBothEnds() {
        IArchimateRelationship r = actor.getTargetRelationships().get(0);

        assertThat(read(r).pages.get(0).entries).extracting(e -> e.name).containsExactly("Client", "Portal");
    }

    private IArchimateElement printOrder() {
        return element(F.createGrouping(), "PrintOrder", FolderType.OTHER);
    }

    private void position(IArchimateElement element, IArchimateElement printOrder, String name) {
        IArchimateRelationship r = F.createAssociationRelationship();
        r.setName(name);
        r.connect(element, printOrder);
        model.getFolder(FolderType.RELATIONS).getElements().add(r);
    }

    @Test
    void printOrderSortsByRelationshipNameNumbersByValueOthersLast() {
        IArchimateDiagramModel view = view("Ordered");
        IArchimateElement a = element(F.createNode(), "A", FolderType.TECHNOLOGY);
        IArchimateElement b = element(F.createNode(), "B", FolderType.TECHNOLOGY);
        IArchimateElement c = element(F.createNode(), "C", FolderType.TECHNOLOGY);
        IArchimateElement free = element(F.createNode(), "0 free", FolderType.TECHNOLOGY);
        IArchimateElement order = printOrder();
        for (IArchimateElement e : List.of(a, b, c, free, order)) {
            add(view, e);
        }
        position(a, order, "10");
        position(b, order, "2");
        position(c, order, "1");

        DocPage page = read(view).pages.get(0);

        assertThat(page.entries).extracting(e -> e.name).containsExactly("C", "B", "A", "0 free");
        assertThat(page.entries.get(0).relations).isEmpty();
    }

    @Test
    void printOrderOnTheViewWinsOverOneElsewhere() {
        IArchimateDiagramModel view = view("Ordered");
        IArchimateElement a = element(F.createNode(), "A", FolderType.TECHNOLOGY);
        IArchimateElement b = element(F.createNode(), "B", FolderType.TECHNOLOGY);
        IArchimateElement here = printOrder();
        IArchimateElement elsewhere = printOrder();
        add(view, a);
        add(view, b);
        add(view, here);
        position(a, elsewhere, "1");
        position(a, here, "2");
        position(b, elsewhere, "2");
        position(b, here, "1");

        assertThat(read(view).pages.get(0).entries).extracting(e -> e.name).containsExactly("B", "A");
    }

    @Test
    void printOrderAlsoSortsElementsDescribedOnEarlierPages() {
        IArchimateElement order = printOrder();
        position(actor, order, "2");
        position(portal, order, "1");
        IArchimateDiagramModel view = view("Again");
        add(view, actor);
        add(view, portal);

        Doc doc = read(first, view);

        assertThat(doc.pages.get(2).contents).extracting(l -> l.label).containsExactly("Portal", "Client");
    }

    @Test
    void printOrderIsNotDescribedAnywhere() {
        IArchimateElement order = printOrder();
        position(actor, order, "1");

        Doc doc = read(model);

        assertThat(doc.pages).flatExtracting(p -> p.entries).extracting(e -> e.name).doesNotContain("PrintOrder");
    }

    @ParameterizedTest
    @CsvSource({"2,10", "1.9,1.10", "a2,a10", "A,b", "1,1a"})
    void naturalOrder(String lower, String higher) {
        assertThat(ModelReader.compareNatural(lower, higher)).isNegative();
        assertThat(ModelReader.compareNatural(higher, lower)).isPositive();
    }

    @Test
    void selectionAcrossModelsIsRefused() {
        IArchimateModel other = F.createArchimateModel();
        other.setDefaults();

        assertThatThrownBy(() -> read(model, other)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("several models");
    }

    @Test
    void emptyFolderIsRefused() {
        assertThatThrownBy(() -> read(model.getFolder(FolderType.STRATEGY))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no views or elements");
    }

    private IArchimateRelationship connect(IArchimateRelationship r, IArchimateElement source, IArchimateElement target) {
        r.connect(source, target);
        model.getFolder(FolderType.RELATIONS).getElements().add(r);
        return r;
    }

    private IJunction junction(String type) {
        IJunction junction = F.createJunction();
        junction.setType(type);
        model.getFolder(FolderType.OTHER).getElements().add(junction);
        return junction;
    }

    @Test
    void junctionIsNeverDescribedOrListed() {
        IJunction junction = junction(IJunction.AND_JUNCTION_TYPE);
        add(first, junction);
        connect(F.createTriggeringRelationship(), actor, junction);

        Doc doc = read(model);

        assertThat(doc.pages).flatExtracting(p -> p.entries).extracting(e -> e.type).doesNotContain("Junction");
        assertThat(doc.pages).flatExtracting(p -> p.contents).extracting(l -> l.label).doesNotContain("Junction");
        assertThatThrownBy(() -> read(junction)).hasMessageContaining("no views or elements");
    }

    @Test
    void relationshipIsFollowedThroughChainedJunctionsWithoutLooping() {
        IJunction or = junction(IJunction.OR_JUNCTION_TYPE);
        IJunction and = junction(IJunction.AND_JUNCTION_TYPE);
        IArchimateElement a = element(F.createBusinessProcess(), "A", FolderType.BUSINESS);
        IArchimateElement b = element(F.createBusinessProcess(), "B", FolderType.BUSINESS);
        IArchimateElement c = element(F.createBusinessProcess(), "C", FolderType.BUSINESS);
        IArchimateRelationship start = connect(F.createTriggeringRelationship(), a, or);
        start.setName("then");
        connect(F.createTriggeringRelationship(), or, b);
        connect(F.createTriggeringRelationship(), or, and);
        connect(F.createTriggeringRelationship(), and, c);
        connect(F.createTriggeringRelationship(), and, or); // a loop back
        IArchimateDiagramModel view = view("Flow");
        for (IArchimateElement e : List.of(a, b, c, or, and)) {
            add(view, e);
        }

        DocPage page = read(view).pages.get(0);

        assertThat(page.contents).extracting(l -> l.label).containsExactly("A", "B", "C");
        Relation fromA = page.entries.get(0).relations.get(0);
        assertThat(fromA.outgoing).isTrue();
        assertThat(fromA.name).isEqualTo("then");
        assertThat(fromA.junction).isEqualTo("or");
        assertThat(fromA.others).extracting(l -> l.label).containsExactly("B", "C");
        assertThat(fromA.outside).isFalse();
        Relation intoC = page.entries.get(2).relations.get(0);
        assertThat(intoC.outgoing).isFalse();
        assertThat(intoC.junction).isEqualTo("and");
        assertThat(intoC.others).extracting(l -> l.label).containsExactly("A");
    }

    @Test
    void junctionLeadingNowhereLeavesTheRelationshipOut() {
        IJunction junction = junction(IJunction.AND_JUNCTION_TYPE);
        connect(F.createTriggeringRelationship(), actor, junction);

        assertThat(read(first).pages.get(0).entries.get(0).relations).extracting(r -> r.type)
                .containsExactly("Serving Relationship");
    }

    @Test
    void sketchViewListsTheTextsOfItsStickiesAndActors() {
        ISketchModel sketch = F.createSketchModel();
        sketch.setName("Idea");
        model.getFolder(FolderType.DIAGRAMS).getElements().add(sketch);
        ISketchModelSticky sticky = F.createSketchModelSticky();
        sticky.setName("Goal");
        sticky.setContent("Sell more #todo");
        sketch.getChildren().add(sticky);
        ISketchModelActor actor = F.createSketchModelActor();
        actor.setName("Customer");
        actor.setDocumentation("Buys online");
        sketch.getChildren().add(actor);
        sketch.getChildren().add(F.createSketchModelSticky()); // empty: left out

        DocPage page = read(sketch).pages.get(0);

        assertThat(page.texts).extracting(t -> t.name + ": " + t.text)
                .containsExactly("Goal: Sell more #todo", "Customer: Buys online");
    }

    @Test
    void canvasViewListsTheTextsOfItsBlocksAndStickies() {
        ICanvasFactory canvas = ICanvasFactory.eINSTANCE;
        ICanvasModel view = canvas.createCanvasModel();
        view.setName("Canvas");
        model.getFolder(FolderType.DIAGRAMS).getElements().add(view);
        ICanvasModelBlock block = canvas.createCanvasModelBlock();
        block.setContent("Key partners");
        view.getChildren().add(block);
        ICanvasModelSticky sticky = canvas.createCanvasModelSticky();
        sticky.setName("Bank");
        sticky.setContent("Pays out");
        sticky.setNotes("Contract until 2027");
        block.getChildren().add(sticky);

        DocPage page = read(view).pages.get(0);

        assertThat(page.texts).extracting(t -> t.name + ": " + t.text)
                .containsExactly(": Key partners", "Bank: Pays out\n\nContract until 2027");
        assertThat(page.notes).isEmpty();
    }

    @Test
    void specializationIsShownWithTheType() {
        IProfile broker = F.createProfile();
        broker.setName("Broker");
        broker.setConceptType(actor.eClass().getName());
        model.getProfiles().add(broker);
        actor.getProfiles().add(broker);
        IProfile api = F.createProfile();
        api.setName("API call");
        api.setConceptType("ServingRelationship");
        model.getProfiles().add(api);
        actor.getTargetRelationships().get(0).getProfiles().add(api);

        DocPage page = read(first).pages.get(0);

        assertThat(page.contents).extracting(l -> l.type).containsExactly("Business Actor: Broker", "Application Component");
        Entry client = page.entries.get(0);
        assertThat(client.type).isEqualTo("Business Actor: Broker");
        assertThat(client.relations.get(0).type).isEqualTo("Serving Relationship: API call");
        assertThat(page.entries.get(1).relations.get(0).other.type).isEqualTo("Business Actor: Broker");
    }
}
