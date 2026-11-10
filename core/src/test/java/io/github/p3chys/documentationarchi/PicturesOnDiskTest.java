package io.github.p3chys.documentationarchi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CancellationException;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Link;
import io.github.p3chys.documentationarchi.doc.Picture;
import io.github.p3chys.documentationarchi.render.Exporter;
import io.github.p3chys.documentationarchi.render.Exporter.Format;
import io.github.p3chys.documentationarchi.text.KeywordRules;

/** Pictures of views kept in temporary files: read where a format writes them, deleted when the export ends. */
class PicturesOnDiskTest {

    @TempDir
    Path folder;

    private byte[] png;
    private Doc doc;
    private DocPage view;

    @BeforeEach
    void doc() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB), "png", out);
        png = out.toByteArray();
        DocPage overview = new DocPage("model", "Model");
        view = new DocPage("v1", "View");
        view.image = Picture.temporary(png);
        view.imageWidth = 300;
        view.imageHeight = 200;
        overview.contents.add(new Link(view.id, view.title));
        List<DocPage> pages = new ArrayList<>(List.of(overview, view));
        doc = new Doc("Model", pages);
    }

    private static Path file(Picture picture) throws Exception {
        Field file = Picture.class.getDeclaredField("file");
        file.setAccessible(true);
        return (Path) file.get(picture);
    }

    @Test
    void everyFormatGetsThePictureAndTheTemporaryFileIsGoneAfterwards() throws Exception {
        Path temporary = file(view.image);
        assertThat(temporary).exists();

        new Exporter(doc, KeywordRules.defaults()).export(folder, EnumSet.allOf(Format.class));

        assertThat(Files.readAllBytes(folder.resolve("html/images/view.png"))).isEqualTo(png);
        assertThat(Files.readAllBytes(folder.resolve("confluence/attachments/view.png"))).isEqualTo(png);
        try (PDDocument pdf = Loader.loadPDF(folder.resolve("model.pdf").toFile())) {
            boolean drawn = false;
            for (int i = 0; i < pdf.getNumberOfPages(); i++) {
                drawn |= pdf.getPage(i).getResources().getXObjectNames().iterator().hasNext();
            }
            assertThat(drawn).isTrue();
        }
        assertThat(temporary).doesNotExist();
        assertThat(view.image).isNotNull(); // what was exported still says it was a view
    }

    @Test
    void cancelledExportDeletesItToo() throws Exception {
        Path temporary = file(view.image);

        assertThatThrownBy(() -> new Exporter(doc, KeywordRules.defaults()).export(folder, EnumSet.of(Format.HTML),
                new Exporter.Progress() {
                    @Override
                    public boolean cancelled() {
                        return true;
                    }
                })).isInstanceOf(CancellationException.class);
        assertThat(temporary).doesNotExist();
    }

    @Test
    void pictureInMemoryStaysForAnotherExport() throws Exception {
        view.image = Picture.of(png);
        Exporter exporter = new Exporter(doc, KeywordRules.defaults());
        exporter.export(folder, EnumSet.of(Format.HTML));
        exporter.export(folder, EnumSet.of(Format.HTML));

        assertThat(view.image.bytes()).isEqualTo(png);
        assertThat(view.image.temporary()).isFalse();
    }
}
