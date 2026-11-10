package io.github.p3chys.documentationarchi.render;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.UncheckedIOException;
import java.nio.file.LinkOption;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.util.XRLog;

import io.github.p3chys.documentationarchi.doc.Assets;
import io.github.p3chys.documentationarchi.doc.Doc;
import io.github.p3chys.documentationarchi.doc.DocFolder;
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.text.KeywordRules;

/**
 * Writes a {@link Doc} to a folder:
 * <pre>
 * html/index.html, html/&lt;view&gt;.html, html/elements_a-z.html, html/elements_by-type.html, html/search.js, html/nav.js, html/images/, html/assets/, html/style.css
 * &lt;title&gt;.pdf, assets/ (the files the PDF links to)
 * confluence/pages/&lt;view&gt;.xml, confluence/attachments/, confluence/manifest.json, confluence/upload_to_confluence.py
 * </pre>
 * Each output carries its own copy of the assets it uses, so the export folder can be moved or sent without losing
 * them; the PDF also holds them inside itself.
 */
public final class Exporter {

    public enum Format { HTML, PDF, CONFLUENCE }

    private static final int MAX_TITLE = 240; // Confluence allows 255, leaving room for the " (2)" of a duplicate

    private final Doc doc;
    private final KeywordRules rules;
    private String version;

    public Exporter(Doc doc, KeywordRules rules) {
        this.doc = doc;
        this.rules = rules;
    }

    /**
     * Adds "Generated &lt;date and time&gt; from &lt;model&gt;" to the HTML overview page and the first page of the PDF,
     * and " – &lt;branch&gt; ＠ &lt;commit&gt;" when a {@code .coarchi} folder is next to the model file. Not to the
     * Confluence pages: they are edited there, and the line would age.
     *
     * @param modelFile the .archimate file, null if the model was never saved
     */
    public Exporter version(String modelName, Path modelFile, LocalDateTime time) {
        version = rules.texts().get("generated").replace("{time}",
                time.truncatedTo(ChronoUnit.MINUTES).toString().replace('T', ' ')).replace("{model}", modelName)
                + GitHead.describe(modelFile);
        return this;
    }

    /** References to assets that found no file, see {@link Assets#problems()}. */
    public List<String> problems() {
        return doc.assets.problems();
    }

    /** Told what an export is doing, and asked whether to stop. */
    public interface Progress {
        Progress NONE = new Progress() { };

        /** A new step begins, such as writing one format. */
        default void step(String task) {
        }

        default boolean cancelled() {
            return false;
        }
    }

    public void export(Path folder, Set<Format> formats) throws IOException {
        export(folder, formats, Progress.NONE);
    }

    /**
     * Writes the formats. Each is written next to where it goes and swapped in only when everything succeeded, so a
     * failed or cancelled export leaves the previous one as it was, and a re-export leaves no files of views that no
     * longer exist. Only what the export owns is replaced: html/, confluence/, the PDF and assets/ (the PDF's), and
     * only of the formats exported now.
     *
     * @throws CancellationException if the progress asked to stop; nothing was changed then
     */
    public void export(Path folder, Set<Format> formats, Progress progress) throws IOException {
        Files.createDirectories(folder);
        Path pdf = folder.resolve(Doc.slug(doc.title) + ".pdf");
        Path assets = folder.resolve("assets");
        List<Path> targets = new ArrayList<>();
        if (formats.contains(Format.HTML)) {
            targets.add(folder.resolve("html"));
        }
        if (formats.contains(Format.PDF)) {
            targets.add(pdf);
            targets.add(assets);
        }
        if (formats.contains(Format.CONFLUENCE)) {
            targets.add(folder.resolve("confluence"));
        }
        targets.forEach(t -> {
            deleteAll(staged(t)); // leftovers of an export that was killed half way
            deleteAll(replaced(t));
        });
        try {
            if (formats.contains(Format.HTML)) {
                step(progress, "Writing HTML pages");
                html(staged(folder.resolve("html")), progress);
            }
            if (formats.contains(Format.PDF)) {
                step(progress, "Writing the PDF");
                pdf(staged(pdf), staged(assets));
            }
            if (formats.contains(Format.CONFLUENCE)) {
                step(progress, "Writing Confluence pages");
                confluence(staged(folder.resolve("confluence")), progress);
            }
            step(progress, "Replacing the previous export");
            swap(targets);
        } finally {
            targets.forEach(t -> deleteAll(staged(t)));
        }
    }

