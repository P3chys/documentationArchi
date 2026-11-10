package io.github.p3chys.documentationarchi.ui;

import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.CompoundContributionItem;

import io.github.p3chys.documentationarchi.text.ExportProfile;

/** "Export Profile" in the context menu of a model: one entry per profile of the model's rules, run without the dialog. */
public class ExportProfileMenu extends CompoundContributionItem {

    @Override
    protected IContributionItem[] getContributionItems() {
        ModelReader.Selection selection;
        try {
            selection = ModelReader.Selection.of(ExportHandler.selected(
                    PlatformUI.getWorkbench().getActiveWorkbenchWindow().getSelectionService().getSelection()));
        } catch (IllegalArgumentException | NullPointerException e) { // nothing exportable selected
            return new IContributionItem[0];
        }
        List<ExportProfile> profiles = Profiles.of(selection.model);
        if (profiles.isEmpty()) {
            return new IContributionItem[0];
        }
        MenuManager menu = new MenuManager("Export Profile");
        for (ExportProfile profile : profiles) {
            Action action = new Action(profile.name) {
                @Override
                public void run() {
                    ExportHandler.run(Display.getCurrent().getActiveShell(), selection.model, profile);
                }
            };
            menu.add(new ActionContributionItem(action));
        }
        return new IContributionItem[] { menu };
    }
}
