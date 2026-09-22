package de.earthlingz.oerszebra.analysis;

import android.os.Handler;
import android.os.Looper;

import com.shurik.droidzebra.CandidateMove;
import com.shurik.droidzebra.EngineConfig;
import com.shurik.droidzebra.GameState;
import com.shurik.droidzebra.GameStateListener;
import com.shurik.droidzebra.Move;
import com.shurik.droidzebra.ZebraEngine;
import com.shurik.droidzebra.ZebraEngine.OnGameStateReadyListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import de.earthlingz.oerszebra.GameSettingsConstants;

/**
 * Evaluates every move of an already-finished game, one ply at a time, by
 * replaying an increasing prefix of the move sequence through the engine
 * (reusing the same practice-mode candidate-eval pipeline used live) and
 * reading off the resulting position's best-move score.
 * <p>
 * This never touches the "live" game/engine state directly - the caller is
 * responsible for restoring it (e.g. by reloading the original move
 * sequence) once {@link Listener#onFinished} fires, finished or cancelled.
 */
public class GameAnalyzer {

    // Safety net, not a performance guarantee: some configured search depths
    // can legitimately take a while per position, but a position that never
    // calls back at all (seen in practice - the analyzer just sat there
    // indefinitely with no progress) needs to eventually give up rather than
    // leave the drawer stuck on "Analyzing..." forever with no way out
    // short of force-closing the app.
    private static final long PLY_TIMEOUT_MILLIS = 60_000;

    // The engine runs its own persistent native loop (ZebraEngine.EngineThread)
    // around a single blocking zePlay() call per newGame(); the Java-side
    // ES_READY2PLAY state (which newGame()'s stopGame()/waitForReadyToPlay()
    // dance depends on) is only set once that call returns. A practice-mode
    // eval callback (onBoard(), below) can fire slightly before the native
    // side has fully settled back into ES_USER_INPUT_WAIT - calling
    // engine.newGame() again immediately in that narrow window raced it in
    // testing (stopGame() missing its ES_USER_INPUT_WAIT special case, then
    // zePlay() never returning - the exact indefinite-hang symptom this
    // class's watchdog now recovers from, but shouldn't need to). Giving the
    // native side a moment to settle before issuing the next ply avoids the
    // race outright instead of just recovering from it after the fact.
    private static final long NEXT_PLY_SETTLE_MILLIS = 300;

    public interface Listener {
        /** Fires once, right before ply's evaluation starts, so the UI can show it as "in progress". */
        void onPlyStarted(int ply, int total);

        void onProgress(int done, int total, MoveEval latest);

        void onFinished(List<MoveEval> results, boolean wasCancelled, boolean timedOut);
    }

    private final ZebraEngine engine;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private byte[] moves;
    private int totalMoves;
    private EngineConfig analysisConfig;
    private Listener listener;
    private List<MoveEval> results;
    private int finalBlackDiscs;
    private int finalWhiteDiscs;
    // Bumped every time a new ply's engine.newGame() call is issued, so a
    // watchdog (or a stray callback) scheduled for an earlier ply can tell
    // it's stale once that ply has already resolved and analysis has moved
    // on, instead of acting on/aborting the wrong (now current) attempt.
    private int currentAttemptId;

