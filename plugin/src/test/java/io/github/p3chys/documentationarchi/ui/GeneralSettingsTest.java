package io.github.p3chys.documentationarchi.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.p3chys.documentationarchi.text.KeywordRules;
import io.github.p3chys.documentationarchi.text.KeywordRules.Rule;

class GeneralSettingsTest {

    private static List<Rule> rules(String text) {
        return KeywordRules.parse(text).rules();
    }

    private static String text(List<Rule> rules) {
        return KeywordRules.of(rules).format();
    }

    @Test
    void readsTheSettingRules() {
        GeneralSettings s = GeneralSettings.of(rules(String.join("\n", "jira https://jira.example.com/browse/",
                "language cs", "assets \"My Files\"", "type style haf", "workpackage one-page", "html single-file",
                "pdf cover off", "exclude label draft", "exclude label wip", "hide property internal*")));

        assertThat(s.jira).isEqualTo("https://jira.example.com/browse/");
        assertThat(s.language).isEqualTo("cs");
        assertThat(s.assets).isEqualTo("My Files");
        assertThat(s.hafTypes).isTrue();
        assertThat(s.workPackageOnePage).isTrue();
        assertThat(s.htmlSingleFile).isTrue();
        assertThat(s.pdf).containsExactly("contents", "landscape");
        assertThat(s.excludedLabels).containsExactly("draft", "wip");
        assertThat(s.hiddenProperties).containsExactly("internal*");
    }

    @Test
    void withoutRulesEverythingIsAsUsual() {
        GeneralSettings s = GeneralSettings.of(List.of());

        assertThat(s.jira).isEmpty();
        assertThat(s.language).isEqualTo("en");
        assertThat(s.assets).isEmpty();
        assertThat(s.hafTypes || s.workPackageOnePage || s.htmlSingleFile).isFalse();
        assertThat(s.pdf).containsExactlyElementsOf(KeywordRules.PDF_PARTS);
        assertThat(s.applyTo(List.of())).isEmpty();
    }

    @Test
    void writesOnlyWhatDiffersAndKeepsTheRulesItDoesNotShow() {
        List<Rule> settings = rules(String.join("\n", "jira https://old.example.com/", "language cs",
                "text contents = Table of contents", "index tables", "exclude label draft"));
        GeneralSettings s = GeneralSettings.of(settings);
        s.jira = "https://jira.example.com/browse/";
        s.language = "en";
        s.pdf.remove("landscape");
        s.excludedLabels.clear();
        s.hiddenProperties.add("owner");

        assertThat(text(s.applyTo(settings)).lines().filter(l -> !l.startsWith("#"))).containsExactly(
                "text contents = Table of contents", "index tables", "jira https://jira.example.com/browse/",
                "pdf landscape off", "hide property owner");
    }

    @Test
    void readBackGivesTheSameSettings() {
        GeneralSettings s = new GeneralSettings();
        s.jira = "https://jira.example.com/browse/";
        s.assets = "Files";
        s.hafTypes = true;
        s.pdf.remove("cover");
        s.excludedLabels.add("draft");

        GeneralSettings back = GeneralSettings.of(KeywordRules.parse(text(s.applyTo(List.of()))).rules());

        assertThat(back.jira).isEqualTo(s.jira);
        assertThat(back.assets).isEqualTo("Files");
        assertThat(back.hafTypes).isTrue();
        assertThat(back.pdf).containsExactly("contents", "landscape");
        assertThat(back.excludedLabels).containsExactly("draft");
    }

    @Test
    void aJiraAddressMustBeAWebAddress() {
        GeneralSettings s = new GeneralSettings();
        s.jira = "jira.example.com";

        assertThatThrownBy(() -> s.applyTo(List.of())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Jira address");
    }

    @Test
    void listsAreTypedWithCommasOrSpaces() {
        assertThat(GeneralSettings.words(" internal*, owner;; owner  wip ")).containsExactly("internal*", "owner", "wip");
        assertThat(GeneralSettings.words("  ")).isEmpty();
    }
}
