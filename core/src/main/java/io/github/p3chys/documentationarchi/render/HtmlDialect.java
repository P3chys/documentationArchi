package io.github.p3chys.documentationarchi.render;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import io.github.p3chys.documentationarchi.doc.Assets;
import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.text.KeywordRules.PanelStyle;
import io.github.p3chys.documentationarchi.text.KeywordRules.Rule;
import io.github.p3chys.documentationarchi.text.KeywordRules.TagColour;
import io.github.p3chys.documentationarchi.text.KeywordRules.TextStyle;

/**
 * Plain XHTML: one file per page, all pages in one document for the PDF, or one page in the Documentation Preview.
 */
final class HtmlDialect implements Dialect {

    private final Doc doc;
    private final boolean singleDocument;
    /** In the preview: where the picture of the view is; null otherwise. */
    private final String preview;
    /** In the PDF: wide pictures are on landscape pages, see {@link Pictures#landscape}. */
    private boolean landscape;

    HtmlDialect(Doc doc, boolean singleDocument) {
        this(doc, singleDocument, null);
    }

    private HtmlDialect(Doc doc, boolean singleDocument, String preview) {
        this.doc = doc;
        this.singleDocument = singleDocument;
        this.preview = preview;
    }

    HtmlDialect landscape(boolean on) {
        landscape = on;
        return this;
    }

    /**
     * The preview's: links to views and elements are {@link Preview#REF} and an id, which the preview shows instead of
     * following; assets are the files in the model's Assets folder, the picture is at {@code pictureUrl}.
     */
    static HtmlDialect preview(Doc doc, String pictureUrl) {
        return new HtmlDialect(doc, false, pictureUrl == null ? "" : pictureUrl);
    }

    @Override
    public String link(Doc.Location to, DocPage from, String label) {
        return "<a href=\"" + Xml.escape(href(to, from)) + "\">" + label + "</a>";
    }

    private String href(Doc.Location to, DocPage from) {
        String href;
        if (preview != null) {
            href = Preview.REF + (to.anchor != null ? to.anchor.substring(Preview.ENTRY.length()) : to.page.id);
        } else if (singleDocument) {
            href = "#" + (to.anchor != null ? to.anchor : pageId(to.page));
        } else {
            href = (to.page == from ? "" : to.page.slug() + ".html") + (to.anchor != null ? "#" + to.anchor : "");
        }
        return href.isEmpty() ? "#" : href;
    }

    /** In the PDF's document: the picture of the page with that slug, which the PDF library reads from its file. */
    static final String PICTURE = "docpicture:";

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
    public String code(String code, String language) {
        return "<pre class=\"code\">" + Xml.escape(code) + "</pre>";
    }

    @Override
    public String tag(String label, TagColour colour) {
        return "<span class=\"tag " + colour.name().toLowerCase(Locale.ROOT) + "\">"
                + Xml.escape(label.toUpperCase(Locale.ROOT)) + "</span>";
    }

    @Override
    public String image(DocPage page) {
        String src = singleDocument ? PICTURE + page.slug() : preview != null ? preview : "images/" + page.slug() + ".png";
        // the PDF fits it on one page; a web page shows it at its size, readers zoom and scroll
        int fitted = !singleDocument ? 0 : landscape && Pictures.landscape(page)
                ? Pictures.fit(page.imageWidth, page.imageHeight, Pictures.LANDSCAPE_WIDTH, Pictures.LANDSCAPE_HEIGHT)
                : Pictures.fit(page.imageWidth, page.imageHeight, Pictures.PDF_WIDTH);
        int width = fitted > 0 ? fitted : page.imageWidth;
        String alt = " alt=\"" + Xml.escape(page.title) + "\"/>";
        String image = "<img class=\"diagram\" src=\"" + src + "\" style=\"width:" + width + "px\"";
        List<DocPage.Area> areas = new ArrayList<>();
        List<Doc.Location> targets = new ArrayList<>();
        for (DocPage.Area area : page.areas) {
            Doc.Location to = doc.locate(area.id);
            if (to != null && to.anchor != null) { // an element described somewhere in this export
                areas.add(area);
                targets.add(to);
            }
        }
        if (areas.isEmpty() || page.imageWidth <= 0) {
            return image + alt;
        }
        return singleDocument ? pdfLinks(page, image + alt, width, areas, targets) : map(page, image, alt, areas, targets);
    }

