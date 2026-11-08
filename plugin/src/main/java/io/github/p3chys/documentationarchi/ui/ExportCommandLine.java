package io.github.p3chys.documentationarchi.ui;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import com.archimatetool.commandline.AbstractCommandLineProvider;
import com.archimatetool.commandline.CommandLineState;
import com.archimatetool.model.IArchimateModel;

import io.github.p3chys.documentationarchi.render.Exporter.Format;

/** Exports from Archi's command line, for CI and scheduled documentation builds. */
public class ExportCommandLine extends AbstractCommandLineProvider {

    private static final String FOLDER = "documentation.folder";
    private static final String FORMATS = "documentation.formats";

    @Override
    public void run(CommandLine line) throws Exception {
        if (!line.hasOption(FOLDER)) {
            return;
        }
        IArchimateModel model = CommandLineState.getModel();
        if (model == null) {
            logError("No model loaded. Use --loadModel first.");
            return;
        }
        Path folder = Path.of(line.getOptionValue(FOLDER));
        for (String problem : ExportHandler.export(ModelReader.Selection.of(List.of(model)), folder,
                formats(line.getOptionValue(FORMATS)))) {
            logError(problem);
        }
        logMessage("Exported documentation to " + folder);
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
        options.addOption(Option.builder().longOpt(FORMATS).hasArg().argName("list")
                .desc("Comma separated: html, pdf, confluence (default: all)").build());
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
