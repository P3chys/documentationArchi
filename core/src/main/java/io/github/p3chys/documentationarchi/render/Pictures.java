package io.github.p3chys.documentationarchi.render;

import io.github.p3chys.documentationarchi.doc.DocPage;

/**
 * How large pictures are shown. A picture taller than a PDF page would be cut across several pages, and a very tall
 * one is hard to read on a web page too, so pictures are made small enough to fit a box, keeping their proportions.
 */
final class Pictures {

    /** The width of a PDF page inside its margins (A4, 15 mm each side), in CSS pixels. */
    static final int PDF_WIDTH = 680;
    /** The height a picture may take: a PDF page inside its margins (A4, 18 mm each), less room for a heading. */
    static final int MAX_HEIGHT = 860;
    /** A landscape PDF page inside its margins (A4, 15 mm each side), in CSS pixels. */
    static final int LANDSCAPE_WIDTH = 1000;
    /** The height a picture may take on a landscape PDF page, less room for a heading. */
    static final int LANDSCAPE_HEIGHT = 580;
    /** How much wider than tall a view's picture must be to get a landscape page of the PDF. */
    private static final double WIDE = 1.3;

    private Pictures() {
    }

    /**
     * The width to show a picture at so that it is at most {@code maxWidth} wide and {@link #MAX_HEIGHT} high, or 0
     * when it fits as it is (or its size is unknown) and needs no width of its own.
     */
    static int fit(int width, int height, int maxWidth) {
        return fit(width, height, maxWidth, MAX_HEIGHT);
    }

    /** The same, at most {@code maxHeight} high. */
    static int fit(int width, int height, int maxWidth, int maxHeight) {
        if (width <= 0 || height <= 0) {
            return 0;
        }
        int shown = Math.min(width, maxWidth);
        if ((long) height * shown <= (long) maxHeight * width) {
            return shown == width ? 0 : shown;
        }
        return Math.max(1, (int) ((long) maxHeight * width / height));
    }

    /**
     * Whether a view's picture is clearly wider than tall and too wide for a portrait page: on a landscape page of
     * the PDF it is shown larger.
     */
    static boolean landscape(DocPage page) {
        return page.image != null && page.imageWidth > PDF_WIDTH && page.imageHeight > 0
                && page.imageWidth >= WIDE * page.imageHeight;
    }
}
