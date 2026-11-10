package io.github.p3chys.documentationarchi.ui;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.commandline.AbstractCommandLineProvider;
import com.archimatetool.commandline.CommandLineState;
import com.archimatetool.model.IArchimateModel;

import io.github.p3chys.documentationarchi.render.Exporter.Format;
import io.github.p3chys.documentationarchi.text.ExportProfile;
import io.github.p3chys.documentationarchi.text.KeywordRules;

/**
 * Exports from Archi's command line, for CI and scheduled documentation builds. A failure prints its message and ends
 * the run with exit code 1, with no error dialog (see {@link #fail}), whatever options Archi was given.
 */
public class ExportCommandLine extends AbstractCommandLineProvider {

    private static final String FOLDER = "documentation.folder";
    private static final String FORMATS = "documentation.formats";
    private static final String WORK_PACKAGES = "documentation.workPackages";
    private static final String VIEWS = "documentation.views";
    private static final String FOLDERS = "documentation.folders";
    private static final String CHECK = "documentation.check";
    private static final String RULES = "documentation.rules";
    private static final String STRICT = "documentation.strict";
    private static final String PUBLISH = "documentation.publish";
    private static final String PROFILE = "documentation.profile";
    private static final String ASSETS = "documentation.assets";
    private static final String ELEMENTS = "documentation.elements";
    private static final String SINGLE_FILE = "documentation.single-file";

    /** The environment variable that names the output folder when neither the options nor a profile do (HaF's). */
    static final String OUT_DIR_VARIABLE = "HAF_OUT_DIR";

