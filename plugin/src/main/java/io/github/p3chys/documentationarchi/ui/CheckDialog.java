package io.github.p3chys.documentationarchi.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import com.archimatetool.model.IArchimateModel;

/** The findings of Check Documentation, grouped by kind with counts. Nothing is exported. */
final class CheckDialog extends TitleAreaDialog {

    /** Return code: select {@link #shown()} in the model tree. */
    static final int SHOW = IDialogConstants.CLIENT_ID + 1;

    private final IArchimateModel model;
    private final List<DocCheck.Finding> findings;
    private Tree tree;
    private Button show;
    private final List<Object> shown = new ArrayList<>();

    CheckDialog(Shell shell, IArchimateModel model, List<DocCheck.Finding> findings) {
        super(shell);
        this.model = model;
        this.findings = findings;
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Check Documentation - " + ModelReader.label(model));
    }

    @Override
    protected boolean isResizable() {
        return true;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        setTitle("Check Documentation");
        setMessage(findings.size() + " finding(s). Nothing was exported. Select findings and choose \"Show in Model Tree\".");
        Composite c = new Composite(area, SWT.NONE);
        c.setLayout(new GridLayout(1, false));
        GridDataFactory.fillDefaults().grab(true, true).applyTo(c);
        tree = new Tree(c, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
        GridDataFactory.fillDefaults().grab(true, true).hint(560, 360).applyTo(tree);

        Map<DocCheck.Kind, List<DocCheck.Finding>> byKind = new LinkedHashMap<>();
        findings.forEach(f -> byKind.computeIfAbsent(f.kind, k -> new ArrayList<>()).add(f));
        byKind.forEach((kind, list) -> {
            TreeItem group = new TreeItem(tree, SWT.NONE);
            group.setText(kind.title + " (" + list.size() + ")");
            group.setData(list);
            for (DocCheck.Finding f : list) {
                TreeItem item = new TreeItem(group, SWT.NONE);
                item.setText(f.line());
                item.setData(f);
            }
            group.setExpanded(true);
        });
        tree.addListener(SWT.Selection, e -> updateButtons());
        tree.addListener(SWT.DefaultSelection, e -> buttonPressed(SHOW));
        return area;
    }

    private void updateButtons() {
        show.setEnabled(tree.getSelectionCount() > 0);
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        show = createButton(parent, SHOW, "&Show in Model Tree", true);
        show.setToolTipText("Select the objects of the chosen findings in the model tree");
        createButton(parent, IDialogConstants.CLOSE_ID, IDialogConstants.CLOSE_LABEL, false);
        updateButtons();
    }

    @Override
    protected void buttonPressed(int id) {
        if (id == SHOW && show.isEnabled()) {
            Set<Object> objects = new LinkedHashSet<>();
            for (TreeItem item : tree.getSelection()) {
                if (item.getData() instanceof DocCheck.Finding) {
                    objects.add(((DocCheck.Finding) item.getData()).object);
                } else {
                    for (Object f : (List<?>) item.getData()) {
                        objects.add(((DocCheck.Finding) f).object);
                    }
                }
            }
            shown.addAll(objects);
            setReturnCode(SHOW);
            close();
        } else if (id == IDialogConstants.CLOSE_ID) {
            setReturnCode(CANCEL);
            close();
        }
    }

    /** The objects of the chosen findings, to select in the model tree. */
    List<Object> shown() {
        return shown;
    }
}
