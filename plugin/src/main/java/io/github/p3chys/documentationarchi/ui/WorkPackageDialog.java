package io.github.p3chys.documentationarchi.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
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
import com.archimatetool.model.IWorkPackage;

/**
 * Which work packages change the selected elements: tick a work package to link all of them to it, untick it to take
 * them off. A grey tick means only some of them are linked, and stays so unless clicked. A new work package is named
 * after its Jira ticket.
 */
final class WorkPackageDialog extends TitleAreaDialog {

    private final List<IArchimateElement> elements;
    private final List<IWorkPackage> workPackages;
    private Table table;
    private Text name;
    private Combo kinds;
    /** The kind of change found for each element (see {@link WorkPackages#unassigned}); null when none was. */
    private final Map<IArchimateElement, String> found;
    private Function<IArchimateElement, String> kind = e -> null;
    private final List<IWorkPackage> link = new ArrayList<>();
    private final List<IWorkPackage> unlink = new ArrayList<>();
    private String create;

    WorkPackageDialog(Shell shell, IArchimateModel model, List<IArchimateElement> elements) {
        this(shell, model, elements, null);
    }

    /** @param found the kind of change to suggest per element, for elements found changed; null for none */
    WorkPackageDialog(Shell shell, IArchimateModel model, List<IArchimateElement> elements,
            Map<IArchimateElement, String> found) {
        super(shell);
        this.elements = elements;
        this.found = found;
        // closed ones only where an element is still linked, so the link can be taken off
        this.workPackages = WorkPackages.of(model);
        this.workPackages.removeIf(wp -> WorkPackages.closed(wp)
                && elements.stream().noneMatch(WorkPackages.elements(wp)::contains));
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Link to Work Package");
    }

    @Override
    protected boolean isResizable() {
        return true;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        setTitle(elements.size() == 1 ? ModelReader.label(elements.get(0)) : elements.size() + " elements");
        setMessage("The work packages (Jira tickets) that change " + (elements.size() == 1 ? "this element" : "these elements")
                + ". Exporting a work package exports only the elements it changes.");
        Composite c = new Composite(area, SWT.NONE);
        c.setLayout(new GridLayout(2, false));
        GridDataFactory.fillDefaults().grab(true, true).applyTo(c);

        table = new Table(c, SWT.BORDER | SWT.CHECK | SWT.FULL_SELECTION | SWT.V_SCROLL);
        table.setHeaderVisible(true);
        column("Work package", 220);
        column("Elements", 80);
        column("Description", 260);
        GridDataFactory.fillDefaults().span(2, 1).grab(true, true).hint(SWT.DEFAULT, 260).applyTo(table);
        for (IWorkPackage wp : workPackages) {
            List<IArchimateElement> changed = WorkPackages.elements(wp);
            long linked = elements.stream().filter(changed::contains).count();
            TableItem item = new TableItem(table, SWT.NONE);
            item.setData(wp);
            item.setText(new String[] { ModelReader.label(wp) + (WorkPackages.closed(wp) ? " (closed)" : ""),
                    String.valueOf(changed.size()), firstLine(wp.getDocumentation()) });
            item.setImage(0, ArchiLabelProvider.INSTANCE.getImage(wp));
            item.setChecked(linked > 0);
            item.setGrayed(linked > 0 && linked < elements.size());
        }
        // a click on a grey tick makes it a full one: all elements linked
        table.addListener(SWT.Selection, e -> {
            if (e.detail == SWT.CHECK && e.item instanceof TableItem && ((TableItem) e.item).getGrayed()) {
                ((TableItem) e.item).setGrayed(false);
                ((TableItem) e.item).setChecked(true);
            }
        });

        new Label(c, SWT.NONE).setText("&New work package:");
        name = new Text(c, SWT.BORDER);
        name.setMessage("Jira ticket, e.g. RBWDP-20025");
        GridDataFactory.fillDefaults().grab(true, false).applyTo(name);
        name.addListener(SWT.Modify, e -> validate());
        if (workPackages.isEmpty()) {
            name.setFocus();
        }

        new Label(c, SWT.NONE).setText("&Kind of change:");
        kinds = new Combo(c, SWT.DROP_DOWN | SWT.READ_ONLY);
        if (found != null) {
            kinds.add("as found: new or change");
        }
        kinds.add("(not said)");
        WorkPackages.KINDS.forEach(kinds::add);
        kinds.select(0);
        kinds.setToolTipText("Names the links to the ticked work packages, and shows in the documentation next to the "
                + "ticket: RBWDP-20025 (new). \"(not said)\" leaves the links as they are.");
        return area;
    }

    private void column(String text, int width) {
        TableColumn column = new TableColumn(table, SWT.NONE);
        column.setText(text);
        column.setWidth(width);
    }

    static String firstLine(String text) {
        String line = text == null ? "" : text.strip().split("\\R", 2)[0];
        return line.length() > 80 ? line.substring(0, 80) + "..." : line;
    }

    private void validate() {
        String text = name.getText().strip();
        boolean valid = text.isEmpty() || WorkPackages.validName(text);
        setErrorMessage(valid ? null : "A work package is named after its Jira ticket: the key first, such as RBWDP-20025.");
        getButton(IDialogConstants.OK_ID).setEnabled(valid);
    }

    @Override
    protected void okPressed() {
        for (TableItem item : table.getItems()) {
            IWorkPackage wp = (IWorkPackage) item.getData();
            if (item.getChecked() && !item.getGrayed()) {
                link.add(wp);
            } else if (!item.getChecked()) {
                unlink.add(wp);
            }
        }
        create = name.getText().strip().isEmpty() ? null : name.getText().strip();
        String chosen = kinds.getText();
        kind = WorkPackages.KINDS.contains(chosen) ? e -> chosen
                : found != null && kinds.getSelectionIndex() == 0 ? found::get : e -> null;
        super.okPressed();
    }

    List<IWorkPackage> link() {
        return link;
    }

    List<IWorkPackage> unlink() {
        return unlink;
    }

    /** The kind of change to each element, null for one where it isn't said. */
    Function<IArchimateElement, String> kind() {
        return kind;
    }

    /** The name of a work package to make, null for none. */
    String newName() {
        return create;
    }
}
