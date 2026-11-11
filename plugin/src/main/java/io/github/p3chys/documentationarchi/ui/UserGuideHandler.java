package io.github.p3chys.documentationarchi.ui;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.program.Program;
import org.eclipse.ui.handlers.HandlerUtil;

/** Documentation > User Guide: the user guide, in the web browser. */
public class UserGuideHandler extends AbstractHandler {

    static final String URL = "https://github.com/P3chys/documentationArchi/blob/main/docs/user-guide.md";

    @Override
    public Object execute(ExecutionEvent event) {
        if (!Program.launch(URL)) {
            MessageDialog.openInformation(HandlerUtil.getActiveShell(event), "User Guide",
                    "No web browser could be opened. The user guide is at " + URL);
        }
        return null;
    }
}
