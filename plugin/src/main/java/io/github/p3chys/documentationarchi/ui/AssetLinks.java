package io.github.p3chys.documentationarchi.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.gef.commands.Command;

import com.archimatetool.editor.model.commands.EObjectFeatureCommand;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimatePackage;
import com.archimatetool.model.IDocumentable;
import com.archimatetool.model.ITextContent;

import io.github.p3chys.documentationarchi.doc.Assets;

/**
 * "Add Asset...": brings a file into the model's Assets folder (unless it is there already) and writes its
 * {@code [[asset:path]]} into a description, which the export then resolves (see {@link Assets}).
 */
final class AssetLinks {

    private AssetLinks() {
    }

    /** The text that holds a description of the object: documentation, a note's text, the model's purpose. */
    static EAttribute descriptionOf(EObject o) {
        if (o instanceof IArchimateModel) {
            return IArchimatePackage.Literals.ARCHIMATE_MODEL__PURPOSE;
        }
        if (o instanceof IDocumentable) {
            return IArchimatePackage.Literals.DOCUMENTABLE__DOCUMENTATION;
        }
        return o instanceof ITextContent ? IArchimatePackage.Literals.TEXT_CONTENT__CONTENT : null;
    }

    /**
     * The Assets folder next to the model file, whatever its case; Assets/ when there is none yet.
     *
     * @throws Assets.LinkedFolderException when it links to another folder
     */
    static Path assetsFolder(Path modelFolder) throws IOException {
        Path folder = Assets.folderIn(modelFolder);
        return folder != null ? folder : modelFolder.resolve(Assets.FOLDER);
    }

    /**
     * The path to write after {@code asset:} for a chosen file. A file inside the Assets folder stays where it is;
     * any other is copied into it, under a new name if a different file (or a link) already has its name.
     */
    static String bringIn(Path assets, Path chosen) throws IOException {
        Files.createDirectories(assets);
        if (!assets.equals(assetsFolder(assets.getParent()))) {
            throw new IOException(assets + " is not the Assets folder of " + assets.getParent());
        }
        Path real = chosen.toRealPath();
        Path folder = assets.toRealPath();
        if (!real.startsWith(folder)) {
            String name = chosen.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            String extension = dot > 0 ? name.substring(dot) : "";
            Path target = folder.resolve(name);
            for (int i = 2; Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                    && (Files.isSymbolicLink(target) || !sameContent(target, real)); i++) {
                target = folder.resolve(base + "-" + i + extension);
            }
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                Files.copy(real, target);
            }
            real = target;
        }
        return folder.relativize(real).toString().replace('\\', '/');
    }

    private static boolean sameContent(Path a, Path b) throws IOException {
        if (Files.size(a) != Files.size(b)) {
            return false;
        }
        try (InputStream x = Files.newInputStream(a); InputStream y = Files.newInputStream(b)) {
            byte[] bx = new byte[65536];
            byte[] by = new byte[65536];
            for (int n; (n = x.readNBytes(bx, 0, bx.length)) > 0;) {
                if (y.readNBytes(by, 0, by.length) != n || !Arrays.equals(bx, 0, n, by, 0, n)) {
                    return false;
                }
            }
            return true;
        }
    }

    /** The description with one reference per line added at its end. */
    static String appended(String description, List<String> paths) {
        StringBuilder text = new StringBuilder(description == null ? "" : description);
        for (String path : paths) {
            if (text.length() > 0 && text.charAt(text.length() - 1) != '\n') {
                text.append('\n');
            }
            text.append("[[asset:").append(path).append("]]");
        }
        return text.toString();
    }

    /** Adds the references to the object's description, as one command that Undo takes back; null for none. */
    static Command add(EObject target, List<String> paths) {
        EAttribute feature = descriptionOf(target);
        if (feature == null || paths.isEmpty()) {
            return null;
        }
        return new EObjectFeatureCommand(paths.size() == 1 ? "Add Asset" : "Add Assets", target, feature,
                appended((String) target.eGet(feature), paths));
    }
}
