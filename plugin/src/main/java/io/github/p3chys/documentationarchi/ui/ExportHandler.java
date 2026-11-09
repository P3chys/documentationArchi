package io.github.p3chys.documentationarchi.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.emf.ecore.EObject;
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
import io.github.p3chys.documentationarchi.text.KeywordRules;

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
        run(shell, selection);
        return null;
    }

    /** Asks where to and in which formats, then exports in the background. */
    static void run(Shell shell, ModelReader.Selection selection) {
        ExportDialog dialog = new ExportDialog(shell, describe(selection) + (ProjectRules.exists(selection.model)
                ? "\nFormatting from " + ProjectRules.file(selection.model) + " (project rules)" : ""));
        if (dialog.open() != ExportDialog.OK) {
            return;
        }
        Path folder = Path.of(dialog.folder()).resolve(Doc.slug(selection.title()));
        if (notEmpty(folder) && !MessageDialog.openQuestion(shell, "Export Documentation",
                folder + " already exists. Replace the files in it?")) {
            return;
        }
        start(Display.getCurrent(), selection, folder, dialog.formats());
    }

    /** The selected objects of the model tree or a view (the model objects of what a view shows). */
    static List<Object> selected(ExecutionEvent event) {
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
        if (!selection.workPackages.isEmpty()) {
            return (selection.workPackages.size() == 1 ? "Work package " : "Work packages ") + selection.title() + ": "
                    + selection.elements.size() + " element(s) of \"" + selection.model.getName() + "\"";
        }
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
                } catch (IllegalArgumentException e) { // unusable formatting rules: the message says which file and line
                    display.asyncExec(() -> MessageDialog.openError(display.getActiveShell(), "Export Documentation",
                            "The export failed. " + e.getMessage()));
                    return Status.CANCEL_STATUS;
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

    /**
     * Exports, and notes what the export showed, so the model tree marks what changes after it.
     *
     * @return references to assets that found no file
     */
    static List<String> export(ModelReader.Selection selection, Path folder, Set<Format> formats) throws IOException {
        return export(selection, folder, formats, null);
    }

    /** @param rulesFile formatting rules to use instead of the project file and the preferences, or null */
    static List<String> export(ModelReader.Selection selection, Path folder, Set<Format> formats, Path rulesFile)
            throws IOException {
        // the PDF library uses AWT classes, which must not start a GUI next to SWT (it would hang on macOS)
        System.setProperty("java.awt.headless", "true");
        List<EObject> shown = new ArrayList<>();
        KeywordRules rules = ProjectRules.resolve(selection.model, rulesFile, Prefs::rules); // fails before any work
        Doc doc = ModelReader.read(selection, ViewImages::render, shown);
        Map<String, String> fingerprints = ExportState.fingerprints(shown); // as read, whatever changes meanwhile
        long time = System.currentTimeMillis();
        Exporter exporter = new Exporter(doc, rules);
        exporter.export(folder, formats);
        try {
            ExportState.get().record(selection.model, fingerprints, time);
            ExportMarks.refresh();
        } catch (IOException | RuntimeException e) {
            Activator.log("The export worked, but what it showed could not be noted for the model tree marks", e);
        }
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
