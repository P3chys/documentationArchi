package io.github.p3chys.documentationarchi.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.gef.commands.Command;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;

class PrintOrderTest {

    private static final IArchimateFactory F = IArchimateFactory.eINSTANCE;

    private IArchimateModel model;
    private IArchimateElement a;
    private IArchimateElement b;
    private IArchimateElement c;
    private IArchimateDiagramModel view;

    @BeforeEach
    void model() {
        model = F.createArchimateModel();
        model.setDefaults();
        a = element("A");
        b = element("B");
        c = element("C");
        view = view("View");
        for (IArchimateElement e : List.of(a, b, c)) {
            add(view, e);
        }
    }

    private IArchimateElement element(String name) {
        IArchimateElement element = F.createNode();
        element.setName(name);
        model.getFolder(FolderType.TECHNOLOGY).getElements().add(element);
        return element;
    }

    private IArchimateDiagramModel view(String name) {
        IArchimateDiagramModel v = F.createArchimateDiagramModel();
        v.setName(name);
        model.getFolder(FolderType.DIAGRAMS).getElements().add(v);
        return v;
    }

    private static IDiagramModelArchimateObject add(IArchimateDiagramModel v, IArchimateElement element) {
        IDiagramModelArchimateObject object = F.createDiagramModelArchimateObject();
        object.setArchimateElement(element);
        object.setBounds(F.createBounds(0, 0, 120, 55));
        v.getChildren().add(object);
        return object;
    }

    private IArchimateElement printOrder() {
        IArchimateElement printOrder = F.createGrouping();
        printOrder.setName("PrintOrder");
        model.getFolder(FolderType.OTHER).getElements().add(printOrder);
        return printOrder;
    }

    private IArchimateRelationship link(IArchimateElement element, IArchimateElement printOrder, String name) {
        IArchimateRelationship r = F.createAssociationRelationship();
        r.setName(name);
        r.connect(element, printOrder);
        model.getFolder(FolderType.RELATIONS).getElements().add(r);
        return r;
    }

    /** Element name to the names of its links to the PrintOrder. */
    private static Map<String, List<String>> links(IArchimateElement printOrder) {
        Map<String, List<String>> links = new TreeMap<>();
        List<IArchimateRelationship> relationships = new ArrayList<>(printOrder.getTargetRelationships());
        relationships.addAll(printOrder.getSourceRelationships());
        for (IArchimateRelationship r : relationships) {
            IArchimateElement other = (IArchimateElement) (r.getSource() == printOrder ? r.getTarget() : r.getSource());
            links.computeIfAbsent(other.getName(), k -> new ArrayList<>()).add(r.getName());
        }
        return links;
    }

    private List<IArchimateElement> printOrders() {
        List<IArchimateElement> found = new ArrayList<>();
        model.eAllContents().forEachRemaining(o -> {
            if (o instanceof IArchimateElement && ModelReader.isPrintOrder((IArchimateElement) o)) {
                found.add((IArchimateElement) o);
            }
        });
        return found;
    }

    /** The connections on the view. */
    private static long drawnLinks(IArchimateDiagramModel v) {
        List<Object> connections = new ArrayList<>();
        v.eAllContents().forEachRemaining(o -> {
            if (o instanceof IDiagramModelArchimateConnection) {
                connections.add(o);
            }
        });
        return connections.size();
    }

    @Test
    void positionsHaveThreeDigits() {
        assertThat(List.of(PrintOrder.name(1), PrintOrder.name(42), PrintOrder.name(999))).containsExactly("001", "042", "999");
    }

    @Test
    void viewWithoutPrintOrderGetsOneWithNumberedLinks() {
        Command command = PrintOrder.save(view, List.of(c, a, b));
        command.execute();

        IArchimateElement printOrder = PrintOrder.onView(view);
        assertThat(printOrder).isNotNull();
        assertThat(printOrder.eContainer()).isSameAs(model.getFolder(FolderType.OTHER));
        assertThat(links(printOrder)).isEqualTo(Map.of("A", List.of("002"), "B", List.of("003"), "C", List.of("001")));
        assertThat(ModelReader.ordered(view)).containsExactly(c, a, b);
        assertThat(drawnLinks(view)).isEqualTo(3);

        command.undo();

        assertThat(printOrders()).isEmpty();
        assertThat(a.getSourceRelationships()).isEmpty();
        assertThat(model.getFolder(FolderType.RELATIONS).getElements()).isEmpty();
        assertThat(ModelReader.ordered(view)).containsExactly(a, b, c);
        assertThat(drawnLinks(view)).isZero();

        command.redo();

        assertThat(ModelReader.ordered(view)).containsExactly(c, a, b);
        assertThat(drawnLinks(view)).isEqualTo(3);
    }

