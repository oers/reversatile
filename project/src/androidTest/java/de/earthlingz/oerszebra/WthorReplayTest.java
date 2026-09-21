package de.earthlingz.oerszebra;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static androidx.test.platform.app.InstrumentationRegistry.getArguments;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.content.res.AssetManager;
import android.util.Log;

import com.shurik.droidzebra.Move;

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
                    undoAndRedoGame(moves, gameIndex, file);
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

    private void undoAndRedoGame(String moves, int gameIndex, byte[] file)
            throws InterruptedException {
        dismissOpenDialogIfPresent();
        for (int offset = moves.length() - 2; offset >= 0; offset -= 2) {
            String expectedMoves = moves.substring(0, offset);
            if (offset > 0) {
                sendUndoUntilApplied(expectedMoves, gameIndex);
            } else {
                zebra.runOnUiThread(zebra::undo);
            }
        }

        for (int offset = 2; offset <= moves.length(); offset += 2) {
            String expectedMoves = moves.substring(0, offset);
            if (offset < moves.length()) {
                sendRedoUntilApplied(expectedMoves, gameIndex);
            } else {
                sendFinalRedoUntilApplied(file, gameIndex);
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

    private void sendFinalRedoUntilApplied(byte[] file, int gameIndex)
            throws InterruptedException {
        int offset = HEADER_SIZE + gameIndex * GAME_RECORD_SIZE;
        int expectedBlackScore = file[offset + 6] & 0xff;
        int expectedWhiteScore = 64 - expectedBlackScore;
        zebra.runOnUiThread(zebra::redo);
        if (tryWaitForScore(expectedBlackScore, expectedWhiteScore, UNDO_REDO_TIMEOUT_MILLIS)) {
            return;
        }
        fail("WThor game " + gameIndex + " final redo did not update the score. "
                + "Expected: " + expectedBlackScore + "/" + expectedWhiteScore
                + ", actual: " + zebra.getState().getBlackScore()
                + "/" + zebra.getState().getWhiteScore()
                + ", move sequence: "
                + removePasses(zebra.getGameState().getMoveSequenceAsString()));
    }

    private void sendUndoUntilApplied(String expectedMoves, int gameIndex)
            throws InterruptedException {
        zebra.runOnUiThread(zebra::undo);
        if (!tryWaitForMoveSequence(expectedMoves, UNDO_REDO_TIMEOUT_MILLIS)) {
            waitForMoveSequence(expectedMoves, gameIndex);
        }
    }

    private void sendRedoUntilApplied(String expectedMoves, int gameIndex)
            throws InterruptedException {
        zebra.runOnUiThread(zebra::redo);
        if (!tryWaitForMoveSequence(expectedMoves, UNDO_REDO_TIMEOUT_MILLIS)) {
            waitForMoveSequence(expectedMoves, gameIndex);
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

    private void waitForMoveSequence(String expectedMoves, int gameIndex)
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
        fail("WThor game " + gameIndex + " did not reach move-by-move prefix at move "
                + expectedMoveIndex + ". Expected move: " + expectedMove
                + ", last actual move: " + actualLastMove
                + ", expected prefix: " + expectedMoves
                + ", actual: " + actualMoves);
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
        long timeout = System.currentTimeMillis() + (finalMove ? 10_000 : 60_000);
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
        waitForMoveSequence(expectedMoves, gameIndex);
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
