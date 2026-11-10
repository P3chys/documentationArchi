package io.github.p3chys.documentationarchi.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.github.p3chys.documentationarchi.doc.Assets;
import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.DocTable;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.doc.Link;
import io.github.p3chys.documentationarchi.doc.ObjectText;
import io.github.p3chys.documentationarchi.doc.Property;
import io.github.p3chys.documentationarchi.doc.Relation;
import io.github.p3chys.documentationarchi.text.KeywordRules;
import io.github.p3chys.documentationarchi.text.RichText;

/** The content of one page, the same for every output apart from what its {@link Dialect} decides. */
final class PageBody {

    /** How far each open nesting tag indents, in pixels. */
    private static final int INDENT = 20;

    private final Doc doc;
    private final DocPage page;
    private final Dialect dialect;
    private final KeywordRules rules;
    private final Set<Assets.Asset> used;
    private final StringBuilder out = new StringBuilder();
    /** Paragraphs in grey: a relationship's description written with it. */
    private boolean grey;

    private PageBody(Doc doc, DocPage page, Dialect dialect, KeywordRules rules, Set<Assets.Asset> used) {
        this.doc = doc;
        this.page = page;
        this.dialect = dialect;
        this.rules = rules;
        this.used = used;
    }

    /** @param used gets the assets the page shows or links to */
    static String render(Doc doc, DocPage page, Dialect dialect, KeywordRules rules, Set<Assets.Asset> used) {
        PageBody body = new PageBody(doc, page, dialect, rules, used);
        body.page();
        return body.out.toString();
    }

    private void page() {
        for (String name : rules.pageSections()) {
            switch (name) {
                case "description": section(() -> {
                    description(page.documentation);
                    page.tables.forEach(this::table);
                }); break;
                case "contents": section(() -> contents()); break;
                case "picture": section(() -> {
                    if (page.image != null) {
                        out.append("<p>").append(dialect.image(page)).append("</p>");
                    }
                }); break;
                case "properties": section(() -> properties(page.properties)); break;
                case "notes": section(this::notes); break;
                case "related":
                    section(() -> links(t("related-views"), page.relatedViews));
                    page.labels.forEach((label, links) -> section(() -> {
                        KeywordRules.TagColour colour = rules.tag(label);
                        out.append("<h2>").append(colour == null ? Xml.escape(label) : dialect.tag(label, colour))
                                .append("</h2><ul>");
                        links.forEach(l -> out.append("<li>").append(link(l)).append("</li>"));
                        out.append("</ul>");
                    }));
                    break;
                case "elements":
                    for (Entry entry : page.entries) {
                        section(() -> {
                            int i = page.entries.indexOf(entry);
                            if (i == 0) {
                                out.append(heading(2, t("elements")));
                            }
                            String group = page.groups.get(entry.id);
                            if (group != null && (i == 0 || !group.equals(page.groups.get(page.entries.get(i - 1).id)))) {
                                out.append(heading(3, group));
                            }
                            entry(entry);
                        });
                    }
                    break;
                default: // the rules check the names
            }
        }
    }

    /** A table of a generated page: links, descriptions or property values per cell. */
    private void table(DocTable table) {
        out.append("<table><tbody><tr>");
        table.header.forEach(h -> out.append("<th>").append(Xml.escape(h)).append("</th>"));
        out.append("</tr>");
        for (List<DocTable.Cell> row : table.rows) {
            out.append("<tr>");
            for (DocTable.Cell cell : row) {
                out.append("<td>");
                if (!cell.links.isEmpty()) {
                    out.append(cell.links.stream().map(this::link).collect(java.util.stream.Collectors.joining(", ")));
                } else if (cell.description) {
                    description(cell.text);
                } else {
                    out.append(inlines(RichText.inlines(cell.text, rules)));
                }
                out.append("</td>");
            }
            out.append("</tr>");
        }
        out.append("</tbody></table>");
    }

    private void notes() {
        if (!page.notes.isEmpty() || !page.texts.isEmpty()) {
            out.append(heading(2, t("notes")));
            page.notes.forEach(this::description);
            for (ObjectText t : page.texts) {
                if (!t.name.isBlank()) {
                    out.append("<p><strong>").append(Xml.escape(t.name)).append("</strong></p>");
                }
                description(t.text);
            }
        }
    }

