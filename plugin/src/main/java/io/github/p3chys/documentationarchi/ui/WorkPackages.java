package io.github.p3chys.documentationarchi.ui;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CompoundCommand;

import com.archimatetool.editor.model.commands.AddListMemberCommand;
import com.archimatetool.editor.model.commands.EObjectFeatureCommand;
import com.archimatetool.editor.model.commands.RemoveListMemberCommand;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimatePackage;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IAssociationRelationship;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IProperty;
import com.archimatetool.model.IWorkPackage;
import com.archimatetool.model.util.ArchimateModelUtils;

import io.github.p3chys.documentationarchi.doc.Published;

/**
 * Work packages: ArchiMate Work Package elements named after a Jira ticket, such as {@code RBWDP-20025}. An element
 * linked to one by an association is changed within that ticket. Exporting a work package exports only the elements
 * it changes, a specification for the developers.
 * <p>
 * Like {@link PrintOrder}, the links are plain associations a modeller can also draw by hand, and changes come as one
 * command, so Undo takes them back.
 * <p>
 * The name of a link says what kind of change it is ({@link #KINDS}, or whatever the modeller writes). A number at
 * its start is the element's place in the work package's export: {@code 001}, {@code 002 change}. A work
 * package whose ticket is done is closed (the property {@link #CLOSED}): it stays in the model with its links, but
 * the dialogs and the exports of other things no longer list it.
 */
final class WorkPackages {

    private static final IArchimateFactory F = IArchimateFactory.eINSTANCE;
    /** A Jira issue key (project key, dash, number), optionally followed by a summary. */
    private static final Pattern NAME = Pattern.compile("[A-Z][A-Z0-9_]*-[0-9]+(\\s.*)?", Pattern.DOTALL);
    /** The name of a link: the element's place in the export (a number at the start), then the kind of change. */
    private static final Pattern LINK_NAME = Pattern.compile("^\\s*(?<position>\\d+)(?:[\\s.:)-]+|$)(?<kind>.*)$",
            Pattern.DOTALL);

    /** On a work package: its ticket is done. Any value but "false" closes it. */
    static final String CLOSED = "doc:closed";
    /** The kinds of change the dialog offers as the name of a link; any other name is shown as it is. */
    static final List<String> KINDS = List.of("new", "change", "remove");

    private WorkPackages() {
    }

    /** Whether the work package's ticket is done. */
    static boolean closed(IWorkPackage wp) {
        for (IProperty p : wp.getProperties()) {
            if (p.getKey() != null && CLOSED.equalsIgnoreCase(p.getKey().strip())
                    && !"false".equalsIgnoreCase(String.valueOf(p.getValue()).strip())) {
                return true;
            }
        }
        return false;
    }

    /** The work packages of the model that are not closed, by name. */
    static List<IWorkPackage> open(IArchimateModel model) {
        List<IWorkPackage> list = of(model);
        list.removeIf(WorkPackages::closed);
        return list;
    }

    /**
     * What kind of change the work package makes to the element: the name of their link without the number in front;
     * null when it says none.
     */
    static String kind(IWorkPackage wp, IArchimateElement element) {
        for (IArchimateRelationship r : links(wp).getOrDefault(element, List.of())) {
            String kind = kind(r);
            if (kind != null) {
                return kind;
            }
        }
        return null;
    }

    /** The kind of change a link's name says, without the number in front; null for none. */
    private static String kind(IArchimateRelationship r) {
        String name = r.getName() == null ? "" : r.getName().strip();
        Matcher m = LINK_NAME.matcher(name);
        String kind = m.matches() ? m.group("kind").strip() : name;
        return kind.isEmpty() ? null : kind;
    }

    /** The number a link's name starts with, as in {@code 001} or {@code 002 change}; null when it starts with none. */
    static BigInteger position(IArchimateRelationship r) {
        Matcher m = r.getName() == null ? null : LINK_NAME.matcher(r.getName());
        return m != null && m.matches() ? new BigInteger(m.group("position")) : null;
    }

    /** The element's place in the work package's export: the smallest number its links start with; null for none. */
    static BigInteger position(IWorkPackage wp, IArchimateElement element) {
        return position(links(wp).getOrDefault(element, List.of()));
    }

