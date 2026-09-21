package de.earthlingz.oerszebra;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
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

    @Test
    public void replayAllGames() throws Exception {
        byte[] file = readAsset(FILE_NAME);
        assertEquals("Unexpected WThor header", HEADER_SIZE, file.length % GAME_RECORD_SIZE);

        int gameCount = littleEndianInt(file, 4);
        assertEquals("Unexpected WThor game count", gameCount,
                (file.length - HEADER_SIZE) / GAME_RECORD_SIZE);

        for (int gameIndex = 0; gameIndex < gameCount; gameIndex++) {
            String moves = decodeGame(file, gameIndex);
            playAndWaitForReplay(moves, gameIndex);

            int offset = HEADER_SIZE + gameIndex * GAME_RECORD_SIZE;
            int expectedBlackScore = file[offset + 6] & 0xff;
            int expectedWhiteScore = 64 - expectedBlackScore;
            assertEquals("WThor black score for game " + gameIndex,
                    expectedBlackScore, zebra.getState().getBlackScore());
            assertEquals("WThor white score for game " + gameIndex,
                    expectedWhiteScore, zebra.getState().getWhiteScore());
        }
    }

    private void playAndWaitForReplay(String moves, int gameIndex) throws InterruptedException {
        Object previousGameState = zebra.getGameState();
        zebra.runOnUiThread(() -> zebra.consumeMovesString(toThorNotation(moves)));

        long timeout = System.currentTimeMillis() + 30_000;
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
