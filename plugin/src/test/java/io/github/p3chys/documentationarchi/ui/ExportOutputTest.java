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
    void outputFolderFallsBackToTheEnvironmentVariableThenTheTemporaryFolder() {
        assertThat(ExportOutput.defaultFolder("C:/out/haf", "C:/tmp")).isEqualTo(Path.of("C:/out/haf"));
        assertThat(ExportOutput.defaultFolder(null, "C:/tmp")).isEqualTo(Path.of("C:/tmp"));
        assertThat(ExportOutput.defaultFolder("  ", "C:/tmp")).isEqualTo(Path.of("C:/tmp"));
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