    private static BigInteger position(List<IArchimateRelationship> links) {
        BigInteger position = null;
        for (IArchimateRelationship r : links) {
            BigInteger p = position(r);
            if (p != null && (position == null || p.compareTo(position) < 0)) {
                position = p;
            }
        }
        return position;
    }

    /**
     * The elements in the order the links of the work packages give them: by work package, as listed; within one, by
     * the number its link to the element starts with, then those without a number; the same number and no number by
     * name. An element several of them change goes with the first. Elements none of them changes come last, by name.
     */
    static List<IArchimateElement> ordered(List<IWorkPackage> workPackages, Collection<IArchimateElement> elements) {
        List<Map<IArchimateElement, List<IArchimateRelationship>>> links = workPackages.stream().map(WorkPackages::links)
                .collect(Collectors.toList());
        Map<IArchimateElement, Integer> group = new HashMap<>();
        Map<IArchimateElement, BigInteger> positions = new HashMap<>();
        for (IArchimateElement element : elements) {
            int i = 0;
            while (i < links.size() && !links.get(i).containsKey(element)) {
                i++;
            }
            group.put(element, i);
            positions.put(element, i < links.size() ? position(links.get(i).get(element)) : null);
        }
        List<IArchimateElement> list = new ArrayList<>(elements);
        list.sort(Comparator.<IArchimateElement>comparingInt(group::get)
                .thenComparing(positions::get, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ModelReader::label, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    /**
     * Where a work package gives two or more elements the same number, so the export can only list those by name: a
     * line per number, such as {@code RBWDP-7, 002: Beta, Gamma}. Empty when the numbers are all different.
     *
     * @param exported whether an element is in the export; the others never clash
     */
    static List<String> clashes(Collection<IWorkPackage> workPackages, Predicate<IArchimateElement> exported) {
        List<String> clashes = new ArrayList<>();
        for (IWorkPackage wp : workPackages) {
            Map<BigInteger, List<IArchimateElement>> byNumber = new TreeMap<>();
            Map<BigInteger, String> written = new HashMap<>();
            links(wp).forEach((element, links) -> {
                BigInteger position = position(links);
                if (position != null && exported.test(element)) {
                    byNumber.computeIfAbsent(position, k -> new ArrayList<>()).add(element);
                    links.stream().filter(r -> position.equals(position(r))).findFirst()
                            .ifPresent(r -> written.putIfAbsent(position, r.getName().strip().split("\\D", 2)[0]));
                }
            });
            byNumber.forEach((number, elements) -> {
                if (elements.size() > 1) {
                    elements.sort(Comparator.comparing(ModelReader::label, String.CASE_INSENSITIVE_ORDER));
                    clashes.add(ModelReader.label(wp) + ", " + written.get(number) + ": "
                            + elements.stream().map(ModelReader::label).collect(Collectors.joining(", ")));
                }
            });
        }
        return clashes;
    }

    /** Closes the work packages or opens them again; null when nothing changes. */
    static Command close(Collection<IWorkPackage> workPackages, boolean close) {
        CompoundCommand command = new CompoundCommand(close ? "Close Work Package" : "Reopen Work Package");
        for (IWorkPackage wp : workPackages) {
            if (closed(wp) == close) {
                continue;
            }
            List<IProperty> marks = new ArrayList<>();
            wp.getProperties().forEach(p -> {
                if (p.getKey() != null && CLOSED.equalsIgnoreCase(p.getKey().strip())) {
                    marks.add(p);
                }
            });
            if (!close) {
                marks.forEach(p -> command.add(new RemoveListMemberCommand<>(wp.getProperties(), p)));
            } else if (marks.isEmpty()) {
                IProperty mark = F.createProperty();
                mark.setKey(CLOSED);
                mark.setValue("true");
                command.add(new AddListMemberCommand<>(wp.getProperties(), mark));
            } else { // it said "false"
                marks.forEach(p -> command.add(new EObjectFeatureCommand(command.getLabel(), p,
                        IArchimatePackage.Literals.PROPERTY__VALUE, "true")));
            }
        }
        return command.isEmpty() ? null : command;
    }

    /**
     * The elements that the documentation shows differently from what was published and that no open work package
     * changes yet, with the kind of change that suggests: "new" for an element added since, "change" for one that
     * says something else. Elements that are gone can't be linked; views and work packages are not elements to link.
     *
     * @param changes what changed since the documentation was published, see {@link Published#changesTo}
     */
    static Map<IArchimateElement, String> unassigned(IArchimateModel model, List<Published.Change> changes) {
        Map<IArchimateElement, String> found = new LinkedHashMap<>();
        for (Published.Change change : changes) {
            Object o = change.kind == Published.Kind.REMOVED ? null : ArchimateModelUtils.getObjectByID(model, change.id);
            if (o instanceof IArchimateElement && !(o instanceof IWorkPackage) && !ModelReader.isPrintOrder((IArchimateElement) o)
                    && of((IArchimateElement) o).stream().allMatch(WorkPackages::closed)) {
                found.put((IArchimateElement) o, change.kind == Published.Kind.ADDED ? KINDS.get(0) : KINDS.get(1));
            }
        }
        return found;
    }

    /** Whether the name starts with a Jira issue key, as work package names do. */
    static boolean validName(String name) {
        return NAME.matcher(name.strip()).matches();
    }

    /** The work packages of the model, by name (numbers by value, so RBWDP-9 comes before RBWDP-10). */
    static List<IWorkPackage> of(IArchimateModel model) {
        List<IWorkPackage> list = new ArrayList<>();
        model.eAllContents().forEachRemaining(o -> {
            if (o instanceof IWorkPackage) {
                list.add((IWorkPackage) o);
            }
        });
        list.sort(Comparator.comparing(ModelReader::label, ModelReader::compareNatural));
        return list;
    }

    /** The work packages that change the element, by name. */
    static List<IWorkPackage> of(IArchimateElement element) {
        List<IWorkPackage> list = new ArrayList<>();
        List<IArchimateRelationship> relationships = new ArrayList<>(element.getSourceRelationships());
        relationships.addAll(element.getTargetRelationships());
        for (IArchimateRelationship r : relationships) {
            IWorkPackage wp = workPackage(r);
            if (wp != null && changed(r) == element && !list.contains(wp)) {
                list.add(wp);
            }
        }
        list.sort(Comparator.comparing(ModelReader::label, ModelReader::compareNatural));
        return list;
    }

    /**
     * The element a link of a work package changes; null when the relationship is no such link. A link is an
     * association, either way round, between a work package and an element that is neither a work package nor a
     * PrintOrder.
     */
    static IArchimateElement changed(IArchimateRelationship r) {
        if (!(r instanceof IAssociationRelationship)) {
            return null;
        }
        IArchimateConcept source = r.getSource();
        IArchimateConcept target = r.getTarget();
        IArchimateConcept other = source instanceof IWorkPackage ? target : target instanceof IWorkPackage ? source : null;
        return other instanceof IArchimateElement && !(other instanceof IWorkPackage) && !ModelReader.isPrintOrder(other)
                ? (IArchimateElement) other : null;
    }

    /** The work package of a link, null when the relationship is no link of a work package. */
    static IWorkPackage workPackage(IArchimateRelationship r) {
        if (changed(r) == null) {
            return null;
        }
        return (IWorkPackage) (r.getSource() instanceof IWorkPackage ? r.getSource() : r.getTarget());
    }

    /** The elements the work package changes, by name. */
    static List<IArchimateElement> elements(IWorkPackage wp) {
        List<IArchimateElement> elements = new ArrayList<>(links(wp).keySet());
        elements.sort(Comparator.comparing(ModelReader::label, String.CASE_INSENSITIVE_ORDER));
        return elements;
    }

    /** The links of the work package by the element they change, each element's in model order. */
    static Map<IArchimateElement, List<IArchimateRelationship>> links(IWorkPackage wp) {
        Map<IArchimateElement, List<IArchimateRelationship>> links = new LinkedHashMap<>();
        Set<IArchimateRelationship> connected = new HashSet<>(wp.getSourceRelationships());
        connected.addAll(wp.getTargetRelationships());
        List<IArchimateRelationship> ordered = new ArrayList<>();
        if (wp.getArchimateModel() != null && !connected.isEmpty()) {
            for (TreeIterator<EObject> it = wp.getArchimateModel().eAllContents(); it.hasNext();) {
                EObject o = it.next();
                if (connected.remove(o)) {
                    ordered.add((IArchimateRelationship) o);
                }
            }
        }
        ordered.addAll(connected); // not in the model (yet)
        for (IArchimateRelationship r : ordered) {
            IArchimateElement element = changed(r);
            if (element != null) {
                links.computeIfAbsent(element, k -> new ArrayList<>()).add(r);
            }
        }
        return links;
    }

    /** The link's name for another kind of change: the number it starts with stays in front. */
    static String renamed(IArchimateRelationship link, String kind) {
        Matcher m = LINK_NAME.matcher(link.getName() == null ? "" : link.getName());
        return m.matches() ? m.group("position") + " " + kind : kind;
    }

    /** The work package with this name, ignoring case; null if there is none. */
    static IWorkPackage named(IArchimateModel model, String name) {
        String wanted = name.strip().toLowerCase(Locale.ROOT);
        return of(model).stream().filter(wp -> wp.getName() != null && wp.getName().strip().toLowerCase(Locale.ROOT)
                .equals(wanted)).findFirst().orElse(null);
    }

    /**
     * Links the elements to the work packages in {@code link} and takes them off those in {@code unlink}. A new
     * work package called {@code create} (if not null) is added to the model first and linked as well; when one
     * with that name exists, that one is linked instead.
     *
     * @return null when nothing changes
     */
    static Command change(IArchimateModel model, Collection<IArchimateElement> elements, Collection<IWorkPackage> link,
            Collection<IWorkPackage> unlink, String create) {
        return change(model, elements, link, unlink, create, e -> null);
    }

    /**
     * @param kind the kind of change to each element, which names its links to the work packages in {@code link}:
     *        new links, and those already there when they say something else. Null for an element leaves its links
     *        as they are and new ones without a name.
     */
    static Command change(IArchimateModel model, Collection<IArchimateElement> elements, Collection<IWorkPackage> link,
            Collection<IWorkPackage> unlink, String create, Function<IArchimateElement, String> kind) {
        CompoundCommand command = new CompoundCommand("Link to Work Package");
        List<IWorkPackage> linking = new ArrayList<>(link);
        if (create != null && !create.isBlank()) {
            IWorkPackage existing = named(model, create);
            if (existing != null) {
                if (!linking.contains(existing)) {
                    linking.add(existing);
                }
            } else {
                IWorkPackage wp = F.createWorkPackage();
                wp.setName(create.strip());
                IFolder folder = model.getDefaultFolderForObject(wp);
                command.add(new AddListMemberCommand<>(folder.getElements(), wp));
                linking.add(wp);
            }
        }
        for (IWorkPackage wp : linking) {
            Map<IArchimateElement, List<IArchimateRelationship>> linked = links(wp);
            for (IArchimateElement element : elements) {
                String name = kind.apply(element);
                if (!linked.containsKey(element) && !(element instanceof IWorkPackage) && !ModelReader.isPrintOrder(element)) {
                    IArchimateRelationship relationship = F.createAssociationRelationship();
                    if (name != null) {
                        relationship.setName(name);
                    }
                    command.add(new PrintOrder.CreateLink("Link to Work Package", relationship, element, wp));
                } else if (name != null && !name.equals(kind(wp, element)) && linked.containsKey(element)) {
                    IArchimateRelationship first = linked.get(element).get(0);
                    command.add(new EObjectFeatureCommand("Kind of Change", first,
                            IArchimatePackage.Literals.NAMEABLE__NAME, renamed(first, name)));
                }
            }
        }
        for (IWorkPackage wp : unlink) {
            if (linking.contains(wp)) {
                continue;
            }
            Map<IArchimateElement, List<IArchimateRelationship>> links = links(wp);
            for (IArchimateElement element : elements) {
                links.getOrDefault(element, List.of()).forEach(r -> PrintOrder.delete(command, r, Set.of()));
            }
        }
        return command.isEmpty() ? null : command;
    }
}
