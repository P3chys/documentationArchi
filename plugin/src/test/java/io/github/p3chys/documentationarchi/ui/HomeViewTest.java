package io.github.p3chys.documentationarchi.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.gef.commands.Command;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IProperty;

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;

class HomeViewTest {

    private static final IArchimateFactory F = IArchimateFactory.eINSTANCE;

    private IArchimateModel model;
    private IArchimateElement actor;
    private IArchimateElement portal;
    private IArchimateDiagramModel first;
    private IArchimateDiagramModel second;

    @BeforeEach
    void model() {
        model = F.createArchimateModel();
        model.setDefaults();
        actor = element(F.createBusinessActor(), "Client");
        portal = element(F.createApplicationComponent(), "Portal");
        first = view("First", actor, portal);
        second = view("Second", actor, portal);
    }

    private IArchimateElement element(IArchimateElement element, String name) {
        element.setName(name);
        model.getDefaultFolderForObject(element).getElements().add(element);
        return element;
    }

    private IArchimateDiagramModel view(String name, IArchimateElement... elements) {
        IArchimateDiagramModel view = F.createArchimateDiagramModel();
        view.setName(name);
        model.getDefaultFolderForObject(view).getElements().add(view);
        for (IArchimateElement e : elements) {
            IDiagramModelArchimateObject object = F.createDiagramModelArchimateObject();
            object.setArchimateElement(e);
            view.getChildren().add(object);
        }
        return view;
    }

    private Doc read(Object... objects) {
        return ModelReader.read(ModelReader.Selection.of(List.of(objects)), (view, page) -> { });
    }

    private static DocPage page(Doc doc, String title) {
        return doc.pages.stream().filter(p -> p.title.equals(title)).findFirst().get();
    }

    @Test
    void describeHereSetsTheHomeAndUndoTakesItBack() {
        Command command = HomeView.toggle(second, List.of(actor));
        command.execute();

        assertThat(ModelReader.home(actor)).isEqualTo(second.getId());
        command.undo();
        assertThat(ModelReader.home(actor)).isNull();
        assertThat(actor.getProperties()).isEmpty();
    }

    @Test
    void describeHereOnElementsAlreadyDescribedHereClearsTheHome() {
        HomeView.toggle(second, List.of(actor)).execute();
        HomeView.toggle(first, List.of(portal)).execute();

        HomeView.toggle(second, List.of(actor, portal)).execute(); // not all at home here: sets
        assertThat(ModelReader.home(portal)).isEqualTo(second.getId());
        assertThat(portal.getProperties()).hasSize(1);
        HomeView.toggle(second, List.of(actor, portal)).execute(); // all at home here: clears
        assertThat(actor.getProperties()).isEmpty();
        assertThat(portal.getProperties()).isEmpty();
        assertThat(HomeView.toggle(second, List.of())).isNull();
    }

    @Test
    void elementIsDescribedOnItsHomeViewAndThePropertyIsNotShown() {
        IProperty owner = F.createProperty();
        owner.setKey("owner");
        owner.setValue("Adam");
        actor.getProperties().add(owner);
        HomeView.toggle(second, List.of(actor)).execute();

        Doc doc = read(model);

        assertThat(page(doc, "First").entries).extracting(e -> e.name).containsExactly("Portal");
        assertThat(page(doc, "First").contents).extracting(l -> l.label).containsExactly("Client", "Portal");
        assertThat(page(doc, "Second").entries).extracting(e -> e.name).containsExactly("Client");
        assertThat(page(doc, "Second").entries.get(0).properties).extracting(p -> p.key).containsExactly("owner");
        assertThat(doc.locate(actor.getId()).page.title).isEqualTo("Second");
    }

    @Test
    void homeViewOutsideTheExportOrNotShowingTheElementKeepsTheFirstView() {
        HomeView.toggle(second, List.of(actor)).execute();
        assertThat(read(first).pages.get(0).entries).extracting(e -> e.name).containsExactly("Client", "Portal");

        IArchimateDiagramModel empty = view("Empty");
        IProperty home = actor.getProperties().get(0);
        home.setValue(empty.getId());
        assertThat(page(read(model), "First").entries).extracting(e -> e.name).containsExactly("Client", "Portal");
    }
}
