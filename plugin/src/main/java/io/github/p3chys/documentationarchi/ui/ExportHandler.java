package io.github.p3chys.documentationarchi.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.gef.EditPart;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.render.Exporter;
import io.github.p3chys.documentationarchi.render.Exporter.Format;

/** The right-click "Export Documentation..." of the model tree and the diagram editors. */
public class ExportHandler extends AbstractHandler {

    private static final int MAX_PROBLEMS = 15;

    @Override
    public Object execute(ExecutionEvent event) {
        Shell shell = HandlerUtil.getActiveShell(event);
        ModelReader.Selection selection;
        try {
            selection = ModelReader.Selection.of(selected(event));
        } catch (IllegalArgumentException e) {
            MessageDialog.openInformation(shell, "Export Documentation", e.getMessage());
            return null;
        }
        ExportDialog dialog = new ExportDialog(shell, describe(selection));
        if (dialog.open() != ExportDialog.OK) {
            return null;
        }
        Path folder = Path.of(dialog.folder()).resolve(Doc.slug(selection.model.getName()));
        if (notEmpty(folder) && !MessageDialog.openQuestion(shell, "Export Documentation",
                folder + " already exists. Replace the files in it?")) {
            return null;
        }
        start(Display.getCurrent(), selection, folder, dialog.formats());
        return null;
    }

    private static List<Object> selected(ExecutionEvent event) {
        ISelection selection = HandlerUtil.getActiveMenuSelection(event);
        if (selection == null) {
            selection = HandlerUtil.getCurrentSelection(event);
        }
        List<Object> objects = new ArrayList<>();
        if (selection instanceof IStructuredSelection) {
            for (Object o : ((IStructuredSelection) selection).toList()) {
                objects.add(o instanceof EditPart ? ((EditPart) o).getModel() : o);
            }
        }
        return objects;
    }

    private static String describe(ModelReader.Selection selection) {
        if (selection.wholeModel) {
            return "Whole model \"" + selection.model.getName() + "\"";
        }
        return selection.views.size() + " view(s) and " + selection.elements.size() + " element(s) of \""
                + selection.model.getName() + "\"";
    }

    private static boolean notEmpty(Path folder) {
        if (!Files.isDirectory(folder)) {
            return false;
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.findAny().isPresent();
        } catch (IOException e) {
            return true;
        }
    }

    private static void start(Display display, ModelReader.Selection selection, Path folder, Set<Format> formats) {
        Job job = new Job("Exporting documentation") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    List<String> problems = export(selection, folder, formats);
                    display.asyncExec(() -> finished(display, folder, problems));
                    return Status.OK_STATUS;
                } catch (IOException | RuntimeException e) {
                    Activator.log("Export failed", e);
                    display.asyncExec(() -> MessageDialog.openError(display.getActiveShell(), "Export Documentation",
                            "The export failed: " + e));
                    return Status.CANCEL_STATUS;
                }
            }
        };
        job.setUser(true);
        job.schedule();
    }

    /** @return references to assets that found no file */
    static List<String> export(ModelReader.Selection selection, Path folder, Set<Format> formats) throws IOException {
        // the PDF library uses AWT classes, which must not start a GUI next to SWT (it would hang on macOS)
        System.setProperty("java.awt.headless", "true");
        Doc doc = ModelReader.read(selection, ViewImages::render);
        Exporter exporter = new Exporter(doc, Prefs.rules());
        exporter.export(folder, formats);
        return exporter.problems();
    }

    private static void finished(Display display, Path folder, List<String> problems) {
        String message = "Exported to " + folder;
        if (!problems.isEmpty()) {
            message += "\n\nThese assets are missing from the export (shown in red on their pages):\n"
                    + problems.stream().limit(MAX_PROBLEMS).map(p -> "- " + p).collect(Collectors.joining("\n"))
                    + (problems.size() > MAX_PROBLEMS ? "\n... and " + (problems.size() - MAX_PROBLEMS) + " more" : "");
        }
        MessageDialog dialog = new MessageDialog(display.getActiveShell(), "Export Documentation", null, message,
                problems.isEmpty() ? MessageDialog.INFORMATION : MessageDialog.WARNING,
                new String[] { "Open Folder", "Close" }, 0);
        if (dialog.open() == 0) {
            Program.launch(folder.toString());
        }
    }
}
