package io.github.p3chys.documentationarchi.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IAccessRelationship;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IProfile;
import com.archimatetool.model.IProperty;
import com.archimatetool.model.IWorkPackage;

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.doc.Entry.Part;
import io.github.p3chys.documentationarchi.doc.Relation;
import io.github.p3chys.documentationarchi.text.KeywordRules;

/** The model conventions of HaF, the documentation tool documentationArchi replaces. */
class HafConventionsTest {

    private static final IArchimateFactory F = IArchimateFactory.eINSTANCE;
    /** U+02DD, HaF's token delimiter. */
    private static final String A = "˝";

    private IArchimateModel model;
    private IArchimateDiagramModel view;

    @BeforeEach
    void model() {
        model = F.createArchimateModel();
        model.setDefaults();
        model.setName("Model");
        view = F.createArchimateDiagramModel();
        view.setName("View");
        model.getFolder(FolderType.DIAGRAMS).getElements().add(view);
    }

    private <T extends IArchimateElement> T element(T element, String name) {
        element.setName(name);
        model.getDefaultFolderForObject(element).getElements().add(element);
        IDiagramModelArchimateObject object = F.createDiagramModelArchimateObject();
        object.setArchimateElement(element);
        view.getChildren().add(object);
        return element;
    }

    private <T extends IArchimateRelationship> T relate(T r, IArchimateConcept source, IArchimateConcept target, String name) {
        r.setName(name);
        r.connect(source, target);
        model.getFolder(FolderType.RELATIONS).getElements().add(r);
        return r;
    }

    private IProfile profile(String name, IArchimateConcept concept) {
        IProfile profile = F.createProfile();
        profile.setName(name);
        profile.setConceptType(concept.eClass().getName());
        model.getProfiles().add(profile);
        concept.getProfiles().add(profile);
        return profile;
    }

    private Doc read() {
        return read(KeywordRules.defaults(), List.of(view));
    }

    private Doc read(KeywordRules rules, List<?> selected) {
        ModelReader.Selection selection = ModelReader.Selection.of(selected);
        return ModelReader.read(selection, (v, page) -> { }, new ArrayList<>(), rules, selection.exclusion(rules));
    }

    private static Entry entry(Doc doc, String name) {
        return doc.pages.stream().flatMap(p -> p.entries.stream()).filter(e -> e.name.equals(name)).findFirst()
                .orElseThrow();
    }

    /** The parts as short text: kind, then what matters for it. */
    private static List<String> parts(List<Part> parts) {
        List<String> out = new ArrayList<>();
        for (Part p : parts) {
            out.add(part(p));
        }
        return out;
    }

    /** One part as short text; a TEXT part with what stands at its markers in braces. */
    private static String part(Part p) {
        switch (p.kind) {
            case SENTENCE:
                return "SENTENCE " + p.verb + " " + type(p) + p.link.label + " | " + p.text + " | " + p.note;
            case SECTION: return "SECTION " + p.title + ": " + p.text;
            case NESTED: return "NESTED " + type(p) + p.link.label + " " + parts(p.parts);
            case ROLE: return "ROLE " + p.text.replace("{role}", p.title);
            case RECURSIVE: return "RECURSIVE " + p.title;
            case OBJECT: return "OBJECT " + type(p) + p.link.label;
            default:
                String text = p.text;
                for (int i = 0; i < p.inline.size(); i++) {
                    text = text.replace(Part.marker(i), "{" + part(p.inline.get(i)) + "}");
                }
                return "TEXT " + text;
        }
    }

    private static String type(Part p) {
        return p.link.type == null ? "" : "«" + p.link.type + "»";
    }

    @Test
    void specializedMarkerOrdersByTheSmallestFirstNumberAndIsNeverShown() {
        IArchimateElement marker = element(F.createDataObject(), "Order of things");
        profile("PrintOrder", marker);
        IArchimateElement a = element(F.createBusinessActor(), "A");
        IArchimateElement b = element(F.createBusinessActor(), "B");
        IArchimateElement c = element(F.createBusinessActor(), "C");
        relate(F.createAssociationRelationship(), a, marker, "pos 20");
        relate(F.createAssociationRelationship(), b, marker, "10");
        relate(F.createAssociationRelationship(), b, marker, "30");
        relate(F.createAssociationRelationship(), marker, c, "5th");

        Doc doc = read();

        assertThat(doc.pages.get(0).entries).extracting(e -> e.name).containsExactly("C", "B", "A");
        assertThat(entry(doc, "A").relations).isEmpty();
        assertThat(ModelReader.isPrintOrder(marker)).isTrue();
    }

