package io.github.p3chys.documentationarchi.ui;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;

import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateModelObject;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateComponent;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelComponent;
import com.archimatetool.model.IDiagramModelNote;
import com.archimatetool.model.IDiagramModelReference;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.INameable;
import com.archimatetool.model.IProperty;

import io.github.p3chys.documentationarchi.doc.Assets;
import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.doc.Link;
import io.github.p3chys.documentationarchi.doc.Property;
import io.github.p3chys.documentationarchi.doc.Relation;

/**
 * Turns what the user selected into a {@link Doc}: one page per view, plus an overview page when there is more than
 * one page. An element is described on the first page that shows it; later pages link there.
 * <p>
 * Elements connected to an element named PrintOrder are listed by the name of that relationship, before the others,
 * which follow by name; a PrintOrder shown on the view wins over others. The "Print Order..." menu
 * ({@link PrintOrder}) keeps these elements and relationships for the user. PrintOrder is a modelling aid, so it and
 * its relationships are never part of the export ({@link ViewImages} takes it out of the pictures too).
 */
final class ModelReader {

    static final String PRINT_ORDER = "PrintOrder";
    private static final Pattern CHUNK = Pattern.compile("\\d+|\\D+");

    /** What to export: the model, or some views and elements of it. */
    static final class Selection {
        final IArchimateModel model;
        final boolean wholeModel;
        final Set<IDiagramModel> views = new LinkedHashSet<>();
        final Set<IArchimateElement> elements = new LinkedHashSet<>();

        private Selection(IArchimateModel model, boolean wholeModel) {
            this.model = model;
            this.wholeModel = wholeModel;
        }

        /** @throws IllegalArgumentException if nothing can be exported, or the objects are from several models */
        static Selection of(List<?> objects) {
            Set<IArchimateModel> models = objects.stream().filter(IArchimateModelObject.class::isInstance)
                    .map(o -> ((IArchimateModelObject) o).getArchimateModel()).collect(Collectors.toSet());
            if (models.size() > 1) {
                throw new IllegalArgumentException("The selection spans several models. Select objects of one model.");
            }
            if (models.isEmpty()) {
                throw new IllegalArgumentException("Select a model, views or elements to export.");
            }
            IArchimateModel model = models.iterator().next();
            Selection selection = new Selection(model, objects.contains(model));
            if (selection.wholeModel) {
                selection.views.addAll(model.getDiagramModels());
                model.eAllContents().forEachRemaining(o -> {
                    if (o instanceof IArchimateElement) {
                        selection.elements.add((IArchimateElement) o);
                    }
                });
            } else {
                objects.forEach(selection::add);
            }
            if (selection.views.isEmpty() && selection.elements.isEmpty() && !selection.wholeModel) {
                throw new IllegalArgumentException("The selection holds no views or elements to export.");
            }
            return selection;
        }

        private void add(Object o) {
            if (o instanceof IFolder) {
                ((IFolder) o).getElements().forEach(this::add);
                ((IFolder) o).getFolders().forEach(this::add);
            } else if (o instanceof IDiagramModel) {
                views.add((IDiagramModel) o);
            } else if (o instanceof IArchimateElement) {
                elements.add((IArchimateElement) o);
            } else if (o instanceof IArchimateRelationship) {
                add(((IArchimateRelationship) o).getSource());
                add(((IArchimateRelationship) o).getTarget());
            } else if (o instanceof IDiagramModelArchimateComponent) {
                add(((IDiagramModelArchimateComponent) o).getArchimateConcept());
            } else if (o instanceof IDiagramModelComponent) {
                views.add(((IDiagramModelComponent) o).getDiagramModel()); // a note or group: its view
            }
        }
    }

    private final Selection selection;
    private final BiConsumer<IDiagramModel, DocPage> images;
    private final Map<String, List<IDiagramModel>> viewsOf = new HashMap<>();
    private final Set<String> described = new HashSet<>();

