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
    private static final String IGNORE_LAYOUT = "marks.ignoreLayout";
    private static final String SHOW_VIEWS = "entry.views";
    private static final String SHOW_WORK_PACKAGES = "entry.workPackages";

    private Prefs() {
    }

    private static IPreferenceStore store() {
        IPreferenceStore store = Activator.get().getPreferenceStore();
        store.setDefault(KEYWORDS, KeywordRules.DEFAULTS);
        store.setDefault(FOLDER, System.getProperty("user.home"));
        store.setDefault(MARKS, true);
        store.setDefault(SHOW_VIEWS, true);
        store.setDefault(SHOW_WORK_PACKAGES, true);
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

    /** Whether the model tree leaves out objects of which only the layout changed since their export. */
    static boolean ignoreLayout() {
        return store().getBoolean(IGNORE_LAYOUT);
    }

    static void ignoreLayout(boolean ignore) {
        store().setValue(IGNORE_LAYOUT, ignore);
    }

    /** Whether an element's description says which views show it ("Views: ..."). */
    static boolean showViews() {
        return store().getBoolean(SHOW_VIEWS);
    }

    static void showViews(boolean show) {
        store().setValue(SHOW_VIEWS, show);
    }

    /** Whether an element's description says which work packages change it ("Work packages: ..."). */
    static boolean showWorkPackages() {
        return store().getBoolean(SHOW_WORK_PACKAGES);
    }

    static void showWorkPackages(boolean show) {
        store().setValue(SHOW_WORK_PACKAGES, show);
    }

    /**
     * The rules of an export with what these preferences leave out of every element's description, whichever rules
     * file the export uses: they are this computer's choice. Outside Archi (in tests) the rules as they are.
     */
    static KeywordRules applied(KeywordRules rules) {
        if (org.osgi.framework.FrameworkUtil.getBundle(Prefs.class) == null) {
            return rules;
        }
        KeywordRules applied = rules;
        if (!showViews()) {
            applied = applied.withoutEntryPart("views");
        }
        if (!showWorkPackages()) {
            applied = applied.withoutEntryPart("workpackages");
        }
        return applied;
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
