package io.github.p3chys.documentationarchi.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CompoundCommand;

import com.archimatetool.editor.model.commands.AddListMemberCommand;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IAssociationRelationship;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IWorkPackage;

/**
 * Work packages: ArchiMate Work Package elements named after a Jira ticket, such as {@code RBWDP-20025}. An element
 * linked to one by an association is changed within that ticket. Exporting a work package exports only the elements
 * it changes, a specification for the developers.
 * <p>
 * Like {@link PrintOrder}, the links are plain associations a modeller can also draw by hand, and changes come as one
 * command, so Undo takes them back.
 */
final class WorkPackages {

    private static final IArchimateFactory F = IArchimateFactory.eINSTANCE;
    /** A Jira issue key (project key, dash, number), optionally followed by a summary. */
    private static final Pattern NAME = Pattern.compile("[A-Z][A-Z0-9_]*-[0-9]+(\\s.*)?", Pattern.DOTALL);

    private WorkPackages() {
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
            Set<IArchimateElement> linked = links(wp).keySet();
            for (IArchimateElement element : elements) {
                if (!linked.contains(element) && !(element instanceof IWorkPackage) && !ModelReader.isPrintOrder(element)) {
                    command.add(new PrintOrder.CreateLink("Link to Work Package", F.createAssociationRelationship(),
                            element, wp));
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
