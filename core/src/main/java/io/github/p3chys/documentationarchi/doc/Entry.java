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

    /**
     * The description as printed, when its tokens ({@code ˝˝NAME˝˝}) write relationships into it; empty to print
     * {@link #documentation} as it is.
     */
    public final List<Part> body = new ArrayList<>();

    /** A piece of a description: text, or what a token writes in its place. */
    public static final class Part {
        public enum Kind {
            /** Description markup. */
            TEXT,
            /** A line printed as it is, such as a role. */
            PLAIN,
            /** "triggers «Process»Pay", with the relationship's description in grey and the other's business one. */
            SENTENCE,
            /** A heading and description markup under it, such as input parameters. */
            SECTION,
            /** A part composed into the element, described in full, indented. */
            NESTED
        }

        public final Kind kind;
        /** TEXT, SECTION: markup; PLAIN: the line; SENTENCE: the relationship's description, "" for none. */
        public final String text;
        /** SENTENCE: how the relationship reads. */
        public final String verb;
        /** SENTENCE: the other element; NESTED: the part. Its type is the short one: Process, not Business Process. */
        public final Link link;
        /** SENTENCE: the other element's business description, null for none. */
        public final String note;
        /** SECTION: the heading. */
        public final String title;
        /** NESTED: the part's own description. */
        public final List<Part> parts;

        private Part(Kind kind, String text, String verb, Link link, String note, String title, List<Part> parts) {
            this.kind = kind;
            this.text = text;
            this.verb = verb;
            this.link = link;
            this.note = note;
            this.title = title;
            this.parts = parts;
        }

        public static Part text(String markup) {
            return new Part(Kind.TEXT, markup, null, null, null, null, List.of());
        }

        public static Part plain(String line) {
            return new Part(Kind.PLAIN, line, null, null, null, null, List.of());
        }

        public static Part sentence(String verb, Link other, String description, String note) {
            return new Part(Kind.SENTENCE, description == null ? "" : description, verb, other, note, null, List.of());
        }

        public static Part section(String title, String markup) {
            return new Part(Kind.SECTION, markup == null ? "" : markup, null, null, null, title, List.of());
        }

        public static Part nested(Link part, List<Part> parts) {
            return new Part(Kind.NESTED, "", null, part, null, null, List.copyOf(parts));
        }
    }

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
