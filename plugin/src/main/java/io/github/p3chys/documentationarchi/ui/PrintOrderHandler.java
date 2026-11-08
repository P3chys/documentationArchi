package io.github.p3chys.documentationarchi.ui;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CommandStack;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelComponent;

/**
 * The right-click "Print Order..." of a view, in the model tree and the diagram editor. Keeps the chosen order as
 * PrintOrder links ({@link PrintOrder}), in one command on the model's command stack, so Undo takes it back.
 */
public class PrintOrderHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) {
        Shell shell = HandlerUtil.getActiveShell(event);
        IDiagramModel view = view(HandlerUtil.getActiveMenuSelection(event));
        if (view == null) {
            view = view(HandlerUtil.getCurrentSelection(event));
        }
        if (view == null) {
            MessageDialog.openInformation(shell, "Print Order", "Select a view.");
            return null;
        }
        if (ModelReader.ordered(view).isEmpty()) {
            MessageDialog.openInformation(shell, "Print Order", "The view shows no elements to order.");
            return null;
        }
        PrintOrderDialog dialog = new PrintOrderDialog(shell, view);
        int result = dialog.open();
        Command command = result == PrintOrderDialog.OK ? PrintOrder.save(view, dialog.order())
                : result == PrintOrderDialog.RESET ? PrintOrder.remove(view) : null;
        if (command != null) {
            CommandStack stack = (CommandStack) view.getArchimateModel().getAdapter(CommandStack.class);
            if (stack != null) {
                stack.execute(command);
            } else {
                command.execute();
            }
        }
        return null;
    }

    /** The one view selected, or the view of the diagram objects selected; null for anything else. */
    private static IDiagramModel view(ISelection selection) {
        if (!(selection instanceof IStructuredSelection) || selection.isEmpty()) {
            return null;
        }
        IDiagramModel view = null;
        for (Object o : ((IStructuredSelection) selection).toList()) {
            Object model = o instanceof EditPart ? ((EditPart) o).getModel() : o;
            IDiagramModel v = model instanceof IDiagramModel ? (IDiagramModel) model
                    : model instanceof IDiagramModelComponent ? ((IDiagramModelComponent) model).getDiagramModel() : null;
            if (v == null || view != null && v != view) {
                return null;
            }
            view = v;
        }
        return view;
    }
}
