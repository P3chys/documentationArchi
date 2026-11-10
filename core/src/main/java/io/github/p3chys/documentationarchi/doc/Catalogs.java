package io.github.p3chys.documentationarchi.doc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import io.github.p3chys.documentationarchi.text.Catalog;
import io.github.p3chys.documentationarchi.text.Texts;

/** The pages of the catalog rules, made from the document: one row per described element of the types. */
public final class Catalogs {

    /** The pages, and a note per catalog that was left out because it has no rows. */
    public static final class Result {
        public final List<DocPage> pages = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();
    }

    private Catalogs() {
    }

    /**
     * @param folderIds the ids of the elements in a folder path (a catalog's {@code folder=}); throws
     *        IllegalArgumentException for a folder that does not exist
     */
    public static Result build(Doc doc, List<Catalog> catalogs, Texts texts, Function<String, Set<String>> folderIds) {
        Result result = new Result();
        int number = 0;
        for (Catalog catalog : catalogs) {
            number++;
            Set<String> inFolder = null;
            if (catalog.folder != null) {
                try {
                    inFolder = folderIds.apply(catalog.folder);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("The catalog \"" + catalog.title + "\": " + e.getMessage(), e);
                }
            }
            List<Entry> entries = new ArrayList<>();
            for (DocPage page : doc.pages) {
                for (Entry entry : page.entries) {
                    if (catalog.matches(entry.type) && (inFolder == null || inFolder.contains(entry.id))
                            && !entry.id.startsWith("changes-")) {
                        entries.add(entry);
                    }
                }
            }
            if (entries.isEmpty()) {
                result.notes.add("The catalog \"" + catalog.title + "\" has no described " + String.join(", ", catalog.types)
                        + " and was left out.");
                continue;
            }
            entries.sort(Comparator.comparing((Entry e) -> e.name.toLowerCase(Locale.ROOT)).thenComparing(e -> e.id));
            List<String> header = new ArrayList<>();
            for (String column : catalog.columns) {
                header.add(column.equalsIgnoreCase("name") ? texts.get("name")
                        : column.equalsIgnoreCase("description") ? texts.get("description") : column);
            }
            DocTable table = new DocTable(header);
            for (Entry entry : entries) {
                List<DocTable.Cell> row = new ArrayList<>();
                for (String column : catalog.columns) {
                    if (column.equalsIgnoreCase("name")) {
                        row.add(DocTable.Cell.links(List.of(new Link(entry.id, entry.name))));
                    } else if (column.equalsIgnoreCase("description")) {
                        row.add(DocTable.Cell.description(Generated.firstParagraph(entry.documentation)));
                    } else {
                        row.add(DocTable.Cell.value(property(entry, column)));
                    }
                }
                table.rows.add(row);
            }
            DocPage page = new DocPage("catalog-" + number, catalog.title);
            page.tables.add(table);
            result.pages.add(page);
        }
        return result;
    }

    private static String property(Entry entry, String key) {
        for (Property p : entry.properties) {
            if (p.key != null && p.key.strip().equalsIgnoreCase(key)) {
                return p.value;
            }
        }
        return "";
    }
}
