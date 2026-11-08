package io.github.p3chys.documentationarchi.ui;

import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import io.github.p3chys.documentationarchi.text.KeywordRules.CodeTheme;
import io.github.p3chys.documentationarchi.text.KeywordRules.Kind;
import io.github.p3chys.documentationarchi.text.KeywordRules.Rule;

/** How one {@link PageElement} looks, and for SQL blocks also Confluence's theme and line numbers. */
final class ElementStyleDialog extends TitleAreaDialog {

    private final PageElement element;
    private final Rule initial;
    private StyleEditor style;
    private Combo theme;
    private Button lineNumbers;
    private Rule rule;

    /** @param initial the element's rule, null if it looks as usual */
    ElementStyleDialog(Shell shell, PageElement element, Rule initial) {
        super(shell);
        this.element = element;
        this.initial = initial;
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Edit Formatting");
    }

    @Override
    protected boolean isResizable() {
        return true;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        setTitle(element.name);
        setMessage("How this part of every page looks in the exported documentation.");
        Composite area = (Composite) super.createDialogArea(parent);
        Composite c = new Composite(area, SWT.NONE);
        GridLayoutFactory.swtDefaults().applyTo(c);
        GridDataFactory.fillDefaults().grab(true, true).applyTo(c);

        style = new StyleEditor(c, element.intro(), element.note, () -> { });
        GridDataFactory.fillDefaults().grab(true, true).applyTo(style.control());
        style.set(initial != null ? initial.style : element.startingStyle());
        style.sample(element.base(), element.before, element.sample, "");

        if (element.kind == Kind.SQL) {
            Group confluence = new Group(c, SWT.NONE);
            confluence.setText("Confluence code block");
            GridLayoutFactory.swtDefaults().numColumns(2).applyTo(confluence);
            GridDataFactory.fillDefaults().grab(true, false).applyTo(confluence);
            new Label(confluence, SWT.NONE).setText("&Theme:");
            theme = new Combo(confluence, SWT.READ_ONLY);
            theme.add("(Confluence's default)");
            for (CodeTheme t : CodeTheme.values()) {
                theme.add(t.confluenceName);
            }
            theme.select(initial == null || initial.theme == null ? 0 : initial.theme.ordinal() + 1);
            lineNumbers = new Button(confluence, SWT.CHECK);
            lineNumbers.setText("&Line numbers");
            lineNumbers.setSelection(initial != null && initial.lineNumbers);
            GridDataFactory.swtDefaults().span(2, 1).applyTo(lineNumbers);
        }
        return area;
    }

    @Override
    protected void okPressed() {
        CodeTheme chosen = theme == null || theme.getSelectionIndex() <= 0 ? null : CodeTheme.values()[theme.getSelectionIndex() - 1];
        rule = element.rule(style.get(), chosen, lineNumbers != null && lineNumbers.getSelection());
        super.okPressed();
    }

    /** The element's rule, once the dialog was closed with OK. */
    Rule rule() {
        return rule;
    }
}
