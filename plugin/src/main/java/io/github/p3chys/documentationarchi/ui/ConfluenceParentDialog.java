package io.github.p3chys.documentationarchi.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;

import io.github.p3chys.documentationarchi.confluence.Confluence;

/**
 * Shows a space of Confluence (by its key: a company has too many to list) and its page tree, read as it is opened,
 * to choose the page an export goes under (or the space, for its top). Confluence is read in the background, so
 * the window never hangs on it.
 */
final class ConfluenceParentDialog extends TitleAreaDialog {

    private static final int CONNECTION = IDialogConstants.CLIENT_ID + 1;
    private static final String LOADING = "Loading...";

    /** A space or a page of the tree; its children are read when it is first opened. */
    private static final class Node {
        final Node parent;
        final Confluence.Space space;
        final Confluence.Page page;
        List<Object> children;

        Node(Node parent, Confluence.Space space, Confluence.Page page) {
            this.parent = parent;
            this.space = space;
            this.page = page;
        }

        String spaceKey() {
            return parent == null ? space.key : parent.spaceKey();
        }

        String path() {
            String name = page != null ? page.title : space.name != null ? space.name : space.key;
            return parent == null ? name : parent.path() + " / " + name;
        }
    }

    private Confluence confluence;
    private TreeViewer tree;
    private Combo spaceKey;
    private Label chosen;
    /** The spaces, or LOADING, or what went wrong. */
    private List<Object> roots = new ArrayList<>(List.of(LOADING));
    private ConfluenceTarget target;

    ConfluenceParentDialog(Shell shell) {
        super(shell);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Upload to Confluence");
    }

    @Override
    protected boolean isResizable() {
        return true;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        Composite c = new Composite(area, SWT.NONE);
        GridLayoutFactory.swtDefaults().numColumns(2).applyTo(c);
        GridDataFactory.fillDefaults().grab(true, true).applyTo(c);
        setTitle("Where the export goes");

        Composite row = new Composite(c, SWT.NONE);
        GridLayoutFactory.fillDefaults().numColumns(3).applyTo(row);
        GridDataFactory.fillDefaults().span(2, 1).grab(true, false).applyTo(row);
        new Label(row, SWT.NONE).setText("&Space key:");
        spaceKey = new Combo(row, SWT.DROP_DOWN);
        spaceKey.setItems(Prefs.confluenceSpaces().toArray(new String[0]));
        spaceKey.setToolTipText("The key of the space, as in its address: ARCH in /display/ARCH/. Only that space is "
                + "read, not all of Confluence.");
        GridDataFactory.fillDefaults().grab(true, false).applyTo(spaceKey);
        Button open = new Button(row, SWT.PUSH);
        open.setText("&Open");
        open.addListener(SWT.Selection, e -> openSpace());
        spaceKey.addListener(SWT.Traverse, e -> { // Enter in the field opens the space, it does not close the window
            if (e.detail == SWT.TRAVERSE_RETURN) {
                e.doit = false;
                openSpace();
            }
        });
        spaceKey.addListener(SWT.Selection, e -> openSpace()); // a key from the list

        tree = new TreeViewer(c, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL);
        GridDataFactory.fillDefaults().span(2, 1).grab(true, true).hint(480, 360).applyTo(tree.getControl());
        tree.setContentProvider(new Content());
        tree.setLabelProvider(new Labels());
        tree.addSelectionChangedListener(e -> selected());
        tree.addDoubleClickListener(e -> {
            Object o = ((IStructuredSelection) e.getSelection()).getFirstElement();
            if (o instanceof Node) {
                tree.setExpandedState(o, !tree.getExpandedState(o));
            }
        });
        tree.setInput(this);

        chosen = new Label(c, SWT.WRAP);
        GridDataFactory.fillDefaults().span(2, 1).grab(true, false).hint(480, SWT.DEFAULT).applyTo(chosen);
        ConfluenceTarget last = Prefs.confluenceTarget();
        chosen.setText(last == null ? "Choose a page, or the space for its top." : "Last time: " + last.path);
        if (last != null) {
            spaceKey.setText(last.spaceKey);
        } else if (spaceKey.getItemCount() > 0) {
            spaceKey.select(0);
        }
        connect();
        return area;
    }

    /** Takes the settings of the preferences, then opens the space of the key given, if one is. */
    private void connect() {
        try {
            confluence = Prefs.confluence();
        } catch (IllegalArgumentException e) {
            confluence = null;
            roots = new ArrayList<>(List.of(e.getMessage()));
            setErrorMessage(e.getMessage() + " Use Connection... to set it up.");
            tree.refresh();
            return;
        }
        setErrorMessage(null);
        setMessage("Confluence at " + confluence.url() + ". Give the key of the space, then choose the page the export "
                + "goes under, or the space for its top. Pages already there with the titles of the export get a new version.");
        if (spaceKey.getText().isBlank()) {
            roots = new ArrayList<>(List.of("Give the key of the space and press Open."));
            tree.refresh();
        } else {
            openSpace();
        }
    }

