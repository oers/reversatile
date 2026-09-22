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

    // Keep the engine search shallow: GameAnalyzer runs one practice-mode
    // search per played move, so the base class's strong "22|20|0" test
    // depth would multiply into many full-strength searches and make this
    // test very slow.
    @Override
    protected String getTestSearchDepth() {
        return "1|1|1";
    }

    // First four moves of the verified-legal sequence already used by
    // DroidZebraTest#testIssue79 - short on purpose, see getTestSearchDepth().
    private static final String SHORT_LEGAL_GAME = "E6F6C4D6";
    private static final int SHORT_LEGAL_GAME_MOVES = 4;

    // This is a mid-game position, not a finished game, so no Game Over
    // dialog ever appears - wait for the move replay itself instead of
    // waitForOpenendDialogs (which would poll forever here).
    private void loadGame(String moveText, int expectedDisksPlayed) throws InterruptedException {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        intent.putExtra(Intent.EXTRA_TEXT, moveText);

        zebra.runOnUiThread(() -> zebra.onNewIntent(intent));
        waitForDisksPlayed(expectedDisksPlayed, 20000);
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

    // GameState is momentarily null while a new game is being (re)established
    // (e.g. right after firing the moves intent, before the engine hands back
    // the replayed GameState) - treat that as "not there yet" rather than NPEing.
    //
    // Also wait for zebra.getState() (GameStateBoardModel, what countSquares()
    // and the score getters read) to reflect the same ply count: it mirrors the
    // raw engine GameState asynchronously via the onBoard() callback, so it can
    // still show the previous position for a moment after getDisksPlayed()
    // already reports the new one (confirmed in CI: an assertion right after a
    // bare disksPlayed wait saw 64 empty squares - the untouched starting board
    // - even though disksPlayed already read 4). These test games never pass,
    // so total discs is always 4 (the opening position) plus plies played.
    private void waitForDisksPlayed(int expected, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        int expectedDiscs = 4 + expected;
        while (System.currentTimeMillis() < deadline) {
            GameState gameState = zebra.getGameState();
            if (gameState != null && gameState.getDisksPlayed() == expected
                    && zebra.getState().getBlackScore() + zebra.getState().getWhiteScore()
                            == expectedDiscs) {
                return;
            }
            Thread.sleep(100);
        }
    }

    @Test
    public void testAnalyzeGameProducesOneEvalPerMoveAndRestoresLiveGame() throws InterruptedException {
        loadGame(SHORT_LEGAL_GAME, SHORT_LEGAL_GAME_MOVES);

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
        // GameAnalyzer evaluates most-recent-move-first (see its start()) so
        // results come back newest-first, matching the drawer's display order.
        for (int i = 0; i < results.size(); i++) {
            assertEquals("plies must come back newest-first", originalDisksPlayed - i, results.get(i).getPly());
        }

        // the live game must be restored exactly to where it was before analysis
        waitForDisksPlayed(originalDisksPlayed, 20000);
        assertEquals(originalDisksPlayed, zebra.getGameState().getDisksPlayed());
        assertEquals(originalEmpty, countSquares(ZebraEngine.PLAYER_EMPTY));
        assertEquals(originalBlack, countSquares(ZebraEngine.PLAYER_BLACK));
        assertEquals(originalWhite, countSquares(ZebraEngine.PLAYER_WHITE));
    }

    @Test
    public void testCancellingAnalysisStillRestoresLiveGame() throws InterruptedException {
        loadGame(SHORT_LEGAL_GAME, SHORT_LEGAL_GAME_MOVES);

        GameState original = zebra.getGameState();
        int originalDisksPlayed = original.getDisksPlayed();
        int originalEmpty = countSquares(ZebraEngine.PLAYER_EMPTY);

        zebra.runOnUiThread(zebra::analyzeGame);
        // cancel almost immediately, well before all plies can have been evaluated
        zebra.runOnUiThread(zebra::cancelAnalysisIfRunning);

        waitForAnalysisProgressGone(60000);
        waitForDisksPlayed(originalDisksPlayed, 20000);

        assertEquals(originalDisksPlayed, zebra.getGameState().getDisksPlayed());
        assertEquals(originalEmpty, countSquares(ZebraEngine.PLAYER_EMPTY));
        assertTrue("cancelled analysis must not report more evals than were played",
                zebra.getLastAnalysisResults().size() <= originalDisksPlayed);
    }

    @Test
    public void testJumpToMoveNavigatesBoardToThatPly() throws InterruptedException {
        loadGame(SHORT_LEGAL_GAME, SHORT_LEGAL_GAME_MOVES);
        int originalDisksPlayed = zebra.getGameState().getDisksPlayed();

        zebra.runOnUiThread(zebra::analyzeGame);
        waitForAnalysisResults(originalDisksPlayed, 60000);
        waitForAnalysisProgressGone(60000);
        waitForDisksPlayed(originalDisksPlayed, 20000);

        int targetPly = 2;
        zebra.runOnUiThread(() -> zebra.jumpToMove(targetPly));
        waitForDisksPlayed(targetPly, 20000);

        assertEquals(targetPly, zebra.getGameState().getDisksPlayed());

        // leave the game back where it was
        zebra.runOnUiThread(() -> zebra.jumpToMove(originalDisksPlayed));
        waitForDisksPlayed(originalDisksPlayed, 20000);
        assertEquals(originalDisksPlayed, zebra.getGameState().getDisksPlayed());
    }

    // Regression test: tapping a result while analysis is still running used
    // to silently do nothing (the tap was ignored outright). requestJumpToMove
    // is what the drawer's row click actually calls; this exercises its
    // deferred path specifically by requesting a jump immediately after
    // starting analysis, well before it can have finished on its own.
    @Test
    public void testRequestJumpToMoveDuringAnalysisJumpsOnceSettled() throws InterruptedException {
        loadGame(SHORT_LEGAL_GAME, SHORT_LEGAL_GAME_MOVES);
        int originalDisksPlayed = zebra.getGameState().getDisksPlayed();

        zebra.runOnUiThread(zebra::analyzeGame);
        int targetPly = 2;
        zebra.runOnUiThread(() -> zebra.requestJumpToMove(targetPly));

        waitForAnalysisProgressGone(60000);
        waitForDisksPlayed(targetPly, 20000);
        assertEquals(targetPly, zebra.getGameState().getDisksPlayed());

        // leave the game back where it was
        zebra.runOnUiThread(() -> zebra.jumpToMove(originalDisksPlayed));
        waitForDisksPlayed(originalDisksPlayed, 20000);
        assertEquals(originalDisksPlayed, zebra.getGameState().getDisksPlayed());
    }
}