    @Test
    void existingPrintOrderOnTheViewIsRenumberedCompletedAndCleaned() {
        IArchimateElement printOrder = printOrder();
        add(view, printOrder);
        IArchimateRelationship drawn = link(a, printOrder, "10");
        IArchimateRelationship reversed = F.createAssociationRelationship(); // drawn the other way round
        reversed.setName("1");
        reversed.connect(printOrder, b);
        model.getFolder(FolderType.RELATIONS).getElements().add(reversed);
        link(b, printOrder, "7"); // a duplicate
        IArchimateElement gone = element("Gone"); // no longer on any view of the PrintOrder
        link(gone, printOrder, "3");
        IDiagramModelArchimateConnection connection = F.createDiagramModelArchimateConnection();
        connection.setArchimateRelationship(drawn);
        connection.connect(view.getChildren().get(0), view.getChildren().get(3));

        PrintOrder.save(view, List.of(b, c, a)).execute();

        assertThat(PrintOrder.onView(view)).isSameAs(printOrder);
        assertThat(printOrders()).containsExactly(printOrder);
        assertThat(links(printOrder)).isEqualTo(Map.of("A", List.of("003"), "B", List.of("001"), "C", List.of("002")));
        assertThat(drawn.getName()).isEqualTo("003");
        assertThat(connection.getSource()).isNotNull(); // the drawn link stays drawn, the others get drawn
        assertThat(drawnLinks(view)).isEqualTo(3);
        IDiagramModelArchimateConnection reversedDrawn = (IDiagramModelArchimateConnection) reversed
                .getReferencingDiagramComponents().get(0);
        assertThat(((IDiagramModelArchimateObject) reversedDrawn.getSource()).getArchimateElement()).isSameAs(printOrder);
        assertThat(ModelReader.ordered(view)).containsExactly(b, c, a);
    }

    @Test
    void unchangedOrderChangesNothing() {
        PrintOrder.save(view, List.of(b, a, c)).execute();

        assertThat(PrintOrder.save(view, List.of(b, a, c))).isNull();
    }

    @Test
    void sharedPrintOrderKeepsLinksOfElementsOnItsOtherViews() {
        IArchimateElement printOrder = printOrder();
        add(view, printOrder);
        IArchimateDiagramModel other = view("Other");
        IArchimateElement elsewhere = element("Elsewhere");
        add(other, elsewhere);
        add(other, printOrder);
        link(elsewhere, printOrder, "5");

        PrintOrder.save(view, List.of(a, b, c)).execute();

        assertThat(links(printOrder)).containsEntry("Elsewhere", List.of("5")).containsEntry("A", List.of("001"));

        PrintOrder.remove(view).execute();

        assertThat(PrintOrder.onView(view)).isNull();
        assertThat(PrintOrder.onView(other)).isSameAs(printOrder);
        assertThat(links(printOrder)).containsOnlyKeys("Elsewhere");
    }

    @Test
    void removeDeletesThePrintOrderAndItsLinksAndUndoBringsThemBack() {
        PrintOrder.save(view, List.of(c, b, a)).execute();
        IArchimateElement printOrder = PrintOrder.onView(view);

        Command remove = PrintOrder.remove(view);
        remove.execute();

        assertThat(printOrders()).isEmpty();
        assertThat(view.getChildren()).hasSize(3);
        assertThat(drawnLinks(view)).isZero();
        assertThat(model.getFolder(FolderType.RELATIONS).getElements()).isEmpty();
        assertThat(ModelReader.ordered(view)).containsExactly(a, b, c);

        remove.undo();

        assertThat(PrintOrder.onView(view)).isSameAs(printOrder);
        assertThat(ModelReader.ordered(view)).containsExactly(c, b, a);
    }

    @Test
    void removeWithoutPrintOrderIsNothing() {
        assertThat(PrintOrder.remove(view)).isNull();
    }
}
