package io.github.p3chys.documentationarchi.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
     * views (by name or id) and folders (paths like {@code Views/Sales}).
     *
     * @throws IllegalArgumentException listing every name that matches nothing, or matches several views
     */
    static ModelReader.Selection select(IArchimateModel model, List<String> workPackages, List<String> views,
            List<String> folders) {
        if (workPackages.isEmpty() && views.isEmpty() && folders.isEmpty()) {
            return ModelReader.Selection.of(List.of(model));
        }
        if (!workPackages.isEmpty() && !(views.isEmpty() && folders.isEmpty())) {
            throw new IllegalArgumentException("Work packages cannot be combined with views or folders: export them in "
                    + "separate runs.");
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
