package io.github.p3chys.documentationarchi.ui;

import java.util.Locale;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import io.github.p3chys.documentationarchi.text.KeywordRules.Kind;
import io.github.p3chys.documentationarchi.text.KeywordRules.PanelStyle;
import io.github.p3chys.documentationarchi.text.KeywordRules.Rule;
import io.github.p3chys.documentationarchi.text.KeywordRules.TagColour;
import io.github.p3chys.documentationarchi.text.KeywordRules.TextStyle;

/** Adds or edits one keyword: what it is, where it is recognised and how it looks. */
final class KeywordRuleDialog extends TitleAreaDialog {

    private static final String[] KINDS = { "Text style - the text anywhere in a description",
        "Label - #keyword or [[keyword]]", "Panel - a line starting with \"keyword:\"" };
    static final String INLINE_NOTE = "Confluence keeps text colour, bold, italic, underline, strikethrough and smaller "
            + "text. Highlight and larger text show in HTML and PDF, and in Confluence only where it keeps them.";

    private final Rule initial;
    private Rule rule;

    private Text keyword;
    private Combo kind;
    private Composite options;
    private StackLayout stack;
    private StyleEditor style;
    private Composite tagPage;
    private Composite panelPage;
    private Combo tagColour;
    private Combo panelStyle;

    /** @param initial the rule to edit, null for a new one */
    KeywordRuleDialog(Shell shell, Rule initial) {
        super(shell);
        this.initial = initial;
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(initial == null ? "Add Keyword" : "Edit Keyword");
    }

    @Override
    protected boolean isResizable() {
        return true;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        setTitle(initial == null ? "Add Keyword" : "Edit Keyword");
        setMessage("How a keyword in descriptions looks in the exported documentation.");
        Composite area = (Composite) super.createDialogArea(parent);
        Composite c = new Composite(area, SWT.NONE);
        GridLayoutFactory.swtDefaults().numColumns(2).applyTo(c);
        GridDataFactory.fillDefaults().grab(true, true).applyTo(c);

        new Label(c, SWT.NONE).setText("&Keyword:");
        keyword = new Text(c, SWT.BORDER);
        GridDataFactory.fillDefaults().grab(true, false).hint(320, SWT.DEFAULT).applyTo(keyword);

        new Label(c, SWT.NONE).setText("&Type:");
        kind = new Combo(c, SWT.READ_ONLY);
        kind.setItems(KINDS);
        GridDataFactory.fillDefaults().grab(true, false).applyTo(kind);

        options = new Composite(c, SWT.NONE);
        stack = new StackLayout();
        options.setLayout(stack);
        GridDataFactory.fillDefaults().grab(true, true).span(2, 1).indent(0, 8).applyTo(options);
        style = new StyleEditor(options, "Default: like the text around it (text " + PageElement.TEXT_COLOUR + ").\n"
                + "The settings below change it; unchecked or 100 % keeps it.", INLINE_NOTE, this::update);
        tagPage = new Composite(options, SWT.NONE);
        tagColour = choice(tagPage, "Label &colour:", TagColour.values());
        panelPage = new Composite(options, SWT.NONE);
        panelStyle = choice(panelPage, "Panel &style:", PanelStyle.values());

        load(initial != null ? initial : Rule.style("Keyword", TextStyle.BOLD));
        keyword.addListener(SWT.Modify, e -> update());
        kind.addListener(SWT.Selection, e -> update());
        update();
        keyword.setFocus();
        keyword.selectAll();
        return area;
    }

    private Combo choice(Composite page, String label, Enum<?>[] values) {
        GridLayoutFactory.fillDefaults().numColumns(2).applyTo(page);
        new Label(page, SWT.NONE).setText(label);
        Combo combo = new Combo(page, SWT.READ_ONLY);
        for (Enum<?> value : values) {
            combo.add(value.name().toLowerCase(Locale.ROOT));
        }
        combo.select(0);
        combo.addListener(SWT.Selection, e -> update());
        return combo;
    }

    private void load(Rule r) {
        keyword.setText(r.keyword);
        kind.select(r.kind == Kind.TAG ? 1 : r.kind == Kind.PANEL ? 2 : 0);
        if (r.tag != null) {
            tagColour.select(r.tag.ordinal());
        }
        if (r.panel != null) {
            panelStyle.select(r.panel.ordinal());
        }
        if (r.style != null) {
            style.set(r.style);
        }
    }

    private Kind kind() {
        return kind.getSelectionIndex() == 1 ? Kind.TAG : kind.getSelectionIndex() == 2 ? Kind.PANEL : Kind.STYLE;
    }

    private void update() {
        Kind k = kind();
        stack.topControl = k == Kind.STYLE ? style.control() : k == Kind.TAG ? tagPage : panelPage;
        options.layout();
        String text = keyword.getText().strip();
        String problem = problem(k, text);
        rule = null;
        if (problem == null) {
            try {
                rule = k == Kind.TAG ? Rule.tag(text, TagColour.values()[tagColour.getSelectionIndex()])
                        : k == Kind.PANEL ? Rule.panel(text, PanelStyle.values()[panelStyle.getSelectionIndex()])
                        : Rule.style(text, style.get());
            } catch (IllegalArgumentException e) {
                problem = e.getMessage();
            }
        }
        setErrorMessage(problem);
        if (getButton(IDialogConstants.OK_ID) != null) {
            getButton(IDialogConstants.OK_ID).setEnabled(rule != null);
        }
        style.sample(StyleEditor.Base.TEXT, "Text with ", text, " in it.");
    }

    /** What would keep the keyword from ever being recognised, null if nothing. */
    private static String problem(Kind kind, String text) {
        if (text.isEmpty()) {
            return "Enter the keyword.";
        }
        if (kind == Kind.PANEL && (text.contains(":") || text.length() > 40)) {
            return "A panel keyword is at most 40 characters, without \":\".";
        }
        if (kind == Kind.TAG && text.contains("]")) {
            return "A label keyword cannot contain \"]\".";
        }
        return null;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        super.createButtonsForButtonBar(parent);
        update();
    }

    /** The rule entered, once the dialog was closed with OK. */
    Rule rule() {
        return rule;
    }
}
