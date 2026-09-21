package com.shurik.droidzebra;

import static de.earthlingz.oerszebra.GameSettingsConstants.FUNCTION_HUMAN_VS_HUMAN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

// Plain-JVM test: no Android framework, no emulator/device. Loads the real
// droidzebra-jni.c JNI entry points (via ZebraEngine's declared native
// methods) from a host-compiled libdroidzebra.so (see project/hostjni/,
// built separately from the Android NDK build). The point is to exercise
// the actual native engine on whatever CPU this JVM runs on - in CI, a
// genuine arm64 GitHub-hosted runner, with no emulator/KVM involved at all
// since this is just a normal process running directly on its own host CPU.
//
// Skips cleanly (Assume) if the host .so hasn't been built - e.g. the main
// x86 CI workflow's plain `./gradlew test` run, which never builds it -
// so this can't break that already-green suite. The arm64 CI job builds
// the .so first specifically so this test actually runs there.
public class HostJniSmokeTest {

    @Rule
    public TemporaryFolder filesDir = new TemporaryFolder();

    private static File findNativeLib() {
        String override = System.getProperty("droidzebra.native.lib");
        if (override != null) {
            File f = new File(override);
            return f.isFile() ? f : null;
        }
        String[] candidates = {
                "hostjni/build/libdroidzebra.so",
                "../hostjni/build/libdroidzebra.so",
                "project/hostjni/build/libdroidzebra.so",
        };
        for (String candidate : candidates) {
            File f = new File(candidate);
            if (f.isFile()) {
                return f;
            }
        }
        return null;
    }

    private static File findAssetsDir() {
        String[] candidates = {"src/main/assets", "project/src/main/assets"};
        for (String candidate : candidates) {
            File f = new File(candidate);
            if (f.isDirectory()) {
                return f;
            }
        }
        return null;
    }

    private static class TestGameContext implements GameContext {
        private final File filesDir;
        private final File assetsDir;

        TestGameContext(File filesDir, File assetsDir) {
            this.filesDir = filesDir;
            this.assetsDir = assetsDir;
        }

        @Override
        public InputStream open(String fromAssetPath) throws IOException {
            return new FileInputStream(new File(assetsDir, fromAssetPath));
        }

        @Override
        public File getFilesDir() {
            return filesDir;
        }
    }

    private static String removePasses(String moves) {
        return moves.replace("--", "");
    }

    @Test
    public void makeMoveFlipsDiscsOnRealHardware() throws Exception {
        File nativeLib = findNativeLib();
        Assume.assumeTrue("host libdroidzebra not built (run project/hostjni's Makefile first) - "
                + "skipping, this is expected on workflows that don't build it", nativeLib != null);
        File assetsDir = findAssetsDir();
        Assume.assumeTrue("could not locate project/src/main/assets from " + new File(".").getAbsolutePath(),
                assetsDir != null);

        // ZebraEngine's own static initializer does
        // System.loadLibrary("droidzebra"), which resolves via
        // java.library.path (configured in build.gradle to point at this
        // same nativeLib's directory) - not by path we could pass here at
        // runtime, so this first active use of ZebraEngine is what
        // actually loads it.
        ZebraEngine engine = ZebraEngine.get(new TestGameContext(filesDir.getRoot(), assetsDir));

        AtomicReference<GameState> gameStateRef = new AtomicReference<>();
        engine.newGameBlocking(
                new EngineConfig(FUNCTION_HUMAN_VS_HUMAN, 1, 1, 0,
                        false, null, false, false, false, 0, 0, 0),
                gameStateRef::set);

        long deadline = System.currentTimeMillis() + 20_000;
        while (gameStateRef.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        GameState gameState = gameStateRef.get();
        if (gameState == null) {
            fail("Engine never delivered a GameState - newGameBlocking() didn't reach ES_PLAY_IN_PROGRESS");
        }

        // f5 is a real move from a WThor tournament game this same engine
        // has replayed correctly many times over in WthorReplayTest - a
        // known-legal Black opening move in this app's coordinate system,
        // not a guess. makeMove() silently no-ops until the engine reaches
        // ES_USER_INPUT_WAIT (asynchronously, on the native engine thread),
        // so retry it - same pattern WthorReplayTest's undo/redo helpers
        // use for the same reason.
        Move f5 = new Move(5, 4);
        deadline = System.currentTimeMillis() + 20_000;
        while (!"f5".equals(removePasses(gameState.getMoveSequenceAsString()))
                && System.currentTimeMillis() < deadline) {
            try {
                engine.makeMove(gameState, f5);
            } catch (InvalidMove e) {
                fail("f5 should be a legal opening move: " + e);
            }
            Thread.sleep(10);
        }

        assertEquals("f5", removePasses(gameState.getMoveSequenceAsString()));
        assertEquals(1, gameState.getDisksPlayed());
        // The standard start has White on d4 and e5. f5 is in line with
        // e5 (white) and d5 (black) along row 5, so it flips e5 to black -
        // White drops from its starting 2 discs to 1. Proves the actual
        // flip computation (make_move/DoFlips) ran correctly, not just
        // that a disc got placed.
        assertEquals(1, gameState.getWhitePlayer().getDiscCount());
    }
}
