package io.github.p3chys.documentationarchi.ui;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.gef.EditPart;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationAdapter;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.part.ViewPart;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.util.ArchimateModelUtils;

import io.github.p3chys.documentationarchi.doc.Assets;
import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.render.Preview;
import io.github.p3chys.documentationarchi.text.KeywordRules;

/**
 * "Documentation Preview": the selected element, relationship or view as the HTML export shows it, read and rendered
 * the same way, kept up to date while the model changes. It only reads: the model is never changed and no export is
 * noted for the model tree marks. The page goes through a file, which every browser SWT uses can show at any size
 * and with the model's assets, and scripts stay off.
 */
public final class PreviewView extends ViewPart {

    public static final String ID = "io.github.p3chys.documentationarchi.preview";
    /** How long the model must stay unchanged before the preview follows: typing a description is many changes. */
    private static final int DELAY = 500;

    private Browser browser;
    private EObject target;
    private IArchimateModel model;
    /** Changed while the view was hidden: shown again when it comes back. */
    private boolean stale;
    private Path folder;
    private Path page;
    private int pages;
    /** The control that had the focus before a page was loaded, which keeps it. */
    private Control focus;
    /** The real files of the assets the page shows or links to: the only files a click opens. */
    private Set<Path> assets = Set.of();
    private final PictureCache pictures = new PictureCache(ViewImages::render);
    private final Runnable refresh = this::refresh;

    private final ISelectionListener selections = (part, selection) -> {
        if (part != this) {
            select(selection);
        }
    };

    private final PropertyChangeListener changes = this::modelEvent;

    private final IPartListener2 visibility = new IPartListener2() {
        @Override
        public void partVisible(IWorkbenchPartReference ref) {
            if (ref.getPart(false) == PreviewView.this && stale) {
                refresh();
            }
        }
    };

    @Override
    public void createPartControl(Composite parent) {
        try {
            browser = new Browser(parent, SWT.NONE);
        } catch (SWTError e) {
            Label label = new Label(parent, SWT.WRAP);
            label.setText("The preview needs a web browser, and none is available: " + e.getMessage());
            return;
        }
        browser.setJavascriptEnabled(false);
        browser.addLocationListener(new LocationAdapter() {
            @Override
            public void changing(LocationEvent event) {
                follow(event);
            }
        });
        browser.addProgressListener(new ProgressAdapter() {
            @Override
            public void completed(ProgressEvent event) {
                keepFocus();
            }
        });
        getSite().getPage().addPostSelectionListener(selections);
        getSite().getPage().addPartListener(visibility);
        IEditorModelManager.INSTANCE.addPropertyChangeListener(changes);
        select(getSite().getPage().getSelection());
        if (target == null) {
            refresh();
        }
    }

    @Override
    public void setFocus() {
        if (browser != null) {
            browser.setFocus();
        }
    }

    @Override
    public void dispose() {
        IEditorModelManager.INSTANCE.removePropertyChangeListener(changes);
        getSite().getPage().removePostSelectionListener(selections);
        getSite().getPage().removePartListener(visibility);
        Display.getDefault().timerExec(-1, refresh);
        deleteFiles(null);
        if (folder != null) {
            try {
                Files.deleteIfExists(folder);
            } catch (IOException e) {
                // a temporary folder; the system clears it eventually
            }
        }
        super.dispose();
    }

    /** Shows the object now; "Show in Documentation Preview" and links in the preview come here. */
    void show(EObject object) {
        if (object == null || object == target) {
            return;
        }
        target = object;
        model = PreviewTarget.model(object);
        Display.getDefault().timerExec(-1, refresh);
        refresh();
    }

    private void select(ISelection selection) {
        if (selection instanceof IStructuredSelection && !selection.isEmpty()) {
            Object first = ((IStructuredSelection) selection).getFirstElement();
            show(PreviewTarget.of(first instanceof EditPart ? ((EditPart) first).getModel() : first));
        }
    }

    private void modelEvent(PropertyChangeEvent event) {
        if (Display.getCurrent() == null) {
            Display.getDefault().asyncExec(() -> modelEvent(event));
            return;
        }
        if (browser == null || browser.isDisposed() || model == null) {
            return;
        }
        String property = event.getPropertyName();
        if (IEditorModelManager.PROPERTY_MODEL_REMOVED.equals(property) && event.getNewValue() == model) {
            target = null; // closed
            model = null;
            refresh();
        } else if (IEditorModelManager.PROPERTY_ECORE_EVENT.equals(property) && event.getNewValue() instanceof Notification
                && PreviewTarget.affects((Notification) event.getNewValue(), model)) {
            // again only once the changes stop: each one starts the wait anew
            Display.getCurrent().timerExec(DELAY, refresh);
        }
    }

