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

import com.archimatetool.commandline.AbstractCommandLineProvider;
import com.archimatetool.commandline.CommandLineState;
import com.archimatetool.model.IArchimateModel;

import io.github.p3chys.documentationarchi.render.Exporter.Format;
import io.github.p3chys.documentationarchi.text.ExportProfile;

/**
 * Exports from Archi's command line, for CI and scheduled documentation builds. Archi's providers signal failure by
 * throwing from {@link #run}; with Archi's {@code --abortOnException} that ends the run with a non-zero exit.
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

    @Override
    public void run(CommandLine line) throws Exception {
        if (line.hasOption(CHECK)) {
            check(line);
        }
        if (!line.hasOption(FOLDER) && !line.hasOption(PROFILE)) {
            return;
        }
        IArchimateModel model = CommandLineState.getModel();
        if (model == null) {
            throw failure("No model loaded. Use --loadModel first.");
        }
        Path rules = line.hasOption(RULES) ? Path.of(line.getOptionValue(RULES)) : null;
        ExportHandler.Result result;
        Path folder;
        try {
            ExportProfile profile = line.hasOption(PROFILE) ? Profiles.find(model, rules, line.getOptionValue(PROFILE))
                    : null;
            folder = line.hasOption(FOLDER) ? Path.of(line.getOptionValue(FOLDER))
                    : Profiles.output(model, rules, profile);
            if (folder == null) {
                throw new IllegalArgumentException(Profiles.where(model, rules, profile) + " has no output=<folder>. "
                        + "Add it, or give --" + FOLDER + ".");
            }
            // what is given on the command line overrides the profile
            ModelReader.Selection selection = line.hasOption(WORK_PACKAGES) || line.hasOption(VIEWS)
                    || line.hasOption(FOLDERS) || profile == null
                            ? CommandLineSelection.select(model, CommandLineSelection.names(line.getOptionValue(WORK_PACKAGES)),
                                    CommandLineSelection.names(line.getOptionValue(VIEWS)),
                                    CommandLineSelection.names(line.getOptionValue(FOLDERS)))
                            : Profiles.selection(model, rules, profile);
            Set<Format> formats = line.hasOption(FORMATS) ? formats(line.getOptionValue(FORMATS))
                    : profile != null ? Profiles.formats(profile, EnumSet.allOf(Format.class))
                            : EnumSet.allOf(Format.class);
            result = ExportHandler.exportAll(selection, folder, formats, rules, new NullProgressMonitor(),
                    line.hasOption(PUBLISH) || profile != null && profile.publish, profile);
        } catch (IllegalArgumentException e) { // unknown names, unusable rules, nothing to export
            throw failure(e.getMessage());
        }
        List<String> problems = result.problems;
        problems.forEach(this::logError);
        logMessage("Exported documentation to " + folder
                + (result.excluded > 0 ? ", " + result.excluded + " object(s) left out by the exclusion rules" : ""));
        if (line.hasOption(STRICT) && !problems.isEmpty()) {
            throw new IllegalStateException(problems.size() + " asset reference(s) have no file, see the errors above");
        }
    }

    /** Lists what is missing or wrong in the documentation; with --documentation.strict, findings fail the run. */
    private void check(CommandLine line) {
        IArchimateModel model = CommandLineState.getModel();
        if (model == null) {
            throw failure("No model loaded. Use --loadModel first.");
        }
        List<DocCheck.Finding> findings;
        try {
            findings = DocCheck.check(model, ProjectRules.resolve(model,
                    line.hasOption(RULES) ? Path.of(line.getOptionValue(RULES)) : null, Prefs::rules));
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
        return new IllegalArgumentException(message);
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
                .desc("Export the model's documentation to this folder").build());
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
        options.addOption(Option.builder().longOpt(CHECK)
                .desc("List what is missing or wrong in the documentation, without exporting; with "
                        + "--documentation.strict, findings fail the run")
                .build());
        options.addOption(Option.builder().longOpt(RULES).hasArg().argName("file")
                .desc("Formatting rules to use instead of documentation.rules next to the model").build());
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
