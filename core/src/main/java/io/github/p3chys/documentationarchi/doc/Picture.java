package io.github.p3chys.documentationarchi.doc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;

/**
 * The PNG of a view's picture. An export keeps it in a temporary file, so that a model with many large views does not
 * hold every picture in memory until the last format is written; the bytes are read where a format writes them. The
 * Documentation Preview and tests keep it in memory.
 */
public final class Picture {

    /** Where the temporary pictures go; what an export killed half way left there is removed by a later one. */
    private static final String FOLDER = "documentationArchi-pictures";
    private static final Duration STALE = Duration.ofDays(1);
    private static boolean swept;

    private final byte[] bytes;
    private final Path file;

    private Picture(byte[] bytes, Path file) {
        this.bytes = bytes;
        this.file = file;
    }

    /** A picture held in memory. */
    public static Picture of(byte[] png) {
        return new Picture(png, null);
    }

    /** A picture in a temporary file, deleted by {@link #delete()} (the export does it when it ends) or at exit. */
    public static Picture temporary(byte[] png) throws IOException {
        Path folder = Files.createDirectories(Path.of(System.getProperty("java.io.tmpdir"), FOLDER));
        sweep(folder);
        Path file = Files.createTempFile(folder, "view-", ".png");
        file.toFile().deleteOnExit();
        Files.write(file, png);
        return new Picture(null, file);
    }

    /** Leftovers of exports that never ended, once per run. */
    private static synchronized void sweep(Path folder) {
        if (swept) {
            return;
        }
        swept = true;
        Instant before = Instant.now().minus(STALE);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "view-*.png")) {
            for (Path f : files) {
                if (Files.getLastModifiedTime(f).toInstant().isBefore(before)) {
                    Files.deleteIfExists(f);
                }
            }
        } catch (IOException | UncheckedIOException e) {
            // left for the next run
        }
    }

    /** Whether it is in a temporary file. */
    public boolean temporary() {
        return file != null;
    }

    public byte[] bytes() throws IOException {
        return file == null ? bytes : Files.readAllBytes(file);
    }

    public InputStream open() throws IOException {
        return file == null ? new ByteArrayInputStream(bytes) : Files.newInputStream(file);
    }

    public void copyTo(Path target) throws IOException {
        if (file == null) {
            Files.write(target, bytes);
        } else {
            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Deletes the temporary file, quietly; a picture in memory stays as it is. */
    public void delete() {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                // deleted at exit, or by a later export
            }
        }
    }
}
