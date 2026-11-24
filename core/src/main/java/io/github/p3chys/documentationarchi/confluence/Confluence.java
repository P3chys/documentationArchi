package io.github.p3chys.documentationarchi.confluence;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;

/**
 * Confluence Data Center or Server, through its REST API: its spaces and their page tree, to choose where an export
 * goes, and the upload of an export's {@code confluence/} folder there. The upload does what the
 * {@code upload_to_confluence.py} of the export does: pages are matched by title in the space, existing ones get a new
 * version and the others are created, so the same export can be uploaded again.
 */
public final class Confluence {

    private static final int LIMIT = 100;
    private static final int MAX_ERROR = 300;

    /** A space: its key and its name. */
    public static final class Space {
        public final String key;
        public final String name;

        public Space(String key, String name) {
            this.key = key;
            this.name = name;
        }
    }

    /** A page: its id and its title. */
    public static final class Page {
        public final String id;
        public final String title;

        public Page(String id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    /** Told which page is being uploaded, and asked whether to stop. */
    public interface Progress {
        Progress NONE = new Progress() { };

        default void page(String title) {
        }

        default boolean cancelled() {
            return false;
        }

        /**
         * Whether to take over pages of the space that have the titles of exported pages but are elsewhere, not
         * under the chosen parent: they are updated with the export's content and moved under it.
         *
         * @param pages their titles and where they are now
         */
        default boolean takeOver(List<String> pages) {
            return true;
        }
    }

    /** A request Confluence refused or could not answer. */
    public static final class Failure extends IOException {
        private static final long serialVersionUID = 1L;
        public final int status;

        Failure(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private final String url;
    private final String auth;
    private final HttpClient http;

    /**
     * @param url the address of Confluence, such as https://confluence.example.com
     * @param user the user name; empty to use the password as a personal access token
     * @throws IllegalArgumentException if the address is not an http(s) one, or there is no password
     */
    public Confluence(String url, String user, String password) {
        String base = url == null ? "" : url.strip().replaceAll("/+$", "");
        base = base.endsWith("/rest/api") ? base.substring(0, base.length() - "/rest/api".length()) : base;
        if (!base.matches("(?i)https?://[^/\\s]+\\S*")) {
            throw new IllegalArgumentException("Give the address of Confluence, such as https://confluence.example.com");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Give the password (or a personal access token) for Confluence");
        }
        this.url = base;
        this.auth = user == null || user.isBlank() ? "Bearer " + password
                : "Basic " + Base64.getEncoder().encodeToString((user.strip() + ":" + password).getBytes(StandardCharsets.UTF_8));
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL).proxy(ProxySelector.getDefault()).build();
    }

    public String url() {
        return url;
    }

    /** The address of a page in the browser. */
    public String pageUrl(String id) {
        return url + "/pages/viewpage.action?pageId=" + id;
    }

    /**
     * Who Confluence takes the user for: the display name.
     *
     * @throws Failure if it does not accept the user name and password
     */
    public String currentUser() throws IOException {
        Object user = get("/user/current");
        if ("anonymous".equals(Json.string(user, "type"))) {
            throw new Failure(401, "Confluence did not accept the user name and password.");
        }
        String name = Json.string(user, "displayName");
        return name != null ? name : Json.string(user, "username");
    }

    /**
     * The spaces the user can see, in the order Confluence gives them, and the user's own personal space last (not
     * the personal spaces of everyone else, of which a company has thousands).
     */
    public List<Space> spaces() throws IOException {
        List<Space> spaces = new ArrayList<>();
        for (Object s : all("/space?type=global")) {
            spaces.add(new Space(Json.string(s, "key"), Json.string(s, "name")));
        }
        String user = Json.string(get("/user/current"), "username");
        if (user != null) {
            try {
                Object own = get("/space/" + encode("~" + user));
                spaces.add(new Space(Json.string(own, "key"), Json.string(own, "name")));
            } catch (Failure e) {
                if (e.status != 404 && e.status != 403) {
                    throw e;
                }
            }
        }
        return spaces;
    }

    /** The pages at the top of a space. */
    public List<Page> rootPages(String spaceKey) throws IOException {
        return pages(all("/space/" + encode(spaceKey) + "/content/page?depth=root"));
    }

    /** The pages right under a page. */
    public List<Page> children(String pageId) throws IOException {
        return pages(all("/content/" + encode(pageId) + "/child/page?"));
    }

    /**
     * Uploads an export's {@code confluence/} folder: its first page goes under the parent page, or at the top of the
     * space, the others under the pages the export puts them. Every page is made before any content goes in, so that
     * links between them resolve.
     * <p>
     * Pages are matched by title in the whole space. Before anything is changed, the progress is asked whether to take
     * over pages of those titles that are elsewhere in the space, not under the parent.
     *
     * @param parentId the page to put the export under, null for the top of the space
     * @return the id of the first page of the export
     * @throws CancellationException if the progress asked to stop, or not to take pages over; the pages uploaded so
     *         far stay
     * @throws IOException also if the parent is one of the export's own pages or under one
     */
    public String upload(Path folder, String spaceKey, String parentId, Progress progress) throws IOException {
        Object manifest = Json.parse(Files.readString(folder.resolve("manifest.json")));
        List<?> entries = Json.list(manifest, "pages");
        if (entries.isEmpty()) {
            throw new IOException("The export has no Confluence pages: " + folder.resolve("manifest.json"));
        }
        Map<String, Object> existing = new LinkedHashMap<>(); // by title
        for (Object entry : entries) {
            check(progress);
            String title = Json.string(entry, "title");
            List<?> found = Json.list(get("/content?type=page&spaceKey=" + encode(spaceKey) + "&title=" + encode(title)
                    + "&expand=version,ancestors"), "results");
            if (!found.isEmpty()) {
                existing.put(title, found.get(0));
            }
        }
        checkParent(parentId, existing);
        List<String> strangers = strangers(parentId, existing);
        if (!strangers.isEmpty() && !progress.takeOver(strangers)) {
            throw new CancellationException("Nothing was uploaded: pages with the titles of the export are elsewhere in "
                    + "the space");
        }
        Map<String, String> ids = new HashMap<>();
        Map<String, Integer> versions = new HashMap<>();
        for (Object entry : entries) {
            check(progress);
            String title = Json.string(entry, "title");
            String parent = Json.string(entry, "parent");
            Object page = existing.containsKey(title) ? existing.get(title)
                    : create(spaceKey, title, parent == null ? parentId : ids.get(parent));
            ids.put(title, Json.string(page, "id"));
            Object number = Json.at(page, "version", "number");
            versions.put(title, number instanceof Double ? ((Double) number).intValue() : 1);
        }
        for (Object entry : entries) {
            check(progress);
            String title = Json.string(entry, "title");
            progress.page(title);
            String id = ids.get(title);
            for (Object name : Json.list(entry, "attachments")) {
                attach(id, folder.resolve("attachments").resolve(name.toString()));
            }
            String parent = Json.string(entry, "parent");
            String body = Files.readString(folder.resolve(Json.string(entry, "file")));
            update(id, title, versions.get(title) + 1, body, parent == null ? parentId : ids.get(parent));
        }
        return ids.get(Json.string(entries.get(0), "title"));
    }

    private static void check(Progress progress) {
        if (progress.cancelled()) {
            throw new CancellationException("The upload was cancelled");
        }
    }

    /** A page cannot go under itself or its own child: the parent must not be one of the export's pages, or under one. */
    private void checkParent(String parentId, Map<String, Object> existing) throws IOException {
        if (parentId == null || existing.isEmpty()) {
            return;
        }
        List<String> above = new ArrayList<>(List.of(parentId));
        Json.list(get("/content/" + encode(parentId) + "?expand=ancestors"), "ancestors")
                .forEach(a -> above.add(Json.string(a, "id")));
        for (Map.Entry<String, Object> page : existing.entrySet()) {
            if (above.contains(Json.string(page.getValue(), "id"))) {
                throw new IOException("The chosen page is the export's own page \"" + page.getKey() + "\" or under it, "
                        + "and a page cannot go under itself. Choose a page outside the export.");
            }
        }
    }

    /**
     * The pages found by title that are not where the export puts its pages: not under the parent (or not at the top
     * of the space, without a parent), nor under another page of the export that is. "Title (in A / B)" each.
     */
    private static List<String> strangers(String parentId, Map<String, Object> existing) {
        Set<String> ours = new HashSet<>();
        for (boolean grew = true; grew; ) {
            grew = false;
            for (Object page : existing.values()) {
                List<String> ancestors = new ArrayList<>();
                Json.list(page, "ancestors").forEach(a -> ancestors.add(Json.string(a, "id")));
                boolean placed = parentId == null ? ancestors.isEmpty() : ancestors.contains(parentId);
                if ((placed || ancestors.stream().anyMatch(ours::contains)) && ours.add(Json.string(page, "id"))) {
                    grew = true;
                }
            }
        }
        List<String> strangers = new ArrayList<>();
        existing.forEach((title, page) -> {
            if (!ours.contains(Json.string(page, "id"))) {
                List<String> path = new ArrayList<>();
                Json.list(page, "ancestors").forEach(a -> path.add(Json.string(a, "title")));
                strangers.add(title + (path.isEmpty() ? " (at the top of the space)" : " (in " + String.join(" / ", path) + ")"));
            }
        });
        return strangers;
    }

    private Object create(String spaceKey, String title, String parentId) throws IOException {
        String data = "{\"type\": \"page\", \"title\": " + Json.quote(title) + ", \"space\": {\"key\": "
                + Json.quote(spaceKey) + "}, " + ancestors(parentId)
                + "\"body\": {\"storage\": {\"value\": \"\", \"representation\": \"storage\"}}}";
        return send("POST", "/content", data);
    }

    private void update(String id, String title, int version, String body, String parentId) throws IOException {
        send("PUT", "/content/" + encode(id), "{\"type\": \"page\", \"title\": " + Json.quote(title)
                + ", \"version\": {\"number\": " + version + "}, " + ancestors(parentId)
                + "\"body\": {\"storage\": {\"value\": " + Json.quote(body) + ", \"representation\": \"storage\"}}}");
    }

    private static String ancestors(String parentId) {
        return parentId == null ? "" : "\"ancestors\": [{\"id\": " + Json.quote(parentId) + "}], ";
    }

    /** Adds the file to the page, or a new version of it if the page has a file of that name. */
    private void attach(String pageId, Path file) throws IOException {
        String name = file.getFileName().toString();
        String path = "/content/" + encode(pageId) + "/child/attachment";
        List<?> existing = Json.list(get(path + "?filename=" + encode(name)), "results");
        String boundary = UUID.randomUUID().toString().replace("-", "");
        String type = URLConnection.guessContentTypeFromName(name);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + name.replace("\"", "%22") + "\"\r\nContent-Type: " + (type == null ? "application/octet-stream" : type)
                + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(Files.readAllBytes(file));
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = request(path + (existing.isEmpty() ? "" : "/" + encode(Json.string(existing.get(0), "id")) + "/data"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-Atlassian-Token", "nocheck")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        call(request);
    }

    private static List<Page> pages(List<Object> results) {
        List<Page> pages = new ArrayList<>();
        for (Object p : results) {
            pages.add(new Page(Json.string(p, "id"), Json.string(p, "title")));
        }
        return pages;
    }

    /** Every result of a listing, page by page. */
    private List<Object> all(String path) throws IOException {
        List<Object> all = new ArrayList<>();
        String separator = path.endsWith("?") ? "" : path.contains("?") ? "&" : "?";
        for (int start = 0;; ) {
            Object json = get(path + separator + "limit=" + LIMIT + "&start=" + start);
            List<?> results = Json.list(json, "results");
            all.addAll(results);
            start += results.size();
            if (results.isEmpty() || Json.at(json, "_links", "next") == null) {
                return all;
            }
        }
    }

    private Object get(String path) throws IOException {
        return call(request(path).GET().build());
    }

    private Object send(String method, String path, String json) throws IOException {
        return call(request(path).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(url + "/rest/api" + path)).timeout(Duration.ofMinutes(2))
                .header("Authorization", auth).header("Accept", "application/json");
    }

    private Object call(HttpRequest request) throws IOException {
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("The connection to Confluence was interrupted");
        } catch (IOException e) {
            throw new IOException("Confluence at " + url + " cannot be reached: " + e, e);
        }
        int status = response.statusCode();
        String body = response.body();
        if (status >= 200 && status < 300) {
            try {
                return body == null || body.isBlank() ? Map.of() : Json.parse(body);
            } catch (IllegalArgumentException e) {
                throw new Failure(status, "Confluence at " + url + " did not answer as its REST API does. Is the "
                        + "address right?");
            }
        }
        throw new Failure(status, message(request, status, body,
                response.headers().firstValue("X-Seraph-LoginReason").orElse("")));
    }

    private String message(HttpRequest request, int status, String body, String loginReason) {
        if (loginReason.contains("AUTHENTICATION_DENIED")) {
            return "Confluence asks for a CAPTCHA after failed sign-ins: sign in once in the browser, then try again.";
        }
        if (status == 401) {
            return "Confluence did not accept the user name and password (401).";
        }
        String said = null;
        try {
            said = Json.string(Json.parse(body), "message");
        } catch (RuntimeException e) {
            // not JSON: an HTML error page
        }
        if (said == null) {
            said = body == null ? "" : body.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").strip();
        }
        said = said.length() > MAX_ERROR ? said.substring(0, MAX_ERROR) + "..." : said;
        String path = request.uri().getRawPath().replaceFirst("^.*?/rest/api", "");
        return (status == 404 ? "Not found in Confluence" : status == 403 ? "Confluence does not allow it"
                : "Confluence answered " + status) + " (" + request.method() + " " + path + ")" + (said.isEmpty() ? "" : ": " + said);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
