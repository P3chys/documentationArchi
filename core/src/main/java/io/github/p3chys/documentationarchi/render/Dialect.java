package io.github.p3chys.documentationarchi.render;

import io.github.p3chys.documentationarchi.doc.Assets;
import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.text.KeywordRules;
import io.github.p3chys.documentationarchi.text.KeywordRules.PanelStyle;
import io.github.p3chys.documentationarchi.text.KeywordRules.Rule;
import io.github.p3chys.documentationarchi.text.KeywordRules.TagColour;
import io.github.p3chys.documentationarchi.text.KeywordRules.TextStyle;

/**
 * What differs between the outputs. Everything else on a page is plain XHTML, which HTML, the PDF and Confluence
 * storage format all share, so the outputs can't drift apart.
 */
interface Dialect {

    String link(Doc.Location to, DocPage from, String escapedLabel);

    String entryHeading(Entry entry, String content);

    /** @param title heading of the panel, null for none */
    String panel(PanelStyle style, String title, String body);

    /** @param rule how SQL blocks look, null for as usual */
    String sql(String sql, Rule rule);

    /** SQL as {@link #sql(String, Rule)}, for outputs that can colour its keywords and tokens by the rules. */
    default String sql(String sql, Rule rule, KeywordRules rules) {
        return sql(sql, rule);
    }

    String tag(String label, TagColour colour);

    /** @param language as written in the description, null for none */
    String code(String code, String language);

    /** Markup in a style: bold, italic and underline as tags, the rest as one inline style. */
    default String styled(String escapedText, TextStyle style) {
        StringBuilder css = new StringBuilder();
        if (style.colour != null) {
            css.append("color: ").append(TextStyle.rgb(style.colour)).append("; ");
        }
        if (style.background != null) {
            css.append("background-color: ").append(TextStyle.rgb(style.background)).append("; padding: 0 2px; ");
        }
        if (style.size != 100) {
            css.append("font-size: ").append(style.size).append("%; ");
        }
        if (style.strike) {
            css.append("text-decoration: line-through; ");
        }
        String text = emphasis(escapedText, style);
        return css.length() == 0 ? text : "<span style=\"" + css.toString().strip() + "\">" + text + "</span>";
    }

    static String emphasis(String text, TextStyle style) {
        String t = style.underline ? "<u>" + text + "</u>" : text;
        t = style.italic ? "<em>" + t + "</em>" : t;
        return style.bold ? "<strong>" + t + "</strong>" : t;
    }

    String image(DocPage page);

    /** A file of the Assets folder: the picture where the output shows it, otherwise a link that opens the file. */
    String asset(Assets.Asset asset, String escapedLabel);

    /** A picture at a web address. */
    String remoteImage(String escapedUrl);
}
