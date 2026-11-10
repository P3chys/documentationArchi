package io.github.p3chys.documentationarchi.doc;

import java.util.ArrayList;
import java.util.List;

/** Pages made from a finished {@link Doc}, such as catalogs and indexes, added the way the page of changes is. */
public final class Generated {

    private Generated() {
    }

    /** The document with these pages after its first page, which lists them first. */
    public static Doc with(Doc doc, List<DocPage> generated) {
        if (generated.isEmpty()) {
            return doc;
        }
        List<DocPage> pages = new ArrayList<>(doc.pages);
        pages.addAll(1, generated);
        for (int i = generated.size() - 1; i >= 0; i--) {
            pages.get(0).contents.add(0, new Link(generated.get(i).id, generated.get(i).title));
        }
        Doc result = new Doc(doc.title, pages, doc.assets);
        result.names.putAll(doc.names);
        result.external.putAll(doc.external);
        result.notes.addAll(doc.notes);
        return result;
    }

    /** The first paragraph of a description: up to the first blank line. */
    public static String firstParagraph(String text) {
        if (text == null) {
            return "";
        }
        return text.strip().split("\\R\\s*\\R", 2)[0].strip();
    }
}
