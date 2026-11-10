package io.github.p3chys.documentationarchi.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.handlers.HandlerUtil;

import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IJunction;

/**
 * The right-click "Describe Here" of elements in a view: the export describes them on this view's page, not on the
 * first that shows them ({@link HomeView}). On elements already described here it takes that back.
 */
public class DescribeHereHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) {
        IDiagramModel view = null;
        List<IArchimateElement> elements = new ArrayList<>();
        for (Object o : ExportHandler.selected(event)) {
            if (o instanceof IDiagramModelArchimateObject) {
                IArchimateElement element = ((IDiagramModelArchimateObject) o).getArchimateElement();
                IDiagramModel on = ((IDiagramModelArchimateObject) o).getDiagramModel();
                if (view != null && on != view) {
                    view = null;
                    break;
                }
                view = on;
                if (!(element instanceof IJunction) && !ModelReader.isPrintOrder(element) && !elements.contains(element)) {
                    elements.add(element);
                }
            }
        }
        if (view == null || elements.isEmpty()) {
            MessageDialog.openInformation(HandlerUtil.getActiveShell(event), "Describe Here",
                    "Select the elements in a view that its page should describe.");
            return null;
        }
        WorkPackageHandler.execute(view.getArchimateModel(), HomeView.toggle(view, elements));
        return null;
    }
}