    @Test
    void groupingNamedPrintOrderStillOrders() {
        IArchimateElement marker = element(F.createGrouping(), "PrintOrder");
        IArchimateElement a = element(F.createBusinessActor(), "A");
        IArchimateElement b = element(F.createBusinessActor(), "B");
        relate(F.createAssociationRelationship(), a, marker, "002");
        relate(F.createAssociationRelationship(), b, marker, "001");

        assertThat(read().pages.get(0).entries).extracting(e -> e.name).containsExactly("B", "A");
    }

    @Test
    void hafTokensMatchRelationshipsNamedWithTheirDelimitersAndCrlfIsALineEnd() {
        IArchimateElement process = element(F.createBusinessProcess(), "Pay");
        IArchimateElement next = element(F.createBusinessProcess(), "Sign");
        IArchimateElement event = element(F.createBusinessEvent(), "Paid");
        relate(F.createTriggeringRelationship(), process, next, A + A + "0035" + A + A);
        relate(F.createTriggeringRelationship(), process, event, A + "write" + A).setDocumentation("When done");
        process.setDocumentation("First\r\n" + A + A + "0035" + A + A + "\r\nthen " + A + "write" + A
                + "\r\nnot " + A + A + "0099" + A + A);

        Entry pay = entry(read(), "Pay");

        // sentences stay on their line
        assertThat(parts(pay.body)).containsExactly("TEXT First\n{SENTENCE triggers «Process»Sign |  | null}\n"
                + "then {SENTENCE triggers «Event»Paid | When done | null}\nnot " + A + A + "0099" + A + A);
        assertThat(pay.documentation).isEqualTo("First\n[[ref:" + next.getId() + "]]\nthen [[ref:" + event.getId()
                + "]]\nnot " + A + A + "0099" + A + A);
        assertThat(pay.relations).isEmpty(); // both go out and are said
    }

    @Test
    void sentencesForEveryKindOfRelationshipKeepIncomingRows() {
        IArchimateElement process = element(F.createBusinessProcess(), "Pay");
        IArchimateElement service = element(F.createApplicationService(), "Payments");
        IArchimateElement goal = element(F.createGoal(), "Grow");
        IArchimateElement data = element(F.createBusinessObject(), "Order");
        service.setDocumentation("Description: Moves " + A + "M" + A + " money.\nTechnically: REST");
        relate(F.createServingRelationship(), service, process, "S");
        relate(F.createInfluenceRelationship(), process, goal, "I");
        relate(F.createFlowRelationship(), data, process, "F");
        relate(F.createAggregationRelationship(), process, data, "G").setDocumentation("<tbl>\nA | B |\n1 | 2 |\n</tbl>");
        relate(F.createAssociationRelationship(), service, data, A + "M" + A);
        process.setDocumentation("`S`\n`I`\n`F`\n`G`");

        Entry pay = entry(read(), "Pay");

        assertThat(parts(pay.body)).containsExactly(
                "TEXT {SENTENCE use «Service»Payments |  | Moves [[ref:" + data.getId() + "]] money.}\n"
                        + "{SENTENCE influences «Goal»Grow |  | null}\n{SENTENCE flows from «Object»Order |  | null}",
                // a table in the description makes it a block of its own
                "SENTENCE aggregation «Object»Order | <tbl>\nA | B |\n1 | 2 |\n</tbl> | null");
        // incoming relationships said by a token stay listed, outgoing ones don't
        assertThat(pay.relations).extracting(r -> r.other.label).containsExactly("Payments", "Order");
    }

    @Test
    void relationshipNamesThatAreOnlyATokenAreNotPrinted() {
        IArchimateElement process = element(F.createBusinessProcess(), "Pay");
        IArchimateElement next = element(F.createBusinessProcess(), "Sign");
        IArchimateElement other = element(F.createBusinessProcess(), "Archive");
        relate(F.createTriggeringRelationship(), process, next, " " + A + A + "TRIG1" + A + A + " ");
        relate(F.createFlowRelationship(), process, other, A + "ACC" + A);
        relate(F.createFlowRelationship(), next, other, "nightly");

        Doc doc = read();

        assertThat(entry(doc, "Pay").relations).extracting(r -> r.name).containsExactly("", "");
        assertThat(entry(doc, "Sign").relations).extracting(r -> r.name).containsExactly("nightly", "");
    }

