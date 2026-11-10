package io.github.p3chys.documentationarchi.doc;

import java.util.ArrayList;
import java.util.List;

/** The full description of one element. Each element has exactly one, on the first page that shows it. */
public final class Entry {

    public final String id;
    public final String name;
    public final String type;
    public final String documentation;
    public final List<Property> properties;
    public final List<Relation> relations;
    public final List<Link> views;
    /** The work packages (Jira tickets) that change this element. */
    public final List<Link> workPackages;
    /** For a work package: the elements it changes. */
    public final List<Link> changes;
    /** Elements this one is drawn inside on a view, with no relationship between the two. */
    public final List<Link> partOf = new ArrayList<>();
    /** Elements drawn inside this one on a view, with no relationship between the two. */
    public final List<Link> contains = new ArrayList<>();
    /** Lines printed after the description, such as which role may use the element. */
    public final List<String> facts = new ArrayList<>();
    /** Parts with a heading after the description, such as its input parameters. */
    public final List<Section> sections = new ArrayList<>();
    /** Where the heading links to, such as a work package's ticket; null for no link. */
    public String headingUrl;

    /** A part of the description with a heading of its own; its text is formatted like a description. */
    public static final class Section {
        public final String title;
        public final String text;

        public Section(String title, String text) {
            this.title = title;
            this.text = text;
        }
    }

    public Entry(String id, String name, String type, String documentation, List<Property> properties,
            List<Relation> relations, List<Link> views) {
        this(id, name, type, documentation, properties, relations, views, List.of(), List.of());
    }

    public Entry(String id, String name, String type, String documentation, List<Property> properties,
            List<Relation> relations, List<Link> views, List<Link> workPackages, List<Link> changes) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.documentation = documentation;
        this.properties = properties;
        this.relations = relations;
        this.views = views;
        this.workPackages = workPackages;
        this.changes = changes;
    }

    public String anchor() {
        return "e-" + id;
    }
}
