package io.github.p3chys.documentationarchi.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.EObject;

import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateModelObject;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateComponent;
import com.archimatetool.model.IDiagramModelComponent;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IJunction;

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.render.Preview;
import io.github.p3chys.documentationarchi.text.KeywordRules;

/**
 * What the Documentation Preview shows for a selection, and which model changes it has to show again. Kept apart
 * from the view, which only has the browser and the timing, so it is tested without a workbench.
 */
final class PreviewTarget {

    static final String NOTHING = "Select an element, relationship or view to see it as the documentation export shows it.";

    private PreviewTarget() {
    }

    /**
     * The object to show for a selected one: what a view's figure stands for (a note or group: its view), or the
     * element, relationship, view, model or folder itself. Null for anything else. A diagram editor's selection is
     * of edit parts: their models are what is selected.
     */
    static EObject of(Object o) {
        if (o instanceof IDiagramModelArchimateComponent) {
            return ((IDiagramModelArchimateComponent) o).getArchimateConcept();
        }
        if (o instanceof IDiagramModelComponent && !(o instanceof IDiagramModel)) {
            return ((IDiagramModelComponent) o).getDiagramModel();
        }
        return o instanceof IArchimateModelObject ? (EObject) o : null;
    }

    /** The model of the object; null when it was deleted from it. */
    static IArchimateModel model(EObject target) {
        if (target instanceof IArchimateModel) {
            return (IArchimateModel) target;
        }
        return target instanceof IArchimateModelObject ? ((IArchimateModelObject) target).getArchimateModel() : null;
    }

    /** Whether a change can alter what the preview shows of the model: any real change to it. */
    static boolean affects(Notification change, IArchimateModel model) {
        if (model == null || change.isTouch()) {
            return false;
        }
        Object notifier = change.getNotifier();
        return notifier == model || notifier instanceof EObject && model((EObject) notifier) == model;
    }

    /** The view whose picture the preview shows, null when it shows none. */
    static IDiagramModel view(EObject target) {
        return target instanceof IDiagramModel ? (IDiagramModel) target : null;
    }

    /**
     * The HTML to show.
     *
     * @param doc the whole model as the export reads it
     * @param pictures where the picture of a page is, as a link of the document; writes it there if need be
     */
    static String html(EObject target, Doc doc, KeywordRules rules, Function<DocPage, String> pictures) {
        if (target == null || target instanceof IFolder) {
            return Preview.message(NOTHING);
        }
        if (model(target) == null) {
            return Preview.message("It is no longer in the model.");
        }
        if (target instanceof IJunction) {
            return Preview.message("Junctions are never described in the export: the relationships through them are.");
        }
        if (target instanceof IArchimateElement && ModelReader.isPrintOrder((IArchimateElement) target)) {
            return Preview.message("PrintOrder only sets the order of the descriptions; it never appears in the export.");
        }
        String id = ((IArchimateModelObject) target).getId();
        if (target instanceof IArchimateModel || target instanceof IDiagramModel) {
            Doc.Location at = doc.locate(id);
            DocPage page = target instanceof IArchimateModel ? doc.pages.get(0) : at == null ? null : at.page;
            return page == null ? Preview.message("The export has no page for it.")
                    : Preview.page(doc, page, rules, page.image == null ? null : pictures.apply(page));
        }
        List<String> ids = new ArrayList<>();
        if (target instanceof IArchimateRelationship) {
            // described with the elements at its ends (through junctions, those beyond them are)
            ids.add(((IArchimateRelationship) target).getSource().getId());
            ids.add(((IArchimateRelationship) target).getTarget().getId());
        } else {
            ids.add(id);
        }
        String html = Preview.elements(doc, ids, rules);
        return html != null ? html : Preview.message("The export describes it nowhere.");
    }
}
