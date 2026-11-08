package io.github.p3chys.documentationarchi.render;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
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
import io.github.p3chys.documentationarchi.doc.DocPage;
import io.github.p3chys.documentationarchi.text.KeywordRules;

/**
 * Writes a {@link Doc} to a folder:
 * <pre>
 * html/index.html, html/&lt;view&gt;.html, html/images/, html/assets/, html/style.css
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

    public Exporter(Doc doc, KeywordRules rules) {
        this.doc = doc;
        this.rules = rules;
    }

    /** References to assets that found no file, see {@link Assets#problems()}. */
    public List<String> problems() {
        return doc.assets.problems();
    }

    public void export(Path folder, Set<Format> formats) throws IOException {
        Files.createDirectories(folder);
        if (formats.contains(Format.HTML)) {
            html(folder.resolve("html"));
        }
        if (formats.contains(Format.PDF)) {
            pdf(folder.resolve(Doc.slug(doc.title) + ".pdf"));
        }
        if (formats.contains(Format.CONFLUENCE)) {
            confluence(folder.resolve("confluence"));
        }
    }

    private void html(Path folder) throws IOException {
        Files.createDirectories(folder.resolve("images"));
        Files.writeString(folder.resolve("style.css"), resource("style.css"));
        HtmlDialect dialect = new HtmlDialect(false);
        Set<Assets.Asset> used = new LinkedHashSet<>();
        for (DocPage page : doc.pages) {
            String nav = page == doc.pages.get(0) ? ""
                    : "<nav><a href=\"index.html\">" + Xml.escape(doc.title) + "</a></nav>";
            Files.writeString(folder.resolve(page.slug() + ".html"), "<!DOCTYPE html>\n"
                    + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"utf-8\"/><title>"
                    + Xml.escape(page.title) + "</title><link rel=\"stylesheet\" href=\"style.css\"/></head><body>"
                    + nav + "<h1>" + title(page, dialect) + "</h1>" + PageBody.render(doc, page, dialect, rules, used)
                    + "</body></html>\n");
            if (page.image != null) {
                Files.write(folder.resolve("images").resolve(page.slug() + ".png"), page.image);
            }
        }
        copy(used, folder.resolve("assets"));
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
    private void pdf(Path file) throws IOException {
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
        copy(used, file.resolveSibling("assets"));
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
        HtmlDialect dialect = new HtmlDialect(true);
        StringBuilder bookmarks = new StringBuilder();
        StringBuilder body = new StringBuilder();
        for (DocPage page : doc.pages) {
            String id = HtmlDialect.pageId(page);
            bookmarks.append("<bookmark name=\"").append(Xml.escape(page.title)).append("\" href=\"#").append(id).append("\"/>");
            body.append("<h1 id=\"").append(id).append("\">").append(title(page, dialect)).append("</h1>")
                    .append(PageBody.render(doc, page, dialect, rules, used));
        }
        return "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"utf-8\"/><title>" + Xml.escape(doc.title)
                + "</title><bookmarks>" + bookmarks + "</bookmarks><style>" + resource("style.css") + resource("pdf.css")
                + "</style></head><body>" + body + "</body></html>";
    }

    /** The page title as the h1 of HTML and PDF; in Confluence it is the page's own title, which has no style. */
    private String title(DocPage page, Dialect dialect) {
        String title = Xml.escape(page.title);
        return rules.heading(1) == null ? title : dialect.styled(title, rules.heading(1));
    }

    private void confluence(Path folder) throws IOException {
        Path pages = Files.createDirectories(folder.resolve("pages"));
        Path attachments = Files.createDirectories(folder.resolve("attachments"));
        Map<DocPage, String> titles = confluenceTitles();
        StorageDialect dialect = new StorageDialect(titles);
        StringBuilder manifest = new StringBuilder("{\n  \"pages\": [");
        String separator = "\n";
        for (DocPage page : doc.pages) {
            Set<Assets.Asset> used = new LinkedHashSet<>();
            Files.writeString(pages.resolve(page.slug() + ".xml"), PageBody.render(doc, page, dialect, rules, used));
            List<String> files = new ArrayList<>();
            if (page.image != null) {
                files.add(json(page.slug() + ".png"));
                Files.write(attachments.resolve(page.slug() + ".png"), page.image);
            }
            copy(used, attachments);
            used.forEach(a -> files.add(json(a.name)));
            String parent = page == doc.pages.get(0) ? "null" : json(titles.get(doc.pages.get(0)));
            manifest.append(separator).append("    {\"title\": ").append(json(titles.get(page)))
                    .append(", \"parent\": ").append(parent)
                    .append(", \"file\": ").append(json("pages/" + page.slug() + ".xml"))
                    .append(", \"attachments\": [").append(String.join(", ", files)).append("]}");
            separator = ",\n";
        }
        Files.writeString(folder.resolve("manifest.json"), manifest.append("\n  ]\n}\n").toString());
        Files.writeString(folder.resolve("upload_to_confluence.py"), resource("upload_to_confluence.py"));
    }

    /** Page titles are unique per Confluence space, ignoring case, so the model name goes in front of each view's. */
    private Map<DocPage, String> confluenceTitles() {
        Map<DocPage, String> titles = new LinkedHashMap<>();
        Set<String> taken = new HashSet<>();
        for (DocPage page : doc.pages) {
            String title = page.title.equals(doc.title) ? page.title : doc.title + " - " + page.title;
            title = title.length() > MAX_TITLE ? title.substring(0, MAX_TITLE) : title;
            String candidate = title;
            for (int i = 2; !taken.add(candidate.toLowerCase(Locale.ROOT)); i++) {
                candidate = title + " (" + i + ")";
            }
            titles.put(page, candidate);
        }
        return titles;
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
