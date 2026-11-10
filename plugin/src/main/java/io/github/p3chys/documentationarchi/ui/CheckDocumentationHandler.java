package io.github.p3chys.documentationarchi.ui;

import java.util.List;

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

/** The right-click "Check Documentation..." of anything in a model: lists what is wrong, exports nothing. */
public class CheckDocumentationHandler extends AbstractHandler {

    private static final String TREE = "com.archimatetool.editor.treeModelView";

    @Override
    public Object execute(ExecutionEvent event) {
        Shell shell = HandlerUtil.getActiveShell(event);
        IArchimateModel model = ExportHandler.selected(event).stream().filter(IArchimateModelObject.class::isInstance)
                .map(o -> ((IArchimateModelObject) o).getArchimateModel()).findFirst().orElse(null);
        if (model == null) {
            MessageDialog.openInformation(shell, "Check Documentation", "Select the model or anything in it.");
            return null;
        }
        List<DocCheck.Finding> findings;
        try {
            findings = DocCheck.check(model, ProjectRules.resolve(model, null, Prefs::rules));
        } catch (IllegalArgumentException e) { // unusable formatting rules: the message says which file and line
            MessageDialog.openError(shell, "Check Documentation", e.getMessage());
            return null;
        }
        if (findings.isEmpty()) {
            MessageDialog.openInformation(shell, "Check Documentation", "Nothing to report: the documentation of \""
                    + ModelReader.label(model) + "\" has none of the problems this check looks for.");
            return null;
        }
        CheckDialog dialog = new CheckDialog(shell, model, findings);
        if (dialog.open() == CheckDialog.SHOW) {
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
