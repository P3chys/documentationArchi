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

    private static io.github.p3chys.documentationarchi.doc.Entry entry(Doc doc, String id) {
        return doc.pages.stream().flatMap(p -> p.entries.stream()).filter(e -> e.id.equals(id)).findFirst().orElse(null);
    }

    @Test
    void kindOfChangeNamesTheLinkAndShowsNextToTheTicketAndTheElement() {
        assertThat(WorkPackages.kind(ticket, portal)).isNull();
        run(WorkPackages.change(model, List.of(portal, api), List.of(ticket), List.of(), null, e -> e == api ? "new" : "change"));
        assertThat(WorkPackages.kind(ticket, portal)).isEqualTo("change"); // the link that was there is named
        assertThat(WorkPackages.kind(ticket, api)).isEqualTo("new");
        assertThat(WorkPackages.links(ticket).get(portal)).hasSize(1);
        // not said: the links stay as they are
        assertThat(WorkPackages.change(model, List.of(portal, api), List.of(ticket), List.of(), null, e -> null)).isNull();

        Doc doc = read(model);
        assertThat(entry(doc, api.getId()).workPackages).extracting(l -> l.label).containsExactly("RBWDP-20025 (new)");
        assertThat(entry(doc, ticket.getId()).changes).extracting(l -> l.label).containsExactly("API (new)", "Portal (change)");
    }

    @Test
    void closedWorkPackageStaysInTheModelButIsListedNowhereUnlessAskedFor() {
        IWorkPackage open = workPackage("RBWDP-20030");
        run(WorkPackages.change(model, List.of(portal), List.of(open), List.of(), null));
        Command close = WorkPackages.close(List.of(ticket), true);
        run(close);
        assertThat(WorkPackages.closed(ticket)).isTrue();
        assertThat(WorkPackages.close(List.of(ticket), true)).isNull();
        assertThat(WorkPackages.open(model)).containsExactly(open);
        assertThat(WorkPackages.of(model)).containsExactly(ticket, open);
        assertThat(WorkPackages.elements(ticket)).containsExactly(portal); // its links are kept

        Doc doc = read(model);
        assertThat(entry(doc, portal.getId()).workPackages).extracting(l -> l.label).containsExactly("RBWDP-20030");
        assertThat(entry(doc, ticket.getId())).isNull();
        assertThat(entry(doc, open.getId())).isNotNull();
        assertThat(doc.pages).allSatisfy(p -> assertThat(p.entries).noneMatch(e -> e.name.equals("RBWDP-20025")));

        // exported itself, it is all there
        Doc own = ModelReader.read(ModelReader.Selection.ofWorkPackages(model, List.of(ticket)), (view, page) -> { });
        assertThat(own.pages.get(0).title).isEqualTo("RBWDP-20025");
        assertThat(entry(own, portal.getId()).workPackages).extracting(l -> l.label).contains("RBWDP-20025");

        close.undo();
        assertThat(WorkPackages.closed(ticket)).isFalse();
        run(close);
        run(WorkPackages.close(List.of(ticket), false));
        assertThat(WorkPackages.closed(ticket)).isFalse();
        assertThat(ticket.getProperties()).isEmpty();
    }

    @Test
    void whatChangedSincePublishingAndHasNoOpenTicketIsFoundWithItsKindOfChange() {
        io.github.p3chys.documentationarchi.doc.Published published = new io.github.p3chys.documentationarchi.doc.Published();
        published.publish(read(model), java.util.Map.of(), 1000, true);
        assertThat(WorkPackages.unassigned(model, published.changesTo(read(model), true))).isEmpty();

        db.setDocumentation("Now a cluster.");            // changed, no ticket
        portal.setDocumentation("New login.");            // changed, has an open ticket
        IArchimateElement queue = element(F.createNode(), "Queue"); // new, no ticket
        IWorkPackage later = workPackage("RBWDP-20040");  // a work package is not something to link
        api.setName("Public API");
        run(WorkPackages.change(model, List.of(api), List.of(later), List.of(), null));
        run(WorkPackages.close(List.of(later), true));    // changed, its only ticket is closed
        model.getDefaultFolderForObject(db).getElements().remove(element(F.createNode(), "Gone before publishing"));

        java.util.Map<IArchimateElement, String> found = WorkPackages.unassigned(model, published.changesTo(read(model), true));
        assertThat(found).containsOnly(java.util.Map.entry(db, "change"), java.util.Map.entry(queue, "new"),
                java.util.Map.entry(api, "change"));

        run(WorkPackages.change(model, found.keySet(), List.of(ticket), List.of(), null, found::get));
        assertThat(WorkPackages.kind(ticket, queue)).isEqualTo("new");
        assertThat(WorkPackages.kind(ticket, db)).isEqualTo("change");
        assertThat(WorkPackages.unassigned(model, published.changesTo(read(model), true))).isEmpty();
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
