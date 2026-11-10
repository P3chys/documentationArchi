package io.github.p3chys.documentationarchi.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IProperties;
import com.archimatetool.model.IProperty;

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.text.KeywordRules;

/** What the rules and the doc:exclude property leave out of an export. */
class ExclusionTest {

    private static final IArchimateFactory F = IArchimateFactory.eINSTANCE;

    private IArchimateModel model;
    private IArchimateElement shown;
    private IArchimateElement other;
    private IArchimateDiagramModel view;
    private IArchimateRelationship serving;

    @BeforeEach
    void model() {
        model = F.createArchimateModel();
        model.setDefaults();
        model.setName("Model");
        shown = element("Shown", model.getFolder(FolderType.BUSINESS));
        other = element("Other", model.getFolder(FolderType.APPLICATION));
        view = view("View");
        add(view, shown);
        add(view, other);
        serving = F.createServingRelationship();
        serving.connect(other, shown);
        model.getFolder(FolderType.RELATIONS).getElements().add(serving);
    }

    private IArchimateElement element(String name, IFolder folder) {
        IArchimateElement element = F.createBusinessActor();
        element.setName(name);
        folder.getElements().add(element);
        return element;
    }

    private IArchimateDiagramModel view(String name) {
        IArchimateDiagramModel v = F.createArchimateDiagramModel();
        v.setName(name);
        model.getFolder(FolderType.DIAGRAMS).getElements().add(v);
        return v;
    }

    private static void add(IArchimateDiagramModel view, IArchimateElement element) {
        IDiagramModelArchimateObject object = F.createDiagramModelArchimateObject();
        object.setArchimateElement(element);
        view.getChildren().add(object);
    }

    private static void property(IProperties on, String key, String value) {
        IProperty p = F.createProperty();
        p.setKey(key);
        p.setValue(value);
        on.getProperties().add(p);
    }

    private Doc read(String rules, Object... selected) {
        return read(rules, new ArrayList<>(), selected);
    }

    private Doc read(String rules, List<Exclusion> counted, Object... selected) {
        ModelReader.Selection selection = ModelReader.Selection.of(List.of(selected));
        KeywordRules parsed = KeywordRules.parse(rules);
        Exclusion exclusion = selection.exclusion(parsed);
        counted.add(exclusion);
        return ModelReader.read(selection, (v, p) -> { }, new ArrayList<>(), parsed, exclusion);
    }

    private static List<String> entries(DocPage page) {
        List<String> names = new ArrayList<>();
        page.entries.forEach(e -> names.add(e.name));
        return names;
    }

    @Test
    void anExcludedElementIsNotDescribedListedOrRelatedTo() {
        property(other, "doc:exclude", "");

        Doc doc = read("", model);
        DocPage page = doc.pages.get(1);

        assertThat(entries(page)).containsExactly("Shown");
        assertThat(page.contents).extracting(l -> l.label).containsExactly("Shown");
        assertThat(page.entries.get(0).relations).isEmpty();
        assertThat(doc.locate(other.getId())).isNull();
    }

    @Test
    void falseDoesNotExclude() {
        property(other, "doc:exclude", " False ");
        assertThat(entries(read("", model).pages.get(1))).containsExactly("Other", "Shown");
    }

    @Test
    void anExcludedViewHasNoPageAndIsNotListedOnItsElements() {
        IArchimateDiagramModel hidden = view("Hidden");
        add(hidden, shown);
        property(hidden, "doc:exclude", "true");

        Doc doc = read("", model);

        assertThat(doc.pages).extracting(p -> p.title).doesNotContain("Hidden");
        assertThat(doc.pages.get(1).entries.get(1).views).extracting(l -> l.label).containsExactly("View");
    }

    @Test
    void aFolderLeavesOutEverythingInIt() {
        IFolder folder = F.createFolder();
        folder.setName("Drafts");
        model.getFolder(FolderType.BUSINESS).getFolders().add(folder);
        IArchimateElement inside = element("Inside", folder);
        add(view, inside);
        property(folder, "doc:exclude", "yes");

        assertThat(entries(read("", model).pages.get(1))).doesNotContain("Inside");
    }

    @Test
    void anExcludedLabelLeavesOutViewsElementsAndRelationships() {
        shown.setDocumentation("Not final #Draft");
        IArchimateDiagramModel draft = view("Draft view");
        draft.setDocumentation("see [[draft]]");
        serving.setDocumentation("#draft");
        other.setDocumentation("#drafting is another word");

        Doc doc = read("exclude label draft", model);

        assertThat(doc.pages).extracting(p -> p.title).containsExactly("Model", "View");
        assertThat(entries(doc.pages.get(1))).containsExactly("Other");
        assertThat(doc.pages.get(1).entries.get(0).relations).isEmpty();
    }

    @Test
    void anExcludedRelationshipIsNotShownButItsEndsAre() {
        property(serving, "doc:exclude", "1");

        DocPage page = read("", model).pages.get(1);

        assertThat(entries(page)).containsExactly("Other", "Shown");
        assertThat(page.entries.get(0).relations).isEmpty();
        assertThat(page.entries.get(1).relations).isEmpty();
    }

    @Test
    void whatWasSelectedDirectlyIsExportedAnyway() {
        property(view, "doc:exclude", "");
        property(other, "doc:exclude", "");

        Doc viewDoc = read("", view);
        assertThat(viewDoc.pages.get(0).title).isEqualTo("View");
        assertThat(entries(viewDoc.pages.get(0))).containsExactly("Shown");

        Doc elementDoc = read("", other);
        assertThat(entries(elementDoc.pages.get(0))).containsExactly("Other");
    }

    @Test
    void aSelectedFolderIsExportedButItsExcludedContentsAreNot() {
        IFolder folder = model.getFolder(FolderType.BUSINESS);
        property(folder, "doc:exclude", "");
        property(shown, "doc:exclude", "");
        element("Second", folder);

        Doc doc = read("", folder);

        assertThat(entries(doc.pages.get(0))).containsExactly("Second");
    }

    @Test
    void theExportCountsWhatWasLeftOut() {
        property(other, "doc:exclude", "");
        property(serving, "doc:exclude", "");
        List<Exclusion> counted = new ArrayList<>();

        read("", counted, model);

        assertThat(counted.get(0).count()).isEqualTo(2);
    }

    @Test
    void aReferenceToWhatIsLeftOutShowsItsNameAsPlainText() {
        shown.setDocumentation("see [[ref:" + other.getId() + "]]");
        property(other, "doc:exclude", "");

        Doc doc = read("", model);

        assertThat(doc.locate(other.getId())).isNull();
        assertThat(doc.names).containsEntry(other.getId(), "Other");
    }

    @Test
    void docPropertiesAndHiddenPropertiesAreNeverPrinted() {
        property(shown, "doc:home", "x");
        property(shown, "doc:exclude", "false");
        property(shown, "Secret-key", "1");
        property(shown, "internal.a", "2");
        property(shown, "owner", "Adam");

        Doc doc = read("hide property secret-key\nhide property INTERNAL*", model);

        assertThat(doc.pages.get(1).entries.get(1).properties).extracting(p -> p.key).containsExactly("owner");
    }
}
