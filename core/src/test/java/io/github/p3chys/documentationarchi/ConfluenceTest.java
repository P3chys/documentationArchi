package io.github.p3chys.documentationarchi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.github.p3chys.documentationarchi.confluence.Confluence;
import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.render.Exporter;
import io.github.p3chys.documentationarchi.render.Exporter.Format;
import io.github.p3chys.documentationarchi.text.KeywordRules;

/** The Confluence client against a small fake of Confluence's REST API. */
class ConfluenceTest {

    /** A page of the fake: what the client last sent for it. */
    static final class FakePage {
        final String id;
        String title;
        String parent;
        int version = 1;
        String body = "";
        final List<String> attachments = new ArrayList<>();

        FakePage(String id, String title, String parent) {
            this.id = id;
            this.title = title;
            this.parent = parent;
        }
    }

    private HttpServer server;
    private final Map<String, FakePage> pages = new LinkedHashMap<>();
    private final List<String> auth = new ArrayList<>();
    private String url;

    @BeforeEach
    void start() throws IOException {
        pages.put("10", new FakePage("10", "Architecture", null));
        pages.put("11", new FakePage("11", "Services", "10"));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rest/api", this::handle);
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange x) throws IOException {
        auth.add(x.getRequestHeaders().getFirst("Authorization"));
        String path = x.getRequestURI().getPath().substring("/rest/api".length());
        Map<String, String> query = query(x.getRequestURI().getRawQuery());
        String method = x.getRequestMethod();
        String body = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if ("Bearer wrong".equals(auth.get(auth.size() - 1))) {
            reply(x, 401, "");
            return;
        }
        Matcher m;
        if (path.equals("/user/current")) {
            reply(x, 200, "{\"username\": \"adam\", \"displayName\": \"Adam P\"}");
        } else if (path.equals("/space") && "0".equals(query.get("start"))) { // two listing pages
            reply(x, 200, "{\"results\": [{\"key\": \"ARCH\", \"name\": \"Architecture\"}], \"_links\": {\"next\": \"/x\"}}");
        } else if (path.equals("/space")) {
            reply(x, 200, "{\"results\": [{\"key\": \"DEV\", \"name\": \"Developers\"}], \"_links\": {}}");
        } else if (path.equals("/space/~adam")) {
            reply(x, 200, "{\"key\": \"~adam\", \"name\": \"Adam P\"}");
        } else if (path.equals("/space/ARCH/content/page")) {
            reply(x, 200, list(p -> p.parent == null));
        } else if ((m = Pattern.compile("/content/(\\d+)/child/page").matcher(path)).matches()) {
            String id = m.group(1);
            reply(x, 200, list(p -> id.equals(p.parent)));
        } else if (path.equals("/content") && method.equals("GET")) {
            reply(x, 200, list(p -> p.title.equals(query.get("title"))));
        } else if (path.equals("/content") && method.equals("POST")) {
            FakePage page = new FakePage(Integer.toString(100 + pages.size()), field(body, "title"), ancestor(body));
            pages.put(page.id, page);
            reply(x, 200, json(page));
        } else if ((m = Pattern.compile("/content/(\\d+)").matcher(path)).matches() && method.equals("GET")) {
            reply(x, 200, json(pages.get(m.group(1))));
        } else if ((m = Pattern.compile("/content/(\\d+)").matcher(path)).matches() && method.equals("PUT")) {
            FakePage page = pages.get(m.group(1));
            page.title = field(body, "title");
            page.parent = ancestor(body);
            page.version = Integer.parseInt(body.replaceAll("(?s).*\"version\": \\{\"number\": (\\d+).*", "$1"));
            page.body = body.replaceAll("(?s).*\"storage\": \\{\"value\": \"(.*?)\", \"representation.*", "$1");
            reply(x, 200, json(page));
        } else if ((m = Pattern.compile("/content/(\\d+)/child/attachment(/.*)?").matcher(path)).matches()) {
            FakePage page = pages.get(m.group(1));
            if (method.equals("GET")) {
                reply(x, 200, page.attachments.contains(query.get("filename"))
                        ? "{\"results\": [{\"id\": \"att1\"}]}" : "{\"results\": []}");
            } else {
                String name = body.replaceAll("(?s).*filename=\"([^\"]+)\".*", "$1");
                page.attachments.add(name + (m.group(2) == null ? "" : " (new version)"));
                reply(x, 200, "{\"results\": []}");
            }
        } else {
            reply(x, 404, "{\"message\": \"No such thing\"}");
        }
    }

