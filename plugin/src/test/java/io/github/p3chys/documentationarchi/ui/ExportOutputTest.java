package io.github.p3chys.documentationarchi.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.p3chys.documentationarchi.render.Exporter.Format;

class ExportOutputTest {

    @TempDir
    Path folder;

    @Test
    void outputFolderFallsBackToTheEnvironmentVariableThenANewTemporaryFolder() throws Exception {
        assertThat(ExportOutput.defaultFolder("C:/out/haf", folder.toString())).isEqualTo(Path.of("C:/out/haf"));
        Path fresh = ExportOutput.defaultFolder(null, folder.toString());
        assertThat(fresh.getParent()).isEqualTo(folder);
        assertThat(fresh.getFileName().toString()).startsWith("documentation-");
        assertThat(Files.isDirectory(fresh)).isTrue();
        assertThat(ExportOutput.defaultFolder("  ", folder.toString())).isNotEqualTo(fresh).startsWith(folder);
    }

    @Test
    void anEmptyOutputFolderIsMissingWhatTheFormatsPromise() throws Exception {
        assertThat(ExportOutput.missing(folder, EnumSet.allOf(Format.class)))
                .contains("html/index.html", "the PDF", "confluence/manifest.json");
        assertThat(ExportOutput.missing(folder.resolve("none"), EnumSet.of(Format.PDF))).contains("the PDF");
    }

    @Test
    void anExportThatWroteEverythingHasNothingMissing() throws Exception {
        Files.createDirectories(folder.resolve("html"));
        Files.writeString(folder.resolve("html/index.html"), "<html/>");
        Files.writeString(folder.resolve("Model.pdf"), "%PDF");
        Files.createDirectories(folder.resolve("confluence"));
        Files.writeString(folder.resolve("confluence/manifest.json"), "{}");

        assertThat(ExportOutput.missing(folder, EnumSet.allOf(Format.class))).isNull();
        assertThat(ExportOutput.missing(folder, EnumSet.of(Format.HTML))).isNull();
    }
}
