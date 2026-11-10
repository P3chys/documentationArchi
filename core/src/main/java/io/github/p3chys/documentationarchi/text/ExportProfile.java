package io.github.p3chys.documentationarchi.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A named way to set up an export, kept in the rules text so everyone and the command line export the same:
 * <pre>
 * profile developers folders=Views/Sales,Views/Claims formats=html,pdf exclude=#internal hide=cost* language=en output=docs/dev publish
 * </pre>
 * Names, and values with spaces, are quoted. Empty lists mean "not set".
 */
public final class ExportProfile {

    /** The formats a profile may name, as the export knows them. */
    public static final List<String> FORMATS = List.of("html", "pdf", "confluence");

    public final String name;
    public final List<String> folders;
    public final List<String> views;
    public final List<String> workPackages;
    public final List<String> formats;
    public final List<String> exclude;
    public final List<String> hide;
    /** One of {@link Texts#LANGUAGES}, null for the rules' own. */
    public final String language;
    /** The export folder, relative to the model file's folder unless absolute; null for not set. */
    public final String output;
    public final boolean publish;
    /** The line of the rules text the profile is on, 0 when unknown. */
    public final int line;

    private ExportProfile(String name, List<String> folders, List<String> views, List<String> workPackages,
            List<String> formats, List<String> exclude, List<String> hide, String language, String output,
            boolean publish, int line) {
        this.name = name;
        this.folders = folders;
        this.views = views;
        this.workPackages = workPackages;
        this.formats = formats;
        this.exclude = exclude;
        this.hide = hide;
        this.language = language;
        this.output = output;
        this.publish = publish;
        this.line = line;
    }

    /**
     * @param text what follows {@code profile}: the name and the settings
     * @throws IllegalArgumentException for an unknown or repeated setting, or a value that does not fit
     */
    public static ExportProfile parse(String text) {
        List<String> tokens = tokens(text);
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("expected: profile <name> <setting>...");
        }
        String name = unquote(tokens.get(0)).strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("the profile needs a name");
        }
        List<String> folders = new ArrayList<>();
        List<String> views = new ArrayList<>();
        List<String> workPackages = new ArrayList<>();
        List<String> formats = new ArrayList<>();
        List<String> exclude = new ArrayList<>();
        List<String> hide = new ArrayList<>();
        String language = null;
        String output = null;
        boolean publish = false;
        List<String> seen = new ArrayList<>();
        for (String token : tokens.subList(1, tokens.size())) {
            int equals = token.indexOf('=');
            String setting = (equals < 0 ? token : token.substring(0, equals)).toLowerCase(Locale.ROOT);
            String value = equals < 0 ? null : token.substring(equals + 1);
            if (seen.contains(setting)) {
                throw new IllegalArgumentException("\"" + setting + "\" is there twice");
            }
            seen.add(setting);
            if (setting.equals("publish")) {
                if (value != null) {
                    throw new IllegalArgumentException("publish takes no value");
                }
                publish = true;
                continue;
            }
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException("expected " + setting + "=<value>");
            }
            switch (setting) {
                case "folders": folders = items(value); break;
                case "views": views = items(value); break;
                case "workpackages": workPackages = items(value); break;
                case "formats":
                    for (String f : items(value)) {
                        String format = f.toLowerCase(Locale.ROOT);
                        if (!FORMATS.contains(format)) {
                            throw new IllegalArgumentException("unknown format \"" + f + "\", use "
                                    + String.join(", ", FORMATS));
                        }
                        if (!formats.contains(format)) {
                            formats.add(format);
                        }
                    }
                    break;
                case "exclude":
                    for (String label : items(value)) {
                        exclude.add(label.startsWith("#") ? label.substring(1) : label);
                    }
                    if (exclude.stream().anyMatch(l -> l.isEmpty() || l.matches(".*\\s.*"))) {
                        throw new IllegalArgumentException("a label must be one word");
                    }
                    break;
                case "hide": hide = items(value); break;
                case "language":
                    language = unquote(value).strip().toLowerCase(Locale.ROOT);
                    if (!Texts.LANGUAGES.contains(language)) {
                        throw new IllegalArgumentException("unknown language \"" + language + "\", use "
                                + String.join(" or ", Texts.LANGUAGES));
                    }
                    break;
                case "output": output = unquote(value).strip(); break;
                default:
                    throw new IllegalArgumentException("unknown setting \"" + setting + "\", use folders, views, "
                            + "workpackages, formats, exclude, hide, language, output or publish");
            }
        }
        if (!workPackages.isEmpty() && !(folders.isEmpty() && views.isEmpty())) {
            throw new IllegalArgumentException("workpackages cannot be combined with folders or views");
        }
        return new ExportProfile(name, folders, views, workPackages, formats, exclude, hide, language, output, publish, 0);
    }

    /** The same profile, noted to be on this line of the rules text. */
    ExportProfile atLine(int number) {
        return new ExportProfile(name, folders, views, workPackages, formats, exclude, hide, language, output, publish,
                number);
    }

    /** Whether the profile names what to export; if not, it exports the whole model. */
    public boolean hasScope() {
        return !(folders.isEmpty() && views.isEmpty() && workPackages.isEmpty());
    }

    /** The profile as text for {@link #parse}: the name and the settings. */
    public String text() {
        List<String> parts = new ArrayList<>();
        parts.add(quote(name));
        add(parts, "folders", folders);
        add(parts, "views", views);
        add(parts, "workpackages", workPackages);
        add(parts, "formats", formats);
        add(parts, "exclude", exclude.stream().map(l -> "#" + l).collect(java.util.stream.Collectors.toList()));
        add(parts, "hide", hide);
        if (language != null) {
            parts.add("language=" + language);
        }
        if (output != null) {
            parts.add("output=" + quote(output));
        }
        if (publish) {
            parts.add("publish");
        }
        return String.join(" ", parts);
    }

    private static void add(List<String> parts, String setting, List<String> values) {
        if (!values.isEmpty()) {
            parts.add(setting + "=" + String.join(",", values.stream().map(ExportProfile::quote)
                    .collect(java.util.stream.Collectors.toList())));
        }
    }

    private static String quote(String text) {
        return text.matches(".*[\\s,\"].*") ? "\"" + text.replace("\"", "") + "\"" : text;
    }

    private static String unquote(String text) {
        return text.replace("\"", "");
    }

    /** Splits at spaces outside quotes; the quotes stay for {@link #items} and {@link #unquote}. */
    private static List<String> tokens(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (char c : text.strip().toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
            }
            if (Character.isWhitespace(c) && !quoted) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("a quote is not closed");
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    /** Splits at commas outside quotes. */
    private static List<String> items(String value) {
        List<String> items = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (char c : value.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                add(items, current);
            } else {
                current.append(c);
            }
        }
        add(items, current);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("the list is empty");
        }
        return items;
    }

    private static void add(List<String> items, StringBuilder current) {
        String item = current.toString().strip();
        current.setLength(0);
        if (!item.isEmpty()) {
            items.add(item);
        }
    }
}
