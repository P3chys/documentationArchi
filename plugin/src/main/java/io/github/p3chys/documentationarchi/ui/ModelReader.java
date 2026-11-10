package io.github.p3chys.documentationarchi.ui;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.osgi.framework.FrameworkUtil;

import com.archimatetool.model.IAccessRelationship;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateModelObject;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IAssignmentRelationship;
import com.archimatetool.model.IAssociationRelationship;
import com.archimatetool.model.IBusinessRole;
import com.archimatetool.model.ICompositionRelationship;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateComponent;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelComponent;
import com.archimatetool.model.IDiagramModelGroup;
import com.archimatetool.model.IDiagramModelNote;
import com.archimatetool.model.IDiagramModelObject;
import com.archimatetool.model.IDiagramModelReference;
import com.archimatetool.model.IDocumentable;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IJunction;
import com.archimatetool.model.INameable;
import com.archimatetool.model.IProfile;
import com.archimatetool.model.IProperty;
import com.archimatetool.model.IServingRelationship;
import com.archimatetool.model.ITextContent;
import com.archimatetool.model.ITriggeringRelationship;
import com.archimatetool.model.IWorkPackage;

import io.github.p3chys.documentationarchi.doc.Assets;
import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocFolder;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.doc.Link;
import io.github.p3chys.documentationarchi.doc.ObjectText;
import io.github.p3chys.documentationarchi.doc.Property;
import io.github.p3chys.documentationarchi.doc.Relation;
import io.github.p3chys.documentationarchi.text.KeywordRules;
import io.github.p3chys.documentationarchi.text.RichText;

/**
 * Turns what the user selected into a {@link Doc}: one page per view, plus an overview page when there is more than
 * one page. An element is described on the first page that shows it; later pages link there.
 * <p>
 * Elements connected to a PrintOrder (an element named PrintOrder, or of any name with the specialization PrintOrder)
 * on the view are listed by the first number in the name of that relationship, the smallest of several, before the
 * others, which follow by name. The "Print Order..." menu
 * ({@link PrintOrder}) keeps these elements and relationships for the user. PrintOrder is a modelling aid, so it and
 * its relationships are never part of the export ({@link ViewImages} takes it out of the pictures too).
 * <p>
 * Work packages ({@link WorkPackages}) are listed with the elements they change, and each element with its work
 * packages, instead of their links among the relationships. Exporting work packages exports only the elements they
 * change: a page for the work package, the views that show those elements, and the elements on no view.
 * <p>
 * Junctions are never described or listed: a relationship to one is followed through it, and through junctions
 * after it, to the elements on the other side, and shown as one relationship to all of them, with "and" or "or".
 * <p>
 * In an element's description, {@code `name`} (or {@code ``name``}) stands for the element at the other end of its
 * relationship of that name, either way round: a link to it, or for a part composed into it of its own type, that
 * part's description in its place. Such relationships are not listed again among those going out. Access
 * relationships read as what they do and can build their SQL, associations to a Business Role {@code [auth] ROLE} say
 * who may use the element, and {@code [parameters]} elements become its input and output.
 * <p>
 * An element with a property {@value #CONFLUENCE_URL} is documented on that Confluence page, and a work package named
 * after a ticket ({@code ABC-123}) in Jira when the rules say where ({@code jira <url>}): links to them go there.
 * <p>
 * An element whose {@value #HOME} property names a view of the export that shows it is described on that view's
 * page instead of the first ("Describe Here", {@link HomeView}). The property itself is never shown.
 */
final class ModelReader {

    static final String PRINT_ORDER = "PrintOrder";
    /** The property holding the id of the view an element is described on. */
    static final String HOME = "doc:home";
    /** How the model tree sorts names. */
    private static final Collator TREE = Collator.getInstance();
    private static final Pattern CHUNK = Pattern.compile("\\d+|\\D+");
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    /** The property of an element holding the address of its Confluence page. */
    static final String CONFLUENCE_URL = "confluenceUrl";
    /** A work package named like this is a Jira ticket. */
    private static final Pattern TICKET = Pattern.compile("[A-Z][A-Z0-9_]*-\\d+");
    /** {@code ``name``}, or {@code `name`}: a relationship of the element named so, written into the text. */
    private static final Pattern TOKEN = Pattern.compile("(?<!`)``([^`\\n]+)``(?!`)|(?<!`)`([^`\\n]+)`(?!`)");
    private static final Pattern AUTH = Pattern.compile("(?i)^\\s*\\[auth]\\s*(\\S.*?)\\s*$");
    /** The roles from the fewest rights to the most: a role named here allows the ones after it too. */
    private static final List<String> ROLES = List.of("VIEW", "DEV", "OPE", "ADMIN");
    private static final Pattern SELLIST = Pattern.compile("(?is)<sellist>(.*?)</sellist>");
    private static final Pattern UPDLIST = Pattern.compile("(?is)<updlist>(.*?)</updlist>");
    private static final Pattern TECHNICALLY = Pattern.compile("(?im)^[ \\t]*Technically:[ \\t]*$");
    /** Assignment verbs, from the source's end and from the target's. */
    private static final Map<String, String> PASSIVE = Map.of("provides", "provided-by", "publishes", "published-by",
            "generates", "generated-by", "implements", "implemented-by");

    /** What to export: the model, or some views and elements of it. */
    static final class Selection {
        final IArchimateModel model;
        final boolean wholeModel;
        final Set<IDiagramModel> views = new LinkedHashSet<>();
        final Set<IArchimateElement> elements = new LinkedHashSet<>();
        /** Work packages to export; then {@link #elements} are the elements they change, and views are not set. */
        final Set<IWorkPackage> workPackages = new LinkedHashSet<>();
        /** What the user selected themselves: exported even if the rules leave it out, see {@link Exclusion}. */
        final Set<Object> exempt = new LinkedHashSet<>();
        /** The whole model or a folder: the views' pages go under their folders. */
        boolean folders;

        private Selection(IArchimateModel model, boolean wholeModel) {
            this.model = model;
            this.wholeModel = wholeModel;
        }

