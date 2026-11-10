package io.github.p3chys.documentationarchi.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IWorkPackage;

/** What the command line asks to export, as names, turned into the {@link ModelReader.Selection} the dialog also uses. */
final class CommandLineSelection {

    private CommandLineSelection() {
    }

    /** The names of a comma separated option value; empty for none. */
    static List<String> names(String list) {
        return list == null ? List.of() : Arrays.stream(list.split(",")).map(String::strip).filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Nothing named: the whole model. Else work packages (by name or by the ticket key their name starts with), or
     * views (by name or id), folders (paths like {@code Views/Sales}) and elements (by name or id; they go on the
     * "Selected elements" page, as when selected in Archi).
     *
     * @throws IllegalArgumentException listing every name that matches nothing, or matches several views or elements
     */
    static ModelReader.Selection select(IArchimateModel model, List<String> workPackages, List<String> views,
            List<String> folders) {
        return select(model, workPackages, views, folders, List.of());
    }

    static ModelReader.Selection select(IArchimateModel model, List<String> workPackages, List<String> views,
            List<String> folders, List<String> elements) {
        if (workPackages.isEmpty() && views.isEmpty() && folders.isEmpty() && elements.isEmpty()) {
            return ModelReader.Selection.of(List.of(model));
        }
        if (!workPackages.isEmpty() && !(views.isEmpty() && folders.isEmpty() && elements.isEmpty())) {
            throw new IllegalArgumentException("Work packages cannot be combined with views, folders or elements: "
                    + "export them in separate runs.");
        }
        List<String> problems = new ArrayList<>();
        Set<Object> chosen = new LinkedHashSet<>();
        for (String name : workPackages) {
            List<IWorkPackage> found = WorkPackages.of(model).stream()
                    .filter(wp -> matches(wp.getName(), name)).collect(Collectors.toList());
            IWorkPackage exact = found.stream().filter(wp -> name.equals(wp.getName())).findFirst().orElse(null);
            if (exact != null) {
                found = List.of(exact); // a full name is exact, though a ticket key may start several names
            }
            if (found.isEmpty()) {
                problems.add("work package \"" + name + "\"");
            } else if (found.size() > 1) {
                problems.add("work package \"" + name + "\" is ambiguous, it matches "
                        + found.stream().map(ModelReader::label).collect(Collectors.joining(", ")));
            } else {
                chosen.add(found.get(0));
            }
        }
        for (String name : views) {
            List<IDiagramModel> found = model.getDiagramModels().stream()
                    .filter(v -> name.equals(v.getId()) || name.equals(v.getName())).collect(Collectors.toList());
            if (found.isEmpty()) {
                problems.add("view \"" + name + "\"");
            } else if (found.size() > 1) {
                problems.add("view \"" + name + "\" is ambiguous, use one of the ids "
                        + found.stream().map(IDiagramModel::getId).collect(Collectors.joining(", ")));
            } else {
                chosen.add(found.get(0));
            }
        }
        for (String name : elements) {
            List<IArchimateElement> found = new ArrayList<>();
            model.eAllContents().forEachRemaining(o -> {
                if (o instanceof IArchimateElement
                        && (name.equals(((IArchimateElement) o).getId()) || name.equals(((IArchimateElement) o).getName()))) {
                    found.add((IArchimateElement) o);
                }
            });
            if (found.isEmpty()) {
                problems.add("element \"" + name + "\"");
            } else if (found.size() > 1) {
                problems.add("element \"" + name + "\" is ambiguous, use one of the ids "
                        + found.stream().map(IArchimateElement::getId).collect(Collectors.joining(", ")));
            } else {
                chosen.add(found.get(0));
            }
        }
        for (String path : folders) {
            IFolder folder = folder(model, path);
            if (folder == null) {
                problems.add("folder \"" + path + "\"");
            } else {
                chosen.add(folder);
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Not found in \"" + model.getName() + "\": "
                    + String.join("; ", problems));
        }
        return ModelReader.Selection.of(new ArrayList<>(chosen));
    }

    /** The full name, or the ticket key the name starts with ("RBWDP-1 Limits" for RBWDP-1, ignoring case). */
    private static boolean matches(String workPackage, String wanted) {
        if (workPackage == null || workPackage.isBlank()) {
            return false;
        }
        String key = workPackage.strip().split("\\s+", 2)[0];
        return workPackage.equals(wanted) || key.equalsIgnoreCase(wanted);
    }

    static IFolder folder(IArchimateModel model, String path) {
        List<IFolder> level = model.getFolders();
        IFolder folder = null;
        for (String segment : path.split("/")) {
            String wanted = segment.strip();
            if (wanted.isEmpty()) {
                continue;
            }
            folder = level.stream().filter(f -> wanted.equals(f.getName())).findFirst().orElse(null);
            if (folder == null) {
                return null;
            }
            level = folder.getFolders();
        }
        return folder;
    }
}
