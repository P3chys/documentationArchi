package io.github.p3chys.documentationarchi.ui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import com.archimatetool.editor.ui.ArchiLabelProvider;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IWorkPackage;

/**
 * The one window for the model's work packages (Jira tickets): how many elements each changes, how many of those no
 * export shows as they are now, and the elements of the selected ones. From here they are made, exported, shown in
 * the model tree and marked done.
 * <p>
 * Opened with elements selected, the work packages get ticks: tick one to link all the elements to it, untick it to
 * take them off; a grey tick means only some are linked. Every change is made at once as one command, so Undo takes
 * it back and the window has nothing to confirm: its only way out is Close.
 */
final class WorkPackagesDialog extends TitleAreaDialog {

    /** Return code: export the selected work packages. */
    static final int EXPORT = IDialogConstants.CLIENT_ID + 1;
    /** Return code: show {@link #shown()} in the model tree. */
    static final int SHOW = IDialogConstants.CLIENT_ID + 2;
    /** Link what changed since the documentation was published; the handler does it once the dialog is closed. */
    static final int CHANGED = IDialogConstants.CLIENT_ID + 3;
    private static final int DONE = IDialogConstants.CLIENT_ID + 4;

    private final IArchimateModel model;
    /** The elements the ticks link; empty when the window was opened without elements selected. */
    private final List<IArchimateElement> linking;
    /** The kind of change found for each element (see {@link WorkPackages#unassigned}); null when none was. */
    private final Map<IArchimateElement, String> found;
    private Table packages;
    private Table elements;
    private Text name;
    private Button add;
    private Combo kinds;
    private Button export;
    private Button show;
    private Button done;
    private Button showClosed;
    private final List<IWorkPackage> selected = new ArrayList<>();
    private final List<Object> shown = new ArrayList<>();
    /** What the title area says about the window, once it no longer says that the model has no work packages. */
    private String message;

    /**
     * @param linking the elements to link to work packages, empty for none
     * @param found the kind of change to suggest per element, for elements found changed; null for none
     */
    WorkPackagesDialog(Shell shell, IArchimateModel model, List<IArchimateElement> linking,
            Map<IArchimateElement, String> found) {
        super(shell);
        this.model = model;
        this.linking = linking;
        this.found = found;
    }

    private boolean linkMode() {
        return !linking.isEmpty();
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Work Packages - " + ModelReader.label(model));
    }

    @Override
    protected boolean isResizable() {
        return true;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        Composite c = new Composite(area, SWT.NONE);
        GridLayoutFactory.swtDefaults().numColumns(3).applyTo(c);
        GridDataFactory.fillDefaults().grab(true, true).applyTo(c);

        if (linkMode()) {
            String what = linking.size() == 1 ? "\"" + ModelReader.label(linking.get(0)) + "\"" : linking.size() + " elements";
            setTitle(found != null ? "Link " + what + " changed since the last publish" : "Link " + what);
            message = ("Tick the work packages that change " + (linking.size() == 1 ? "it" : "them") + ", untick to take "
                    + "the link off. A grey tick: only some are linked. Changes are made at once; Edit > Undo takes them back.");
        } else {
            setTitle("Work packages");
            message = ("The Jira tickets of the model and the elements each changes. Select one to see its elements, "
                    + "double-click to export it. To link elements, select them first and open this window from their menu.");
        }

        setMessage(message);
        packages = table(c, SWT.MULTI | (linkMode() ? SWT.CHECK : SWT.NONE), 200);
        column(packages, "Work package", 200);
        column(packages, "Elements", 70);
        column(packages, "Not exported", 100);
        column(packages, "Description", 300);
        packages.addListener(SWT.Selection, e -> {
            if (e.detail == SWT.CHECK) {
                ticked((TableItem) e.item);
            } else {
                refresh();
            }
        });
        packages.addListener(SWT.DefaultSelection, e -> buttonPressed(EXPORT));

        showClosed = new Button(c, SWT.CHECK);
        showClosed.setText("Show work packages marked &done");
        showClosed.setToolTipText("Work packages whose ticket is done stay in the model with their links, but are "
                + "otherwise not listed");
        showClosed.addListener(SWT.Selection, e -> fill(selected));
        GridDataFactory.fillDefaults().span(3, 1).applyTo(showClosed);

        new Label(c, SWT.NONE).setText("&New work package:");
        name = new Text(c, SWT.BORDER);
        name.setMessage("Jira ticket, e.g. RBWDP-20025");
        GridDataFactory.fillDefaults().grab(true, false).applyTo(name);
        name.addListener(SWT.Modify, e -> validate());
        name.addListener(SWT.Traverse, e -> {
            if (e.detail == SWT.TRAVERSE_RETURN) {
                e.doit = false;
                add();
            }
        });
        add = new Button(c, SWT.PUSH);
        add.setText(linkMode() ? "&Add and Link" : "&Add");
        add.setToolTipText(linkMode() ? "Add a work package named after the ticket and link the selection to it"
                : "Add a work package named after the ticket to the model");
        add.addListener(SWT.Selection, e -> add());

        if (linkMode()) {
            new Label(c, SWT.NONE).setText("&Kind of change:");
            kinds = new Combo(c, SWT.DROP_DOWN | SWT.READ_ONLY);
            if (found != null) {
                kinds.add("as found: new or change");
            }
            kinds.add("(not said)");
            WorkPackages.KINDS.forEach(kinds::add);
            kinds.select(0);
            kinds.setToolTipText("Names the links to the ticked work packages, and shows in the documentation next to "
                    + "the ticket: RBWDP-20025 (new). Choosing one renames the links already ticked.");
            kinds.addListener(SWT.Selection, e -> kindChosen());
            GridDataFactory.fillDefaults().span(2, 1).applyTo(kinds);
        }

        Label label = new Label(c, SWT.NONE);
        label.setText("Elements the selected work packages change:");
        GridDataFactory.fillDefaults().span(3, 1).indent(0, 8).applyTo(label);
        elements = table(c, SWT.MULTI, 160);
        column(elements, "Element", 260);
        column(elements, "Type", 160);
        column(elements, "Export", 240);
        elements.addListener(SWT.Selection, e -> updateButtons());

        fill(List.of());
        if (WorkPackages.of(model).isEmpty()) {
            setMessage("The model has no work packages yet. Type a Jira ticket under \"New work package\" and press Add.");
            name.setFocus();
        }
        return area;
    }

