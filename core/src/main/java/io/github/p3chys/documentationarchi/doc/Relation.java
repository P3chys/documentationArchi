package io.github.p3chys.documentationarchi.doc;

/** One relationship of an element, seen from that element. */
public final class Relation {

    public final boolean outgoing;
    public final String type;
    public final String name;
    public final String documentation;
    public final Link other;
    /** The other element is not in the view the description is on. */
    public final boolean outside;

    public Relation(boolean outgoing, String type, String name, String documentation, Link other, boolean outside) {
        this.outgoing = outgoing;
        this.type = type;
        this.name = name;
        this.documentation = documentation;
        this.other = other;
        this.outside = outside;
    }
}
