package io.github.p3chys.documentationarchi.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.gef.commands.Command;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IWorkPackage;

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Entry;

class WorkPackagesTest {

    private static final IArchimateFactory F = IArchimateFactory.eINSTANCE;

    private IArchimateModel model;
    private IArchimateElement portal;
    private IArchimateElement api;
    private IArchimateElement db;
    private IArchimateDiagramModel apps;
    private IArchimateDiagramModel data;
    private IWorkPackage ticket;

    @BeforeEach
    void model() {
        model = F.createArchimateModel();
        model.setDefaults();
        model.setName("Bank");
        portal = element(F.createApplicationComponent(), "Portal");
        api = element(F.createApplicationInterface(), "API");
        db = element(F.createNode(), "DB");
        relate(F.createServingRelationship(), api, portal);
        apps = view("Applications", portal, api);
        data = view("Data", db);
        ticket = workPackage("RBWDP-20025");
        ticket.setDocumentation("Limits for payments");
        run(WorkPackages.change(model, List.of(portal), List.of(ticket), List.of(), null));
    }

    private IArchimateElement element(IArchimateElement element, String name) {
        element.setName(name);
        model.getDefaultFolderForObject(element).getElements().add(element);
        return element;
    }

    private IWorkPackage workPackage(String name) {
        IWorkPackage wp = F.createWorkPackage();
        element(wp, name);
        return wp;
    }

    private IArchimateRelationship relate(IArchimateRelationship r, IArchimateElement source, IArchimateElement target) {
        r.connect(source, target);
        model.getFolder(FolderType.RELATIONS).getElements().add(r);
        return r;
    }

    private IArchimateDiagramModel view(String name, IArchimateElement... elements) {
        IArchimateDiagramModel view = F.createArchimateDiagramModel();
        view.setName(name);
        model.getFolder(FolderType.DIAGRAMS).getElements().add(view);
        for (IArchimateElement e : elements) {
            IDiagramModelArchimateObject object = F.createDiagramModelArchimateObject();
            object.setArchimateElement(e);
            view.getChildren().add(object);
        }
        return view;
    }

    private static void run(Command command) {
        assertThat(command).isNotNull();
        command.execute();
    }

    private Doc read(Object... objects) {
        return ModelReader.read(ModelReader.Selection.of(List.of(objects)), (view, page) -> { });
    }

    @ParameterizedTest
    @CsvSource({ "RBWDP-20025, true", "RBWDP-20025 Payment limits, true", "AB_2-7, true", "rbwdp-1, false",
            "Payment limits, false", "RBWDP-, false", "-12, false" })
    void namesStartWithAJiraKey(String name, boolean valid) {
        assertThat(WorkPackages.validName(name)).isEqualTo(valid);
    }

    @Test
    void linksAreAssociationsThatUndoTakesBack() {
        IArchimateRelationship link = ticket.getTargetRelationships().get(0);
        assertThat(link.eClass().getName()).isEqualTo("AssociationRelationship");
        assertThat(link.getSource()).isEqualTo(portal);
        assertThat(WorkPackages.elements(ticket)).containsExactly(portal);
        assertThat(WorkPackages.of(portal)).containsExactly(ticket);

        Command command = WorkPackages.change(model, List.of(portal, api), List.of(), List.of(ticket), "RBWDP-9 New one");
        command.execute();
        IWorkPackage created = WorkPackages.named(model, "rbwdp-9 new one");
        assertThat(created).isNotNull();
        assertThat(created.eContainer()).isEqualTo(model.getFolder(FolderType.IMPLEMENTATION_MIGRATION));
        assertThat(WorkPackages.elements(created)).containsExactly(api, portal);
        assertThat(WorkPackages.elements(ticket)).isEmpty();
        assertThat(WorkPackages.of(model)).containsExactly(created, ticket); // 9 before 20025

        command.undo();
        assertThat(WorkPackages.named(model, "RBWDP-9 New one")).isNull();
        assertThat(WorkPackages.elements(ticket)).containsExactly(portal);
    }

