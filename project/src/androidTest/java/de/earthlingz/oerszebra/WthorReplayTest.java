package de.earthlingz.oerszebra;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static androidx.test.platform.app.InstrumentationRegistry.getArguments;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.content.res.AssetManager;

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
    public void replayAllGames() throws Exception {
        byte[] file = readAsset(FILE_NAME);
        assertEquals("Unexpected WThor header", HEADER_SIZE, file.length % GAME_RECORD_SIZE);

        int gameCount = littleEndianInt(file, 4);
        assertEquals("Unexpected WThor game count", gameCount,
                (file.length - HEADER_SIZE) / GAME_RECORD_SIZE);

        int gamesToRun = getGameLimit(gameCount);
        for (int gameIndex = 0; gameIndex < gamesToRun; gameIndex++) {
            String moves = decodeGame(file, gameIndex);
            if ((gameIndex + 1) % 10 == 0) {
                playAndWaitMoveByMove(moves, gameIndex);
                undoAndRedoGame(moves, gameIndex);
            } else if ((gameIndex & 1) == 1) {
                playAndWaitMoveByMove(moves, gameIndex);
            } else {
                playAndWaitForReplay(moves, gameIndex);
            }

            int offset = HEADER_SIZE + gameIndex * GAME_RECORD_SIZE;
            int expectedBlackScore = file[offset + 6] & 0xff;
            int expectedWhiteScore = 64 - expectedBlackScore;
            assertEquals("WThor black score for game " + gameIndex,
                    expectedBlackScore, zebra.getState().getBlackScore());
            assertEquals("WThor white score for game " + gameIndex,
                    expectedWhiteScore, zebra.getState().getWhiteScore());
        }
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
        zebra.runOnUiThread(zebra::startNewGameAndResetUI);
        waitForMoveSequence("", gameIndex);

        for (int offset = 0; offset < moves.length(); offset += 2) {
            String expectedMoves = moves.substring(0, offset + 2);
            Move move = new Move(moves.charAt(offset) - 'a',
                    moves.charAt(offset + 1) - '1');
            playMoveAndWait(move, expectedMoves, gameIndex);
        }

        waitForOpenendDialogs(true);
    }

    private void undoAndRedoGame(String moves, int gameIndex) throws InterruptedException {
        for (int offset = moves.length() - 2; offset >= 0; offset -= 2) {
            String expectedMoves = moves.substring(0, offset);
            zebra.runOnUiThread(zebra::undo);
            waitForMoveSequence(expectedMoves, gameIndex);
        }

        for (int offset = 2; offset <= moves.length(); offset += 2) {
            String expectedMoves = moves.substring(0, offset);
            zebra.runOnUiThread(zebra::redo);
            waitForMoveSequence(expectedMoves, gameIndex);
        }

        waitForOpenendDialogs(true);
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
        fail("WThor game " + gameIndex + " did not reach move-by-move prefix. Expected: "
                + expectedMoves + ", actual: " + actualMoves);
    }

    private void playMoveAndWait(Move move, String expectedMoves, int gameIndex)
            throws InterruptedException {
        long timeout = System.currentTimeMillis() + 30_000;
        boolean passRequested = false;
        while (System.currentTimeMillis() < timeout) {
            if (hasMoveSequence(expectedMoves)) {
                return;
            }
            if (hasOpenDialog()) {
                confirmPassDialog();
                passRequested = true;
            } else if (!passRequested && zebra.getState() != null
                    && !zebra.getState().isValidMove(move)) {
                passRequested = true;
                zebra.runOnUiThread(zebra::pass);
            } else {
                zebra.runOnUiThread(() -> zebra.onMakeMove(move));
            }
            Thread.sleep(50);
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
                waitForOpenendDialogs(true);
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
