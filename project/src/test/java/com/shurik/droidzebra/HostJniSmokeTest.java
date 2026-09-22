package com.shurik.droidzebra;

import static de.earthlingz.oerszebra.GameSettingsConstants.FUNCTION_HUMAN_VS_HUMAN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
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

    // WThor game database bundled for WthorReplayTest (androidTest) - reused
    // here as a source of real, exhaustively-legal tournament games instead
    // of hand-written move sequences, same rationale as f5 below.
    private static File findWthorFile() {
        String[] candidates = {
                "src/androidTest/" + WTHOR_FILE_NAME,
                "project/src/androidTest/" + WTHOR_FILE_NAME,
        };
        for (String candidate : candidates) {
            File f = new File(candidate);
            if (f.isFile()) {
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
        // OnGameStateReadyListener's method has a default body, so it isn't
        // a functional interface (no abstract method) - a method reference
        // doesn't compile against it, hence the anonymous class.
        engine.newGameBlocking(
                new EngineConfig(FUNCTION_HUMAN_VS_HUMAN, 1, 1, 0,
                        false, null, false, false, false, 0, 0, 0),
                new ZebraEngine.OnGameStateReadyListener() {
                    @Override
                    public void onGameStateReady(GameState gameState) {
                        gameStateRef.set(gameState);
                    }
                });

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

    // ------------------------------------------------------------------
    // Full-game replay + undo/redo, on the same real arm64 hardware, no
    // emulator. Exercises make_move/DoFlips/unmake_move/UndoFlips across a
    // whole real tournament game instead of a single move, since the app
    // was just resynced from upstream zebra and a single-move smoke test
    // alone doesn't say much about the undo/redo C code specifically.
    // ------------------------------------------------------------------

    private static final String WTHOR_FILE_NAME = "WTH_2025.wtb";
    private static final int WTHOR_HEADER_SIZE = 16;
    private static final int WTHOR_GAME_HEADER_SIZE = 8;
    private static final int WTHOR_GAME_RECORD_SIZE = 68;
    private static final long WAIT_TIMEOUT_MILLIS = 20_000;

    // WThor game #4 from the bundled database: a real, complete (60-ply)
    // tournament game with no forced pass anywhere, confirmed by bulk-
    // replaying every game's move list and checking the resulting move
    // sequence for "--" (see git history for how this index was picked).
    // Deliberately passless, so this test broadly validates make_move/
    // undo_turn/redo_turn on real hardware without also tripping the
    // separately-tracked, already-documented undo/redo-across-a-forced-
    // pass bug exercised (and @Ignore'd) below - that bug isn't specific
    // to this sync and re-triggering it here would just block every PR
    // on a long-standing, known issue instead of validating the sync.
    private static final int PASSLESS_GAME_INDEX = 4;

    @Test
    public void playsFullGameThenUndoesAndRedoesCleanly() throws Exception {
        File nativeLib = findNativeLib();
        Assume.assumeTrue("host libdroidzebra not built (run project/hostjni's Makefile first) - "
                + "skipping, this is expected on workflows that don't build it", nativeLib != null);
        File assetsDir = findAssetsDir();
        Assume.assumeTrue("could not locate project/src/main/assets from " + new File(".").getAbsolutePath(),
                assetsDir != null);
        File wthorFile = findWthorFile();
        Assume.assumeTrue("could not locate " + WTHOR_FILE_NAME + " from " + new File(".").getAbsolutePath(),
                wthorFile != null);

        byte[] file = Files.readAllBytes(wthorFile.toPath());
        String moveSequence = decodeGameMoveText(file, PASSLESS_GAME_INDEX);
        // Sanity-check this really is the passless, complete game this test
        // was written against - if the bundled WThor database ever changes,
        // fail loudly here instead of silently testing a different (maybe
        // shorter, maybe pass-containing) game than intended.
        assertEquals("WThor game " + PASSLESS_GAME_INDEX + " is not the expected 60-ply, passless game "
                        + "this test was written against - the bundled " + WTHOR_FILE_NAME + " may have changed",
                60, moveSequence.length() / 2);

        ZebraEngine engine = ZebraEngine.get(new TestGameContext(filesDir.getRoot(), assetsDir));
        // Captured from the engine's own fresh-game initialization, before
        // any moves - the real standard starting position (2 black + 2
        // white center discs), not an empty board. Used below (after
        // undoing the whole game) to check whether unmake_move() actually
        // restores this exact position.
        byte[][] pristineStart = capturePristineStartBoard(engine);
        GameState gameState = replayGame(engine, file, PASSLESS_GAME_INDEX);

        waitForMoveSequence(gameState, moveSequence);
        assertEquals(60, gameState.getDisksPlayed());
        byte[][] finalBoard = captureBoard(gameState);

        // Undo all the way back to the empty starting position, one ply at
        // a time, capturing the board after each undo - gives a known-good
        // expected board for every intermediate ply, not just the final
        // position (finalBoard) and the empty start, so a redo bug that
        // only shows up mid-game (not just at the very end) is still
        // caught below.
        Map<Integer, byte[][]> boardsByPly = new HashMap<>();
        boardsByPly.put(60, finalBoard);
        for (int ply = 60; ply >= 1; ply--) {
            String expectedAfterUndo = moveSequence.substring(0, (ply - 1) * 2);
            undoOnce(engine, gameState, expectedAfterUndo, ply);
            boardsByPly.put(ply - 1, captureBoard(gameState));
        }

        // If undoing all 60 plies doesn't restore the exact standard
        // starting position, unmake_move() itself already leaves the board
        // wrong before redo is even involved.
        String startDiff = diffBoards(pristineStart, captureBoard(gameState));
        assertEquals("board after undoing all the way to ply 0 does not match "
                + "the standard starting position: " + startDiff, "<no differing squares>", startDiff);

        // Redo all the way forward again, checking both the redone square's
        // own color and the *whole* board against the same ply captured
        // during the undo pass above (already proven correct by the
        // pristine-start check) - catches a redo that places its own disc
        // right but computes the wrong flip set, not just a wrong final
        // square.
        for (int ply = 1; ply <= 60; ply++) {
            String expectedAfterRedo = moveSequence.substring(0, ply * 2);
            redoOnce(engine, gameState, expectedAfterRedo, ply);
            byte[][] expectedBoard = boardsByPly.get(ply);
            String redoDiff = diffBoards(expectedBoard, captureBoard(gameState));
            assertEquals("redo of ply " + ply + " diverges from the board captured for the "
                    + "same ply during the undo pass: " + redoDiff, "<no differing squares>", redoDiff);
        }

        assertEquals(moveSequence, removePasses(gameState.getMoveSequenceAsString()));
        assertEquals(60, gameState.getDisksPlayed());
        String finalDiff = diffBoards(finalBoard, captureBoard(gameState));
        assertEquals("board after redoing back to the end does not match the original "
                + "playthrough: " + finalDiff, "<no differing squares>", finalDiff);
    }

    // Same known, already-documented native bug as PR #96's testRedoAcrossPass
    // and WthorReplayTest#replayGamesWithUndoAndRedo (there @Suppress'd for
    // the identical reason - see that file's own extensive write-up): undoing
    // across a forced pass and redoing back leaves several board squares
    // permanently wrong. WThor game 0 (bundled in WTH_2025.wtb) is the exact
    // same known-bad game already root-caused there (Black has no legal move
    // once White plays g7; White then plays the final move a5).
    //
    // Kept here, @Ignore'd, purely so a future debugging session can
    // re-enable this exact repro without an emulator at all: this plain-JVM
    // path builds and iterates in seconds (host compiler + JUnitCore), not
    // an emulator boot plus a full androidTest run, and runs directly on
    // whatever real CPU it's built for - not to re-litigate or attempt to
    // fix the bug in this change.
    @Ignore("Known native undo/redo-across-forced-pass bug, see PR #96 testRedoAcrossPass "
            + "and WthorReplayTest#replayGamesWithUndoAndRedo for the full write-up. Kept here "
            + "as a fast, no-emulator repro harness for a future debugging session.")
    @Test
    public void undoRedoAcrossForcedPassReproducesKnownNativeBug() throws Exception {
        File nativeLib = findNativeLib();
        Assume.assumeTrue("host libdroidzebra not built (run project/hostjni's Makefile first) - "
                + "skipping, this is expected on workflows that don't build it", nativeLib != null);
        File assetsDir = findAssetsDir();
        Assume.assumeTrue("could not locate project/src/main/assets from " + new File(".").getAbsolutePath(),
                assetsDir != null);
        File wthorFile = findWthorFile();
        Assume.assumeTrue("could not locate " + WTHOR_FILE_NAME + " from " + new File(".").getAbsolutePath(),
                wthorFile != null);

        // The bundled WThor file only ever records real moves - a forced
        // pass isn't something a player "chose", so it's never encoded in
        // the file itself, only inferred during replay. moveSequence here
        // is deliberately the passless, real-moves-only text (same as
        // decodeGameMoveText is used elsewhere in this class): the sanity
        // check below for the known pass has to look at the engine's own
        // replayed sequence instead, not this raw text.
        int gameIndex = 0;
        byte[] file = Files.readAllBytes(wthorFile.toPath());
        String moveSequence = decodeGameMoveText(file, gameIndex);

        ZebraEngine engine = ZebraEngine.get(new TestGameContext(filesDir.getRoot(), assetsDir));
        byte[][] pristineStart = capturePristineStartBoard(engine);
        GameState gameState = replayGame(engine, file, gameIndex);
        waitForMoveSequence(gameState, moveSequence);
        assertEquals("WThor game " + gameIndex + " no longer contains the known forced pass "
                        + "this test was written against - the bundled " + WTHOR_FILE_NAME + " may have changed",
                true, gameState.getMoveSequenceAsString().contains("--"));
        byte[][] finalBoard = captureBoard(gameState);

        int plyCount = moveSequence.length() / 2;
        for (int ply = plyCount; ply >= 1; ply--) {
            String expectedAfterUndo = moveSequence.substring(0, (ply - 1) * 2);
            undoOnce(engine, gameState, expectedAfterUndo, ply);
        }
        String startDiff = diffBoards(pristineStart, captureBoard(gameState));
        assertEquals("<no differing squares>", startDiff);

        for (int ply = 1; ply <= plyCount; ply++) {
            String expectedAfterRedo = moveSequence.substring(0, ply * 2);
            redoOnce(engine, gameState, expectedAfterRedo, ply);
        }
        String finalDiff = diffBoards(finalBoard, captureBoard(gameState));
        assertEquals("board after redoing across the forced pass back to the end does not "
                + "match the original playthrough: " + finalDiff, "<no differing squares>", finalDiff);
    }

    // ------------------------------------------------------------------
    // Shared helpers for the tests above
    // ------------------------------------------------------------------

    // The real standard starting position (2 black + 2 white center discs),
    // captured from the engine's own fresh-game initialization rather than
    // hand-coded - GameState(int) alone gives an all-EMPTY board, not the
    // actual Othello start, and this way the check below doesn't depend on
    // this test getting the center squares' coordinates/colors right by hand.
    private static byte[][] capturePristineStartBoard(ZebraEngine engine) throws InterruptedException {
        AtomicReference<GameState> gameStateRef = new AtomicReference<>();
        engine.newGameBlocking(
                new EngineConfig(FUNCTION_HUMAN_VS_HUMAN, 1, 1, 0,
                        false, null, false, false, false, 0, 0, 0),
                new ZebraEngine.OnGameStateReadyListener() {
                    @Override
                    public void onGameStateReady(GameState gameState) {
                        gameStateRef.set(gameState);
                    }
                });
        long deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MILLIS;
        while (gameStateRef.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        GameState gameState = gameStateRef.get();
        if (gameState == null) {
            fail("Engine never delivered a fresh GameState to capture the starting position");
        }
        long boardDeadline = System.currentTimeMillis() + WAIT_TIMEOUT_MILLIS;
        while (gameState.getBlackPlayer().getDiscCount() != 2
                || gameState.getWhitePlayer().getDiscCount() != 2) {
            if (System.currentTimeMillis() > boardDeadline) {
                fail("fresh game never reached the standard 2 black + 2 white starting position");
            }
            Thread.sleep(10);
        }
        return captureBoard(gameState);
    }

    private static GameState replayGame(ZebraEngine engine, byte[] wthorFile, int gameIndex)
            throws InterruptedException {
        byte[] moveInts = decodeGameMoveInts(wthorFile, gameIndex);
        AtomicReference<GameState> gameStateRef = new AtomicReference<>();
        engine.newGameBlocking(moveInts, moveInts.length,
                new EngineConfig(FUNCTION_HUMAN_VS_HUMAN, 1, 1, 0,
                        false, null, false, false, false, 0, 0, 0),
                new ZebraEngine.OnGameStateReadyListener() {
                    @Override
                    public void onGameStateReady(GameState gameState) {
                        gameStateRef.set(gameState);
                    }
                });
        long deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MILLIS;
        while (gameStateRef.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        GameState gameState = gameStateRef.get();
        if (gameState == null) {
            fail("Engine never delivered a GameState for WThor game " + gameIndex);
        }
        return gameState;
    }

    private static void waitForMoveSequence(GameState gameState, String expectedMoveSequence)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MILLIS;
        while (!expectedMoveSequence.equals(removePasses(gameState.getMoveSequenceAsString()))
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals("bulk replay did not reach the expected final move sequence",
                expectedMoveSequence, removePasses(gameState.getMoveSequenceAsString()));
    }

    // A single undo()/redo() call is only guaranteed to be picked up once the
    // engine is back in ES_USER_INPUT_WAIT (ZebraEngine#undoMove/redoMove
    // silently no-op otherwise). Firing a second call before the first one's
    // update lands can advance the game by an extra ply instead of retrying
    // the same one (the exact same race WthorReplayTest's identically-named
    // helpers already guard against) - fire once and give it a single
    // generous wait, never retry.
    private static void undoOnce(ZebraEngine engine, GameState gameState, String expectedMoveSequence, int ply)
            throws InterruptedException {
        engine.undoMove(gameState);
        long deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MILLIS;
        while (!expectedMoveSequence.equals(removePasses(gameState.getMoveSequenceAsString()))
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals("undo of ply " + ply + " did not land", expectedMoveSequence,
                removePasses(gameState.getMoveSequenceAsString()));
    }

    private static void redoOnce(ZebraEngine engine, GameState gameState, String expectedMoveSequence, int ply)
            throws InterruptedException {
        engine.redoMove(gameState);
        long deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MILLIS;
        while (!expectedMoveSequence.equals(removePasses(gameState.getMoveSequenceAsString()))
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals("redo of ply " + ply + " did not land", expectedMoveSequence,
                removePasses(gameState.getMoveSequenceAsString()));
    }

    private static byte[][] captureBoard(GameState gameState) {
        ByteBoard board = gameState.getByteBoard();
        int size = board.size();
        byte[][] copy = new byte[size][size];
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                copy[x][y] = board.get(x, y);
            }
        }
        return copy;
    }

    private static String diffBoards(byte[][] expected, byte[][] actual) {
        StringBuilder diff = new StringBuilder();
        for (int x = 0; x < expected.length; x++) {
            for (int y = 0; y < expected[x].length; y++) {
                if (expected[x][y] != actual[x][y]) {
                    if (diff.length() > 0) {
                        diff.append(", ");
                    }
                    diff.append(new Move(x, y).getText())
                            .append(": expected ").append(fieldName(expected[x][y]))
                            .append(", actual ").append(fieldName(actual[x][y]));
                }
            }
        }
        return diff.length() == 0 ? "<no differing squares>" : diff.toString();
    }

    private static String fieldName(byte field) {
        if (field == ZebraEngine.PLAYER_BLACK) {
            return "BLACK";
        }
        if (field == ZebraEngine.PLAYER_WHITE) {
            return "WHITE";
        }
        return "EMPTY";
    }

    private static byte[] decodeGameMoveInts(byte[] file, int gameIndex) {
        int offset = WTHOR_HEADER_SIZE + gameIndex * WTHOR_GAME_RECORD_SIZE;
        byte[] moves = new byte[WTHOR_GAME_RECORD_SIZE - WTHOR_GAME_HEADER_SIZE];
        int count = 0;
        for (int i = offset + WTHOR_GAME_HEADER_SIZE; i < offset + WTHOR_GAME_RECORD_SIZE; i++) {
            int encodedMove = file[i] & 0xff;
            if (encodedMove == 0) {
                continue;
            }
            int row = encodedMove / 10;
            int column = encodedMove % 10;
            moves[count++] = (byte) new Move(column - 1, row - 1).getMoveInt();
        }
        byte[] result = new byte[count];
        System.arraycopy(moves, 0, result, 0, count);
        return result;
    }

    private static String decodeGameMoveText(byte[] file, int gameIndex) {
        int offset = WTHOR_HEADER_SIZE + gameIndex * WTHOR_GAME_RECORD_SIZE;
        StringBuilder moves = new StringBuilder();
        for (int i = offset + WTHOR_GAME_HEADER_SIZE; i < offset + WTHOR_GAME_RECORD_SIZE; i++) {
            int encodedMove = file[i] & 0xff;
            if (encodedMove == 0) {
                continue;
            }
            int row = encodedMove / 10;
            int column = encodedMove % 10;
            moves.append(new Move(column - 1, row - 1).getText());
        }
        return moves.toString();
    }
}
