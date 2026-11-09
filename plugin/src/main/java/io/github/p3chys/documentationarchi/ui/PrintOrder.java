package io.github.p3chys.documentationarchi.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CompoundCommand;

import com.archimatetool.editor.diagram.commands.DiagramCommandFactory;
import com.archimatetool.editor.model.commands.AddListMemberCommand;
import com.archimatetool.editor.model.commands.DeleteArchimateElementCommand;
import com.archimatetool.editor.model.commands.DeleteArchimateRelationshipCommand;
import com.archimatetool.editor.model.commands.EObjectFeatureCommand;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimatePackage;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateComponent;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelObject;
import com.archimatetool.model.IFolder;

/**
 * Keeps the print order of a view the way modellers do it by hand (see {@link ModelReader}): a PrintOrder element
 * shown on the view, and an association named 001, 002, 003... from each element of the view to it, drawn on the
 * view. The changes come as one command, so Undo takes them back.
 */
final class PrintOrder {

    private static final IArchimateFactory F = IArchimateFactory.eINSTANCE;

    private PrintOrder() {
    }

    /** The PrintOrder element shown on the view, null if there is none. */
    static IArchimateElement onView(IDiagramModel view) {
        return printOrdersOn(view).stream().findFirst().orElse(null);
    }

    /**
     * Numbers the elements in this order: renames, adds, draws and removes links to the view's PrintOrder, which is
     * created and put on the view when it has none. Links to elements no view of that PrintOrder shows are removed.
     *
     * @return null when nothing changes
     */
    static Command save(IDiagramModel view, List<IArchimateElement> order) {
        CompoundCommand command = new CompoundCommand("Set Print Order");
        IArchimateElement printOrder = onView(view);
        IDiagramModelObject printOrderObject;
        if (printOrder == null) {
            printOrder = F.createGrouping();
            printOrder.setName(ModelReader.PRINT_ORDER);
            IFolder folder = view.getArchimateModel().getDefaultFolderForObject(printOrder);
            command.add(new AddListMemberCommand<>(folder.getElements(), printOrder));
            printOrderObject = diagramObject(printOrder, view);
            command.add(new AddListMemberCommand<>(view.getChildren(), printOrderObject));
        } else {
            printOrderObject = objectOf(printOrder, view);
        }
        Map<IArchimateElement, List<IArchimateRelationship>> links = links(printOrder);
        for (int i = 0; i < order.size(); i++) {
            IArchimateElement element = order.get(i);
            String name = name(i + 1);
            List<IArchimateRelationship> existing = links.remove(element);
            IArchimateRelationship link;
            if (existing == null) {
                link = F.createAssociationRelationship();
                link.setName(name);
                command.add(new CreateLink(link, element, printOrder));
            } else {
                // of several links, keep the one drawn on the view, else the oldest
                existing.stream().filter(r -> drawnOn(r, view)).findFirst().ifPresent(r -> {
                    existing.remove(r);
                    existing.add(0, r);
                });
                link = existing.get(0);
                if (!name.equals(link.getName())) {
                    command.add(new EObjectFeatureCommand("Rename", link, IArchimatePackage.Literals.NAMEABLE__NAME, name));
                }
                existing.subList(1, existing.size()).forEach(r -> delete(command, r, Set.of()));
            }
            if (!drawnOn(link, view)) {
                IDiagramModelObject elementObject = objectOf(element, view);
                command.add(link.getSource() == element ? new DrawLink(link, elementObject, printOrderObject)
                        : new DrawLink(link, printOrderObject, elementObject));
            }
        }
        Set<IArchimateElement> shown = new HashSet<>(order);
        for (IDiagramModel other : viewsOf(printOrder)) {
            shown.addAll(ModelReader.elementsOn(other));
        }
        links.forEach((element, relationships) -> {
            if (!shown.contains(element)) {
                relationships.forEach(r -> delete(command, r, Set.of()));
            }
        });
        return command.isEmpty() ? null : command;
    }

