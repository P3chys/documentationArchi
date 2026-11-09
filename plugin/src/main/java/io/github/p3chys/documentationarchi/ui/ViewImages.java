package io.github.p3chys.documentationarchi.ui;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.eclipse.draw2d.IFigure;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.GraphicalEditPart;
import org.eclipse.gef.GraphicalViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.archimatetool.editor.diagram.util.DiagramUtils;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateObject;

import io.github.p3chys.documentationarchi.doc.DocPage;

/** Pictures of views. Drawn at twice the size, so they stay sharp in the PDF and when zooming in. */
final class ViewImages {

    private static final int SCALE = 2;
    private static final int MARGIN = 10;

    private ViewImages() {
    }

    /** Figures can only be drawn on the UI thread, wherever the export runs. */
    static void render(IDiagramModel view, DocPage page) {
        Display display = Display.getCurrent();
        if (display != null) {
            draw(view, page);
        } else {
            Display.getDefault().syncExec(() -> draw(view, page));
        }
    }

    private static void draw(IDiagramModel view, DocPage page) {
        Shell shell = new Shell();
        try {
            GraphicalViewer viewer = DiagramUtils.createViewer(view, shell);
            removePrintOrder(view, viewer);
            Image image = DiagramUtils.createImage(viewer, SCALE, MARGIN);
            try {
                ImageData data = image.getImageData();
                ImageLoader loader = new ImageLoader();
                loader.data = new ImageData[] { data };
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                loader.save(png, SWT.IMAGE_PNG);
                page.image = png.toByteArray();
                page.imageWidth = data.width / SCALE;
                page.imageHeight = data.height / SCALE;
            } finally {
                image.dispose();
            }
        } finally {
            shell.dispose();
        }
    }

    /** Only from the figures of this throwaway viewer: the model stays as it is. */
    private static void removePrintOrder(IDiagramModel view, GraphicalViewer viewer) {
        view.eAllContents().forEachRemaining(o -> {
            if (o instanceof IDiagramModelArchimateObject
                    && ModelReader.isPrintOrder(((IDiagramModelArchimateObject) o).getArchimateElement())) {
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