    private ModelReader(Selection selection, BiConsumer<IDiagramModel, DocPage> images) {
        this.selection = selection;
        this.images = images;
    }

    /** @param images fills in the picture of a view's page */
    static Doc read(Selection selection, BiConsumer<IDiagramModel, DocPage> images) {
        return new ModelReader(selection, images).read();
    }

    private Doc read() {
        IArchimateModel model = selection.model;
        for (IDiagramModel view : model.getDiagramModels()) {
            for (IArchimateElement element : elementsOn(view)) {
                viewsOf.computeIfAbsent(element.getId(), k -> new ArrayList<>()).add(view);
            }
        }

        List<DocPage> pages = new ArrayList<>();
        selection.views.forEach(view -> pages.add(viewPage(view)));

        List<IArchimateElement> rest = ordered(selection.elements, null).stream()
                .filter(e -> !described.contains(e.getId())).collect(Collectors.toList());
        if (!rest.isEmpty()) {
            DocPage page = new DocPage("elements", selection.wholeModel ? "Elements not in any view" : "Selected elements");
            rest.forEach(e -> page.contents.add(link(e)));
            describe(rest, rest, page);
            pages.add(page);
        }

        if (selection.wholeModel || pages.size() > 1) {
            DocPage overview = new DocPage(model.getId(), label(model));
            overview.documentation = model.getPurpose();
            overview.properties = properties(model.getProperties());
            pages.forEach(p -> overview.contents.add(new Link(p.id, p.title)));
            pages.add(0, overview);
        }
        // the Assets folder next to the model file; a model never saved has none
        return new Doc(label(model), pages,
                Assets.in(model.getFile() == null ? null : model.getFile().getAbsoluteFile().toPath().getParent()));
    }

    private DocPage viewPage(IDiagramModel view) {
        DocPage page = new DocPage(view.getId(), label(view));
        page.documentation = view.getDocumentation();
        page.properties = properties(view.getProperties());
        images.accept(view, page);
        for (TreeIterator<EObject> i = view.eAllContents(); i.hasNext();) {
            EObject o = i.next();
            if (o instanceof IDiagramModelNote && !((IDiagramModelNote) o).getContent().isBlank()) {
                page.notes.add(((IDiagramModelNote) o).getContent());
            } else if (o instanceof IDiagramModelReference) {
                IDiagramModel target = ((IDiagramModelReference) o).getReferencedModel();
                page.relatedViews.add(new Link(target.getId(), label(target)));
            }
        }
        List<IArchimateElement> shown = ordered(elementsOn(view), view);
        List<IArchimateElement> fresh = shown.stream().filter(e -> !described.contains(e.getId())).collect(Collectors.toList());
        shown.forEach(e -> page.contents.add(link(e)));
        describe(fresh, shown, page);
        return page;
    }

    /** @param onPage all elements of the page; relationships to any other element are listed apart */
    private void describe(List<IArchimateElement> elements, List<IArchimateElement> onPage, DocPage page) {
        Set<IArchimateConcept> inside = new HashSet<>(onPage);
        for (IArchimateElement element : elements) {
            described.add(element.getId());
            page.entries.add(entry(element, inside));
        }
    }

    private Entry entry(IArchimateElement element, Set<IArchimateConcept> inside) {
        List<Relation> relations = new ArrayList<>();
        element.getSourceRelationships().stream().filter(r -> !isPrintOrder(r.getTarget()))
                .forEach(r -> relations.add(relation(r, true, inside)));
        element.getTargetRelationships().stream().filter(r -> !isPrintOrder(r.getSource()))
                .forEach(r -> relations.add(relation(r, false, inside)));
        List<Link> views = viewsOf.getOrDefault(element.getId(), List.of()).stream().distinct()
                .map(v -> new Link(v.getId(), label(v))).collect(Collectors.toList());
        return new Entry(element.getId(), label(element), typeName(element), element.getDocumentation(),
                properties(element.getProperties()), relations, views);
    }