        /** Work packages: their names; anything else: the model's. Names the export folder and the document. */
        String title() {
            if (workPackages.isEmpty()) {
                return label(model);
            }
            List<String> names = workPackages.stream().map(ModelReader::label).collect(Collectors.toList());
            return names.size() <= 3 ? String.join(", ", names)
                    : String.join(", ", names.subList(0, 3)) + " and " + (names.size() - 3) + " more";
        }

        /** What the rules leave out of this export. */
        Exclusion exclusion(KeywordRules rules) {
            return new Exclusion(rules, exempt);
        }

        private void exempt(Object o) {
            if (o instanceof IArchimateModel) {
                return;
            }
            exempt.add(o);
            if (o instanceof IDiagramModelArchimateComponent) {
                exempt.add(((IDiagramModelArchimateComponent) o).getArchimateConcept());
            } else if (o instanceof IDiagramModelComponent) {
                exempt.add(((IDiagramModelComponent) o).getDiagramModel());
            } else if (o instanceof IArchimateRelationship) {
                exempt.add(((IArchimateRelationship) o).getSource());
                exempt.add(((IArchimateRelationship) o).getTarget());
            }
        }

        /** A selection of work packages only: they are exported with the elements they change. */
        static Selection ofWorkPackages(IArchimateModel model, Collection<IWorkPackage> workPackages) {
            Selection selection = new Selection(model, false);
            selection.workPackages.addAll(workPackages);
            selection.exempt.addAll(workPackages);
            workPackages.forEach(wp -> selection.elements.addAll(WorkPackages.elements(wp)));
            if (selection.elements.isEmpty()) {
                throw new IllegalArgumentException((workPackages.size() == 1 ? "The work package " + selection.title()
                        + " changes" : "The work packages change") + " no elements yet. Link elements to it with "
                        + "\"Link to Work Package...\".");
            }
            return selection;
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
            List<Object> concepts = objects.stream().map(o -> o instanceof IDiagramModelArchimateComponent
                    ? ((IDiagramModelArchimateComponent) o).getArchimateConcept() : o).collect(Collectors.toList());
            if (concepts.stream().allMatch(IWorkPackage.class::isInstance)) {
                return ofWorkPackages(model, concepts.stream().map(IWorkPackage.class::cast)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
            }
            Selection selection = new Selection(model, objects.contains(model));
            objects.forEach(selection::exempt);
            selection.folders = selection.wholeModel;
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
                folders = true;
                ((IFolder) o).getElements().forEach(this::add);
                ((IFolder) o).getFolders().forEach(this::add);
            } else if (o instanceof IDiagramModel) {
                views.add((IDiagramModel) o);
            } else if (o instanceof IJunction) {
                return; // never described on its own
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
    /** The views that get a page, with the elements they show. */
    private final Map<String, Set<IArchimateElement>> viewPages = new HashMap<>();
    private final Collection<EObject> exported;
    /** For the Labels page: which labels there are; for hiding properties. */
    private final KeywordRules rules;
    private final Exclusion exclusion;
    /** The views and elements to export: those of the selection that the rules do not leave out. */
    private final Set<IDiagramModel> views;
    private final Set<IArchimateElement> elements;

    private ModelReader(Selection selection, BiConsumer<IDiagramModel, DocPage> images, Collection<EObject> exported,
            KeywordRules rules, Exclusion exclusion) {
        this.selection = selection;
        this.images = images;
        this.exported = exported;
        this.rules = rules;
        this.exclusion = exclusion;
        this.views = visible(selection.views, new LinkedHashSet<>());
        this.elements = visible(selection.elements, new LinkedHashSet<>());
    }

    private String t(String key) {
        return rules.texts().get(key);
    }

    private <T extends EObject, C extends Collection<T>> C visible(Collection<T> objects, C to) {
        objects.stream().filter(o -> !exclusion.excluded(o)).forEach(to::add);
        return to;
    }

    /** The elements the rules leave in, in the order given. */
    private List<IArchimateElement> visible(Collection<IArchimateElement> elements) {
        return visible(elements, new ArrayList<>());
    }

    /** The elements the view shows that the rules leave in. */
    private Set<IArchimateElement> shown(IDiagramModel view) {
        return visible(elementsOn(view), new LinkedHashSet<>());
    }

    /** The formatting the export uses; outside Archi (in tests, where there is no plugin) the built-in one. */
    private static KeywordRules rules() {
        return FrameworkUtil.getBundle(ModelReader.class) == null ? KeywordRules.defaults() : Prefs.rules();
    }

    /** @param images fills in the picture of a view's page */
    static Doc read(Selection selection, BiConsumer<IDiagramModel, DocPage> images) {
        return read(selection, images, new ArrayList<>());
    }

    /**
     * @param exported gets the model, views, elements and relationships the document shows, for {@link ExportState}
     */
    static Doc read(Selection selection, BiConsumer<IDiagramModel, DocPage> images, Collection<EObject> exported) {
        KeywordRules rules = rules();
        return read(selection, images, exported, rules, selection.exclusion(rules));
    }

    /**
     * @param rules the rules of the export: project file, preferences or defaults
     * @param exclusion told what the rules left out; also hides it from the pictures drawn meanwhile
     */
    static Doc read(Selection selection, BiConsumer<IDiagramModel, DocPage> images, Collection<EObject> exported,
            KeywordRules rules, Exclusion exclusion) {
        Exclusion.current(exclusion);
        List<DocPage> drawn = new ArrayList<>();
        try {
            ModelReader reader = new ModelReader(selection, (view, page) -> {
                drawn.add(page);
                images.accept(view, page);
            }, exported, rules, exclusion);
            return selection.workPackages.isEmpty() ? reader.read() : reader.readWorkPackages();
        } catch (RuntimeException | Error e) {
            // no export follows to delete the pictures drawn into temporary files
            drawn.forEach(p -> {
                if (p.image != null) {
                    p.image.delete();
                }
            });
            throw e;
        } finally {
            Exclusion.current(null);
        }
    }

    private Doc read() {
        IArchimateModel model = selection.model;
        for (IDiagramModel view : model.getDiagramModels()) {
            if (exclusion.excluded(view)) {
                continue;
            }
            for (IArchimateElement element : shown(view)) {
                viewsOf.computeIfAbsent(element.getId(), k -> new ArrayList<>()).add(view);
            }
        }

        List<DocPage> pages = new ArrayList<>();
        views.forEach(view -> viewPages.put(view.getId(), shown(view)));
        List<IDiagramModel> order = new ArrayList<>(views);
        boolean nested = selection.folders && order.stream().anyMatch(v -> !path(v).isEmpty());
        if (nested) {
            order.sort(ModelReader::treeOrder);
        }
        Map<IFolder, DocFolder> folders = new HashMap<>();
        for (IDiagramModel view : order) {
            DocPage page = viewPage(view, null);
            page.folder = nested ? folder(path(view), folders) : null;
            pages.add(page);
        }

        List<IArchimateElement> rest = ordered(elements, null).stream()
                .filter(e -> !described.contains(e.getId())).collect(Collectors.toList());
        // of the whole model, the work packages get a page of their own: the overview of the Jira tickets
        List<IArchimateElement> workPackages = selection.wholeModel
                ? rest.stream().filter(IWorkPackage.class::isInstance).collect(Collectors.toList()) : new ArrayList<>();
        rest.removeAll(workPackages);
        workPackages.removeIf(wp -> WorkPackages.closed((IWorkPackage) wp)); // done: exported only when asked for
        elementsPage(rest, selection.wholeModel ? t("elements-not-in-any-view") : t("selected-elements"), pages);
        if (!workPackages.isEmpty()) {
            workPackages.sort(Comparator.comparing(ModelReader::label, ModelReader::compareNatural));
            DocPage page = new DocPage("work-packages", t("work-packages"));
            workPackages.forEach(e -> page.contents.add(link(e)));
            describe(workPackages, workPackages, page);
            pages.add(page);
        }
        if (selection.wholeModel) {
            labelsPage(pages);
        }

        if (selection.wholeModel || pages.size() > 1) {
            DocPage overview = new DocPage(model.getId(), label(model));
            overview.documentation = model.getPurpose();
            overview.properties = properties(model.getProperties());
            pages.forEach(p -> overview.contents.add(new Link(p.id, p.title)));
            pages.add(0, overview);
            exported.add(model);
        } else {
            pages.forEach(p -> p.folder = null); // one page: nothing to put under folders
        }
        return doc(label(model), pages);
    }

    /** The folders of the view below the model's Views folder, the outermost first. */
    static List<IFolder> path(IDiagramModel view) {
        List<IFolder> path = new ArrayList<>();
        for (EObject f = view.eContainer(); f instanceof IFolder && f.eContainer() instanceof IFolder; f = f.eContainer()) {
            path.add(0, (IFolder) f);
        }
        return path;
    }

    /**
     * The order of the model tree, by name: folders sorted, views in a folder sorted. A folder's own views come
     * before its folders, so that in the overview a folder's heading never seems to hold the views after it.
     */
    static int treeOrder(IDiagramModel a, IDiagramModel b) {
        List<IFolder> p = path(a);
        List<IFolder> q = path(b);
        for (int i = 0;; i++) {
            if (i == p.size() && i == q.size()) {
                return TREE.compare(label(a), label(b));
            }
            if (i == p.size() || i == q.size()) {
                return i == p.size() ? -1 : 1; // the view directly in the folder first
            }
            if (p.get(i) != q.get(i)) {
                int names = TREE.compare(label(p.get(i)), label(q.get(i)));
                return names != 0 ? names : p.get(i).getId().compareTo(q.get(i).getId());
            }
        }
    }

    private static DocFolder folder(List<IFolder> path, Map<IFolder, DocFolder> folders) {
        DocFolder parent = null;
        for (IFolder f : path) {
            DocFolder known = folders.get(f);
            if (known == null) {
                known = new DocFolder(f.getId(), label(f), parent);
                known.documentation = f.getDocumentation();
                folders.put(f, known);
            }
            parent = known;
        }
        return parent;
    }

    /**
     * Only the elements the work packages change, on the first page that shows them. The work package's page comes
     * first, with its description (the specification) and the elements it changes; of several work packages, each
     * is described there.
     */
    private Doc readWorkPackages() {
        IArchimateModel model = selection.model;
        Set<IArchimateElement> changed = elements;
        List<DocPage> pages = new ArrayList<>();
        List<IDiagramModel> showing = new ArrayList<>();
        for (IDiagramModel view : model.getDiagramModels()) {
            Set<IArchimateElement> on = exclusion.excluded(view) ? Set.of() : shown(view);
            if (on.stream().anyMatch(changed::contains)) {
                showing.add(view);
                viewPages.put(view.getId(), on);
            }
        }
        showing.forEach(view -> pages.add(viewPage(view, changed)));
        List<DocPage> views = new ArrayList<>(pages);
        elementsPage(ordered(changed, null).stream().filter(e -> !described.contains(e.getId()))
                .collect(Collectors.toList()), t("elements-not-in-any-view"), pages);

        List<IWorkPackage> workPackages = new ArrayList<>(selection.workPackages);
        DocPage first;
        if (workPackages.size() == 1) {
            IWorkPackage wp = workPackages.get(0);
            first = new DocPage(wp.getId(), label(wp));
            first.documentation = wp.getDocumentation();
            first.properties = properties(wp.getProperties());
            ordered(changed, null).forEach(e -> first.contents.add(link(e)));
            WorkPackages.links(wp).values().forEach(exported::addAll);
            exported.add(wp);
        } else {
            first = new DocPage("work-packages", selection.title());
            workPackages.forEach(wp -> first.contents.add(link(wp)));
            describe(new ArrayList<>(workPackages), List.of(), first);
        }
        views.forEach(p -> first.relatedViews.add(new Link(p.id, p.title)));
        pages.add(0, first);
        return doc(selection.title(), pages);
    }

    /**
     * The page of the labels (#risk, #todo...): per configured label that occurs, the views, elements and
     * relationships whose description, notes or property values carry it, each linking to where it is described.
     */
    private void labelsPage(List<DocPage> pages) {
        Map<String, List<Link>> found = new HashMap<>();
        for (DocPage page : pages) {
            List<String> texts = new ArrayList<>(page.notes);
            texts.add(page.documentation);
            page.texts.forEach(t -> texts.add(t.text));
            page.properties.forEach(p -> texts.add(p.value));
            if (viewPages.containsKey(page.id)) {
                labelled(texts, new Link(page.id, page.title), found);
            }
            for (Entry entry : page.entries) {
                List<String> own = new ArrayList<>(List.of(entry.documentation));
                entry.properties.forEach(p -> own.add(p.value));
                labelled(own, new Link(entry.id, entry.name, entry.type), found);
            }
        }
        for (TreeIterator<EObject> i = selection.model.eAllContents(); i.hasNext();) {
            EObject o = i.next();
            if (o instanceof IArchimateRelationship) {
                IArchimateRelationship r = (IArchimateRelationship) o;
                if (isPrintOrder(r.getSource()) || isPrintOrder(r.getTarget()) || exclusion.excludedWithEnds(r)) {
                    continue;
                }
                List<String> texts = new ArrayList<>(List.of(r.getDocumentation()));
                r.getProperties().forEach(p -> texts.add(p.getValue()));
                // described with the element at one end
                IArchimateConcept at = described.contains(r.getSource().getId()) ? r.getSource() : r.getTarget();
                String ends = label(r.getSource()) + " → " + label(r.getTarget());
                labelled(texts, new Link(at.getId(), ends, typeName(r)), found);
            }
        }
        DocPage page = new DocPage("labels", t("labels"));
        for (String label : rules.labels()) {
            if (found.containsKey(label)) {
                page.labels.put(label, found.get(label));
            }
        }
        if (!page.labels.isEmpty()) {
            pages.add(page);
        }
    }

    private void labelled(List<String> texts, Link link, Map<String, List<Link>> found) {
        Set<String> labels = new LinkedHashSet<>();
        texts.forEach(t -> labels.addAll(RichText.labels(t, rules)));
        labels.forEach(l -> found.computeIfAbsent(l, k -> new ArrayList<>()).add(link));
    }

    /**
     * The Assets folder next to the model file; a model never saved has none. References ([[ref:id]]) to elements
     * and views of the model the export leaves out show their names.
     */
    private Doc doc(String title, List<DocPage> pages) {
        IArchimateModel model = selection.model;
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("The rules leave out everything selected (" + exclusion.count()
                    + " object(s) with " + Exclusion.PROPERTY + " or an excluded label).");
        }
        Doc doc = new Doc(title, pages,
                Assets.in(model.getFile() == null ? null : model.getFile().getAbsoluteFile().toPath().getParent()));
        boolean[] warned = {false};
        model.eAllContents().forEachRemaining(o -> {
            if (o instanceof IDiagramModel || o instanceof IArchimateElement && !(o instanceof IJunction)) {
                doc.names.putIfAbsent(((IArchimateModelObject) o).getId(), label((INameable) o));
            }
            if (o instanceof IArchimateElement && !exclusion.excluded(o)) {
                IArchimateElement element = (IArchimateElement) o;
                String url = jira(element);
                for (IProperty p : element.getProperties()) {
                    String page = p.getValue() == null ? "" : p.getValue().strip();
                    if (url == null && p.getKey() != null && CONFLUENCE_URL.equalsIgnoreCase(p.getKey().strip())
                            && !page.isEmpty()) {
                        url = confluence(page, element);
                        if (url.equals(page) && !page.contains("#") && !warned[0]) {
                            warned[0] = true;
                            doc.notes.add("Some " + CONFLUENCE_URL + " values (such as of \"" + label(element)
                                    + "\") name the page by its id only: links to those elements go to the page, "
                                    + "not to the element on it.");
                        }
                    }
                }
                if (url != null) {
                    doc.external.put(element.getId(), url);
                }
            }
        });
        return doc;
    }

    /** The ticket of a work package named after one, when the rules say where tickets are; null otherwise. */
    private String jira(IArchimateElement element) {
        String base = rules.jira();
        if (base == null || !(element instanceof IWorkPackage) || element.getName() == null
                || !TICKET.matcher(element.getName().strip()).matches()) {
            return null;
        }
        return base + (base.endsWith("/") || base.endsWith("=") ? "" : "/") + element.getName().strip();
    }

    /**
     * Where an element on a Confluence page is: the page's address with the anchor Confluence gives the element's
     * heading, {@code #id-<PageTitle>-<«Type»Name>} (spaces and dashes left out, other than ASCII percent-encoded).
     * An address with an anchor already is used as it is; so is one that names the page only by its id, which has
     * no title for the anchor.
     */
    static String confluence(String url, IArchimateElement element) {
        if (url.contains("#")) {
            return url;
        }
        String path = url.replaceAll("[?].*$", "").replaceAll("/+$", "");
        String last = path.substring(path.lastIndexOf('/') + 1);
        if (last.matches("\\d+") || last.equalsIgnoreCase("viewpage.action") || url.contains("pageId=")) {
            return url;
        }
        String title = last.replace("+", "").replace("%20", "").replace(" ", "").replace("-", "");
        String heading = ("\u00ab" + shortType(element) + "\u00bb" + label(element)).replace(" ", "").replace("-", "");
        return url + "#id-" + percent(title) + "-" + percent(heading);
    }

    /** The type without its layer: Business Process, Application Process and Technology Process are all "Process". */
    static String shortType(EObject object) {
        return object.eClass().getName().replaceFirst("^(Business|Application|Technology)(?=[A-Z])", "");
    }

    /** Characters outside ASCII as their UTF-8 bytes, percent-encoded; the rest as it is. */
    private static String percent(String text) {
        StringBuilder s = new StringBuilder();
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            if (b >= 0) {
                s.append((char) b);
            } else {
                s.append('%').append(String.format("%02X", b & 0xff));
            }
        }
        return s.toString();
    }