    private static void step(Progress progress, String task) {
        check(progress);
        progress.step(task);
    }

    private static void check(Progress progress) {
        if (progress.cancelled()) {
            throw new CancellationException("The export was cancelled");
        }
    }

    private static Path staged(Path target) {
        return target.resolveSibling(target.getFileName() + ".new");
    }

    private static Path replaced(Path target) {
        return target.resolveSibling(target.getFileName() + ".old");
    }

    /**
     * Moves the written files in place of the old ones. A target that was not written (assets/ of a PDF without
     * assets) is removed. If a move fails, the ones done are put back.
     */
    private static void swap(List<Path> targets) throws IOException {
        List<Path> done = new ArrayList<>();
        try {
            for (Path target : targets) {
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    Files.move(target, replaced(target));
                }
                done.add(target);
                if (Files.exists(staged(target), LinkOption.NOFOLLOW_LINKS)) {
                    Files.move(staged(target), target);
                }
            }
        } catch (IOException | RuntimeException e) {
            for (Path target : done) {
                deleteAll(target);
                if (Files.exists(replaced(target), LinkOption.NOFOLLOW_LINKS)) {
                    try {
                        Files.move(replaced(target), target);
                    } catch (IOException suppressed) {
                        e.addSuppressed(suppressed);
                    }
                }
            }
            throw e;
        }
        targets.forEach(t -> deleteAll(replaced(t)));
    }

    /** Deletes a file or a folder with everything in it; quietly, it is only ever something of ours that is left. */
    private static void deleteAll(Path path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> all = Files.walk(path)) {
            for (Path p : (Iterable<Path>) all.sorted(Comparator.reverseOrder())::iterator) {
                Files.deleteIfExists(p);
            }
        } catch (IOException | UncheckedIOException e) {
            // left behind; the next export removes it
        }
    }

    private void html(Path folder, Progress progress) throws IOException {
        Files.createDirectories(folder.resolve("images"));
        Files.writeString(folder.resolve("style.css"), resource("style.css"));
        Files.writeString(folder.resolve("nav.js"), resource("nav.js"));
        HtmlDialect dialect = new HtmlDialect(doc, false);
        HtmlSite site = new HtmlSite(doc, dialect, rules.texts());
        Files.writeString(folder.resolve(HtmlSite.SEARCH), site.searchData());
        Set<Assets.Asset> used = new LinkedHashSet<>();
        for (DocPage page : doc.pages) {
            check(progress);
            String nav = page == doc.pages.get(0) ? ""
                    : "<nav><a href=\"index.html\">" + Xml.escape(doc.title) + "</a></nav>";
            Files.writeString(folder.resolve(page.slug() + ".html"), htmlPage(page.title, site.sidebar(page, null),
                    nav + "<h1>" + title(page, dialect) + "</h1>" + PageBody.render(doc, page, dialect, rules, used)
                    + (page == doc.pages.get(0) && version != null
                            ? "<footer class=\"version\">" + Xml.escape(version) + "</footer>" : "")));
            if (page.image != null) {
                Files.write(folder.resolve("images").resolve(page.slug() + ".png"), page.image);
            }
        }
        String home = "<nav><a href=\"index.html\">" + Xml.escape(doc.title) + "</a></nav>";
        String aToZ = rules.texts().get("elements-a-z");
        Files.writeString(folder.resolve(HtmlSite.A_Z), htmlPage(aToZ, site.sidebar(null, HtmlSite.A_Z),
                home + "<h1>" + Xml.escape(aToZ) + "</h1>" + site.aToZ()));
        String byType = rules.texts().get("elements-by-type");
        Files.writeString(folder.resolve(HtmlSite.BY_TYPE), htmlPage(byType, site.sidebar(null, HtmlSite.BY_TYPE),
                home + "<h1>" + Xml.escape(byType) + "</h1>" + site.byType()));
        copy(used, folder.resolve("assets"));
    }

    /** A page of the HTML export: the sidebar, then the content; the scripts only make the search work. */
    private static String htmlPage(String title, String sidebar, String content) {
        return "<!DOCTYPE html>\n<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"utf-8\"/>"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/><title>" + Xml.escape(title)
                + "</title><link rel=\"stylesheet\" href=\"style.css\"/></head><body class=\"site\">" + sidebar
                + "<main>" + content + "</main><script src=\"" + HtmlSite.SEARCH + "\"></script>"
                + "<script src=\"nav.js\"></script></body></html>\n";
    }

    private static void copy(Set<Assets.Asset> assets, Path folder) throws IOException {
        if (!assets.isEmpty()) {
            Files.createDirectories(folder);
        }
        for (Assets.Asset asset : assets) {
            Files.copy(asset.file, folder.resolve(asset.name), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * The PDF, and next to it assets/ with the files it links to. Those files also go inside the PDF as its
     * attachments, for a PDF sent on its own (viewers with an attachments list show them).
     */
    private void pdf(Path file, Path assets) throws IOException {
        XRLog.setLoggingEnabled(false);
        Set<Assets.Asset> used = new LinkedHashSet<>();
        String html = singleDocument(used);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(html, null);
        // pictures are in the document as data; nothing else is ever loaded
        for (ExternalResourceControlPriority priority : ExternalResourceControlPriority.values()) {
            builder.useExternalResourceAccessControl((uri, type) -> uri.startsWith("data:"), priority);
        }
        font(builder, "DejaVuSans.ttf", 400, FontStyle.NORMAL);
        font(builder, "DejaVuSans-Bold.ttf", 700, FontStyle.NORMAL);
        font(builder, "DejaVuSans-Oblique.ttf", 400, FontStyle.ITALIC);
        font(builder, "DejaVuSans-BoldOblique.ttf", 700, FontStyle.ITALIC);
        builder.useFont(() -> Exporter.class.getResourceAsStream("fonts/DejaVuSansMono.ttf"), "DejaVu Sans Mono",
                400, FontStyle.NORMAL, true);
        builder.toStream(out);
        builder.run();
        used.removeIf(HtmlDialect::pdfImage);
        copy(used, assets);
        try (PDDocument pdf = Loader.loadPDF(out.toByteArray())) {
            attach(pdf, used);
            pdf.save(file.toFile());
        }
    }

    private static void attach(PDDocument pdf, Set<Assets.Asset> assets) throws IOException {
        if (assets.isEmpty()) {
            return;
        }
        Map<String, PDComplexFileSpecification> files = new TreeMap<>();
        for (Assets.Asset asset : assets) {
            byte[] bytes = asset.bytes();
            PDEmbeddedFile embedded = new PDEmbeddedFile(pdf, new ByteArrayInputStream(bytes));
            embedded.setSubtype(asset.mimeType());
            embedded.setSize(bytes.length);
            PDComplexFileSpecification spec = new PDComplexFileSpecification();
            spec.setFile(asset.name);
            spec.setFileUnicode(asset.name);
            spec.setEmbeddedFile(embedded);
            spec.setEmbeddedFileUnicode(embedded);
            files.put(asset.name, spec);
        }
        PDEmbeddedFilesNameTreeNode tree = new PDEmbeddedFilesNameTreeNode();
        tree.setNames(files);
        PDDocumentCatalog catalog = pdf.getDocumentCatalog();
        PDDocumentNameDictionary names = catalog.getNames() != null ? catalog.getNames() : new PDDocumentNameDictionary(catalog);
        names.setEmbeddedFiles(tree);
        catalog.setNames(names);
    }

    private static void font(PdfRendererBuilder builder, String file, int weight, FontStyle style) {
        builder.useFont(() -> Exporter.class.getResourceAsStream("fonts/" + file), "DejaVu Sans", weight, style, true);
    }

    private String singleDocument(Set<Assets.Asset> used) throws IOException {
        HtmlDialect dialect = new HtmlDialect(doc, true);
        StringBuilder bookmarks = new StringBuilder();
        bookmarks(doc.outline, bookmarks);
        StringBuilder body = new StringBuilder();
        for (DocPage page : doc.pages) {
            String id = HtmlDialect.pageId(page);
            body.append("<h1 id=\"").append(id).append("\">").append(title(page, dialect)).append("</h1>")
                    .append(PageBody.render(doc, page, dialect, rules, used));
        }
        return "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"utf-8\"/><title>" + Xml.escape(doc.title)
                + "</title><bookmarks>" + bookmarks + "</bookmarks><style>" + resource("style.css") + resource("pdf.css") + firstPage()
                + "</style></head><body>" + body + "</body></html>";
    }

    /** The bookmarks of the PDF, nested as the folders; a folder's leads to its first page. */
    private static void bookmarks(List<Doc.Node> nodes, StringBuilder to) {
        for (Doc.Node node : nodes) {
            DocPage page = node.page != null ? node.page : first(node);
            to.append("<bookmark name=\"").append(Xml.escape(node.page != null ? page.title : node.folder.name))
                    .append("\" href=\"#").append(HtmlDialect.pageId(page)).append("\"");
            if (node.page != null) {
                to.append("/>");
            } else {
                to.append(">");
                bookmarks(node.children, to);
                to.append("</bookmark>");
            }
        }
    }

    /** The first page in a folder, or in the folders in it; every folder of the outline has one. */
    static DocPage first(Doc.Node folder) {
        Doc.Node node = folder;
        while (node.page == null) {
            node = node.children.get(0);
        }
        return node.page;
    }

    /** The version line as the last line of the first page: a page margin box, which sits below the content. */
    private String firstPage() {
        if (version == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        version.replace('＠', '@') // the PDF font has no full-width @
                .chars().forEach(c -> text.append(c == '"' || c == '\\' || c == '&' || c == '<' || c < 0x20
                ? String.format("\\%x ", c)
                : String.valueOf((char) c)));
        return "@page :first { @bottom-left { content: \"" + text + "\"; font-family: \"DejaVu Sans\"; font-size: 8px; "
                + "color: #6b778c; } }";
    }

    /** The page title as the h1 of HTML and PDF; in Confluence it is the page's own title, which has no style. */
    private String title(DocPage page, Dialect dialect) {
        String title = Xml.escape(page.title);
        return rules.heading(1) == null ? title : dialect.styled(title, rules.heading(1));
    }

    private void confluence(Path folder, Progress progress) throws IOException {
        Path pages = Files.createDirectories(folder.resolve("pages"));
        Path attachments = Files.createDirectories(folder.resolve("attachments"));
        Set<String> taken = new HashSet<>();
        Map<DocPage, String> titles = new LinkedHashMap<>();
        doc.pages.forEach(p -> titles.put(p, confluenceTitle(p.title, taken)));
        Map<DocFolder, String> folders = new LinkedHashMap<>();
        doc.folders.forEach(f -> folders.put(f, confluenceTitle(f.name, taken)));
        StorageDialect dialect = new StorageDialect(titles);
        List<String> entries = new ArrayList<>();
        for (DocPage page : doc.pages) {
            check(progress);
            Set<Assets.Asset> used = new LinkedHashSet<>();
            Files.writeString(pages.resolve(page.slug() + ".xml"), PageBody.render(doc, page, dialect, rules, used));
            List<String> files = new ArrayList<>();
            if (page.image != null) {
                files.add(json(page.slug() + ".png"));
                Files.write(attachments.resolve(page.slug() + ".png"), page.image);
            }
            String parent = page == doc.pages.get(0) ? null
                    : page.folder != null ? folders.get(page.folder) : titles.get(doc.pages.get(0));
            entries.add(manifestEntry(titles.get(page), parent, page.slug(), files, used, attachments));
        }
        // after the first page, each folder before what is in it, so that the upload makes the parents first
        List<String> folderEntries = new ArrayList<>();
        for (DocFolder f : doc.folders) {
            Set<Assets.Asset> used = new LinkedHashSet<>();
            Files.writeString(pages.resolve(f.slug() + ".xml"), folderPage(f, dialect, titles, folders, used));
            String parent = f.parent == null ? titles.get(doc.pages.get(0)) : folders.get(f.parent);
            folderEntries.add(manifestEntry(folders.get(f), parent, f.slug(), new ArrayList<>(), used, attachments));
        }
        entries.addAll(1, folderEntries);
        Files.writeString(folder.resolve("manifest.json"),
                "{\n  \"pages\": [\n" + String.join(",\n", entries) + "\n  ]\n}\n");
        Files.writeString(folder.resolve("upload_to_confluence.py"), resource("upload_to_confluence.py"));
    }

    /** @param parent title of the parent page, null for the top */
    private static String manifestEntry(String title, String parent, String slug, List<String> files,
            Set<Assets.Asset> used, Path attachments) throws IOException {
        copy(used, attachments);
        used.forEach(a -> files.add(json(a.name)));
        return "    {\"title\": " + json(title) + ", \"parent\": " + (parent == null ? "null" : json(parent))
                + ", \"file\": " + json("pages/" + slug + ".xml") + ", \"attachments\": [" + String.join(", ", files) + "]}";
    }

    /** The page of a folder: its description, then links to what is in it, pages and folders. */
    private String folderPage(DocFolder folder, StorageDialect dialect, Map<DocPage, String> titles,
            Map<DocFolder, String> folders, Set<Assets.Asset> used) {
        DocPage page = new DocPage(folder.id, folder.name);
        page.documentation = folder.documentation;
        StringBuilder list = new StringBuilder();
        for (Doc.Node node : node(doc.outline, folder).children) {
            String title = node.page != null ? titles.get(node.page) : folders.get(node.folder);
            String label = Xml.escape(node.page != null ? node.page.title : node.folder.name);
            list.append("<li>").append(StorageDialect.pageLink(title, label)).append("</li>");
        }
        String description = PageBody.render(doc, page, dialect, rules, used);
        return description + (description.isEmpty() ? "" : "<hr/>") + "<ul>" + list + "</ul>";
    }

    /** The node of a folder in the outline, null if it is not there. */
    static Doc.Node node(List<Doc.Node> nodes, DocFolder folder) {
        for (Doc.Node node : nodes) {
            if (node.folder == null) {
                continue;
            }
            Doc.Node found = node.folder == folder ? node : node(node.children, folder);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Page titles are unique per Confluence space, ignoring case, so the model name goes in front of each view's. */
    private String confluenceTitle(String name, Set<String> taken) {
        String title = name.equals(doc.title) ? name : doc.title + " - " + name;
        title = title.length() > MAX_TITLE ? title.substring(0, MAX_TITLE) : title;
        String candidate = title;
        for (int i = 2; !taken.add(candidate.toLowerCase(Locale.ROOT)); i++) {
            candidate = title + " (" + i + ")";
        }
        return candidate;
    }

    private static String json(String text) {
        StringBuilder s = new StringBuilder("\"");
        text.chars().forEach(c -> {
            if (c == '"' || c == '\\') {
                s.append('\\').append((char) c);
            } else if (c < 0x20) {
                s.append(String.format("\\u%04x", c));
            } else {
                s.append((char) c);
            }
        });
        return s.append('"').toString();
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = Exporter.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