    /**
     * An image map, in the coordinates of the image as shown: at its width, never smaller (it scrolls sideways
     * instead), since the areas of a map do not shrink with the image. The topmost figure is the first area.
     */
    private String map(DocPage page, String image, String alt, List<DocPage.Area> areas, List<Doc.Location> targets) {
        String name = "map-" + page.slug();
        StringBuilder map = new StringBuilder("<map name=\"").append(name).append("\" id=\"").append(name).append("\">");
        for (int i = areas.size() - 1; i >= 0; i--) {
            DocPage.Area a = areas.get(i);
            String label = Xml.escape(a.label);
            map.append("<area shape=\"rect\" coords=\"").append(a.x).append(',').append(a.y).append(',')
                    .append(a.x + a.width).append(',').append(a.y + a.height).append("\" href=\"")
                    .append(Xml.escape(href(targets.get(i), page))).append("\" alt=\"").append(label)
                    .append("\" title=\"").append(label).append("\"/>");
        }
        return "<span class=\"diagram\">" + image + " usemap=\"#" + name + "\"" + alt + map + "</map></span>";
    }

    /**
     * The PDF library knows no image maps: a link box over each figure, at the size the picture is fitted to. Drawn
     * after their figures' outer ones, so the inner one is on top.
     */
    private String pdfLinks(DocPage page, String image, int width, List<DocPage.Area> areas, List<Doc.Location> targets) {
        double scale = width / (double) page.imageWidth;
        StringBuilder s = new StringBuilder("<span class=\"diagram-links\" style=\"width:").append(width).append("px;");
        if (page.imageHeight > 0) {
            s.append(" height:").append(Math.round(page.imageHeight * scale)).append("px;");
        }
        s.append("\">").append(image);
        for (int i = 0; i < areas.size(); i++) {
            DocPage.Area a = areas.get(i);
            s.append("<a href=\"").append(Xml.escape(href(targets.get(i), page))).append("\" style=\"left:")
                    .append(px(a.x * scale)).append("; top:").append(px(a.y * scale)).append("; width:")
                    .append(px(a.width * scale)).append("; height:").append(px(a.height * scale)).append(";\"></a>");
        }
        return s.append("</span>").toString();
    }

    private static String px(double value) {
        return String.format(Locale.ROOT, "%.1fpx", value);
    }

    /**
     * Pages link to the copy in assets/ next to them; the PDF too, for files (and SVG, which the PDF library can't
     * draw): a relative link is what every PDF viewer opens, attachments behind a link Edge and Chrome ignore. The
     * PDF draws pictures from data, and also carries the files as attachments (see {@link Exporter}).
     */
    @Override
    public String asset(Assets.Asset asset, String label) {
        if (singleDocument && pdfImage(asset)) {
            int fitted = Pictures.fit(asset.width(), asset.height(), Pictures.PDF_WIDTH);
            return "<img class=\"asset\" src=\"data:" + asset.mimeType() + ";base64," + base64(asset) + "\"" + width(fitted)
                    + " alt=\"" + label + "\"/>";
        }
        String href = preview != null ? asset.file.toUri().toString() : "assets/" + encode(asset.name);
        if (singleDocument || !asset.image()) {
            return "<a class=\"asset\" href=\"" + href + "\">" + label + "</a>";
        }
        int fitted = Pictures.fit(asset.width(), asset.height(), Integer.MAX_VALUE);
        String image = "<img class=\"asset\" src=\"" + href + "\"" + width(fitted) + " alt=\"" + label + "\"/>";
        // a picture shown smaller opens at its full size
        return fitted > 0 ? "<a href=\"" + href + "\">" + image + "</a>" : image;
    }

    private static String width(int fitted) {
        return fitted > 0 ? " style=\"width:" + fitted + "px\"" : "";
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
