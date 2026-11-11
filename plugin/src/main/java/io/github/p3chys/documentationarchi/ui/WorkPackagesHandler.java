package io.github.p3chys.documentationarchi.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateModelObject;
import com.archimatetool.model.IDiagramModelArchimateComponent;
import com.archimatetool.model.IWorkPackage;

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.Published;

/**
 * The right-click "Work Packages..." of anything in a model: the work packages (Jira tickets) of the model, with
 * ticks to link the selected elements to them ({@link WorkPackagesDialog}).
 */
public class WorkPackagesHandler extends AbstractHandler {

    private static final String TREE = "com.archimatetool.editor.treeModelView";
    private static final String TITLE = "Work Packages";

    @Override
    public Object execute(ExecutionEvent event) {
        Shell shell = HandlerUtil.getActiveShell(event);
        List<Object> selection = ExportHandler.selected(event);
        IArchimateModel model = selection.stream().filter(IArchimateModelObject.class::isInstance)
                .map(o -> ((IArchimateModelObject) o).getArchimateModel()).findFirst().orElse(null);
        if (model == null) {
            MessageDialog.openInformation(shell, TITLE, "Select the model or anything in it.");
            return null;
        }
        List<IArchimateElement> linking = elements(selection, model);
        Map<IArchimateElement, String> found = null;
        while (true) {
            WorkPackagesDialog dialog = new WorkPackagesDialog(shell, model, linking, found);
            int result = dialog.open();
            if (result == WorkPackagesDialog.EXPORT) {
                try {
                    ExportHandler.run(shell, ModelReader.Selection.ofWorkPackages(model, dialog.selected()));
                } catch (IllegalArgumentException e) {
                    MessageDialog.openInformation(shell, "Export Documentation", e.getMessage());
                }
            } else if (result == WorkPackagesDialog.SHOW) {
                show(HandlerUtil.getActiveWorkbenchWindow(event).getActivePage(), dialog.shown());
            } else if (result == WorkPackagesDialog.CHANGED) {
                Map<IArchimateElement, String> changed = changed(shell, model);
                if (changed != null) { // else back to the window as it was
                    found = changed;
                    linking = new ArrayList<>(changed.keySet());
                }
                continue;
            }
            return null;
        }
    }

    /** The elements of the selection that can be linked to a work package: of the model, no work packages. */
    static List<IArchimateElement> elements(List<Object> selection, IArchimateModel model) {
        List<IArchimateElement> elements = new ArrayList<>();
        for (Object o : selection) {
            Object concept = o instanceof IDiagramModelArchimateComponent
                    ? ((IDiagramModelArchimateComponent) o).getArchimateConcept() : o;
            if (concept instanceof IArchimateElement && !(concept instanceof IWorkPackage)
                    && ((IArchimateElement) concept).getArchimateModel() == model
                    && !ModelReader.isPrintOrder((IArchimateElement) concept) && !elements.contains(concept)) {
                elements.add((IArchimateElement) concept);
            }
        }
        return elements;
    }

    /**
     * What changed since the documentation was published and no open work package changes yet, with the kind of
     * change found: the tickets say what the next published documentation changes, without anyone looking for the
     * elements one by one. Null, after saying why, when there is nothing to link.
     */
    private static Map<IArchimateElement, String> changed(Shell shell, IArchimateModel model) {
        Published published = ExportState.get().published(model);
        if (published.lastTime() == 0) {
            MessageDialog.openInformation(shell, TITLE, "Nothing was published yet, so there is nothing to compare "
                    + "with. Tick \"Publish\" in an export of the documentation your readers get.");
            return null;
        }
        // without pictures: only what the elements say is compared
        Doc doc = ModelReader.read(ModelReader.Selection.of(List.of(model)), (view, page) -> { });
        Map<IArchimateElement, String> found = WorkPackages.unassigned(model, published.changesTo(doc, true));
        if (found.isEmpty()) {
            MessageDialog.openInformation(shell, TITLE, "Every element that changed since the documentation was "
                    + "published is linked to an open work package.");
            return null;
        }
        return found;
    }

    /** Selects the objects in the model tree. */
    private static void show(IWorkbenchPage page, List<?> objects) {
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
