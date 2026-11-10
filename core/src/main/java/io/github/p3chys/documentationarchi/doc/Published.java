package io.github.p3chys.documentationarchi.doc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import io.github.p3chys.documentationarchi.text.Texts;

/**
 * What the published documentation of a model says, object by object: kept in a file next to the model, so that
 * everyone who works on the model knows what the readers have, whoever published it and on whichever computer.
 * <p>
 * Two things come from it: the model tree marks what the published documentation doesn't show yet (by the
 * fingerprint kept with each object), and an export can say what changed since ({@link #changesTo}), because the
 * texts themselves are kept: a view's or element's name, type, description, properties and relationships, as the
 * documentation showed them.
 * <p>
 * The file is text, one value per line and always in the same order, so two versions of it compare well:
 * <pre>
 * id-123	name	Customer
 * id-123	f:Description	Buys policies.
 * </pre>
 */
public final class Published {

    /** The name of the file, next to the model file. */
    public static final String FILE = "documentation.published";

    /** Pages that are not a view or the model: lists the export puts together itself. */
    private static final Set<String> OWN_PAGES = Set.of("elements", "work-packages", "labels", "changes");
    private static final String HEADER = "# documentationArchi: what the published documentation of this model shows."
            + " Written by \"Publish\"; do not edit.";

    /** One object as it was published. Text is kept of views and elements; of anything else only the fingerprint. */
    public static final class Item {
        public String kind = "";
        public String name = "";
        public String type = "";
        /** The plugin's fingerprint of the object when it was published, for the model tree marks; null for none. */
        public String print;
        /** When it was published, in milliseconds; 0 for unknown. */
        public long time;
        /** What the documentation said of it, by what it is ("Description", "Property owner"...), in page order. */
        public final Map<String, String> fields = new LinkedHashMap<>();

        boolean hasText() {
            return !kind.isEmpty();
        }
    }

    public enum Kind { ADDED, CHANGED, REMOVED }

    /** One field of a changed object; {@code before} or {@code after} is null when it wasn't or isn't there. */
    public static final class FieldChange {
        public final String field;
        public final String before;
        public final String after;

        FieldChange(String field, String before, String after) {
            this.field = field;
            this.before = before;
            this.after = after;
        }
    }

    /** A view or element that the documentation shows differently now, or no longer, or for the first time. */
    public static final class Change {
        public final Kind kind;
        public final String id;
        public final String name;
        public final String type;
        public final List<FieldChange> fields;

        Change(Kind kind, String id, String name, String type, List<FieldChange> fields) {
            this.kind = kind;
            this.id = id;
            this.name = name;
            this.type = type;
            this.fields = fields;
        }
    }

    private final Map<String, Item> items = new TreeMap<>();

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** The object as it was published, null if it never was. */
    public Item item(String id) {
        return items.get(id);
    }

    /** When anything was last published, in milliseconds; 0 for never. */
    public long lastTime() {
        return items.values().stream().mapToLong(i -> i.time).max().orElse(0);
    }

    // ---------------------------------------------------------------- from a document

    /**
     * Notes that this document was published now. Objects it doesn't show stay as they were published before, so
     * publishing a part (a work package) keeps the rest.
     *
     * @param prints the fingerprints of everything the document shows, by id
     * @param whole  the document is the whole model: what it doesn't show is no longer published
     */
    public void publish(Doc doc, Map<String, String> prints, long time, boolean whole) {
        Map<String, Item> shown = texts(doc);
        if (whole) {
            items.keySet().retainAll(union(shown.keySet(), prints.keySet()));
        }
        shown.forEach((id, item) -> {
            item.time = time;
            items.put(id, item);
        });
        prints.forEach((id, print) -> {
            Item item = items.computeIfAbsent(id, k -> new Item());
            item.print = print;
            item.time = time;
        });
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> all = new LinkedHashSet<>(a);
        all.addAll(b);
        return all;
    }