    private void elementsPage(List<IArchimateElement> elements, String title, List<DocPage> pages) {
        if (!elements.isEmpty()) {
            DocPage page = new DocPage("elements", title);
            elements.forEach(e -> page.contents.add(link(e)));
            describe(elements, elements, page);
            pages.add(page);
        }
    }

    /** @param only the elements to list and describe, null for all of the view */
    private DocPage viewPage(IDiagramModel view, Set<IArchimateElement> only) {
        exported.add(view);
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
            } else if (o instanceof IDiagramModelArchimateObject
                    && ((IDiagramModelArchimateObject) o).getArchimateElement() instanceof IJunction) {
                exported.add(((IDiagramModelArchimateObject) o).getArchimateElement()); // shown in the picture
            } else if (!(view instanceof IArchimateDiagramModel) && o instanceof IDiagramModelObject) {
                ObjectText text = text((IDiagramModelObject) o);
                if (!text.name.isBlank() || !text.text.isBlank()) {
                    page.texts.add(text);
                }
            }
        }
        List<IArchimateElement> shown = grouped(ordered(shown(view), view), view, page);
        List<IArchimateElement> listed = only == null ? shown
                : shown.stream().filter(only::contains).collect(Collectors.toList());
        List<IArchimateElement> fresh = listed.stream().filter(e -> !described.contains(e.getId()) && describedOn(e, view))
                .collect(Collectors.toList());
        listed.forEach(e -> page.contents.add(link(e)));
        describe(fresh, shown, page);
        return page;
    }

    /**
     * What an object of a sketch or canvas view says: its name, and its text, description and notes. Read by the
     * model's features, so canvas views need no part of Archi's canvas plugin.
     */
    private static ObjectText text(IDiagramModelObject o) {
        List<String> parts = new ArrayList<>();
        if (o instanceof ITextContent) {
            parts.add(((ITextContent) o).getContent());
        }
        if (o instanceof IDocumentable) {
            parts.add(((IDocumentable) o).getDocumentation());
        }
        EStructuralFeature notes = o.eClass().getEStructuralFeature("notes"); // of a canvas sticky
        if (notes != null && o.eGet(notes) instanceof String) {
            parts.add((String) o.eGet(notes));
        }
        return new ObjectText(o.getName(), parts.stream().filter(p -> p != null && !p.isBlank()).map(String::strip)
                .collect(Collectors.joining("\n\n")));
    }

    /** Whether this view's page describes the element: unless its home is another view of the export that shows it. */
    private boolean describedOn(IArchimateElement element, IDiagramModel view) {
        String home = home(element);
        return home == null || home.equals(view.getId())
                || !viewPages.getOrDefault(home, Set.of()).contains(element);
    }

    /** The id of the view the element is to be described on, null for the first that shows it. */
    static String home(IArchimateElement element) {
        for (IProperty p : element.getProperties()) {
            if (HOME.equals(p.getKey()) && p.getValue() != null && !p.getValue().isBlank()) {
                return p.getValue().strip();
            }
        }
        return null;
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
        exported.add(element);
        // relationships the description says otherwise: written into its text, roles, parameters
        Set<IArchimateRelationship> said = new HashSet<>();
        Set<IArchimateElement> within = new HashSet<>(Set.of(element));
        String documentation = tokens(element, element.getDocumentation(), said, within);
        List<String> facts = new ArrayList<>();
        List<Entry.Section> sections = new ArrayList<>();
        roles(element, said, facts);
        parameters(element, said, sections);
        List<Relation> relations = new ArrayList<>();
        // links to PrintOrder are left out, those of work packages are listed apart
        for (IArchimateRelationship r : element.getSourceRelationships()) {
            if (!isPrintOrder(r.getTarget()) && WorkPackages.changed(r) == null && !said.contains(r)
                    && !isParameters(r.getTarget())) {
                relation(r, true, inside, relations);
            }
        }
        for (IArchimateRelationship r : element.getTargetRelationships()) {
            if (!isPrintOrder(r.getSource()) && WorkPackages.changed(r) == null && !said.contains(r)
                    && !isParameters(r.getSource())) {
                relation(r, false, inside, relations);
            }
        }
        List<Link> views = viewsOf.getOrDefault(element.getId(), List.of()).stream().distinct()
                .map(v -> new Link(v.getId(), label(v))).collect(Collectors.toList());
        List<Link> workPackages = new ArrayList<>();
        List<Link> changes = new ArrayList<>();
        if (element instanceof IWorkPackage) {
            Map<IArchimateElement, List<IArchimateRelationship>> links = WorkPackages.links((IWorkPackage) element);
            links.values().forEach(exported::addAll);
            ordered(visible(links.keySet()), null).forEach(e -> changes.add(new Link(e.getId(),
                    label(e) + kind((IWorkPackage) element, e), typeName(e))));
        } else {
            for (IWorkPackage wp : WorkPackages.of(element)) {
                if (exclusion.excluded(wp) || WorkPackages.closed(wp) && !selection.workPackages.contains(wp)) {
                    continue;
                }
                workPackages.add(new Link(wp.getId(), label(wp) + kind(wp, element)));
                exported.addAll(WorkPackages.links(wp).getOrDefault(element, List.of()));
            }
        }
        Entry entry = new Entry(element.getId(), label(element), typeName(element), documentation,
                properties(element.getProperties()), relations, views, workPackages, changes);
        entry.facts.addAll(facts);
        entry.sections.addAll(sections);
        entry.headingUrl = jira(element);
        nesting(element, entry);
        return entry;
    }

    /**
     * The description with each {@code `name`} that names a relationship of the element written as the element at
     * its other end: a reference to it, or, for a part of the same type composed into this one, that part's own
     * description, its tokens resolved too (once per part, so parts that contain each other end). A name no
     * relationship has stays as it is.
     *
     * @param said gets the relationships going out that the text says, which are not listed again
     * @param within the elements whose description is being written, outermost first
     */
    private String tokens(IArchimateElement element, String text, Set<IArchimateRelationship> said,
            Set<IArchimateElement> within) {
        if (text == null || text.indexOf('`') < 0) {
            return text == null ? "" : text;
        }
        Matcher m = TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        int done = 0;
        while (m.find()) {
            String name = (m.group(1) != null ? m.group(1) : m.group(2)).strip();
            IArchimateRelationship r = named(element, name);
            if (r == null) {
                continue;
            }
            IArchimateConcept other = r.getSource() == element ? r.getTarget() : r.getSource();
            out.append(text, done, m.start());
            done = m.end();
            exported.add(r);
            if (r.getSource() == element) {
                said.add(r);
            }
            if (r instanceof ICompositionRelationship && r.getSource() == element
                    && other.eClass() == element.eClass() && !within.contains(other)) {
                IArchimateElement part = (IArchimateElement) other;
                Set<IArchimateElement> deeper = new HashSet<>(within);
                deeper.add(part);
                out.append("**[[ref:").append(part.getId()).append("]]**\n")
                        .append(tokens(part, part.getDocumentation(), new HashSet<>(), deeper).strip());
            } else {
                out.append("[[ref:").append(other.getId()).append("]]");
            }
        }
        return out.append(text.substring(done)).toString();
    }

    /** The element's relationship of this name (ignoring case) to another element, either way round; null if none. */
    private IArchimateRelationship named(IArchimateElement element, String name) {
        List<IArchimateRelationship> all = new ArrayList<>(element.getSourceRelationships());
        all.addAll(element.getTargetRelationships());
        for (IArchimateRelationship r : all) {
            IArchimateConcept other = r.getSource() == element ? r.getTarget() : r.getSource();
            if (r.getName() != null && r.getName().strip().equalsIgnoreCase(name) && other instanceof IArchimateElement
                    && !(other instanceof IJunction) && !isPrintOrder(other) && WorkPackages.changed(r) == null
                    && !exclusion.excludedWithEnds(r)) {
                return r;
            }
        }
        return null;
    }

    /**
     * "Available only for role DEV and higher": from associations to Business Roles named {@code [auth] DEV}, the
     * role with the fewest rights of several. Those associations are not listed among the relationships.
     */
    private void roles(IArchimateElement element, Set<IArchimateRelationship> said, List<String> facts) {
        List<String> roles = new ArrayList<>();
        List<IArchimateRelationship> all = new ArrayList<>(element.getSourceRelationships());
        all.addAll(element.getTargetRelationships());
        for (IArchimateRelationship r : all) {
            IArchimateConcept other = r.getSource() == element ? r.getTarget() : r.getSource();
            Matcher m = other instanceof IBusinessRole ? AUTH.matcher(label(other)) : null;
            if (r instanceof IAssociationRelationship && m != null && m.matches() && !exclusion.excludedWithEnds(r)) {
                said.add(r);
                exported.add(r);
                roles.add(m.group(1));
            }
        }
        if (roles.isEmpty()) {
            return;
        }
        roles.sort(Comparator.comparingInt(role -> {
            int rank = ROLES.indexOf(role.toUpperCase(Locale.ROOT));
            return rank < 0 ? ROLES.size() : rank;
        }));
        String role = roles.get(0);
        boolean higher = ROLES.contains(role.toUpperCase(Locale.ROOT))
                && !role.equalsIgnoreCase(ROLES.get(ROLES.size() - 1));
        facts.add(t(higher ? "role-and-higher" : "role-only").replace("{role}", role));
    }

    /**
     * The descriptions of the {@code [parameters]} elements the element accesses: read, its input parameters,
     * written, its output (for an {@code [api]} element: request and response). They are not listed as relationships.
     */
    private void parameters(IArchimateElement element, Set<IArchimateRelationship> said, List<Entry.Section> sections) {
        boolean api = label(element).strip().toLowerCase(Locale.ROOT).startsWith("[api]");
        List<Entry.Section> input = new ArrayList<>();
        List<Entry.Section> output = new ArrayList<>();
        for (IArchimateRelationship r : element.getSourceRelationships()) {
            if (!(r instanceof IAccessRelationship) || !isParameters(r.getTarget()) || exclusion.excludedWithEnds(r)) {
                continue;
            }
            said.add(r);
            exported.add(r);
            String text = r.getTarget() instanceof IDocumentable ? ((IDocumentable) r.getTarget()).getDocumentation() : "";
            int access = access((IAccessRelationship) r);
            if (access != IAccessRelationship.WRITE_ACCESS) {
                input.add(new Entry.Section(t(api ? "request" : "input-parameters"), text));
            }
            if (access == IAccessRelationship.WRITE_ACCESS || access == IAccessRelationship.READ_WRITE_ACCESS) {
                output.add(new Entry.Section(t(api ? "response" : "output"), text));
            }
        }
        sections.addAll(input);
        sections.addAll(output);
    }

    private static boolean isParameters(IArchimateConcept concept) {
        return concept instanceof IArchimateElement && concept.getName() != null
                && concept.getName().strip().toLowerCase(Locale.ROOT).startsWith("[parameters]");
    }

    /**
     * Whether the access reads, writes or both: its specialization says ({@code select} reads; {@code update},
     * {@code insert}, {@code delete} write), otherwise Archi's own setting. One of the {@link IAccessRelationship}
     * constants.
     */
    private static int access(IAccessRelationship r) {
        String kind = specialization(r);
        if (kind != null) {
            switch (kind) {
                case "select": case "read": return IAccessRelationship.READ_ACCESS;
                case "update": case "insert": case "delete": case "write": case "merge":
                    return IAccessRelationship.WRITE_ACCESS;
                default:
            }
        }
        return r.getAccessType();
    }

    /** The name of the concept's specialization in lower case, null for none. */
    private static String specialization(IArchimateConcept concept) {
        IProfile profile = concept.getPrimaryProfile();
        return profile == null || profile.getName() == null || profile.getName().isBlank() ? null
                : profile.getName().strip().toLowerCase(Locale.ROOT);
    }

    /**
     * The relationship's description, with what an access builds: {@code <sellist>a, b</sellist>} of a {@code select}
     * access becomes {@code SELECT a, b FROM table}, {@code <updList>} of an {@code update} one {@code UPDATE table
     * SET ...}; the query of a {@code [select]} element accessed follows.
     */
    static String documentation(IArchimateRelationship r) {
        String text = r.getDocumentation() == null ? "" : r.getDocumentation();
        if (!(r instanceof IAccessRelationship)) {
            return text;
        }
        String table = label(r.getTarget()).replaceFirst("^\\s*\\[[^\\]]*]\\s*", "").strip();
        String kind = specialization(r);
        if ("select".equals(kind)) {
            text = sql(text, SELLIST, items -> "SELECT " + String.join(",\n       ", items) + "\nFROM " + table);
        } else if ("update".equals(kind)) {
            text = sql(text, UPDLIST, items -> "UPDATE " + table + "\nSET " + String.join(",\n    ", items));
        }
        IArchimateConcept target = r.getTarget();
        String query = target instanceof IDocumentable ? ((IDocumentable) target).getDocumentation() : null;
        if (target.getName() != null && target.getName().strip().toLowerCase(Locale.ROOT).startsWith("[select]")
                && query != null && !query.isBlank()) {
            boolean tagged = query.toLowerCase(Locale.ROOT).contains("<sql>");
            text = (text.isBlank() ? "" : text.stripTrailing() + "\n")
                    + (tagged ? query.strip() : "<sql>" + query.strip() + "</sql>");
        }
        return text;
    }

    /** Each list of the pattern in the text as the SQL built from its items (split at commas and line ends). */
    private static String sql(String text, Pattern list, Function<List<String>, String> build) {
        Matcher m = list.matcher(text);
        StringBuilder out = new StringBuilder();
        int done = 0;
        while (m.find()) {
            List<String> items = new ArrayList<>();
            for (String item : m.group(1).split("[,\\n]")) {
                if (!item.isBlank()) {
                    items.add(item.strip());
                }
            }
            out.append(text, done, m.start()).append("<sql>").append(build.apply(items)).append("</sql>");
            done = m.end();
        }
        return out.append(text.substring(done)).toString();
    }

    /**
     * The elements in the view's groups after the others, a group's together, the groups in their order on the
     * view; print order stays inside each. An element shown more than once counts where it is shown first.
     */
    private List<IArchimateElement> grouped(List<IArchimateElement> elements, IDiagramModel view, DocPage page) {
        Map<IArchimateElement, IDiagramModelGroup> groupOf = new HashMap<>();
        List<IDiagramModelGroup> groups = new ArrayList<>();
        for (TreeIterator<EObject> i = view.eAllContents(); i.hasNext();) {
            EObject o = i.next();
            if (o instanceof IDiagramModelGroup) {
                groups.add((IDiagramModelGroup) o);
            } else if (o instanceof IDiagramModelArchimateObject) {
                IArchimateElement element = ((IDiagramModelArchimateObject) o).getArchimateElement();
                if (!groupOf.containsKey(element)) {
                    groupOf.put(element, enclosing(o, IDiagramModelGroup.class));
                }
            }
        }
        List<IArchimateElement> list = new ArrayList<>(elements);
        list.sort(Comparator.comparingInt(e -> groups.indexOf(groupOf.get(e))));
        for (IArchimateElement e : list) {
            IDiagramModelGroup group = groupOf.get(e);
            if (group != null) {
                page.groups.put(e.getId(), group.getName() == null || group.getName().isBlank() ? t("group")
                        : group.getName().strip());
            }
        }
        return list;
    }

    /** The nearest object of the type that the diagram object is drawn inside, null if none. */
    private static <T> T enclosing(EObject o, Class<T> type) {
        for (EObject p = o.eContainer(); p != null && !(p instanceof IDiagramModel); p = p.eContainer()) {
            if (type.isInstance(p)) {
                return type.cast(p);
            }
        }
        return null;
    }

    /** Elements drawn inside one another on any view, where no relationship says how they belong together. */
    private void nesting(IArchimateElement element, Entry entry) {
        for (IDiagramModelArchimateComponent c : element.getReferencingDiagramComponents()) {
            if (!(c instanceof IDiagramModelArchimateObject)) {
                continue;
            }
            IDiagramModelArchimateObject parent = enclosing(c, IDiagramModelArchimateObject.class);
            if (parent != null) {
                nested(element, parent.getArchimateElement(), entry.partOf);
            }
            for (TreeIterator<EObject> i = c.eAllContents(); i.hasNext();) {
                EObject o = i.next();
                if (o instanceof IDiagramModelArchimateObject && enclosing(o, IDiagramModelArchimateObject.class) == c) {
                    nested(element, ((IDiagramModelArchimateObject) o).getArchimateElement(), entry.contains);
                }
            }
        }
    }

    private void nested(IArchimateElement element, IArchimateElement other, List<Link> to) {
        if (other == element || other instanceof IJunction || isPrintOrder(other) || exclusion.excluded(other)
                || to.stream().anyMatch(l -> l.id.equals(other.getId()))) {
            return;
        }
        for (IArchimateRelationship r : element.getSourceRelationships()) {
            if (r.getTarget() == other) {
                return;
            }
        }
        for (IArchimateRelationship r : element.getTargetRelationships()) {
            if (r.getSource() == other) {
                return;
            }
        }
        to.add(link(other));
    }

    /** Adds the relationship as seen from one end; one through a junction that leads to no element is left out. */
    private void relation(IArchimateRelationship r, boolean outgoing, Set<IArchimateConcept> inside, List<Relation> to) {
        if (exclusion.excludedWithEnds(r)) {
            return;
        }
        IArchimateConcept end = outgoing ? r.getTarget() : r.getSource();
        List<IArchimateConcept> others = new ArrayList<>();
        String junction = null;
        if (end instanceof IJunction) {
            junction = IJunction.OR_JUNCTION_TYPE.equals(((IJunction) end).getType()) ? "or" : "and";
            beyond((IJunction) end, outgoing, others, new HashSet<>());
        } else {
            others.add(end);
        }
        if (others.isEmpty()) {
            return;
        }
        exported.add(r);
        String text = documentation(r);
        // serving and triggering show what the other element is for, triggering only when the relationship doesn't say
        String note = others.size() == 1 && (r instanceof IServingRelationship
                || r instanceof ITriggeringRelationship && text.isBlank()) ? business(others.get(0)) : null;
        to.add(new Relation(outgoing, typeName(r), r.getName(), text,
                others.stream().map(ModelReader::link).collect(Collectors.toList()), junction,
                others.stream().noneMatch(inside::contains), verb(r, outgoing), note));
    }

    /** How the relationship reads from the end it is listed at; null where its type says it best. */
    private String verb(IArchimateRelationship r, boolean outgoing) {
        if (r instanceof ITriggeringRelationship) {
            return t(outgoing ? "triggers" : "triggered-by");
        }
        if (r instanceof IServingRelationship) {
            return t(outgoing ? "used-by" : "uses");
        }
        if (r instanceof ICompositionRelationship) {
            return t(outgoing ? "composed-of" : "part-of-whole");
        }
        if (r instanceof IAccessRelationship) {
            switch (access((IAccessRelationship) r)) {
                case IAccessRelationship.READ_ACCESS: return t(outgoing ? "reads" : "read-by");
                case IAccessRelationship.WRITE_ACCESS: return t(outgoing ? "writes" : "written-by");
                case IAccessRelationship.READ_WRITE_ACCESS: return t(outgoing ? "reads-writes" : "read-written-by");
                default: return null;
            }
        }
        if (r instanceof IAssignmentRelationship) {
            String source = r.getSource().eClass().getName();
            String target = r.getTarget().eClass().getName();
            String verb = null;
            if (source.endsWith("Component")) {
                verb = target.endsWith("Service") ? "provides" : target.endsWith("Interface") ? "publishes"
                        : target.endsWith("Event") ? "generates"
                        : target.endsWith("Process") || target.endsWith("Function") ? "implements" : null;
            } else if (source.endsWith("Interface") && target.endsWith("Service")) {
                verb = "publishes";
            }
            return verb == null ? null : t(outgoing ? verb : PASSIVE.get(verb));
        }
        return null;
    }

    /**
     * What the element is for: its description up to a line {@code Technically:}, without a {@code Description:} in
     * front; null when it has none.
     */
    static String business(IArchimateConcept concept) {
        String text = concept instanceof IDocumentable ? ((IDocumentable) concept).getDocumentation() : null;
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher technically = TECHNICALLY.matcher(text);
        if (technically.find()) {
            text = text.substring(0, technically.start());
        }
        text = text.strip().replaceFirst("(?i)^Description:\\s*", "").strip();
        return text.isEmpty() ? null : text;
    }

    /** The elements a junction leads to, going on in the same direction, through further junctions once each. */
    private void beyond(IJunction junction, boolean outgoing, List<IArchimateConcept> ends, Set<IJunction> seen) {
        if (!seen.add(junction)) {
            return;
        }
        exported.add(junction);
        for (IArchimateRelationship r : outgoing ? junction.getSourceRelationships() : junction.getTargetRelationships()) {
            IArchimateConcept next = outgoing ? r.getTarget() : r.getSource();
            if (exclusion.excludedWithEnds(r)) {
                continue;
            }
            if (next instanceof IJunction) {
                exported.add(r);
                beyond((IJunction) next, outgoing, ends, seen);
            } else if (!isPrintOrder(next) && WorkPackages.changed(r) == null) {
                exported.add(r);
                if (!ends.contains(next)) {
                    ends.add(next);
                }
            }
        }
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
        Set<IArchimateElement> onView = view == null ? null : elementsOn(view);
        Map<IArchimateElement, IArchimateRelationship> positions = new HashMap<>();
        List<IArchimateElement> list = new ArrayList<>();
        for (IArchimateElement element : elements) {
            if (!isPrintOrder(element) && !(element instanceof IJunction)) {
                list.add(element);
                positions.put(element, position(element, onView));
            }
        }
        list.sort(Comparator.<IArchimateElement, IArchimateRelationship>comparing(positions::get,
                Comparator.nullsLast(ModelReader::comparePositions))
                .thenComparing(ModelReader::label, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    /**
     * The element's relationship to a PrintOrder shown on the view whose name has the smallest number, null for none:
     * a PrintOrder of another view says nothing about this one. For a list that belongs to no view ({@code onView}
     * null), any PrintOrder. Names without a number say nothing.
     */
    private static IArchimateRelationship position(IArchimateElement element, Set<IArchimateElement> onView) {
        IArchimateRelationship position = null;
        List<IArchimateRelationship> relationships = new ArrayList<>(element.getSourceRelationships());
        relationships.addAll(element.getTargetRelationships());
        for (IArchimateRelationship r : relationships) {
            IArchimateConcept other = r.getSource() == element ? r.getTarget() : r.getSource();
            if (isPrintOrder(other) && number(r) != null && (onView == null || onView.contains(other))
                    && (position == null || comparePositions(r, position) < 0)) {
                position = r;
            }
        }
        return position;
    }

    /** By the first number in the names, then by the names as {@link #compareNatural} does. */
    private static int comparePositions(IArchimateRelationship a, IArchimateRelationship b) {
        int numbers = number(a).compareTo(number(b));
        return numbers != 0 ? numbers : compareNatural(a.getName().strip(), b.getName().strip());
    }

    /** The first number in the relationship's name, null when it has none. */
    private static BigInteger number(IArchimateRelationship r) {
        Matcher m = r.getName() == null ? null : DIGITS.matcher(r.getName());
        return m != null && m.find() ? new BigInteger(m.group()) : null;
    }

    /** An element named PrintOrder, or of any name with the specialization PrintOrder (ignoring case). */
    static boolean isPrintOrder(IArchimateConcept concept) {
        if (!(concept instanceof IArchimateElement)) {
            return false;
        }
        if (concept.getName() != null && PRINT_ORDER.equalsIgnoreCase(concept.getName().strip())) {
            return true;
        }
        return concept.getProfiles().stream().anyMatch(p -> p.getName() != null
                && PRINT_ORDER.equalsIgnoreCase(p.getName().strip()));
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

    /** The properties to print: not the {@code doc:} ones, which are settings, and not those a rule hides. */
    private List<Property> properties(EList<IProperty> properties) {
        return properties.stream().filter(p -> p.getKey() == null
                        || !p.getKey().strip().toLowerCase(Locale.ROOT).startsWith("doc:") && !rules.hidesProperty(p.getKey()))
                .map(p -> new Property(p.getKey(), p.getValue())).collect(Collectors.toList());
    }

    static String label(INameable object) {
        String name = object.getName();
        return name == null || name.isBlank() ? typeName((EObject) object) : name;
    }

    /** " (new)": the kind of change the work package makes to the element, as their link names it; "" for none. */
    private static String kind(IWorkPackage wp, IArchimateElement element) {
        String kind = WorkPackages.kind(wp, element);
        return kind == null ? "" : " (" + kind + ")";
    }

    /** "BusinessActor" to "Business Actor". */
    private static String typeName(EObject object) {
        return object.eClass().getName().replaceAll("(?<=[a-z])(?=[A-Z])", " ");
    }

    /**
     * The type shown for an element or relationship: with its specialization (an Archi profile, since Archi 4.9),
     * "Business Actor: Broker".
     */
    private static String typeName(INameable object) {
        IProfile profile = object instanceof IArchimateConcept ? ((IArchimateConcept) object).getPrimaryProfile() : null;
        String type = typeName((EObject) object);
        return profile == null || profile.getName() == null || profile.getName().isBlank() ? type
                : type + ": " + profile.getName().strip();
    }
}
