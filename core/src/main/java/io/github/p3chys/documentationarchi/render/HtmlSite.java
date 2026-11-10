package io.github.p3chys.documentationarchi.render;

import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocFolder;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.text.Texts;

/**
 * What the HTML pages have around their content: a sidebar with the pages under their folders and a search box, an
 * index of the described elements A–Z and one by type, and the data the search filters ({@value #SEARCH}). The
 * sidebar is plain lists and {@code details}, which fold without JavaScript; only the search needs it, and it reads
 * its data from a script file, which a page opened from disk may load where it may not fetch.
 */
final class HtmlSite {

    static final String A_Z = "elements_a-z.html";
    static final String BY_TYPE = "elements_by-type.html";
    static final String SEARCH = "search.js";
    /** Text of a description that the search data holds at most, enough to find it by. */
    private static final int SEARCH_TEXT = 2000;
    /** The page of changes since the published documentation ({@code Published}) lists changes, not elements. */
    private static final String CHANGES = "changes";

    private final Doc doc;
    private final HtmlDialect dialect;
    private final Texts texts;
    private final Collator order = Collator.getInstance(Locale.ROOT);

    HtmlSite(Doc doc, HtmlDialect dialect, Texts texts) {
        this.doc = doc;
        this.dialect = dialect;
        this.texts = texts;
    }

    /** @param current the page it is on, null on an index page @param file the file it is in */
    String sidebar(DocPage current, String file) {
        StringBuilder s = new StringBuilder("<aside class=\"sidebar\"><form class=\"search\" hidden=\"hidden\""
                + " onsubmit=\"return false;\"><input type=\"search\" id=\"search\" placeholder=\"")
                .append(Xml.escape(texts.get("search"))).append("\" aria-label=\"").append(Xml.escape(texts.get("search")))
                .append("\" autocomplete=\"off\"/></form><ul id=\"search-results\" hidden=\"hidden\" data-none=\"")
                .append(Xml.escape(texts.get("nothing-found"))).append("\"></ul><div class=\"tree\"><ul>");
        tree(doc.outline, current, s);
        s.append("</ul><ul class=\"indexes\">");
        index(A_Z, texts.get("elements-a-z"), file, s);
        index(BY_TYPE, texts.get("elements-by-type"), file, s);
        return s.append("</ul></div></aside>").toString();
    }

    private void tree(List<Doc.Node> nodes, DocPage current, StringBuilder s) {
        for (Doc.Node node : nodes) {
            if (node.page != null) {
                String title = Xml.escape(node.page.title);
                s.append(node.page == current ? "<li class=\"current\"><a href=\"" + file(node.page)
                        + "\" aria-current=\"page\">" + title + "</a></li>"
                        : "<li><a href=\"" + file(node.page) + "\">" + title + "</a></li>");
            } else {
                // a folder is open on the pages in it, closed elsewhere
                s.append("<li class=\"folder\"><details").append(holds(node, current) ? " open=\"open\"" : "")
                        .append("><summary>").append(Xml.escape(node.folder.name)).append("</summary><ul>");
                tree(node.children, current, s);
                s.append("</ul></details></li>");
            }
        }
    }

    private static boolean holds(Doc.Node folder, DocPage page) {
        for (DocFolder f = page == null ? null : page.folder; f != null; f = f.parent) {
            if (f == folder.folder) {
                return true;
            }
        }
        return false;
    }

    private static void index(String file, String title, String current, StringBuilder s) {
        s.append(file.equals(current) ? "<li class=\"current\"><a href=\"" + file + "\" aria-current=\"page\">"
                : "<li><a href=\"" + file + "\">").append(Xml.escape(title)).append("</a></li>");
    }

    static String file(DocPage page) {
        return page.slug() + ".html";
    }

