package io.github.p3chys.documentationarchi.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.emf.ecore.EObject;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelComponent;
import com.archimatetool.model.IDocumentable;
import com.archimatetool.model.IIdentifier;
import com.archimatetool.model.IJunction;
import com.archimatetool.model.INameable;
import com.archimatetool.model.IProperty;
import com.archimatetool.model.ITextContent;
import com.archimatetool.model.IWorkPackage;

import io.github.p3chys.documentationarchi.doc.Assets;
import io.github.p3chys.documentationarchi.text.KeywordRules;
import io.github.p3chys.documentationarchi.text.RichText;

/**
 * Finds what is wrong with the documentation of a model without exporting it: things nobody described, things no
 * export would show, references that lead nowhere, and labels with a typo. Objects the rules leave out
 * ({@link Exclusion}) are not checked. No UI here, so tests run headless.
 */
final class DocCheck {

    /** The kinds of findings, in the order they are listed. */
    enum Kind {
        NO_DESCRIPTION("Elements and views without a description"),
        ON_NO_VIEW("Elements on no view"),
        EMPTY_VIEW("Views without elements"),
        ASSET("Assets that are missing or ambiguous"),
        REFERENCE("References to objects that no longer exist"),
        LABEL("Words that look like a label but are not one"),
        UNRESOLVED_TOKEN("˝tokens˝ that match no relationship"),
        MALFORMED_TOKEN("Malformed ˝tokens˝"),
        WORK_PACKAGE("Work packages that change no element");

        final String title;

        Kind(String title) {
            this.title = title;
        }
    }

    /** One finding: what is wrong, and with which object. */
    static final class Finding {
        final Kind kind;
        final EObject object;
        /** The name of the object. */
        final String name;
        /** What is wrong, empty when the kind says it all. */
        final String detail;

        Finding(Kind kind, EObject object, String detail) {
            this.kind = kind;
            this.object = object;
            this.name = object instanceof IArchimateModel ? ModelReader.label((IArchimateModel) object)
                    : object instanceof INameable
                            ? ModelReader.label((INameable) object) : "";
            this.detail = detail;
        }

        /** One line for lists. */
        String line() {
            return detail.isEmpty() ? name : name + ": " + detail;
        }
    }

    private static final Pattern HASH = Pattern.compile("(?<![\\p{L}\\p{N}_#&])#([\\p{L}\\p{N}_-]{3,})");
    /** A HaF abbreviation token: {@code ˝˝NAME˝˝} or {@code ˝NAME˝}. */
    private static final Pattern TOKEN = Pattern.compile("˝˝([^˝]+)˝˝|˝([^˝]+)˝");
    private static final Pattern CODE = Pattern.compile("(?s)<sql>.*?</sql>|<code[^>]*>.*?</code>|```.*?```");

    private final IArchimateModel model;
    private final KeywordRules rules;
    private final Exclusion exclusion;
    private final Assets assets;
    private final Set<String> ids = new HashSet<>();
    private final List<Finding> findings = new ArrayList<>();

    private DocCheck(IArchimateModel model, KeywordRules rules) {
        this.model = model;
        this.rules = rules;
        this.exclusion = new Exclusion(rules, Set.of());
        Path file = model.getFile() == null ? null : model.getFile().getAbsoluteFile().toPath().getParent();
        this.assets = Assets.in(file, rules.assetsFolder());
    }

    /** The findings for the model, grouped by kind in the order of {@link Kind}, each kind in model order. */
    static List<Finding> check(IArchimateModel model, KeywordRules rules) {
        DocCheck check = new DocCheck(model, rules);
        check.run();
        List<Finding> sorted = new ArrayList<>(check.findings);
        sorted.sort((a, b) -> Integer.compare(a.kind.ordinal(), b.kind.ordinal()));
        return sorted;
    }

    private void run() {
        ids.add(model.getId());
        model.eAllContents().forEachRemaining(o -> {
            if (o instanceof IIdentifier) {
                ids.add(((IIdentifier) o).getId());
            }
        });
        texts(model, model.getPurpose());
        model.eAllContents().forEachRemaining(o -> {
            if (o instanceof IDiagramModel) {
                view((IDiagramModel) o);
            } else if (o instanceof IArchimateElement) {
                element((IArchimateElement) o);
            } else if (o instanceof IArchimateRelationship) {
                relationship((IArchimateRelationship) o);
            }
        });
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }

    private void view(IDiagramModel view) {
        if (exclusion.excluded(view)) {
            return;
        }
        if (blank(view.getDocumentation())) {
            findings.add(new Finding(Kind.NO_DESCRIPTION, view, "view"));
        }
        if (view instanceof IArchimateDiagramModel && ModelReader.elementsOn(view).isEmpty()) {
            findings.add(new Finding(Kind.EMPTY_VIEW, view, ""));
        }
        texts(view, view.getDocumentation());
        properties(view, view.getProperties());
        // notes, and the texts of sketch and canvas objects, belong to the view
        view.eAllContents().forEachRemaining(o -> {
            if (o instanceof IDiagramModelComponent && !(o instanceof IDiagramModelArchimateObject)) {
                if (o instanceof ITextContent) {
                    texts(view, ((ITextContent) o).getContent());
                }
                if (o instanceof IDocumentable) {
                    texts(view, ((IDocumentable) o).getDocumentation());
                }
            }
        });
    }

