package io.github.p3chys.documentationarchi.doc;

/**
 * A folder of views below the model's Views folder. The outputs nest the pages of its views under it, as the model
 * tree does: the overview, the PDF's bookmarks and contents, the HTML sidebar and the Confluence page tree.
 */
public final class DocFolder {

    public final String id;
    public final String name;
    /** The folder it is in; null directly in the Views folder. */
    public final DocFolder parent;
    public String documentation = "";

    /** Name for its Confluence page file, unique among the pages. Set by {@link Doc}. */
    String slug;

    public DocFolder(String id, String name, DocFolder parent) {
        this.id = id;
        this.name = name;
        this.parent = parent;
    }

    public String slug() {
        return slug;
    }

    /** How deep it is: 0 directly in the Views folder. */
    public int depth() {
        return parent == null ? 0 : parent.depth() + 1;
    }
}
