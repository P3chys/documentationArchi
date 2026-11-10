package io.github.p3chys.documentationarchi.doc;

import java.util.List;

/** One relationship of an element, seen from that element. */
public final class Relation {

    public final boolean outgoing;
    public final String type;
    public final String name;
    public final String documentation;
    /** The first of {@link #others}. */
    public final Link other;
    /**
     * The elements at the other end. More than one when the relationship goes through a junction to all of them:
     * {@link #junction} says how they combine.
     */
    public final List<Link> others;
    /** "and" or "or" for a relationship through a junction, null for a direct one. */
    public final String junction;
    /** The other element is not in the view the description is on. */
    public final boolean outside;

    public Relation(boolean outgoing, String type, String name, String documentation, Link other, boolean outside) {
        this(outgoing, type, name, documentation, List.of(other), null, outside);
    }

    public Relation(boolean outgoing, String type, String name, String documentation, List<Link> others,
            String junction, boolean outside) {
        if (others.isEmpty()) {
            throw new IllegalArgumentException("A relationship needs an element at the other end");
        }
        this.outgoing = outgoing;
        this.type = type;
        this.name = name;
        this.documentation = documentation;
        this.other = others.get(0);
        this.others = List.copyOf(others);
        this.junction = junction;
        this.outside = outside;
    }
}
