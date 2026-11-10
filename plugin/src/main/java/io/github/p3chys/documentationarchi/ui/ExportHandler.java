package io.github.p3chys.documentationarchi.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
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

import com.archimatetool.model.IArchimateModel;

import io.github.p3chys.documentationarchi.doc.Catalogs;
import io.github.p3chys.documentationarchi.doc.DataIndex;
import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Generated;
import io.github.p3chys.documentationarchi.doc.Published;
import io.github.p3chys.documentationarchi.render.Exporter;
import io.github.p3chys.documentationarchi.render.Exporter.Format;
import io.github.p3chys.documentationarchi.text.ExportProfile;
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
                ? "\nFormatting from " + ProjectRules.file(selection.model) + " (project rules)" : ""),
                selection.model.getFile() != null, selection.model, Profiles.of(selection.model));
        if (dialog.open() != ExportDialog.OK) {
            return;
        }
        ExportProfile profile = dialog.profile();
        ModelReader.Selection chosen = selection;
        if (profile != null && profile.hasScope()) { // the profile says what to export, not the menu
            try {
                chosen = Profiles.selection(selection.model, null, profile);
            } catch (IllegalArgumentException e) {
                MessageDialog.openError(shell, "Export Documentation", e.getMessage());
                return;
            }
        }
        // a profile's output is the export folder itself, as on the command line
        Path folder = profile != null && profile.output != null ? Path.of(dialog.folder())
                : Path.of(dialog.folder()).resolve(Doc.slug(chosen.title()));
        if (notEmpty(folder) && !MessageDialog.openQuestion(shell, "Export Documentation",
                folder + " already exists. Replace the files in it?")) {
            return;
        }
        start(Display.getCurrent(), chosen, folder, dialog.formats(), dialog.publish(), profile);
    }

    /** Runs a profile of the model's rules without asking; what it does not set comes from the preferences. */
    static void run(Shell shell, IArchimateModel model, ExportProfile profile) {
        ModelReader.Selection selection;
        Path folder;
        try {
            selection = Profiles.selection(model, null, profile);
            Path output = Profiles.output(model, null, profile);
            folder = output != null ? output : Path.of(Prefs.folder()).resolve(Doc.slug(selection.title()));
        } catch (IllegalArgumentException e) {
            MessageDialog.openError(shell, "Export Documentation", e.getMessage());
            return;
        }
        if (notEmpty(folder) && !MessageDialog.openQuestion(shell, "Export Documentation",
                "Profile " + profile.name + " exports to " + folder + ", which already exists. Replace the files in it?")) {
            return;
        }
        start(Display.getCurrent(), selection, folder, Profiles.formats(profile, Prefs.formats()), profile.publish,
                profile);
    }

    /** The selected objects of the model tree or a view (the model objects of what a view shows). */
    static List<Object> selected(ExecutionEvent event) {
        ISelection selection = HandlerUtil.getActiveMenuSelection(event);
        if (selection == null) {
            selection = HandlerUtil.getCurrentSelection(event);
        }
        return selected(selection);
    }

    static List<Object> selected(ISelection selection) {
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

    private static void start(Display display, ModelReader.Selection selection, Path folder, Set<Format> formats,
            boolean publish, ExportProfile profile) {
        Job job = new Job("Exporting documentation") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    Result result = exportAll(selection, folder, formats, null, monitor, publish, profile);
                    display.asyncExec(() -> finished(display, folder, result, publish));
                    return Status.OK_STATUS;
                } catch (CancellationException | OperationCanceledException e) { // the previous export is as it was
                    return Status.CANCEL_STATUS;
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
        return export(selection, folder, formats, null, new NullProgressMonitor());
    }

    /**
     * @param rulesFile formatting rules to use instead of the project file and the preferences, or null
     * @param monitor told the progress per view drawn and per format written; cancelling it stops the export before
     *        anything of the previous one is replaced, and nothing is noted for the model tree marks
     * @throws CancellationException if the monitor was cancelled
     */
    static List<String> export(ModelReader.Selection selection, Path folder, Set<Format> formats, Path rulesFile,
            IProgressMonitor monitor) throws IOException {
        return exportAll(selection, folder, formats, rulesFile, monitor, false).problems;
    }

    /** What an export found: assets without a file, how many objects the rules left out, and notes on generated pages. */
    static final class Result {
        final List<String> problems;
        final int excluded;
        final List<String> notes;

        Result(List<String> problems, int excluded, List<String> notes) {
            this.problems = problems;
            this.excluded = excluded;
            this.notes = notes;
        }
    }

    /**
     * Like {@link #export(ModelReader.Selection, Path, Set, Path, IProgressMonitor)}, with more to report.
     *
     * @param publish this export is the documentation the readers get: it is noted in the file
     *        {@link Published#FILE} next to the model, for the team's model tree marks and for the "Changes since"
     *        page of later exports. An export of the whole model always has that page once something was published.
     */
    static Result exportAll(ModelReader.Selection selection, Path folder, Set<Format> formats, Path rulesFile,
            IProgressMonitor monitor, boolean publish) throws IOException {
        return exportAll(selection, folder, formats, rulesFile, monitor, publish, null);
    }

    /** @param profile adds its labels to leave out, properties to hide and language to the rules; null for none */
    static Result exportAll(ModelReader.Selection selection, Path folder, Set<Format> formats, Path rulesFile,
            IProgressMonitor monitor, boolean publish, ExportProfile profile) throws IOException {
        return exportAll(selection, folder, formats, rulesFile, monitor, publish, profile, null);
    }

    /** @param assets the folder to read assets from instead of the rules' (the command line's), null for none */
    static Result exportAll(ModelReader.Selection selection, Path folder, Set<Format> formats, Path rulesFile,
            IProgressMonitor monitor, boolean publish, ExportProfile profile, String assets) throws IOException {
        if (publish && selection.model.getFile() == null) {
            throw new IllegalArgumentException("Save the model first: what is published is noted in the file "
                    + Published.FILE + " next to the model file.");
        }
        SubMonitor progress = SubMonitor.convert(monitor, "Exporting documentation",
                selection.views.size() + formats.size() + 1);
        // the PDF library uses AWT classes, which must not start a GUI next to SWT (it would hang on macOS)
        System.setProperty("java.awt.headless", "true");
        List<EObject> shown = new ArrayList<>();
        KeywordRules rules = ProjectRules.resolve(selection.model, rulesFile, Prefs::rules); // fails before any work
        if (profile != null) {
            rules = rules.with(profile);
        }
        if (assets != null) {
            rules = rules.withAssets(assets);
        }
        Exclusion exclusion = selection.exclusion(rules);
        Doc doc = ModelReader.read(selection, (view, page) -> {
            check(progress);
            progress.subTask("Drawing " + view.getName());
            ViewImages.renderToFile(view, page); // deleted when the export ends (structure-nav-pdf: pictures on disk)
            progress.worked(1);
        }, shown, rules, exclusion);
        Map<String, String> fingerprints = ExportState.fingerprints(shown); // as read, whatever changes meanwhile
        long time = System.currentTimeMillis();
        Published before = published(selection);
        Doc shownDoc = selection.wholeModel && before.lastTime() > 0 ? Published.with(doc, Published.page(
                before.changesTo(doc, true), DATE.format(Instant.ofEpochMilli(before.lastTime()).atZone(ZoneId.systemDefault())),
                rules.texts()::get))
                : doc;
        List<String> notes = new ArrayList<>();
        Doc generatedDoc = shownDoc;
        if (selection.wholeModel) { // catalogs and indexes describe the whole model
            List<DocPage> generated = new ArrayList<>();
            Catalogs.Result catalogs = Catalogs.build(doc, rules.catalogs(), rules.texts(),
                    path -> Profiles.folderElementIds(selection.model, path));
            generated.addAll(catalogs.pages);
            notes.addAll(catalogs.notes);
            Catalogs.Result indexes = DataIndex.build(doc, rules.indexes(), rules);
            generated.addAll(indexes.pages);
            notes.addAll(indexes.notes);
            generatedDoc = Generated.with(shownDoc, generated);
        }
        Exporter exporter = new Exporter(generatedDoc, rules).version(selection.model.getName(),
                selection.model.getFile() == null ? null : selection.model.getFile().toPath(), LocalDateTime.now());
        exporter.export(folder, formats, new Exporter.Progress() {
            @Override
            public void step(String task) {
                progress.subTask(task);
                progress.worked(1);
            }

            @Override
            public boolean cancelled() {
                return progress.isCanceled();
            }
        });
        try {
            ExportState.get().record(selection.model, fingerprints, time);
            ExportMarks.refresh();
        } catch (IOException | RuntimeException e) {
            Activator.log("The export worked, but what it showed could not be noted for the model tree marks", e);
        }
        if (publish) { // after the export worked; a failure here fails the run, since publishing was asked for
            ExportState.get().publish(selection.model, doc, fingerprints, time, selection.wholeModel);
            ExportMarks.refresh();
        }
        notes.addAll(doc.notes);
        return new Result(exporter.problems(), exclusion.count(), notes);
    }

    /** What was published of the model; nothing if that cannot be told, which must not stop an export. */
    private static Published published(ModelReader.Selection selection) {
        try {
            return ExportState.get().published(selection.model);
        } catch (RuntimeException e) {
            Activator.log("What was published could not be read, so the export has no page of changes", e);
            return new Published();
        }
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static void check(IProgressMonitor monitor) {
        if (monitor.isCanceled()) {
            throw new CancellationException("The export was cancelled");
        }
    }

    private static void finished(Display display, Path folder, Result result, boolean published) {
        List<String> problems = result.problems;
        String message = "Exported to " + folder + (published ? "\n\nNoted as published in " + Published.FILE
                + " next to the model file. Share that file with the model so your colleagues' marks follow." : "");
        if (result.excluded > 0) {
            message += "\n" + result.excluded + " object(s) were left out by the exclusion rules.";
        }
        for (String note : result.notes) {
            message += "\n" + note;
        }
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
