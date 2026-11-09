package io.github.p3chys.documentationarchi.ui;

import java.beans.PropertyChangeEvent;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.views.IModelView;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateModelObject;
import com.archimatetool.model.IFolder;

/**
 * Marks in the model tree what no documentation export reflects yet: a dot after the name of each element,
 * relationship and view that changed since the last export that showed it (orange) or that no export showed yet
 * (blue), and a ring after folders (and the model) that hold such objects. Hovering a mark tells what it means.
 * <p>
 * Archi's tree has no decorators, so the marks are drawn onto it. They follow every change of the model, Undo
 * included, and {@link ExportState} after each export.
 */
public class ExportMarks implements IStartup {

    private static final String TREE = "com.archimatetool.editor.treeModelView";
    private static final String HOOKED = ExportMarks.class.getName();
    private static final int SIZE = 7;
    private static final int GAP = 4;

    private static final List<Tree> trees = new ArrayList<>();
    /** Per model, the marks of its objects, and the folders that hold unexported changes; made again after changes. */
    private static final Map<IArchimateModel, Marks> marks = new HashMap<>();
    private static boolean redrawing;
    private static Color changed;
    private static Color fresh;

    private static final class Marks {
        final Map<Object, ExportState.Mark> objects = new HashMap<>();
        /** Folders and models below which something changed, or is new. */
        final Set<Object> changedBelow = new HashSet<>();
        final Set<Object> newBelow = new HashSet<>();
    }

    @Override
    public void earlyStartup() {
        IWorkbench workbench = PlatformUI.getWorkbench();
        workbench.getDisplay().asyncExec(() -> {
            Display display = workbench.getDisplay();
            changed = new Color(display, 255, 139, 0);
            fresh = new Color(display, 0, 101, 255);
            for (IWorkbenchWindow window : workbench.getWorkbenchWindows()) {
                watch(window);
            }
            workbench.addWindowListener(new IWindowListener() {
                @Override
                public void windowOpened(IWorkbenchWindow window) {
                    watch(window);
                }

                @Override
                public void windowActivated(IWorkbenchWindow window) {
                }

                @Override
                public void windowDeactivated(IWorkbenchWindow window) {
                }

                @Override
                public void windowClosed(IWorkbenchWindow window) {
                }
            });
            IEditorModelManager.INSTANCE.addPropertyChangeListener(ExportMarks::modelChanged);
        });
    }

    private static void watch(IWorkbenchWindow window) {
        window.getPartService().addPartListener(new IPartListener2() {
            @Override
            public void partOpened(IWorkbenchPartReference ref) {
                hook(ref.getPart(false));
            }

            @Override
            public void partVisible(IWorkbenchPartReference ref) {
                hook(ref.getPart(false));
            }

            @Override
            public void partActivated(IWorkbenchPartReference ref) {
            }

            @Override
            public void partBroughtToTop(IWorkbenchPartReference ref) {
            }

            @Override
            public void partClosed(IWorkbenchPartReference ref) {
            }

            @Override
            public void partDeactivated(IWorkbenchPartReference ref) {
            }

            @Override
            public void partHidden(IWorkbenchPartReference ref) {
            }

            @Override
            public void partInputChanged(IWorkbenchPartReference ref) {
            }
        });
        for (IWorkbenchPage page : window.getPages()) {
            IViewPart view = page.findView(TREE);
            if (view != null) {
                hook(view);
            }
        }
    }

    private static void hook(IWorkbenchPart part) {
        if (!(part instanceof IModelView) || !TREE.equals(part.getSite().getId())) {
            return;
        }
        StructuredViewer viewer = ((IModelView) part).getViewer();
        if (viewer == null || !(viewer.getControl() instanceof Tree) || viewer.getControl().getData(HOOKED) != null) {
            return;
        }
        Tree tree = (Tree) viewer.getControl();
        tree.setData(HOOKED, Boolean.TRUE);
        trees.add(tree);
        tree.addListener(SWT.Dispose, e -> trees.remove(tree));
        tree.addListener(SWT.PaintItem, ExportMarks::paint);
        tree.addListener(SWT.MouseMove, e -> tooltip(tree, e.x, e.y));
        tree.addListener(SWT.MouseExit, e -> tooltip(tree, -1, -1));
        tree.redraw();
    }

    /** Any change of a model (Undo too), a model opened or closed: the marks are made again when next drawn. */
    private static void modelChanged(PropertyChangeEvent event) {
        String property = event.getPropertyName();
        if (IEditorModelManager.PROPERTY_ECORE_EVENT.equals(property)
                || IEditorModelManager.COMMAND_STACK_CHANGED.equals(property)
                || IEditorModelManager.PROPERTY_MODEL_REMOVED.equals(property)
                || IEditorModelManager.PROPERTY_MODEL_LOADED.equals(property)
                || IEditorModelManager.PROPERTY_MODEL_OPENED.equals(property)) {
            refresh();
        }
    }

