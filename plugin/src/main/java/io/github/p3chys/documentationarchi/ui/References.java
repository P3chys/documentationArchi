package io.github.p3chys.documentationarchi.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.gef.commands.Command;

import com.archimatetool.editor.model.commands.EObjectFeatureCommand;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateModelObject;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IJunction;

/**
 * "Insert Reference...": writes {@code [[ref:id]]} into a description, which the export shows as a link to the
 * element or view by its current name, so renaming it never leaves an old name behind.
 */
final class References {

    private References() {
    }

    /** What a description can refer to: the views and elements of the model (not junctions or PrintOrder), by name. */
    static List<IArchimateModelObject> targets(IArchimateModel model) {
        List<IArchimateModelObject> targets = new ArrayList<>();
        model.eAllContents().forEachRemaining(o -> {
            if (o instanceof IDiagramModel || o instanceof IArchimateElement && !(o instanceof IJunction)
                    && !ModelReader.isPrintOrder((IArchimateElement) o)) {
                targets.add((IArchimateModelObject) o);
            }
        });
        targets.sort(Comparator.comparing(ModelReader::label, String.CASE_INSENSITIVE_ORDER));
        return targets;
    }

    /** The description with the reference added on a line of its own at its end. */
    static String appended(String description, String id) {
        String text = description == null ? "" : description;
        return text + (text.isEmpty() || text.endsWith("\n") ? "" : "\n") + "[[ref:" + id + "]]";
    }

    /** Adds the reference to the object's description, as one command that Undo takes back; null if it has none. */
    static Command add(EObject target, IArchimateModelObject to) {
        EAttribute feature = AssetLinks.descriptionOf(target);
        return feature == null ? null : new EObjectFeatureCommand("Insert Reference", target, feature,
                appended((String) target.eGet(feature), to.getId()));
    }
}
