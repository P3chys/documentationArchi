package io.github.p3chys.documentationarchi.ui;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import io.github.p3chys.documentationarchi.text.KeywordRules;

/** All keyword rules as text, one per line, for copying between machines or editing many at once. */
final class KeywordTextDialog extends TitleAreaDialog {

    private final String initial;
    private Text text;
    private KeywordRules rules;

    KeywordTextDialog(Shell shell, String initial) {
        super(shell);
        this.initial = initial;
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Keywords as Text");
    }

    @Override
    protected boolean isResizable() {
        return true;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        setTitle("Keywords as Text");
        setMessage("One rule per line; lines starting with # are explanations and are not kept.");
        Composite area = (Composite) super.createDialogArea(parent);
        text = new Text(area, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
        text.setFont(JFaceResources.getTextFont());
        text.setText(initial);
        text.addListener(SWT.Modify, e -> validate());
        GridDataFactory.fillDefaults().grab(true, true).hint(620, 320).indent(8, 8).applyTo(text);
        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        super.createButtonsForButtonBar(parent);
        validate();
    }

    private void validate() {
        try {
            rules = KeywordRules.parse(text.getText());
            setErrorMessage(null);
        } catch (IllegalArgumentException e) {
            rules = null;
            setErrorMessage(e.getMessage());
        }
        if (getButton(IDialogConstants.OK_ID) != null) {
            getButton(IDialogConstants.OK_ID).setEnabled(rules != null);
        }
    }

    /** The rules entered, once the dialog was closed with OK. */
    KeywordRules rules() {
        return rules;
    }
}