    /**
     * Takes the PrintOrder elements off the view with their links to its elements, except links that other views of
     * the same PrintOrder still use. A PrintOrder left on no view and with no links is deleted.
     *
     * @return null when the view shows no PrintOrder
     */
    static Command remove(IDiagramModel view) {
        CompoundCommand command = new CompoundCommand("Remove Print Order");
        for (IArchimateElement printOrder : printOrdersOn(view)) {
            Set<IDiagramModelObject> goingAway = new HashSet<>();
            for (IDiagramModelArchimateComponent c : printOrder.getReferencingDiagramComponents()) {
                if (c.getDiagramModel() == view && c instanceof IDiagramModelObject) {
                    goingAway.add((IDiagramModelObject) c);
                    command.add(DiagramCommandFactory.createDeleteDiagramObjectCommand((IDiagramModelObject) c));
                }
            }
            Set<IArchimateElement> stillUsed = new HashSet<>();
            List<IDiagramModel> otherViews = viewsOf(printOrder);
            otherViews.remove(view);
            otherViews.forEach(v -> stillUsed.addAll(ModelReader.elementsOn(v)));
            boolean keepsLinks = false;
            for (Map.Entry<IArchimateElement, List<IArchimateRelationship>> link : links(printOrder).entrySet()) {
                if (stillUsed.contains(link.getKey())) {
                    keepsLinks = true;
                } else {
                    link.getValue().forEach(r -> delete(command, r, goingAway));
                }
            }
            if (otherViews.isEmpty() && !keepsLinks && unrelated(printOrder)) {
                command.add(new DeleteArchimateElementCommand(printOrder));
            }
        }
        return command.isEmpty() ? null : command;
    }

    /** Positions as three digits, 001 to 999, so they also sort right as plain text. */
    static String name(int position) {
        return String.format("%03d", position);
    }

    /** The first object showing the element on the view. */
    private static IDiagramModelObject objectOf(IArchimateElement element, IDiagramModel view) {
        for (IDiagramModelArchimateComponent c : element.getReferencingDiagramComponents()) {
            if (c.getDiagramModel() == view && c instanceof IDiagramModelObject) {
                return (IDiagramModelObject) c;
            }
        }
        throw new IllegalArgumentException(element.getName() + " is not on the view " + view.getName());
    }

    private static boolean drawnOn(IArchimateRelationship link, IDiagramModel view) {
        return link.getReferencingDiagramComponents().stream().anyMatch(c -> c.getDiagramModel() == view);
    }

    private static List<IArchimateElement> printOrdersOn(IDiagramModel view) {
        List<IArchimateElement> found = new ArrayList<>();
        for (IArchimateElement e : ModelReader.elementsOn(view)) {
            if (ModelReader.isPrintOrder(e)) {
                found.add(e);
            }
        }
        return found;
    }

    /**
     * The relationships between the PrintOrder and other elements, either way round, by the other element; each
     * element's in model order, oldest first.
     */
    private static Map<IArchimateElement, List<IArchimateRelationship>> links(IArchimateElement printOrder) {
        Map<IArchimateElement, List<IArchimateRelationship>> links = new LinkedHashMap<>();
        Set<IArchimateRelationship> connected = new HashSet<>(printOrder.getTargetRelationships());
        connected.addAll(printOrder.getSourceRelationships());
        List<IArchimateRelationship> relationships = new ArrayList<>();
        if (printOrder.getArchimateModel() != null && !connected.isEmpty()) {
            for (TreeIterator<EObject> it = printOrder.getArchimateModel().eAllContents(); it.hasNext();) {
                EObject o = it.next();
                if (connected.remove(o)) {
                    relationships.add((IArchimateRelationship) o);
                }
            }
        }
        relationships.addAll(connected); // not in the model (yet)
        for (IArchimateRelationship r : relationships) {
            IArchimateConcept other = r.getSource() == printOrder ? r.getTarget() : r.getSource();
            if (other instanceof IArchimateElement && !ModelReader.isPrintOrder(other)) {
                links.computeIfAbsent((IArchimateElement) other, k -> new ArrayList<>()).add(r);
            }
        }
        return links;
    }

