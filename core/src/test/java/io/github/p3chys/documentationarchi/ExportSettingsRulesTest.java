package io.github.p3chys.documentationarchi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.github.p3chys.documentationarchi.text.KeywordRules;

/** The rule lines that are settings of the export rather than looks: exclusions, hidden properties. */
class ExportSettingsRulesTest {

    @Test
    void excludeLabelAndHidePropertyAreReadAndWrittenBack() {
        KeywordRules rules = KeywordRules.parse("exclude label #Draft\nhide property secret\nhide property internal*\n");

        assertThat(rules.excludedLabels()).containsExactly("draft");
        assertThat(rules.hidesProperty("SECRET")).isTrue();
        assertThat(rules.hidesProperty("Internal.x")).isTrue();
        assertThat(rules.hidesProperty("secret2")).isFalse();
        assertThat(KeywordRules.parse(rules.format()).rules()).isEqualTo(rules.rules());
    }

    @Test
    void oldRuleTextsStillWork() {
        KeywordRules rules = KeywordRules.defaults();
        assertThat(rules.excludedLabels()).isEmpty();
        assertThat(rules.hidesProperty("anything")).isFalse();
    }

    @Test
    void mistakesNameTheLine() {
        assertThatThrownBy(() -> KeywordRules.parse("bold X\nexclude label two words"))
                .hasMessageContaining("Line 2").hasMessageContaining("one word");
        assertThatThrownBy(() -> KeywordRules.parse("exclude draft")).hasMessageContaining("exclude label");
        assertThatThrownBy(() -> KeywordRules.parse("\nhide property")).hasMessageContaining("Line 2");
    }
}
