package io.github.p3chys.documentationarchi.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import com.archimatetool.model.IArchimateModel;

import io.github.p3chys.documentationarchi.text.KeywordRules;

/**
 * The formatting rules that belong to a model: {@value #NAME} next to its .archimate file, in the "Edit as Text..."
 * format. Kept with the model so every analyst and the CI build style it the same way. Precedence: the project file,
 * then the preferences, then the built-in defaults (what the preferences hold until changed). A file adds its lines to
 * the built-in rules unless it says {@code defaults off}, see {@link KeywordRules#parseFile}.
 */
final class ProjectRules {

    static final String NAME = "documentation.rules";

    private ProjectRules() {
    }

    /** The project rules file of the model, which may not exist; null for a model that was never saved. */
    static Path file(IArchimateModel model) {
        if (model == null || model.getFile() == null) {
            return null;
        }
        Path parent = model.getFile().toPath().toAbsolutePath().getParent();
        return parent == null ? null : parent.resolve(NAME);
    }

    /** Whether the model has a project rules file. */
    static boolean exists(IArchimateModel model) {
        Path file = file(model);
        return file != null && Files.isRegularFile(file);
    }

    /**
     * The rules to export the model with: {@code override} if given, else its project file if there is one, else
     * {@code preferences}.
     *
     * @throws IllegalArgumentException naming the file and line of a file that is not valid or cannot be read; it never
     *         falls back to other rules
     */
    static KeywordRules resolve(IArchimateModel model, Path override, Supplier<KeywordRules> preferences) {
        Path file = override != null ? override : exists(model) ? file(model) : null;
        return file == null ? preferences.get() : read(file);
    }

    static KeywordRules read(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read the formatting rules " + file + ": " + e.getMessage(), e);
        }
        try {
            return KeywordRules.parseFile(text);
        } catch (IllegalArgumentException e) {
            // the parser says "Line 3: ..."
            throw new IllegalArgumentException("The formatting rules " + file + " are not valid. " + e.getMessage(), e);
        }
    }

    /** Writes the rules as the project file of the model. */
    static Path save(IArchimateModel model, KeywordRules rules) throws IOException {
        Path file = file(model);
        if (file == null) {
            throw new IOException("Save the model first: the rules go next to its file.");
        }
        Files.writeString(file, rules.formatFile(), StandardCharsets.UTF_8);
        return file;
    }
}
