package io.github.p3chys.documentationarchi.ui;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.ui.handlers.HandlerUtil;

import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateModelObject;
import com.archimatetool.model.INameable;

/**
 * The right-click "Insert Reference..." of the model, a view, an element, a relationship or a note: pick an element
 * or view, and {@code [[ref:id]]} goes at the end of the description ({@link References}), in one command that Undo
 * takes back.
 */
public class InsertReferenceHandler extends AbstractHandler {

    private static final String TITLE = "Insert Reference";

    @Override
    public Object execute(ExecutionEvent event) {
        Shell shell = HandlerUtil.getActiveShell(event);
        EObject target = AddAssetHandler.target(HandlerUtil.getActiveMenuSelection(event));
        if (target == null) {
            target = AddAssetHandler.target(HandlerUtil.getCurrentSelection(event));
        }
        if (target == null) {
            MessageDialog.openInformation(shell, TITLE, "Select one element, relationship, view, note or the model.");
            return null;
        }
        IArchimateModel model = ((IArchimateModelObject) target).getArchimateModel();
        ElementListSelectionDialog dialog = new ElementListSelectionDialog(shell, new LabelProvider() {
            @Override
            public String getText(Object o) {
                String type = ((EObject) o).eClass().getName().replaceAll("(?<=[a-z])(?=[A-Z])", " ");
                return ModelReader.label((INameable) o) + "  (" + type + ")";
            }
        });
        dialog.setTitle(TITLE);
        dialog.setMessage("The element or view to refer to (type to filter). The export shows its current name.");
        dialog.setElements(References.targets(model).toArray());
        dialog.setMultipleSelection(false);
        if (dialog.open() != Window.OK || dialog.getFirstResult() == null) {
            return null;
        }
        WorkPackageHandler.execute(model, References.add(target, (IArchimateModelObject) dialog.getFirstResult()));
        return null;
    }
}
