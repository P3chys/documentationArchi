package io.github.p3chys.documentationarchi.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.p3chys.documentationarchi.text.KeywordRules.Rule;
import io.github.p3chys.documentationarchi.text.KeywordRules.TextStyle;

/** How rules read in the UI. No SWT here: page elements use it outside the workbench too. */
final class KeywordStyles {

    private KeywordStyles() {
    }

    static String where(Rule rule) {
        switch (rule.kind) {
            case PANEL: return "Line \"" + rule.keyword + ": ...\"";
            case TAG: return rule.keyword.matches("[\\p{L}\\p{N}_-]+") ? "#" + rule.keyword + " or [[" + rule.keyword + "]]"
                    : "[[" + rule.keyword + "]]";
            default: return "Anywhere in the text";
        }
    }

    static String describe(Rule rule) {
        switch (rule.kind) {
            case PANEL: return lower(rule.panel) + " panel";
            case TAG: return lower(rule.tag) + " label";
            default: return describe(rule.style);
        }
    }

    static String describe(TextStyle style) {
        List<String> parts = new ArrayList<>();
        if (style.colour != null) {
            parts.add("text " + style.colour);
        }
        if (style.background != null) {
            parts.add("highlight " + style.background);
        }
        if (style.size != 100) {
            parts.add(style.size + " %");
        }
        if (style.bold) {
            parts.add("bold");
        }
        if (style.italic) {
            parts.add("italic");
        }
        if (style.underline) {
            parts.add("underline");
        }
        if (style.strike) {
            parts.add("strikethrough");
        }
        return parts.isEmpty() ? "unchanged" : String.join(", ", parts);
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
