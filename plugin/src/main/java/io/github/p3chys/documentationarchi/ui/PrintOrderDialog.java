package io.github.p3chys.documentationarchi.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceAdapter;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import com.archimatetool.editor.ui.ArchiLabelProvider;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelObject;

/**
 * Puts the elements of a view in the order the export describes them: move them with the buttons, Alt+Up/Down or by
 * dragging. The result is the order to number the elements in (see {@link PrintOrder}), or none for {@link #RESET}.
 */
final class PrintOrderDialog extends Dialog {

    /** The return code of "Remove Order": forget the view's own order. */
    static final int RESET = IDialogConstants.CLIENT_ID + 1;

    private final IDiagramModel view;
    private final List<IArchimateElement> order;
    private final boolean hasOrder;
    private Table table;
    private final Map<String, Button> buttons = new HashMap<>();

    PrintOrderDialog(Shell shell, IDiagramModel view) {
        super(shell);
        this.view = view;
        this.order = new ArrayList<>(ModelReader.ordered(view));
        this.hasOrder = PrintOrder.onView(view) != null;
    }

    /** The elements in the chosen order, once the dialog closed with OK. */
    List<IArchimateElement> order() {
        return order;
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Print Order - " + ArchiLabelProvider.INSTANCE.getLabel(view));
    }

    @Override
    protected boolean isResizable() {
        return true;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite c = (Composite) super.createDialogArea(parent);
        c.setLayout(new GridLayout(2, false));

        Label label = new Label(c, SWT.WRAP);
        label.setText("The order in which the export describes the elements of this view. It is kept as links named "
                + "001, 002, 003... to a PrintOrder element on the view, created when the view has none.");
        GridDataFactory.fillDefaults().span(2, 1).hint(420, SWT.DEFAULT).applyTo(label);

        table = new Table(c, SWT.BORDER | SWT.MULTI | SWT.FULL_SELECTION | SWT.V_SCROLL);
        table.setHeaderVisible(true);
        column("#", 40);
        column("Element", 260);
        column("Type", 160);
        GridDataFactory.fillDefaults().grab(true, true).hint(SWT.DEFAULT, 360).applyTo(table);
        table.addListener(SWT.Selection, e -> updateButtons());
        table.addListener(SWT.KeyDown, e -> {
            if ((e.stateMask & SWT.ALT) != 0 && (e.keyCode == SWT.ARROW_UP || e.keyCode == SWT.ARROW_DOWN)) {
                move(e.keyCode == SWT.ARROW_UP ? -1 : 1);
                e.doit = false;
            }
        });
        dragAndDrop();

        Composite side = new Composite(c, SWT.NONE);
        GridLayout layout = new GridLayout(1, true);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        side.setLayout(layout);
        GridDataFactory.fillDefaults().align(SWT.FILL, SWT.BEGINNING).applyTo(side);
        button(side, "top", "&Top", () -> moveTo(true));
        button(side, "up", "&Up", () -> move(-1));
        button(side, "down", "&Down", () -> move(1));
        button(side, "bottom", "&Bottom", () -> moveTo(false));
        new Label(side, SWT.NONE);
        button(side, "name", "By &Name", () -> sort(Comparator.comparing(PrintOrderDialog::label,
                ModelReader::compareNatural)));
        button(side, "layout", "By &Layout", () -> sort(Comparator.comparing(this::place,
                Comparator.<int[]>comparingInt(p -> p[1]).thenComparingInt(p -> p[0]))));

        refresh(List.of());
        return c;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        Button reset = createButton(parent, RESET, "&Remove Order", false);
        reset.setToolTipText("Take the PrintOrder element and its links off this view");
        reset.setEnabled(hasOrder);
        super.createButtonsForButtonBar(parent);
    }

    @Override
    protected void buttonPressed(int id) {
        if (id == RESET) {
            setReturnCode(RESET);
            close();
        } else {
            super.buttonPressed(id);
        }
    }

    private void column(String text, int width) {
        TableColumn column = new TableColumn(table, SWT.NONE);
        column.setText(text);
        column.setWidth(width);
    }

    private void button(Composite parent, String key, String text, Runnable action) {
        Button button = new Button(parent, SWT.PUSH);
        button.setText(text);
        button.addListener(SWT.Selection, e -> action.run());
        GridDataFactory.fillDefaults().grab(true, false).applyTo(button);
        buttons.put(key, button);
    }

    /** Moves the selected elements one place up (-1) or down (1), keeping them together where they meet the end. */
    private void move(int step) {
        int[] selected = table.getSelectionIndices();
        if (selected.length == 0) {
            return;
        }
        Arrays.sort(selected);
        boolean[] chosen = new boolean[order.size()];
        for (int i : selected) {
            chosen[i] = true;
        }
        if (step < 0) {
            for (int i = 1; i < order.size(); i++) {
                if (chosen[i] && !chosen[i - 1]) {
                    swap(chosen, i, i - 1);
                }
            }
        } else {
            for (int i = order.size() - 2; i >= 0; i--) {
                if (chosen[i] && !chosen[i + 1]) {
                    swap(chosen, i, i + 1);
                }
            }
        }
        refresh(selection());
    }

