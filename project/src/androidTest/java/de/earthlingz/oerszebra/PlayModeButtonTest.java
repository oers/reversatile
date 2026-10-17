package de.earthlingz.oerszebra;

import android.content.Context;
import android.content.SharedPreferences;

import com.shurik.droidzebra.ZebraEngine;

import org.junit.Test;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static de.earthlingz.oerszebra.GameSettingsConstants.FUNCTION_HUMAN_VS_HUMAN;
import static de.earthlingz.oerszebra.GameSettingsConstants.FUNCTION_ZEBRA_BLACK;
import static de.earthlingz.oerszebra.GameSettingsConstants.FUNCTION_ZEBRA_WHITE;
import static de.earthlingz.oerszebra.GlobalSettingsLoader.SETTINGS_KEY_DISPLAY_MOVES;
import static de.earthlingz.oerszebra.GlobalSettingsLoader.SETTINGS_KEY_FUNCTION;
import static de.earthlingz.oerszebra.GlobalSettingsLoader.SETTINGS_KEY_PRACTICE_MODE;
import static de.earthlingz.oerszebra.GlobalSettingsLoader.SHARED_PREFS_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The toolbar's play mode button: analysis (evaluations and legal moves
 * shown, nobody plays against you), then vs computer and vs human (no help
 * on the board), then back to analysis.
 */
public class PlayModeButtonTest extends BasicTest {

    @Test
    public void buttonCyclesTheModesAndHidesHelpWhilePlaying() {
        try {
            cycleUntil(PlayMode.ANALYSIS);
            assertTrue(onUi(() -> zebra.evalsDisplayEnabled()));

            // new game: Black to move, so the computer takes White
            cycle();
            assertEquals(PlayMode.VS_COMPUTER, onUiMode());
            assertEquals(FUNCTION_ZEBRA_WHITE, function());
            assertFalse(prefs().getBoolean(SETTINGS_KEY_PRACTICE_MODE, true));
            assertFalse(prefs().getBoolean(SETTINGS_KEY_DISPLAY_MOVES, true));
            assertFalse(onUi(() -> zebra.evalsDisplayEnabled()));

            cycle();
            assertEquals(PlayMode.VS_HUMAN, onUiMode());
            assertEquals(FUNCTION_HUMAN_VS_HUMAN, function());
            assertFalse(prefs().getBoolean(SETTINGS_KEY_DISPLAY_MOVES, true));
            assertFalse(onUi(() -> zebra.evalsDisplayEnabled()));

            cycle();
            assertEquals(PlayMode.ANALYSIS, onUiMode());
            assertEquals(FUNCTION_HUMAN_VS_HUMAN, function());
            assertTrue(prefs().getBoolean(SETTINGS_KEY_DISPLAY_MOVES, false));
            assertTrue(onUi(() -> zebra.evalsDisplayEnabled()));
        } finally {
            // the other tests expect the default: analysis
            restoreAnalysisAndSettle();
        }
    }

    // With White to move, switching to "vs computer" gives the computer
    // Black, so the player still has the move.
    @Test
    public void computerTakesTheSideThatIsNotToMove() throws InterruptedException {
        try {
            cycleUntil(PlayMode.ANALYSIS);
            zebra.runOnUiThread(() -> zebra.onMakeMove(new com.shurik.droidzebra.Move(5, 4)));
            waitUntil(() -> zebra.getGameState() != null
                    && zebra.getGameState().getSideToMove() == ZebraEngine.PLAYER_WHITE, 10000);
            assertEquals(ZebraEngine.PLAYER_WHITE, zebra.getGameState().getSideToMove());

            cycle();
            assertEquals(PlayMode.VS_COMPUTER, onUiMode());
            assertEquals(FUNCTION_ZEBRA_BLACK, function());
        } finally {
            restoreAnalysisAndSettle();
        }
    }

    // Every mode change makes the engine reload its settings and, in
    // analysis, start evaluating the position again. Leaving while it is
    // still busy let the next test's activity start a game on a busy engine
    // (seen in CI: WthorReplayTest's first move never landed right after
    // this test), so wait until it is back waiting for input.
    private void restoreAnalysisAndSettle() {
        cycleUntil(PlayMode.ANALYSIS);
        // The settings change reaches the engine asynchronously, so it can
        // still read "waiting" for a moment before picking it up - require
        // it to stay waiting for a while, not just once.
        long deadline = System.currentTimeMillis() + 20_000;
        long waitingSince = -1;
        try {
            while (System.currentTimeMillis() < deadline) {
                boolean waiting = zebra.getEngineState() == ZebraEngine.ENGINE_STATE.ES_USER_INPUT_WAIT;
                if (!waiting) {
                    waitingSince = -1;
                } else if (waitingSince < 0) {
                    waitingSince = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - waitingSince >= 500) {
                    break;
                }
                Thread.sleep(20);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        getInstrumentation().waitForIdleSync();
    }

    private void cycle() {
        getInstrumentation().runOnMainSync(() -> zebra.cyclePlayMode());
        getInstrumentation().waitForIdleSync();
    }

    private void cycleUntil(PlayMode mode) {
        for (int i = 0; i < PlayMode.values().length && onUiMode() != mode; i++) {
            cycle();
        }
        assertEquals(mode, onUiMode());
    }

    private PlayMode onUiMode() {
        PlayMode[] mode = new PlayMode[1];
        getInstrumentation().runOnMainSync(() -> mode[0] = zebra.currentPlayMode());
        return mode[0];
    }

    private boolean onUi(java.util.function.BooleanSupplier read) {
        boolean[] result = new boolean[1];
        getInstrumentation().runOnMainSync(() -> result[0] = read.getAsBoolean());
        return result[0];
    }

    private int function() {
        return Integer.parseInt(prefs().getString(SETTINGS_KEY_FUNCTION, "-1"));
    }

    private SharedPreferences prefs() {
        return getInstrumentation().getTargetContext()
                .getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
    }
}
