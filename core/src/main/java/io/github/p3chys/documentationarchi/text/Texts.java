package io.github.p3chys.documentationarchi.text;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The fixed texts of the output (headings, table headers, notices), in one place: English and Czech are built in,
 * and a {@code text <key> = <replacement>} rule overrides any single one. Names of element and relationship types
 * are not here; they stay as Archi calls them.
 */
public final class Texts {

    /** The built-in languages, the first one is the default. */
    public static final List<String> LANGUAGES = List.of("en", "cs");

    private static final Map<String, String[]> BUILT_IN = new LinkedHashMap<>();

    static {
        add("contents", "Contents", "Obsah");
        add("elements", "Elements", "Prvky");
        add("notes", "Notes", "Poznámky");
        add("related-views", "Related views", "Související pohledy");
        add("other-related-elements", "Other related elements", "Další související prvky");
        add("changed-elements", "Changed elements", "Měněné prvky");
        add("work-packages", "Work packages", "Pracovní balíčky");
        add("property", "Property", "Vlastnost");
        add("value", "Value", "Hodnota");
        add("relationship", "Relationship", "Vztah");
        add("element", "Element", "Prvek");
        add("description", "Description", "Popis");
        add("views", "Views", "Pohledy");
        add("part-of", "Part of", "Součást");
        add("contains", "Contains", "Obsahuje");
        add("labels", "Labels", "Štítky");
        add("group", "Group", "Skupina");
        add("elements-not-in-any-view", "Elements not in any view", "Prvky mimo pohledy");
        add("selected-elements", "Selected elements", "Vybrané prvky");
        add("generated", "Generated {time} from {model}", "Vygenerováno {time} z {model}");
        add("missing-asset", "missing asset", "chybějící příloha");
        add("missing-reference", "missing reference", "chybějící odkaz");
        // the page of changes since the published documentation
        add("changes-since", "Changes since {date}", "Změny od {date}");
        add("changes-summary", "Since the documentation published on {date}: {added} added, {changed} changed, {removed} removed.",
                "Od dokumentace zveřejněné {date}: přidáno {added}, změněno {changed}, odebráno {removed}.");
        add("changes-none", "Nothing changed since the documentation published on {date}.",
                "Od dokumentace zveřejněné {date} se nic nezměnilo.");
        add("added", "Added", "Přidáno");
        add("changed", "Changed", "Změněno");
        add("removed", "Removed", "Odebráno");
        add("before", "before", "před");
        add("after", "after", "po");
        add("new", "new", "nové");
    }

    private final int language;
    private final Map<String, String> overrides;

    private static void add(String key, String... texts) {
        BUILT_IN.put(key, texts);
    }

    /** @param language one of {@link #LANGUAGES} @param overrides replacements by key */
    public Texts(String language, Map<String, String> overrides) {
        this.language = Math.max(0, LANGUAGES.indexOf(language));
        this.overrides = Collections.unmodifiableMap(overrides);
    }

    /** English, nothing replaced. */
    public static Texts english() {
        return new Texts("en", Map.of());
    }

    /** The text for a key. @throws IllegalArgumentException for a key that does not exist */
    public String get(String key) {
        String own = overrides.get(key);
        if (own != null) {
            return own;
        }
        String[] texts = BUILT_IN.get(key);
        if (texts == null) {
            throw new IllegalArgumentException("unknown text \"" + key + "\"");
        }
        return texts[language];
    }

    /** Whether the key names a text. */
    public static boolean exists(String key) {
        return BUILT_IN.containsKey(key.toLowerCase(Locale.ROOT));
    }

    /** All keys, for the help and the documentation. */
    public static List<String> keys() {
        return List.copyOf(BUILT_IN.keySet());
    }

    /** The built-in text of a key in a language, e.g. for showing the user what a rule replaces. */
    public static String builtIn(String key, String language) {
        return BUILT_IN.get(key)[Math.max(0, LANGUAGES.indexOf(language))];
    }
}