    /** Makes the marks again (after a change or an export) and redraws the trees, once however often it's asked. */
    static void refresh() {
        Display display = PlatformUI.isWorkbenchRunning() ? PlatformUI.getWorkbench().getDisplay() : null;
        if (display == null || display.isDisposed()) {
            return;
        }
        display.asyncExec(() -> {
            marks.clear();
            if (!redrawing) {
                redrawing = true;
                display.asyncExec(() -> {
                    redrawing = false;
                    trees.forEach(tree -> {
                        if (!tree.isDisposed()) {
                            tree.redraw();
                        }
                    });
                });
            }
        });
    }

    private static Marks marks(IArchimateModel model) {
        return marks.computeIfAbsent(model, m -> {
            Marks result = new Marks();
            ExportState state = ExportState.get();
            List<EObject> objects = new ArrayList<>();
            objects.add(m);
            m.eAllContents().forEachRemaining(objects::add);
            for (EObject o : objects) {
                ExportState.Mark mark = state.mark(o);
                if (mark == null) {
                    continue;
                }
                result.objects.put(o, mark);
                if (mark.status != ExportState.Status.CURRENT) {
                    Set<Object> below = mark.status == ExportState.Status.CHANGED ? result.changedBelow : result.newBelow;
                    for (EObject p = o.eContainer(); p != null; p = p.eContainer()) {
                        if (p instanceof IFolder || p instanceof IArchimateModel) {
                            below.add(p);
                        }
                    }
                }
            }
            return result;
        });
    }

    /** What is drawn after an item: a dot for the object itself, or a ring for what is below it. */
    private static final class Shown {
        final boolean own;
        final ExportState.Status status;
        final long exported;

        Shown(boolean own, ExportState.Status status, long exported) {
            this.own = own;
            this.status = status;
            this.exported = exported;
        }
    }

    /** @return null when nothing is drawn */
    private static Shown shown(Object data) {
        IArchimateModel model = data instanceof IArchimateModel ? (IArchimateModel) data
                : data instanceof IArchimateModelObject ? ((IArchimateModelObject) data).getArchimateModel() : null;
        if (model == null || !Prefs.marks()) {
            return null;
        }
        Marks m = marks(model);
        ExportState.Mark own = m.objects.get(data);
        if (own != null && own.status != ExportState.Status.CURRENT) {
            return new Shown(true, own.status, own.exported);
        }
        if (m.changedBelow.contains(data)) {
            return new Shown(false, ExportState.Status.CHANGED, 0);
        }
        return m.newBelow.contains(data) ? new Shown(false, ExportState.Status.NEW, 0) : null;
    }

    private static void paint(Event e) {
        TreeItem item = (TreeItem) e.item;
        Shown shown;
        try {
            shown = shown(item.getData());
        } catch (RuntimeException ex) {
            return; // a model closing while drawn
        }
        if (shown == null) {
            return;
        }
        Rectangle dot = dot(item);
        GC gc = e.gc;
        Color previousBackground = gc.getBackground();
        Color previousForeground = gc.getForeground();
        int antialias = gc.getAntialias();
        gc.setAntialias(SWT.ON);
        Color colour = shown.status == ExportState.Status.CHANGED ? changed : fresh;
        if (shown.own) {
            gc.setBackground(colour);
            gc.fillOval(dot.x, dot.y, dot.width, dot.height);
        } else {
            gc.setForeground(colour);
            gc.drawOval(dot.x, dot.y, dot.width - 1, dot.height - 1);
        }
        gc.setAntialias(antialias);
        gc.setBackground(previousBackground);
        gc.setForeground(previousForeground);
    }

    /** Where the mark goes: after the item's text, centred on it. */
    private static Rectangle dot(TreeItem item) {
        Rectangle text = item.getBounds();
        return new Rectangle(text.x + text.width + GAP, text.y + (text.height - SIZE) / 2, SIZE, SIZE);
    }

    private static void tooltip(Tree tree, int x, int y) {
        String text = null;
        TreeItem item = x < 0 ? null : tree.getItem(new Point(x, y));
        if (item != null) {
            Rectangle dot = dot(item);
            if (x >= dot.x - 2 && x <= dot.x + dot.width + 2) {
                text = describe(shown(item.getData()));
            }
        }
        if (text == null ? tree.getToolTipText() != null : !text.equals(tree.getToolTipText())) {
            tree.setToolTipText(text);
        }
    }

    private static String describe(Shown shown) {
        if (shown == null) {
            return null;
        }
        if (!shown.own) {
            return shown.status == ExportState.Status.CHANGED ? "Holds changes no documentation export shows yet"
                    : "Holds objects no documentation export shows yet";
        }
        if (shown.status == ExportState.Status.NEW) {
            return "In no documentation export yet";
        }
        return "Changed since its last documentation export" + (shown.exported > 0
                ? " (" + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(shown.exported)) + ")"
                : "");
    }
}
