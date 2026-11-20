package de.earthlingz.oerszebra;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;

import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.shurik.droidzebra.GameState;
import com.shurik.droidzebra.ZebraEngine;

import de.earthlingz.oerszebra.guessmove.GuessMoveActivity;

import org.junit.Test;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

// Guess Move plays its own games on the shared engine. Coming back from it,
// the main board used to stay cut off from the engine: every move, undo and
// redo there was silently dropped.
public class GuessMoveReturnTest extends BasicTest {

    // Same verified-legal start as BackAndKeyInputTest.
    private static final String SHORT_LEGAL_GAME = "E6F6C4D6";
    private static final int SHORT_LEGAL_GAME_MOVES = 4;

    @Test
    public void testBoardStillPlaysAfterGuessMove() throws InterruptedException {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        intent.putExtra(Intent.EXTRA_TEXT, SHORT_LEGAL_GAME);
        zebra.runOnUiThread(() -> zebra.onNewIntent(intent));
        waitForDisksPlayed(SHORT_LEGAL_GAME_MOVES);
        assertEquals(SHORT_LEGAL_GAME_MOVES, zebra.getGameState().getDisksPlayed());

        getInstrumentation().runOnMainSync(() -> zebra.openGuessMove());
        waitUntil(() -> resumedGuessMove() != null && !zebra.isGameConnectedToEngine(), 30000);
        Activity guessMove = resumedGuessMove();
        assertTrue("Guess Move did not take over the engine",
                guessMove != null && !zebra.isGameConnectedToEngine());

        getInstrumentation().runOnMainSync(guessMove::finish);

        waitUntil(() -> zebra.isGameConnectedToEngine()
                && zebra.getGameState().getDisksPlayed() == SHORT_LEGAL_GAME_MOVES
                && zebra.getEngineState() == ZebraEngine.ENGINE_STATE.ES_USER_INPUT_WAIT, 30000);
        assertTrue("the game was not handed back to the engine", zebra.isGameConnectedToEngine());
        assertEquals(SHORT_LEGAL_GAME_MOVES, zebra.getGameState().getDisksPlayed());

        // undo is what used to be dropped
        getInstrumentation().runOnMainSync(() -> zebra.getOnBackPressedDispatcher().onBackPressed());
        waitForDisksPlayed(SHORT_LEGAL_GAME_MOVES - 1);
        assertEquals(SHORT_LEGAL_GAME_MOVES - 1, zebra.getGameState().getDisksPlayed());
    }

    private void waitForDisksPlayed(int expected) throws InterruptedException {
        waitUntil(() -> {
            GameState gameState = zebra.getGameState();
            return gameState != null && gameState.getDisksPlayed() == expected;
        }, 20000);
    }

    private Activity resumedGuessMove() {
        AtomicReference<Activity> found = new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> {
            Collection<Activity> resumed = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED);
            for (Activity activity : resumed) {
                if (activity instanceof GuessMoveActivity) {
                    found.set(activity);
                }
            }
        });
        return found.get();
    }
}
