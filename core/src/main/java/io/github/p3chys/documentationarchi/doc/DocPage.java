package io.github.p3chys.documentationarchi.doc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One page of the export: the model overview, a view, or a list of elements without a view. */
public final class DocPage {

    public final String id;
    public final String title;
    public String documentation = "";
    public List<Property> properties = new ArrayList<>();
    /** PNG of the view, null for pages without a diagram. */
    public byte[] image;
    /** Width to show the image at, in CSS pixels. */
    public int imageWidth;
    /** Height of the image at {@link #imageWidth}, in CSS pixels; 0 when unknown. */
    public int imageHeight;
    public final List<String> notes = new ArrayList<>();
    /** On a sketch or canvas view: the texts of its stickies, actors, blocks and groups, listed with the notes. */
    public final List<ObjectText> texts = new ArrayList<>();
    /** Views this view refers to with a diagram reference. */
    public final List<Link> relatedViews = new ArrayList<>();
    /** The elements described on this page. */
    public final List<Entry> entries = new ArrayList<>();
    /** What the page holds: the pages of the export on the overview, every element of the view on a view's page. */
    public final List<Link> contents = new ArrayList<>();
    /**
     * On a view's page: the name of the visual group an element sits in, by the element's id. The contents and the
     * entries list the elements of a group together, and the page puts the group's name above them.
     */
    public final Map<String, String> groups = new HashMap<>();

    /** File name without extension, unique in the export. Set by {@link Doc}. */
    String slug;

    public DocPage(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public String slug() {
        return slug;
    }
}