    /** The elements of the page; those in a group of the view under its name. */
    private void contents() {
        if (page.contents.isEmpty()) {
            return;
        }
        out.append(heading(2, t("contents")));
        if (page == doc.pages.get(0) && doc.nested()) { // the overview: the views under their folders
            outline(doc.outline, 3);
            return;
        }
        String group = null;
        for (int i = 0; i < page.contents.size(); i++) {
            Link l = page.contents.get(i);
            String in = page.groups.get(l.id);
            if (i == 0 || !Objects.equals(in, group)) {
                out.append(i == 0 ? "" : "</ul>").append(in != null ? heading(3, in) : "").append("<ul>");
            }
            group = in;
            out.append("<li>").append(link(l)).append("</li>");
        }
        out.append("</ul>");
    }

    /**
     * The listed pages of the outline, a folder as a heading with its description. A folder's own pages come before
     * its folders; pages after the folders at the top go under a heading of their own, never under the last folder.
     */
    private void outline(List<Doc.Node> nodes, int level) {
        boolean list = false;
        boolean afterFolder = false;
        for (Doc.Node node : nodes) {
            if (!listed(node)) {
                continue;
            }
            if (node.page != null) {
                if (afterFolder && level == 3) {
                    out.append(heading(level, t("other-pages")));
                    afterFolder = false;
                }
                out.append(list ? "" : "<ul>").append("<li>").append(link(new Link(node.page.id, node.page.title)))
                        .append("</li>");
                list = true;
                continue;
            }
            out.append(list ? "</ul>" : "").append(heading(Math.min(level, 6), node.folder.name));
            list = false;
            afterFolder = true;
            description(node.folder.documentation);
            outline(node.children, level + 1);
        }
        out.append(list ? "</ul>" : "");
    }

    /** Whether the overview lists the page, or a page of the folder. */
    private boolean listed(Doc.Node node) {
        return node.page != null ? node.page != page && page.contents.stream().anyMatch(l -> l.id.equals(node.page.id))
                : node.children.stream().anyMatch(this::listed);
    }

    private String t(String key) {
        return rules.texts().get(key);
    }

    /** A part of the page, with a line between it and what comes before. Writes nothing if the part is empty. */
    private String heading(int level, String text) {
        return "<h" + level + ">" + styled(rules.heading(level), Xml.escape(text)) + "</h" + level + ">";
    }

    /** Markup in a heading's style, or as it is if headings of its level look as usual. */
    private String styled(KeywordRules.TextStyle style, String markup) {
        return style == null ? markup : dialect.styled(markup, style);
    }

    private void section(Runnable content) {
        int start = out.length();
        out.append(start > 0 ? "<hr/>" : "");
        int body = out.length();
        content.run();
        if (out.length() == body) {
            out.setLength(start);
        }
    }

    private void entry(Entry entry) {
        String name = entry.headingUrl == null ? Xml.escape(entry.name) : external(entry.headingUrl, Xml.escape(entry.name));
        out.append(dialect.entryHeading(entry, styled(rules.heading(3), type(entry.type) + name)));
        List<Relation> inside = new ArrayList<>();
        Map<String, List<Relation>> outside = new LinkedHashMap<>();
        for (Relation r : entry.relations) {
            if (r.outside) {
                outside.computeIfAbsent(r.type, k -> new ArrayList<>()).add(r);
            } else {
                inside.add(r);
            }
        }
        for (String part : rules.entryParts()) {
            switch (part) {
                case "description":
                    if (entry.body.isEmpty()) {
                        description(entry.documentation);
                    } else {
                        parts(entry.body);
                    }
                    entry.facts.forEach(f -> out.append("<p>").append(Xml.escape(f)).append("</p>"));
                    for (Entry.Section section : entry.sections) {
                        out.append(heading(4, section.title));
                        description(section.text);
                    }
                    break;
                case "properties": properties(entry.properties); break;
                case "relations":
                    linkLine(t("part-of"), entry.partOf);
                    linkLine(t("contains"), entry.contains);
                    if (!entry.changes.isEmpty()) {
                        out.append(heading(4, t("changed-elements"))).append("<ul>");
                        entry.changes.forEach(l -> out.append("<li>").append(link(l)).append("</li>"));
                        out.append("</ul>");
                    }
                    relationTable(inside);
                    break;
                case "views": linkLine(t("views"), entry.views); break;
                case "workpackages": linkLine(t("work-packages"), entry.workPackages); break;
                case "other":
                    if (!outside.isEmpty()) {
                        out.append(heading(4, t("other-related-elements")));
                        outside.forEach(this::relationGroup);
                    }
                    break;
                default: // the rules check the names
            }
        }
    }

