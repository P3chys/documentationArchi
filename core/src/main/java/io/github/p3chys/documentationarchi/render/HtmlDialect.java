package io.github.p3chys.documentationarchi.render;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

import io.github.p3chys.documentationarchi.doc.Assets;
import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.text.KeywordRules.PanelStyle;
import io.github.p3chys.documentationarchi.text.KeywordRules.Rule;
import io.github.p3chys.documentationarchi.text.KeywordRules.TagColour;
import io.github.p3chys.documentationarchi.text.KeywordRules.TextStyle;

/** Plain XHTML: one file per page, or all pages in one document for the PDF. */
final class HtmlDialect implements Dialect {

    private final boolean singleDocument;

    HtmlDialect(boolean singleDocument) {
        this.singleDocument = singleDocument;
    }

    @Override
    public String link(Doc.Location to, DocPage from, String label) {
        String href;
        if (singleDocument) {
            href = "#" + (to.anchor != null ? to.anchor : pageId(to.page));
        } else {
            href = (to.page == from ? "" : to.page.slug() + ".html") + (to.anchor != null ? "#" + to.anchor : "");
        }
        return "<a href=\"" + Xml.escape(href.isEmpty() ? "#" : href) + "\">" + label + "</a>";
    }

    static String pageId(DocPage page) {
        return "page-" + page.slug();
    }

    @Override
    public String entryHeading(Entry entry, String content) {
        return "<h3 id=\"" + Xml.escape(entry.anchor()) + "\">" + content + "</h3>";
    }

    @Override
    public String panel(PanelStyle style, String title, String body) {
        String heading = title == null ? "" : "<p class=\"panel-title\">" + Xml.escape(title) + "</p>";
        return "<div class=\"panel " + style.name().toLowerCase(Locale.ROOT) + "\">" + heading + body + "</div>";
    }

    @Override
    public String sql(String sql, Rule rule) {
        if (rule == null) {
            return "<pre class=\"sql\">" + Xml.escape(sql) + "</pre>";
        }
        // the background on the whole block, the size relative to the block's own font
        String background = rule.style.background == null ? ""
                : " style=\"background-color: " + TextStyle.rgb(rule.style.background) + ";\"";
        return "<pre class=\"sql\"" + background + ">" + styled(Xml.escape(sql), rule.style.withoutBackground()) + "</pre>";
    }

    @Override
    public String tag(String label, TagColour colour) {
        return "<span class=\"tag " + colour.name().toLowerCase(Locale.ROOT) + "\">"
                + Xml.escape(label.toUpperCase(Locale.ROOT)) + "</span>";
    }

    @Override
    public String image(DocPage page) {
        String src = singleDocument ? "data:image/png;base64," + Base64.getEncoder().encodeToString(page.image)
                : "images/" + page.slug() + ".png";
        return "<img class=\"diagram\" src=\"" + src + "\" style=\"width:" + page.imageWidth + "px\" alt=\""
                + Xml.escape(page.title) + "\"/>";
    }

    /**
     * Pages link to the copy in assets/ next to them; the PDF too, for files (and SVG, which the PDF library can't
     * draw): a relative link is what every PDF viewer opens, attachments behind a link Edge and Chrome ignore. The
     * PDF draws pictures from data, and also carries the files as attachments (see {@link Exporter}).
     */
    @Override
    public String asset(Assets.Asset asset, String label) {
        if (singleDocument && pdfImage(asset)) {
            return "<img class=\"asset\" src=\"data:" + asset.mimeType() + ";base64," + base64(asset) + "\" alt=\""
                    + label + "\"/>";
        }
        String href = "assets/" + encode(asset.name);
        return !singleDocument && asset.image() ? "<img class=\"asset\" src=\"" + href + "\" alt=\"" + label + "\"/>"
                : "<a class=\"asset\" href=\"" + href + "\">" + label + "</a>";
    }

    /** Drawn in the PDF; any other asset is a file the PDF links to. */
    static boolean pdfImage(Assets.Asset asset) {
        return asset.image() && !asset.extension().equals("svg");
    }

    private static String base64(Assets.Asset asset) {
        try {
            return Base64.getEncoder().encodeToString(asset.bytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A file name as one part of a relative URL. */
    private static String encode(String name) {
        return URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