    private String list(java.util.function.Predicate<FakePage> which) {
        List<String> found = new ArrayList<>();
        pages.values().stream().filter(which).forEach(p -> found.add(json(p)));
        return "{\"results\": [" + String.join(", ", found) + "], \"_links\": {}}";
    }

    /** As Confluence: its ancestors from the top of the space down to its parent. */
    private String json(FakePage p) {
        List<String> ancestors = new ArrayList<>();
        for (FakePage a = pages.get(p.parent); a != null; a = pages.get(a.parent)) {
            ancestors.add(0, "{\"id\": \"" + a.id + "\", \"title\": \"" + a.title + "\"}");
        }
        return "{\"id\": \"" + p.id + "\", \"title\": \"" + p.title + "\", \"version\": {\"number\": " + p.version
                + "}, \"ancestors\": [" + String.join(", ", ancestors) + "]}";
    }

    private static String field(String body, String key) {
        return body.replaceAll("(?s).*?\"" + key + "\": \"([^\"]*)\".*", "$1");
    }

    private static String ancestor(String body) {
        return body.contains("\"ancestors\"") ? body.replaceAll("(?s).*\"ancestors\": \\[\\{\"id\": \"(\\d+)\".*", "$1") : null;
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> query = new LinkedHashMap<>();
        if (raw != null) {
            for (String pair : raw.split("&")) {
                String[] kv = pair.split("=", 2);
                query.put(kv[0], kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
            }
        }
        return query;
    }

    private static void reply(HttpExchange x, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().add("Content-Type", "application/json");
        x.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            x.getResponseBody().write(bytes);
        }
        x.close();
    }