    /** The elements the export describes, with where. */
    private List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        for (DocPage page : doc.pages) {
            if (!page.id.equals(CHANGES)) {
                entries.addAll(page.entries);
            }
        }
        entries.sort(Comparator.comparing((Entry e) -> e.name, order).thenComparing(e -> e.type, order));
        return entries;
    }

    /** The A–Z index: the elements by name under their first letter. */
    String aToZ() {
        Map<String, List<Entry>> letters = new TreeMap<>(order);
        for (Entry entry : entries()) {
            letters.computeIfAbsent(letter(entry.name), k -> new ArrayList<>()).add(entry);
        }
        StringBuilder s = new StringBuilder();
        if (letters.size() > 1) {
            s.append("<p class=\"letters\">");
            int i = 0;
            for (String l : letters.keySet()) {
                s.append("<a href=\"#letter-").append(i++).append("\">").append(Xml.escape(l)).append("</a> ");
            }
            s.append("</p>");
        }
        int i = 0;
        for (Map.Entry<String, List<Entry>> letter : letters.entrySet()) {
            s.append("<h2 id=\"letter-").append(i++).append("\">").append(Xml.escape(letter.getKey())).append("</h2><ul>");
            letter.getValue().forEach(e -> s.append("<li>").append(type(e.type)).append(link(e)).append("</li>"));
            s.append("</ul>");
        }
        return s.toString();
    }

    /** The index by type: the types sorted, the elements of each by name. */
    String byType() {
        Map<String, List<Entry>> types = new TreeMap<>(order);
        for (Entry entry : entries()) {
            types.computeIfAbsent(entry.type, k -> new ArrayList<>()).add(entry);
        }
        StringBuilder s = new StringBuilder();
        types.forEach((type, entries) -> {
            s.append("<h2>").append(Xml.escape(type)).append(" <small>(").append(entries.size()).append(")</small></h2><ul>");
            entries.forEach(e -> s.append("<li>").append(link(e)).append("</li>"));
            s.append("</ul>");
        });
        return s.toString();
    }

    private String link(Entry entry) {
        return dialect.link(doc.locate(entry.id), null, Xml.escape(entry.name));
    }

    /** The letter an index groups a name under: without accents, upper case; # for anything but a letter. */
    static String letter(String name) {
        String plain = Normalizer.normalize(name.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        if (plain.isEmpty() || !Character.isLetter(plain.codePointAt(0))) {
            return "#";
        }
        return new String(Character.toChars(plain.codePointAt(0))).toUpperCase(Locale.ROOT);
    }

    private static String type(String type) {
        return "<small><span style=\"color: rgb(107,119,140);\">&lt;&lt;" + Xml.escape(type) + "&gt;&gt;</span></small> ";
    }

    /**
     * The search data as a script: per page and per described element its name, type (views and other pages: their
     * kind), where it is and the start of its description.
     */
    String searchData() {
        StringBuilder s = new StringBuilder("window.DOC_SEARCH = [\n");
        for (DocPage page : doc.pages) {
            item(page.title, texts.get(page.image != null ? "view" : "page"), file(page),
                    page.documentation, s);
            if (!page.id.equals(CHANGES)) {
                for (Entry entry : page.entries) {
                    item(entry.name, entry.type, file(page) + "#" + entry.anchor(), entry.documentation, s);
                }
            }
        }
        return s.append("];\n").toString();
    }

    private static void item(String name, String type, String url, String text, StringBuilder s) {
        String plain = text == null ? "" : text.replaceAll("\\s+", " ").strip();
        plain = plain.length() > SEARCH_TEXT ? plain.substring(0, SEARCH_TEXT) : plain;
        s.append("{n:").append(js(name)).append(",t:").append(js(type)).append(",u:").append(js(url))
                .append(",d:").append(js(plain)).append("},\n");
    }

    /** A string for a script: nothing in it can end the string or the script. */
    static String js(String text) {
        StringBuilder s = new StringBuilder("\"");
        text.chars().forEach(c -> {
            if (c == '"' || c == '\\') {
                s.append('\\').append((char) c);
            } else if (c < 0x20 || c == '<' || c == '>' || c == '&' || c == 0x2028 || c == 0x2029) {
                s.append(String.format("\\u%04x", c));
            } else {
                s.append((char) c);
            }
        });
        return s.append('"').toString();
    }
}
