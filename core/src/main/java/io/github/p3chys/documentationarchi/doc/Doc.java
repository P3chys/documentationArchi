package io.github.p3chys.documentationarchi.doc;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** A whole export, independent of the output format. The first page is the entry page. */
public final class Doc {

    /** Where a link leads: a page, and an anchor on it for elements. */
    public static final class Location {
        public final DocPage page;
        public final String anchor;

        Location(DocPage page, String anchor) {
            this.page = page;
            this.anchor = anchor;
        }
    }

    /** A folder with what is in it, or a page, in the {@link #outline}. */
    public static final class Node {
        /** Null for a page. */
        public final DocFolder folder;
        /** Null for a folder. */
        public final DocPage page;
        /** What the folder holds, in the order of the pages. */
        public final List<Node> children = new ArrayList<>();

        Node(DocFolder folder, DocPage page) {
            this.folder = folder;
            this.page = page;
        }
    }

    public final String title;
    public final List<DocPage> pages;
    /**
     * The pages under their folders: each page in the folder it is in, a folder where its first page is. Without
     * folders, the pages as they are.
     */
    public final List<Node> outline = new ArrayList<>();
    /** The folders of {@link #outline}, each before those in it; empty when the pages are not in folders. */
    public final List<DocFolder> folders = new ArrayList<>();
    /** Files that descriptions refer to with [[asset:name]]. */
    public final Assets assets;
    /**
     * The current names of views and elements by id, for references to them ([[ref:id]]): those of the export, and
     * whatever else the reader adds, so a reference to something not exported still shows its name.
     */
    public final Map<String, String> names = new HashMap<>();
    private final Map<String, Location> locations = new HashMap<>();

    public Doc(String title, List<DocPage> pages) {
        this(title, pages, Assets.none());
    }

    public Doc(String title, List<DocPage> pages, Assets assets) {
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("A document needs at least one page");
        }
        this.title = title;
        this.pages = List.copyOf(pages);
        this.assets = assets;
        Set<String> slugs = new HashSet<>();
        for (DocPage page : pages) {
            page.slug = slugs.isEmpty() ? "index" : unique(slug(page.title), slugs);
            slugs.add(page.slug);
            assets.reserve(page.slug + ".png"); // the picture of the view, next to the assets in the export
            locations.put(page.id, new Location(page, null));
            names.put(page.id, page.title);
            for (Entry entry : page.entries) {
                locations.put(entry.id, new Location(page, entry.anchor()));
                names.put(entry.id, entry.name);
            }
        }
        Map<DocFolder, Node> nodes = new HashMap<>();
        for (DocPage page : pages) { // the entry page is never in a folder: it holds them
            node(page == pages.get(0) ? null : page.folder, nodes).add(new Node(null, page));
        }
        for (DocFolder folder : folders) {
            folder.slug = unique(slug(folder.name), slugs);
            slugs.add(folder.slug);
        }
    }

    /** Where a page in this folder goes: the folder's children, made on its first page; the top for none. */
    private List<Node> node(DocFolder folder, Map<DocFolder, Node> nodes) {
        if (folder == null) {
            return outline;
        }
        Node node = nodes.get(folder);
        if (node == null) {
            List<Node> in = node(folder.parent, nodes);
            node = new Node(folder, null);
            nodes.put(folder, node);
            folders.add(folder);
            in.add(node);
        }
        return node.children;
    }

    /** Whether the pages are in folders, see {@link #outline}. */
    public boolean nested() {
        return !folders.isEmpty();
    }

    /** Where this view or element is documented, null if it is not part of the export. */
    public Location locate(String id) {
        return locations.get(id);
    }

    /** A name safe for files on every platform and in URLs, whatever the view is called. */
    public static String slug(String name) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        slug = slug.length() > 60 ? slug.substring(0, 60).replaceAll("-$", "") : slug;
        return slug.isEmpty() ? "page" : slug;
    }

    private static String unique(String slug, Set<String> taken) {
        String candidate = slug;
        for (int i = 2; taken.contains(candidate); i++) {
            candidate = slug + "-" + i;
        }
        return candidate;
    }
}
