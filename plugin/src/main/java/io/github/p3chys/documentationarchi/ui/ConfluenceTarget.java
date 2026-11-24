package io.github.p3chys.documentationarchi.ui;

/** Where in Confluence an export is uploaded: under a page of a space, or at the top of the space. */
final class ConfluenceTarget {

    final String spaceKey;
    /** The parent page, null for the top of the space. */
    final String pageId;
    /** What the user sees: the space and the titles of the pages down to the parent, "Architecture / Services". */
    final String path;

    ConfluenceTarget(String spaceKey, String pageId, String path) {
        this.spaceKey = spaceKey;
        this.pageId = pageId;
        this.path = path;
    }
}
