package io.github.p3chys.documentationarchi.ui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.archimatetool.model.IArchimateModel;

import io.github.p3chys.documentationarchi.render.Exporter.Format;
import io.github.p3chys.documentationarchi.text.ExportProfile;

/** Asks where to export to and in which formats; an export profile of the model fills in and locks what it sets. */
final class ExportDialog extends Dialog {

    private final String what;
    private final boolean canPublish;
    private Button publishButton;
    private boolean publish;
    private final Map<Format, Button> formatButtons = new EnumMap<>(Format.class);
    private Text folderText;
    private String folder;
    private Set<Format> formats;
    private final IArchimateModel model;
    private final List<ExportProfile> profiles;
    private Label label;
    private Button browse;
    private ExportProfile profile;
    private boolean broken;

    /**
     * @param canPublish the model has a file, next to which what is published can be noted
     * @param profiles the export profiles of the model's rules, offered after "(none)"
     */
    ExportDialog(Shell shell, String what, boolean canPublish, IArchimateModel model, List<ExportProfile> profiles) {
        super(shell);
        this.what = what;
        this.canPublish = canPublish;
        this.model = model;
        this.profiles = profiles;
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

        label = new Label(c, SWT.WRAP);
        label.setText(what);
        GridDataFactory.fillDefaults().span(3, 1).hint(480, SWT.DEFAULT).applyTo(label);

        Combo profileCombo = null;
        if (!profiles.isEmpty()) {
            new Label(c, SWT.NONE).setText("Profile:");
            profileCombo = new Combo(c, SWT.READ_ONLY);
            List<String> names = new ArrayList<>(List.of("(none)"));
            profiles.forEach(p -> names.add(p.name));
            profileCombo.setItems(names.toArray(new String[0]));
            profileCombo.select(0);
            profileCombo.setToolTipText("An export set-up kept in the model's rules (profile lines). It fills in and "
                    + "locks what it sets.");
            GridDataFactory.fillDefaults().span(2, 1).applyTo(profileCombo);
        }

        new Label(c, SWT.NONE).setText("Folder:");
        folderText = new Text(c, SWT.BORDER);
        folderText.setText(Prefs.folder());
        GridDataFactory.fillDefaults().grab(true, false).hint(380, SWT.DEFAULT).applyTo(folderText);
        browse = new Button(c, SWT.PUSH);
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

        // never remembered: publishing is a decision each time
        publishButton = new Button(c, SWT.CHECK);
        publishButton.setText("Publish: this export is what the readers get (noted next to the model, for the whole team)");
        publishButton.setToolTipText("Notes what this export shows in the file documentation.published next to the model "
                + "file. The model tree then marks what the published documentation doesn't show yet, for everyone the "
                + "file is shared with, and later exports of the whole model list what changed since.");
        publishButton.setEnabled(canPublish);
        GridDataFactory.fillDefaults().span(3, 1).indent(0, 8).applyTo(publishButton);
        if (profileCombo != null) {
            Combo combo = profileCombo;
            combo.addListener(SWT.Selection, e -> {
                int i = combo.getSelectionIndex();
                apply(i <= 0 ? null : profiles.get(i - 1));
            });
        }
        return c;
    }

    /** Fills in and locks what the profile sets; with none, gives it all back as it was remembered. */
    private void apply(ExportProfile chosen) {
        profile = chosen;
        broken = false;
        label.setText(what);
        boolean fixedFolder = false;
        if (chosen == null) {
            folderText.setText(Prefs.folder());
            Set<Format> remembered = Prefs.formats();
            formatButtons.forEach((format, button) -> button.setSelection(remembered.contains(format)));
            publishButton.setSelection(false);
        } else {
            try {
                var output = Profiles.output(model, null, chosen);
                if (output != null) {
                    folderText.setText(output.toString());
                    fixedFolder = true;
                }
                if (chosen.hasScope()) {
                    Profiles.selection(model, null, chosen); // unknown folders or views show now, not after the export
                    label.setText("Profile " + chosen.name + " exports "
                            + String.join(", ", chosen.workPackages.isEmpty() ? scope(chosen) : chosen.workPackages));
                }
            } catch (IllegalArgumentException e) {
                label.setText(e.getMessage());
                broken = true;
            }
            if (!chosen.formats.isEmpty()) {
                Set<Format> fixed = Profiles.formats(chosen, Set.of());
                formatButtons.forEach((format, button) -> button.setSelection(fixed.contains(format)));
            }
            if (chosen.publish) {
                publishButton.setSelection(canPublish);
            }
        }
        folderText.setEnabled(!fixedFolder);
        browse.setEnabled(!fixedFolder);
        formatButtons.values().forEach(b -> b.setEnabled(chosen == null || chosen.formats.isEmpty()));
        publishButton.setEnabled(canPublish && (chosen == null || !chosen.publish));
        label.getParent().layout();
        updateOk();
    }

    private static List<String> scope(ExportProfile p) {
        List<String> scope = new ArrayList<>(p.folders);
        scope.addAll(p.views);
        return scope;
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
        getButton(IDialogConstants.OK_ID).setEnabled(!broken
                && formatButtons.values().stream().anyMatch(Button::getSelection));
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
        publish = publishButton.getSelection();
        if (profile == null) { // a profile's values are not what the next export starts with
            Prefs.folder(folder);
            Prefs.formats(formats);
        }
        super.okPressed();
    }

    String folder() {
        return folder;
    }

    Set<Format> formats() {
        return formats;
    }

    /** The profile chosen, null for none. */
    ExportProfile profile() {
        return profile;
    }

    boolean publish() {
        return publish;
    }
}
