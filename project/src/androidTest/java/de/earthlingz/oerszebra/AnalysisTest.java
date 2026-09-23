package de.earthlingz.oerszebra;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import com.shurik.droidzebra.GameState;
import com.shurik.droidzebra.ZebraEngine;

import org.junit.Test;

import java.util.List;

import de.earthlingz.oerszebra.analysis.MoveEval;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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

    // A finished game (full board) with a forced pass near the end - the
    // same game DroidZebraTest#testRedoAcrossPass uses.
    private static final String GAME_WITH_PASS = "D3C5F6F5F4C3C4D2E2B4D1F3B5E3F2F1A4D6E6E7F7B6E8C6B3A5D7A3E1A6G1A2C2C7B8D8C8G8G6H6G5H5G4H4H3G7H8F8H7A8A7B7A1B2G3G2H2H1C1B1";
    private static final int GAME_WITH_PASS_MOVES = 60;

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

    // Real (non-pass) moves played. Unlike GameState#getDisksPlayed this is
    // what the analysis numbers its plies by.
    private int realMoves() {
        GameState gameState = zebra.getGameState();
        return gameState == null ? -1 : gameState.exportMoveSequence().length;
    }

    private void waitForRealMoves(int expected, long timeoutMillis) throws InterruptedException {
        waitUntil(() -> realMoves() == expected, timeoutMillis);
    }

    private void waitForAnalysisResults(int expectedSize, long timeoutMillis) throws InterruptedException {
        waitUntil(() -> zebra.getLastAnalysisResults().size() == expectedSize, timeoutMillis);
    }

    private void waitForAnalysisProgressGone(long timeoutMillis) throws InterruptedException {
        TextView progress = zebra.findViewById(R.id.status_analysis_progress);
        waitUntil(() -> progress == null || progress.getVisibility() == View.GONE, timeoutMillis);
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
        int expectedDiscs = 4 + expected;
        waitUntil(() -> {
            GameState gameState = zebra.getGameState();
            return gameState != null && gameState.getDisksPlayed() == expected
                    && zebra.getState().getBlackScore() + zebra.getState().getWhiteScore() == expectedDiscs;
        }, timeoutMillis);
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

    // Regression test: jumpToMove() used to truncate the "provided moves"
    // array to the target ply, so the engine had no memory of anything
    // played after it and redo() could never move past the jump target.
    // jumpToMove() now loads the whole analyzed game and walks back to the
    // target ply with undoMove() instead, so redo() should be able to walk
    // all the way back to the end of the game again.
    @Test
    public void testRedoWorksPastAJumpedToPosition() throws InterruptedException {
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

        for (int ply = targetPly + 1; ply <= originalDisksPlayed; ply++) {
            zebra.runOnUiThread(zebra::redo);
            waitForDisksPlayed(ply, 20000);
            assertEquals("redo() must reach ply " + ply + " after jumping back to "
                    + targetPly, ply, zebra.getGameState().getDisksPlayed());
        }
    }

    // Regression test: the engine's disksPlayed counts pass turns, the
    // analysis doesn't - jumpToMove() compared the two and polled forever
    // on any game with a pass. It also replays the whole game, which for a
    // finished game re-fired game-over and popped the Game Over dialog.
    @Test
    public void testJumpAcrossPassInFinishedGame() throws InterruptedException {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        intent.putExtra(Intent.EXTRA_TEXT, GAME_WITH_PASS);
        zebra.runOnUiThread(() -> zebra.onNewIntent(intent));
        waitForOpenendDialogs(true); // the Game Over dialog of the loaded game
        waitForRealMoves(GAME_WITH_PASS_MOVES, 20000);
        getInstrumentation().waitForIdleSync();

        zebra.runOnUiThread(zebra::analyzeGame);
        waitForAnalysisResults(GAME_WITH_PASS_MOVES, 180000);
        waitForAnalysisProgressGone(180000);
        waitForRealMoves(GAME_WITH_PASS_MOVES, 20000);

        int targetPly = GAME_WITH_PASS_MOVES - 6; // before the forced pass
        zebra.runOnUiThread(() -> zebra.jumpToMove(targetPly));
        waitUntil(() -> realMoves() == targetPly
                && zebra.getEngineState() == ZebraEngine.ENGINE_STATE.ES_USER_INPUT_WAIT, 20000);
        getInstrumentation().waitForIdleSync();

        assertEquals(targetPly, realMoves());
        assertNull("jumping inside a finished game must not show the Game Over dialog",
                zebra.getSupportFragmentManager().findFragmentByTag("dialog_gameover"));
    }

    // Loads the finished GAME_WITH_PASS and starts analyzing it, returning
    // once the analysis is visibly underway (its first drawer row is up), so
    // whatever the test does next really happens mid-analysis.
    private void startAnalyzingGameWithPass() throws InterruptedException {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        intent.putExtra(Intent.EXTRA_TEXT, GAME_WITH_PASS);
        zebra.runOnUiThread(() -> zebra.onNewIntent(intent));
        waitForOpenendDialogs(true); // the Game Over dialog of the loaded game
        waitForRealMoves(GAME_WITH_PASS_MOVES, 20000);
        getInstrumentation().waitForIdleSync();

        zebra.runOnUiThread(zebra::analyzeGame);
        View drawerHandle = zebra.findViewById(R.id.analysis_drawer_handle);
        waitUntil(() -> drawerHandle.getVisibility() == View.VISIBLE, 60000);
    }

    // Regression test: "New Game" from the menu cancels the analysis first
    // (as onOptionsItemSelected does), and the analysis finishing used to
    // restore the analyzed game over the new one.
    @Test
    public void testNewGameDuringAnalysisIsNotOverwritten() throws InterruptedException {
        startAnalyzingGameWithPass();

        zebra.runOnUiThread(() -> {
            zebra.cancelAnalysisIfRunning();
            zebra.startNewGameAndResetUI();
        });
        waitForRealMoves(0, 20000);
        waitForAnalysisProgressGone(20000);
        // time for a stale analysis to (wrongly) restore the old game
        Thread.sleep(3000);

        assertEquals(0, realMoves());
        assertTrue(zebra.getLastAnalysisResults().isEmpty());
    }

    // Regression test: undo during analysis cancelled the analysis but was
    // itself silently dropped (the engine was still on the analysis'
    // session); it now runs once the live game is restored.
    @Test
    public void testUndoDuringAnalysisIsAppliedAfterRestore() throws InterruptedException {
        startAnalyzingGameWithPass();

        zebra.runOnUiThread(zebra::undo);
        waitForAnalysisProgressGone(60000);
        waitForRealMoves(GAME_WITH_PASS_MOVES - 1, 20000);

        assertEquals(GAME_WITH_PASS_MOVES - 1, realMoves());
    }
}
