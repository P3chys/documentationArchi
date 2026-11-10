package io.github.p3chys.documentationarchi;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.p3chys.documentationarchi.doc.Assets;
import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.doc.Entry;
import io.github.p3chys.documentationarchi.doc.Link;
import io.github.p3chys.documentationarchi.doc.Relation;
import io.github.p3chys.documentationarchi.render.Preview;
import io.github.p3chys.documentationarchi.text.KeywordRules;

class PreviewTest {

    @TempDir
    Path model;

    private Doc doc;
    private DocPage view;
    private final Set<Assets.Asset> used = new HashSet<>();

    @BeforeEach
    void doc() throws Exception {
        Path assets = Files.createDirectories(model.resolve("Assets"));
        Files.write(assets.resolve("flow.png"), new byte[] { 1 });
        Files.write(assets.resolve("SLA.pdf"), new byte[] { 2 });
        Entry client = new Entry("e1", "Client", "Business Actor",
                "WARNING: #risk\n\n[[asset:SLA.pdf|the SLA]] [[asset:gone.png]] [[ref:e2]]", List.of(),
                List.of(new Relation(true, "Serving Relationship", "", "", new Link("e2", "Portal", "Application Component"), false)),
                List.of());
        Entry portal = new Entry("e2", "Portal", "Application Component", "", List.of(), List.of(), List.of());
        view = new DocPage("v1", "Sales");
        view.image = new byte[] { 1, 2, 3 };
        view.imageWidth = 300;
        view.entries.add(client);
        view.groups.put("e1", "Front office");
        view.contents.add(new Link("e1", "Client", "Business Actor"));
        DocPage other = new DocPage("v2", "Back office");
        other.entries.add(portal);
        doc = new Doc("Model", List.of(view, other), Assets.in(model));
    }

    @Test
    void pageIsTheExportedPageWithLinksThePreviewFollowsItself() {
        String html = Preview.page(doc, view, KeywordRules.defaults(), "picture-1.png", used);

        assertThat(html).startsWith("<!DOCTYPE html>").contains("<meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\"/>")
                .contains("<h1>Sales</h1>").contains("src=\"picture-1.png\"")
                .contains("<a href=\"#ref-e1\">Client</a>") // the contents, to the description on the same page
                .contains("<a href=\"#ref-e2\">Portal</a>") // to the other page
                .contains("class=\"panel warning\"").contains("class=\"tag red\">RISK</span>")
                .contains("<h3>Front office</h3>")
                .contains("href=\"" + model.resolve("Assets/SLA.pdf").toUri() + "\">the SLA</a>")
                .contains("[missing asset: gone.png]")
                .contains("img.diagram"); // the export's style sheet
        assertThat(used).extracting(a -> a.name).containsExactly("SLA.pdf");
    }

    @Test
    void elementIsItsDescriptionNamingThePageItIsOn() {
        String html = Preview.elements(doc, List.of("e2"), KeywordRules.defaults(), used);

        assertThat(html).contains("<title>Portal</title>")
                .contains("Described on <a href=\"#ref-v2\">Back office</a>")
                .contains("<h3 id=\"e-e2\">").doesNotContain("Client");
    }

    @Test
    void relationshipShowsBothEnds() {
        assertThat(Preview.elements(doc, List.of("e1", "e2"), KeywordRules.defaults(), used))
                .contains("<h3 id=\"e-e1\">").contains("<h3 id=\"e-e2\">").contains("<h3>Front office</h3>");
    }

    @Test
    void nothingForWhatNoPageDescribes() {
        assertThat(Preview.elements(doc, List.of("v1", "nothere"), KeywordRules.defaults(), used)).isNull();
        assertThat(Preview.message("Select <something>")).contains("Select &lt;something&gt;");
    }

    @Test
    void linksOfThePreviewLeadToIds() {
        assertThat(Preview.target("file:///C:/tmp/preview-3.html#ref-id-123")).isEqualTo("id-123");
        assertThat(Preview.target("about:blank#ref-e1")).isEqualTo("e1");
        assertThat(Preview.target("https://example.com/x#top")).isNull();
        assertThat(Preview.target("about:blank#ref-")).isNull();
        assertThat(Preview.target(null)).isNull();
    }
}
