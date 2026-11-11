package io.github.p3chys.documentationarchi.ui;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.p3chys.documentationarchi.text.KeywordRules;
import io.github.p3chys.documentationarchi.text.KeywordRules.Kind;
import io.github.p3chys.documentationarchi.text.KeywordRules.Rule;

/**
 * The settings of the General tab of the preferences, which are rule lines ({@code jira}, {@code language},
 * {@code assets}...) that otherwise only "Edit as Text" shows. Read from the setting rules and written back into
 * them, leaving the rules the tab does not show (profiles, catalogs, fixed texts...) as they are.
 */
final class GeneralSettings {

    /** The kinds of rules this tab owns: written from its fields, any other rule of these kinds is replaced. */
    static final Set<Kind> KINDS = EnumSet.of(Kind.JIRA, Kind.LANGUAGE, Kind.ASSETS, Kind.TYPE_STYLE, Kind.WORK_PACKAGE,
            Kind.HTML, Kind.PDF, Kind.EXCLUDE_LABEL, Kind.HIDE_PROPERTY);

    /** Where tickets are, the key goes after it; empty for nowhere. */
    String jira = "";
    String language = "en";
    /** The assets folder as written; empty for the usual (Assets). */
    String assets = "";
    boolean hafTypes;
    boolean workPackageOnePage;
    boolean htmlSingleFile;
    /** The parts of {@link KeywordRules#PDF_PARTS} the PDF has. */
    final Set<String> pdf = new LinkedHashSet<>(KeywordRules.PDF_PARTS);
    /** Views, elements and relationships with these labels (#draft) stay out of every export; without #. */
    final List<String> excludedLabels = new ArrayList<>();
    /** Properties with these keys are never printed; * at the end matches any ending. */
    final List<String> hiddenProperties = new ArrayList<>();

    static GeneralSettings of(List<Rule> settings) {
        KeywordRules rules = KeywordRules.of(settings);
        GeneralSettings s = new GeneralSettings();
        s.jira = rules.jira() == null ? "" : rules.jira();
        s.assets = rules.assetsFolder() == null ? "" : rules.assetsFolder();
        s.hafTypes = rules.hafTypes();
        s.workPackageOnePage = rules.workPackageOnePage();
        s.htmlSingleFile = rules.htmlSingleFile();
        s.pdf.removeIf(part -> !rules.pdf(part));
        for (Rule rule : settings) {
            if (rule.kind == Kind.LANGUAGE) {
                s.language = rule.keyword;
            } else if (rule.kind == Kind.EXCLUDE_LABEL) {
                s.excludedLabels.add(rule.keyword);
            } else if (rule.kind == Kind.HIDE_PROPERTY) {
                s.hiddenProperties.add(rule.keyword);
            }
        }
        return s;
    }

    /**
     * The setting rules with this tab's rules in place of those of its kinds: only what differs from the usual is
     * written, so the text stays short.
     *
     * @throws IllegalArgumentException when a field is not valid, saying which
     */
    List<Rule> applyTo(List<Rule> settings) {
        List<Rule> rules = new ArrayList<>();
        settings.stream().filter(r -> !KINDS.contains(r.kind)).forEach(rules::add);
        if (!jira.isBlank()) {
            try {
                rules.add(Rule.jira(jira));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Jira address: it must start with http:// or https://, e.g. "
                        + "https://jira.example.com/browse/");
            }
        }
        if (!language.equals("en")) {
            rules.add(Rule.language(language));
        }
        if (!assets.isBlank()) {
            rules.add(Rule.assets(assets.strip()));
        }
        if (hafTypes) {
            rules.add(Rule.typeStyle("haf"));
        }
        if (workPackageOnePage) {
            rules.add(Rule.workPackage("one-page"));
        }
        if (htmlSingleFile) {
            rules.add(Rule.htmlSingleFile(true));
        }
        for (String part : KeywordRules.PDF_PARTS) {
            if (!pdf.contains(part)) {
                rules.add(Rule.pdf(part, false));
            }
        }
        excludedLabels.forEach(label -> rules.add(Rule.excludeLabel(label)));
        hiddenProperties.forEach(key -> rules.add(Rule.hideProperty(key)));
        return rules;
    }

    /** A list typed as "a, b c": the words, without empty ones. */
    static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        for (String word : text.split("[,;\\s]+")) {
            if (!word.isBlank() && !words.contains(word.strip())) {
                words.add(word.strip());
            }
        }
        return words;
    }
}
