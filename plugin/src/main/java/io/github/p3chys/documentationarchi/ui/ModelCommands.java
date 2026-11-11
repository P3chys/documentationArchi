package io.github.p3chys.documentationarchi.ui;

import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CommandStack;

import com.archimatetool.model.IArchimateModel;

/** Runs the plugin's changes to a model on the model's command stack, so Undo takes them back. */
final class ModelCommands {

    private ModelCommands() {
    }

    /** Does nothing for a null command (nothing to change). */
    static void execute(IArchimateModel model, Command command) {
        if (command == null) {
            return;
        }
        CommandStack stack = (CommandStack) model.getAdapter(CommandStack.class);
        if (stack != null) {
            stack.execute(command);
        } else {
            command.execute();
        }
    }
}