    @Test
    void listsTheSpacesPageByPageAndTheOwnPersonalSpace() throws Exception {
        List<Confluence.Space> spaces = new Confluence(url, "adam", "secret").spaces();

        assertThat(spaces).extracting(s -> s.key).containsExactly("ARCH", "DEV", "~adam");
        assertThat(auth.get(0)).isEqualTo("Basic " + java.util.Base64.getEncoder()
                .encodeToString("adam:secret".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void walksThePageTree() throws Exception {
        Confluence confluence = new Confluence(url, "adam", "secret");

        assertThat(confluence.rootPages("ARCH")).extracting(p -> p.title).containsExactly("Architecture");
        assertThat(confluence.children("10")).extracting(p -> p.id + " " + p.title).containsExactly("11 Services");
        assertThat(confluence.children("11")).isEmpty();
    }

    @Test
    void aTokenWithoutUserNameIsSentAsBearer() throws Exception {
        assertThat(new Confluence(url, "", "token").currentUser()).isEqualTo("Adam P");
        assertThat(auth).containsExactly("Bearer token");

        assertThatThrownBy(() -> new Confluence(url, " ", "wrong").currentUser())
                .hasMessageContaining("did not accept the user name and password");
    }

    @Test
    void refusesWhatIsNoAddressOrHasNoPassword() {
        assertThatThrownBy(() -> new Confluence("confluence", "a", "b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Confluence(url, "a", "")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void uploadsAnExportUnderTheChosenPageAndUpdatesItTheNextTime(@TempDir Path dir) throws Exception {
        DocPage overview = new DocPage("m", "Model");
        overview.documentation = "About \"it\"";
        DocPage view = new DocPage("v", "[microservice] Hello");
        view.image = io.github.p3chys.documentationarchi.doc.Picture.of(new byte[] { 1, 2, 3 });
        Doc doc = new Doc("Model", List.of(overview, view));
        new Exporter(doc, KeywordRules.parse("")).export(dir, java.util.Set.of(Format.CONFLUENCE));
        Confluence confluence = new Confluence(url, "adam", "secret");
        List<String> uploaded = new ArrayList<>();

        String top = confluence.upload(dir.resolve("confluence"), "ARCH", "11", new Confluence.Progress() {
            @Override
            public void page(String title) {
                uploaded.add(title);
            }
        });

        FakePage model = pages.get(top);
        assertThat(model.title).isEqualTo("Model");
        assertThat(model.parent).isEqualTo("11");
        assertThat(model.version).isEqualTo(2);
        assertThat(model.body).contains("About &quot;it&quot;");
        FakePage hello = pages.values().stream().filter(p -> p.title.equals("[microservice] Hello")).findFirst().get();
        assertThat(hello.parent).isEqualTo(top);
        assertThat(hello.attachments).containsExactly("microservice-hello.png");
        assertThat(uploaded).containsExactly("Model", "[microservice] Hello");
        assertThat(confluence.pageUrl(top)).isEqualTo(url.replaceAll("/$", "") + "/pages/viewpage.action?pageId=" + top);

        int count = pages.size();
        confluence.upload(dir.resolve("confluence"), "ARCH", "11", NEVER_TAKE_OVER); // its own pages: nothing to ask

        assertThat(pages).hasSize(count);
        assertThat(model.version).isEqualTo(3);
        assertThat(hello.attachments).containsExactly("microservice-hello.png", "microservice-hello.png (new version)");
    }

    /** Fails the test if asked: no page should be taken over. */
    private static final Confluence.Progress NEVER_TAKE_OVER = new Confluence.Progress() {
        @Override
        public boolean takeOver(List<String> pages) {
            throw new AssertionError("asked to take over " + pages);
        }
    };

    private Path export(Path dir) throws IOException {
        Doc doc = new Doc("Model", List.of(new DocPage("m", "Model"), new DocPage("v", "Services")));
        new Exporter(doc, KeywordRules.parse("")).export(dir, java.util.Set.of(Format.CONFLUENCE));
        return dir.resolve("confluence");
    }

    @Test
    void asksBeforeTakingOverAPageOfTheSameTitleElsewhereInTheSpace(@TempDir Path dir) throws Exception {
        pages.put("12", new FakePage("12", "Elsewhere", null));
        List<String> asked = new ArrayList<>();
        Confluence.Progress no = new Confluence.Progress() {
            @Override
            public boolean takeOver(List<String> found) {
                asked.addAll(found);
                return false;
            }
        };

        assertThatThrownBy(() -> new Confluence(url, "adam", "secret").upload(export(dir), "ARCH", "12", no))
                .isInstanceOf(java.util.concurrent.CancellationException.class).hasMessageContaining("Nothing was uploaded");

        assertThat(asked).containsExactly("Services (in Architecture)");
        assertThat(pages).hasSize(3);
        assertThat(pages.get("11").parent).isEqualTo("10");
    }

    @Test
    void theParentCannotBeAPageOfTheExportItself(@TempDir Path dir) throws Exception {
        Path export = export(dir);
        Confluence confluence = new Confluence(url, "adam", "secret");
        String top = confluence.upload(export, "ARCH", "10", Confluence.Progress.NONE); // takes Services over

        assertThat(pages.get("11").parent).isEqualTo(top);
        assertThatThrownBy(() -> confluence.upload(export, "ARCH", "11", NEVER_TAKE_OVER))
                .hasMessageContaining("export's own page");
        assertThatThrownBy(() -> confluence.upload(export, "ARCH", top, NEVER_TAKE_OVER))
                .hasMessageContaining("export's own page \"Model\"");
    }

    @Test
    void anErrorSaysWhatConfluenceSaid(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("manifest.json"), "{\"pages\": []}");

        assertThatThrownBy(() -> new Confluence(url, "adam", "secret").upload(dir, "ARCH", null, Confluence.Progress.NONE))
                .hasMessageContaining("no Confluence pages");
        assertThatThrownBy(() -> new Confluence(url, "adam", "secret").children("nope"))
                .hasMessageContaining("Not found in Confluence").hasMessageContaining("No such thing");
    }
}
