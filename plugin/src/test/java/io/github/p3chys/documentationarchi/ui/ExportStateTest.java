package io.github.p3chys.documentationarchi.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IProperty;

import io.github.p3chys.documentationarchi.ui.ExportState.Status;

class ExportStateTest {

    private static final IArchimateFactory F = IArchimateFactory.eINSTANCE;

    @TempDir
    Path folder;

    private IArchimateModel model;
    private IArchimateElement a;
    private IArchimateElement b;
    private IArchimateDiagramModel view;
    private IDiagramModelArchimateObject shown;

    @BeforeEach
    void model() {
        model = F.createArchimateModel();
        model.setDefaults();
        model.setName("Model");
        a = element("A");
        b = element("B");
        view = F.createArchimateDiagramModel();
        view.setName("View");
        model.getFolder(FolderType.DIAGRAMS).getElements().add(view);
        shown = F.createDiagramModelArchimateObject();
        shown.setArchimateElement(a);
        shown.setBounds(F.createBounds(10, 10, 120, 55));
        view.getChildren().add(shown);
    }

    private IArchimateElement element(String name) {
        IArchimateElement element = F.createApplicationComponent();
        element.setName(name);
        model.getFolder(FolderType.APPLICATION).getElements().add(element);
        return element;
    }

    /** Exports the whole model, as the export does, and notes it. */
    private ExportState export(ExportState state) throws Exception {
        List<EObject> exported = new ArrayList<>();
        ModelReader.read(ModelReader.Selection.of(List.of(model)), (v, p) -> { }, exported);
        state.record(model, ExportState.fingerprints(exported), 1000);
        return state;
    }

    private static Status status(ExportState state, EObject o) {
        return state.mark(o).status;
    }

    /** Publishes the whole model, as an export with "Publish" does. */
    private void publish(ExportState state, long time) throws Exception {
        List<EObject> exported = new ArrayList<>();
        io.github.p3chys.documentationarchi.doc.Doc doc = ModelReader.read(ModelReader.Selection.of(List.of(model)),
                (v, p) -> { }, exported);
        state.publish(model, doc, ExportState.fingerprints(exported), time, true);
    }

    @Test
    void whatAColleaguePublishedCountsAsExportedOnEveryComputer() throws Exception {
        java.nio.file.Files.createDirectories(folder.resolve("shared"));
        model.setFile(folder.resolve("shared/model.archimate").toFile());
        ExportState bob = new ExportState(folder.resolve("bob"));
        assertThat(status(bob, a)).isEqualTo(Status.NEW);

        publish(new ExportState(folder.resolve("alice")), 5000);
        assertThat(folder.resolve("shared/documentation.published")).exists();
        bob = new ExportState(folder.resolve("bob")); // Bob never exported anything himself
        assertThat(status(bob, a)).isEqualTo(Status.CURRENT);
        assertThat(status(bob, view)).isEqualTo(Status.CURRENT);
        assertThat(bob.mark(a).exported).isEqualTo(5000);

        shown.setBounds(F.createBounds(50, 50, 120, 55));
        assertThat(status(bob, view)).isEqualTo(Status.LAYOUT);
        a.setName("A renamed");
        assertThat(status(bob, a)).isEqualTo(Status.CHANGED);
        assertThat(status(bob, element("C"))).isEqualTo(Status.NEW);

        // his own export of the changed model counts too: either is enough
        export(bob);
        assertThat(status(bob, a)).isEqualTo(Status.CURRENT);
        assertThat(status(bob, view)).isEqualTo(Status.CURRENT);
    }

