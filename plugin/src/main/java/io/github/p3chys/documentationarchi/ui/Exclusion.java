package io.github.p3chys.documentationarchi.ui;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.emf.ecore.EObject;

import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDocumentable;
import com.archimatetool.model.IProperties;
import com.archimatetool.model.IProperty;

import io.github.p3chys.documentationarchi.text.KeywordRules;

/**
 * What the rules leave out of an export: a view, element or relationship (and everything in a folder) with a
 * {@value #PROPERTY} property that is not {@code false}, or whose description carries a label of an
 * {@code exclude label} rule. Objects the user selected themselves are exported all the same, and so is
 * everything in a selected folder that has the property itself.
 */
final class Exclusion {

    /** The property that leaves an object out. */
    static final String PROPERTY = "doc:exclude";

    private static final ThreadLocal<Exclusion> CURRENT = new ThreadLocal<>();

    private final Set<Pattern> labels = new LinkedHashSet<>();
    private final Set<Object> exempt;
    private final Set<EObject> left = Collections.synchronizedSet(new LinkedHashSet<>());

    /** @param exempt what was selected directly: not left out, and the folders above it no longer count */
    Exclusion(KeywordRules rules, Collection<?> exempt) {
        for (String label : rules.excludedLabels()) {
            String word = Pattern.quote(label);
            labels.add(Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_])#" + word + "(?![\\p{L}\\p{N}_-])|\\[\\[\\s*" + word
                    + "\\s*\\]\\]"));
        }
        this.exempt = new LinkedHashSet<>(exempt);
    }

    /** Nothing is left out. */
    static Exclusion none() {
        return new Exclusion(KeywordRules.defaults(), Set.of());
    }

    /** The exclusion of the export running on this thread, for the pictures; null outside one. */
    static Exclusion current() {
        return CURRENT.get();
    }

    static void current(Exclusion exclusion) {
        if (exclusion == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(exclusion);
        }
    }

    /** Whether the view, element or relationship is left out. Remembers it for {@link #count()}. */
    boolean excluded(EObject object) {
        if (object == null || !test(object)) {
            return false;
        }
        left.add(object);
        return true;
    }

    /** Whether a relationship is left out, or an element at its ends is. */
    boolean excludedWithEnds(IArchimateRelationship relationship) {
        return excluded((EObject) relationship) | excluded(relationship.getSource())
                | excluded(relationship.getTarget());
    }

    /** How many objects the rules left out so far. */
    int count() {
        return left.size();
    }

    private boolean test(EObject object) {
        for (EObject o = object; o != null; o = o.eContainer()) {
            if (exempt.contains(o)) {
                if (o == object) {
                    return false;
                }
                break; // a selected folder: what is inside is checked on its own
            }
            if (flagged(o)) {
                return true;
            }
        }
        return object instanceof IDocumentable && labelled(((IDocumentable) object).getDocumentation());
    }

    private boolean labelled(String text) {
        return text != null && labels.stream().anyMatch(p -> p.matcher(text).find());
    }

    /** Whether the object itself has the property with a value other than false. */
    static boolean flagged(EObject o) {
        if (o instanceof IProperties) {
            for (IProperty p : ((IProperties) o).getProperties()) {
                if (PROPERTY.equalsIgnoreCase(p.getKey() == null ? "" : p.getKey().strip())
                        && !"false".equals(p.getValue() == null ? "" : p.getValue().strip().toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }
}
