package io.github.p3chys.documentationarchi.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelNote;
import com.archimatetool.model.IProperty;
import com.archimatetool.model.IWorkPackage;

import io.github.p3chys.documentationarchi.text.KeywordRules;

/** What Check Documentation finds, without a UI. */
class DocCheckTest {

    private static final IArchimateFactory F = IArchimateFactory.eINSTANCE;

    @TempDir
    Path folder;

    private IArchimateModel model;
    private IArchimateElement described;
    private IArchimateDiagramModel view;

    @BeforeEach
    void model() {
        model = F.createArchimateModel();
        model.setDefaults();
        model.setName("Model");
        described = element("Described", "Has a text");
        view = view("View", "A view");
        add(view, described);
    }

    private IArchimateElement element(String name, String documentation) {
        IArchimateElement element = F.createBusinessActor();
        element.setName(name);
        element.setDocumentation(documentation);
        model.getFolder(FolderType.BUSINESS).getElements().add(element);
        return element;
    }

    private IArchimateDiagramModel view(String name, String documentation) {
        IArchimateDiagramModel v = F.createArchimateDiagramModel();
        v.setName(name);
        v.setDocumentation(documentation);
        model.getFolder(FolderType.DIAGRAMS).getElements().add(v);
        return v;
    }

    private static void add(IArchimateDiagramModel view, IArchimateElement element) {
        IDiagramModelArchimateObject object = F.createDiagramModelArchimateObject();
        object.setArchimateElement(element);
        view.getChildren().add(object);
    }

    private List<DocCheck.Finding> check(String rules) {
        return DocCheck.check(model, KeywordRules.parse(KeywordRules.DEFAULTS + "\n" + rules));
    }

    private static List<String> of(List<DocCheck.Finding> findings, DocCheck.Kind kind) {
        return findings.stream().filter(f -> f.kind == kind).map(f -> f.name).collect(Collectors.toList());
    }

    @Test
    void aWellDocumentedModelHasNoFindings() {
        assertThat(check("")).isEmpty();
    }

    @Test
    void elementsAndViewsWithoutADescriptionAreFound() {
        IArchimateElement bare = element("Bare", " ");
        add(view, bare);
        add(view("Bare view", ""), described);

        List<DocCheck.Finding> findings = check("");

        assertThat(of(findings, DocCheck.Kind.NO_DESCRIPTION)).containsExactlyInAnyOrder("Bare", "Bare view");
    }

    @Test
    void elementsOnNoViewAndViewsWithoutElementsAreFound() {
        element("Lonely", "text");
        view("Empty", "text");
        IWorkPackage ticket = F.createWorkPackage();
        ticket.setName("ABC-1");
        ticket.setDocumentation("text");
        ticket.setName("ABC-1");
        model.getFolder(FolderType.IMPLEMENTATION_MIGRATION).getElements().add(ticket);

        List<DocCheck.Finding> findings = check("");

        assertThat(of(findings, DocCheck.Kind.ON_NO_VIEW)).containsExactly("Lonely");
        assertThat(of(findings, DocCheck.Kind.EMPTY_VIEW)).containsExactly("Empty");
        assertThat(of(findings, DocCheck.Kind.WORK_PACKAGE)).containsExactly("ABC-1");
    }

    @Test
    void aWorkPackageThatChangesAnElementIsFine() {
        IWorkPackage ticket = F.createWorkPackage();
        ticket.setName("ABC-1");
        ticket.setDocumentation("text");
        model.getFolder(FolderType.IMPLEMENTATION_MIGRATION).getElements().add(ticket);
        com.archimatetool.model.IArchimateRelationship link = F.createAssociationRelationship();
        link.connect(described, ticket);
        model.getFolder(FolderType.RELATIONS).getElements().add(link);

        assertThat(check("")).isEmpty();
    }

    @Test
    void missingAndAmbiguousAssetsAreFound() throws Exception {
        Files.createDirectories(folder.resolve("Assets/a"));
        Files.createDirectories(folder.resolve("Assets/b"));
        Files.writeString(folder.resolve("Assets/a/same.png"), "1");
        Files.writeString(folder.resolve("Assets/b/same.png"), "2");
        Files.writeString(folder.resolve("Assets/there.png"), "3");
        model.setFile(folder.resolve("m.archimate").toFile());
        described.setDocumentation("[[asset:there.png]] [[asset:gone.png]] [[asset:same.png]]");

        List<DocCheck.Finding> findings = check("");

        assertThat(findings).extracting(f -> f.kind).containsOnly(DocCheck.Kind.ASSET);
        assertThat(findings).extracting(f -> f.detail).anyMatch(d -> d.contains("gone.png") && d.contains("not found"))
                .anyMatch(d -> d.contains("same.png") && d.contains("matches 2"));
        assertThat(findings).hasSize(2).allMatch(f -> f.object == described);
    }

    @Test
    void referencesToObjectsThatNoLongerExistAreFound() {
        described.setDocumentation("see [[ref:" + view.getId() + "]] and [[ref:id-gone|that]]");

        List<DocCheck.Finding> findings = check("");

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).kind).isEqualTo(DocCheck.Kind.REFERENCE);
        assertThat(findings.get(0).detail).contains("id-gone");
    }

    @Test
    void wordsThatLookLikeAConfiguredLabelAreFound() {
        described.setDocumentation("#risk fine, #rsik typo, #riskk typo, #unrelated, #ab, #123, <sql>select '#rsiks'</sql>");
        IProperty property = F.createProperty();
        property.setKey("k");
        property.setValue("#todoo here");
        view.getProperties().add(property);
        IDiagramModelNote note = F.createDiagramModelNote();
        note.setContent("#Deprecated is fine #deprecatd is not");
        view.getChildren().add(note);

        List<DocCheck.Finding> findings = check("");

        assertThat(findings).extracting(f -> f.detail).containsExactlyInAnyOrder("#rsik (did you mean #risk?)",
                "#riskk (did you mean #risk?)", "#todoo (did you mean #todo?)", "#deprecatd (did you mean #deprecated?)");
    }

    @Test
    void excludedObjectsAreNotChecked() {
        IArchimateElement hidden = element("Hidden", "");
        IProperty exclude = F.createProperty();
        exclude.setKey("doc:exclude");
        exclude.setValue("");
        hidden.getProperties().add(exclude);
        IArchimateElement draft = element("Draft", "#draft [[ref:gone]]");

        assertThat(check("exclude label draft")).isEmpty();
        assertThat(draft).isNotNull();
    }

    @Test
    void theListIsGroupedByKindWithCounts() {
        element("Lonely", "");
        element("Lonely 2", "");

        String text = DocCheck.format(check(""));

        assertThat(text).contains("Elements and views without a description (2)\n  - Lonely: Business Actor\n")
                .contains("Elements on no view (2)\n");
    }

    @Test
    void distanceCountsSingleCharacterChanges() {
        assertThat(DocCheck.distance("risk", "risk")).isZero();
        assertThat(DocCheck.distance("rsk", "risk")).isEqualTo(1);
        assertThat(DocCheck.distance("rsik", "risk")).isEqualTo(2);
    }
}
