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
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.doc.Link;
import io.github.p3chys.documentationarchi.doc.ObjectText;
import io.github.p3chys.documentationarchi.doc.Property;
import io.github.p3chys.documentationarchi.doc.Relation;
import io.github.p3chys.documentationarchi.text.KeywordRules;
import io.github.p3chys.documentationarchi.text.RichText;

/** The content of one page, the same for every output apart from what its {@link Dialect} decides. */
final class PageBody {

    private final Doc doc;
    private final DocPage page;
    private final Dialect dialect;
    private final KeywordRules rules;
    private final Set<Assets.Asset> used;
    private final StringBuilder out = new StringBuilder();

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
        section(() -> description(page.documentation));
        section(() -> contents());
        section(() -> {
            if (page.image != null) {
                out.append("<p>").append(dialect.image(page)).append("</p>");
            }
        });
        section(() -> properties(page.properties));
        section(() -> {
            if (!page.notes.isEmpty() || !page.texts.isEmpty()) {
                out.append(heading(2, "Notes"));
                page.notes.forEach(this::description);
                for (ObjectText t : page.texts) {
                    if (!t.name.isBlank()) {
                        out.append("<p><strong>").append(Xml.escape(t.name)).append("</strong></p>");
                    }
                    description(t.text);
                }
            }
        });
        section(() -> links("Related views", page.relatedViews));
        for (Entry entry : page.entries) {
            section(() -> {
                int i = page.entries.indexOf(entry);
                if (i == 0) {
                    out.append(heading(2, "Elements"));
                }
                String group = page.groups.get(entry.id);
                if (group != null && (i == 0 || !group.equals(page.groups.get(page.entries.get(i - 1).id)))) {
                    out.append(heading(3, group));
                }
                entry(entry);
            });
        }
    }

    /** The elements of the page; those in a group of the view under its name. */
    private void contents() {
        if (page.contents.isEmpty()) {
            return;
        }
        out.append(heading(2, "Contents"));
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
        out.append(dialect.entryHeading(entry, styled(rules.heading(3), type(entry.type) + Xml.escape(entry.name))));
        description(entry.documentation);
        properties(entry.properties);
        linkLine("Part of", entry.partOf);
        linkLine("Contains", entry.contains);
        List<Relation> inside = new ArrayList<>();
        Map<String, List<Relation>> outside = new LinkedHashMap<>();
        for (Relation r : entry.relations) {
            if (r.outside) {
                outside.computeIfAbsent(r.type, k -> new ArrayList<>()).add(r);
            } else {
                inside.add(r);
            }
        }
        if (!entry.changes.isEmpty()) {
            out.append(heading(4, "Changed elements")).append("<ul>");
            entry.changes.forEach(l -> out.append("<li>").append(link(l)).append("</li>"));
            out.append("</ul>");
        }
        relationTable(inside);
        linkLine("Views", entry.views);
        linkLine("Work packages", entry.workPackages);
        if (!outside.isEmpty()) {
            out.append(heading(4, "Other related elements"));
            outside.forEach(this::relationGroup);
        }
    }

    private void linkLine(String label, List<Link> links) {
        if (links.isEmpty()) {
            return;
        }
        out.append("<p>").append(label).append(": ");
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
        out.append("<table><tbody><tr><th>Relationship</th><th>Element</th><th>Description</th></tr>");
        for (Relation r : relations) {
            out.append("<tr><td>").append(arrow(r)).append(Xml.escape(r.type));
            if (!r.name.isEmpty()) {
                out.append("<br/>").append(Xml.escape(r.name));
            }
            out.append("</td><td>").append(others(r)).append("</td><td>");
            description(r.documentation);
            out.append("</td></tr>");
        }
        out.append("</tbody></table>");
    }

    private void relationGroup(String type, List<Relation> relations) {
        out.append("<p><strong>").append(Xml.escape(type)).append("</strong></p><ul>");
        for (Relation r : relations) {
            out.append("<li>").append(arrow(r)).append(others(r));
            if (!r.name.isEmpty()) {
                out.append(" (").append(Xml.escape(r.name)).append(")");
            }
            description(r.documentation);
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
        Doc.Location to = doc.locate(link.id);
        String label = Xml.escape(link.label);
        return (link.type != null ? type(link.type) : "") + (to == null ? label : dialect.link(to, page, label));
    }

    /** The type in front of an element's name. {@code small} and an inline colour both survive in Confluence. */
    private static String type(String type) {
        return "<small><span style=\"color: rgb(107,119,140);\">&lt;&lt;" + Xml.escape(type) + "&gt;&gt;</span></small> ";
    }

    private void properties(List<Property> properties) {
        if (properties.isEmpty()) {
            return;
        }
        out.append("<table><tbody><tr><th>Property</th><th>Value</th></tr>");
        for (Property p : properties) {
            out.append("<tr><td>").append(Xml.escape(p.key)).append("</td><td>")
                    .append(inlines(RichText.inlines(p.value, rules))).append("</td></tr>");
        }
        out.append("</tbody></table>");
    }

    private void description(String text) {
        for (RichText.Block block : RichText.parse(text, rules)) {
            String body = "<p>" + inlines(block.inlines) + "</p>";
            out.append(block.sql != null ? dialect.sql(block.sql, rules.sql())
                    : block.style == null ? body : dialect.panel(block.style, block.title, body));
        }
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

    /** A reference to a file that isn't there stays visible in red, so whoever reads the page notices. */
    private String asset(RichText.Inline reference) {
        Assets.Asset asset = doc.assets.find(reference.text, page.title);
        if (asset == null) {
            return "<span style=\"color: rgb(191,38,0);\">[missing asset: " + Xml.escape(reference.text) + "]</span>";
        }
        used.add(asset);
        return dialect.asset(asset, Xml.escape(reference.label != null ? reference.label : asset.name));
    }
}
