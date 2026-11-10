package io.github.p3chys.documentationarchi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.p3chys.documentationarchi.render.GitHead;

class GitHeadTest {

    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";

    @TempDir
    Path folder;

    private Path model;
    private Path git;

    @BeforeEach
    void repository() throws IOException {
        model = folder.resolve("m.archimate");
        git = Files.createDirectories(folder.resolve(".coarchi"));
    }

    private void write(String file, String text) throws IOException {
        Files.createDirectories(git.resolve(file).getParent());
        Files.writeString(git.resolve(file), text);
    }

    @Test
    void noCoarchiFolderGivesNothing() throws IOException {
        Files.delete(git);
        assertThat(GitHead.describe(model)).isEmpty();
        assertThat(GitHead.describe(null)).isEmpty();
    }

    @Test
    void branchWithALooseRef() throws IOException {
        write("HEAD", "ref: refs/heads/main\n");
        write("refs/heads/main", COMMIT + "\n");
        assertThat(GitHead.describe(model)).isEqualTo(" \u2013 main \uFF20 0123456");
    }

    @Test
    void branchNamesWithSlashes() throws IOException {
        write("HEAD", "ref: refs/heads/analyst/adam\n");
        write("refs/heads/analyst/adam", COMMIT);
        assertThat(GitHead.describe(model)).isEqualTo(" \u2013 analyst/adam \uFF20 0123456");
    }

    @Test
    void branchWithAPackedRef() throws IOException {
        write("HEAD", "ref: refs/heads/main\n");
        write("packed-refs", "# pack-refs with: peeled fully-peeled sorted\n"
                + "ffffffffffffffffffffffffffffffffffffffff refs/heads/other\n"
                + COMMIT + " refs/heads/main\n^" + COMMIT + "\n");
        assertThat(GitHead.describe(model)).isEqualTo(" \u2013 main \uFF20 0123456");
    }

    @Test
    void detachedHead() throws IOException {
        write("HEAD", COMMIT + "\n");
        assertThat(GitHead.describe(model)).isEqualTo(" \u2013 detached \uFF20 0123456");
    }

    @Test
    void anUnbornBranchHasNoCommit() throws IOException {
        write("HEAD", "ref: refs/heads/main\n");
        assertThat(GitHead.describe(model)).isEqualTo(" \u2013 main");
    }

    @Test
    void unreadableOrUnknownContentLeavesItOut() throws IOException {
        assertThat(GitHead.describe(model)).isEmpty(); // no HEAD
        write("HEAD", "garbage");
        assertThat(GitHead.describe(model)).isEmpty();
        write("HEAD", "ref: refs/heads/main");
        write("refs/heads/main", "not a hash");
        assertThat(GitHead.describe(model)).isEqualTo(" \u2013 main");
    }

    @Test
    void aBranchNameThatIsAPathIsNotFollowed() throws IOException {
        write("HEAD", "ref: refs/heads/../../secret");
        write("secret", COMMIT);
        assertThat(GitHead.describe(model)).isEmpty();
    }
}
