package io.github.p3chys.documentationarchi.ui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import com.archimatetool.editor.ui.ArchiLabelProvider;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IWorkPackage;

/**
 * The overview of the model's work packages: how many elements each changes, how many of those no export shows as
 * they are now, and the elements of the selected ones. From here they are exported or shown in the model tree.
 */
final class WorkPackagesDialog extends TitleAreaDialog {

    /** Return code: export the selected work packages. */
    static final int EXPORT = IDialogConstants.CLIENT_ID + 1;
    /** Return code: show {@link #shown()} in the model tree. */
    static final int SHOW = IDialogConstants.CLIENT_ID + 2;

    private final IArchimateModel model;
    private Table packages;
    private Table elements;
    private Button export;
    private Button show;
    private final List<IWorkPackage> selected = new ArrayList<>();
    private final List<Object> shown = new ArrayList<>();

    WorkPackagesDialog(Shell shell, IArchimateModel model) {
        super(shell);
        this.model = model;
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
        setTitle("Work packages");
        setMessage("The Jira tickets of the model and the elements each changes. \"Not exported\" counts what changed "
                + "since the last export that showed it, or no export showed yet.");
        Composite c = new Composite(area, SWT.NONE);
        c.setLayout(new GridLayout(1, false));
        GridDataFactory.fillDefaults().grab(true, true).applyTo(c);

        packages = table(c, SWT.MULTI, 200);
        column(packages, "Work package", 200);
        column(packages, "Elements", 70);
        column(packages, "Not exported", 100);
        column(packages, "Description", 300);
        ExportState state = ExportState.get();
        for (IWorkPackage wp : WorkPackages.of(model)) {
            List<IArchimateElement> changed = WorkPackages.elements(wp);
            int behind = 0;
            for (IArchimateElement e : changed) {
                behind += behind(state, e) ? 1 : 0;
            }
            TableItem item = new TableItem(packages, SWT.NONE);
            item.setData(wp);
            item.setText(new String[] { ModelReader.label(wp), String.valueOf(changed.size()),
                    behind == 0 ? "" : String.valueOf(behind), WorkPackageDialog.firstLine(wp.getDocumentation()) });
            item.setImage(0, ArchiLabelProvider.INSTANCE.getImage(wp));
        }
        packages.addListener(SWT.Selection, e -> refresh());
        packages.addListener(SWT.DefaultSelection, e -> buttonPressed(EXPORT));

        Label label = new Label(c, SWT.NONE);
        label.setText("Elements they change:");
        elements = table(c, SWT.MULTI, 200);
        column(elements, "Element", 260);
        column(elements, "Type", 160);
        column(elements, "Export", 240);
        elements.addListener(SWT.Selection, e -> updateButtons());

        if (packages.getItemCount() == 0) {
            setMessage("The model has no work packages yet. Right-click elements and choose \"Link to Work Package...\" "
                    + "to make one, named after its Jira ticket.");
        }
        return area;
    }

    private static Table table(Composite parent, int style, int height) {
        Table table = new Table(parent, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL | style);
        table.setHeaderVisible(true);
        GridDataFactory.fillDefaults().grab(true, true).hint(SWT.DEFAULT, height).applyTo(table);
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
            TableItem item = new TableItem(elements, SWT.NONE);
            item.setData(e);
            item.setText(new String[] { ModelReader.label(e), labels.getDefaultName(e.eClass()), export });
            item.setImage(0, labels.getImage(e));
        }
        elements.setRedraw(true);
        updateButtons();
    }

    private void updateButtons() {
        export.setEnabled(!selected.isEmpty() && elements.getItemCount() > 0);
        show.setEnabled(!selected.isEmpty());
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        show = createButton(parent, SHOW, "&Show in Model Tree", false);
        show.setToolTipText("Select the chosen elements, or else the selected work packages, in the model tree");
        export = createButton(parent, EXPORT, "&Export...", true);
        export.setToolTipText("Export the selected work packages: only the elements they change");
        createButton(parent, IDialogConstants.CLOSE_ID, IDialogConstants.CLOSE_LABEL, false);
        updateButtons();
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
