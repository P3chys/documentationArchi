package io.github.p3chys.documentationarchi.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;

import io.github.p3chys.documentationarchi.text.KeywordRules;

class ProjectRulesTest {

    @TempDir
    Path folder;

    private IArchimateModel model;
    private final KeywordRules preferences = KeywordRules.parse("bold Prefs");

    @BeforeEach
    void model() {
        model = IArchimateFactory.eINSTANCE.createArchimateModel();
        model.setFile(folder.resolve("m.archimate").toFile());
    }

    @Test
    void withoutAFileThePreferencesApply() {
        assertThat(ProjectRules.exists(model)).isFalse();
        assertThat(ProjectRules.resolve(model, null, () -> preferences)).isSameAs(preferences);
    }

    @Test
    void aModelThatWasNeverSavedHasNoProjectFile() {
        model.setFile(null);
        assertThat(ProjectRules.file(model)).isNull();
        assertThat(ProjectRules.resolve(model, null, () -> preferences)).isSameAs(preferences);
    }

    @Test
    void theProjectFileReplacesThePreferences() throws IOException {
        Files.writeString(folder.resolve("documentation.rules"), "# mine\nbold Project\n");
        KeywordRules rules = ProjectRules.resolve(model, null, () -> preferences);
        assertThat(rules.rules()).extracting(r -> r.keyword).contains("Project").doesNotContain("Prefs");
    }

    @Test
    void aFileAddsToTheBuiltInRules() throws IOException {
        Files.writeString(folder.resolve("documentation.rules"), "tag blue risk\ntag grey security\n");
        KeywordRules rules = ProjectRules.resolve(model, null, () -> preferences);
        assertThat(rules.rules()).contains(KeywordRules.Rule.panel("INFO", KeywordRules.PanelStyle.INFO));
        assertThat(rules.tag("todo")).isEqualTo(KeywordRules.TagColour.YELLOW);
        assertThat(rules.tag("risk")).as("the file's line replaces the built-in one")
                .isEqualTo(KeywordRules.TagColour.BLUE);
        assertThat(rules.tag("security")).isEqualTo(KeywordRules.TagColour.GREY);
    }

    @Test
    void defaultsOffLeavesOutTheBuiltInRules() throws IOException {
        Files.writeString(folder.resolve("documentation.rules"), "defaults off\nbold Project\n");
        KeywordRules rules = ProjectRules.resolve(model, null, () -> preferences);
        assertThat(rules.rules()).extracting(r -> r.kind)
                .doesNotContain(KeywordRules.Kind.PANEL, KeywordRules.Kind.TAG);
        assertThat(rules.labels()).isEmpty();
    }

    @Test
    void savedRulesWithoutABuiltInOneKeepItLeftOut() throws IOException {
        KeywordRules rules = KeywordRules.parse(KeywordRules.DEFAULTS.replace("tag yellow draft\n", ""));
        Path file = ProjectRules.save(model, rules);
        assertThat(Files.readString(file)).contains("\ndefaults off\n");
        assertThat(ProjectRules.read(file).tag("draft")).isNull();
        assertThat(ProjectRules.read(file).tag("todo")).isEqualTo(KeywordRules.TagColour.YELLOW);
    }

    @Test
    void savedRulesWithAllBuiltInOnesNeedNoDefaultsLine() throws IOException {
        Path file = ProjectRules.save(model, KeywordRules.parse(KeywordRules.DEFAULTS + "tag blue security\n"));
        assertThat(Files.readString(file)).doesNotContain("\ndefaults ");
        assertThat(ProjectRules.read(file).tag("security")).isEqualTo(KeywordRules.TagColour.BLUE);
    }

    @Test
    void anOverrideBeatsTheProjectFile() throws IOException {
        Files.writeString(folder.resolve("documentation.rules"), "bold Project\n");
        Path other = folder.resolve("other.rules");
        Files.writeString(other, "bold Other\n");
        assertThat(ProjectRules.resolve(model, other, () -> preferences).rules()).extracting(r -> r.keyword)
                .contains("Other").doesNotContain("Project", "Prefs");
    }

    @Test
    void anInvalidFileFailsWithFileAndLine() throws IOException {
        Path file = folder.resolve("documentation.rules");
        Files.writeString(file, "bold Fine\n\nnonsense here\n");
        assertThatThrownBy(() -> ProjectRules.resolve(model, null, () -> preferences))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(file.toString()).hasMessageContaining("Line 3");
    }

    @Test
    void aMissingOverrideFails() {
        Path missing = folder.resolve("nope.rules");
        assertThatThrownBy(() -> ProjectRules.resolve(model, missing, () -> preferences))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nope.rules");
    }

    @Test
    void savedRulesReadBack() throws IOException {
        KeywordRules rules = KeywordRules.parse("tag red risk\nbold Description:");
        Path file = ProjectRules.save(model, rules);
        assertThat(file).isEqualTo(folder.resolve("documentation.rules"));
        // the list leaves out built-in rules, so the file says defaults off and reads back as exactly this list
        assertThat(Files.readString(file)).contains("\ndefaults off\n");
        assertThat(ProjectRules.read(file).rules()).filteredOn(r -> r.kind != KeywordRules.Kind.DEFAULTS)
                .isEqualTo(rules.rules());
    }

    @Test
    void anUnsavedModelCannotSaveRules() {
        model.setFile(null);
        assertThatThrownBy(() -> ProjectRules.save(model, preferences)).isInstanceOf(IOException.class);
    }
}
