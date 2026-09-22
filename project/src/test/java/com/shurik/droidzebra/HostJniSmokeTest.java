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
    // Matches WthorReplayTest's old default game count, now that the
    // exhaustive-corpus coverage lives here instead (see
    // allWthorGamesUndoRedoRoundTripCleanly).
    private static final int WTHOR_GAME_LIMIT = 200;
    private static final long WAIT_TIMEOUT_MILLIS = 20_000;

    // WThor game #4 from the bundled database: a real, complete (60-ply)
    // tournament game with no forced pass anywhere, confirmed by bulk-
    // replaying every game's move list and checking the resulting move
    // sequence for "--" (see git history for how this index was picked).
    // Deliberately passless, so this test broadly validates make_move/
    // undo_turn/redo_turn on real hardware independent of the forced-pass
    // case exercised separately below.
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
        verifyUndoRedoRoundTrip(engine, file, PASSLESS_GAME_INDEX);
    }

    // Same corpus WthorReplayTest#replayGamesWithUndoAndRedo used to walk
    // in full (up to 200 games) through the real app on an emulator. That
    // test now only exercises a handful of curated games, since what it's
    // actually proving at that point is the Android integration layer
    // (Activity/UI thread/GameStateBoardModel), not engine correctness -
    // this test is what covers engine correctness (make_move/undo_turn/
    // redo_turn) across the whole bundled corpus now, and does so far
    // more cheaply since it needs no emulator: real arm64 hardware, no
    // virtualization, seconds per game.
    @Test
    public void allWthorGamesUndoRedoRoundTripCleanly() throws Exception {
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
        int gameCount = littleEndianInt(file, 4);
        int gamesToRun = Math.min(gameCount, WTHOR_GAME_LIMIT);

        ZebraEngine engine = ZebraEngine.get(new TestGameContext(filesDir.getRoot(), assetsDir));
        for (int gameIndex = 0; gameIndex < gamesToRun; gameIndex++) {
            try {
                verifyUndoRedoRoundTrip(engine, file, gameIndex);
            } catch (AssertionError e) {
                throw new AssertionError("WThor game " + gameIndex + ": " + e.getMessage(), e);
            }
        }
    }

    // Bulk-replays one WThor game, then undoes it all the way back to the
    // standard starting position and redoes it all the way forward again,
    // one ply at a time, diffing the *whole* board (not just move-sequence
    // text) at every step against boards captured during the undo pass -
    // catches a redo that gets its own square right but computes the
    // wrong flip set, not just a wrong final square. Works for any game
    // regardless of how many forced passes it contains.
    private static void verifyUndoRedoRoundTrip(ZebraEngine engine, byte[] wthorFile, int gameIndex)
            throws InterruptedException {
        String moveSequence = decodeGameMoveText(wthorFile, gameIndex);
        int realMoveCount = moveSequence.length() / 2;

        // Captured from the engine's own fresh-game initialization, before
        // any moves - the real standard starting position (2 black + 2
        // white center discs), not an empty board. Used below (after
        // undoing the whole game) to check whether unmake_move() actually
        // restores this exact position.
        byte[][] pristineStart = capturePristineStartBoard(engine);
        GameState gameState = replayGame(engine, wthorFile, gameIndex);

        waitForMoveSequence(gameState, moveSequence);
        byte[][] finalBoard = captureBoard(gameState);

        // Undo all the way back to the empty starting position, one ply at
        // a time, capturing the board after each undo - gives a known-good
        // expected board for every intermediate ply, not just the final
        // position (finalBoard) and the empty start, so a redo bug that
        // only shows up mid-game (not just at the very end) is still
        // caught below.
        Map<Integer, byte[][]> boardsByPly = new HashMap<>();
        boardsByPly.put(realMoveCount, finalBoard);
        for (int ply = realMoveCount; ply >= 1; ply--) {
            String expectedAfterUndo = moveSequence.substring(0, (ply - 1) * 2);
            undoOnce(engine, gameState, expectedAfterUndo, ply);
            boardsByPly.put(ply - 1, captureBoard(gameState));
        }

        // If undoing all the way doesn't restore the exact standard
        // starting position, unmake_move() itself already leaves the board
        // wrong before redo is even involved.
        String startDiff = diffBoards(pristineStart, captureBoard(gameState));
        assertEquals("board after undoing all the way to ply 0 does not match "
                + "the standard starting position: " + startDiff, "<no differing squares>", startDiff);

        // Redo all the way forward again, checking both the redone square's
        // own color and the *whole* board against the same ply captured
        // during the undo pass above (already proven correct by the
        // pristine-start check).
        for (int ply = 1; ply <= realMoveCount; ply++) {
            String expectedAfterRedo = moveSequence.substring(0, ply * 2);
            redoOnce(engine, gameState, expectedAfterRedo, ply);
            byte[][] expectedBoard = boardsByPly.get(ply);
            String redoDiff = diffBoards(expectedBoard, captureBoard(gameState));
            assertEquals("redo of ply " + ply + " diverges from the board captured for the "
                    + "same ply during the undo pass: " + redoDiff, "<no differing squares>", redoDiff);
        }

        assertEquals(moveSequence, removePasses(gameState.getMoveSequenceAsString()));
        String finalDiff = diffBoards(finalBoard, captureBoard(gameState));
        assertEquals("board after redoing back to the end does not match the original "
                + "playthrough: " + finalDiff, "<no differing squares>", finalDiff);
    }

    // This was the known, long-standing native bug documented on PR #96 as
    // testRedoAcrossPass and reproduced independently via bulk WThor replay
    // in WthorReplayTest#replayGamesWithUndoAndRedo (there @Suppress'd):
    // undoing across a forced pass and redoing back used to leave several
    // board squares permanently wrong. WThor game 0 (bundled in
    // WTH_2025.wtb) is the exact same game that bug was root-caused on
    // (Black has no legal move once White plays g7; White then plays the
    // final move a5).
    //
    // Root cause (found using this same plain-JVM test as a fast, no-
    // emulator repro harness - seconds per iteration instead of an
    // emulator boot plus a full androidTest run): _droidzebra_redo_turn
    // (droidzebra-jni.c) stopped exactly at its numeric disks_played
    // target, even when that landed on a forced-pass position - unlike
    // _droidzebra_undo_turn, which symmetrically absorbs a pass into the
    // same call via its human_can_move loop. The pass was then left to the
    // main game loop's own automatic pass handling, which runs
    // asynchronously after redo_turn returns and races the next
    // UI_EVENT_REDO: if that arrived before the engine reached
    // ES_USER_INPUT_WAIT again, ZebraEngine's state guard silently dropped
    // it, and the move right after the pass never got redone. Fixed by
    // making _droidzebra_redo_turn absorb a trailing forced pass itself,
    // symmetric with undo, so a single redo() call is self-contained again.
    @Test
    public void undoRedoAcrossForcedPassWorksCorrectly() throws Exception {
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
        // the file itself, only inferred during replay. Sanity-check the
        // known pass is still there before handing off to the shared
        // full-cycle verification below - if the bundled WThor database
        // ever changes, fail loudly here instead of silently testing a
        // now-passless game that wouldn't exercise this bug at all.
        int gameIndex = 0;
        byte[] file = Files.readAllBytes(wthorFile.toPath());
        String moveSequence = decodeGameMoveText(file, gameIndex);

        ZebraEngine engine = ZebraEngine.get(new TestGameContext(filesDir.getRoot(), assetsDir));
        GameState gameState = replayGame(engine, file, gameIndex);
        waitForMoveSequence(gameState, moveSequence);
        assertEquals("WThor game " + gameIndex + " no longer contains the known forced pass "
                        + "this test was written against - the bundled " + WTHOR_FILE_NAME + " may have changed",
                true, gameState.getMoveSequenceAsString().contains("--"));

        verifyUndoRedoRoundTrip(engine, file, gameIndex);
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

    private static int littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | ((bytes[offset + 3] & 0xff) << 24);
    }
}