    private void swap(boolean[] chosen, int i, int j) {
        Collections.swap(order, i, j);
        boolean b = chosen[i];
        chosen[i] = chosen[j];
        chosen[j] = b;
    }

    private void moveTo(boolean top) {
        List<IArchimateElement> selected = selection();
        order.removeAll(selected);
        order.addAll(top ? 0 : order.size(), selected);
        refresh(selected);
    }

    private void sort(Comparator<IArchimateElement> comparator) {
        List<IArchimateElement> selected = selection();
        order.sort(comparator);
        refresh(selected);
    }

    private List<IArchimateElement> selection() {
        List<IArchimateElement> selected = new ArrayList<>();
        for (TableItem item : table.getSelection()) {
            selected.add((IArchimateElement) item.getData());
        }
        return selected;
    }

    private void refresh(List<IArchimateElement> selected) {
        table.setRedraw(false);
        table.removeAll();
        ArchiLabelProvider labels = ArchiLabelProvider.INSTANCE;
        for (int i = 0; i < order.size(); i++) {
            IArchimateElement element = order.get(i);
            TableItem item = new TableItem(table, SWT.NONE);
            item.setData(element);
            item.setText(new String[] { String.valueOf(i + 1), label(element), labels.getDefaultName(element.eClass()) });
            item.setImage(1, labels.getImage(element));
            if (selected.contains(element)) {
                table.select(i);
            }
        }
        table.setRedraw(true);
        if (table.getSelectionIndex() >= 0) {
            table.showSelection();
        }
        updateButtons();
    }

    private void updateButtons() {
        int[] selected = table.getSelectionIndices();
        boolean any = selected.length > 0;
        boolean first = any && Arrays.stream(selected).min().getAsInt() == 0
                && selected.length == Arrays.stream(selected).max().getAsInt() + 1;
        boolean last = any && Arrays.stream(selected).max().getAsInt() == order.size() - 1
                && selected.length == order.size() - Arrays.stream(selected).min().getAsInt();
        buttons.get("top").setEnabled(any && !first);
        buttons.get("up").setEnabled(any && !first);
        buttons.get("down").setEnabled(any && !last);
        buttons.get("bottom").setEnabled(any && !last);
        buttons.get("name").setEnabled(order.size() > 1);
        buttons.get("layout").setEnabled(order.size() > 1);
    }

    /** Drags the selected rows to before the row they are dropped on (after the last row when below it). */
    private void dragAndDrop() {
        Transfer[] types = { TextTransfer.getInstance() };
        DragSource source = new DragSource(table, DND.DROP_MOVE);
        source.setTransfer(types);
        source.addDragListener(new DragSourceAdapter() {
            @Override
            public void dragSetData(DragSourceEvent event) {
                event.data = "print-order";
            }
        });
        DropTarget target = new DropTarget(table, DND.DROP_MOVE);
        target.setTransfer(types);
        target.addDropListener(new DropTargetAdapter() {
            @Override
            public void dragOver(DropTargetEvent event) {
                event.feedback = DND.FEEDBACK_INSERT_BEFORE | DND.FEEDBACK_SCROLL;
            }

            @Override
            public void drop(DropTargetEvent event) {
                List<IArchimateElement> selected = selection();
                IArchimateElement before = event.item == null ? null : (IArchimateElement) event.item.getData();
                while (before != null && selected.contains(before)) { // dropped onto itself: before the next one
                    int next = order.indexOf(before) + 1;
                    before = next < order.size() ? order.get(next) : null;
                }
                order.removeAll(selected);
                order.addAll(before == null ? order.size() : order.indexOf(before), selected);
                refresh(selected);
            }
        });
    }

    /** Where the element first appears on the view, as {x, y} from the view's corner. */
    private int[] place(IArchimateElement element) {
        int[] best = null;
        for (var i = view.eAllContents(); i.hasNext();) {
            EObject o = i.next();
            if (o instanceof IDiagramModelArchimateObject
                    && ((IDiagramModelArchimateObject) o).getArchimateElement() == element) {
                int[] p = { 0, 0 };
                for (EObject e = o; e instanceof IDiagramModelObject; e = e.eContainer()) {
                    p[0] += ((IDiagramModelObject) e).getBounds().getX();
                    p[1] += ((IDiagramModelObject) e).getBounds().getY();
                }
                if (best == null || p[1] < best[1] || p[1] == best[1] && p[0] < best[0]) {
                    best = p;
                }
            }
        }
        return best == null ? new int[] { 0, 0 } : best;
    }

    private static String label(IArchimateElement element) {
        String name = element.getName();
        return name == null || name.isBlank() ? ArchiLabelProvider.INSTANCE.getDefaultName(element.eClass()) : name;
    }
}
