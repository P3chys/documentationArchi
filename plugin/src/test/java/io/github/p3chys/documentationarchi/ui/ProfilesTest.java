package io.github.p3chys.documentationarchi.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IFolder;

import io.github.p3chys.documentationarchi.render.Exporter.Format;
import io.github.p3chys.documentationarchi.text.ExportProfile;

class ProfilesTest {

    @TempDir
    Path folder;

    private IArchimateModel model;
    private IArchimateDiagramModel orders;

    @BeforeEach
    void model() throws IOException {
        IArchimateFactory f = IArchimateFactory.eINSTANCE;
        model = f.createArchimateModel();
        model.setDefaults();
        model.setName("Bank");
        model.setFile(folder.resolve("m.archimate").toFile());
        IFolder sales = f.createFolder();
        sales.setName("Sales");
        model.getFolder(FolderType.DIAGRAMS).getFolders().add(sales);
        orders = f.createArchimateDiagramModel();
        orders.setName("Orders");
        sales.getElements().add(orders);
        Files.writeString(folder.resolve("documentation.rules"),
                "profile dev folders=Views/Sales formats=pdf output=docs/dev publish\n"
                + "profile whole\nprofile broken views=Nothing folders=Views/Nope\n");
    }

    @Test
    void theModelsProfilesAreListed() {
        assertThat(Profiles.of(model)).extracting(p -> p.name).containsExactly("dev", "whole", "broken");
    }

    @Test
    void aProfileSelectsItsFolderAndNothingMeansTheWholeModel() {
        ExportProfile dev = Profiles.find(model, null, "DEV");
        assertThat(Profiles.selection(model, null, dev).views).containsExactly(orders);
        assertThat(Profiles.selection(model, null, Profiles.find(model, null, "whole")).wholeModel).isTrue();
    }

    @Test
    void outputIsRelativeToTheModelFileUnlessAbsolute() {
        ExportProfile dev = Profiles.find(model, null, "dev");
        assertThat(Profiles.output(model, null, dev)).isEqualTo(folder.resolve("docs/dev"));
        assertThat(Profiles.output(model, null, Profiles.find(model, null, "whole"))).isNull();
        Path abs = folder.resolve("elsewhere").toAbsolutePath();
        assertThat(Profiles.output(model, null, ExportProfile.parse("x output=\"" + abs + "\""))).isEqualTo(abs);
        model.setFile(null);
        assertThatThrownBy(() -> Profiles.output(model, null, dev)).hasMessageContaining("Save the model");
    }

    @Test
    void formatsComeFromTheProfileOrTheFallback() {
        assertThat(Profiles.formats(Profiles.find(model, null, "dev"), Set.of(Format.HTML))).containsExactly(Format.PDF);
        assertThat(Profiles.formats(Profiles.find(model, null, "whole"), Set.of(Format.HTML)))
                .containsExactly(Format.HTML);
    }

    @Test
    void unknownFoldersAndViewsNameTheFileAndLine() {
        assertThatThrownBy(() -> Profiles.selection(model, null, Profiles.find(model, null, "broken")))
                .hasMessageContaining("documentation.rules, line 3").hasMessageContaining("broken")
                .hasMessageContaining("view \"Nothing\"").hasMessageContaining("folder \"Views/Nope\"");
    }

    @Test
    void anUnknownProfileListsTheKnownOnes() {
        assertThatThrownBy(() -> Profiles.find(model, null, "nope"))
                .hasMessageContaining("Known profiles: dev, whole, broken");
    }
}
