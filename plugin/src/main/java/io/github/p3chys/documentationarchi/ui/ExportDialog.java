package io.github.p3chys.documentationarchi.ui;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import io.github.p3chys.documentationarchi.render.Exporter.Format;

/** Asks where to export to and in which formats. */
final class ExportDialog extends Dialog {

    private final String what;
    private final Map<Format, Button> formatButtons = new EnumMap<>(Format.class);
    private Text folderText;
    private String folder;
    private Set<Format> formats;

    ExportDialog(Shell shell, String what) {
        super(shell);
        this.what = what;
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Export Documentation");
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite c = (Composite) super.createDialogArea(parent);
        c.setLayout(new GridLayout(3, false));

        Label label = new Label(c, SWT.NONE);
        label.setText(what);
        GridDataFactory.fillDefaults().span(3, 1).applyTo(label);

        new Label(c, SWT.NONE).setText("Folder:");
        folderText = new Text(c, SWT.BORDER);
        folderText.setText(Prefs.folder());
        GridDataFactory.fillDefaults().grab(true, false).hint(380, SWT.DEFAULT).applyTo(folderText);
        Button browse = new Button(c, SWT.PUSH);
        browse.setText("Browse...");
        browse.addListener(SWT.Selection, e -> {
            DirectoryDialog dialog = new DirectoryDialog(getShell());
            dialog.setFilterPath(folderText.getText());
            String chosen = dialog.open();
            if (chosen != null) {
                folderText.setText(chosen);
            }
        });

        Set<Format> selected = Prefs.formats();
        format(c, Format.HTML, "HTML pages", selected);
        format(c, Format.PDF, "PDF document", selected);
        format(c, Format.CONFLUENCE, "Confluence pages (storage format, with an upload script)", selected);
        return c;
    }

    private void format(Composite parent, Format format, String text, Set<Format> selected) {
        Button button = new Button(parent, SWT.CHECK);
        button.setText(text);
        button.setSelection(selected.contains(format));
        button.addListener(SWT.Selection, e -> updateOk());
        GridDataFactory.fillDefaults().span(3, 1).applyTo(button);
        formatButtons.put(format, button);
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, "Export", true);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
        updateOk();
    }

    private void updateOk() {
        getButton(IDialogConstants.OK_ID).setEnabled(formatButtons.values().stream().anyMatch(Button::getSelection));
    }

    @Override
    protected void okPressed() {
        folder = folderText.getText().strip();
        formats = EnumSet.noneOf(Format.class);
        formatButtons.forEach((format, button) -> {
            if (button.getSelection()) {
                formats.add(format);
            }
        });
        Prefs.folder(folder);
        Prefs.formats(formats);
        super.okPressed();
    }

    String folder() {
        return folder;
    }

    Set<Format> formats() {
        return formats;
    }
}