    /** Reads the space of the key given, alone, and opens its page tree. */
    private void openSpace() {
        String key = spaceKey.getText().strip();
        if (confluence == null || key.isEmpty()) {
            return;
        }
        setErrorMessage(null);
        roots = new ArrayList<>(List.of(LOADING));
        tree.refresh();
        load(() -> confluence.space(key), space -> {
            Node node = new Node(null, space, null);
            roots = new ArrayList<>(List.of(node));
            tree.refresh();
            tree.setExpandedState(node, true);
            Prefs.confluenceSpaceUsed(space.key);
        }, error -> {
            roots = new ArrayList<>(List.of(error));
            setErrorMessage(error);
            tree.refresh();
        });
    }

    /** Reads in the background, and hands the result or what went wrong to the window, if it is still open. */
    private <T> void load(Callable<T> read, Consumer<T> done, Consumer<String> failed) {
        Confluence reading = confluence;
        Display display = tree.getControl().getDisplay(); // here: the tree may be gone when the reading is done
        Job job = Job.create("Reading Confluence", monitor -> {
            T result = null;
            String error = null;
            try {
                result = read.call();
            } catch (IOException | RuntimeException e) {
                error = e.getMessage() != null ? e.getMessage() : e.toString();
            } catch (Exception e) {
                error = e.toString();
            }
            T r = result;
            String err = error;
            if (display.isDisposed()) {
                return Status.OK_STATUS;
            }
            display.asyncExec(() -> {
                if (tree.getControl().isDisposed() || reading != confluence) {
                    return;
                }
                if (err == null) {
                    done.accept(r);
                } else {
                    failed.accept(err);
                }
            });
            return Status.OK_STATUS;
        });
        job.setSystem(true);
        job.schedule();
    }

    private void open(Node node) {
        node.children = new ArrayList<>(List.of(LOADING));
        load(() -> node.page == null ? confluence.rootPages(node.space.key) : confluence.children(node.page.id), pages -> {
            List<Object> children = new ArrayList<>();
            pages.forEach(p -> children.add(new Node(node, null, p)));
            node.children = children;
            tree.refresh(node);
        }, error -> {
            node.children = new ArrayList<>(List.of(error));
            tree.refresh(node);
        });
    }

    private void selected() {
        Object o = tree.getStructuredSelection().getFirstElement();
        Button ok = getButton(IDialogConstants.OK_ID);
        if (o instanceof Node) {
            Node node = (Node) o;
            target = new ConfluenceTarget(node.spaceKey(), node.page == null ? null : node.page.id, node.path());
            chosen.setText(node.page == null ? "The export goes at the top of the space " + node.path() + "."
                    : "The export goes under " + node.path() + ".");
        } else {
            target = null;
        }
        if (ok != null) {
            ok.setEnabled(target != null);
        }
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, CONNECTION, "&Connection...", false);
        createButton(parent, IDialogConstants.OK_ID, "Choose", true).setEnabled(false);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    }

    @Override
    protected void buttonPressed(int id) {
        if (id == CONNECTION) {
            if (PreferencesUtil.createPreferenceDialogOn(getShell(), DocumentationPreferencePage.ID,
                    new String[] { DocumentationPreferencePage.ID }, DocumentationPreferencePage.CONFLUENCE_TAB).open() == OK) {
                connect();
            }
            return;
        }
        super.buttonPressed(id);
    }

    @Override
    protected void okPressed() {
        Prefs.confluenceTarget(target);
        super.okPressed();
    }

    /** The place chosen. */
    ConfluenceTarget target() {
        return target;
    }

    private final class Content implements ITreeContentProvider {
        @Override
        public Object[] getElements(Object input) {
            return roots.toArray();
        }

        @Override
        public Object[] getChildren(Object element) {
            if (!(element instanceof Node)) {
                return new Object[0];
            }
            Node node = (Node) element;
            if (node.children == null) {
                open(node);
            }
            return node.children.toArray();
        }

        @Override
        public Object getParent(Object element) {
            return element instanceof Node ? ((Node) element).parent : null;
        }

        @Override
        public boolean hasChildren(Object element) {
            return element instanceof Node && (((Node) element).children == null || !((Node) element).children.isEmpty());
        }
    }

    private static final class Labels extends LabelProvider {
        @Override
        public String getText(Object element) {
            if (!(element instanceof Node)) {
                return element.toString();
            }
            Node node = (Node) element;
            return node.page != null ? node.page.title
                    : (node.space.name != null ? node.space.name : node.space.key) + " (" + node.space.key + ")";
        }

        @Override
        public Image getImage(Object element) {
            ISharedImages images = PlatformUI.getWorkbench().getSharedImages();
            if (!(element instanceof Node)) {
                return LOADING.equals(element) ? null : images.getImage(ISharedImages.IMG_OBJS_WARN_TSK);
            }
            return images.getImage(((Node) element).page == null ? ISharedImages.IMG_OBJ_FOLDER : ISharedImages.IMG_OBJ_FILE);
        }
    }
}
