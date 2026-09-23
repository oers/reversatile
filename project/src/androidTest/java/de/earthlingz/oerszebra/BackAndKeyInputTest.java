package de.earthlingz.oerszebra;

import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.shurik.droidzebra.GameState;
import com.shurik.droidzebra.ZebraEngine;

import org.junit.Test;

import de.earthlingz.oerszebra.BoardView.BoardView;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class BackAndKeyInputTest extends BasicTest {

    // First four moves of the verified-legal sequence also used by
    // DroidZebraTest#testIssue79 and AnalysisTest.
    private static final String SHORT_LEGAL_GAME = "E6F6C4D6";
    private static final int SHORT_LEGAL_GAME_MOVES = 4;

    private void loadShortGame() throws InterruptedException {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        intent.putExtra(Intent.EXTRA_TEXT, SHORT_LEGAL_GAME);
        zebra.runOnUiThread(() -> zebra.onNewIntent(intent));
        waitForDisksPlayed(SHORT_LEGAL_GAME_MOVES);
        assertEquals(SHORT_LEGAL_GAME_MOVES, zebra.getGameState().getDisksPlayed());
    }

    private void waitForDisksPlayed(int expected) throws InterruptedException {
        waitUntil(() -> {
            GameState gameState = zebra.getGameState();
            return gameState != null && gameState.getDisksPlayed() == expected;
        }, 20000);
    }

    // Goes through the back dispatcher - the path Android 13+ uses with
    // predictive back enabled - rather than a KEYCODE_BACK key event.
    @Test
    public void testBackUndoesLastMove() throws InterruptedException {
        loadShortGame();
        // undo is silently dropped unless the engine is waiting for input
        // (practice mode may still be computing evals right after the load)
        waitUntil(() -> zebra.getEngineState() == ZebraEngine.ENGINE_STATE.ES_USER_INPUT_WAIT, 20000);

        getInstrumentation().runOnMainSync(() -> zebra.getOnBackPressedDispatcher().onBackPressed());
        waitForDisksPlayed(SHORT_LEGAL_GAME_MOVES - 1);

        assertEquals(SHORT_LEGAL_GAME_MOVES - 1, zebra.getGameState().getDisksPlayed());
        assertFalse("back must take back a move, not close the game", zebra.isFinishing());
    }

    // onBoardStateChanged() clears the board's move selection; key and
    // trackball input right after that used to throw a NullPointerException.
    @Test
    public void testKeyAndTrackballInputAfterBoardChangeDoNotCrash() throws InterruptedException {
        loadShortGame();
        BoardView boardView = zebra.getBoardView();

        getInstrumentation().runOnMainSync(() -> {
            boardView.onBoardStateChanged();
            boardView.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT,
                    new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
        });

        getInstrumentation().runOnMainSync(() -> {
            boardView.onBoardStateChanged();
            long now = SystemClock.uptimeMillis();
            MotionEvent move = MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, 1f, 0f, 0);
            boardView.onTrackballEvent(move);
            move.recycle();
        });

        assertEquals("input must not have made a move", SHORT_LEGAL_GAME_MOVES,
                zebra.getGameState().getDisksPlayed());
    }
}
