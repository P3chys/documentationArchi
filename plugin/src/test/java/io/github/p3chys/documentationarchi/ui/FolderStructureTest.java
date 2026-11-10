package io.github.p3chys.documentationarchi.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IFolder;

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Picture;

/** The pages of views under their folders, as the model tree has them. */
class FolderStructureTest {

    private static final IArchimateFactory F = IArchimateFactory.eINSTANCE;

    private IArchimateModel model;
    private IFolder sales;
    private IFolder orders;

    @BeforeEach
    void model() {
        model = F.createArchimateModel();
        model.setDefaults();
        model.setName("Shop");
        view(top(), "Zeta");
        view(top(), "Alpha");
        IFolder support = folder(top(), "Support");
        sales = folder(top(), "Sales");
        sales.setDocumentation("What we sell");
        orders = folder(sales, "Orders");
        view(support, "Tickets");
        view(orders, "Order flow");
        view(sales, "Pricing");
        view(sales, "Catalogue");
    }

    private IFolder top() {
        return model.getFolder(FolderType.DIAGRAMS);
    }

    private static IFolder folder(IFolder in, String name) {
        IFolder folder = F.createFolder();
        folder.setName(name);
        in.getFolders().add(folder);
        return folder;
    }

    private static IArchimateDiagramModel view(IFolder in, String name) {
        IArchimateDiagramModel view = F.createArchimateDiagramModel();
        view.setName(name);
        in.getElements().add(view);
        return view;
    }

    private Doc read(Object... objects) {
        return ModelReader.read(ModelReader.Selection.of(List.of(objects)), (view, page) -> { });
    }

    private static List<String> titles(Doc doc) {
        return doc.pages.stream().map(p -> p.title).collect(Collectors.toList());
    }

    @Test
    void pagesFollowTheTreeOwnViewsBeforeSubFolders() {
        Doc doc = read(model);

        assertThat(titles(doc)).containsExactly("Shop", "Alpha", "Zeta", "Catalogue", "Pricing", "Order flow", "Tickets");
        DocPage flow = doc.pages.get(5);
        assertThat(flow.folder.name).isEqualTo("Orders");
        assertThat(flow.folder.parent.name).isEqualTo("Sales");
        assertThat(flow.folder.parent.documentation).isEqualTo("What we sell");
        assertThat(doc.pages.get(1).folder).isNull();
        assertThat(doc.folders).extracting(f -> f.name).containsExactly("Sales", "Orders", "Support");
        assertThat(doc.outline).extracting(n -> n.page != null ? n.page.title : n.folder.name)
                .containsExactly("Shop", "Alpha", "Zeta", "Sales", "Support");
    }

    @Test
    void folderExportKeepsThePathBelowTheViewsFolder() {
        Doc doc = read(sales);

        assertThat(titles(doc)).containsExactly("Shop", "Catalogue", "Pricing", "Order flow");
        assertThat(doc.pages.get(1).folder.name).isEqualTo("Sales");
    }

    @Test
    void modelWithoutSubFoldersStaysAsItWas() {
        top().getFolders().clear();
        Doc doc = read(model);

        assertThat(titles(doc)).containsExactly("Shop", "Zeta", "Alpha");
        assertThat(doc.nested()).isFalse();
    }

    @Test
    void selectedViewsAreNotNested() {
        Doc doc = read(orders.getElements().get(0), sales.getElements().get(0));

        assertThat(titles(doc)).containsExactly("Shop", "Order flow", "Pricing");
        assertThat(doc.nested()).isFalse();
    }

    @Test
    void picturesInTemporaryFilesAreDeletedWhenReadingFails() {
        List<Picture> drawn = new ArrayList<>();
        assertThatThrownBy(() -> ModelReader.read(ModelReader.Selection.of(List.of(model)), (view, page) -> {
            if (drawn.size() == 2) {
                throw new IllegalStateException("cannot draw");
            }
            try {
                page.image = Picture.temporary(new byte[] { 1 });
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            drawn.add(page.image);
        })).hasMessage("cannot draw");

        assertThat(drawn).hasSize(2).allSatisfy(p -> assertThatThrownBy(p::bytes).isInstanceOf(java.io.IOException.class));
    }

    @Test
    void oneViewInAFolderIsOnePage() {
        Doc doc = read(orders);

        assertThat(titles(doc)).containsExactly("Order flow");
        assertThat(doc.nested()).isFalse();
    }
}