    public GameAnalyzer(ZebraEngine engine) {
        this.engine = engine;
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * Starts analyzing {@code finishedGame}. {@code baseConfig} supplies the
     * search-strength settings to evaluate with; engine function and
     * practice mode are overridden internally so every position pauses for
     * an eval instead of auto-playing.
     */
    public void start(GameState finishedGame, EngineConfig baseConfig, Listener listener) {
        if (running.get()) {
            return;
        }
        this.moves = finishedGame.exportMoveSequence();
        this.totalMoves = moves.length;
        this.analysisConfig = baseConfig
                .alterEngineFunction(GameSettingsConstants.FUNCTION_HUMAN_VS_HUMAN)
                .alterPracticeMode(true);
        this.listener = listener;
        this.results = new ArrayList<>();
        this.finalBlackDiscs = finishedGame.getBlackPlayer().getDiscCount();
        this.finalWhiteDiscs = finishedGame.getWhitePlayer().getDiscCount();

        cancelled.set(false);
        running.set(true);
        currentAttemptId = 0;

        if (totalMoves == 0) {
            finish(false, false);
            return;
        }
        // Analyze most-recent-move-first (totalMoves down to 1) rather than
        // in game order: each ply's evaluation is independent (newGame(...)
        // always replays from scratch), and the drawer displays newest-first
        // (see DroidZebra#updateAnalysisDrawer), so analyzing in that same
        // order lets each result land in its final row immediately and the
        // list fill top-to-bottom, instead of every new result briefly
        // becoming the top row before being pushed down by the next one.
        analyzePly(totalMoves);
    }

    /** Stops the analysis after whatever ply is currently in flight finishes. */
    public void cancel() {
        cancelled.set(true);
    }

    private void analyzePly(int ply) {
        if (cancelled.get()) {
            finish(true, false);
            return;
        }

        if (ply == totalMoves) {
            // Terminal position: no further move to search from, so use the
            // exact final score instead of an engine search.
            addResult(ply, exactFinalScore());
            advance(ply);
            return;
        }

        final int attemptId = ++currentAttemptId;
        notifyPlyStarted(ply);
        mainHandler.postDelayed(() -> onPlyTimedOut(attemptId), PLY_TIMEOUT_MILLIS);

        engine.newGame(moves, ply, analysisConfig, new OnGameStateReadyListener() {
            @Override
            public void onGameStateReady(GameState gameState) {
                gameState.setGameStateListener(new GameStateListener() {
                    @Override
                    public void onBoard(GameState board) {
                        if (attemptId != currentAttemptId) {
                            return; // stale - this attempt already timed out
                        }
                        CandidateMove best = board.getBestMove();
                        if (best != null && best.hasEval) {
                            gameState.removeGameStateListener();
                            addResult(ply, normalizeToWhite(best.score, board.getSideToMove()));
                            advance(ply);
                        }
                    }

                    @Override
                    public void onGameOver() {
                        // Position after this ply turned out to be terminal
                        // (shouldn't normally happen before the last ply of
                        // an already-completed game) - fall back to the
                        // known final score rather than stalling.
                        if (attemptId != currentAttemptId) {
                            return;
                        }
                        gameState.removeGameStateListener();
                        addResult(ply, exactFinalScore());
                        advance(ply);
                    }
                });
            }
        });
    }

    private void onPlyTimedOut(int attemptId) {
        if (attemptId != currentAttemptId) {
            return; // this ply already resolved (or analysis already stopped)
        }
        finish(true, true);
    }

    private void advance(int justFinishedPly) {
        if (justFinishedPly <= 1) {
            finish(false, false);
        } else {
            final int nextPly = justFinishedPly - 1;
            mainHandler.postDelayed(() -> analyzePly(nextPly), NEXT_PLY_SETTLE_MILLIS);
        }
    }

    private int exactFinalScore() {
        return (finalWhiteDiscs - finalBlackDiscs) * 128;
    }

    private static int normalizeToWhite(int rawScore, int sideToMove) {
        return sideToMove == ZebraEngine.PLAYER_WHITE ? rawScore : -rawScore;
    }

    private void notifyPlyStarted(int ply) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onPlyStarted(ply, totalMoves);
            }
        });
    }

    private void addResult(int ply, int whiteScore) {
        MoveEval eval = new MoveEval(ply, new Move(moves[ply - 1]), whiteScore);
        results.add(eval);
        // Plies are no longer analyzed in ascending order (see start()), so
        // the completed count is results.size(), not ply itself.
        int done = results.size();
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onProgress(done, totalMoves, eval);
            }
        });
    }

    private void finish(boolean wasCancelled, boolean timedOut) {
        // Idempotent: a watchdog firing at nearly the same moment as a ply
        // that just legitimately resolved could otherwise call this twice.
        if (!running.compareAndSet(true, false)) {
            return;
        }
        List<MoveEval> finalResults = Collections.unmodifiableList(new ArrayList<>(results));
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onFinished(finalResults, wasCancelled, timedOut);
            }
        });
    }
}