    /** A description with what its tokens write in their places. */
    private void parts(List<Entry.Part> parts) {
        for (Entry.Part p : parts) {
            switch (p.kind) {
                case TEXT: description(p.text); break;
                case PLAIN: out.append("<p>").append(Xml.escape(p.text)).append("</p>"); break;
                case SECTION:
                    out.append(heading(4, p.title));
                    description(p.text);
                    break;
                case SENTENCE:
                    out.append("<p>").append(Xml.escape(p.verb)).append(" ").append(haf(p.link));
                    if (p.note != null) {
                        out.append(" ").append(noteMarkup(p.note));
                    }
                    out.append("</p>");
                    greyDescription(p.text);
                    break;
                case NESTED:
                    out.append("<div style=\"margin-left: 20px; border-left: 2px solid rgb(223,225,230); padding-left: 10px;\">")
                            .append("<p><strong>").append(haf(p.link)).append("</strong></p>");
                    parts(p.parts);
                    out.append("</div>");
                    break;
                default:
            }
        }
    }

    /** «Process»Pay, the name a link to where it is documented. */
    private String haf(Link link) {
        String type = link.type == null ? "" : "\u00ab" + Xml.escape(link.type) + "\u00bb";
        return type + target(link.id, Xml.escape(link.label));
    }

    /** A relationship's description in grey: its text lines grey, a table in it as a table. */
    private void greyDescription(String text) {
        boolean was = grey;
        grey = true;
        try {
            description(text);
        } finally {
            grey = was;
        }
    }

    /** The other element's business description: in parentheses, grey italics. */
    private String noteMarkup(String note) {
        return "<em>" + grey("(" + inlines(RichText.inlines(note, rules)) + ")") + "</em>";
    }

    private void linkLine(String label, List<Link> links) {
        if (links.isEmpty()) {
            return;
        }
        out.append("<p>").append(Xml.escape(label)).append(": ");
        String separator = "";
        for (Link l : links) {
            out.append(separator).append(link(l));
            separator = ", ";
        }
        out.append("</p>");
    }

    private void relationTable(List<Relation> relations) {
        if (relations.isEmpty()) {
            return;
        }
        out.append("<table><tbody><tr><th>").append(Xml.escape(t("relationship"))).append("</th><th>")
                .append(Xml.escape(t("element"))).append("</th><th>").append(Xml.escape(t("description")))
                .append("</th></tr>");
        for (Relation r : relations) {
            out.append("<tr><td>").append(arrow(r));
            if (r.verb != null) { // the type stays, smaller, as the information it is
                out.append(Xml.escape(r.verb)).append("<br/>").append(grey(Xml.escape(r.type)));
            } else {
                out.append(Xml.escape(r.type));
            }
            if (!r.name.isEmpty()) {
                out.append("<br/>").append(Xml.escape(r.name));
            }
            out.append("</td><td>").append(others(r)).append("</td><td>");
            description(r.documentation);
            note(r);
            out.append("</td></tr>");
        }
        out.append("</tbody></table>");
    }

    private void relationGroup(String type, List<Relation> relations) {
        out.append("<p><strong>").append(Xml.escape(type)).append("</strong></p><ul>");
        for (Relation r : relations) {
            out.append("<li>").append(arrow(r)).append(r.verb == null ? "" : Xml.escape(r.verb) + " ").append(others(r));
            if (!r.name.isEmpty()) {
                out.append(" (").append(Xml.escape(r.name)).append(")");
            }
            description(r.documentation);
            note(r);
            out.append("</li>");
        }
        out.append("</ul>");
    }

