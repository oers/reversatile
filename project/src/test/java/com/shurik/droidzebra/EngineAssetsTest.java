package com.shurik.droidzebra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class EngineAssetsTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private FakeContext context;
    private File dir;

    private static class FakeContext implements GameContext {
        private final File filesDir;
        String version;
        int opens = 0;

        FakeContext(File filesDir, String version) {
            this.filesDir = filesDir;
            this.version = version;
        }

        @Override
        public InputStream open(String fromAssetPath) throws IOException {
            opens++;
            return new ByteArrayInputStream(("asset " + fromAssetPath).getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public File getFilesDir() {
            return filesDir;
        }

        @Override
        public String assetVersion() {
            return version;
        }
    }

    @Before
    public void setUp() throws IOException {
        dir = folder.newFolder("files");
        context = new FakeContext(dir, "v1");
    }

    // What zeGlobalInit does after prepare(): unpack book.cmp.z into
    // book.bin, then delete book.cmp.z.
    private void simulateNativeUnpack() throws IOException {
        assertTrue(new File(dir, EngineAssets.BOOK_FILE_COMPRESSED).delete());
        Files.write(new File(dir, EngineAssets.BOOK_FILE).toPath(), "unpacked".getBytes(StandardCharsets.UTF_8));
    }

    private String recordedVersion() throws IOException {
        return new String(Files.readAllBytes(new File(dir, EngineAssets.VERSION_FILE).toPath()),
                StandardCharsets.UTF_8);
    }

    @Test
    public void firstStartCopiesBothAssetsAndRecordsTheVersion() throws IOException {
        EngineAssets.prepare(context);

        assertEquals(2, context.opens);
        assertTrue(new File(dir, EngineAssets.PATTERNS_FILE).exists());
        assertTrue(new File(dir, EngineAssets.BOOK_FILE_COMPRESSED).exists());
        assertEquals("v1", recordedVersion());
    }

    // The bug: book.cmp.z is gone after the native unpack, so every later
    // start used to copy both assets and unpack the book again.
    @Test
    public void laterStartOfTheSameVersionCopiesNothing() throws IOException {
        EngineAssets.prepare(context);
        simulateNativeUnpack();

        EngineAssets.prepare(context);

        assertEquals(2, context.opens);
        assertTrue(new File(dir, EngineAssets.BOOK_FILE).exists());
        assertFalse(new File(dir, EngineAssets.BOOK_FILE_COMPRESSED).exists());
    }

    @Test
    public void appUpdateCopiesAgainAndDropsTheOldBook() throws IOException {
        EngineAssets.prepare(context);
        simulateNativeUnpack();

        context.version = "v2";
        EngineAssets.prepare(context);

        assertEquals(4, context.opens);
        assertFalse("the stale unpacked book must go, so the new one gets unpacked",
                new File(dir, EngineAssets.BOOK_FILE).exists());
        assertTrue(new File(dir, EngineAssets.BOOK_FILE_COMPRESSED).exists());
        assertEquals("v2", recordedVersion());
    }

    @Test
    public void missingFileIsCopiedAgain() throws IOException {
        EngineAssets.prepare(context);
        simulateNativeUnpack();
        assertTrue(new File(dir, EngineAssets.PATTERNS_FILE).delete());

        EngineAssets.prepare(context);

        assertEquals(4, context.opens);
        assertTrue(new File(dir, EngineAssets.PATTERNS_FILE).exists());
    }

    @Test
    public void unknownVersionCopiesOnEveryStart() throws IOException {
        context.version = null;
        EngineAssets.prepare(context);
        simulateNativeUnpack();

        EngineAssets.prepare(context);

        assertEquals(4, context.opens);
        assertFalse(new File(dir, EngineAssets.VERSION_FILE).exists());
    }
}