    @Test
    void businessDescriptionNeedsDescriptionAndTechnically() {
        IArchimateElement process = element(F.createBusinessProcess(), "Pay");
        IArchimateElement plain = element(F.createApplicationService(), "Plain");
        plain.setDocumentation("Just text, no structure");
        relate(F.createServingRelationship(), plain, process, "");

        assertThat(entry(read(), "Pay").relations).singleElement().satisfies(r -> assertThat(r.note).isNull());
    }

    @Test
    void compositionOfTheSameTypeIsDescribedInPlaceWithItsRolesAndAccesses() {
        IArchimateElement whole = element(F.createApplicationFunction(), "Whole");
        IArchimateElement part = element(F.createApplicationFunction(), "Part");
        IArchimateElement data = element(F.createDataObject(), "Data");
        IArchimateElement table = element(F.createDataObject(), "[table ORDERS]");
        IArchimateElement role = element(F.createBusinessRole(), "[auth] OPE");
        relate(F.createCompositionRelationship(), whole, part, A + A + "P" + A + A);
        relate(F.createCompositionRelationship(), part, whole, A + A + "BACK" + A + A);
        relate(F.createCompositionRelationship(), whole, data, A + A + "D" + A + A);
        relate(F.createAssociationRelationship(), part, role, "");
        relate(F.createAccessRelationship(), part, table, "").setAccessType(IAccessRelationship.READ_ACCESS);
        whole.setDocumentation("Steps:\n" + A + A + "P" + A + A + "\nUses " + A + A + "D" + A + A + ".");
        part.setDocumentation("Does a step, then " + A + A + "BACK" + A + A);

        Entry entry = entry(read(), "Whole");

        assertThat(parts(entry.body)).containsExactly("TEXT Steps:",
                "NESTED «Function»Part [TEXT Does a step, then {RECURSIVE Whole}, "
                        + "ROLE Available only for role OPE and higher., SENTENCE read from [table ORDERS] |  | null]",
                "TEXT Uses {OBJECT «DataObject»Data}.");
        assertThat(entry.documentation).endsWith("Uses [[ref:" + data.getId() + "]].");
        assertThat(entry.relations).extracting(r -> r.other.label).containsExactly("Part"); // BACK comes in
    }

    @Test
    void accessBuildsHafsSql() {
        IArchimateElement function = element(F.createApplicationFunction(), "Load");
        IArchimateElement table = element(F.createDataObject(), "[table ORDERS]");
        IArchimateElement query = element(F.createDataObject(), "[select] open orders");
        query.setDocumentation("select * from ORDERS where STATE = 'OPEN'");
        IAccessRelationship select = relate(F.createAccessRelationship(), function, table, "S");
        profile("select", select);
        select.setDocumentation("<sellist>ID,\r\ncoalesce(a, b)<sellist>\r\nwhere STATE = 'NEW'");
        IAccessRelationship update = relate(F.createAccessRelationship(), function, table, "U");
        profile("update", update);
        update.setDocumentation("<updList>set STATE = 'DONE'</UPDLIST>\nwhere ID = {id}");
        IAccessRelationship named = relate(F.createAccessRelationship(), function, query, "Q");
        named.getProfiles().add(select.getPrimaryProfile());
        named.setDocumentation("for the screen");
        IAccessRelationship insert = relate(F.createAccessRelationship(), function, table, "I");
        profile("insert", insert);
        insert.setDocumentation("one row");
        relate(F.createAccessRelationship(), function, table, "W").setAccessType(IAccessRelationship.UNSPECIFIED_ACCESS);
        function.setDocumentation("`S`\n`U`\n`Q`\n`I`\n`W`");

        Doc doc = read();
        Entry load = entry(doc, "Load");

        assertThat(parts(load.body)).containsExactly(
                "TEXT <sql>select\nID,\ncoalesce(a, b)\nfrom [table ORDERS]\nwhere STATE = 'NEW'</sql>",
                "TEXT <sql>update [table ORDERS]\nset STATE = 'DONE'\nwhere ID = {id}</sql>",
                "TEXT <sql>select * from ORDERS where STATE = 'OPEN'</sql>\nfor the screen",
                // an access's object without its type: its name says it
                "TEXT {SENTENCE insert into [table ORDERS] | one row | null}\n{SENTENCE write into [table ORDERS] |  | null}");
        assertThat(load.relations).isEmpty();
        List<Relation> incoming = entry(doc, "[table ORDERS]").relations;
        assertThat(incoming).extracting(r -> r.verb).containsExactly("is read by", "is written by", "is written by",
                "is written by");
        assertThat(incoming.get(0).documentation).startsWith("<sql>select\nID,");
    }

