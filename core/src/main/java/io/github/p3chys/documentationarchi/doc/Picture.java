package io.github.p3chys.documentationarchi.doc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The PNG of a view's picture. An export keeps it in a temporary file, so that a model with many large views does not
 * hold every picture in memory until the last format is written; the bytes are read where a format writes them. The
 * Documentation Preview and tests keep it in memory.
 */
public final class Picture {

    /**
     * Where the temporary pictures go: a folder of this run's own, made new (owner-only on POSIX), so nobody else can
     * have put a file or a link there before.
     */
    private static Path folder;

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
        Path file = Files.createTempFile(folder(), "view-", ".png");
        file.toFile().deleteOnExit();
        Files.write(file, png);
        return new Picture(null, file);
    }

    private static synchronized Path folder() throws IOException {
        if (folder == null || !Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
            folder = Files.createTempDirectory("documentationArchi-pictures-");
            folder.toFile().deleteOnExit(); // after the files in it, which were registered later
        }
        return folder;
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
