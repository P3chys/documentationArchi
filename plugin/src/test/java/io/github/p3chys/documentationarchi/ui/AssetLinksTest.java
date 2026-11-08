package io.github.p3chys.documentationarchi.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.gef.commands.Command;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModelNote;

import io.github.p3chys.documentationarchi.doc.Assets;

class AssetLinksTest {

    private static final IArchimateFactory F = IArchimateFactory.eINSTANCE;

    @TempDir
    Path dir;

    @Test
    void fileFromElsewhereIsCopiedIntoAssetsCreatedWhenMissing() throws Exception {
        Path chosen = Files.writeString(Files.createDirectories(dir.resolve("Downloads")).resolve("SLA 2026.pdf"), "sla");
        Path assets = AssetLinks.assetsFolder(dir);

        assertThat(assets).isEqualTo(dir.resolve("Assets"));
        assertThat(AssetLinks.bringIn(assets, chosen)).isEqualTo("SLA 2026.pdf");
        assertThat(dir.resolve("Assets/SLA 2026.pdf")).hasContent("sla");
        assertThat(chosen).exists();
    }

    @Test
    void existingAssetsFolderIsFoundIgnoringCase() throws Exception {
        Files.createDirectories(dir.resolve("assets"));

        assertThat(AssetLinks.assetsFolder(dir).getFileName().toString()).isEqualTo("assets");
    }

    @Test
    void fileAlreadyInAssetsStaysWhereItIs() throws Exception {
        Path assets = Files.createDirectories(dir.resolve("Assets/procesy"));
        Path inside = Files.writeString(assets.resolve("flow.png"), "picture");

        assertThat(AssetLinks.bringIn(dir.resolve("Assets"), inside)).isEqualTo("procesy/flow.png");
        try (var files = Files.list(dir.resolve("Assets"))) {
            assertThat(files).hasSize(1);
        }
    }

    @Test
    void sameFileAgainIsReusedAndADifferentOneWithItsNameGetsANewName() throws Exception {
        Path assets = Files.createDirectories(dir.resolve("Assets"));
        Files.writeString(assets.resolve("logo.png"), "old logo");
        Path same = Files.writeString(Files.createDirectories(dir.resolve("a")).resolve("logo.png"), "old logo");
        Path other = Files.writeString(Files.createDirectories(dir.resolve("b")).resolve("logo.png"), "new logo");

        assertThat(AssetLinks.bringIn(assets, same)).isEqualTo("logo.png");
        assertThat(AssetLinks.bringIn(assets, other)).isEqualTo("logo-2.png");
        assertThat(AssetLinks.bringIn(assets, other)).isEqualTo("logo-2.png");
        assertThat(assets.resolve("logo.png")).hasContent("old logo");
        assertThat(assets.resolve("logo-2.png")).hasContent("new logo");
    }

    @Test
    void referencesGoOnTheirOwnLinesAtTheEnd() {
        assertThat(AssetLinks.appended("", List.of("a.png"))).isEqualTo("[[asset:a.png]]");
        assertThat(AssetLinks.appended(null, List.of("a.png"))).isEqualTo("[[asset:a.png]]");
        assertThat(AssetLinks.appended("About it", List.of("a.png", "docs/b c.pdf")))
                .isEqualTo("About it\n[[asset:a.png]]\n[[asset:docs/b c.pdf]]");
        assertThat(AssetLinks.appended("About it\n", List.of("a.png"))).isEqualTo("About it\n[[asset:a.png]]");
    }

    @Test
    void commandAddsToTheDescriptionAndUndoTakesItBack() {
        IArchimateElement element = F.createBusinessActor();
        element.setDocumentation("Who buys");
        Command command = AssetLinks.add(element, List.of("photo.jpg"));

        command.execute();
        assertThat(element.getDocumentation()).isEqualTo("Who buys\n[[asset:photo.jpg]]");
        command.undo();
        assertThat(element.getDocumentation()).isEqualTo("Who buys");
        assertThat(AssetLinks.add(element, List.of())).isNull();
    }

    @Test
    void notesGetItInTheirTextAndTheModelInItsPurpose() {
        IDiagramModelNote note = F.createDiagramModelNote();
        note.setContent("Remember");
        IArchimateModel model = F.createArchimateModel();

        AssetLinks.add(note, List.of("a.png")).execute();
        AssetLinks.add(model, List.of("b.png")).execute();

        assertThat(note.getContent()).isEqualTo("Remember\n[[asset:a.png]]");
        assertThat(model.getPurpose()).isEqualTo("[[asset:b.png]]");
    }

    @Test
    void addedReferenceIsFoundByTheExport() throws Exception {
        Path chosen = Files.writeString(dir.resolve("diagram.png"), "picture");
        String path = AssetLinks.bringIn(AssetLinks.assetsFolder(dir), chosen);

        assertThat(Assets.in(dir).find(path, "View").file).isEqualTo(dir.resolve("Assets/diagram.png").toRealPath());
    }
}