    /** The element at the other end; through a junction, all of them, joined by its "and" or "or". */
    private String others(Relation r) {
        StringBuilder s = new StringBuilder();
        for (Link l : r.others) {
            s.append(s.length() == 0 ? "" : " " + Xml.escape(r.junction) + " ").append(link(l));
        }
        return s.toString();
    }

    /** The other element's business description, in parentheses, grey italics. */
    private void note(Relation r) {
        if (r.note != null) {
            out.append("<p>").append(noteMarkup(r.note)).append("</p>");
        }
    }

    /** {@code small} and an inline colour both survive in Confluence. */
    private static String grey(String markup) {
        return "<span style=\"color: rgb(107,119,140);\">" + markup + "</span>";
    }

    /** A link out of the export: a Confluence page or a Jira ticket. */
    private static String external(String url, String escapedLabel) {
        if (!url.matches("(?i)https?://\\S+")) { // never a javascript: or file: link
            return escapedLabel;
        }
        return "<a href=\"" + Xml.escape(url) + "\">" + escapedLabel + "</a>";
    }

    private static String arrow(Relation r) {
        return r.outgoing ? "→ " : "← ";
    }

    private void links(String heading, List<Link> links) {
        if (links.isEmpty()) {
            return;
        }
        out.append(heading(2, heading)).append("<ul>");
        links.forEach(l -> out.append("<li>").append(link(l)).append("</li>"));
        out.append("</ul>");
    }

    private String link(Link link) {
        return (link.type != null ? type(link.type) : "") + target(link.id, Xml.escape(link.label));
    }

    /**
     * A link to where an element or view is documented: its Confluence page or Jira ticket when it has one, else its
     * description in this export; the label alone when there is neither.
     */
    private String target(String id, String escapedLabel) {
        String url = doc.external.get(id);
        if (url != null) {
            return external(url, escapedLabel);
        }
        Doc.Location to = doc.locate(id);
        return to == null ? escapedLabel : dialect.link(to, page, escapedLabel);
    }

    /**
     * The type in front of an element's name. {@code small} and an inline colour both survive in Confluence. With
     * {@code type style haf}: {@code «Process»} right before the name, as HaF writes it.
     */
    private String type(String type) {
        if (rules.hafTypes()) {
            return "\u00ab" + Xml.escape(shortType(type)) + "\u00bb";
        }
        return "<small><span style=\"color: rgb(107,119,140);\">&lt;&lt;" + Xml.escape(type) + "&gt;&gt;</span></small> ";
    }

    /** "Business Process: Broker" to "Process": without the layer, the specialization and spaces. */
    static String shortType(String type) {
        String t = type.contains(":") ? type.substring(0, type.indexOf(':')) : type;
        return t.strip().replaceFirst("^(Business|Application|Technology) ", "").replace(" ", "");
    }

    private void properties(List<Property> properties) {
        if (properties.isEmpty()) {
            return;
        }
        out.append("<table><tbody><tr><th>").append(Xml.escape(t("property"))).append("</th><th>")
                .append(Xml.escape(t("value"))).append("</th></tr>");
        for (Property p : properties) {
            out.append("<tr><td>").append(Xml.escape(p.key)).append("</td><td>")
                    .append(inlines(RichText.inlines(p.value, rules))).append("</td></tr>");
        }
        out.append("</tbody></table>");
    }

    private void description(String text) {
        RichText.parse(text, rules).forEach(block -> out.append(block(block)));
    }

    /** The block, in a division indented by the nesting tags open where it stands. */
    private String block(RichText.Block block) {
        String html = content(block);
        return block.indent > 0 ? "<div style=\"margin-left: " + block.indent * INDENT + "px\">" + html + "</div>" : html;
    }

