package io.github.p3chys.documentationarchi.ui;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * "Documentation Preview" of the Window menu opens the view; "Show in Documentation Preview" of a context menu also
 * shows what was right-clicked, leaving the focus where it was.
 */
public class ShowPreviewHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
        if (window == null || window.getActivePage() == null) {
            return null;
        }
        boolean menu = HandlerUtil.getActiveMenuSelection(event) != null;
        try {
            PreviewView view = (PreviewView) window.getActivePage().showView(PreviewView.ID, null,
                    menu ? IWorkbenchPage.VIEW_VISIBLE : IWorkbenchPage.VIEW_ACTIVATE);
            List<Object> selected = menu ? ExportHandler.selected(event) : List.of();
            if (!selected.isEmpty()) {
                view.show(PreviewTarget.of(selected.get(0)));
            }
        } catch (PartInitException e) {
            throw new ExecutionException("Cannot open the Documentation Preview", e);
        }
        return null;
    }
}
