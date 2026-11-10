package io.github.p3chys.documentationarchi.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A table of the elements of one type, a page of whole-model exports:
 * <pre>
 * catalog "Applications" type=Application Component|Application Service folder=Views/Core columns=name,Owner,description
 * </pre>
 * {@code type} names the types as the export prints them; {@code *} at the end of one matches any ending, and several
 * are separated by {@code |}. {@code columns} are {@code name}, {@code description} (the first paragraph) and property
 * keys. Values may hold spaces; the title is quoted.
 */
public final class Catalog {

    private static final Pattern KEY = Pattern.compile("(?<!\\S)(type|folder|columns)=");

    public final String title;
    public final List<String> types;
    /** A folder path like {@code Views/Core}, null for the whole model. */
    public final String folder;
    public final List<String> columns;

    private Catalog(String title, List<String> types, String folder, List<String> columns) {
        this.title = title;
        this.types = types;
        this.folder = folder;
        this.columns = columns;
    }

    /** @param text what follows {@code catalog} @throws IllegalArgumentException for what does not fit */
    public static Catalog parse(String text) {
        String rest = text.strip();
        String usage = "expected: catalog \"<title>\" type=<type> [folder=<path>] columns=name,<property>,description";
        if (!rest.startsWith("\"") || rest.indexOf('"', 1) < 0) {
            throw new IllegalArgumentException(usage);
        }
        int end = rest.indexOf('"', 1);
        String title = rest.substring(1, end).strip();
        if (title.isEmpty()) {
            throw new IllegalArgumentException("the catalog needs a title");
        }
        rest = rest.substring(end + 1);
        Matcher m = KEY.matcher(rest);
        String type = null;
        String folder = null;
        String columns = null;
        List<String> seen = new ArrayList<>();
        int previous = -1;
        String key = null;
        while (true) {
            boolean found = m.find();
            if (previous >= 0) {
                String value = rest.substring(previous, found ? m.start() : rest.length()).strip();
                if (value.isEmpty()) {
                    throw new IllegalArgumentException("expected " + key + "=<value>");
                }
                if (key.equals("type")) {
                    type = value;
                } else if (key.equals("folder")) {
                    folder = value.replace("\"", "");
                } else {
                    columns = value;
                }
            } else if (!rest.substring(0, found ? m.start() : rest.length()).isBlank()) {
                throw new IllegalArgumentException(usage);
            }
            if (!found) {
                break;
            }
            key = m.group(1);
            if (seen.contains(key)) {
                throw new IllegalArgumentException("\"" + key + "\" is there twice");
            }
            seen.add(key);
            previous = m.end();
        }
        if (type == null || columns == null) {
            throw new IllegalArgumentException(usage);
        }
        List<String> types = new ArrayList<>();
        for (String t : type.replace("\"", "").split("\\|")) {
            if (!t.isBlank()) {
                types.add(t.strip());
            }
        }
        List<String> cols = new ArrayList<>();
        for (String c : columns.replace("\"", "").split(",")) {
            if (!c.isBlank()) {
                cols.add(c.strip());
            }
        }
        if (types.isEmpty() || cols.isEmpty()) {
            throw new IllegalArgumentException(usage);
        }
        return new Catalog(title, types, folder, cols);
    }

    /** Whether an element type, as the export prints it, is one of the catalog's; case does not matter. */
    public boolean matches(String type) {
        String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
        for (String wanted : types) {
            String w = wanted.toLowerCase(Locale.ROOT);
            if (w.endsWith("*") ? t.startsWith(w.substring(0, w.length() - 1)) : t.equals(w)) {
                return true;
            }
        }
        return false;
    }

    /** The catalog as text for {@link #parse}: the title and the settings. */
    public String text() {
        return "\"" + title + "\" type=" + String.join("|", types) + (folder == null ? "" : " folder=" + folder)
                + " columns=" + String.join(",", columns);
    }
}
