package io.github.p3chys.documentationarchi.ui;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;

import com.archimatetool.editor.views.IModelView;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateModelObject;

/** The right-click "Work Packages..." of anything in a model: the overview of its work packages (Jira tickets). */
public class WorkPackagesHandler extends AbstractHandler {

    private static final String TREE = "com.archimatetool.editor.treeModelView";

    @Override
    public Object execute(ExecutionEvent event) {
        Shell shell = HandlerUtil.getActiveShell(event);
        IArchimateModel model = ExportHandler.selected(event).stream().filter(IArchimateModelObject.class::isInstance)
                .map(o -> ((IArchimateModelObject) o).getArchimateModel()).findFirst().orElse(null);
        if (model == null) {
            MessageDialog.openInformation(shell, "Work Packages", "Select the model or anything in it.");
            return null;
        }
        WorkPackagesDialog dialog = new WorkPackagesDialog(shell, model);
        int result = dialog.open();
        if (result == WorkPackagesDialog.EXPORT) {
            try {
                ExportHandler.run(shell, ModelReader.Selection.ofWorkPackages(model, dialog.selected()));
            } catch (IllegalArgumentException e) {
                MessageDialog.openInformation(shell, "Export Documentation", e.getMessage());
            }
        } else if (result == WorkPackagesDialog.SHOW) {
            show(HandlerUtil.getActiveWorkbenchWindow(event).getActivePage(), dialog.shown());
        }
        return null;
    }

    /** Selects the objects in the model tree. */
    private static void show(IWorkbenchPage page, java.util.List<?> objects) {
        try {
            IViewPart view = page.showView(TREE);
            if (view instanceof IModelView) {
                ((IModelView) view).getViewer().setSelection(new StructuredSelection(objects), true);
            }
        } catch (PartInitException e) {
            Activator.log("Cannot show the model tree", e);
        }
    }
}
