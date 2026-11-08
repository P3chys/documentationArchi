package io.github.p3chys.documentationarchi.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.resource.ColorDescriptor;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import io.github.p3chys.documentationarchi.text.KeywordRules;
import io.github.p3chys.documentationarchi.text.KeywordRules.Kind;
import io.github.p3chys.documentationarchi.text.KeywordRules.Rule;
import io.github.p3chys.documentationarchi.text.KeywordRules.TextStyle;

/**
 * Preferences > Documentation Export: one list of everything whose look can be changed. The page parts (tokens,
 * headings, SQL blocks) are always there and can be reset to their default; keywords can be added and removed.
 */
public class DocumentationPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

    static final String ID = "io.github.p3chys.documentationarchi.prefs";

    private final List<Rule> keywords = new ArrayList<>();
    private final Map<PageElement, Rule> elementRules = new LinkedHashMap<>();
    private TableViewer table;
    private LocalResourceManager resources;
    private Button edit;
    private Button remove;
    private Button up;
    private Button down;
    /** False until the list is changed: saved rules that could not be loaded are not overwritten by an empty list. */
    private boolean changed;

    @Override
    public void init(IWorkbench workbench) {
        setDescription("How the exported documentation (HTML, PDF and Confluence) formats the parts of every page and "
                + "keywords in descriptions.");
    }

    @Override
    protected Control createContents(Composite parent) {
        Composite c = new Composite(parent, SWT.NONE);
        GridLayoutFactory.fillDefaults().numColumns(2).applyTo(c);
        resources = new LocalResourceManager(JFaceResources.getResources(), c);

        table = new TableViewer(c, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.V_SCROLL);
        Table t = table.getTable();
        t.setHeaderVisible(true);
        t.setLinesVisible(true);
        GridDataFactory.fillDefaults().grab(true, true).hint(620, 380).applyTo(t);
        table.setContentProvider(ArrayContentProvider.getInstance());
        column("Name", 130, new StyledLabels());
        column("Type", 80, labels(this::type));
        column("Applies to", 210, labels(this::where));
        column("Formatting", 330, labels(this::formatting));
        table.addSelectionChangedListener(e -> updateButtons());
        table.addDoubleClickListener(e -> edit());

        Composite buttons = new Composite(c, SWT.NONE);
        GridLayoutFactory.fillDefaults().applyTo(buttons);
        GridDataFactory.fillDefaults().align(SWT.FILL, SWT.BEGINNING).applyTo(buttons);
        button(buttons, "&Add Keyword...", this::add);
        edit = button(buttons, "&Edit...", this::edit);
        remove = button(buttons, "&Remove", this::remove);
        up = button(buttons, "Move &Up", () -> move(-1));
        down = button(buttons, "Move &Down", () -> move(1));
        button(buttons, "Edit as &Text...", this::editAsText);

        Label hint = new Label(c, SWT.WRAP);
        hint.setText("Default is how the export looks when nothing is changed; Reset brings a page part back to it. "
                + "Keywords: a text style anywhere in a description, a label for #keyword or [[keyword]], or a panel "
                + "for a line starting with \"keyword:\". Keywords ignore case. Files of the Assets folder next to the "
                + "model go into the export with [[asset:name]] (pictures are shown, other files attached).");
        GridDataFactory.fillDefaults().span(2, 1).hint(620, SWT.DEFAULT).applyTo(hint);

        try {
            load(KeywordRules.parse(Prefs.keywords()));
        } catch (IllegalArgumentException e) {
            refresh(List.of());
            setErrorMessage("The saved formatting is not valid and was not loaded: " + e.getMessage());
        }
        changed = false;
        return c;
    }

    private void column(String title, int width, ColumnLabelProvider labels) {
        TableViewerColumn column = new TableViewerColumn(table, SWT.NONE);
        column.getColumn().setText(title);
        column.getColumn().setWidth(width);
        column.setLabelProvider(labels);
    }

    private static ColumnLabelProvider labels(Function<Object, String> text) {
        return new ColumnLabelProvider() {
            @Override
            public String getText(Object row) {
                return text.apply(row);
            }
        };
    }

    // a row is a PageElement or a keyword Rule

    private static String name(Object row) {
        return row instanceof PageElement ? ((PageElement) row).name : ((Rule) row).keyword;
    }

    private String type(Object row) {
        if (row instanceof PageElement) {
            return "Page part";
        }
        switch (((Rule) row).kind) {
            case PANEL: return "Panel";
            case TAG: return "Label";
            default: return "Text style";
        }
    }

    private String where(Object row) {
        return row instanceof PageElement ? ((PageElement) row).where : KeywordStyles.where((Rule) row);
    }

    private String formatting(Object row) {
        if (row instanceof Rule) {
            return KeywordStyles.describe((Rule) row);
        }
        PageElement element = (PageElement) row;
        Rule rule = elementRules.get(element);
        if (rule == null) {
            return "Default: " + element.defaultLook;
        }
        String text = KeywordStyles.describe(rule.style);
        if (rule.theme != null) {
            text += ", Confluence theme " + rule.theme.confluenceName;
        }
        if (rule.lineNumbers) {
            text += ", line numbers";
        }
        return "Changed: " + text;
    }

    /** The style the row's name is drawn in. */
    private TextStyle look(Object row) {
        if (row instanceof Rule) {
            return ((Rule) row).style;
        }
        Rule rule = elementRules.get(row);
        return rule != null ? rule.style : ((PageElement) row).startingStyle();
    }

    /** The name drawn in its own colours and font, as far as a table can show it. */
    private final class StyledLabels extends ColumnLabelProvider {
        @Override
        public String getText(Object row) {
            return name(row);
        }

        @Override
        public Color getForeground(Object row) {
            TextStyle s = look(row);
            return s != null && s.colour != null ? colour(s.colour) : null;
        }

        @Override
        public Color getBackground(Object row) {
            TextStyle s = look(row);
            return s != null && s.background != null ? colour(s.background) : null;
        }

        @Override
        public Font getFont(Object row) {
            TextStyle s = look(row);
            boolean heading = row instanceof PageElement && ((PageElement) row).kind == Kind.HEADING;
            int bits = (heading || s != null && s.bold ? SWT.BOLD : 0) | (s != null && s.italic ? SWT.ITALIC : 0);
            return bits == SWT.NONE ? null : resources.create(FontDescriptor.createFrom(table.getTable().getFont()).setStyle(bits));
        }
    }

    private Color colour(String hex) {
        return resources.create(ColorDescriptor.createFrom(StyleEditor.rgb(hex)));
    }

    private static Button button(Composite parent, String text, Runnable action) {
        Button b = new Button(parent, SWT.PUSH);
        b.setText(text);
        b.addListener(SWT.Selection, e -> action.run());
        GridDataFactory.fillDefaults().applyTo(b);
        return b;
    }

    private void load(KeywordRules loaded) {
        keywords.clear();
        elementRules.clear();
        for (Rule rule : loaded.rules()) {
            PageElement element = PageElement.ALL.stream().filter(e -> e.matches(rule)).findFirst().orElse(null);
            if (element != null) {
                elementRules.put(element, rule);
            } else {
                keywords.add(rule);
            }
        }
        refresh(List.of());
    }

    /** The page part rules in the order of the list, then the keywords. */
    private KeywordRules all() {
        List<Rule> all = new ArrayList<>();
        PageElement.ALL.stream().map(elementRules::get).filter(Objects::nonNull).forEach(all::add);
        all.addAll(keywords);
        return KeywordRules.of(all);
    }

    private void refresh(List<?> selected) {
        changed = true;
        List<Object> rows = new ArrayList<>(PageElement.ALL);
        rows.addAll(keywords);
        table.setInput(rows);
        table.setSelection(new StructuredSelection(selected), true);
        updateButtons();
    }

    private List<?> selected() {
        return ((IStructuredSelection) table.getSelection()).toList();
    }

    private void updateButtons() {
        List<?> selected = selected();
        boolean onlyParts = !selected.isEmpty() && selected.stream().allMatch(r -> r instanceof PageElement);
        edit.setEnabled(selected.size() == 1);
        remove.setText(onlyParts ? "&Reset" : "&Remove");
        remove.setEnabled(selected.stream().anyMatch(r -> r instanceof Rule || elementRules.containsKey(r)));
        Object one = selected.size() == 1 ? selected.get(0) : null;
        up.setEnabled(one instanceof Rule && keywords.indexOf(one) > 0);
        down.setEnabled(one instanceof Rule && keywords.indexOf(one) < keywords.size() - 1);
    }

    private void add() {
        KeywordRuleDialog dialog = new KeywordRuleDialog(getShell(), null);
        if (dialog.open() == Window.OK) {
            replace(-1, dialog.rule());
        }
    }

    private void edit() {
        List<?> selected = selected();
        if (selected.size() != 1) {
            return;
        }
        if (selected.get(0) instanceof PageElement) {
            PageElement element = (PageElement) selected.get(0);
            ElementStyleDialog dialog = new ElementStyleDialog(getShell(), element, elementRules.get(element));
            if (dialog.open() == Window.OK) {
                elementRules.put(element, dialog.rule());
                refresh(List.of(element));
            }
            return;
        }
        Rule rule = (Rule) selected.get(0);
        KeywordRuleDialog dialog = new KeywordRuleDialog(getShell(), rule);
        if (dialog.open() == Window.OK) {
            replace(keywords.indexOf(rule), dialog.rule());
        }
    }

    /** Puts a keyword at an index (-1: at the end), dropping any other rule of the same kind for the same keyword. */
    private void replace(int index, Rule rule) {
        List<Rule> updated = new ArrayList<>(keywords);
        if (index >= 0) {
            updated.set(index, rule);
        } else {
            updated.add(rule);
        }
        updated.removeIf(r -> r != rule && r.kind == rule.kind
                && r.keyword.toLowerCase(Locale.ROOT).equals(rule.keyword.toLowerCase(Locale.ROOT)));
        keywords.clear();
        keywords.addAll(updated);
        refresh(List.of(rule));
    }

    /** Removes the selected keywords and puts the selected page parts back to their default. */
    private void remove() {
        List<?> selected = new ArrayList<>(selected());
        keywords.removeAll(selected);
        selected.forEach(elementRules::remove);
        refresh(selected.stream().filter(r -> r instanceof PageElement).collect(Collectors.toList()));
    }

    private void move(int by) {
        Rule rule = (Rule) selected().get(0);
        int index = keywords.indexOf(rule);
        keywords.remove(index);
        keywords.add(index + by, rule);
        refresh(List.of(rule));
    }

    private void editAsText() {
        KeywordTextDialog dialog = new KeywordTextDialog(getShell(), all().format());
        if (dialog.open() == Window.OK) {
            load(dialog.rules());
            setErrorMessage(null);
        }
    }

    @Override
    protected void performDefaults() {
        load(KeywordRules.parse(Prefs.defaultKeywords()));
        setErrorMessage(null);
        super.performDefaults();
    }

    @Override
    public boolean performOk() {
        if (changed) {
            Prefs.keywords(all().format());
        }
        return true;
    }
}
