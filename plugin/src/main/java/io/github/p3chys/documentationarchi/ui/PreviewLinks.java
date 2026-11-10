package io.github.p3chys.documentationarchi.ui;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Locale;

import io.github.p3chys.documentationarchi.render.Preview;

/**
 * What a click on a link of the Documentation Preview does. The preview never navigates away from its page: a view
 * or element is shown in it, a web address opens in the system's browser, a file of the Assets folder opens with its
 * program, and anything else (a file elsewhere on the computer, written into a description) does nothing.
 */
final class PreviewLinks {

    enum Action { LOAD, SHOW, OPEN, IGNORE }

    private PreviewLinks() {
    }

    /**
     * @param page the page the preview is loading, null if none
     * @param assets the model's Assets folder, null if it has none
     */
    static Action decide(String location, Path page, Path assets) {
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
        return assets != null && file.startsWith(assets.toAbsolutePath().normalize()) ? Action.OPEN : Action.IGNORE;
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
