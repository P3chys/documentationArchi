package io.github.p3chys.documentationarchi.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.notify.impl.AdapterImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelNote;
import com.archimatetool.model.IJunction;

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.text.KeywordRules;

class PreviewTargetTest {

    private static final IArchimateFactory F = IArchimateFactory.eINSTANCE;

    private IArchimateModel model;
    private IArchimateElement actor;
    private IArchimateElement portal;
    private IArchimateRelationship serving;
    private IArchimateDiagramModel view;
    private IDiagramModelArchimateObject actorFigure;
    private IDiagramModelNote note;

    @BeforeEach
    void model() {
        model = F.createArchimateModel();
        model.setDefaults();
        model.setName("Model");
        actor = element(F.createBusinessActor(), "Client", FolderType.BUSINESS);
        actor.setDocumentation("Who **buys**");
        portal = element(F.createApplicationComponent(), "Portal", FolderType.APPLICATION);
        serving = F.createServingRelationship();
        serving.connect(portal, actor);
        model.getFolder(FolderType.RELATIONS).getElements().add(serving);
        view = F.createArchimateDiagramModel();
        view.setName("Sales");
        view.setDocumentation("The sales view");
        model.getFolder(FolderType.DIAGRAMS).getElements().add(view);
        actorFigure = F.createDiagramModelArchimateObject();
        actorFigure.setArchimateElement(actor);
        view.getChildren().add(actorFigure);
        note = F.createDiagramModelNote();
        view.getChildren().add(note);
    }

    private IArchimateElement element(IArchimateElement element, String name, FolderType folder) {
        element.setName(name);
        model.getFolder(folder).getElements().add(element);
        return element;
    }

    private String html(Object selected) {
        Doc doc = ModelReader.read(ModelReader.Selection.of(List.of(model)), (v, page) -> {
            page.image = new byte[] { 1 };
            page.imageWidth = 100;
        });
        return PreviewTarget.html(PreviewTarget.of(selected), doc, KeywordRules.defaults(), p -> "picture-" + p.title + ".png");
    }

    @Test
    void figuresStandForTheirElementNotesForTheirView() {
        assertThat(PreviewTarget.of(actorFigure)).isSameAs(actor);
        assertThat(PreviewTarget.of(note)).isSameAs(view);
        assertThat(PreviewTarget.of(view)).isSameAs(view);
        assertThat(PreviewTarget.of(model)).isSameAs(model);
        assertThat(PreviewTarget.of("text")).isNull();
        assertThat(PreviewTarget.view(view)).isSameAs(view);
        assertThat(PreviewTarget.view(actor)).isNull();
    }

    @Test
    void viewIsItsWholePageWithItsPicture() {
        assertThat(html(view)).contains("<h1>Sales</h1>").contains("The sales view")
                .contains("src=\"picture-Sales.png\"").contains("<h3 id=\"e-" + actor.getId() + "\">")
                .contains("Who <strong>buys</strong>");
    }

    @Test
    void elementIsItsDescriptionOnThePageThatHasIt() {
        assertThat(html(actorFigure)).contains("Described on <a href=\"#ref-" + view.getId() + "\">Sales</a>")
                .contains("<a href=\"#ref-" + portal.getId() + "\">Portal</a>") // its relationship, to the other end
                .doesNotContain("picture-");
        assertThat(html(portal)).contains("Described on <a href=\"#ref-elements\">Elements not in any view</a>");
    }

    @Test
    void relationshipIsBothEnds() {
        assertThat(html(serving)).contains("<h3 id=\"e-" + actor.getId() + "\">").contains("<h3 id=\"e-" + portal.getId() + "\">");
    }

    @Test
    void modelIsTheOverview() {
        assertThat(html(model)).contains("<h1>Model</h1>").contains("#ref-" + view.getId());
    }