    /** What the document says of each view and element it describes. */
    static Map<String, Item> texts(Doc doc) {
        Map<String, Item> texts = new LinkedHashMap<>();
        for (DocPage page : doc.pages) {
            if (!OWN_PAGES.contains(page.id)) {
                Item item = new Item();
                item.kind = page.image != null ? "view" : "page";
                item.name = page.title;
                field(item, "Description", page.documentation);
                page.properties.forEach(p -> field(item, "Property " + p.key, p.value));
                field(item, "Notes", String.join("\n\n", page.notes));
                List<String> texts2 = new ArrayList<>();
                page.texts.forEach(t -> texts2.add(t.name.isEmpty() ? t.text : t.name + ": " + t.text));
                field(item, "Texts", String.join("\n\n", texts2));
                if (page.image != null) {
                    field(item, "Elements", labels(page.contents));
                }
                texts.put(page.id, item);
            }
            for (Entry entry : page.entries) {
                Item item = new Item();
                item.kind = "element";
                item.name = entry.name;
                item.type = entry.type;
                field(item, "Description", entry.documentation);
                entry.properties.forEach(p -> field(item, "Property " + p.key, p.value));
                for (Relation r : entry.relations) {
                    String others = labels(r.others);
                    String key = "Relationship " + (r.outgoing ? "→ " : "← ") + r.type
                            + (r.name == null || r.name.isEmpty() ? "" : " \"" + r.name + "\"") + " "
                            + (r.junction == null ? others : others.replace(", ", " " + r.junction + " "));
                    // a relationship without a description is still one: its being there is what changed
                    item.fields.merge(key, r.documentation == null || r.documentation.isBlank() ? "(no description)"
                            : r.documentation, (a, b) -> a + "\n\n" + b);
                }
                field(item, "Work packages", labels(entry.workPackages));
                field(item, "Changed elements", labels(entry.changes));
                texts.put(entry.id, item);
            }
        }
        return texts;
    }

    private static void field(Item item, String name, String value) {
        if (value != null && !value.isBlank()) {
            item.fields.merge(name, value.replace("\r\n", "\n").strip(), (a, b) -> a + "\n\n" + b);
        }
    }

    private static String labels(List<Link> links) {
        List<String> labels = new ArrayList<>();
        links.forEach(l -> labels.add(l.label));
        return String.join(", ", labels);
    }

    // ---------------------------------------------------------------- changes

    /**
     * What the document shows differently from what was published: the views and elements it adds, those it says
     * something else about (with each changed name, description, property and relationship, before and after), and,
     * for a document of the whole model, those it no longer has. In the order of the document.
     */
    public List<Change> changesTo(Doc doc, boolean whole) {
        List<Change> changes = new ArrayList<>();
        Map<String, Item> now = texts(doc);
        now.forEach((id, item) -> {
            Item before = items.get(id);
            if (before == null || !before.hasText()) {
                changes.add(new Change(Kind.ADDED, id, item.name, item.type, List.of()));
                return;
            }
            List<FieldChange> fields = new ArrayList<>();
            if (!before.name.equals(item.name)) {
                fields.add(new FieldChange("Name", before.name, item.name));
            }
            if (!before.type.equals(item.type)) {
                fields.add(new FieldChange("Type", before.type, item.type));
            }
            for (String field : union(before.fields.keySet(), item.fields.keySet())) {
                String was = before.fields.get(field);
                String is = item.fields.get(field);
                if (!Objects.equals(was, is)) {
                    fields.add(new FieldChange(field, was, is));
                }
            }
            if (!fields.isEmpty()) {
                changes.add(new Change(Kind.CHANGED, id, item.name, item.type, fields));
            }
        });
        if (whole) {
            items.forEach((id, item) -> {
                if (item.hasText() && !now.containsKey(id)) {
                    changes.add(new Change(Kind.REMOVED, id, item.name, item.type, List.of()));
                }
            });
        }
        return changes;
    }

    /**
     * The changes as a page of the document: a line that counts them, the list of the changed objects (each a link
     * to where it is described now) and, per object, what it said before and what it says after.
     *
     * @param since when the documentation was published, as the title and the first line show it
     */
    public static DocPage page(List<Change> changes, String since) {
        return page(changes, since, Texts.english()::get);
    }

