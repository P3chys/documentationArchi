package io.github.p3chys.documentationarchi.doc;

/** A reference to a view or element by its Archi id. Shown as plain text when the target is not in the export. */
public final class Link {

    public final String id;
    public final String label;
    /** Type of the element, shown in front of its name. Null for views and pages. */
    public final String type;

    public Link(String id, String label) {
        this(id, label, null);
    }

    public Link(String id, String label, String type) {
        this.id = id;
        this.label = label;
        this.type = type;
    }
}
