package io.github.p3chys.documentationarchi.ui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import com.archimatetool.model.IDiagramModel;

import io.github.p3chys.documentationarchi.doc.DocPage;

/**
 * The pictures of views the Documentation Preview showed, each kept until its view changes: drawing one takes long
 * enough to notice while typing. A view changes, for its picture, when its {@link ExportState#fingerprint} does:
 * anything on it moved or changed, or an element on it was renamed.
 */
final class PictureCache {

    private static final int SIZE = 16;

    private static final class Picture {
        final String fingerprint;
        final DocPage drawn;

        Picture(String fingerprint, DocPage drawn) {
            this.fingerprint = fingerprint;
            this.drawn = drawn;
        }
    }

    private final BiConsumer<IDiagramModel, DocPage> painter;
    private final Map<String, Picture> pictures = new LinkedHashMap<>(SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Picture> eldest) {
            return size() > SIZE;
        }
    };

    /** @param painter draws the picture of a view into a page, as {@link ViewImages#render} does */
    PictureCache(BiConsumer<IDiagramModel, DocPage> painter) {
        this.painter = painter;
    }

    /** Gives the page the picture of the view: the one drawn before if the view is as it was, else a new one. */
    void fill(IDiagramModel view, DocPage page) {
        String fingerprint = ExportState.fingerprint(view);
        Picture picture = pictures.get(view.getId());
        if (picture == null || !picture.fingerprint.equals(fingerprint)) {
            DocPage drawn = new DocPage(view.getId(), page.title);
            painter.accept(view, drawn);
            picture = new Picture(fingerprint, drawn);
            pictures.put(view.getId(), picture);
        }
        page.image = picture.drawn.image;
        page.imageWidth = picture.drawn.imageWidth;
        page.imageHeight = picture.drawn.imageHeight;
        page.areas.clear();
        page.areas.addAll(picture.drawn.areas);
    }
}
