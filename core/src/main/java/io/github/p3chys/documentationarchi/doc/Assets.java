package io.github.p3chys.documentationarchi.doc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

/**
 * The files of the Assets folder next to the model, which descriptions refer to with {@code [[asset:name]]}. The
 * export carries a copy of each file it uses, so it never points back to this folder.
 * <p>
 * A reference is a path inside the folder ({@code diagrams/flow.png}) or just the file name when only one file has
 * it; case and the kind of slash don't matter. References that find no file, or several, are collected as
 * {@link #problems()}.
 */
public final class Assets {

    /** The folder next to the model; its name is found ignoring case. */
    public static final String FOLDER = "Assets";

    private static final Set<String> IMAGES = Set.of("png", "jpg", "jpeg", "gif", "bmp", "svg");

    /** One file of the folder, under a name unique in the export. */
    public static final class Asset {
        public final Path file;
        /** File name in the export: the file's own, made unique among the export's files ignoring case. */
        public final String name;
        private int width = -1;

        Asset(Path file, String name) {
            this.file = file;
            this.name = name;
        }

        public String extension() {
            int dot = name.lastIndexOf('.');
            return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        }

        /** Shown as a picture (rather than a file to open) where the output can. */
        public boolean image() {
            return IMAGES.contains(extension());
        }

        public String mimeType() {
            switch (extension()) {
                case "png": return "image/png";
                case "jpg": case "jpeg": return "image/jpeg";
                case "gif": return "image/gif";
                case "bmp": return "image/bmp";
                case "svg": return "image/svg+xml";
                default:
                    try {
                        String type = Files.probeContentType(file);
                        return type != null ? type : "application/octet-stream";
                    } catch (IOException e) {
                        return "application/octet-stream";
                    }
            }
        }

        /** Width of a picture in pixels, 0 when it can't be read (SVG, damaged files). */
        public synchronized int width() {
            if (width < 0) {
                try {
                    var picture = ImageIO.read(file.toFile());
                    width = picture == null ? 0 : picture.getWidth();
                } catch (IOException e) {
                    width = 0;
                }
            }
            return width;
        }

        public byte[] bytes() throws IOException {
            return Files.readAllBytes(file);
        }
    }

    private final Path folder;
    /** By path inside the folder, lower case with forward slashes. */
    private final Map<String, Path> byPath = new HashMap<>();
    /** By file name, lower case. */
    private final Map<String, List<Path>> byName = new HashMap<>();
    private final Map<Path, Asset> assets = new HashMap<>();
    private final Set<String> taken = new HashSet<>();
    private final Set<String> problems = new LinkedHashSet<>();

    private Assets(Path folder) {
        this.folder = folder;
        if (folder == null) {
            return;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path f : files.filter(Files::isRegularFile).collect(Collectors.toList())) {
                String path = folder.relativize(f).toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                byPath.put(path, f);
                byName.computeIfAbsent(f.getFileName().toString().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(f);
            }
        } catch (IOException e) {
            problems.add("Cannot read " + folder + ": " + e.getMessage());
        }
    }

    /** No Assets folder: every reference is a problem. */
    public static Assets none() {
        return new Assets(null);
    }

    /** The Assets folder in this folder (the model's), if there is one. */
    public static Assets in(Path modelFolder) {
        if (modelFolder == null || !Files.isDirectory(modelFolder)) {
            return none();
        }
        try (Stream<Path> children = Files.list(modelFolder)) {
            return new Assets(children.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(FOLDER)).findFirst().orElse(null));
        } catch (IOException e) {
            return none();
        }
    }

    /** A file name the export uses for something else (the pictures of views), which assets must not take. */
    void reserve(String name) {
        taken.add(name.toLowerCase(Locale.ROOT));
    }

    /**
     * The file a reference means, or null (and a problem noted) when there is none or the name is ambiguous.
     *
     * @param where the page the reference is on, for the problem
     */
    public synchronized Asset find(String reference, String where) {
        String path = reference.strip().replace('\\', '/').replaceAll("^/+", "").toLowerCase(Locale.ROOT);
        Path file = byPath.get(path);
        if (file == null && !path.contains("/")) {
            List<Path> named = byName.getOrDefault(path, List.of());
            if (named.size() > 1) {
                problems.add("Asset \"" + reference + "\" on \"" + where + "\" matches " + named.size()
                        + " files, use its path inside " + FOLDER + "/");
                return null;
            }
            file = named.isEmpty() ? null : named.get(0);
        }
        if (file == null) {
            problems.add("Asset \"" + reference + "\" on \"" + where + "\" not found"
                    + (folder == null ? ": there is no " + FOLDER + " folder next to the model" : " in " + folder));
            return null;
        }
        return assets.computeIfAbsent(file, f -> new Asset(f, unique(f.getFileName().toString())));
    }

    private String unique(String name) {
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        String candidate = name;
        for (int i = 2; !taken.add(candidate.toLowerCase(Locale.ROOT)); i++) {
            candidate = base + "-" + i + extension;
        }
        return candidate;
    }

    /** References that found no file, in the order met. */
    public synchronized List<String> problems() {
        return List.copyOf(problems);
    }
}