    @Test
    void linkingTwiceChangesNothingAndAnExistingNameIsReused() {
        assertThat(WorkPackages.change(model, List.of(portal), List.of(ticket), List.of(), "rbwdp-20025")).isNull();
        assertThat(WorkPackages.change(model, List.of(api), List.of(), List.of(), null)).isNull();
        run(WorkPackages.change(model, List.of(api), List.of(), List.of(), "rbwdp-20025"));
        assertThat(WorkPackages.of(model)).containsExactly(ticket);
        assertThat(WorkPackages.elements(ticket)).containsExactly(api, portal);
    }

    @Test
    void workPackageExportHasOnlyItsElementsOnTheViewsThatShowThem() {
        IArchimateElement loose = element(F.createNode(), "Loose");
        run(WorkPackages.change(model, List.of(loose), List.of(ticket), List.of(), null));
        List<EObject> exported = new ArrayList<>();

        Doc doc = ModelReader.read(ModelReader.Selection.of(List.of(ticket)), (v, p) -> { }, exported);

        assertThat(doc.title).isEqualTo("RBWDP-20025");
        assertThat(doc.pages).extracting(p -> p.title)
                .containsExactly("RBWDP-20025", "Applications", "Elements not in any view");
        DocPage first = doc.pages.get(0);
        assertThat(first.documentation).isEqualTo("Limits for payments");
        assertThat(first.contents).extracting(l -> l.label).containsExactly("Loose", "Portal");
        assertThat(first.relatedViews).extracting(l -> l.label).containsExactly("Applications");
        DocPage view = doc.pages.get(1);
        assertThat(view.contents).extracting(l -> l.label).containsExactly("Portal");
        Entry entry = view.entries.get(0);
        assertThat(entry.name).isEqualTo("Portal");
        assertThat(entry.workPackages).extracting(l -> l.label).containsExactly("RBWDP-20025");
        // its relationship to the API stays, the link to the work package is not one of them
        assertThat(entry.relations).extracting(r -> r.other.label).containsExactly("API");
        assertThat(doc.locate(api.getId())).isNull();
        assertThat(exported).contains(ticket, portal, loose, apps).doesNotContain(api, data, db)
                .containsAll(ticket.getTargetRelationships());
    }

    @Test
    void severalWorkPackagesAreEachDescribedOnTheFirstPage() {
        IWorkPackage other = workPackage("RBWDP-3");
        run(WorkPackages.change(model, List.of(db), List.of(other), List.of(), null));

        Doc doc = read(ticket, other);

        assertThat(doc.title).isEqualTo("RBWDP-20025, RBWDP-3");
        assertThat(doc.pages).extracting(p -> p.title).containsExactly("RBWDP-20025, RBWDP-3", "Applications", "Data");
        assertThat(doc.pages.get(0).entries).extracting(e -> e.name).containsExactly("RBWDP-20025", "RBWDP-3");
        assertThat(doc.pages.get(0).entries.get(1).changes).extracting(l -> l.label).containsExactly("DB");
    }

    @Test
    void aWorkPackageWithoutElementsCannotBeExported() {
        IWorkPackage empty = workPackage("RBWDP-1");
        assertThatThrownBy(() -> ModelReader.Selection.of(List.of(empty)))
                .hasMessageContaining("RBWDP-1 changes no elements");
    }

    @Test
    void wholeModelListsTheWorkPackagesOnAPageOfTheirOwn() {
        Doc doc = read(model);

        assertThat(doc.pages).extracting(p -> p.title)
                .containsExactly("Bank", "Applications", "Data", "Work packages");
        Entry wp = doc.pages.get(3).entries.get(0);
        assertThat(wp.name).isEqualTo("RBWDP-20025");
        assertThat(wp.changes).extracting(l -> l.label).containsExactly("Portal");
        assertThat(wp.relations).isEmpty();
        assertThat(doc.pages.get(1).entries.get(1).workPackages).extracting(l -> l.label).containsExactly("RBWDP-20025");
    }
}
