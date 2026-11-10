package io.github.p3chys.documentationarchi.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;

import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateModelObject;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelComponent;
import com.archimatetool.model.IDiagramModelArchimateComponent;
import com.archimatetool.model.IIdentifier;
import com.archimatetool.model.IProperty;

/**
 * What the model looked like at its last documentation export, so the model tree can show what no export reflects
 * yet ({@link ExportMarks}). Every export keeps, for each element, relationship and view it shows (and the model, for
 * the overview page), a fingerprint of what the export shows of it, with the time. Whatever export was the last to
 * show an object counts, so exporting a work package brings its elements up to date too.
 * <p>
 * The fingerprints are kept per model (by its id) in the plugin's own folder of Archi's workspace, not in the model:
 * an export doesn't change the model, and needs no saving.
 */
final class ExportState {

    /** How an object stands against the exports. */
    enum Status {
        /** As the last export that showed it. */
        CURRENT,
        /** Changed since the last export that showed it. */
        CHANGED,
        /** Only its layout (positions, sizes, fonts, colours) changed since; what it says is as exported. */
        LAYOUT,
        /** No export showed it yet. */
        NEW
    }

    /** The status of one object, and when it was last exported (0 for never). */
    static final class Mark {
        final Status status;
        final long exported;

        Mark(Status status, long exported) {
            this.status = status;
            this.exported = exported;
        }
    }

    private static ExportState instance;

    private final Path folder;
    private final Map<String, Properties> models = new HashMap<>();

    ExportState(Path folder) {
        this.folder = folder;
    }

    /** The state kept in the plugin's folder of the workspace. */
    static synchronized ExportState get() {
        if (instance == null) {
            instance = new ExportState(Activator.get().getStateLocation().toFile().toPath().resolve("exports"));
        }
        return instance;
    }

    /**
     * Whether exports can show the object, so its mark means something. PrintOrder and its links are modelling aids
     * that never appear; notes, groups and other diagram objects are part of their view.
     */
    static boolean tracked(Object o) {
        if (o instanceof IArchimateElement) {
            return !ModelReader.isPrintOrder((IArchimateConcept) o);
        }
        if (o instanceof IArchimateRelationship) {
            IArchimateRelationship r = (IArchimateRelationship) o;
            return !ModelReader.isPrintOrder(r.getSource()) && !ModelReader.isPrintOrder(r.getTarget());
        }
        return o instanceof IDiagramModel || o instanceof IArchimateModel;
    }

    /**
     * A hash of everything the export shows of the object: its name, description, properties and other settings; of
     * a view, also everything on it, with the names it shows, since they are in its picture. The relationships of an
     * element are objects of their own; of the model only what its overview page shows counts.
     */
    static String fingerprint(EObject o) {
        Print print = print(o);
        return hash(print.content) + "," + hash(print.layout);
    }

    /** What versions before the content/layout split kept: one hash of everything. */
    static String legacyFingerprint(EObject o) {
        return hash(print(o).all);
    }

    private static Print print(EObject o) {
        Print print = new Print();
        if (o instanceof IArchimateModel) {
            IArchimateModel model = (IArchimateModel) o;
            StringBuilder s = new StringBuilder();
            s.append(model.getName()).append('\0').append(model.getPurpose()).append('\0');
            for (IProperty p : model.getProperties()) {
                s.append(p.getKey()).append('=').append(p.getValue()).append('\0');
            }
            print.all.append(s);
            print.content.append(s);
        } else {
            write(o, print, false);
        }
        return print;
    }

