package de.earthlingz.oerszebra;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static androidx.test.platform.app.InstrumentationRegistry.getArguments;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.content.res.AssetManager;
import android.util.Log;

import com.shurik.droidzebra.Move;
import com.shurik.droidzebra.ZebraEngine;

import de.earthlingz.oerszebra.BoardView.GameStateBoardModel;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class WthorReplayTest extends BasicTest {

    private static final String FILE_NAME = "WTH_2025.wtb";
    private static final int HEADER_SIZE = 16;
    private static final int GAME_HEADER_SIZE = 8;
    private static final int GAME_RECORD_SIZE = 68;
    private static final int LOCAL_GAME_LIMIT = 200;

    // Shallow on purpose: none of these tests assert on evaluation quality,
    // and practice mode (on by default) recomputes evals for every legal
    // move after every human-vs-human position change - including undo/
    // redo and the WThor replay tests' many per-move steps. At a deeper
    // depth those searches piled up enough under CI load to make replay
    // steps miss their wait windows late in a game (seen in CI: WThor
    // redo/move-by-move replay consistently landing one move short right
    // at the end of longer games).
    @Override
    protected String getTestSearchDepth() {
        return "1|1|1";
    }

    @Test
    public void replayGamesFast() throws Exception {
        replayGames(ReplayMode.FAST);
    }

    @Test
    public void replayGamesMoveByMove() throws Exception {
        replayGames(ReplayMode.MOVE_BY_MOVE);
    }

    // This test used to be @Suppress'd for the same long-standing bug
    // written up on PR #96 as testRedoAcrossPass, rediscovered here via
    // bulk WThor replay. Several real, unrelated bugs were found and fixed
    // along the way to get here (see git history):
    //   1. UI_EVENT_REDO was unhandled in droidzebra-jni.c's
    //      post-game-over loop (native, fixed).
    //   2. GameState.getMoveSequenceAsString() returned stale, leftover
    //      moves after an undo shortened the sequence (Java-side display
    //      bug, fixed).
    //   3. Two test-side async-lag bugs in this file's own diagnostics
    //      (settle-wait missing on the post-undo check; pristineStartBoard
    //      captured before GameStateBoardModel's first async update
    //      landed).
    //
    // With all of those fixed, the undo+redo cycle for WThor game 0 was
    // finally bisected cleanly enough to find the actual native bug: this
    // game contains exactly one forced pass (Black has no legal move once
    // White plays g7; White then plays the final move, a5) - confirmed by
    // independently re-simulating this exact game's rules in Python and
    // matching the recorded 31/33 score exactly. Redoing across that pass
    // used to leave several squares (a5, a6, b4, b5, b6, c5, d5) wrong,
    // exactly matching PR #96's description of testRedoAcrossPass.
    //
    // Root cause (found using a plain-JVM host-native-JNI test as a fast,
    // no-emulator repro harness - see HostJniSmokeTest#
    // undoRedoAcrossForcedPassWorksCorrectly): _droidzebra_redo_turn
    // (droidzebra-jni.c) stopped exactly at its numeric disks_played
    // target, even when that landed on a forced-pass position - unlike
    // _droidzebra_undo_turn, which symmetrically absorbs a pass into the
    // same call via its human_can_move loop. The pass was then left to the
    // main game loop's own automatic pass handling, which runs
    // asynchronously after redo_turn returns and raced the next
    // UI_EVENT_REDO: if that arrived before the engine reached
    // ES_USER_INPUT_WAIT again, ZebraEngine's state guard silently dropped
    // it, and the move right after the pass never got redone. Fixed by
    // making _droidzebra_redo_turn absorb a trailing forced pass itself,
    // symmetric with undo, so a single redo() call is self-contained again.
    @Test
    public void replayGamesWithUndoAndRedo() throws Exception {
        replayGames(ReplayMode.UNDO_AND_REDO);
    }

    private void replayGames(ReplayMode mode) throws Exception {
        byte[] file = readAsset(FILE_NAME);
        assertEquals("Unexpected WThor header", HEADER_SIZE, file.length % GAME_RECORD_SIZE);

        int gameCount = littleEndianInt(file, 4);
        assertEquals("Unexpected WThor game count", gameCount,
                (file.length - HEADER_SIZE) / GAME_RECORD_SIZE);

        // Captured before any WThor game is loaded - the app's default
        // fresh-game board, i.e. the standard Othello starting position
        // (4 center discs, everything else empty). Used below (UNDO_AND_REDO
        // only) to check whether undoing all the way back to ply 0 actually
        // restores this exact position, or whether unmake_move() itself
        // already leaves the board subtly wrong before any redo even starts.
        //
        // GameStateBoardModel (what captureBoard() reads) is only populated
        // once the native engine thread fires its first onBoard() callback -
        // BasicTest#init()'s zebra.initialized() check says the activity is
        // up, not that this first callback has already landed. Capturing
        // here without waiting for it raced ahead of that callback in CI,
        // reading default/empty field values at all four center squares
        // instead of the real starting position - which then showed up as
        // "d4/d5/e4/e5 expected EMPTY" in the post-undo diff below, and
        // stayed perfectly reproducible even after adding a settle-wait to
        // that check, because the corruption was in this snapshot itself,
        // not in a transient read of the post-undo board. Wait for the
        // actual starting position (2 black + 2 white discs) first.
        if (mode == ReplayMode.UNDO_AND_REDO) {
            waitForSquareCount(ZebraEngine.PLAYER_BLACK, 2, 5_000);
            waitForSquareCount(ZebraEngine.PLAYER_WHITE, 2, 5_000);
        }
        byte[][] pristineStartBoard = mode == ReplayMode.UNDO_AND_REDO ? captureBoard() : null;

        int[] gameIndices = gameIndicesFor(mode, gameCount);
        for (int gameIndex : gameIndices) {
            String moves = decodeGame(file, gameIndex);
            switch (mode) {
                case FAST:
                    playAndWaitForReplay(moves, gameIndex);
                    break;
                case MOVE_BY_MOVE:
                    playAndWaitMoveByMove(moves, gameIndex);
                    break;
                case UNDO_AND_REDO:
                    playAndWaitForReplay(moves, gameIndex);
                    // GameStateBoardModel (what captureBoard() reads) updates
                    // asynchronously from the raw engine GameState that
                    // playAndWaitForReplay polls on, same lag already noted
                    // below for the post-loop score assertion - wait for it
                    // to actually reach the known WThor score before treating
                    // this snapshot as "the correct board", or a couple of
                    // squares from the tail of the original playthrough can
                    // still be mid-flight and pollute the later diff.
                    waitForGameScore(file, gameIndex);
                    // Captured from the just-completed straight playthrough,
                    // before any undo happens - this is the known-correct
                    // final board (replayGamesFast/MoveByMove confirm this
                    // exact sequence produces the WThor-recorded score every
                    // run), so a mismatch after undo+redo can be pinned down
                    // to specific squares instead of just "score is wrong".
                    byte[][] originalBoard = captureBoard();
                    undoAndRedoGame(moves, gameIndex, file, originalBoard, pristineStartBoard);
                    break;
            }

            // The score shown via zebra.getState() (GameStateBoardModel) is updated
            // asynchronously from the raw engine GameState the wait-for-replay helpers
            // above poll on, so it can still lag behind right after they return -
            // including in FAST mode, which used to skip this and read a stale score.
            waitForGameScore(file, gameIndex);
            assertGameScore(file, gameIndex);
        }
    }

    private byte[][] captureBoard() {
        GameStateBoardModel model = zebra.getState();
        int width = model.getBoardRowWidth();
        int height = model.getBoardHeight();
        byte[][] board = new byte[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                board[x][y] = model.getFieldByte(x, y);
            }
        }
        return board;
    }

    private String diffBoards(byte[][] expected, byte[][] actual) {
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

    private String fieldName(byte field) {
        if (field == ZebraEngine.PLAYER_BLACK) {
            return "BLACK";
        }
        if (field == ZebraEngine.PLAYER_WHITE) {
            return "WHITE";
        }
        return "EMPTY";
    }

    private void assertGameScore(byte[] file, int gameIndex) {
        int offset = HEADER_SIZE + gameIndex * GAME_RECORD_SIZE;
        int expectedBlackScore = file[offset + 6] & 0xff;
        int expectedWhiteScore = 64 - expectedBlackScore;
        assertEquals("WThor black score for game " + gameIndex,
                expectedBlackScore, zebra.getState().getBlackScore());
        assertEquals("WThor white score for game " + gameIndex,
                expectedWhiteScore, zebra.getState().getWhiteScore());
    }

    private void waitForGameScore(byte[] file, int gameIndex) throws InterruptedException {
        int offset = HEADER_SIZE + gameIndex * GAME_RECORD_SIZE;
        int expectedBlackScore = file[offset + 6] & 0xff;
        int expectedWhiteScore = 64 - expectedBlackScore;
        long timeout = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < timeout) {
            if (zebra.getState().getBlackScore() == expectedBlackScore
                    && zebra.getState().getWhiteScore() == expectedWhiteScore) {
                return;
            }
            Thread.sleep(10);
        }
    }

    private enum ReplayMode {
        FAST,
        MOVE_BY_MOVE,
        UNDO_AND_REDO
    }

    private int getGameLimit(int gameCount) {
        String configuredLimit = getArguments().getString("wthorGameLimit");
        if ("all".equalsIgnoreCase(configuredLimit)) {
            return gameCount;
        }
        if (configuredLimit != null) {
            try {
                return Math.min(gameCount, Math.max(1, Integer.parseInt(configuredLimit)));
            } catch (NumberFormatException ignored) {
                fail("Invalid wthorGameLimit: " + configuredLimit);
            }
        }
        return Math.min(gameCount, LOCAL_GAME_LIMIT);
    }

    // MOVE_BY_MOVE and UNDO_AND_REDO drive every step through the real app
    // (Activity, UI thread, GameStateBoardModel, dialogs) - what they're
    // actually proving is that this Android integration layer works, not
    // engine correctness. Engine correctness (make_move/undo_turn/
    // redo_turn across the whole bundled WThor corpus) is now covered
    // exhaustively and far more cheaply by the arm64 host-JNI test
    // (HostJniSmokeTest#allWthorGamesUndoRedoRoundTripCleanly, no
    // emulator), so running all ~200 games through those two modes here
    // added emulator time and flakiness surface without adding real
    // coverage. A handful of games chosen to cover distinct situations is
    // enough to prove the integration layer itself works: no pass (4),
    // exactly one forced pass - the exact game the undo/redo-across-a-
    // forced-pass bug was found and fixed on (0), several passes across a
    // full 60-move game (97), and a short game that ends early after
    // repeated passes (155).
    private static final int[] CURATED_GAME_INDICES = {4, 0, 97, 155};

    private int[] gameIndicesFor(ReplayMode mode, int gameCount) {
        // An explicit wthorGameLimit override (e.g. a manual full-corpus
        // run) still applies to any mode, same as before this change.
        boolean hasExplicitLimit = getArguments().getString("wthorGameLimit") != null;
        if (mode == ReplayMode.FAST || hasExplicitLimit) {
            int limit = getGameLimit(gameCount);
            int[] indices = new int[limit];
            for (int i = 0; i < limit; i++) {
                indices[i] = i;
            }
            return indices;
        }
        return CURATED_GAME_INDICES;
    }

    private void playAndWaitMoveByMove(String moves, int gameIndex) throws InterruptedException {
        Object previousGameState = zebra.getGameState();
        zebra.runOnUiThread(zebra::startNewGameAndResetUI);
        waitForNewGame(previousGameState, gameIndex);

        for (int offset = 0; offset < moves.length(); offset += 2) {
            String expectedMoves = moves.substring(0, offset + 2);
            Move move = new Move(moves.charAt(offset) - 'a',
                    moves.charAt(offset + 1) - '1');
            playMoveAndWait(move, offset / 2, expectedMoves, gameIndex,
                    offset + 2 == moves.length());
        }

        dismissOpenDialogIfPresent();
    }

    private void waitForNewGame(Object previousGameState, int gameIndex)
            throws InterruptedException {
        long startedAt = System.currentTimeMillis();
        long timeout = startedAt + 30_000;
        while (System.currentTimeMillis() < timeout) {
            if (zebra.getGameState() != null
                    && zebra.getGameState() != previousGameState
                    && removePasses(zebra.getGameState().getMoveSequenceAsString()).isEmpty()) {
                return;
            }
            Thread.sleep(10);
        }
        Log.e("WthorReplayTest", "New game timeout after "
                + (System.currentTimeMillis() - startedAt) + " ms for game " + gameIndex);
        String actualMoves = zebra.getGameState() == null
                ? "<no game state>"
                : removePasses(zebra.getGameState().getMoveSequenceAsString());
        fail("WThor game " + gameIndex + " did not create a new empty game state. "
                + "Actual moves: " + actualMoves);
    }

    private void undoAndRedoGame(String moves, int gameIndex, byte[] file, byte[][] originalBoard,
                                  byte[][] pristineStartBoard)
            throws InterruptedException {
        dismissOpenDialogIfPresent();
        // Captured once undo is verified correct end-to-end (the per-call
        // square check below, plus the pristine-position check after the
        // loop) - gives a full expected board for every intermediate ply,
        // not just the final ply (originalBoard) and the starting position
        // (pristineStartBoard). Keyed by disksPlayed, since undoing the move
        // at a given offset always lands on disksPlayed == offset / 2.
        java.util.Map<Integer, byte[][]> boardsByPly = new java.util.HashMap<>();
        for (int offset = moves.length() - 2; offset >= 0; offset -= 2) {
            // The very last undo (offset 0, landing on the empty starting
            // position) used to fire without waiting for it to actually be
            // applied, unlike every other undo/redo call in this method -
            // if the first redo() below then arrived before that undo had
            // landed, ZebraEngine#redoMove's ES_USER_INPUT_WAIT guard
            // silently dropped it, and the test just sat on two nested
            // timeouts (~50s) at the undo->redo transition before failing
            // with the state unchanged. Wait for it like all the others.
            String undoneSquare = moves.substring(offset, offset + 2);
            sendUndoUntilApplied(moves.substring(0, offset), gameIndex);
            // Bisects exactly which of the 58 undo calls first fails to
            // clear its own square, instead of only learning "the board is
            // wrong" after all of them - the board after undoing all the
            // way to ply 0 was confirmed wrong (a stray disc at f5, move
            // 1's square, plus a corrupted starting position), but that
            // alone doesn't say whether every undo silently no-ops or only
            // a specific one (e.g. the very last, row 1->0 transition) does.
            int x = undoneSquare.charAt(0) - 'a';
            int y = undoneSquare.charAt(1) - '1';
            // GameStateBoardModel (what getFieldByte reads) updates
            // asynchronously from the raw GameState that
            // sendUndoUntilApplied/hasMoveSequence just waited on - give it
            // a brief chance to catch up so a stale read doesn't look like
            // a real failure to clear the square.
            byte fieldAfterUndo = zebra.getState().getFieldByte(x, y);
            long fieldWaitDeadline = System.currentTimeMillis() + 2_000;
            while (fieldAfterUndo != ZebraEngine.PLAYER_EMPTY
                    && System.currentTimeMillis() < fieldWaitDeadline) {
                Thread.sleep(10);
                fieldAfterUndo = zebra.getState().getFieldByte(x, y);
            }
            if (fieldAfterUndo != ZebraEngine.PLAYER_EMPTY) {
                fail("WThor game " + gameIndex + " undo of ply " + (offset / 2 + 1)
                        + " (" + undoneSquare + ") did not clear that square - still "
                        + fieldName(fieldAfterUndo) + ". Move sequence now: "
                        + removePasses(zebra.getGameState().getMoveSequenceAsString()));
            }
            boardsByPly.put(offset / 2, captureBoard());
        }
        Log.i("WthorReplayTest", "WThor game " + gameIndex
                + " undo phase complete, engine state=" + zebra.getEngineState()
                + " - starting redo phase");
        // If undoing all 58 plies doesn't restore the exact standard
        // starting position, unmake_move() itself already leaves the board
        // wrong before any redo/make_move is even involved - narrows the
        // later final-score mismatch down to "broken by undo" vs
        // "broken by redo" instead of just "broken somewhere in between".
        // Unlike the per-call checks above, this used to be a single
        // one-shot captureBoard() right after the loop with no settle-wait -
        // GameStateBoardModel lags the raw GameState it was just polled
        // against (same async gap called out on fieldAfterUndo/fieldAfterRedo
        // above), and this is the only check that ever looks at d4/d5/e4/e5:
        // they're never a move's own target square (only flipped or restored
        // by unmake_move), so the per-call checks above never cover them.
        // That's why this check alone flip-flopped between runs on an
        // otherwise identical commit (clean once, 4-5 stale squares another
        // time) - give it the same poll the per-call checks already get.
        String postUndoDiff = diffBoards(pristineStartBoard, captureBoard());
        long postUndoDeadline = System.currentTimeMillis() + 2_000;
        while (!"<no differing squares>".equals(postUndoDiff)
                && System.currentTimeMillis() < postUndoDeadline) {
            Thread.sleep(10);
            postUndoDiff = diffBoards(pristineStartBoard, captureBoard());
        }
        if (!"<no differing squares>".equals(postUndoDiff)) {
            fail("WThor game " + gameIndex + " board after undoing all the way to ply 0 does "
                    + "not match the standard starting position: " + postUndoDiff);
        }

        for (int offset = 2; offset <= moves.length(); offset += 2) {
            String expectedMoves = moves.substring(0, offset);
            String redoneSquare = moves.substring(offset - 2, offset);
            int ply = offset / 2;
            int x = redoneSquare.charAt(0) - 'a';
            int y = redoneSquare.charAt(1) - '1';
            // Not simply ply%2 (odd=black, even=white): a forced pass
            // anywhere before this ply shifts which side actually plays
            // every real move after it, exactly what happens in this game -
            // real move 58 (g7) is White's, Black is then forced to pass,
            // so real move 59 (a5) is White's too even though 59 is odd.
            // Read the true color off the board captured for this exact
            // ply during the undo pass instead - already proven correct by
            // the per-call undo checks and the pristine-position check
            // above, so it doesn't need re-deriving from parity at all.
            // boardsByPly only covers plies 0..moves.length()/2 - 1 (the
            // undo loop that fills it starts one ply below the fully-played
            // game, since that top position is never undone-to) - the
            // final ply's already-verified-correct board is originalBoard
            // instead, captured right after the initial straight
            // playthrough.
            byte[][] boardForThisPly = (offset == moves.length()) ? originalBoard : boardsByPly.get(ply);
            byte expectedColor = boardForThisPly[x][y];
            if (offset < moves.length()) {
                sendRedoUntilApplied(expectedMoves, gameIndex);
            } else {
                sendFinalRedoUntilApplied(file, gameIndex, originalBoard, expectedColor);
            }
            // Mirrors the undo-phase per-call check above: does the square
            // this redo call just placed actually show the mover's color?
            // The final 7-square board diff (a5, a6, b4, b5, b6, c5, d5)
            // includes several squares that were directly played, not just
            // captured (c5=ply3, b5=ply10, b6=ply15, a6=ply17, b4=ply21) -
            // if one of those specific redo calls doesn't even place its
            // own disc correctly, that's the culprit; if it does and only
            // OTHER (captured) squares end up wrong, the bug is in that
            // move's flip computation instead.
            byte fieldAfterRedo = zebra.getState().getFieldByte(x, y);
            long fieldWaitDeadline = System.currentTimeMillis() + 2_000;
            while (fieldAfterRedo != expectedColor && System.currentTimeMillis() < fieldWaitDeadline) {
                Thread.sleep(10);
                fieldAfterRedo = zebra.getState().getFieldByte(x, y);
            }
            if (fieldAfterRedo != expectedColor) {
                fail("WThor game " + gameIndex + " redo of ply " + ply + " (" + redoneSquare
                        + ") did not place " + fieldName(expectedColor) + " there - actual "
                        + fieldName(fieldAfterRedo) + ". Move sequence now: "
                        + removePasses(zebra.getGameState().getMoveSequenceAsString()));
            }
            // The own-square check above only proves this redo placed its
            // own disc - it says nothing about the squares it should have
            // flipped. Compare the *whole* board against the same ply
            // captured during the undo pass (already proven correct by the
            // per-call undo checks and the pristine-position check above),
            // to catch a redo that gets its own square right but computes
            // the wrong flip set - which is exactly how the final-ply-only
            // 7-square diff (a5/a6/b4/b5/b6/c5/d5) could survive every
            // per-square check above it and only surface at the very end.
            if (offset < moves.length()) {
                byte[][] expectedBoard = boardsByPly.get(ply);
                String redoDiff = diffBoards(expectedBoard, captureBoard());
                long redoDiffDeadline = System.currentTimeMillis() + 2_000;
                while (!"<no differing squares>".equals(redoDiff)
                        && System.currentTimeMillis() < redoDiffDeadline) {
                    Thread.sleep(10);
                    redoDiff = diffBoards(expectedBoard, captureBoard());
                }
                if (!"<no differing squares>".equals(redoDiff)) {
                    fail("WThor game " + gameIndex + " redo of ply " + ply + " (" + redoneSquare
                            + ") placed its own square correctly but the full board no longer "
                            + "matches the same ply from the verified-correct undo pass: "
                            + redoDiff + ". Move sequence now: "
                            + removePasses(zebra.getGameState().getMoveSequenceAsString()));
                }
            }
        }

        dismissOpenDialogIfPresent();
    }

    // A single undo()/redo() call is only guaranteed to be picked up once the
    // engine is back in ES_USER_INPUT_WAIT (ZebraEngine#undoMove/redoMove
    // silently no-op otherwise, same as the manual toolbar buttons). Firing a
    // second undo()/redo() before the first one's board update lands - e.g.
    // because practice mode's post-move eval search made it slower than a
    // short per-attempt timeout - advances the game by an extra ply instead
    // of retrying the same one, which showed up in CI as moves being skipped.
    // Fire once and give it a single generous wait instead of racing retries.
    private static final long UNDO_REDO_TIMEOUT_MILLIS = 20_000;

    private void sendFinalRedoUntilApplied(byte[] file, int gameIndex, byte[][] originalBoard,
                                            byte expectedFinalMoveColor)
            throws InterruptedException {
        int offset = HEADER_SIZE + gameIndex * GAME_RECORD_SIZE;
        int expectedBlackScore = file[offset + 6] & 0xff;
        int expectedWhiteScore = 64 - expectedBlackScore;
        ZebraEngine.ENGINE_STATE stateBeforeCall = zebra.getEngineState();
        // expectedFinalMoveColor is read off boardsByPly/originalBoard, not
        // derived from ply%2 alternation - a forced pass earlier in the
        // game no longer throws this off (see the caller). Kept here as a
        // diagnostic aid: sideToMoveBeforeCall diverging from it would
        // still point at a genuinely wrong side-to-move computation
        // upstream, not just a stale assumption in this file.
        byte sideToMoveBeforeCall = (byte) zebra.getGameState().getSideToMove();
        zebra.runOnUiThread(zebra::redo);
        if (tryWaitForScore(expectedBlackScore, expectedWhiteScore, UNDO_REDO_TIMEOUT_MILLIS)) {
            return;
        }
        // The move sequence can be bit-for-bit correct (every square that
        // was clicked matches the original game) while the actual disc
        // colors on the board still diverge, if some redo step's make_move
        // computed the wrong flips - diffing against the board captured
        // right after the original straight playthrough (known-correct,
        // confirmed every run by replayGamesFast/MoveByMove) pins down
        // exactly which squares, instead of just "the score is wrong".
        fail("WThor game " + gameIndex + " final redo did not update the score. "
                + "Expected: " + expectedBlackScore + "/" + expectedWhiteScore
                + ", actual: " + zebra.getState().getBlackScore()
                + "/" + zebra.getState().getWhiteScore()
                + ", move sequence: "
                + removePasses(zebra.getGameState().getMoveSequenceAsString())
                + ", engine state before redo()/now: " + stateBeforeCall
                + "/" + zebra.getEngineState()
                + ", side to move before final redo: " + fieldName(sideToMoveBeforeCall)
                + " (expected " + fieldName(expectedFinalMoveColor) + ")"
                + ", board diff vs. original playthrough: "
                + diffBoards(originalBoard, captureBoard()));
    }

    private void sendUndoUntilApplied(String expectedMoves, int gameIndex)
            throws InterruptedException {
        ZebraEngine.ENGINE_STATE stateBeforeCall = zebra.getEngineState();
        Log.i("WthorReplayTest", "game " + gameIndex + " undo() -> target ply "
                + expectedMoves.length() / 2 + ", engine state before call: " + stateBeforeCall);
        zebra.runOnUiThread(zebra::undo);
        if (!tryWaitForMoveSequence(expectedMoves, UNDO_REDO_TIMEOUT_MILLIS)) {
            waitForMoveSequence(expectedMoves, gameIndex, "undo", stateBeforeCall);
        }
    }

    private void sendRedoUntilApplied(String expectedMoves, int gameIndex)
            throws InterruptedException {
        ZebraEngine.ENGINE_STATE stateBeforeCall = zebra.getEngineState();
        Log.i("WthorReplayTest", "game " + gameIndex + " redo() -> target ply "
                + expectedMoves.length() / 2 + ", engine state before call: " + stateBeforeCall);
        zebra.runOnUiThread(zebra::redo);
        if (!tryWaitForMoveSequence(expectedMoves, UNDO_REDO_TIMEOUT_MILLIS)) {
            waitForMoveSequence(expectedMoves, gameIndex, "redo", stateBeforeCall);
        }
    }

    private boolean tryWaitForMoveSequence(String expectedMoves, long timeoutMillis)
            throws InterruptedException {
        long timeout = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < timeout) {
            if (hasMoveSequence(expectedMoves)) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    private boolean tryWaitForScore(int expectedBlackScore, int expectedWhiteScore,
                                    long timeoutMillis) throws InterruptedException {
        long timeout = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < timeout) {
            if (zebra.getState().getBlackScore() == expectedBlackScore
                    && zebra.getState().getWhiteScore() == expectedWhiteScore) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    private void waitForMoveSequence(String expectedMoves, int gameIndex, String callLabel)
            throws InterruptedException {
        waitForMoveSequence(expectedMoves, gameIndex, callLabel, null);
    }

    private void waitForMoveSequence(String expectedMoves, int gameIndex, String callLabel,
                                      ZebraEngine.ENGINE_STATE stateBeforeCall)
            throws InterruptedException {
        long timeout = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < timeout) {
            if (zebra.getGameState() != null
                    && expectedMoves.equals(
                    removePasses(zebra.getGameState().getMoveSequenceAsString()))) {
                return;
            }
            Thread.sleep(10);
        }

        String actualMoves = zebra.getGameState() == null
                ? "<no game state>"
                : removePasses(zebra.getGameState().getMoveSequenceAsString());
        int expectedMoveIndex = actualMoves.length() / 2;
        String expectedMove = expectedMoveIndex < expectedMoves.length()
                ? expectedMoves.substring(expectedMoveIndex, expectedMoveIndex + 2)
                : "<end>";
        String actualLastMove = actualMoves.length() >= 2
                ? actualMoves.substring(actualMoves.length() - 2)
                : "<none>";
        // Engine state right before the triggering call, and again now: if
        // it was already something other than ES_USER_INPUT_WAIT before the
        // call, ZebraEngine's guard silently dropped the undo()/redo() and
        // nothing was ever queued - a fundamentally different failure than
        // the call having been applied but landing on the wrong position.
        fail("WThor game " + gameIndex + " " + callLabel
                + "() did not reach move-by-move prefix at move "
                + expectedMoveIndex + ". Expected move: " + expectedMove
                + ", last actual move: " + actualLastMove
                + ", expected prefix: " + expectedMoves
                + ", actual: " + actualMoves
                + ", engine state before " + callLabel + "()/now: " + stateBeforeCall
                + "/" + zebra.getEngineState());
    }

    // Re-sending onMakeMove every poll tick raced the engine: a move that was
    // genuinely accepted but just hadn't propagated to getMoveSequenceAsString()
    // yet (e.g. while practice mode's post-move eval search was still running)
    // got sent again, and if that stray tap landed on a still-legal square in
    // the meantime it played an extra, wrong move - producing exactly the kind
    // of "actual diverges from expected" failures seen in CI. Only re-send
    // after giving a single attempt real time to land.
    private static final long MOVE_RESEND_INTERVAL_MILLIS = 2_000;

    private void playMoveAndWait(Move move, int moveIndex, String expectedMoves, int gameIndex,
                                 boolean finalMove) throws InterruptedException {
        long timeout = System.currentTimeMillis() + (finalMove ? 30_000 : 60_000);
        long nextSendAt = 0;
        while (System.currentTimeMillis() < timeout) {
            if (hasMoveSequence(expectedMoves)) {
                return;
            }
            if (finalMove && hasGameOverDialog()) {
                return;
            }
            if (hasOpenDialog()) {
                confirmPassDialog();
            } else if (System.currentTimeMillis() >= nextSendAt) {
                zebra.runOnUiThread(() -> zebra.onMakeMove(move));
                nextSendAt = System.currentTimeMillis() + MOVE_RESEND_INTERVAL_MILLIS;
            }
            Thread.sleep(50);
        }
        if (finalMove) {
            return;
        }
        waitForMoveSequence(expectedMoves, gameIndex, "move");
    }

    private void confirmPassDialog() throws InterruptedException {
        zebra.runOnUiThread(() -> {
            androidx.fragment.app.Fragment fragment = zebra.getSupportFragmentManager()
                    .findFragmentByTag("dialog_pass");
            if (fragment instanceof DroidZebra.DialogPass) {
                android.app.Dialog dialog = ((androidx.fragment.app.DialogFragment) fragment)
                        .getDialog();
                if (dialog instanceof android.app.AlertDialog) {
                    android.widget.Button button = ((android.app.AlertDialog) dialog).getButton(
                            android.content.DialogInterface.BUTTON_POSITIVE);
                    if (button != null) {
                        button.performClick();
                    }
                }
            }
        });
        long timeout = System.currentTimeMillis() + 5_000;
        while (hasOpenDialog() && System.currentTimeMillis() < timeout) {
            Thread.sleep(10);
        }
    }

    private boolean hasOpenDialog() {
        for (androidx.fragment.app.Fragment fragment
                : zebra.getSupportFragmentManager().getFragments()) {
            if (fragment instanceof androidx.fragment.app.DialogFragment) {
                return true;
            }
        }
        return false;
    }

    private boolean hasGameOverDialog() {
        return zebra.getSupportFragmentManager().findFragmentByTag("dialog_gameover")
                instanceof androidx.fragment.app.DialogFragment;
    }

    private void dismissOpenDialogIfPresent() throws InterruptedException {
        if (hasOpenDialog()) {
            zebra.runOnUiThread(() -> {
                for (androidx.fragment.app.Fragment fragment
                        : zebra.getSupportFragmentManager().getFragments()) {
                    if (fragment instanceof androidx.fragment.app.DialogFragment) {
                        ((androidx.fragment.app.DialogFragment) fragment).dismiss();
                    }
                }
            });
        }
    }

    private boolean hasMoveSequence(String expectedMoves) {
        return zebra.getGameState() != null
                && expectedMoves.equals(
                removePasses(zebra.getGameState().getMoveSequenceAsString()));
    }

    private void playAndWaitForReplay(String moves, int gameIndex) throws InterruptedException {
        Object previousGameState = zebra.getGameState();
        zebra.runOnUiThread(() -> zebra.consumeMovesString(toThorNotation(moves)));

        long timeout = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < timeout) {
            if (zebra.getGameState() != null
                    && zebra.getGameState() != previousGameState
                    && moves.equals(removePasses(zebra.getGameState().getMoveSequenceAsString()))) {
                dismissOpenDialogIfPresent();
                return;
            }
            Thread.sleep(10);
        }

        String actualMoves = zebra.getGameState() == null
                ? "<no game state>"
                : removePasses(zebra.getGameState().getMoveSequenceAsString());
        fail("WThor game " + gameIndex + " was not replayed. Expected: "
                + moves + ", actual: " + actualMoves);
    }

    private String removePasses(String moves) {
        return moves.replace("--", "");
    }

    private String decodeGame(byte[] file, int gameIndex) {
        int offset = HEADER_SIZE + gameIndex * GAME_RECORD_SIZE;
        StringBuilder moves = new StringBuilder();

        for (int i = offset + GAME_HEADER_SIZE; i < offset + GAME_RECORD_SIZE; i++) {
            int encodedMove = file[i] & 0xff;
            if (encodedMove == 0) {
                continue;
            }

            int row = encodedMove / 10;
            int column = encodedMove % 10;
            if (row < 1 || row > 8 || column < 1 || column > 8) {
                fail("Invalid WThor move " + encodedMove + " in game " + gameIndex);
            }
            moves.append(new Move(column - 1, row - 1).getText());
        }

        return moves.toString();
    }

    private String toThorNotation(String moves) {
        StringBuilder notation = new StringBuilder();
        for (int i = 0; i < moves.length(); i += 4) {
            notation.append(i / 4 + 1).append(". ")
                    .append(moves, i, Math.min(i + 2, moves.length())).append(' ');
            if (i + 2 < moves.length()) {
                notation.append(moves, i + 2, Math.min(i + 4, moves.length())).append(' ');
            }
        }
        return notation.toString();
    }

    private byte[] readAsset(String fileName) throws IOException {
        AssetManager assets = getInstrumentation().getContext().getAssets();
        try (InputStream input = assets.open(fileName);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private int littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | ((bytes[offset + 3] & 0xff) << 24);
    }
}
