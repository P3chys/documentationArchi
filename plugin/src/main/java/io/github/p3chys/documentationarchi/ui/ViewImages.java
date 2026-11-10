package io.github.p3chys.documentationarchi.ui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.draw2d.FreeformFigure;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.GraphicalEditPart;
import org.eclipse.gef.GraphicalViewer;
import org.eclipse.gef.LayerConstants;
import org.eclipse.gef.editparts.LayerManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.archimatetool.editor.diagram.util.DiagramUtils;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;

import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Picture;

/**
 * Pictures of views, and where on them each element is drawn, so the pages can link the picture to the descriptions.
 * Drawn at twice the size, so they stay sharp in the PDF and when zooming in.
 */
final class ViewImages {

    private static final int SCALE = 2;
    private static final int MARGIN = 10;

    private ViewImages() {
    }

    /** Figures can only be drawn on the UI thread, wherever the export runs. The picture is kept in memory. */
    static void render(IDiagramModel view, DocPage page) {
        Exclusion hidden = Exclusion.current(); // of the export running on this thread
        Display display = Display.getCurrent();
        if (display != null) {
            draw(view, page, hidden);
        } else {
            Display.getDefault().syncExec(() -> draw(view, page, hidden));
        }
    }

    /**
     * For an export: the picture goes to a temporary file, which the export deletes when it ends ({@link Picture}),
     * so a model with many large views never holds all their pictures in memory.
     */
    static void renderToFile(IDiagramModel view, DocPage page) {
        render(view, page);
        try {
            page.image = Picture.temporary(page.image.bytes());
        } catch (IOException e) {
            throw new UncheckedIOException("The picture of " + page.title + " could not be written to a temporary file", e);
        }
    }

    private static void draw(IDiagramModel view, DocPage page, Exclusion hidden) {
        Shell shell = new Shell();
        try {
            GraphicalViewer viewer = DiagramUtils.createViewer(view, shell);
            removePrintOrder(view, viewer, hidden);
            List<PictureAreas.Box> boxes = boxes(view, viewer); // after: what is taken out of the picture has no area
            Rectangle extent = extent(layers(viewer));
            Image image = DiagramUtils.createImage(viewer, SCALE, MARGIN);
            try {
                ImageData data = image.getImageData();
                if (extent != null) {
                    page.areas.addAll(PictureAreas.areas(boxes,
                            new int[] { extent.x, extent.y, extent.width, extent.height }, data.width, data.height, SCALE));
                }
                ImageLoader loader = new ImageLoader();
                loader.data = new ImageData[] { data };
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                loader.save(png, SWT.IMAGE_PNG);
                page.image = Picture.of(png.toByteArray());
                page.imageWidth = data.width / SCALE;
                page.imageHeight = data.height / SCALE;
            } finally {
                image.dispose();
            }
        } finally {
            shell.dispose();
        }
    }

    private static IFigure layers(GraphicalViewer viewer) {
        return ((LayerManager) viewer.getEditPartRegistry().get(LayerManager.ID))
                .getLayer(LayerConstants.PRINTABLE_LAYERS);
    }

    /** Where each element's figure is, in the coordinates of the layers the picture is drawn of. */
    private static List<PictureAreas.Box> boxes(IDiagramModel view, GraphicalViewer viewer) {
        IFigure layers = layers(viewer);
        List<PictureAreas.Box> boxes = new ArrayList<>();
        for (IDiagramModelArchimateObject o : PictureAreas.figures(view)) {
            Object part = viewer.getEditPartRegistry().get(o);
            IFigure figure = part instanceof GraphicalEditPart ? ((GraphicalEditPart) part).getFigure() : null;
            if (figure != null && figure.getParent() != null && figure.isVisible()) {
                Rectangle r = figure.getBounds().getCopy();
                figure.getParent().translateToAbsolute(r); // nested figures are placed inside their parent's
                layers.translateToRelative(r);
                boxes.add(new PictureAreas.Box(o.getArchimateElement(), r.x, r.y, r.width, r.height));
            }
        }
        return boxes;
    }

    /**
     * What the picture shows of the diagram before its margin is added: the figures' bounds, as Archi measures them
     * for its pictures. Worked out here rather than asked of Archi, whose ways of saying it differ between versions.
     */
    private static Rectangle extent(IFigure figure) {
        if (!(figure instanceof FreeformFigure)) {
            return figure.getBounds();
        }
        Rectangle extent = null;
        for (Object child : figure.getChildren()) {
            Rectangle bounds = extent((IFigure) child);
            if (bounds != null) {
                extent = extent == null ? new Rectangle(bounds) : extent.union(bounds);
            }
        }
        return extent;
    }

    /**
     * PrintOrder, and what the rules leave out ({@link Exclusion}; null: nothing). Only from the figures of this
     * throwaway viewer: the model stays as it is.
     */
    private static void removePrintOrder(IDiagramModel view, GraphicalViewer viewer, Exclusion hidden) {
        view.eAllContents().forEachRemaining(o -> {
            if (hidden != null && o instanceof IDiagramModelArchimateConnection
                    && hidden.excludedWithEnds(((IDiagramModelArchimateConnection) o).getArchimateRelationship())) {
                remove((EditPart) viewer.getEditPartRegistry().get(o));
            } else if (o instanceof IDiagramModelArchimateObject
                    && (ModelReader.isPrintOrder(((IDiagramModelArchimateObject) o).getArchimateElement())
                    || hidden != null && hidden.excluded(((IDiagramModelArchimateObject) o).getArchimateElement()))) {
                GraphicalEditPart part = (GraphicalEditPart) viewer.getEditPartRegistry().get(o);
                remove(part.getSourceConnections());
                remove(part.getTargetConnections());
                remove(part);
            }
        });
    }

    private static void remove(List<?> parts) {
        parts.forEach(part -> remove((EditPart) part));
    }

    private static void remove(EditPart part) {
        IFigure figure = ((GraphicalEditPart) part).getFigure();
        if (figure.getParent() != null) {
            figure.getParent().remove(figure);
        }
    }
}
