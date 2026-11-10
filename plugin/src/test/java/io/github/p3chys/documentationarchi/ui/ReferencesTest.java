package io.github.p3chys.documentationarchi.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.gef.commands.Command;
import org.junit.jupiter.api.Test;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;

class ReferencesTest {

    private static final IArchimateFactory F = IArchimateFactory.eINSTANCE;

    @Test
    void referenceGoesOnALineOfItsOwnAtTheEnd() {
        assertThat(References.appended(null, "x")).isEqualTo("[[ref:x]]");
        assertThat(References.appended("text", "x")).isEqualTo("text\n[[ref:x]]");
        assertThat(References.appended("text\n", "x")).isEqualTo("text\n[[ref:x]]");
    }

    @Test
    void insertIsUndoableAndTargetsAreViewsAndElementsByName() {
        IArchimateModel model = F.createArchimateModel();
        model.setDefaults();
        IArchimateElement portal = F.createApplicationComponent();
        portal.setName("Portal");
        portal.setDocumentation("Serves");
        model.getDefaultFolderForObject(portal).getElements().add(portal);
        IArchimateElement order = F.createGrouping();
        order.setName("PrintOrder");
        model.getDefaultFolderForObject(order).getElements().add(order);
        IArchimateElement junction = F.createJunction();
        model.getDefaultFolderForObject(junction).getElements().add(junction);
        IArchimateDiagramModel view = F.createArchimateDiagramModel();
        view.setName("Apps");
        model.getDefaultFolderForObject(view).getElements().add(view);

        assertThat(References.targets(model)).containsExactly(view, portal);
        Command command = References.add(portal, view);
        command.execute();
        assertThat(portal.getDocumentation()).isEqualTo("Serves\n[[ref:" + view.getId() + "]]");
        command.undo();
        assertThat(portal.getDocumentation()).isEqualTo("Serves");
    }
}
