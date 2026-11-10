package io.github.p3chys.documentationarchi.render;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;

import com.openhtmltopdf.extend.FSStream;
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

    /** How much of the purpose the cover shows: enough to say what the model is, not the whole description. */
    private static final int COVER_PARAGRAPHS = 3;
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
            doc.pages.forEach(p -> {
                if (p.image != null) {
                    p.image.delete(); // a temporary file is the export's; one in memory stays
                }
            });
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
                page.image.copyTo(folder.resolve("images").resolve(page.slug() + ".png"));
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
        // the pictures of views are read from their files one at a time, assets are in the document as data;
        // nothing else is ever loaded
        builder.useProtocolsStreamImplementation(this::picture, HtmlDialect.PICTURE.substring(0, HtmlDialect.PICTURE.length() - 1));
        for (ExternalResourceControlPriority priority : ExternalResourceControlPriority.values()) {
            builder.useExternalResourceAccessControl((uri, type) -> uri.startsWith("data:")
                    || uri.startsWith(HtmlDialect.PICTURE), priority);
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

    /** The picture of a view for the PDF library, by the address {@link HtmlDialect#image} gave it. */
    private FSStream picture(String uri) {
        DocPage page = doc.pages.stream().filter(p -> p.image != null && uri.equals(HtmlDialect.PICTURE + p.slug()))
                .findFirst().orElse(null);
        return new FSStream() {
            @Override
            public InputStream getStream() {
                try {
                    return page == null ? null : page.image.open();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public Reader getReader() {
                return null;
            }
        };
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
        boolean landscape = rules.pdf("landscape");
        HtmlDialect dialect = new HtmlDialect(doc, true).landscape(landscape);
        StringBuilder bookmarks = new StringBuilder();
        bookmarks(doc.outline, bookmarks);
        StringBuilder body = new StringBuilder();
        boolean cover = rules.pdf("cover") && doc.pages.size() > 1; // one page needs neither
        if (cover) {
            body.append(cover(dialect, used));
        }
        if (rules.pdf("contents") && doc.pages.size() > 1) {
            body.append("<div class=\"toc\"><p class=\"toc-title\">").append(Xml.escape(rules.texts().get("contents")))
                    .append("</p><ul>");
            contents(doc.outline, body);
            body.append("</ul></div>");
        }
        for (DocPage page : doc.pages) {
            boolean wide = landscape && Pictures.landscape(page);
            body.append(wide ? "<div class=\"landscape\">" : "").append("<h1 id=\"").append(HtmlDialect.pageId(page))
                    .append("\">").append(title(page, dialect)).append("</h1>")
                    .append("<div class=\"running-title\">").append(Xml.escape(page.title)).append("</div>")
                    .append(PageBody.render(doc, page, dialect, rules, used)).append(wide ? "</div>" : "");
        }
        return "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"utf-8\"/><title>" + Xml.escape(doc.title)
                + "</title><bookmarks>" + bookmarks + "</bookmarks><style>" + resource("style.css") + resource("pdf.css")
                + margins() + (cover ? "" : firstPage()) + "</style></head><body>" + body + "</body></html>";
    }

    /** The first page of the PDF: the title, the version line and the start of the description (the purpose). */
    private String cover(HtmlDialect dialect, Set<Assets.Asset> used) {
        DocPage first = doc.pages.get(0);
        String[] paragraphs = first.documentation == null ? new String[0] : first.documentation.strip().split("\\R\\s*\\R");
        DocPage purpose = new DocPage(first.id, first.title);
        purpose.documentation = String.join("\n\n", List.of(paragraphs).subList(0, Math.min(COVER_PARAGRAPHS, paragraphs.length)));
        return "<div class=\"cover\"><p class=\"cover-title\">" + Xml.escape(doc.title) + "</p>"
                + (version == null ? "" : "<p class=\"cover-version\">" + Xml.escape(version.replace('＠', '@')) + "</p>")
                + "<div class=\"cover-purpose\">" + PageBody.render(doc, purpose, dialect, rules, used) + "</div></div>";
    }

    /** The table of contents: the pages under their folders, each with its page number. */
    private static void contents(List<Doc.Node> nodes, StringBuilder to) {
        for (Doc.Node node : nodes) {
            if (node.page != null) {
                to.append("<li><a href=\"#").append(HtmlDialect.pageId(node.page)).append("\">")
                        .append(Xml.escape(node.page.title)).append("</a></li>");
            } else {
                to.append("<li class=\"toc-folder\"><span>").append(Xml.escape(node.folder.name)).append("</span><ul>");
                contents(node.children, to);
                to.append("</ul></li>");
            }
        }
    }

    /** The running header (the page's title) and footer (the document's and "page n of m"), but not on the cover. */
    private String margins() {
        String pageOf = rules.texts().get("page-of");
        StringBuilder footer = new StringBuilder();
        Matcher counter = Pattern.compile("\\{pages?\\}").matcher(pageOf);
        int last = 0;
        while (counter.find()) {
            String text = pageOf.substring(last, counter.start());
            footer.append(text.isEmpty() ? "" : cssString(text) + " ")
                    .append(counter.group().equals("{pages}") ? "counter(pages) " : "counter(page) ");
            last = counter.end();
        }
        footer.append(last < pageOf.length() ? cssString(pageOf.substring(last)) : "");
        String look = " font-family: \"DejaVu Sans\"; font-size: 8px; color: #6b778c;";
        return "@page { @top-left { content: element(title);" + look + " } @bottom-left { content: " + cssString(doc.title)
                + ";" + look + " } @bottom-right { content: " + footer.toString().strip() + ";" + look + " } }";
    }

    /** Text as a CSS string, with what would end it escaped. */
    private static String cssString(String text) {
        StringBuilder s = new StringBuilder("\"");
        text.replace('＠', '@') // the PDF font has no full-width @
                .chars().forEach(c -> s.append(c == '"' || c == '\\' || c == '&' || c == '<' || c < 0x20
                        ? String.format("\\%x ", c) : String.valueOf((char) c)));
        return s.append('"').toString();
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
        return "@page :first { @bottom-left { content: " + cssString(version) + "; font-family: \"DejaVu Sans\"; font-size: 8px; "
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
                page.image.copyTo(attachments.resolve(page.slug() + ".png"));
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