    /** Lists the work packages: the open ones, the closed ones when asked for, and those linked to the selection. */
    private void fill(List<IWorkPackage> keep) {
        packages.setRedraw(false);
        packages.removeAll();
        ExportState state = ExportState.get();
        List<TableItem> reselect = new ArrayList<>();
        for (IWorkPackage wp : WorkPackages.of(model)) {
            List<IArchimateElement> changed = WorkPackages.elements(wp);
            long linked = linking.stream().filter(changed::contains).count();
            if (WorkPackages.closed(wp) && !showClosed.getSelection() && linked == 0) {
                continue;
            }
            int behind = 0;
            for (IArchimateElement e : changed) {
                behind += behind(state, e) ? 1 : 0;
            }
            TableItem item = new TableItem(packages, SWT.NONE);
            item.setData(wp);
            item.setText(new String[] { ModelReader.label(wp) + (WorkPackages.closed(wp) ? " (done)" : ""),
                    String.valueOf(changed.size()), behind == 0 ? "" : String.valueOf(behind),
                    firstLine(wp.getDocumentation()) });
            item.setImage(0, ArchiLabelProvider.INSTANCE.getImage(wp));
            if (linkMode()) {
                item.setChecked(linked > 0);
                item.setGrayed(linked > 0 && linked < linking.size());
            }
            if (keep.contains(wp)) {
                reselect.add(item);
            }
        }
        packages.setSelection(reselect.toArray(new TableItem[0]));
        packages.setRedraw(true);
        refresh();
    }

    /** A tick links the selection to the work package, no tick takes it off; a grey tick clicked becomes a full one. */
    private void ticked(TableItem item) {
        IWorkPackage wp = (IWorkPackage) item.getData();
        boolean link = item.getChecked() || item.getGrayed();
        ModelCommands.execute(model, link
                ? WorkPackages.change(model, linking, List.of(wp), List.of(), null, kind())
                : WorkPackages.change(model, linking, List.of(), List.of(wp), null));
        fill(selected);
    }

    /** The kind of change chosen, for each element. */
    private Function<IArchimateElement, String> kind() {
        if (kinds == null) {
            return e -> null;
        }
        String chosen = kinds.getText();
        return WorkPackages.KINDS.contains(chosen) ? e -> chosen
                : found != null && kinds.getSelectionIndex() == 0 ? found::get : e -> null;
    }

    /** Names the links to the work packages already fully ticked after the kind just chosen. */
    private void kindChosen() {
        List<IWorkPackage> ticked = new ArrayList<>();
        for (TableItem item : packages.getItems()) {
            if (item.getChecked() && !item.getGrayed()) {
                ticked.add((IWorkPackage) item.getData());
            }
        }
        if (!ticked.isEmpty()) {
            ModelCommands.execute(model, WorkPackages.change(model, linking, ticked, List.of(), null, kind()));
            fill(selected);
        }
    }

    private void add() {
        String text = name.getText().strip();
        if (!WorkPackages.validName(text)) {
            validate();
            return;
        }
        ModelCommands.execute(model, WorkPackages.change(model, linking, List.of(), List.of(), text, kind()));
        name.setText("");
        IWorkPackage made = WorkPackages.named(model, text);
        if (made != null && WorkPackages.closed(made)) {
            showClosed.setSelection(true);
        }
        fill(made == null ? List.of() : List.of(made));
        setMessage(message);
    }

    private void validate() {
        String text = name.getText().strip();
        boolean valid = text.isEmpty() || WorkPackages.validName(text);
        setErrorMessage(valid ? null : "A work package is named after its Jira ticket: the key first, such as RBWDP-20025.");
        add.setEnabled(!text.isEmpty() && valid);
    }

