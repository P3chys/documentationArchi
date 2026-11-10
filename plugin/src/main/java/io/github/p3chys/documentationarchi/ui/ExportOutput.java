package io.github.p3chys.documentationarchi.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import io.github.p3chys.documentationarchi.render.Exporter.Format;

/** Where the command line exports to when nothing says, and whether an export left what it promised. */
final class ExportOutput {

    private ExportOutput() {
    }

    /**
     * The folder when no option or profile names one: the environment variable's value, else a new folder of its own
     * in the temporary folder, never the shared temporary folder itself (another user could have put links there).
     */
    static Path defaultFolder(String variable, String temporary) throws IOException {
        if (variable != null && !variable.isBlank()) {
            return Path.of(variable.strip());
        }
        return Files.createTempDirectory(Path.of(temporary), "documentation-");
    }

    /**
     * What an export of these formats must have written, checked in the folder.
     *
     * @return what is missing, null if everything is there
     */
    static String missing(Path folder, Set<Format> formats) {
        List<String> missing = new ArrayList<>();
        if (formats.contains(Format.HTML) && !Files.isRegularFile(folder.resolve("html").resolve("index.html"))) {
            missing.add("html/index.html");
        }
        if (formats.contains(Format.PDF) && !hasPdf(folder)) {
            missing.add("the PDF");
        }
        if (formats.contains(Format.CONFLUENCE)
                && !Files.isRegularFile(folder.resolve("confluence").resolve("manifest.json"))) {
            missing.add("confluence/manifest.json");
        }
        return missing.isEmpty() ? null : "no " + String.join(", ", missing) + " in the output folder.";
    }

    private static boolean hasPdf(Path folder) {
        if (!Files.isDirectory(folder)) {
            return false;
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.anyMatch(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"));
        } catch (IOException e) {
            return false;
        }
    }
}