    private static String hash(CharSequence text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // every Java has SHA-256
        }
    }

    /** What is said (names, texts, properties, what is on a view) apart from how it is drawn; all is both, as before. */
    private static final class Print {
        final StringBuilder all = new StringBuilder();
        final StringBuilder content = new StringBuilder();
        final StringBuilder layout = new StringBuilder();

        /** Structure: in both, unless inside a part that is layout only. */
        void frame(boolean inLayout, String text) {
            all.append(text);
            layout.append(text);
            if (!inLayout) {
                content.append(text);
            }
        }

        void value(boolean inLayout, String text) {
            all.append(text);
            (inLayout ? layout : content).append(text);
        }
    }

    /** Features that only draw: position, size, bend points, fonts, colours, line and text looks, pictures. */
    private static final Set<String> LAYOUT = Set.of("bounds", "bendpoints", "font", "fontColor", "fillColor",
            "lineColor", "lineWidth", "lineStyle", "alpha", "lineAlpha", "gradient", "iconColor", "iconVisibleState",
            "textAlignment", "textPosition", "deriveElementLineColor", "borderColor", "imagePath", "imagePosition",
            "connectionRouterType", "background", "relativePosition", "nameVisible");

    private static boolean isLayout(EObject o, EStructuralFeature f) {
        // "type" of a diagram object or connection is its figure or line style; of a folder, what it holds
        return LAYOUT.contains(f.getName()) || "type".equals(f.getName()) && o instanceof IDiagramModelComponent;
    }

    /** All stored features, children in full and other objects by id; what the model works out itself is left out. */
    private static void write(EObject o, Print s, boolean layout) {
        s.frame(layout, o.eClass().getName() + "{");
        if (o instanceof IDiagramModelArchimateComponent) {
            // what it shows, by a feature the model doesn't store as such; its name is in the picture
            IArchimateConcept concept = ((IDiagramModelArchimateComponent) o).getArchimateConcept();
            if (concept != null) {
                s.value(layout, concept.getId() + "\"" + concept.getName() + "\"");
            }
        }
        for (EStructuralFeature f : o.eClass().getEAllStructuralFeatures()) {
            if (f.isTransient() || f.isDerived() || f.isVolatile() || !o.eIsSet(f)) {
                continue;
            }
            boolean inLayout = layout || isLayout(o, f);
            s.value(inLayout, f.getName() + "=");
            Object value = o.eGet(f);
            for (Object v : f.isMany() ? (List<?>) value : Collections.singletonList(value)) {
                if (f instanceof EReference && ((EReference) f).isContainment()) {
                    write((EObject) v, s, inLayout);
                } else if (f instanceof EReference) {
                    s.value(inLayout, v instanceof IIdentifier ? ((IIdentifier) v).getId() : String.valueOf(v));
                } else {
                    s.value(inLayout, String.valueOf(v));
                }
                s.value(inLayout, "\u001f");
            }
            s.value(inLayout, "\u001e");
        }
        s.frame(layout, "}");
    }

    /** The fingerprints of the objects, by id, for {@link #record}. Of objects that aren't tracked, none. */
    static Map<String, String> fingerprints(Collection<? extends EObject> objects) {
        Map<String, String> fingerprints = new LinkedHashMap<>();
        for (EObject o : objects) {
            if (tracked(o) && o instanceof IIdentifier) {
                fingerprints.put(((IIdentifier) o).getId(), fingerprint(o));
            }
        }
        return fingerprints;
    }

    /** How the object stands against the exports; null for objects that aren't tracked. */
    synchronized Mark mark(EObject o) {
        if (!tracked(o) || !(o instanceof IIdentifier)) {
            return null;
        }
        IArchimateModel model = o instanceof IArchimateModelObject ? ((IArchimateModelObject) o).getArchimateModel() : null;
        String stored = model == null ? null : load(model.getId()).getProperty(((IIdentifier) o).getId());
        if (stored == null) {
            return new Mark(Status.NEW, 0);
        }
        int space = stored.indexOf(' ');
        String fingerprint = space < 0 ? stored : stored.substring(0, space);
        boolean legacy = fingerprint.indexOf(',') < 0; // one hash of everything: layout cannot be told apart
        long time = 0;
        try {
            time = space < 0 ? 0 : Long.parseLong(stored.substring(space + 1));
        } catch (NumberFormatException e) {
            // an edited file: the fingerprint still counts
        }
        return new Mark(status(fingerprint, legacy, o), time);
    }

    private static Status status(String stored, boolean legacy, EObject o) {
        if (legacy) {
            return stored.equals(legacyFingerprint(o)) ? Status.CURRENT : Status.CHANGED;
        }
        String[] now = fingerprint(o).split(",");
        String[] then = stored.split(",");
        if (!then[0].equals(now[0])) {
            return Status.CHANGED;
        }
        return then.length > 1 && then[1].equals(now[1]) ? Status.CURRENT : Status.LAYOUT;
    }

    /** Notes that an export at this time showed the objects as they were then. */
    synchronized void record(IArchimateModel model, Map<String, String> fingerprints, long time) throws IOException {
        if (fingerprints.isEmpty()) {
            return;
        }
        Properties properties = load(model.getId());
        fingerprints.forEach((id, fingerprint) -> properties.setProperty(id, fingerprint + " " + time));
        Files.createDirectories(folder);
        Path file = file(model.getId());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(temporary)) {
            properties.store(out, "documentationArchi: what the last export of each object showed, model " + model.getId());
        }
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private Properties load(String modelId) {
        return models.computeIfAbsent(modelId, id -> {
            Properties properties = new Properties();
            Path file = file(id);
            if (Files.isRegularFile(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    properties.load(in);
                } catch (IOException | IllegalArgumentException e) {
                    Activator.log("Cannot read " + file + ", every object counts as not exported", e);
                }
            }
            return properties;
        });
    }

    /** Model ids are Archi's, letters, digits and dashes; anything else is replaced, so a file name never escapes. */
    private Path file(String modelId) {
        return folder.resolve(modelId.replaceAll("[^A-Za-z0-9_-]", "_") + ".properties");
    }
}
