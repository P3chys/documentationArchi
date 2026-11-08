package io.github.p3chys.documentationarchi.doc;

import java.text.Normalizer;
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

    public final String title;
    public final List<DocPage> pages;
    private final Map<String, Location> locations = new HashMap<>();

    public Doc(String title, List<DocPage> pages) {
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("A document needs at least one page");
        }
        this.title = title;
        this.pages = List.copyOf(pages);
        Set<String> slugs = new HashSet<>();
        for (DocPage page : pages) {
            page.slug = slugs.isEmpty() ? "index" : unique(slug(page.title), slugs);
            slugs.add(page.slug);
            locations.put(page.id, new Location(page, null));
            for (Entry entry : page.entries) {
                locations.put(entry.id, new Location(page, entry.anchor()));
            }
        }
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