    private void element(IArchimateElement element) {
        if (element instanceof IJunction || ModelReader.isPrintOrder(element) || exclusion.excluded(element)) {
            return;
        }
        if (element instanceof IWorkPackage) {
            if (WorkPackages.elements((IWorkPackage) element).isEmpty()) {
                findings.add(new Finding(Kind.WORK_PACKAGE, element, ""));
            }
        } else if (element.getReferencingDiagramComponents().isEmpty()) {
            findings.add(new Finding(Kind.ON_NO_VIEW, element, typeName(element)));
        }
        if (blank(element.getDocumentation())) {
            findings.add(new Finding(Kind.NO_DESCRIPTION, element, typeName(element)));
        }
        texts(element, element.getDocumentation());
        tokens(element);
        properties(element, element.getProperties());
    }

    /**
     * The HaF tokens of the element's description: each must be the name of one of its relationships (the double form
     * first), and the ˝ must come in pairs with no ¨ typed for one.
     */
    private void tokens(IArchimateElement element) {
        String text = element.getDocumentation();
        if (blank(text)) {
            return;
        }
        long marks = text.chars().filter(c -> c == '˝').count();
        if (marks % 2 != 0 || text.indexOf('¨') >= 0) {
            findings.add(new Finding(Kind.MALFORMED_TOKEN, element,
                    marks % 2 != 0 ? "odd number of ˝" : "¨ where ˝ was meant"));
        }
        Set<String> names = new HashSet<>();
        List<IArchimateRelationship> all = new ArrayList<>(element.getSourceRelationships());
        all.addAll(element.getTargetRelationships());
        for (IArchimateRelationship r : all) {
            if (!(r.getSource() instanceof IJunction) && !(r.getTarget() instanceof IJunction)
                    && !ModelReader.isPrintOrder(r.getSource()) && !ModelReader.isPrintOrder(r.getTarget())
                    && WorkPackages.changed(r) == null && r.getName() != null) {
                names.add(r.getName().strip());
            }
        }
        Set<String> seen = new HashSet<>();
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            boolean twice = m.group(1) != null;
            String inner = twice ? m.group(1) : m.group(2);
            String written = twice ? "˝˝" + inner + "˝˝" : "˝" + inner + "˝";
            boolean found = names.contains("˝˝" + inner + "˝˝") || names.contains("˝" + inner + "˝");
            if (!found && seen.add(written)) {
                findings.add(new Finding(Kind.UNRESOLVED_TOKEN, element, written));
            }
        }
    }

    private void relationship(IArchimateRelationship r) {
        if (ModelReader.isPrintOrder(r.getSource()) || ModelReader.isPrintOrder(r.getTarget())
                || exclusion.excludedWithEnds(r)) {
            return;
        }
        texts(r, r.getDocumentation());
        properties(r, r.getProperties());
    }

    private static String typeName(IArchimateElement element) {
        return element.eClass().getName().replaceAll("(?<=[a-z])(?=[A-Z])", " ");
    }

    private void properties(EObject owner, List<IProperty> properties) {
        properties.forEach(p -> texts(owner, p.getValue()));
    }

    /** The assets, references and labels in one text of the owner. */
    private void texts(EObject owner, String text) {
        if (blank(text)) {
            return;
        }
        String where = owner instanceof INameable
                ? ((INameable) owner).getName() : "";
        Set<String> seen = new HashSet<>();
        for (RichText.Block block : RichText.parse(text, rules)) {
            RichText.visit(block, i -> {
                if ((i.kind == RichText.InlineKind.ASSET || i.kind == RichText.InlineKind.BAD_ASSET) && seen.add("a" + i.text)) {
                    int before = assets.problems().size();
                    if (i.kind == RichText.InlineKind.ASSET) {
                        assets.find(i.text, where);
                    } else {
                        assets.unsupported(i.text, where);
                    }
                    List<String> problems = assets.problems();
                    if (problems.size() > before) {
                        findings.add(new Finding(Kind.ASSET, owner, problems.get(problems.size() - 1)));
                    }
                } else if (i.kind == RichText.InlineKind.REF && !ids.contains(i.text) && seen.add("r" + i.text)) {
                    findings.add(new Finding(Kind.REFERENCE, owner, "[[ref:" + i.text + "]]"));
                }
            });
        }
        Matcher m = HASH.matcher(CODE.matcher(text).replaceAll(" "));
        while (m.find()) {
            String word = m.group(1);
            String like = look(word);
            if (like != null && seen.add("l" + word.toLowerCase(Locale.ROOT))) {
                findings.add(new Finding(Kind.LABEL, owner, "#" + word + " (did you mean #" + like + "?)"));
            }
        }
    }

    /** The configured label this word differs from by one or two characters, null if it is no typo of one. */
    private String look(String word) {
        String w = word.toLowerCase(Locale.ROOT);
        if (w.chars().noneMatch(Character::isLetter) || rules.tag(w) != null) {
            return null;
        }
        for (String label : rules.labels()) {
            int distance = distance(w, label.toLowerCase(Locale.ROOT));
            // two changes in a short word would make almost any word a typo
            if (distance == 1 || distance == 2 && Math.min(w.length(), label.length()) >= 4) {
                return label;
            }
        }
        return null;
    }

    /** The number of single-character changes, insertions and deletions between two words. */
    static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            int[] current = new int[b.length() + 1];
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int same = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(same, Math.min(previous[j], current[j - 1]) + 1);
            }
            previous = current;
        }
        return previous[b.length()];
    }

    /** The findings as text, grouped by kind with counts: the output of the command line. */
    static String format(List<Finding> findings) {
        Map<Kind, List<Finding>> byKind = new LinkedHashMap<>();
        findings.forEach(f -> byKind.computeIfAbsent(f.kind, k -> new ArrayList<>()).add(f));
        StringBuilder text = new StringBuilder();
        byKind.forEach((kind, list) -> {
            text.append(kind.title).append(" (").append(list.size()).append(")\n");
            list.forEach(f -> text.append("  - ").append(f.line()).append("\n"));
        });
        return text.toString();
    }
}
