package com.shurik.droidzebra;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;

/**
 * Puts the engine's data files (evaluation patterns and opening book) into
 * the files directory, where the native engine reads them.
 * <p>
 * The native side (zeGlobalInit) unpacks book.cmp.z into book.bin and then
 * deletes book.cmp.z, so after the first start only book.bin is left. The
 * bundled files are only copied (and the book unpacked) again when they may
 * have changed - i.e. when {@link GameContext#assetVersion()} differs from
 * the version recorded last time, such as after an app update - or when a
 * file is missing.
 */
final class EngineAssets {

    static final String PATTERNS_FILE = "coeffs2.bin";
    static final String BOOK_FILE = "book.bin";
    static final String BOOK_FILE_COMPRESSED = "book.cmp.z";
    static final String VERSION_FILE = "assets.version";

    private EngineAssets() {
    }

    static void prepare(GameContext context) {
        File dir = context.getFilesDir();
        File pattern = new File(dir, PATTERNS_FILE);
        File book = new File(dir, BOOK_FILE);
        File bookCompressed = new File(dir, BOOK_FILE_COMPRESSED);
        File versionFile = new File(dir, VERSION_FILE);

        String version = context.assetVersion();
        boolean upToDate = version != null
                && version.equals(readVersion(versionFile))
                && pattern.exists()
                && (book.exists() || bookCompressed.exists());
        if (upToDate) {
            return;
        }

        // the native side unpacks the freshly copied book.cmp.z into it again
        book.delete();
        copyAsset(context, PATTERNS_FILE, pattern);
        copyAsset(context, BOOK_FILE_COMPRESSED, bookCompressed);
        if (!pattern.exists() || !bookCompressed.exists()) {
            deleteAll(dir);
            throw new IllegalStateException("Kann coeeffs.bin und book nicht finden");
        }
        writeVersion(versionFile, version);
    }

    /** Removes everything prepare() creates, so the next start copies it all again. */
    static void deleteAll(File dir) {
        new File(dir, PATTERNS_FILE).delete();
        new File(dir, BOOK_FILE).delete();
        new File(dir, BOOK_FILE_COMPRESSED).delete();
        new File(dir, VERSION_FILE).delete();
    }

    private static void copyAsset(GameContext context, String assetPath, File target) {
        try (InputStream in = context.open(assetPath); OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Datei konnte nicht geladen werden: " + assetPath, e);
        }
    }

    private static String readVersion(File versionFile) {
        if (!versionFile.exists()) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(versionFile))) {
            return reader.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    // Without a known version nothing is recorded, so the files are copied
    // again on every start (the previous behavior).
    private static void writeVersion(File versionFile, String version) {
        if (version == null) {
            versionFile.delete();
            return;
        }
        try (Writer writer = new FileWriter(versionFile)) {
            writer.write(version);
        } catch (IOException e) {
            versionFile.delete();
        }
    }
}