    static String firstLine(String text) {
        String line = text == null ? "" : text.strip().split("\\R", 2)[0];
        return line.length() > 80 ? line.substring(0, 80) + "..." : line;
    }

    private static Table table(Composite parent, int style, int height) {
        Table table = new Table(parent, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL | style);
        table.setHeaderVisible(true);
        GridDataFactory.fillDefaults().span(3, 1).grab(true, true).hint(SWT.DEFAULT, height).applyTo(table);
        return table;
    }

    private static void column(Table table, String text, int width) {
        TableColumn column = new TableColumn(table, SWT.NONE);
        column.setText(text);
        column.setWidth(width);
    }

    /** Whether the element, or a link of it to a work package, is not as the last export showed it. */
    private static boolean behind(ExportState state, IArchimateElement element) {
        if (status(state, element) != ExportState.Status.CURRENT) {
            return true;
        }
        for (IWorkPackage wp : WorkPackages.of(element)) {
            for (IArchimateRelationship r : WorkPackages.links(wp).getOrDefault(element, List.of())) {
                if (status(state, r) != ExportState.Status.CURRENT) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ExportState.Status status(ExportState state, EObject o) {
        ExportState.Mark mark = state.mark(o);
        return mark == null ? ExportState.Status.CURRENT : mark.status;
    }

    private void refresh() {
        selected.clear();
        for (TableItem item : packages.getSelection()) {
            selected.add((IWorkPackage) item.getData());
        }
        Set<IArchimateElement> changed = new LinkedHashSet<>();
        selected.forEach(wp -> changed.addAll(WorkPackages.elements(wp)));
        elements.setRedraw(false);
        elements.removeAll();
        ExportState state = ExportState.get();
        ArchiLabelProvider labels = ArchiLabelProvider.INSTANCE;
        for (IArchimateElement e : changed) {
            ExportState.Status status = status(state, e);
            String export = status == ExportState.Status.NEW ? "in no export yet"
                    : status == ExportState.Status.CHANGED ? "changed since the last export"
                    : behind(state, e) ? "newly linked since the last export" : "";
            Set<String> kindsOf = new LinkedHashSet<>();
            selected.forEach(wp -> {
                String kind = WorkPackages.kind(wp, e);
                if (kind != null) {
                    kindsOf.add(kind);
                }
            });
            TableItem item = new TableItem(elements, SWT.NONE);
            item.setData(e);
            item.setText(new String[] { ModelReader.label(e) + (kindsOf.isEmpty() ? "" : " (" + String.join(", ", kindsOf) + ")"),
                    labels.getDefaultName(e.eClass()), export });
            item.setImage(0, labels.getImage(e));
        }
        elements.setRedraw(true);
        updateButtons();
    }

    private void updateButtons() {
        if (export == null) {
            return; // the tables are filled before the buttons are made
        }
        export.setEnabled(!selected.isEmpty() && elements.getItemCount() > 0);
        show.setEnabled(!selected.isEmpty());
        done.setEnabled(!selected.isEmpty());
        done.setText(!selected.isEmpty() && selected.stream().allMatch(WorkPackages::closed) ? "&Reopen" : "Mark &Done");
        done.getParent().layout();
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        Button changed = createButton(parent, CHANGED, "&Changed Since Publish...", false);
        changed.setToolTipText("List the elements that changed since the documentation was published and that no open "
                + "work package changes yet, to link them");
        done = createButton(parent, DONE, "Mark &Done", false);
        done.setToolTipText("The ticket is done: the work package stays in the model with its links, but is no longer "
                + "listed here or in the exports of other things. Reopen brings it back. Undo works.");
        show = createButton(parent, SHOW, "&Show in Model Tree", false);
        show.setToolTipText("Select the chosen elements, or else the selected work packages, in the model tree");
        export = createButton(parent, EXPORT, "&Export...", false);
        export.setToolTipText("Export the selected work packages: only the elements they change");
        createButton(parent, IDialogConstants.CLOSE_ID, IDialogConstants.CLOSE_LABEL, true);
        updateButtons();
        validate();
    }

    @Override
    protected void buttonPressed(int id) {
        if (id == EXPORT && export.isEnabled() || id == SHOW && show.isEnabled()) {
            shown.clear();
            for (TableItem item : elements.getSelection()) {
                shown.add(item.getData());
            }
            if (shown.isEmpty()) {
                shown.addAll(selected);
            }
            setReturnCode(id);
            close();
        } else if (id == CHANGED) {
            setReturnCode(id);
            close();
        } else if (id == DONE && done.isEnabled()) {
            boolean close = !selected.stream().allMatch(WorkPackages::closed);
            ModelCommands.execute(model, WorkPackages.close(selected, close));
            if (close) {
                showClosed.setSelection(true); // so what was just marked stays in sight, with Reopen
            }
            fill(selected);
        } else if (id == IDialogConstants.CLOSE_ID) {
            setReturnCode(CANCEL);
            close();
        }
    }

    /** The work packages selected when the dialog closed. */
    List<IWorkPackage> selected() {
        return selected;
    }

    /** What to show in the model tree. */
    List<Object> shown() {
        return shown;
    }
}
