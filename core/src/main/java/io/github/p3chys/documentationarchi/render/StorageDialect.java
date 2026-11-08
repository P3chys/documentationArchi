package io.github.p3chys.documentationarchi.render;

import java.util.Locale;
import java.util.Map;

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.text.KeywordRules.PanelStyle;
import io.github.p3chys.documentationarchi.text.KeywordRules.Rule;
import io.github.p3chys.documentationarchi.text.KeywordRules.TagColour;
import io.github.p3chys.documentationarchi.text.KeywordRules.TextStyle;

/** Confluence Data Center storage format: links, panels, labels and images as Confluence's own macros. */
final class StorageDialect implements Dialect {

    private static final int MAX_IMAGE_WIDTH = 1200;

    private final Map<DocPage, String> titles;

    StorageDialect(Map<DocPage, String> titles) {
        this.titles = titles;
    }

    @Override
    public String link(Doc.Location to, DocPage from, String label) {
        String anchor = to.anchor != null ? " ac:anchor=\"" + Xml.escape(to.anchor) + "\"" : "";
        String page = to.page == from ? "" : "<ri:page ri:content-title=\"" + Xml.escape(titles.get(to.page)) + "\"/>";
        return "<ac:link" + anchor + ">" + page + "<ac:link-body>" + label + "</ac:link-body></ac:link>";
    }

    @Override
    public String entryHeading(Entry entry, String content) {
        return macro("anchor", "<ac:parameter ac:name=\"\">" + Xml.escape(entry.anchor()) + "</ac:parameter>")
                + "<h3>" + content + "</h3>";
    }

    @Override
    public String panel(PanelStyle style, String title, String body) {
        String heading = title == null ? "" : "<ac:parameter ac:name=\"title\">" + Xml.escape(title) + "</ac:parameter>";
        return macro(style.name().toLowerCase(Locale.ROOT), heading + "<ac:rich-text-body>" + body + "</ac:rich-text-body>");
    }

    @Override
    public String sql(String sql, Rule rule) {
        // CDATA keeps the SQL as it is; only its own end marker has to be split
        String text = Xml.clean(sql).replace("]]>", "]]]]><![CDATA[>");
        // the code macro has no colours or fonts of its own, only its themes
        String look = rule == null ? "" : (rule.theme == null ? "" : parameter("theme", rule.theme.confluenceName))
                + (rule.lineNumbers ? parameter("linenumbers", "true") : "");
        return macro("code", parameter("language", "sql") + look
                + "<ac:plain-text-body><![CDATA[" + text + "]]></ac:plain-text-body>");
    }

    private static String parameter(String name, String value) {
        return "<ac:parameter ac:name=\"" + name + "\">" + Xml.escape(value) + "</ac:parameter>";
    }

    @Override
    public String tag(String label, TagColour colour) {
        String name = colour.name().charAt(0) + colour.name().substring(1).toLowerCase(Locale.ROOT);
        return macro("status", "<ac:parameter ac:name=\"colour\">" + name + "</ac:parameter>"
                + "<ac:parameter ac:name=\"title\">" + Xml.escape(label.toUpperCase(Locale.ROOT)) + "</ac:parameter>");
    }

    /**
     * The markup Confluence's editor writes itself: one span per effect, strikethrough as a span, smaller text as
     * {@code small}. Confluence has no larger text or highlight in every version: those spans go along as well and
     * show where Confluence keeps them, the rest of the style stays either way.
     */
    @Override
    public String styled(String escapedText, TextStyle style) {
        String text = style.strike ? span("text-decoration: line-through", escapedText) : escapedText;
        text = Dialect.emphasis(text, style);
        if (style.size < 100) {
            text = "<small>" + text + "</small>";
        } else if (style.size > 100) {
            text = span("font-size: " + style.size + "%", text);
        }
        if (style.background != null) {
            text = span("background-color: " + TextStyle.rgb(style.background), text);
        }
        return style.colour != null ? span("color: " + TextStyle.rgb(style.colour), text) : text;
    }

    private static String span(String css, String content) {
        return "<span style=\"" + css + ";\">" + content + "</span>";
    }

    @Override
    public String image(DocPage page) {
        return "<ac:image ac:width=\"" + Math.min(page.imageWidth, MAX_IMAGE_WIDTH) + "\"><ri:attachment ri:filename=\""
                + Xml.escape(page.slug()) + ".png\"/></ac:image>";
    }

    private static String macro(String name, String content) {
        return "<ac:structured-macro ac:name=\"" + name + "\">" + content + "</ac:structured-macro>";
    }
}