    private void refresh() {
        if (browser == null || browser.isDisposed()) {
            return;
        }
        if (!getSite().getPage().isPartVisible(this)) {
            stale = true;
            return;
        }
        stale = false;
        String html;
        try {
            html = render();
        } catch (IllegalArgumentException e) { // formatting rules that are not valid: the message names file and line
            html = Preview.message(e.getMessage());
        } catch (IOException | RuntimeException e) {
            Activator.log("The documentation preview failed", e);
            html = Preview.message("The preview failed: " + e);
        }
        try {
            load(html);
        } catch (IOException e) {
            Activator.log("The documentation preview could not be written", e);
        }
    }

    private String render() throws IOException {
        IArchimateModel of = target == null ? null : PreviewTarget.model(target);
        if (of == null) {
            return PreviewTarget.html(target, null, null, null, null);
        }
        model = of;
        KeywordRules rules = ProjectRules.resolve(of, null, Prefs::rules);
        IDiagramModel view = PreviewTarget.view(target);
        // what an export of the whole model gives; of the pictures only the one shown is needed
        Doc doc = ModelReader.read(ModelReader.Selection.of(List.of(of)), (v, p) -> {
            if (v == view) {
                pictures.fill(v, p);
            }
        }, new ArrayList<>());
        List<IOException> failed = new ArrayList<>();
        Set<Assets.Asset> used = new HashSet<>();
        String html = PreviewTarget.html(target, doc, rules, p -> {
            try {
                return picture(p);
            } catch (IOException e) {
                failed.add(e);
                return null;
            }
        }, used);
        if (!failed.isEmpty()) {
            throw failed.get(0);
        }
        assets = used.stream().map(a -> a.file).collect(Collectors.toSet()); // real paths, see Assets
        return html;
    }

    /** Writes the picture under a name of its own, so the browser never shows one it kept from before. */
    private String picture(DocPage p) throws IOException {
        String name = "picture-" + Integer.toHexString(Arrays.hashCode(p.image)) + ".png";
        Path file = folder().resolve(name);
        if (!Files.exists(file)) {
            deleteFiles("picture-");
            Files.write(file, p.image);
        }
        return name;
    }

    private void load(String html) throws IOException {
        Path previous = page;
        page = folder().resolve("preview-" + ++pages + ".html");
        Files.writeString(page, html, StandardCharsets.UTF_8);
        if (previous != null) {
            Files.deleteIfExists(previous);
        }
        Control focused = browser.getDisplay().getFocusControl();
        focus = focused == browser ? null : focused;
        browser.setUrl(page.toUri().toString());
    }

    /** Some browsers take the focus when they load a page; the one typing a description keeps it. */
    private void keepFocus() {
        Control had = focus;
        focus = null;
        if (had != null && !had.isDisposed() && browser.getDisplay().getFocusControl() != had
                && (browser.isFocusControl() || browser.getDisplay().getFocusControl() == null)) {
            had.setFocus();
        }
    }

    private void follow(LocationEvent event) {
        switch (PreviewLinks.decide(event.location, page, assets)) {
            case LOAD:
                return;
            case SHOW:
                event.doit = false;
                Object found = model == null ? null : ArchimateModelUtils.getObjectByID(model, Preview.target(event.location));
                if (found instanceof EObject) {
                    show((EObject) found);
                }
                return;
            case OPEN:
                event.doit = false;
                Path file = PreviewLinks.file(event.location);
                Path asset = file == null ? null : PreviewLinks.asset(file, assets);
                if (file == null || asset != null) { // checked again: decide() said yes for the same file
                    Program.launch(asset != null ? asset.toString() : event.location);
                }
                return;
            default:
                event.doit = false;
        }
    }

    private Path folder() throws IOException {
        if (folder == null) {
            folder = Files.createTempDirectory("documentationarchi-preview");
        }
        return folder;
    }

    /** @param prefix of the files to delete, null for all */
    private void deleteFiles(String prefix) {
        if (folder == null) {
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder)) {
            for (Path file : files) {
                if (prefix == null || file.getFileName().toString().startsWith(prefix)) {
                    Files.deleteIfExists(file);
                }
            }
        } catch (IOException e) {
            // a temporary folder; the system clears it eventually
        }
    }
}
