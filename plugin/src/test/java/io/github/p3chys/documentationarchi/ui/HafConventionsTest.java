package io.github.p3chys.documentationarchi.ui;

import static org.assertj.core.api.Assertions.assertThat;

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

import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.doc.Relation;
import io.github.p3chys.documentationarchi.text.KeywordRules;

/** The model conventions of HaF, the documentation tool documentationArchi replaces. */
class HafConventionsTest {

    private static final IArchimateFactory F = IArchimateFactory.eINSTANCE;

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
        return read(KeywordRules.defaults());
    }

    private Doc read(KeywordRules rules) {
        ModelReader.Selection selection = ModelReader.Selection.of(List.of(view));
        return ModelReader.read(selection, (v, page) -> { }, new java.util.ArrayList<>(), rules,
                selection.exclusion(rules));
    }

    private static Entry entry(Doc doc, String name) {
        return doc.pages.stream().flatMap(p -> p.entries.stream()).filter(e -> e.name.equals(name)).findFirst()
                .orElseThrow();
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
    void tokenIsWrittenAsTheOtherElementAndNotListedAgain() {
        IArchimateElement process = element(F.createBusinessProcess(), "Pay");
        IArchimateElement service = element(F.createApplicationService(), "Payments");
        IArchimateElement event = element(F.createBusinessEvent(), "Paid");
        relate(F.createServingRelationship(), service, process, "PAYSRV");
        relate(F.createTriggeringRelationship(), process, event, "DONE");
        process.setDocumentation("Calls ``PAYSRV`` and then `DONE`, not `NOPE`.");

        Entry pay = entry(read(), "Pay");

        assertThat(pay.documentation).isEqualTo("Calls [[ref:" + service.getId() + "]] and then [[ref:"
                + event.getId() + "]], not `NOPE`.");
        // the triggering goes out and is said; the serving comes in and stays listed
        assertThat(pay.relations).extracting(r -> r.other.label).containsExactly("Payments");
    }

    @Test
    void compositionTokenEmbedsAPartOfTheSameTypeOnceAndLinksOthers() {
        IArchimateElement whole = element(F.createApplicationFunction(), "Whole");
        IArchimateElement part = element(F.createApplicationFunction(), "Part");
        IArchimateElement data = element(F.createDataObject(), "Data");
        relate(F.createCompositionRelationship(), whole, part, "P");
        relate(F.createCompositionRelationship(), part, whole, "BACK"); // a loop
        relate(F.createCompositionRelationship(), whole, data, "D");
        whole.setDocumentation("Steps:\n`P`\nUses `D`.");
        part.setDocumentation("Does a step, then `BACK`.");

        Entry entry = entry(read(), "Whole");

        assertThat(entry.documentation).isEqualTo("Steps:\n**[[ref:" + part.getId() + "]]**\nDoes a step, then [[ref:"
                + whole.getId() + "]].\nUses [[ref:" + data.getId() + "]].");
        assertThat(entry.relations).extracting(r -> r.other.label).containsExactly("Part"); // BACK comes in
    }

    @Test
    void accessReadsAsWhatItDoesAndBuildsItsSql() {
        IArchimateElement function = element(F.createApplicationFunction(), "Load");
        IArchimateElement table = element(F.createDataObject(), "[table] ORDERS");
        IArchimateElement query = element(F.createDataObject(), "[select] open orders");
        query.setDocumentation("select * from ORDERS where STATE = 'OPEN'");
        IAccessRelationship select = relate(F.createAccessRelationship(), function, table, "");
        profile("select", select);
        select.setDocumentation("Reads them.\n<sellist>ID, STATE</sellist>");
        IAccessRelationship update = relate(F.createAccessRelationship(), function, table, "");
        profile("update", update);
        update.setDocumentation("<updList>STATE = 'DONE'\nTIME = now</updList>");
        IAccessRelationship read = relate(F.createAccessRelationship(), function, query, "");
        read.setAccessType(IAccessRelationship.READ_ACCESS);

        Doc doc = read();
        List<Relation> relations = entry(doc, "Load").relations;

        assertThat(relations).extracting(r -> r.verb).containsExactly("read from", "write into", "read from");
        assertThat(relations.get(0).documentation)
                .isEqualTo("Reads them.\n<sql>SELECT ID,\n       STATE\nFROM ORDERS</sql>");
        assertThat(relations.get(1).documentation).isEqualTo("<sql>UPDATE ORDERS\nSET STATE = 'DONE',\n    TIME = now</sql>");
        assertThat(relations.get(2).documentation).isEqualTo("<sql>select * from ORDERS where STATE = 'OPEN'</sql>");
        assertThat(entry(doc, "[table] ORDERS").relations).extracting(r -> r.verb).containsExactly("is read by", "is written by");
    }

    @Test
    void parametersBecomeSectionsAndAreNeverRelated() {
        IArchimateElement function = element(F.createApplicationFunction(), "Compute");
        IArchimateElement api = element(F.createApplicationInterface(), "[api] Prices");
        IArchimateElement in = element(F.createDataObject(), "[parameters] in");
        in.setDocumentation("- id");
        IArchimateElement out = element(F.createDataObject(), "[parameters] out");
        out.setDocumentation("- price");
        for (IArchimateElement source : List.of(function, api)) {
            relate(F.createAccessRelationship(), source, in, "").setAccessType(IAccessRelationship.READ_ACCESS);
            relate(F.createAccessRelationship(), source, out, "").setAccessType(IAccessRelationship.WRITE_ACCESS);
        }

        Doc doc = read();

        assertThat(entry(doc, "Compute").sections).extracting(s -> s.title + ": " + s.text)
                .containsExactly("Input parameters: - id", "Output: - price");
        assertThat(entry(doc, "[api] Prices").sections).extracting(s -> s.title)
                .containsExactly("Request", "Response");
        assertThat(entry(doc, "Compute").relations).isEmpty();
        assertThat(entry(doc, "[parameters] in").relations).hasSize(2); // its own card still says who reads it
    }

    @Test
    void authRoleSaysWhoMayUseItOnce() {
        IArchimateElement screen = element(F.createApplicationComponent(), "Screen");
        IArchimateElement admin = element(F.createApplicationComponent(), "Admin screen");
        IArchimateElement ope = element(F.createBusinessRole(), "[AUTH] ope");
        IArchimateElement dev = element(F.createBusinessRole(), "[auth] DEV");
        IArchimateElement adminRole = element(F.createBusinessRole(), "[auth] ADMIN");
        relate(F.createAssociationRelationship(), screen, ope, "");
        relate(F.createAssociationRelationship(), dev, screen, "");
        relate(F.createAssociationRelationship(), admin, adminRole, "");

        Doc doc = read();

        assertThat(entry(doc, "Screen").facts).containsExactly("Available only for role DEV and higher");
        assertThat(entry(doc, "Screen").relations).isEmpty();
        assertThat(entry(doc, "Admin screen").facts).containsExactly("Available only for role ADMIN");
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
        next.setDocumentation("Signs the contract.");
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
                .containsExactly("is used by", "is provided by", "is published by");
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
        IArchimateElement ticket = element(F.createWorkPackage(), "RBWDP-20025");
        IArchimateElement notTicket = element(F.createWorkPackage(), "RBWDP-20025 portal");

        Doc doc = read(KeywordRules.parseFile("jira https://jira.example.com/browse/"));

        assertThat(doc.external).containsEntry(process.getId(), "https://wiki.example.com/display/ARCH/Orch+Specs-v2"
                + "#id-OrchSpecsv2-%C2%ABProcess%C2%BBSjedn%C3%A1n%C3%ADsmlouvy")
                .containsEntry(fixed.getId(), "https://wiki.example.com/display/ARCH/Page#somewhere")
                .containsEntry(short1.getId(), "https://wiki.example.com/pages/12345")
                .containsEntry(ticket.getId(), "https://jira.example.com/browse/RBWDP-20025")
                .doesNotContainKey(notTicket.getId());
        assertThat(doc.notes).hasSize(1).allMatch(n -> n.contains("Short"));
        assertThat(entry(doc, "RBWDP-20025").headingUrl).isEqualTo("https://jira.example.com/browse/RBWDP-20025");
        assertThat(entry(doc, "RBWDP-20025 portal").headingUrl).isNull();
    }

    @Test
    void withoutJiraRuleTicketsAreNotLinked() {
        IArchimateElement ticket = element(F.createWorkPackage(), "RBWDP-1");
        assertThat(read().external).doesNotContainKey(ticket.getId());
    }

    private static void property(IArchimateElement element, String key, String value) {
        IProperty p = F.createProperty();
        p.setKey(key);
        p.setValue(value);
        element.getProperties().add(p);
    }
}
