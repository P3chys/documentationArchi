package io.github.p3chys.documentationarchi.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CommandStack;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateModelObject;
import com.archimatetool.model.IDiagramModelArchimateComponent;

import io.github.p3chys.documentationarchi.doc.Assets;

/**
 * The right-click "Add Asset..." of the model, a view, an element, a relationship or a note: pick files, they are
 * copied into the Assets folder next to the model (unless they are in it already), and an {@code [[asset:path]]}
 * per file goes at the end of the description, in one command that Undo takes back (the copies stay).
 */
public class AddAssetHandler extends AbstractHandler {

    private static final String TITLE = "Add Asset";

    @Override
    public Object execute(ExecutionEvent event) {
        Shell shell = HandlerUtil.getActiveShell(event);
        EObject target = target(HandlerUtil.getActiveMenuSelection(event));
        if (target == null) {
            target = target(HandlerUtil.getCurrentSelection(event));
        }
        if (target == null) {
            MessageDialog.openInformation(shell, TITLE, "Select one element, relationship, view, note or the model.");
            return null;
        }
        IArchimateModel model = ((IArchimateModelObject) target).getArchimateModel();
        if (model.getFile() == null) {
            MessageDialog.openInformation(shell, TITLE, "Save the model first: its assets are kept in the "
                    + Assets.FOLDER + " folder next to the model file.");
            return null;
        }
        Path modelFolder = model.getFile().getAbsoluteFile().toPath().getParent();
        Path assets;
        try {
            assets = AssetLinks.assetsFolder(modelFolder);
        } catch (IOException e) {
            MessageDialog.openError(shell, TITLE, "Cannot read " + modelFolder + ": " + e.getMessage());
            return null;
        }

        FileDialog dialog = new FileDialog(shell, SWT.OPEN | SWT.MULTI);
        dialog.setText(TITLE + " - copied into " + assets);
        dialog.setFilterPath((assets.toFile().isDirectory() ? assets : modelFolder).toString());
        dialog.setFilterNames(new String[] { "All files", "Pictures" });
        dialog.setFilterExtensions(new String[] { "*.*", "*.png;*.jpg;*.jpeg;*.gif;*.bmp;*.svg" });
        if (dialog.open() == null) {
            return null;
        }

        List<String> paths = new ArrayList<>();
        try {
            for (String name : dialog.getFileNames()) {
                paths.add(AssetLinks.bringIn(assets, new File(dialog.getFilterPath(), name).toPath()));
            }
        } catch (IOException e) {
            Activator.log("Adding an asset failed", e);
            MessageDialog.openError(shell, TITLE, "Cannot copy into " + assets + ": " + e.getMessage());
        }
        Command command = AssetLinks.add(target, paths);
        if (command != null) {
            CommandStack stack = (CommandStack) model.getAdapter(CommandStack.class);
            if (stack != null) {
                stack.execute(command);
            } else {
                command.execute();
            }
        }
        return null;
    }

    /** The one object whose description gets the asset: a diagram object means its element or relationship. */
    private static EObject target(ISelection selection) {
        if (!(selection instanceof IStructuredSelection) || ((IStructuredSelection) selection).size() != 1) {
            return null;
        }
        Object o = ((IStructuredSelection) selection).getFirstElement();
        o = o instanceof EditPart ? ((EditPart) o).getModel() : o;
        if (o instanceof IDiagramModelArchimateComponent) {
            o = ((IDiagramModelArchimateComponent) o).getArchimateConcept();
        }
        return o instanceof IArchimateModelObject && AssetLinks.descriptionOf((EObject) o) != null ? (EObject) o : null;
    }
}