    private static Relation relation(IArchimateRelationship r, boolean outgoing, Set<IArchimateConcept> inside) {
        IArchimateConcept other = outgoing ? r.getTarget() : r.getSource();
        return new Relation(outgoing, typeName(r), r.getName(), r.getDocumentation(), link(other), !inside.contains(other));
    }

    private static Link link(IArchimateConcept concept) {
        return new Link(concept.getId(), label(concept), typeName(concept));
    }

    static Set<IArchimateElement> elementsOn(IDiagramModel view) {
        Set<IArchimateElement> elements = new LinkedHashSet<>();
        view.eAllContents().forEachRemaining(o -> {
            if (o instanceof IDiagramModelArchimateObject) {
                elements.add(((IDiagramModelArchimateObject) o).getArchimateElement());
            }
        });
        return elements;
    }

    /** The elements of the view, in print order. */
    static List<IArchimateElement> ordered(IDiagramModel view) {
        return ordered(elementsOn(view), view);
    }

    /** The elements to describe, in print order. @param view the view they are listed for, null for none */
    private static List<IArchimateElement> ordered(Iterable<IArchimateElement> elements, IDiagramModel view) {
        Set<IArchimateElement> onView = view == null ? Set.of() : elementsOn(view);
        Map<IArchimateElement, String> positions = new HashMap<>();
        List<IArchimateElement> list = new ArrayList<>();
        for (IArchimateElement element : elements) {
            if (!isPrintOrder(element)) {
                list.add(element);
                positions.put(element, position(element, onView));
            }
        }
        list.sort(Comparator.<IArchimateElement, String>comparing(positions::get,
                Comparator.nullsLast(ModelReader::compareNatural))
                .thenComparing(ModelReader::label, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    /** The name of the element's relationship to PrintOrder, preferring a PrintOrder shown on the view. */
    private static String position(IArchimateElement element, Set<IArchimateElement> onView) {
        String position = null;
        List<IArchimateRelationship> relationships = new ArrayList<>(element.getSourceRelationships());
        relationships.addAll(element.getTargetRelationships());
        for (IArchimateRelationship r : relationships) {
            IArchimateConcept other = r.getSource() == element ? r.getTarget() : r.getSource();
            if (isPrintOrder(other) && r.getName() != null && !r.getName().isBlank()) {
                if (onView.contains(other)) {
                    return r.getName();
                }
                position = position == null ? r.getName() : position;
            }
        }
        return position;
    }

    static boolean isPrintOrder(IArchimateConcept concept) {
        return concept instanceof IArchimateElement && PRINT_ORDER.equalsIgnoreCase(concept.getName().strip());
    }

    /** Numbers inside the names compare by value: 2 before 10, 1.9 before 1.10. */
    static int compareNatural(String a, String b) {
        Matcher x = CHUNK.matcher(a);
        Matcher y = CHUNK.matcher(b);
        while (x.find() && y.find()) {
            String p = x.group();
            String q = y.group();
            boolean numbers = Character.isDigit(p.charAt(0)) && Character.isDigit(q.charAt(0));
            int result = numbers ? new BigInteger(p).compareTo(new BigInteger(q)) : p.compareToIgnoreCase(q);
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(a.length(), b.length());
    }

    private static List<Property> properties(EList<IProperty> properties) {
        return properties.stream().map(p -> new Property(p.getKey(), p.getValue())).collect(Collectors.toList());
    }

    private static String label(INameable object) {
        String name = object.getName();
        return name == null || name.isBlank() ? typeName(object) : name;
    }

    /** "BusinessActor" to "Business Actor". */
    private static String typeName(EObject object) {
        return object.eClass().getName().replaceAll("(?<=[a-z])(?=[A-Z])", " ");
    }

    private static String typeName(INameable object) {
        return typeName((EObject) object);
    }
}