    /** @param texts the fixed texts of the output by key, in the document's language (see {@link Texts}) */
    public static DocPage page(List<Change> changes, String since, Function<String, String> texts) {
        DocPage page = new DocPage("changes", texts.apply("changes-since").replace("{date}", since));
        long added = changes.stream().filter(c -> c.kind == Kind.ADDED).count();
        long changed = changes.stream().filter(c -> c.kind == Kind.CHANGED).count();
        long removed = changes.stream().filter(c -> c.kind == Kind.REMOVED).count();
        page.documentation = changes.isEmpty() ? texts.apply("changes-none").replace("{date}", since)
                : texts.apply("changes-summary").replace("{date}", since).replace("{added}", String.valueOf(added))
                        .replace("{changed}", String.valueOf(changed)).replace("{removed}", String.valueOf(removed));
        String before = " – " + texts.apply("before");
        String after = " – " + texts.apply("after");
        String gone = " (" + texts.apply("removed").toLowerCase(java.util.Locale.ROOT) + ")";
        String fresh = " (" + texts.apply("new") + ")";
        for (Change c : changes) {
            String what = texts.apply(c.kind == Kind.ADDED ? "added" : c.kind == Kind.CHANGED ? "changed" : "removed");
            String type = c.type.isEmpty() ? what : what + " " + c.type;
            page.contents.add(new Link(c.id, c.name, type));
            List<Property> rows = new ArrayList<>();
            for (FieldChange f : c.fields) {
                if (f.before != null) {
                    rows.add(new Property(f.field + (f.after == null ? gone : before), f.before));
                }
                if (f.after != null) {
                    rows.add(new Property(f.field + (f.before == null ? fresh : after), f.after));
                }
            }
            if (!rows.isEmpty()) {
                // an id of its own: the element's id leads to its description, which the list above links to
                page.entries.add(new Entry("changes-" + c.id, c.name, type, "", rows, List.of(), List.of()));
            }
        }
        return page;
    }

    /** The document with the page of changes after its first page, which lists it. */
    public static Doc with(Doc doc, DocPage changes) {
        List<DocPage> pages = new ArrayList<>(doc.pages);
        pages.add(1, changes);
        pages.get(0).contents.add(0, new Link(changes.id, changes.title));
        Doc result = new Doc(doc.title, pages, doc.assets);
        result.names.putAll(doc.names);
        return result;
    }

    // ---------------------------------------------------------------- file

    /** What the file says; nothing published if there is no file. */
    public static Published read(Path file) throws IOException {
        return Files.isRegularFile(file) ? parse(Files.readString(file, StandardCharsets.UTF_8)) : new Published();
    }

    /** Lines that are not understood are left out: a newer version may write more. */
    public static Published parse(String text) {
        Published published = new Published();
        for (String line : text.split("\n")) {
            String[] parts = line.endsWith("\r") ? line.substring(0, line.length() - 1).split("\t", 3) : line.split("\t", 3);
            if (line.startsWith("#") || parts.length < 3 || parts[0].isEmpty()) {
                continue;
            }
            Item item = published.items.computeIfAbsent(parts[0], k -> new Item());
            String value = unescape(parts[2]);
            switch (parts[1]) {
                case "kind": item.kind = value; break;
                case "name": item.name = value; break;
                case "type": item.type = value; break;
                case "print": item.print = value; break;
                case "time":
                    try {
                        item.time = Long.parseLong(value);
                    } catch (NumberFormatException e) {
                        // an edited file: the rest of the object still counts
                    }
                    break;
                default:
                    if (parts[1].startsWith("f:")) {
                        item.fields.put(unescape(parts[1].substring(2)), value);
                    }
            }
        }
        return published;
    }

    public String text() {
        StringBuilder s = new StringBuilder(HEADER).append('\n');
        items.forEach((id, item) -> {
            line(s, id, "kind", item.kind);
            line(s, id, "name", item.name);
            line(s, id, "type", item.type);
            line(s, id, "print", item.print);
            line(s, id, "time", item.time == 0 ? null : String.valueOf(item.time));
            item.fields.forEach((field, value) -> line(s, id, "f:" + escape(field), value));
        });
        return s.toString();
    }

    private static void line(StringBuilder s, String id, String key, String value) {
        if (value != null && !value.isEmpty()) {
            s.append(id).append('\t').append(key).append('\t').append(escape(value)).append('\n');
        }
    }

    public void write(Path file) throws IOException {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, text(), StandardCharsets.UTF_8);
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String unescape(String text) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);
                s.append(next == 'n' ? '\n' : next == 'r' ? '\r' : next == 't' ? '\t' : next);
            } else {
                s.append(c);
            }
        }
        return s.toString();
    }
}
