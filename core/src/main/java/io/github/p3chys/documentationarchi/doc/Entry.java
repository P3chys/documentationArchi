package io.github.p3chys.documentationarchi.doc;

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

    public Entry(String id, String name, String type, String documentation, List<Property> properties,
            List<Relation> relations, List<Link> views) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.documentation = documentation;
        this.properties = properties;
        this.relations = relations;
        this.views = views;
    }

    public String anchor() {
        return "e-" + id;
    }
}
