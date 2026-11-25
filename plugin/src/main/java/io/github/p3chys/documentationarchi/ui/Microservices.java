package io.github.p3chys.documentationarchi.ui;

import java.util.List;
import java.util.Locale;

import org.eclipse.emf.ecore.EObject;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IJunction;
import com.archimatetool.model.IWorkPackage;

/**
 * Which microservice a view or an element belongs to, with {@code structure microservices}: a folder directly below
 * the Views folder is a microservice, and so is a folder of the same name (ignoring case and spaces around it)
 * directly below Application, Business or another folder of elements. An element in no such folder, a work package
 * or a junction belongs to none.
 */
final class Microservices {

    /** The layers in the order a microservice's page lists its elements. */
    private static final List<FolderType> LAYERS = List.of(FolderType.STRATEGY, FolderType.BUSINESS,
            FolderType.APPLICATION, FolderType.TECHNOLOGY, FolderType.MOTIVATION, FolderType.IMPLEMENTATION_MIGRATION,
            FolderType.OTHER);

    private Microservices() {
    }

    /** A microservice's folder name as it is compared: "Orders " and "orders" are one. */
    static String key(String name) {
        return name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
    }

    /** The microservice folder of the view (the first below Views), null for a view directly in Views. */
    static IFolder folderOf(IDiagramModel view) {
        List<IFolder> path = ModelReader.path(view);
        return path.isEmpty() ? null : path.get(0);
    }

    /** The microservice folder the element is in (the first below its layer's folder), null for none. */
    static IFolder folderOf(IArchimateElement element) {
        if (element instanceof IWorkPackage || element instanceof IJunction) {
            return null;
        }
        for (EObject f = element.eContainer(); f instanceof IFolder; f = f.eContainer()) {
            if (f.eContainer() instanceof IFolder && f.eContainer().eContainer() instanceof IArchimateModel) {
                return (IFolder) f;
            }
        }
        return null;
    }

    /** The key of the element's microservice, null for none. */
    static String of(IArchimateElement element) {
        IFolder folder = folderOf(element);
        return folder == null ? null : key(ModelReader.label(folder));
    }

    /** The key of the view's microservice, null for none. */
    static String of(IDiagramModel view) {
        IFolder folder = folderOf(view);
        return folder == null ? null : key(ModelReader.label(folder));
    }

    /**
     * Whether the view's microservice has a folder of elements: a folder of views without one, such as Landscape, is
     * a plain folder of views across microservices.
     */
    static boolean hasElements(IDiagramModel view) {
        String key = of(view);
        if (key == null) {
            return false;
        }
        for (IFolder top : view.getArchimateModel().getFolders()) {
            if (top.getType() != FolderType.DIAGRAMS && top.getType() != FolderType.RELATIONS
                    && top.getFolders().stream().anyMatch(f -> key.equals(key(ModelReader.label(f))))) {
                return true;
            }
        }
        return false;
    }

    /** Where the element's layer comes on a microservice's page: business before application before technology. */
    static int layer(IArchimateElement element) {
        for (EObject f = element.eContainer(); f instanceof IFolder; f = f.eContainer()) {
            if (f.eContainer() instanceof IArchimateModel) {
                int i = LAYERS.indexOf(((IFolder) f).getType());
                return i < 0 ? LAYERS.size() : i;
            }
        }
        return LAYERS.size();
    }
}