    @Test
    void modelNeverSavedHasNothingPublishedAndCannotBePublished() throws Exception {
        ExportState state = new ExportState(folder);
        assertThat(state.published(model).isEmpty()).isTrue();
        assertThat(status(state, a)).isEqualTo(Status.NEW);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> publish(state, 1)).hasMessageContaining("never saved");
    }

    @Test
    void whatChangesOrIsAddedAfterAnExportIsMarked() throws Exception {
        ExportState state = new ExportState(folder);
        assertThat(status(state, a)).isEqualTo(Status.NEW);

        export(state);
        assertThat(List.of(a, b, view, model)).allMatch(o -> status(state, o) == Status.CURRENT);
        assertThat(state.mark(a).exported).isEqualTo(1000);

        a.setDocumentation("Changed");
        IProperty p = F.createProperty();
        p.setKey("owner");
        b.getProperties().add(p);
        IArchimateElement c = element("C");
        IArchimateRelationship r = F.createServingRelationship();
        r.connect(a, c);
        model.getFolder(FolderType.RELATIONS).getElements().add(r);

        assertThat(status(state, a)).isEqualTo(Status.CHANGED);
        assertThat(status(state, b)).isEqualTo(Status.CHANGED);
        assertThat(status(state, c)).isEqualTo(Status.NEW);
        assertThat(status(state, r)).isEqualTo(Status.NEW);
        assertThat(status(state, view)).isEqualTo(Status.CURRENT);

        // kept on disk: a new start of Archi knows it too
        ExportState again = export(new ExportState(folder));
        assertThat(List.of(a, b, c, r, view)).allMatch(o -> status(again, o) == Status.CURRENT);
        a.setDocumentation("");
        assertThat(status(new ExportState(folder), a)).isEqualTo(Status.CHANGED);
    }

    @Test
    void aViewChangesWithItsPictureAnElementOnlyWithItself() {
        String element = ExportState.fingerprint(a);
        String picture = ExportState.fingerprint(view);

        IArchimateRelationship r = F.createServingRelationship();
        r.connect(a, b);
        model.getFolder(FolderType.RELATIONS).getElements().add(r);
        assertThat(ExportState.fingerprint(a)).isEqualTo(element); // a relationship is marked on its own

        shown.setBounds(F.createBounds(50, 10, 120, 55));
        assertThat(ExportState.fingerprint(view)).isNotEqualTo(picture);
        String moved = ExportState.fingerprint(view);
        a.setName("Renamed");
        assertThat(ExportState.fingerprint(view)).isNotEqualTo(moved); // the picture shows the name
        assertThat(ExportState.fingerprint(a)).isNotEqualTo(element);
    }

    @Test
    void printOrderIsNeverMarked() {
        IArchimateElement printOrder = F.createGrouping();
        printOrder.setName("PrintOrder");
        model.getFolder(FolderType.OTHER).getElements().add(printOrder);
        IArchimateRelationship link = F.createAssociationRelationship();
        link.connect(a, printOrder);

        assertThat(ExportState.tracked(printOrder)).isFalse();
        assertThat(ExportState.tracked(link)).isFalse();
        assertThat(ExportState.tracked(shown)).isFalse(); // part of its view
        assertThat(ExportState.tracked(a)).isTrue();
    }

    @Test
    void movingABoxOnlyChangesTheLayoutOfItsView() throws Exception {
        ExportState state = export(new ExportState(folder));

        shown.setBounds(F.createBounds(50, 40, 200, 80));
        shown.setFillColor("#ff0000");
        assertThat(status(state, view)).isEqualTo(Status.LAYOUT);
        assertThat(status(state, a)).isEqualTo(Status.CURRENT);

        IDiagramModelArchimateObject second = F.createDiagramModelArchimateObject();
        second.setArchimateElement(b);
        second.setBounds(F.createBounds(10, 100, 120, 55));
        view.getChildren().add(second);
        assertThat(status(state, view)).isEqualTo(Status.CHANGED); // what is on the view is content
    }

    @Test
    void entriesOfOlderVersionsStillReadAndAreNotAllChanged() throws Exception {
        ExportState state = new ExportState(folder);
        state.record(model, java.util.Map.of(a.getId(), ExportState.legacyFingerprint(a),
                view.getId(), ExportState.legacyFingerprint(view)), 1000);
        assertThat(status(state, a)).isEqualTo(Status.CURRENT);
        assertThat(status(state, view)).isEqualTo(Status.CURRENT);

        shown.setBounds(F.createBounds(70, 70, 120, 55));
        assertThat(status(state, view)).isEqualTo(Status.CHANGED); // an old entry cannot tell layout from content
        a.setName("Renamed");
        assertThat(status(state, a)).isEqualTo(Status.CHANGED);
    }
}
