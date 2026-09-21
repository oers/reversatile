package de.earthlingz.oerszebra;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import com.shurik.droidzebra.GameState;
import com.shurik.droidzebra.ZebraEngine;

import org.junit.Test;

import java.util.List;

import de.earthlingz.oerszebra.analysis.MoveEval;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Covers the post-game "Analyze Game" feature (see
 * {@link de.earthlingz.oerszebra.analysis.GameAnalyzer}): every played move
 * gets one evaluation, the live game is left untouched once analysis
 * finishes (or is cancelled), and clicking/jumping to a result navigates
 * the board.
 */
public class AnalysisTest extends BasicTest {

    // Keep the engine search shallow: GameAnalyzer restarts the engine once
    // per played move, so the base class's strong "22|20|0" test depth would
    // multiply into many full-strength searches and make this test very slow.
    @Override
    protected String getTestSearchDepth() {
        return "1|1|1";
    }

    // First four moves of the verified-legal sequence already used by
    // DroidZebraTest#testIssue79 - short on purpose, see getTestSearchDepth().
    private static final String SHORT_LEGAL_GAME = "E6F6C4D6";

    private void loadGame(String moveText) throws InterruptedException {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        intent.putExtra(Intent.EXTRA_TEXT, moveText);

        zebra.runOnUiThread(() -> zebra.onNewIntent(intent));
        waitForOpenendDialogs(false);
    }

    private void waitForAnalysisResults(int expectedSize, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (zebra.getLastAnalysisResults().size() != expectedSize && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
    }

    private void waitForAnalysisProgressGone(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        TextView progress = zebra.findViewById(R.id.status_analysis_progress);
        while (progress != null && progress.getVisibility() != View.GONE && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
    }

    private void waitForDisksPlayed(int expected, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (zebra.getGameState().getDisksPlayed() != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
    }

    @Test
    public void testAnalyzeGameProducesOneEvalPerMoveAndRestoresLiveGame() throws InterruptedException {
        loadGame(SHORT_LEGAL_GAME);

        GameState original = zebra.getGameState();
        int originalDisksPlayed = original.getDisksPlayed();
        int originalEmpty = countSquares(ZebraEngine.PLAYER_EMPTY);
        int originalBlack = countSquares(ZebraEngine.PLAYER_BLACK);
        int originalWhite = countSquares(ZebraEngine.PLAYER_WHITE);

        zebra.runOnUiThread(zebra::analyzeGame);

        waitForAnalysisResults(originalDisksPlayed, 60000);
        waitForAnalysisProgressGone(60000);

        List<MoveEval> results = zebra.getLastAnalysisResults();
        assertEquals("one MoveEval per played move", originalDisksPlayed, results.size());
        for (int i = 0; i < results.size(); i++) {
            assertEquals("plies must come back in order", i + 1, results.get(i).getPly());
        }

        // the live game must be restored exactly to where it was before analysis
        waitForDisksPlayed(originalDisksPlayed, 10000);
        assertEquals(originalDisksPlayed, zebra.getGameState().getDisksPlayed());
        assertEquals(originalEmpty, countSquares(ZebraEngine.PLAYER_EMPTY));
        assertEquals(originalBlack, countSquares(ZebraEngine.PLAYER_BLACK));
        assertEquals(originalWhite, countSquares(ZebraEngine.PLAYER_WHITE));
    }

    @Test
    public void testCancellingAnalysisStillRestoresLiveGame() throws InterruptedException {
        loadGame(SHORT_LEGAL_GAME);

        GameState original = zebra.getGameState();
        int originalDisksPlayed = original.getDisksPlayed();
        int originalEmpty = countSquares(ZebraEngine.PLAYER_EMPTY);

        zebra.runOnUiThread(zebra::analyzeGame);
        // cancel almost immediately, well before all plies can have been evaluated
        zebra.runOnUiThread(zebra::cancelAnalysisIfRunning);

        waitForAnalysisProgressGone(60000);
        waitForDisksPlayed(originalDisksPlayed, 10000);

        assertEquals(originalDisksPlayed, zebra.getGameState().getDisksPlayed());
        assertEquals(originalEmpty, countSquares(ZebraEngine.PLAYER_EMPTY));
        assertTrue("cancelled analysis must not report more evals than were played",
                zebra.getLastAnalysisResults().size() <= originalDisksPlayed);
    }

    @Test
    public void testJumpToMoveNavigatesBoardToThatPly() throws InterruptedException {
        loadGame(SHORT_LEGAL_GAME);
        int originalDisksPlayed = zebra.getGameState().getDisksPlayed();

        zebra.runOnUiThread(zebra::analyzeGame);
        waitForAnalysisResults(originalDisksPlayed, 60000);
        waitForAnalysisProgressGone(60000);
        waitForDisksPlayed(originalDisksPlayed, 10000);

        int targetPly = 2;
        zebra.runOnUiThread(() -> zebra.jumpToMove(targetPly));
        waitForDisksPlayed(targetPly, 10000);

        assertEquals(targetPly, zebra.getGameState().getDisksPlayed());

        // leave the game back where it was
        zebra.runOnUiThread(() -> zebra.jumpToMove(originalDisksPlayed));
        waitForDisksPlayed(originalDisksPlayed, 10000);
        assertEquals(originalDisksPlayed, zebra.getGameState().getDisksPlayed());
    }
}
