package io.github.p3chys.documentationarchi.ui;

import java.util.List;
import java.util.Locale;

import io.github.p3chys.documentationarchi.text.KeywordRules.CodeTheme;
import io.github.p3chys.documentationarchi.text.KeywordRules.Kind;
import io.github.p3chys.documentationarchi.text.KeywordRules.Rule;
import io.github.p3chys.documentationarchi.text.KeywordRules.TextStyle;
import io.github.p3chys.documentationarchi.text.KeywordRules.Token;

/**
 * A part of every page whose look can be changed: the coloured tokens, the headings and SQL blocks. Their default
 * look comes from style.css and pdf.css of the exporter; PageElementTest keeps the numbers here the same.
 */
final class PageElement {

    /** Body text of the export, which headings and SQL blocks inherit. */
    static final String TEXT_COLOUR = "#172b4d";
    static final int TEXT_PX = 14;
    static final String SQL_BACKGROUND = "#f4f5f7";

    private static final String INLINE_NOTE = KeywordRuleDialog.INLINE_NOTE;
    private static final String HEADING_NOTE = "Confluence keeps text colour, bold, italic, underline and "
            + "strikethrough in headings; other sizes and highlight only where it keeps them.";

    static final List<PageElement> ALL = List.of(
            token(Token.TABLE, "[table]", "[orders] in descriptions", "select from ", "[orders]"),
            token(Token.CONSTANT, "'constant'", "'NEW' in descriptions", "state is ", "'NEW'"),
            token(Token.VARIABLE, "{variable}", "{user} in descriptions", "for the ", "{user}"),
            heading(1, "Page titles", 24, 18, "HTML and PDF only: in Confluence the page title is Confluence's own "
                    + "and keeps its usual look."),
            heading(2, "Contents, Notes, Related views, Elements", 20, 14, HEADING_NOTE),
            heading(3, "Element names", 16, 12, HEADING_NOTE),
            heading(4, "Other related elements", 14, 11, HEADING_NOTE),
            new PageElement(Kind.SQL, "sql", "SQL block", "<sql>...</sql> in descriptions", "", "select * from orders",
                    13, 9, "Monospace " + 13 + " px (PDF " + 9 + " px), highlight " + SQL_BACKGROUND
                            + ", grey border; Confluence: its code block in the default theme",
                    "Colours, size and effects show in HTML and PDF. Confluence shows SQL in its code block macro, "
                            + "which only has the theme and line numbers below."));

    final Kind kind;
    final String key;
    final String name;
    /** Where on the page the element is. */
    final String where;
    final String before;
    final String sample;
    /** Font size of the element in the HTML pages and in the PDF, 0 for that of the text around it. */
    final int htmlPx;
    final int pdfPx;
    /** How the element looks without a rule, in words. */
    final String defaultLook;
    final String note;

    private PageElement(Kind kind, String key, String name, String where, String before, String sample, int htmlPx,
            int pdfPx, String defaultLook, String note) {
        this.kind = kind;
        this.key = key;
        this.name = name;
        this.where = where;
        this.before = before;
        this.sample = sample;
        this.htmlPx = htmlPx;
        this.pdfPx = pdfPx;
        this.defaultLook = defaultLook;
        this.note = note;
    }

    private static PageElement token(Token token, String name, String where, String before, String sample) {
        return new PageElement(Kind.TOKEN, lower(token), name, where, before, sample, 0, 0,
                KeywordStyles.describe(token.builtIn), INLINE_NOTE);
    }

    private static PageElement heading(int level, String where, int htmlPx, int pdfPx, String note) {
        return new PageElement(Kind.HEADING, String.valueOf(level), "Heading " + level, where, "", "Heading " + level,
                htmlPx, pdfPx, "bold " + htmlPx + " px (PDF " + pdfPx + " px), text " + TEXT_COLOUR
                        + (level == 1 ? "" : "; Confluence: its own heading " + level), note);
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    boolean matches(Rule rule) {
        return rule.kind == kind && rule.keyword.equals(key);
    }

    /** The style a new rule starts from: tokens have a colour of their own, the rest only what the export gives them. */
    TextStyle startingStyle() {
        return kind == Kind.TOKEN ? Token.valueOf(key.toUpperCase(Locale.ROOT)).builtIn : TextStyle.PLAIN;
    }

    Rule rule(TextStyle style, CodeTheme theme, boolean lineNumbers) {
        switch (kind) {
            case TOKEN: return Rule.token(Token.valueOf(key.toUpperCase(Locale.ROOT)), style);
            case HEADING: return Rule.heading(Integer.parseInt(key), style);
            default: return Rule.sql(style, theme, lineNumbers);
        }
    }

    /** Above the style settings: the default look, and what the settings do to it. */
    String intro() {
        return "Default: " + defaultLook + (kind == Kind.TOKEN ? ", which is what the settings below start from."
                : ".\nThe settings below change it; unchecked or 100 % keeps it.");
    }

    /** What the preview starts from before the rule's style is added. */
    StyleEditor.Base base() {
        switch (kind) {
            case HEADING: return new StyleEditor.Base(true, htmlPx / (float) TEXT_PX, null, false);
            case SQL: return new StyleEditor.Base(false, htmlPx / (float) TEXT_PX, SQL_BACKGROUND, true);
            default: return StyleEditor.Base.TEXT;
        }
    }
}