    @Test
    void whatTheExportNeverShowsSaysWhy() {
        IJunction junction = F.createJunction();
        model.getFolder(FolderType.OTHER).getElements().add(junction);
        IArchimateElement order = element(F.createGrouping(), "PrintOrder", FolderType.OTHER);

        assertThat(html(junction)).contains("Junctions are never described");
        assertThat(html(order)).contains("PrintOrder only sets the order");
        assertThat(html(model.getFolder(FolderType.BUSINESS))).contains(PreviewTarget.NOTHING);
        assertThat(PreviewTarget.html(null, null, null, null)).contains(PreviewTarget.NOTHING);
        EcoreUtil.delete(portal, true);
        assertThat(PreviewTarget.html(portal, null, null, null)).contains("no longer in the model");
    }

    @Test
    void realChangesOfTheModelCount() {
        List<Notification> seen = new ArrayList<>();
        AdapterImpl spy = new AdapterImpl() {
            @Override
            public void notifyChanged(Notification n) {
                seen.add(n);
            }
        };
        actor.eAdapters().add(spy);
        actor.setName("Customer");
        actor.setName("Customer"); // the same again: no change
        IArchimateModel other = F.createArchimateModel();

        assertThat(seen).isNotEmpty();
        assertThat(seen).filteredOn(n -> PreviewTarget.affects(n, model)).hasSize(1);
        assertThat(seen).noneMatch(n -> PreviewTarget.affects(n, other));
        assertThat(seen).noneMatch(n -> PreviewTarget.affects(n, null));
    }

    @Test
    void pictureIsDrawnAgainOnlyWhenItsViewChanges() {
        int[] drawn = { 0 };
        PictureCache cache = new PictureCache((v, page) -> {
            page.image = new byte[] { (byte) ++drawn[0] };
            page.imageWidth = 50;
        });
        DocPage page = new DocPage(view.getId(), "Sales");

        cache.fill(view, page);
        cache.fill(view, new DocPage(view.getId(), "Sales"));
        portal.setDocumentation("not on the view");
        cache.fill(view, page);
        assertThat(drawn[0]).isEqualTo(1);
        assertThat(page.image).containsExactly(1);
        assertThat(page.imageWidth).isEqualTo(50);

        actor.setName("Customer"); // its name is in the picture
        cache.fill(view, page);
        actorFigure.getBounds();
        actorFigure.setBounds(F.createBounds(10, 10, 120, 55)); // moved
        cache.fill(view, page);
        assertThat(drawn[0]).isEqualTo(3);
        assertThat(page.image).containsExactly(3);
    }

    @ParameterizedTest
    @CsvSource({
        "about:blank, LOAD",
        "file:///C:/tmp/preview-1.html, LOAD",
        "file:///C:/tmp/preview-1.html#ref-id-1, SHOW",
        "about:blank#ref-id-1, SHOW",
        "https://example.com/a?b=1, OPEN",
        "mailto:someone@example.com, OPEN",
        "file:///C:/models/Assets/SLA%202026.pdf, OPEN",
        "file:///C:/models/Assets/../secret.txt, IGNORE",
        "file:///C:/Windows/notepad.exe, IGNORE",
        "javascript:alert(1), IGNORE",
        "ftp://example.com/x, IGNORE",
    })
    void linksNeverLeaveThePreview(String location, PreviewLinks.Action action) {
        Path page = PreviewLinks.file("file:///C:/tmp/preview-1.html");
        Path assets = PreviewLinks.file("file:///C:/models/Assets");
        assertThat(PreviewLinks.decide(location, page, assets)).isEqualTo(action);
        assertThat(PreviewLinks.decide("file:///C:/models/Assets/x.png", page, null)).isEqualTo(PreviewLinks.Action.IGNORE);
    }

    @Test
    void fileLinksAreFiles(@TempDir Path dir) {
        Path file = dir.resolve("SLA 2026.pdf");
        assertThat(PreviewLinks.file(file.toUri().toString())).isEqualTo(file.toAbsolutePath());
        assertThat(PreviewLinks.file(file.toUri() + "#page=2")).isEqualTo(file.toAbsolutePath());
        assertThat(PreviewLinks.file("https://example.com")).isNull();
    }
}
