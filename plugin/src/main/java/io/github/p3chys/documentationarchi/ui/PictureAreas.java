package io.github.p3chys.documentationarchi.ui;

import java.util.ArrayList;
import java.util.List;

import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IJunction;

import io.github.p3chys.documentationarchi.doc.DocPage;

/**
 * Where on a view's picture each element is drawn, for the links from the picture to the descriptions. The figures
 * are measured by {@link ViewImages}; what is worked out from them is here, where it is tested without drawing.
 */
final class PictureAreas {

    /** An element's figure, in the coordinates of the diagram. */
    static final class Box {
        final IArchimateElement element;
        final int x;
        final int y;
        final int width;
        final int height;

        Box(IArchimateElement element, int x, int y, int width, int height) {
            this.element = element;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private PictureAreas() {
    }

    /**
     * The figures of elements on the view, nested ones too, in the order they are drawn: one inside another comes
     * after it. Not PrintOrder, which the picture leaves out, nor junctions, which are never described.
     */
    static List<IDiagramModelArchimateObject> figures(IDiagramModel view) {
        List<IDiagramModelArchimateObject> figures = new ArrayList<>();
        view.eAllContents().forEachRemaining(o -> {
            if (o instanceof IDiagramModelArchimateObject) {
                IArchimateElement element = ((IDiagramModelArchimateObject) o).getArchimateElement();
                if (element != null && !(element instanceof IJunction) && !ModelReader.isPrintOrder(element)) {
                    figures.add((IDiagramModelArchimateObject) o);
                }
            }
        });
        return figures;
    }

    /**
     * The boxes on the picture, in CSS pixels of the image shown at its width (one diagram unit each, the picture
     * being drawn at {@code scale}). The margin is whatever the picture is larger than the diagram's extent, the same
     * on every side, so it need not be known.
     *
     * @param extent x, y, width and height of what the picture shows of the diagram, before its margin
     * @param pixelWidth width of the picture in pixels
     */
    static List<DocPage.Area> areas(List<Box> boxes, int[] extent, int pixelWidth, int pixelHeight, int scale) {
        double left = (pixelWidth / (double) scale - extent[2]) / 2;
        double top = (pixelHeight / (double) scale - extent[3]) / 2;
        List<DocPage.Area> areas = new ArrayList<>();
        for (Box box : boxes) {
            areas.add(new DocPage.Area(box.element.getId(), ModelReader.label(box.element),
                    (int) Math.round(box.x - extent[0] + left), (int) Math.round(box.y - extent[1] + top),
                    box.width, box.height));
        }
        return areas;
    }
}
