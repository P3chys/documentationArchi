package io.github.p3chys.documentationarchi.render;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Which state of the model a shared copy is on, read from the git repository {@code .coarchi} that the sibling plugin
 * cooperativeArchi keeps next to the model file. Read as plain files (HEAD, refs, packed-refs): there is no git
 * library and no dependency on that plugin, the folder is only looked for.
 */
public final class GitHead {

    static final String FOLDER = ".coarchi";
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{40}|[0-9a-f]{64}");
    /** Segments of letters, digits and a few signs, separated by single slashes: no "..", backslash, colon or leading slash. */
    private static final Pattern BRANCH_NAME = Pattern.compile("[\\p{L}\\p{N}_+.@-]+(/[\\p{L}\\p{N}_+.@-]+)*");
    private static final String BRANCH ="ref: refs/heads/";
    private static final int SHORT = 7;

    private GitHead() {
    }

    /**
     * " – main ＠ a1b2c3d" for the model file, "" when there is no {@code .coarchi} folder next to it. A part that
     * cannot be read is left out: only the branch if the commit is unknown, only the commit if HEAD is detached.
     */
    public static String describe(Path modelFile) {
        if (modelFile == null) {
            return "";
        }
        try {
            Path parent = modelFile.toAbsolutePath().getParent();
            Path git = parent == null ? null : parent.resolve(FOLDER);
            if (git == null || !Files.isDirectory(git)) {
                return "";
            }
            if (!Files.isRegularFile(git.resolve("HEAD")) && Files.isDirectory(git.resolve(".git"))) {
                git = git.resolve(".git");
            }
            String branch = null;
            String commit;
            String head = read(git.resolve("HEAD"));
            if (head == null) {
                return "";
            }
            if (head.startsWith(BRANCH)) {
                branch = head.substring(BRANCH.length()).strip();
                if (!BRANCH_NAME.matcher(branch).matches() || branch.contains("..")) {
                    return ""; // not a branch name git allows; never turn it into a path
                }
                commit = ref(git, "refs/heads/" + branch);
            } else {
                commit = HASH.matcher(head).matches() ? head : null; // detached
            }
            String shortCommit = commit == null ? null : commit.substring(0, SHORT);
            if (branch == null && shortCommit == null) {
                return "";
            }
            if (branch == null) {
                return " – detached ＠ " + shortCommit;
            }
            return " – " + branch + (shortCommit == null ? "" : " ＠ " + shortCommit);
        } catch (RuntimeException e) { // a path that cannot be used: no line is better than a failed export
            return "";
        }
    }

    /** The commit a ref points to: its loose file, else its line in packed-refs; null if unknown. */
    private static String ref(Path git, String name) {
        String loose = read(git.resolve(name));
        if (loose != null && HASH.matcher(loose).matches()) {
            return loose;
        }
        String packed = read(git.resolve("packed-refs"));
        if (packed != null) {
            for (String line : packed.split("\\R")) {
                String[] parts = line.strip().split("\\s+");
                if (parts.length == 2 && parts[1].equals(name) && HASH.matcher(parts[0]).matches()) {
                    return parts[0];
                }
            }
        }
        return null;
    }

    private static String read(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8).strip() : null;
        } catch (IOException | RuntimeException e) { // unreadable or not text
            return null;
        }
    }
}
