package io.github.p3chys.documentationarchi.ui;

import java.io.IOException;

import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.StorageException;

/**
 * The Confluence password, kept in Eclipse's secure storage (encrypted with the operating system's help), never in
 * the plain preferences. Without a secure storage it is only kept until Archi closes.
 */
final class Secrets {

    private static final String NODE = Activator.ID + "/confluence";
    private static final String PASSWORD = "password";
    /** Used when there is no secure storage. */
    private static String remembered = "";

    private Secrets() {
    }

    static String confluencePassword() {
        try {
            String stored = Store.get();
            return stored != null ? stored : remembered;
        } catch (Exception | LinkageError e) { // no secure storage bundle in this Archi, or it cannot be read
            Activator.log("The Confluence password cannot be read from the secure storage", e);
            return remembered;
        }
    }

    /** @return false if it is only kept until Archi closes */
    static boolean confluencePassword(String password) {
        remembered = password;
        try {
            return Store.put(password);
        } catch (Exception | LinkageError e) {
            Activator.log("The Confluence password cannot be kept in the secure storage", e);
            return false;
        }
    }

    /** Apart, so that an Archi without the secure storage bundle fails here, where it is caught. */
    private static final class Store {

        static String get() throws StorageException {
            ISecurePreferences root = SecurePreferencesFactory.getDefault();
            return root == null ? null : root.node(NODE).get(PASSWORD, null);
        }

        static boolean put(String password) throws StorageException, IOException {
            ISecurePreferences root = SecurePreferencesFactory.getDefault();
            if (root == null) {
                return false;
            }
            if (password.isEmpty()) {
                root.node(NODE).remove(PASSWORD);
            } else {
                root.node(NODE).put(PASSWORD, password, true);
            }
            root.flush();
            return true;
        }
    }
}
