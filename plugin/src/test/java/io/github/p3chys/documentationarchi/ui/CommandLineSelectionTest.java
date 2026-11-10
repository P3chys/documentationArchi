package io.github.p3chys.documentationarchi.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IWorkPackage;

class CommandLineSelectionTest {

    private static final IArchimateFactory F = IArchimateFactory.eINSTANCE;

    private IArchimateModel model;
    private IArchimateDiagramModel overview;
    private IArchimateDiagramModel orders;
    private IArchimateDiagramModel invoices;
    private IWorkPackage unused;

    @BeforeEach
    void model() {
        model = F.createArchimateModel();
        model.setDefaults();
        model.setName("Bank");
        IFolder sales = F.createFolder();
        sales.setName("Sales");
        model.getFolder(FolderType.DIAGRAMS).getFolders().add(sales);
        overview = view("Overview", model.getFolder(FolderType.DIAGRAMS));
        orders = view("Orders", sales);
        invoices = view("Invoices", sales);
        IArchimateElement portal = F.createApplicationComponent();
        portal.setName("Portal");
        model.getFolder(FolderType.APPLICATION).getElements().add(portal);
        IWorkPackage ticket = workPackage("RBWDP-1 Limits");
        unused = workPackage("RBWDP-2 Nothing yet");
        WorkPackages.change(model, List.of(portal), List.of(ticket), List.of(), null).execute();
    }

    private IWorkPackage workPackage(String name) {
        IWorkPackage wp = F.createWorkPackage();
        wp.setName(name);
        model.getDefaultFolderForObject(wp).getElements().add(wp);
        return wp;
    }

    private IArchimateDiagramModel view(String name, IFolder folder) {
        IArchimateDiagramModel view = F.createArchimateDiagramModel();
        view.setName(name);
        folder.getElements().add(view);
        return view;
    }

    private ModelReader.Selection select(String workPackages, String views, String folders) {
        return CommandLineSelection.select(model, CommandLineSelection.names(workPackages),
                CommandLineSelection.names(views), CommandLineSelection.names(folders));
    }

    @Test
    void namesAreSplitAtCommas() {
        assertThat(CommandLineSelection.names(" a , b,,c ")).containsExactly("a", "b", "c");
        assertThat(CommandLineSelection.names(null)).isEmpty();
    }

    @Test
    void nothingNamedExportsTheWholeModel() {
        ModelReader.Selection selection = select(null, null, null);
        assertThat(selection.wholeModel).isTrue();
        assertThat(selection.views).containsExactlyInAnyOrder(overview, orders, invoices);
    }

    @Test
    void viewsByNameAndId() {
        ModelReader.Selection selection = select(null, "Overview, " + orders.getId(), null);
        assertThat(selection.wholeModel).isFalse();
        assertThat(selection.views).containsExactlyInAnyOrder(overview, orders);
    }

    @Test
    void foldersByPath() {
        assertThat(select(null, null, "Views/Sales").views).containsExactlyInAnyOrder(orders, invoices);
        assertThat(select(null, "Overview", "Views/Sales").views).hasSize(3);
    }

    @Test
    void workPackagesByNameOrTicketKey() {
        assertThat(select("RBWDP-1", null, null).workPackages).hasSize(1);
        assertThat(select("rbwdp-1 ,RBWDP-1 Limits", null, null).workPackages).hasSize(1);
    }

    @Test
    void unknownNamesAreListed() {
        assertThatThrownBy(() -> select(null, "Nope,Overview,Other", "Views/Gone"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("view \"Nope\"").hasMessageContaining("view \"Other\"")
                .hasMessageContaining("folder \"Views/Gone\"").hasMessageNotContaining("\"Overview\"");
        assertThatThrownBy(() -> select("RBWDP-99", null, null)).hasMessageContaining("work package \"RBWDP-99\"");
    }

    @Test
    void anAmbiguousViewNameIsAnError() {
        view("Orders", model.getFolder(FolderType.DIAGRAMS));
        assertThatThrownBy(() -> select(null, "Orders", null)).hasMessageContaining("ambiguous");
    }

    @Test
    void workPackagesDoNotMixWithViews() {
        assertThatThrownBy(() -> select("RBWDP-1", "Overview", null)).hasMessageContaining("separate runs");
    }

    @Test
    void aWorkPackageThatChangesNothingCannotBeExported() {
        assertThat(unused).isNotNull();
        assertThatThrownBy(() -> select("RBWDP-2", null, null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no elements");
    }
}