    private String content(RichText.Block block) {
        if (block.sql != null) {
            return dialect.sql(block.sql, rules.sql(), rules);
        }
        if (block.code != null) {
            return dialect.code(block.code, block.language);
        }
        StringBuilder s = new StringBuilder();
        if (block.items != null) {
            String list = block.numbered ? "ol" : "ul";
            s.append("<").append(list).append(">");
            block.items.forEach(item -> s.append("<li>").append(inlines(item)).append("</li>"));
            return s.append("</").append(list).append(">").toString();
        }
        if (block.rows != null) {
            int columns = block.rows.get(0).size();
            s.append("<table><tbody>");
            for (List<List<RichText.Inline>> row : block.rows) {
                String cell = row == block.rows.get(0) ? "th" : "td";
                s.append("<tr>");
                for (int i = 0; i < Math.max(columns, row.size()); i++) {
                    s.append("<").append(cell).append(">").append(i < row.size() ? inlines(row.get(i)) : "")
                            .append("</").append(cell).append(">");
                }
                s.append("</tr>");
            }
            return s.append("</tbody></table>").toString();
        }
        if (block.children.isEmpty()) {
            String text = inlines(block.inlines);
            s.append("<p>").append(grey ? grey(text) : text).append("</p>");
        } else {
            block.children.forEach(b -> s.append(block(b)));
        }
        return block.style == null ? s.toString() : dialect.panel(block.style, block.title, s.toString());
    }

    private String inlines(List<RichText.Inline> inlines) {
        StringBuilder s = new StringBuilder();
        for (RichText.Inline i : inlines) {
            switch (i.kind) {
                case BREAK: s.append("<br/>"); break;
                case TAG: s.append(dialect.tag(i.text, i.colour)); break;
                case URL:
                    String url = Xml.escape(i.text);
                    s.append("<a href=\"").append(url).append("\">").append(url).append("</a>");
                    break;
                case ASSET: s.append(asset(i)); break;
                case BAD_ASSET: s.append(unsupportedAsset(i)); break;
                case IMAGE_URL: s.append(dialect.remoteImage(Xml.escape(i.text))); break;
                case BOLD: s.append("<strong>").append(inlines(i.children)).append("</strong>"); break;
                case ITALIC: s.append("<em>").append(inlines(i.children)).append("</em>"); break;
                case LINK:
                    s.append("<a href=\"").append(Xml.escape(i.text)).append("\">").append(Xml.escape(i.label)).append("</a>");
                    break;
                case REF: s.append(reference(i)); break;
                case STYLED:
                case TABLE:
                case CONSTANT:
                case VARIABLE:
                    // inline styles, not CSS classes: they are what Confluence keeps in storage format
                    s.append(dialect.styled(Xml.escape(i.text), i.style));
                    break;
                default: s.append(Xml.escape(i.text));
            }
        }
        return s.toString();
    }

    /**
     * A link to an element or view, by its current name; its plain name when the export doesn't describe it. One the
     * model doesn't have stays visible in red, like a missing asset.
     */
    private String reference(RichText.Inline reference) {
        String name = reference.label != null ? reference.label : doc.names.get(reference.text);
        if (name == null) {
            return "<span style=\"color: rgb(191,38,0);\">[" + Xml.escape(t("missing-reference")) + ": " + Xml.escape(reference.text) + "]</span>";
        }
        return target(reference.text, Xml.escape(name));
    }

    /** A path outside the assets folder is no asset: it stays visible in red and is reported like a missing one. */
    private String unsupportedAsset(RichText.Inline reference) {
        doc.assets.unsupported(reference.text, page.title);
        return "<span style=\"color: rgb(191,38,0);\">[" + Xml.escape(t("unsupported-asset")) + ": " + Xml.escape(reference.text) + "]</span>";
    }

    /** A reference to a file that isn't there stays visible in red, so whoever reads the page notices. */
    private String asset(RichText.Inline reference) {
        Assets.Asset asset = doc.assets.find(reference.text, page.title);
        if (asset == null) {
            return "<span style=\"color: rgb(191,38,0);\">[" + Xml.escape(t("missing-asset")) + ": " + Xml.escape(reference.text) + "]</span>";
        }
        used.add(asset);
        return dialect.asset(asset, Xml.escape(reference.label != null ? reference.label : asset.name));
    }
}
