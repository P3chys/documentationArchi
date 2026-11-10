package io.github.p3chys.documentationarchi.ui;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

import io.github.p3chys.documentationarchi.render.Preview;

/**
 * What a click on a link of the Documentation Preview does. The preview never navigates away from its page: a view
 * or element is shown in it, a web address opens in the system's browser, an asset of the page opens with its
 * program, and anything else does nothing: a file elsewhere on the computer written into a description, and any
 * program or script, even among the assets (a model shared with you could bring one).
 */
final class PreviewLinks {

    enum Action { LOAD, SHOW, OPEN, IGNORE }

    /** Files that run something when opened, on Windows, macOS or Linux. */
    private static final Set<String> PROGRAMS = Set.of("exe", "com", "bat", "cmd", "msi", "msp", "scr", "pif", "cpl",
            "ps1", "psm1", "vbs", "vbe", "js", "jse", "wsf", "wsh", "hta", "jar", "lnk", "url", "reg", "inf", "sh",
            "bash", "command", "app", "py", "pyw", "pl", "rb", "appref-ms", "application", "gadget", "msc", "scf");

    private PreviewLinks() {
    }

    /**
     * @param page the page the preview is loading, null if none
     * @param assets the files of the Assets folder the page shows or links to, by their real paths
     */
    static Action decide(String location, Path page, Set<Path> assets) {
        if (location == null || location.isEmpty() || location.equals("about:blank")) {
            return Action.LOAD;
        }
        if (Preview.target(location) != null) {
            return Action.SHOW;
        }
        String lower = location.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("mailto:")) {
            return Action.OPEN;
        }
        Path file = file(location);
        if (file == null) {
            return Action.IGNORE;
        }
        if (page != null && file.equals(page.toAbsolutePath().normalize())) {
            return Action.LOAD;
        }
        return asset(file, assets) != null ? Action.OPEN : Action.IGNORE;
    }

    /** The real file of one of the assets that is no program, null for any other file. */
    static Path asset(Path file, Set<Path> assets) {
        Path real;
        try {
            real = file.toRealPath();
        } catch (IOException | RuntimeException e) {
            return null;
        }
        String name = real.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        boolean program = dot < 0 || PROGRAMS.contains(name.substring(dot + 1));
        return assets.contains(real) && !program ? real : null;
    }

    /** The file of a file: location, without its #fragment; null for any other location. */
    static Path file(String location) {
        if (!location.regionMatches(true, 0, "file:", 0, 5)) {
            return null;
        }
        try {
            URI uri = URI.create(location.replace(" ", "%20"));
            return Path.of(new URI(uri.getScheme(), uri.getSchemeSpecificPart(), null)).toAbsolutePath().normalize();
        } catch (RuntimeException | URISyntaxException e) {
            return null;
        }
    }
}
