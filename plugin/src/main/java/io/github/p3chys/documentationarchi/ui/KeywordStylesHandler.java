package io.github.p3chys.documentationarchi.ui;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.handlers.HandlerUtil;

/** Documentation > Settings... (Tools menu and the Documentation submenus): the preferences of the export, on their own. */
public class KeywordStylesHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) {
        PreferencesUtil.createPreferenceDialogOn(HandlerUtil.getActiveShell(event), DocumentationPreferencePage.ID,
                new String[] { DocumentationPreferencePage.ID }, null).open();
        return null;
    }
}
