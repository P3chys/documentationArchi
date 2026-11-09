package io.github.p3chys.documentationarchi.ui;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import org.eclipse.jface.preference.IPreferenceStore;

import io.github.p3chys.documentationarchi.render.Exporter.Format;
import io.github.p3chys.documentationarchi.text.KeywordRules;

/** Settings, kept in Archi's preferences. */
final class Prefs {

    private static final String KEYWORDS = "keywords";
    private static final String FOLDER = "folder";
    private static final String FORMAT = "format.";
    private static final String MARKS = "marks";

    private Prefs() {
    }

    private static IPreferenceStore store() {
        IPreferenceStore store = Activator.get().getPreferenceStore();
        store.setDefault(KEYWORDS, KeywordRules.DEFAULTS);
        store.setDefault(FOLDER, System.getProperty("user.home"));
        store.setDefault(MARKS, true);
        for (Format format : Format.values()) {
            store.setDefault(FORMAT + format.name().toLowerCase(Locale.ROOT), true);
        }
        return store;
    }

    static String keywords() {
        return store().getString(KEYWORDS);
    }

    static String defaultKeywords() {
        return store().getDefaultString(KEYWORDS);
    }

    static void keywords(String text) {
        store().setValue(KEYWORDS, text);
    }

    static KeywordRules rules() {
        return KeywordRules.parse(keywords());
    }

    static String folder() {
        return store().getString(FOLDER);
    }

    static void folder(String folder) {
        store().setValue(FOLDER, folder);
    }

    /** Whether the model tree marks what no export shows yet ({@link ExportMarks}). */
    static boolean marks() {
        return store().getBoolean(MARKS);
    }

    static void marks(boolean show) {
        store().setValue(MARKS, show);
    }

    static Set<Format> formats() {
        Set<Format> formats = EnumSet.noneOf(Format.class);
        for (Format format : Format.values()) {
            if (store().getBoolean(FORMAT + format.name().toLowerCase(Locale.ROOT))) {
                formats.add(format);
            }
        }
        return formats;
    }

    static void formats(Set<Format> formats) {
        for (Format format : Format.values()) {
            store().setValue(FORMAT + format.name().toLowerCase(Locale.ROOT), formats.contains(format));
        }
    }
}
