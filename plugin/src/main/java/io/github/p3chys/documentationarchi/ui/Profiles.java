package io.github.p3chys.documentationarchi.ui;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IFolder;

import io.github.p3chys.documentationarchi.render.Exporter.Format;
import io.github.p3chys.documentationarchi.text.ExportProfile;
import io.github.p3chys.documentationarchi.text.KeywordRules;

/**
 * Export profiles ({@code profile} lines of the rules) turned into what an export needs: the selection, the folder, the
 * formats. Every error names the profile and the file and line it is on, like the other rule lines.
 */
final class Profiles {

    private Profiles() {
    }

    /** The profiles the model's rules define; none if the rules cannot be read (the export reports that). */
    static List<ExportProfile> of(IArchimateModel model) {
        try {
            return ProjectRules.resolve(model, null, Prefs::rules).profiles();
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    /**
     * @param rulesFile the rules to read the profile from instead of the project file and the preferences, or null
     * @throws IllegalArgumentException if there is no such profile, or the rules are not valid
     */
    static ExportProfile find(IArchimateModel model, Path rulesFile, String name) {
        KeywordRules rules = ProjectRules.resolve(model, rulesFile, Prefs::rules);
        ExportProfile profile = rules.profile(name);
        if (profile == null) {
            List<ExportProfile> known = rules.profiles();
            throw new IllegalArgumentException("There is no export profile \"" + name + "\" in " + source(model, rulesFile)
                    + (known.isEmpty() ? ". Add a line like: profile " + name + " formats=html output=docs"
                            : ". Known profiles: " + known.stream().map(p -> p.name).collect(Collectors.joining(", "))));
        }
        return profile;
    }

    /** What to export: the whole model unless the profile names folders, views or work packages. */
    static ModelReader.Selection selection(IArchimateModel model, Path rulesFile, ExportProfile profile) {
        try {
            return CommandLineSelection.select(model, profile.workPackages, profile.views, profile.folders);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(where(model, rulesFile, profile) + ": " + e.getMessage(), e);
        }
    }

    /** The export folder the profile asks for; null if it does not. Relative: from the folder of the model file. */
    static Path output(IArchimateModel model, Path rulesFile, ExportProfile profile) {
        if (profile.output == null) {
            return null;
        }
        Path output;
        try {
            output = Path.of(profile.output);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(where(model, rulesFile, profile) + ": output=" + profile.output
                    + " is not a folder path", e);
        }
        if (output.isAbsolute()) {
            return output;
        }
        Path parent = model.getFile() == null ? null : model.getFile().toPath().toAbsolutePath().getParent();
        if (parent == null) {
            throw new IllegalArgumentException(where(model, rulesFile, profile) + ": output=" + profile.output
                    + " is relative to the model file. Save the model first.");
        }
        return parent.resolve(output).normalize();
    }

    /** The formats of the profile; {@code otherwise} when it names none. */
    static Set<Format> formats(ExportProfile profile, Set<Format> otherwise) {
        if (profile.formats.isEmpty()) {
            return otherwise;
        }
        Set<Format> formats = EnumSet.noneOf(Format.class);
        profile.formats.forEach(f -> formats.add(Format.valueOf(f.toUpperCase(Locale.ROOT))));
        return formats;
    }

    /** The ids of the elements in a folder of the model, and in the folders below it. */
    static Set<String> folderElementIds(IArchimateModel model, String path) {
        IFolder folder = CommandLineSelection.folder(model, path);
        if (folder == null) {
            throw new IllegalArgumentException("folder \"" + path + "\" not found in \"" + model.getName() + "\"");
        }
        Set<String> ids = new java.util.LinkedHashSet<>();
        folder.eAllContents().forEachRemaining(o -> {
            if (o instanceof IArchimateElement) {
                ids.add(((IArchimateElement) o).getId());
            }
        });
        return ids;
    }

    /** {@code Profile "name" (file, line 3)}. */
    static String where(IArchimateModel model, Path rulesFile, ExportProfile profile) {
        return "Profile \"" + profile.name + "\" (" + source(model, rulesFile)
                + (profile.line > 0 ? ", line " + profile.line : "") + ")";
    }

    private static String source(IArchimateModel model, Path rulesFile) {
        Path file = rulesFile != null ? rulesFile : ProjectRules.exists(model) ? ProjectRules.file(model) : null;
        return file == null ? "the preferences" : file.toString();
    }
}