    @Test
    void parametersAreASectionWhereTheTokenIsOrAfterTheDescription() {
        IArchimateElement function = element(F.createApplicationFunction(), "Compute");
        IArchimateElement api = element(F.createApplicationInterface(), "[api] Prices");
        IArchimateElement in = element(F.createDataObject(), "[parameters] in");
        in.setDocumentation("<tbl>\nname | type |\nid | number |\n</tbl>");
        IArchimateElement out = element(F.createDataObject(), "[parameters] out");
        out.setDocumentation("- price");
        IArchimateElement other = element(F.createDataObject(), "[parameters] audit");
        relate(F.createAccessRelationship(), function, in, "IN").setAccessType(IAccessRelationship.READ_ACCESS);
        relate(F.createAccessRelationship(), function, out, "").setAccessType(IAccessRelationship.WRITE_ACCESS);
        profile("log", relate(F.createAccessRelationship(), function, other, ""));
        relate(F.createAccessRelationship(), api, in, "").setAccessType(IAccessRelationship.READ_ACCESS);
        relate(F.createAccessRelationship(), api, out, "").setAccessType(IAccessRelationship.WRITE_ACCESS);
        function.setDocumentation("Takes:\n`IN`");

        Doc doc = read();

        Entry compute = entry(doc, "Compute");
        assertThat(parts(compute.body)).containsExactly("TEXT Takes:",
                "SECTION Input parameters: <tbl>\nname | type |\nid | number |\n</tbl>");
        assertThat(compute.sections).extracting(s -> s.title + ": " + s.text)
                .containsExactly("Output: - price", "[parameters] audit: ");
        assertThat(entry(doc, "[api] Prices").sections).extracting(s -> s.title).containsExactly("Request", "Response");
        assertThat(compute.relations).isEmpty();
    }

    @Test
    void everyAuthRoleIsALineAndATokenPrintsItInPlaceOnce() {
        IArchimateElement screen = element(F.createApplicationComponent(), "Screen");
        IArchimateElement view = element(F.createBusinessRole(), "[AUTH] VIEW");
        IArchimateElement auditor = element(F.createBusinessRole(), "[auth] AUDITOR");
        IArchimateElement admin = element(F.createBusinessRole(), "[auth] ADMIN");
        relate(F.createAssociationRelationship(), screen, view, "");
        relate(F.createAssociationRelationship(), auditor, screen, "");
        relate(F.createAssociationRelationship(), screen, admin, A + "adm" + A);
        screen.setDocumentation("Admins: " + A + "adm" + A);

        Entry entry = entry(read(), "Screen");

        assertThat(parts(entry.body)).containsExactly("TEXT Admins: {ROLE Available only for role ADMIN.}");
        assertThat(entry.factRoles).containsExactly("VIEW", "AUDITOR");
        assertThat(entry.facts).containsExactly("Available only for role VIEW and higher.",
                "Available only for role AUDITOR.");
        assertThat(entry.relations).isEmpty();
    }

    @Test
    void relationshipsReadAsVerbsWithTheOtherEndsBusinessDescription() {
        IArchimateElement component = element(F.createApplicationComponent(), "Core");
        IArchimateElement service = element(F.createApplicationService(), "Quotes");
        IArchimateElement iface = element(F.createApplicationInterface(), "REST");
        IArchimateElement event = element(F.createApplicationEvent(), "Quoted");
        IArchimateElement process = element(F.createBusinessProcess(), "Quote");
        IArchimateElement next = element(F.createBusinessProcess(), "Sign");
        service.setDocumentation("Description: Gives prices.\nTechnically:\nREST call");
        next.setDocumentation("description:\r\nSigns the contract.\r\nTECHNICALLY\r\nby hand");
        relate(F.createAssignmentRelationship(), component, service, "");
        relate(F.createAssignmentRelationship(), component, iface, "");
        relate(F.createAssignmentRelationship(), component, event, "");
        relate(F.createAssignmentRelationship(), component, process, "");
        relate(F.createAssignmentRelationship(), iface, service, "");
        relate(F.createServingRelationship(), service, process, "");
        relate(F.createTriggeringRelationship(), process, next, "");
        relate(F.createCompositionRelationship(), process, next, "").setDocumentation("Said here");

        Doc doc = read();

        assertThat(entry(doc, "Core").relations).extracting(r -> r.verb)
                .containsExactly("provides", "publishes", "generates", "implements");
        assertThat(entry(doc, "Quotes").relations).extracting(r -> r.verb)
                .containsExactly("is used by", "is provided by", "is published on"); // HaF's wording
        Entry quote = entry(doc, "Quote");
        assertThat(quote.relations).extracting(r -> r.verb + "|" + r.note).containsExactly(
                "triggers|Signs the contract.", "composed of|null", "is implemented by|null", "use|Gives prices.");
    }

