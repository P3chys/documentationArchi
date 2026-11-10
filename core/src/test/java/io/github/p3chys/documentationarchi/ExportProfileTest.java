package io.github.p3chys.documentationarchi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.github.p3chys.documentationarchi.text.ExportProfile;
import io.github.p3chys.documentationarchi.text.KeywordRules;

/** The {@code profile} rule lines: named export set-ups kept with the model. */
class ExportProfileTest {

    private static final String LINE = "profile developers folders=Views/Sales,Views/Claims formats=html,pdf "
            + "exclude=#internal hide=cost* language=en output=docs/dev publish";

    @Test
    void allSettingsAreRead() {
        ExportProfile p = KeywordRules.parse(LINE).profile("Developers");

        assertThat(p.name).isEqualTo("developers");
        assertThat(p.folders).containsExactly("Views/Sales", "Views/Claims");
        assertThat(p.formats).containsExactly("html", "pdf");
        assertThat(p.exclude).containsExactly("internal");
        assertThat(p.hide).containsExactly("cost*");
        assertThat(p.language).isEqualTo("en");
        assertThat(p.output).isEqualTo("docs/dev");
        assertThat(p.publish).isTrue();
        assertThat(p.hasScope()).isTrue();
        assertThat(p.line).isEqualTo(1);
    }

    @Test
    void quotedNamesAndValuesWithSpacesSurviveAWriteAndRead() {
        KeywordRules rules = KeywordRules.parse("# x\nprofile \"Sales team\" views=\"Order flow\",Overview output=\"my docs\"\n");
        ExportProfile p = rules.profile("sales team");

        assertThat(p.views).containsExactly("Order flow", "Overview");
        assertThat(p.output).isEqualTo("my docs");
        assertThat(p.line).isEqualTo(2);
        assertThat(KeywordRules.parse(rules.format()).rules()).isEqualTo(rules.rules());
        assertThat(KeywordRules.parse(rules.format()).profile("Sales team").views).containsExactly("Order flow", "Overview");
    }

    @Test
    void noSettingsMeansTheWholeModel() {
        assertThat(KeywordRules.parse("profile all").profile("all").hasScope()).isFalse();
    }

    @Test
    void aProfileAddsItsLabelsPropertiesAndLanguageToTheRules() {
        KeywordRules rules = KeywordRules.parse("exclude label draft\nhide property secret\n"
                + LINE.replace("language=en", "language=cs"));
        KeywordRules with = rules.with(rules.profile("developers"));

        assertThat(with.excludedLabels()).containsExactly("draft", "internal");
        assertThat(with.hidesProperty("cost.net")).isTrue();
        assertThat(with.hidesProperty("secret")).isTrue();
        assertThat(rules.hidesProperty("cost.net")).isFalse();
        assertThat(with.texts().get("contents")).isEqualTo("Obsah");
    }

    @Test
    void mistakesNameTheLine() {
        assertThatThrownBy(() -> KeywordRules.parse("bold X\nprofile a formats=docx")).hasMessageContaining("Line 2")
                .hasMessageContaining("unknown format");
        assertThatThrownBy(() -> KeywordRules.parse("profile a colour=red")).hasMessageContaining("unknown setting");
        assertThatThrownBy(() -> KeywordRules.parse("profile a language=de")).hasMessageContaining("unknown language");
        assertThatThrownBy(() -> KeywordRules.parse("profile a views=x workpackages=y")).hasMessageContaining("combined");
        assertThatThrownBy(() -> KeywordRules.parse("profile a publish=yes")).hasMessageContaining("no value");
        assertThatThrownBy(() -> KeywordRules.parse("profile a output=")).hasMessageContaining("output");
        assertThatThrownBy(() -> KeywordRules.parse("profile a views=x views=y")).hasMessageContaining("twice");
        assertThatThrownBy(() -> KeywordRules.parse("profile")).hasMessageContaining("profile <name>");
        assertThatThrownBy(() -> KeywordRules.parse("profile \"a")).hasMessageContaining("quote");
    }
}
