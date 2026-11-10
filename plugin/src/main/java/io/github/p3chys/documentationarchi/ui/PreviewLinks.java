package io.github.p3chys.documentationarchi.ui;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

import io.github.p3chys.documentationarchi.render.Preview;

/**
 * What a click on a link of the Documentation Preview does. The preview never navigates away from its page: a view
 * or element is shown in it, a web address opens in the system's browser, a document or picture among the assets
 * of the page opens with its program, and anything else does nothing: a file elsewhere on the computer written into
 * a description, and any other kind of asset.
 */
final class PreviewLinks {

    enum Action { LOAD, SHOW, OPEN, IGNORE }

    /**
     * What a click may open: documents and pictures. Anything else could run something (programs, scripts, shortcuts,
     * documents with macros), and a model shared with you can bring such a file into its Assets folder.
     */
    private static final Set<String> DOCUMENTS = Set.of("pdf", "png", "jpg", "jpeg", "gif", "bmp", "svg", "webp", "txt",
            "csv", "md", "log", "rtf", "docx", "xlsx", "pptx", "vsdx", "odt", "ods", "odp", "odg");

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

    /**
     * The file, if it is one of the assets and a document or picture; null for any other. Decided by its path alone:
     * the assets are known by their real paths, and the file system is not asked about a path from a link (a
     * network path would make the computer connect to that server).
     */
    static Path asset(Path file, Set<Path> assets) {
        if (!assets.contains(file)) {
            return null;
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot > 0 && DOCUMENTS.contains(name.substring(dot + 1)) ? file : null;
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
