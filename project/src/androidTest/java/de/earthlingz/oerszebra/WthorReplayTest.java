package de.earthlingz.oerszebra;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static androidx.test.platform.app.InstrumentationRegistry.getArguments;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.content.res.AssetManager;
import android.util.Log;

import androidx.test.filters.Suppress;

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

    @Test
    public void replayGamesFast() throws Exception {
        replayGames(ReplayMode.FAST);
    }

    @Test
    public void replayGamesMoveByMove() throws Exception {
        replayGames(ReplayMode.MOVE_BY_MOVE);
    }

    // Known bug, not yet fully root-caused, currently under active
    // investigation - see git history for the two real bugs already found
    // and fixed along the way:
    //   1. UI_EVENT_REDO was unhandled in droidzebra-jni.c's
    //      post-game-over loop (native, fixed).
    //   2. GameState.getMoveSequenceAsString() returned stale, leftover
    //      moves after an undo shortened the sequence (Java-side display
    //      bug, fixed) - this was masquerading as "redo overshoots to the
    //      full game", but the engine's actual ply position was correct
    //      the whole time; only the string representation lagged behind.
    // Also fixed: undoAndRedoGame()'s very last undo() fired without
    // waiting for it to land, which could race the first redo() into
    // ZebraEngine's ES_USER_INPUT_WAIT guard and get silently dropped.
    //
    // With both of those fixed, WThor game 0's undo+redo cycle now
    // reaches the end with the exact right move sequence, but the FINAL
    // SCORE is still wrong (seen: expected 31/33, actual 37/25) even
    // though replayGamesFast/MoveByMove confirm 31/33 is correct for this
    // exact game played straight through. So the move sequence (which
    // squares got clicked) is right, but the actual board state (which
    // squares ended up which color) has diverged - pointing at a flip
    // computation bug in the native redo replay itself, not a bookkeeping
    // or Java-side issue. captureBoard()/diffBoards() report exactly
    // which squares differ from the known-correct straight-playthrough
    // board, to localize this to the guilty move(s) instead of just
    // knowing "the score is wrong".
    // @Suppress removed while investigating; restore it if this still
    // fails and the investigation is parked again.
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
        byte[][] pristineStartBoard = mode == ReplayMode.UNDO_AND_REDO ? captureBoard() : null;

        int gamesToRun = getGameLimit(gameCount);
        for (int gameIndex = 0; gameIndex < gamesToRun; gameIndex++) {
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
        for (int offset = moves.length() - 2; offset >= 0; offset -= 2) {
            // The very last undo (offset 0, landing on the empty starting
            // position) used to fire without waiting for it to actually be
            // applied, unlike every other undo/redo call in this method -
            // if the first redo() below then arrived before that undo had
            // landed, ZebraEngine#redoMove's ES_USER_INPUT_WAIT guard
            // silently dropped it, and the test just sat on two nested
            // timeouts (~50s) at the undo->redo transition before failing
            // with the state unchanged. Wait for it like all the others.
            sendUndoUntilApplied(moves.substring(0, offset), gameIndex);
        }
        Log.i("WthorReplayTest", "WThor game " + gameIndex
                + " undo phase complete, engine state=" + zebra.getEngineState()
                + " - starting redo phase");
        // If undoing all 58 plies doesn't restore the exact standard
        // starting position, unmake_move() itself already leaves the board
        // wrong before any redo/make_move is even involved - narrows the
        // later final-score mismatch down to "broken by undo" vs
        // "broken by redo" instead of just "broken somewhere in between".
        String postUndoDiff = diffBoards(pristineStartBoard, captureBoard());
        if (!"<no differing squares>".equals(postUndoDiff)) {
            fail("WThor game " + gameIndex + " board after undoing all the way to ply 0 does "
                    + "not match the standard starting position: " + postUndoDiff);
        }

        for (int offset = 2; offset <= moves.length(); offset += 2) {
            String expectedMoves = moves.substring(0, offset);
            if (offset < moves.length()) {
                sendRedoUntilApplied(expectedMoves, gameIndex);
            } else {
                sendFinalRedoUntilApplied(file, gameIndex, originalBoard);
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

    private void sendFinalRedoUntilApplied(byte[] file, int gameIndex, byte[][] originalBoard)
            throws InterruptedException {
        int offset = HEADER_SIZE + gameIndex * GAME_RECORD_SIZE;
        int expectedBlackScore = file[offset + 6] & 0xff;
        int expectedWhiteScore = 64 - expectedBlackScore;
        ZebraEngine.ENGINE_STATE stateBeforeCall = zebra.getEngineState();
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
