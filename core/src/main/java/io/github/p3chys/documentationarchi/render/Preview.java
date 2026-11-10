package io.github.p3chys.documentationarchi.render;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.text.KeywordRules;

/**
 * What the Documentation Preview shows, as a whole HTML document: a page, or the descriptions of elements, rendered
 * by the same {@link PageBody} and {@link HtmlDialect} as the HTML export, so the preview never differs from it.
 * Links to views and elements are {@link #REF} and the id, for the preview to show that one instead.
 */
public final class Preview {

    /** In front of the id of the view or element a link of the preview leads to. */
    public static final String REF = "#ref-";
    /** In front of an element's id in its anchor, see {@link Entry#anchor()}. */
    static final String ENTRY = "e-";
    private static final String STYLE = resource("style.css");

    private Preview() {
    }

    /** A whole page, as its HTML file has it. */
    public static String page(Doc doc, DocPage page, KeywordRules rules, String pictureUrl) {
        HtmlDialect dialect = HtmlDialect.preview(pictureUrl);
        return document(page.title, "<h1>" + title(page.title, rules, dialect) + "</h1>"
                + PageBody.render(doc, page, dialect, rules, new HashSet<>()));
    }

    /**
     * The descriptions of the elements as their page has them, each saying which page that is.
     *
     * @return null when the export describes none of them
     */
    public static String elements(Doc doc, List<String> ids, KeywordRules rules) {
        HtmlDialect dialect = HtmlDialect.preview(null);
        StringBuilder body = new StringBuilder();
        String title = null;
        for (String id : ids) {
            Doc.Location at = doc.locate(id);
            Entry entry = at == null || at.anchor == null ? null
                    : at.page.entries.stream().filter(e -> e.id.equals(id)).findFirst().orElse(null);
            if (entry == null) {
                continue;
            }
            // the page with only this element on it: the same headings and links as there
            DocPage alone = new DocPage(at.page.id, at.page.title);
            alone.entries.add(entry);
            if (at.page.groups.containsKey(id)) {
                alone.groups.put(id, at.page.groups.get(id));
            }
            body.append(body.length() == 0 ? "" : "<hr/>").append("<p class=\"preview-where\">Described on ")
                    .append(dialect.link(doc.locate(at.page.id), alone, Xml.escape(at.page.title)))
                    .append("</p>").append(PageBody.render(doc, alone, dialect, rules, new HashSet<>()));
            title = title == null ? entry.name : title;
        }
        return title == null ? null : document(title, body.toString());
    }

    /** A line of text in place of a page, such as why there is nothing to show. */
    public static String message(String text) {
        return document("Documentation Preview", "<p class=\"preview-message\">" + Xml.escape(text) + "</p>");
    }

    /** The id a link of the preview leads to, null for any other location. */
    public static String target(String location) {
        int at = location == null ? -1 : location.indexOf(REF);
        return at < 0 || at + REF.length() == location.length() ? null : location.substring(at + REF.length());
    }

    private static String title(String title, KeywordRules rules, Dialect dialect) {
        String escaped = Xml.escape(title);
        return rules.heading(1) == null ? escaped : dialect.styled(escaped, rules.heading(1));
    }

    /** IE, the browser of older Archi on Windows, renders as IE 7 unless told otherwise. */
    private static String document(String title, String body) {
        return "<!DOCTYPE html>\n<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"utf-8\"/>"
                + "<meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\"/><title>" + Xml.escape(title) + "</title>"
                + "<style>" + STYLE + "body { max-width: none; } .preview-where, .preview-message { color: #6b778c; }"
                + "</style></head><body>" + body + "</body></html>\n";
    }

    private static String resource(String name) {
        try (InputStream in = Preview.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
