package io.github.p3chys.documentationarchi.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CommandStack;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModelArchimateComponent;
import com.archimatetool.model.IWorkPackage;

/**
 * The right-click "Link to Work Package..." of elements, in the model tree and in views: picks the work packages
 * (Jira tickets) that change them, in one command that Undo takes back ({@link WorkPackages}).
 */
public class WorkPackageHandler extends AbstractHandler {

    private static final String TITLE = "Link to Work Package";

    @Override
    public Object execute(ExecutionEvent event) {
        Shell shell = HandlerUtil.getActiveShell(event);
        List<IArchimateElement> elements = new ArrayList<>();
        for (Object o : ExportHandler.selected(event)) {
            Object concept = o instanceof IDiagramModelArchimateComponent
                    ? ((IDiagramModelArchimateComponent) o).getArchimateConcept() : o;
            if (concept instanceof IArchimateElement && !(concept instanceof IWorkPackage)
                    && !ModelReader.isPrintOrder((IArchimateElement) concept) && !elements.contains(concept)) {
                elements.add((IArchimateElement) concept);
            }
        }
        Set<IArchimateModel> models = elements.stream().map(IArchimateElement::getArchimateModel).collect(Collectors.toSet());
        if (elements.isEmpty() || models.size() != 1) {
            MessageDialog.openInformation(shell, TITLE, elements.isEmpty()
                    ? "Select the elements a work package changes (work packages themselves can't be linked)."
                    : "The selection spans several models. Select elements of one model.");
            return null;
        }
        IArchimateModel model = models.iterator().next();
        WorkPackageDialog dialog = new WorkPackageDialog(shell, model, elements);
        if (dialog.open() == Window.OK) {
            execute(model, WorkPackages.change(model, elements, dialog.link(), dialog.unlink(), dialog.newName(),
                    dialog.kind()));
        }
        return null;
    }

    /** On the model's command stack, so Undo takes it back. */
    static void execute(IArchimateModel model, Command command) {
        if (command == null) {
            return;
        }
        CommandStack stack = (CommandStack) model.getAdapter(CommandStack.class);
        if (stack != null) {
            stack.execute(command);
        } else {
            command.execute();
        }
    }
}