    /**
     * A failure that is already printed. Archi's own handling of a provider's exception ends the run with exit code 0
     * (or, with --abortOnException, makes the launcher open an error dialog, which on macOS waits for a click), so
     * {@link #run} prints the message and ends the run itself, see {@link #fail}.
     */
    private static final class Reported extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        Reported(String message) {
            super(message);
        }
    }

    @Override
    public void run(CommandLine line) throws Exception {
        try {
            export(line);
        } catch (Exception e) {
            if (!(e instanceof Reported)) {
                logError(e.getMessage() != null ? e.getMessage() : e.toString());
            }
            fail(e);
        }
    }

    /**
     * Ends a failed run: with a non-zero exit code, and without any dialog. Only when Archi runs the command line
     * (no workbench): an export from the user interface never ends the program.
     */
    private static void fail(Exception e) throws Exception {
        if (!PlatformUI.isWorkbenchRunning()) {
            System.err.flush();
            System.out.flush();
            System.exit(1);
        }
        throw e;
    }

    private void export(CommandLine line) throws Exception {
        if (line.hasOption(CHECK)) {
            check(line);
        }
        if (!exports(line)) {
            return;
        }
        IArchimateModel model = CommandLineState.getModel();
        if (model == null) {
            throw failure("No model loaded. Use --loadModel first.");
        }
        Path rules = line.hasOption(RULES) ? Path.of(line.getOptionValue(RULES)) : null;
        ExportHandler.Result result;
        Path folder;
        Set<Format> formats;
        try {
            ExportProfile profile = line.hasOption(PROFILE) ? Profiles.find(model, rules, line.getOptionValue(PROFILE))
                    : null;
            folder = line.hasOption(FOLDER) ? Path.of(line.getOptionValue(FOLDER))
                    : profile != null ? Profiles.output(model, rules, profile) : null;
            if (folder == null) {
                folder = ExportOutput.defaultFolder(System.getenv(OUT_DIR_VARIABLE), System.getProperty("java.io.tmpdir"));
                logMessage("No --" + FOLDER + " and no output= in a profile, exporting to " + folder
                        + (System.getenv(OUT_DIR_VARIABLE) != null && !System.getenv(OUT_DIR_VARIABLE).isBlank()
                                ? " (" + OUT_DIR_VARIABLE + ")" : " (the temporary folder)"));
            }
            // what is given on the command line overrides the profile
            ModelReader.Selection selection = line.hasOption(WORK_PACKAGES) || line.hasOption(VIEWS)
                    || line.hasOption(FOLDERS) || line.hasOption(ELEMENTS) || profile == null
                            ? CommandLineSelection.select(model, CommandLineSelection.names(line.getOptionValue(WORK_PACKAGES)),
                                    CommandLineSelection.names(line.getOptionValue(VIEWS)),
                                    CommandLineSelection.names(line.getOptionValue(FOLDERS)),
                                    CommandLineSelection.names(line.getOptionValue(ELEMENTS)))
                            : Profiles.selection(model, rules, profile);
            formats = line.hasOption(FORMATS) ? formats(line.getOptionValue(FORMATS))
                    : profile != null ? Profiles.formats(profile, EnumSet.allOf(Format.class))
                            : EnumSet.allOf(Format.class);
            result = ExportHandler.exportAll(selection, folder, formats, rules, new NullProgressMonitor(),
                    line.hasOption(PUBLISH) || profile != null && profile.publish, profile, line.getOptionValue(ASSETS),
                    line.hasOption(SINGLE_FILE));
        } catch (IllegalArgumentException e) { // unknown names, unusable rules, nothing to export
            throw failure(e.getMessage());
        }
        String missing = ExportOutput.missing(folder, formats);
        if (missing != null) {
            throw failure("The export wrote no output: " + missing + " Nothing was exported to " + folder + ".");
        }
        List<String> problems = result.problems;
        problems.forEach(this::logError);
        result.notes.forEach(this::logMessage);
        logMessage("Exported documentation to " + folder
                + (result.excluded > 0 ? ", " + result.excluded + " object(s) left out by the exclusion rules" : ""));
        if (line.hasOption(STRICT) && !problems.isEmpty()) {
            throw new IllegalStateException(problems.size() + " asset reference(s) have no file, see the errors above");
        }
    }

    /** Whether the options ask for an export (and not only for the check). */
    private static boolean exports(CommandLine line) {
        for (String option : List.of(FOLDER, PROFILE, FORMATS, WORK_PACKAGES, VIEWS, FOLDERS, ELEMENTS, PUBLISH,
                SINGLE_FILE)) {
            if (line.hasOption(option)) {
                return true;
            }
        }
        return false;
    }


    /** Lists what is missing or wrong in the documentation; with --documentation.strict, findings fail the run. */
    private void check(CommandLine line) {
        IArchimateModel model = CommandLineState.getModel();
        if (model == null) {
            throw failure("No model loaded. Use --loadModel first.");
        }
        List<DocCheck.Finding> findings;
        try {
            KeywordRules rules = ProjectRules.resolve(model,
                    line.hasOption(RULES) ? Path.of(line.getOptionValue(RULES)) : null, Prefs::rules);
            findings = DocCheck.check(model, line.hasOption(ASSETS) ? rules.withAssets(line.getOptionValue(ASSETS)) : rules);
        } catch (IllegalArgumentException e) { // unusable rules
            throw failure(e.getMessage());
        }
        logMessage(findings.isEmpty() ? "Check documentation: no findings" : "Check documentation: " + findings.size()
                + " finding(s)\n" + DocCheck.format(findings));
        if (line.hasOption(STRICT) && !findings.isEmpty()) {
            throw failure(findings.size() + " documentation finding(s), see the list above");
        }
    }

    private IllegalArgumentException failure(String message) {
        logError(message);
        return new Reported(message);
    }

    private static Set<Format> formats(String list) {
        if (list == null) {
            return EnumSet.allOf(Format.class);
        }
        Set<Format> formats = EnumSet.noneOf(Format.class);
        for (String name : list.split(",")) {
            formats.add(Format.valueOf(name.strip().toUpperCase(Locale.ROOT)));
        }
        return formats;
    }

    @Override
    public Options getOptions() {
        Options options = new Options();
        options.addOption(Option.builder().longOpt(FOLDER).hasArg().argName("path")
                .desc("Export the model's documentation to this folder (default: output= of the profile, else the "
                        + "environment variable " + OUT_DIR_VARIABLE + ", else the temporary folder)")
                .build());
        options.addOption(Option.builder().longOpt(PROFILE).hasArg().argName("name")
                .desc("Export as this profile of the model's rules says (profile lines); " + "--" + FOLDER
                        + " is then optional, and the options given here override the profile")
                .build());
        options.addOption(Option.builder().longOpt(FORMATS).hasArg().argName("list")
                .desc("Comma separated: html, pdf, confluence (default: all)").build());
        options.addOption(Option.builder().longOpt(WORK_PACKAGES).hasArg().argName("list")
                .desc("Export only these work packages: names or ticket keys, comma separated").build());
        options.addOption(Option.builder().longOpt(VIEWS).hasArg().argName("list")
                .desc("Export only these views: names or ids, comma separated").build());
        options.addOption(Option.builder().longOpt(FOLDERS).hasArg().argName("list")
                .desc("Export only these folders: paths like Views/Sales, comma separated").build());
        options.addOption(Option.builder().longOpt(ELEMENTS).hasArg().argName("list")
                .desc("Export only these elements (on the Selected elements page): names or ids, comma separated; "
                        + "combinable with views and folders")
                .build());
        options.addOption(Option.builder().longOpt(SINGLE_FILE)
                .desc("The HTML format writes one self-contained file per page: style and pictures inside, no "
                        + "sidebar or search")
                .build());
        options.addOption(Option.builder().longOpt(CHECK)
                .desc("List what is missing or wrong in the documentation, without exporting; with "
                        + "--documentation.strict, findings fail the run")
                .build());
        options.addOption(Option.builder().longOpt(RULES).hasArg().argName("file")
                .desc("Formatting rules to use instead of documentation.rules next to the model").build());
        options.addOption(Option.builder().longOpt(ASSETS).hasArg().argName("folder")
                .desc("Read assets from this folder (relative to the model's folder, or absolute) instead of the "
                        + "assets rule or the Assets folder next to the model")
                .build());
        options.addOption(Option.builder().longOpt(PUBLISH)
                .desc("Note this export as the published documentation, in documentation.published next to the model")
                .build());
        options.addOption(Option.builder().longOpt(STRICT)
                .desc("Fail on missing assets too (unknown names always fail); with --abortOnException: non-zero exit")
                .build());
        return options;
    }

    @Override
    public int getPriority() {
        return PRIORITY_REPORT_OR_EXPORT;
    }

    @Override
    protected String getLogPrefix() {
        return "[Documentation Export]";
    }
}
