package io.github.p3chys.documentationarchi.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jface.dialogs.MessageDialog;
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
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;

import io.github.p3chys.documentationarchi.text.KeywordRules;
import io.github.p3chys.documentationarchi.text.KeywordRules.Kind;
import io.github.p3chys.documentationarchi.text.KeywordRules.Rule;
import io.github.p3chys.documentationarchi.text.KeywordRules.TextStyle;
import io.github.p3chys.documentationarchi.text.Texts;

/**
 * Preferences > Documentation Export, in two tabs. General: the settings that are not about looks (the Jira address,
 * the language, the PDF's layout...), which are rule lines too ({@link GeneralSettings}), and this computer's own
 * choices. Formatting: one list of everything whose look can be changed. The page parts (tokens, headings, SQL
 * blocks) are always there and can be reset to their default; keywords can be added and removed.
 */
public class DocumentationPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

    static final String ID = "io.github.p3chys.documentationarchi.prefs";

    private final List<Rule> keywords = new ArrayList<>();
    /** Rules about the export rather than looks (exclusions...): not listed, but kept; "Edit as Text..." shows them. */
    private final List<Rule> settings = new ArrayList<>();
    private final Map<PageElement, Rule> elementRules = new LinkedHashMap<>();
    private TableViewer table;
    private LocalResourceManager resources;
    private Button edit;
    private Button remove;
    private Button up;
    private Button down;
    private Button marks;
    private Button ignoreLayout;
    private Button showViews;
    private Button showWorkPackages;
    private Label projectLine;
    private Text jira;
    private Combo language;
    private Combo typeStyle;
    private Combo workPackage;
    private Text assets;
    private Button pdfCover;
    private Button pdfContents;
    private Button pdfLandscape;
    private Button htmlSingleFile;
    private Text hidden;
    private Text excluded;
    /** True while the General tab is filled from the rules, which is no change by the user. */
    private boolean showing;
    /** False until the list is changed: saved rules that could not be loaded are not overwritten by an empty list. */
    private boolean changed;

    @Override
    public void init(IWorkbench workbench) {
        setDescription("The documentation export (HTML, PDF and Confluence). General: where links go and what the "
                + "output holds. Formatting: how the parts of the pages and keywords in descriptions look.");
    }

    @Override
    protected Control createContents(Composite parent) {
        Composite c = new Composite(parent, SWT.NONE);
        GridLayoutFactory.fillDefaults().numColumns(3).applyTo(c);
        resources = new LocalResourceManager(JFaceResources.getResources(), c);

        TabFolder tabs = new TabFolder(c, SWT.TOP);
        GridDataFactory.fillDefaults().span(3, 1).grab(true, true).applyTo(tabs);
        TabItem generalTab = new TabItem(tabs, SWT.NONE);
        generalTab.setText("&General");
        generalTab.setControl(general(tabs));
        TabItem formattingTab = new TabItem(tabs, SWT.NONE);
        formattingTab.setText("&Formatting");
        formattingTab.setControl(formatting(tabs));

        projectLine = new Label(c, SWT.WRAP);
        GridDataFactory.fillDefaults().grab(true, false).hint(420, SWT.DEFAULT).applyTo(projectLine);
        showProjectRules();
        Button text = button(c, "Edit All as &Text...", this::editAsText);
        text.setToolTipText("Every setting of both tabs as rule lines, with the ones no tab shows (profiles, catalogs, "
                + "fixed texts, page sections...)");
        Button project = button(c, "Save as &Project Rules...", this::saveAsProjectRules);
        project.setToolTipText("Write these settings next to a model's file (" + ProjectRules.NAME + "), so everyone "
                + "exporting that model gets the same documentation");

        marks.setSelection(Prefs.marks());
        ignoreLayout.setSelection(Prefs.ignoreLayout());
        ignoreLayout.setEnabled(marks.getSelection());
        showViews.setSelection(Prefs.showViews());
        showWorkPackages.setSelection(Prefs.showWorkPackages());
        try {
            load(KeywordRules.parse(Prefs.keywords()));
        } catch (IllegalArgumentException e) {
            refresh(List.of());
            showGeneral();
            setErrorMessage("The saved settings are not valid and were not loaded: " + e.getMessage());
        }
        changed = false;
        return c;
    }

    /** The General tab: the settings that are not about looks. */
    private Control general(Composite parent) {
        Composite c = new Composite(parent, SWT.NONE);
        GridLayoutFactory.swtDefaults().applyTo(c);

        Group links = group(c, "Links");
        jira = field(links, "&Jira address:", "https://jira.example.com/browse/",
                "Work packages named after a ticket (RBWDP-20025) link to this address with the ticket key after it. "
                + "Empty: they do not link.");
        new Label(links, SWT.NONE);
        hint(links, "A work package named after a ticket links to this address followed by the ticket key. An "
                + "element with a property confluenceUrl links to that Confluence page.");

        Group output = group(c, "Output");
        new Label(output, SWT.NONE).setText("&Language:");
        language = combo(output, "English", "Czech");
        language.setToolTipText("The language of the fixed texts of the output (Contents, Views, Properties...)");
        new Label(output, SWT.NONE).setText("Element &types:");
        typeStyle = combo(output, "As Archi names them: <<Business Process>> Pay",
                "As HaF writes them: «Process»Pay");
        new Label(output, SWT.NONE).setText("&Work package export:");
        workPackage = combo(output, "A page per view and element", "One page: the ticket, then each element it changes");
        assets = field(output, "&Assets folder:", "Assets",
                "Where [[asset:name]] files are read from: relative to the model's folder, or absolute. Empty: Assets");
        new Label(output, SWT.NONE).setText("PDF:");
        Composite pdf = new Composite(output, SWT.NONE);
        GridLayoutFactory.fillDefaults().numColumns(3).applyTo(pdf);
        pdfCover = check(pdf, "C&over page", 1);
        pdfContents = check(pdf, "Table of c&ontents", 1);
        pdfLandscape = check(pdf, "&Landscape pages for wide views", 1);
        new Label(output, SWT.NONE).setText("HTML:");
        htmlSingleFile = check(output, "One self-contained &file per page (pictures and style inside)", 1);
        for (Button b : new Button[] { pdfCover, pdfContents, pdfLandscape, htmlSingleFile }) {
            b.addListener(SWT.Selection, e -> generalChanged());
        }

        Group entries = group(c, "Element descriptions");
        showViews = check(entries, "Show the &views an element appears on (\"Views: ...\")", 2);
        showWorkPackages = check(entries, "Show the work &packages that change an element (\"Work packages: ...\")", 2);
        hidden = field(entries, "&Hide properties:", "internal*, owner",
                "Properties with these keys are never printed, separated by commas; * at the end matches any ending");
        excluded = field(entries, "Leave out la&bels:", "draft, wip",
                "Views, elements and relationships labelled #draft (in their description) stay out of every export");

        Group tree = group(c, "Models tree");
        marks = check(tree, "&Mark what no export shows yet (orange: changed since, blue: never exported)", 2);
        ignoreLayout = check(tree, "&Ignore layout-only changes (moved boxes, bend points, sizes, fonts, colours)", 2);
        ((GridData) ignoreLayout.getLayoutData()).horizontalIndent = 16;
        marks.addListener(SWT.Selection, e -> ignoreLayout.setEnabled(marks.getSelection()));
        return c;
    }

    /** The Formatting tab: the page parts and keywords, and how each looks. */
    private Control formatting(Composite parent) {
        Composite c = new Composite(parent, SWT.NONE);
        GridLayoutFactory.swtDefaults().numColumns(2).applyTo(c);

        table = new TableViewer(c, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.V_SCROLL);
        Table t = table.getTable();
        t.setHeaderVisible(true);
        t.setLinesVisible(true);
        GridDataFactory.fillDefaults().grab(true, true).hint(560, 340).applyTo(t);
        table.setContentProvider(ArrayContentProvider.getInstance());
        column("Name", 130, new StyledLabels());
        column("Type", 80, labels(this::type));
        column("Applies to", 190, labels(this::where));
        column("Formatting", 300, labels(this::formatting));
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

        Label hint = new Label(c, SWT.WRAP);
        hint.setText("Double-click a row to change it. Page parts are always there: Reset brings one back to its "
                + "default. Keywords: a text style anywhere in a description, a label for #keyword or [[keyword]], "
                + "or a panel for a line starting with \"keyword:\". Keywords ignore case.");
        GridDataFactory.fillDefaults().span(2, 1).hint(560, SWT.DEFAULT).applyTo(hint);
        return c;
    }

    private static Group group(Composite parent, String title) {
        Group g = new Group(parent, SWT.NONE);
        g.setText(title);
        GridLayoutFactory.swtDefaults().numColumns(2).applyTo(g);
        GridDataFactory.fillDefaults().grab(true, false).applyTo(g);
        return g;
    }

    private Text field(Composite parent, String label, String example, String tooltip) {
        new Label(parent, SWT.NONE).setText(label);
        Text text = new Text(parent, SWT.BORDER);
        text.setMessage(example);
        text.setToolTipText(tooltip);
        GridDataFactory.fillDefaults().grab(true, false).hint(360, SWT.DEFAULT).applyTo(text);
        text.addListener(SWT.Modify, e -> generalChanged());
        return text;
    }

    private Combo combo(Composite parent, String... items) {
        Combo combo = new Combo(parent, SWT.DROP_DOWN | SWT.READ_ONLY);
        for (String item : items) {
            combo.add(item);
        }
        combo.select(0);
        GridDataFactory.fillDefaults().applyTo(combo);
        combo.addListener(SWT.Selection, e -> generalChanged());
        return combo;
    }

    private Button check(Composite parent, String text, int span) {
        Button check = new Button(parent, SWT.CHECK);
        check.setText(text);
        GridDataFactory.fillDefaults().span(span, 1).applyTo(check);
        return check;
    }

    private static void hint(Composite parent, String text) {
        Label hint = new Label(parent, SWT.WRAP);
        hint.setText(text);
        hint.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_WIDGET_DARK_SHADOW));
        GridDataFactory.fillDefaults().grab(true, false).hint(360, SWT.DEFAULT).applyTo(hint);
    }

    /** A setting of the General tab was changed: checked now, the rules are written when OK is pressed. */
    private void generalChanged() {
        if (showing) {
            return;
        }
        changed = true;
        try {
            readGeneral().applyTo(settings);
            setErrorMessage(null);
            setValid(true);
        } catch (IllegalArgumentException e) {
            setErrorMessage(e.getMessage());
            setValid(false);
        }
    }

    /** Puts the setting rules into the General tab. */
    private void showGeneral() {
        showing = true;
        GeneralSettings s = GeneralSettings.of(settings);
        jira.setText(s.jira);
        language.select(Math.max(0, Texts.LANGUAGES.indexOf(s.language)));
        typeStyle.select(s.hafTypes ? 1 : 0);
        workPackage.select(s.workPackageOnePage ? 1 : 0);
        assets.setText(s.assets);
        pdfCover.setSelection(s.pdf.contains("cover"));
        pdfContents.setSelection(s.pdf.contains("contents"));
        pdfLandscape.setSelection(s.pdf.contains("landscape"));
        htmlSingleFile.setSelection(s.htmlSingleFile);
        hidden.setText(String.join(", ", s.hiddenProperties));
        excluded.setText(String.join(", ", s.excludedLabels));
        showing = false;
        setValid(true);
    }

    private GeneralSettings readGeneral() {
        GeneralSettings s = new GeneralSettings();
        s.jira = jira.getText().strip();
        s.language = Texts.LANGUAGES.get(Math.max(0, language.getSelectionIndex()));
        s.hafTypes = typeStyle.getSelectionIndex() == 1;
        s.workPackageOnePage = workPackage.getSelectionIndex() == 1;
        s.assets = assets.getText().strip();
        s.pdf.clear();
        if (pdfCover.getSelection()) {
            s.pdf.add("cover");
        }
        if (pdfContents.getSelection()) {
            s.pdf.add("contents");
        }
        if (pdfLandscape.getSelection()) {
            s.pdf.add("landscape");
        }
        s.htmlSingleFile = htmlSingleFile.getSelection();
        s.hiddenProperties.addAll(GeneralSettings.words(hidden.getText()));
        GeneralSettings.words(excluded.getText()).forEach(w -> s.excludedLabels.add(w.startsWith("#") ? w.substring(1) : w));
        return s;
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
        settings.clear();
        elementRules.clear();
        for (Rule rule : loaded.rules()) {
            if (rule.kind.setting) {
                settings.add(rule);
                continue;
            }
            PageElement element = PageElement.ALL.stream().filter(e -> e.matches(rule)).findFirst().orElse(null);
            if (element != null) {
                elementRules.put(element, rule);
            } else {
                keywords.add(rule);
            }
        }
        refresh(List.of());
        showGeneral();
    }

    /** The page part rules in the order of the list, then the keywords. */
    private KeywordRules all() {
        List<Rule> all = new ArrayList<>();
        PageElement.ALL.stream().map(elementRules::get).filter(Objects::nonNull).forEach(all::add);
        all.addAll(keywords);
        all.addAll(readGeneral().applyTo(settings));
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
        if (!isValid()) {
            return; // the error message says what to correct first
        }
        KeywordTextDialog dialog = new KeywordTextDialog(getShell(), all().format());
        if (dialog.open() == Window.OK) {
            load(dialog.rules());
            setErrorMessage(null);
        }
    }

    /** Writes the rules of the list as documentation.rules next to a chosen open, saved model. */
    private void saveAsProjectRules() {
        if (!isValid()) {
            return;
        }
        List<IArchimateModel> saved = IEditorModelManager.INSTANCE.getModels().stream()
                .filter(m -> m.getFile() != null).collect(Collectors.toList());
        if (saved.isEmpty()) {
            MessageDialog.openInformation(getShell(), "Save as Project Rules",
                    "Open and save a model first: the rules are written next to its file.");
            return;
        }
        ElementListSelectionDialog choose = new ElementListSelectionDialog(getShell(), new LabelProvider() {
            @Override
            public String getText(Object model) {
                return ((IArchimateModel) model).getName() + "  (" + ProjectRules.file((IArchimateModel) model) + ")";
            }
        });
        choose.setTitle("Save as Project Rules");
        choose.setMessage("Model to save the formatting rules for (" + ProjectRules.NAME + " next to its file):");
        choose.setElements(saved.toArray());
        choose.setMultipleSelection(false);
        if (choose.open() != Window.OK) {
            return;
        }
        IArchimateModel model = (IArchimateModel) choose.getFirstResult();
        Path file = ProjectRules.file(model);
        if (ProjectRules.exists(model) && !MessageDialog.openQuestion(getShell(), "Save as Project Rules",
                file + " already exists. Replace it?")) {
            return;
        }
        try {
            ProjectRules.save(model, all());
            showProjectRules();
        } catch (IOException e) {
            MessageDialog.openError(getShell(), "Save as Project Rules", "Cannot write " + file + ": " + e.getMessage());
        }
    }

    /** Says which open models take their formatting from a project file instead of these settings. */
    private void showProjectRules() {
        String using = IEditorModelManager.INSTANCE.getModels().stream().filter(ProjectRules::exists)
                .map(m -> "\"" + m.getName() + "\" (" + ProjectRules.file(m) + ")").collect(Collectors.joining(", "));
        projectLine.setText(using.isEmpty() ? "A " + ProjectRules.NAME + " file next to a model's file would be used for "
                + "that model instead of these settings." : "These models use project rules instead of these settings: "
                + using);
        projectLine.getParent().layout();
    }

    @Override
    protected void performDefaults() {
        load(KeywordRules.parse(Prefs.defaultKeywords()));
        marks.setSelection(true);
        ignoreLayout.setSelection(false);
        ignoreLayout.setEnabled(true);
        showViews.setSelection(true);
        showWorkPackages.setSelection(true);
        setErrorMessage(null);
        super.performDefaults();
    }

    @Override
    public boolean performOk() {
        if (!isValid()) {
            return false;
        }
        if (changed) {
            Prefs.keywords(all().format());
        }
        Prefs.showViews(showViews.getSelection());
        Prefs.showWorkPackages(showWorkPackages.getSelection());
        if (marks.getSelection() != Prefs.marks() || ignoreLayout.getSelection() != Prefs.ignoreLayout()) {
            Prefs.marks(marks.getSelection());
            Prefs.ignoreLayout(ignoreLayout.getSelection());
            ExportMarks.refresh();
        }
        return true;
    }
}
