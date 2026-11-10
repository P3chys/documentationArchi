package io.github.p3chys.documentationarchi.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IDiagramModelGroup;

import io.github.p3chys.documentationarchi.doc.DocPage;

class PictureAreasTest {

    private static final IArchimateFactory F = IArchimateFactory.eINSTANCE;

    private final IArchimateModel model = F.createArchimateModel();

    private IArchimateElement element(IArchimateElement element, String name) {
        element.setName(name);
        model.getFolder(FolderType.OTHER).getElements().add(element);
        return element;
    }

    private static IDiagramModelArchimateObject add(IDiagramModelContainer in, IArchimateElement element) {
        IDiagramModelArchimateObject figure = F.createDiagramModelArchimateObject();
        figure.setArchimateElement(element);
        in.getChildren().add(figure);
        return figure;
    }

    @Test
    void everyElementFigureNestedOnesAfterTheirsButNotPrintOrderOrJunctions() {
        model.setDefaults();
        IArchimateDiagramModel view = F.createArchimateDiagramModel();
        model.getFolder(FolderType.DIAGRAMS).getElements().add(view);
        IArchimateElement system = element(F.createApplicationComponent(), "System");
        IArchimateElement module = element(F.createApplicationComponent(), "Module");
        IArchimateElement actor = element(F.createBusinessActor(), "Client");
        IDiagramModelArchimateObject outer = add(view, system);
        IDiagramModelArchimateObject inner = add(outer, module);
        IDiagramModelGroup group = F.createDiagramModelGroup();
        view.getChildren().add(group);
        IDiagramModelArchimateObject inGroup = add(group, actor);
        IDiagramModelArchimateObject again = add(view, actor); // twice on the view: two areas
        add(view, element(F.createGrouping(), "PrintOrder"));
        add(view, element(F.createJunction(), ""));

        assertThat(PictureAreas.figures(view)).containsExactly(outer, inner, inGroup, again);
    }

    @Test
    void areasAreInPixelsOfTheImageAtItsWidthInsideTheMargin() {
        model.setDefaults();
        IArchimateElement system = element(F.createApplicationComponent(), "System");
        IArchimateElement unnamed = element(F.createNode(), "");
        // the diagram spans 40,30 to 440,230; drawn at twice the size with 10 diagram units around it
        List<DocPage.Area> areas = PictureAreas.areas(List.of(new PictureAreas.Box(system, 40, 30, 120, 55),
                new PictureAreas.Box(unnamed, 300, 200, 140, 30)), new int[] { 40, 30, 400, 200 }, 840, 440, 2);

        assertThat(areas).extracting(a -> a.id).containsExactly(system.getId(), unnamed.getId());
        assertThat(areas).extracting(a -> a.label).containsExactly("System", "Node");
        DocPage.Area first = areas.get(0);
        assertThat(new int[] { first.x, first.y, first.width, first.height }).containsExactly(10, 10, 120, 55);
        DocPage.Area last = areas.get(1);
        assertThat(new int[] { last.x, last.y, last.x + last.width, last.y + last.height })
                .containsExactly(270, 180, 410, 210); // the image is 420 x 220 at its width
    }
}