    /** Whether nothing but the links to elements connects to it (so deleting it loses nothing else). */
    private static boolean unrelated(IArchimateElement printOrder) {
        List<IArchimateRelationship> relationships = new ArrayList<>(printOrder.getTargetRelationships());
        relationships.addAll(printOrder.getSourceRelationships());
        return relationships.stream().allMatch(r -> {
            IArchimateConcept other = r.getSource() == printOrder ? r.getTarget() : r.getSource();
            return other instanceof IArchimateElement && !ModelReader.isPrintOrder(other)
                    && r.getSourceRelationships().isEmpty() && r.getTargetRelationships().isEmpty();
        });
    }

    private static List<IDiagramModel> viewsOf(IArchimateElement element) {
        List<IDiagramModel> views = new ArrayList<>();
        for (IDiagramModelArchimateComponent c : element.getReferencingDiagramComponents()) {
            if (!views.contains(c.getDiagramModel())) {
                views.add(c.getDiagramModel());
            }
        }
        return views;
    }

    /** Deletes the relationship and its connections on views, except those of objects deleted anyway. */
    static void delete(CompoundCommand command, IArchimateRelationship r, Set<IDiagramModelObject> goingAway) {
        for (IDiagramModelArchimateComponent c : r.getReferencingDiagramComponents()) {
            if (c instanceof IDiagramModelArchimateConnection) {
                IDiagramModelArchimateConnection connection = (IDiagramModelArchimateConnection) c;
                if (!goingAway.contains(connection.getSource()) && !goingAway.contains(connection.getTarget())) {
                    command.add(DiagramCommandFactory.createDeleteDiagramConnectionCommand(connection));
                }
            }
        }
        command.add(new DeleteArchimateRelationshipCommand(r));
    }

    /** The PrintOrder on the view, right of everything else on it. */
    private static IDiagramModelArchimateObject diagramObject(IArchimateElement printOrder, IDiagramModel view) {
        int right = 0;
        int top = Integer.MAX_VALUE;
        for (Object child : view.getChildren()) {
            if (child instanceof IDiagramModelObject) {
                var bounds = ((IDiagramModelObject) child).getBounds();
                right = Math.max(right, bounds.getX() + Math.max(bounds.getWidth(), 120));
                top = Math.min(top, bounds.getY());
            }
        }
        IDiagramModelArchimateObject object = F.createDiagramModelArchimateObject();
        object.setArchimateElement(printOrder);
        object.setBounds(F.createBounds(right + 40, top == Integer.MAX_VALUE ? 40 : top, 120, 55));
        return object;
    }

    /** Adds the relationship from the element to the PrintOrder (or a work package) to the model. */
    static final class CreateLink extends Command {
        private final IArchimateRelationship relationship;
        private final IArchimateElement element;
        private final IArchimateElement printOrder;
        private IFolder folder;

        CreateLink(IArchimateRelationship relationship, IArchimateElement element, IArchimateElement printOrder) {
            this("Create Print Order Link", relationship, element, printOrder);
        }

        CreateLink(String label, IArchimateRelationship relationship, IArchimateElement element, IArchimateElement printOrder) {
            super(label);
            this.relationship = relationship;
            this.element = element;
            this.printOrder = printOrder;
        }

        @Override
        public void execute() {
            relationship.connect(element, printOrder);
            folder = element.getArchimateModel().getDefaultFolderForObject(relationship);
            folder.getElements().add(relationship);
        }

        @Override
        public void undo() {
            folder.getElements().remove(relationship);
            relationship.disconnect();
        }

        @Override
        public void redo() {
            folder.getElements().add(relationship);
            relationship.reconnect();
        }
    }

    /** Draws the relationship on the view, between the objects of its two ends. */
    private static final class DrawLink extends Command {
        private final IDiagramModelArchimateConnection connection = F.createDiagramModelArchimateConnection();
        private final IDiagramModelObject source;
        private final IDiagramModelObject target;

        DrawLink(IArchimateRelationship relationship, IDiagramModelObject source, IDiagramModelObject target) {
            super("Draw Print Order Link");
            connection.setArchimateRelationship(relationship);
            this.source = source;
            this.target = target;
        }

        @Override
        public void execute() {
            connection.connect(source, target);
        }

        @Override
        public void undo() {
            connection.disconnect();
        }

        @Override
        public void redo() {
            connection.reconnect();
        }
    }
}