    @Test
    void confluenceAndJiraLinks() {
        IArchimateElement process = element(F.createBusinessProcess(), "Sjednání smlouvy");
        property(process, "confluenceUrl", "https://wiki.example.com/display/ARCH/Orch+Specs-v2");
        IArchimateElement fixed = element(F.createApplicationComponent(), "Fixed");
        property(fixed, "confluenceUrl", "https://wiki.example.com/display/ARCH/Page#somewhere");
        IArchimateElement short1 = element(F.createApplicationComponent(), "Short");
        property(short1, "confluenceUrl", "https://wiki.example.com/pages/12345");
        IArchimateElement script = element(F.createApplicationComponent(), "Script");
        property(script, "confluenceUrl", "javascript:alert(1)");
        IArchimateElement ticket = element(F.createWorkPackage(), "RBWDP-20025");
        IArchimateElement notTicket = element(F.createWorkPackage(), "RBWDP-20025 portal");

        Doc doc = read(KeywordRules.parseFile("jira https://jira.example.com/browse/"), List.of(view));

        assertThat(doc.external).containsEntry(process.getId(), "https://wiki.example.com/display/ARCH/Orch+Specs-v2"
                + "#id-OrchSpecsv2-%C2%ABProcess%C2%BBSjedn%C3%A1n%C3%ADsmlouvy")
                .containsEntry(fixed.getId(), "https://wiki.example.com/display/ARCH/Page#somewhere")
                .containsEntry(short1.getId(), "https://wiki.example.com/pages/12345")
                .containsEntry(ticket.getId(), "https://jira.example.com/browse/RBWDP-20025")
                .doesNotContainKey(notTicket.getId()).doesNotContainKey(script.getId());
        assertThat(doc.notes).hasSize(1).allMatch(n -> n.contains("Short"));
        assertThat(entry(doc, "RBWDP-20025").headingUrl).isEqualTo("https://jira.example.com/browse/RBWDP-20025");
        assertThat(entry(doc, "RBWDP-20025 portal").headingUrl).isNull();
        // the Confluence page is the heading's link, not a property row
        Entry described = entry(doc, "Sjednání smlouvy");
        assertThat(described.headingUrl).isEqualTo(doc.external.get(process.getId()));
        assertThat(described.properties).isEmpty();
        assertThat(entry(doc, "Script").headingUrl).isNull();
    }

    @Test
    void withoutJiraRuleTicketsAreNotLinked() {
        IArchimateElement ticket = element(F.createWorkPackage(), "RBWDP-1");
        assertThat(read().external).doesNotContainKey(ticket.getId());
    }

    @Test
    void workPackageOnOnePageHoldsItsDescriptionAndEachElement() {
        IWorkPackage wp = element(F.createWorkPackage(), "RBWDP-7");
        wp.setDocumentation("The spec");
        IArchimateElement b = element(F.createBusinessProcess(), "B");
        IArchimateElement a = element(F.createBusinessProcess(), "A");
        relate(F.createAssociationRelationship(), wp, a, "");
        relate(F.createAssociationRelationship(), b, wp, "");

        Doc doc = read(KeywordRules.parseFile("workpackage one-page"), List.of(wp));

        assertThat(doc.pages).singleElement().satisfies(p -> {
            assertThat(p.title).isEqualTo("RBWDP-7");
            assertThat(p.documentation).isEqualTo("The spec");
            assertThat(p.entries).extracting(e -> e.name).containsExactly("A", "B");
        });
        assertThat(read(KeywordRules.defaults(), List.of(wp)).pages).hasSizeGreaterThan(1);
    }

    private static void property(IArchimateElement element, String key, String value) {
        IProperty p = F.createProperty();
        p.setKey(key);
        p.setValue(value);
        element.getProperties().add(p);
    }
}
