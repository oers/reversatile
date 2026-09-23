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
 * Evaluates every move of an already-finished game, one ply at a time.
 * <p>
 * Loads the engine <em>once</em> with the entire finished move sequence as
 * "provided moves" - the same mechanism used to load any saved/finished
 * game - so it replays without searching and lands exactly on the real
 * game-over position. From there, each ply's evaluation is obtained by
 * stepping backward with the engine's own {@code undoMove()} - the same
 * primitive the live Undo button uses - one ply at a time, waiting for
 * practice mode's search on the resulting position to settle before
 * undoing again. This naturally produces results newest-move-first
 * (matching the drawer's display order) without ever restarting the
 * engine or re-replaying an ever-growing move prefix for every ply.
 * <p>
 * This never touches the "live" game/engine state directly - the caller is
 * responsible for restoring it (e.g. by reloading the original move
 * sequence) once {@link Listener#onFinished} fires, finished or cancelled.
 */
public class GameAnalyzer {

    // Inactivity safety net, not a performance guarantee: some configured
    // search depths can legitimately take a while per position (this class
    // waits for the search to fully settle - see pollForReady - rather than
    // grabbing the first available estimate, so a strong search can easily
    // run past a minute on its own). Tracked via lastActivityAtMillis (bumped
    // on every eval update, see onBoardUpdate below) rather than by
    // re-arming a fresh postDelayed() per update - a real search at high
    // depth reports many times per ply (iterative deepening), and doing the
    // latter piled up one never-cancelled 60s timer per update, thousands of
    // them over a long analysis, which was itself the cause of the severe
    // slowdown/unresponsiveness this comment used to just call "hang".
    private static final long PLY_INACTIVITY_TIMEOUT_MILLIS = 60_000;

    // How often to check whether the current ply's search has actually
    // finished (see pollForReady) - cheap (one field read and one enum
    // compare), so this can be tight without costing anything real.
    private static final long POLL_INTERVAL_MILLIS = 50;

    // Minimum spacing between onPlyEvalUpdated notifications to the
    // listener. Iterative deepening can report a refined eval many times a
    // second at real search depths - unlike the inactivity watchdog fix
    // above, throttling latestWhiteScore itself isn't safe (pollForReady
    // needs the true latest value the instant the ply settles), but the UI
    // notification is just a live preview, so coalescing bursts of it down
    // to this rate is free: it's what was making the drawer's
    // notifyDataSetChanged()-per-update redraw frequent enough to starve
    // the main thread of time to handle a tap while analysis runs.
    private static final long MIN_EVAL_UI_UPDATE_INTERVAL_MILLIS = 150;

    public interface Listener {
        /** Fires once, right before ply's evaluation starts, so the UI can show it as "in progress". */
        void onPlyStarted(int ply, int total);

        /**
         * Fires every time the engine sends a refined evaluation for the
         * ply currently being analyzed - practice-mode search reports
         * progressively (iterative deepening), so this can fire several
         * times per ply before it's actually done. {@code interimEval} is
         * not yet final; {@link #onProgress} fires once when this ply is.
         */
        void onPlyEvalUpdated(int ply, int total, MoveEval interimEval);

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
    // The single GameState/session the whole analysis runs on, set once by
    // the initial newGame()'s onGameStateReady and reused for every undo
    // step after that - unlike the old restart-per-ply design, this never
    // changes for the lifetime of one start()/finish() cycle.
    private GameState gameState;
    // Bumped every time a new wait begins (the initial load, and every undo
    // step after it) and when the run ends, so a stale watchdog/poll from an
    // already-resolved wait can recognize itself as stale.
    private int currentAttemptId;
    // The ply the current wait is for.
    private int awaitedPly;
    // The latest white-perspective score seen for the ply currently being
    // awaited, or null until the first eval for it arrives.
    private Integer latestWhiteScore;
    // Wall-clock time of the last sign of life (ply start, or an eval
    // update) for the current attempt - what the self-rescheduling
    // inactivity watchdog (see armInactivityWatchdog) measures against.
    private long lastActivityAtMillis;
    // Wall-clock time of the last onPlyEvalUpdated notification actually
    // sent to the listener - see MIN_EVAL_UI_UPDATE_INTERVAL_MILLIS.
    private long lastEvalUiNotifyAtMillis;

    public GameAnalyzer(ZebraEngine engine) {
        this.engine = engine;
    }

    public boolean isRunning() {
        return running.get();
    }

    // True when start() was called on a game that had actually ended -
    // set by start(), read by pollForReady's very first call to decide
    // whether the landing position's "eval" can be taken as the exact,
    // already-known final disc differential (only valid once the game is
    // truly over) or needs a real search-based eval like every other ply
    // (e.g. when "Analyze Game" is invoked mid-game from the options menu).
    private boolean isGameOver;

    /**
     * Starts analyzing {@code finishedGame}. {@code baseConfig} supplies the
     * search-strength settings to evaluate with; engine function and
     * practice mode are overridden internally so every position pauses for
     * an eval instead of auto-playing. {@code isGameOver} must reflect
     * whether {@code finishedGame} had actually finished - it decides how
     * the most-recent ply's evaluation is obtained (see {@link #isGameOver}).
     */
    public void start(GameState finishedGame, EngineConfig baseConfig, boolean isGameOver, Listener listener) {
        if (running.get()) {
            return;
        }
        this.moves = finishedGame.exportMoveSequence();
        this.totalMoves = moves.length;
        // Auto-played forced moves are off because _droidzebra_undo_turn
        // keeps undoing through them, so one undoMove() could step back
        // several plies while advance() counts exactly one.
        this.analysisConfig = baseConfig
                .alterEngineFunction(GameSettingsConstants.FUNCTION_HUMAN_VS_HUMAN)
                .alterPracticeMode(true)
                .alterAutoForcedMoves(false);
        this.listener = listener;
        this.results = new ArrayList<>();
        this.isGameOver = isGameOver;
        this.finalBlackDiscs = finishedGame.getBlackPlayer().getDiscCount();
        this.finalWhiteDiscs = finishedGame.getWhitePlayer().getDiscCount();

        cancelled.set(false);
        running.set(true);

        if (totalMoves == 0) {
            finish(false, false);
            return;
        }

        // Analyze most-recent-move-first (totalMoves down to 1) rather than
        // in game order: the drawer displays newest-first (see
        // DroidZebra#updateAnalysisDrawer), and this is also exactly the
        // order undoMove() naturally walks in from the loaded final
        // position, so each result lands in its final row immediately and
        // the list fills top-to-bottom.
        final int attemptId = ++currentAttemptId;
        awaitedPly = totalMoves;
        latestWhiteScore = null;
        lastEvalUiNotifyAtMillis = 0;
        armInactivityWatchdog(attemptId);

        engine.newGame(moves, totalMoves, analysisConfig, new OnGameStateReadyListener() {
            @Override
            public void onGameStateReady(GameState freshGameState) {
                gameState = freshGameState;
                // Set up once and reused for every undo step after this -
                // unlike the old restart-per-ply design there's no new
                // GameState/listener per ply, so this must always read the
                // *current* attemptId/awaitedPly fields (kept up to date by
                // advance()) rather than close over a value captured here,
                // or every update past the first ply would be misjudged
                // stale and silently dropped.
                gameState.setGameStateListener(new GameStateListener() {
                    @Override
                    public void onBoard(GameState board) {
                        onBoardUpdate(board);
                    }
                });
                // A genuinely terminal position needs no search - see
                // pollForReady's requireEval - so this just waits for the
                // replay of the whole move sequence to finish landing on
                // it. When the game isn't actually over (mid-game
                // analysis), this landing position is just like any other
                // ply and needs a real search-based eval instead of the
                // exact-final-score shortcut, which would otherwise report
                // the raw current disc count as if it were the result.
                pollForReady(attemptId, totalMoves, !isGameOver);
            }
        });
    }

    /**
     * Stops the analysis. Interrupts whatever search is currently running
     * (rather than waiting for it to finish naturally, which is what
     * pollForReady's own cancelled check - the only thing that previously
     * stopped this - had to wait out) so cancellation actually takes effect
     * right away instead of after however long the in-flight ply's search
     * still had left to run, which at real search depths - especially in
     * the branchier midgame - could be the slowest part of the whole
     * interaction.
     */
    public void cancel() {
        cancelled.set(true);
        if (gameState != null) {
            engine.stopIfThinking(gameState);
        }
    }

    /**
     * Stops for good without reporting back, for when the listener's owner
     * goes away (e.g. the activity is destroyed on rotation).
     */
    public void release() {
        listener = null;
        cancel();
        currentAttemptId++;
        running.set(false);
    }

    private void onBoardUpdate(GameState board) {
        CandidateMove best = board.getBestMove();
        if (best != null && best.hasEval) {
            // Practice-mode search reports progressively (iterative
            // deepening) - each update here can still be refined by a
            // later, deeper one, so just record/display it and keep
            // waiting; pollForReady is what actually decides this ply is
            // done. No staleness check needed here: advance() only issues
            // the next undoMove() once pollForReady has already confirmed
            // the previous ply's search fully settled (ES_USER_INPUT_WAIT),
            // so there's no in-flight previous-ply update this could ever
            // race against.
            latestWhiteScore = normalizeToWhite(best.score, board.getSideToMove());
            // Just bump the timestamp the already-running watchdog checks
            // against - not another postDelayed(). A real search reports
            // many times per ply (iterative deepening), and scheduling a
            // fresh never-cancelled timer per update piled up thousands of
            // them over a long analysis at real search depths.
            long now = System.currentTimeMillis();
            lastActivityAtMillis = now;
            // Throttle the UI notification specifically (not the score
            // itself, which pollForReady always needs fresh) - see
            // MIN_EVAL_UI_UPDATE_INTERVAL_MILLIS.
            if (now - lastEvalUiNotifyAtMillis >= MIN_EVAL_UI_UPDATE_INTERVAL_MILLIS) {
                lastEvalUiNotifyAtMillis = now;
                notifyPlyEvalUpdated(awaitedPly, latestWhiteScore);
            }
        }
    }

    // The engine only returns to ES_USER_INPUT_WAIT once whatever it was
    // doing has genuinely finished - either the initial full-game replay
    // (landing on the real game-over position) or a practice-mode search
    // for a freshly undone position (see ZebraEngine's MSG_GET_USER_INPUT
    // handling, reached only after _droidzebra_compute_evals() returns, or
    // droidzebra-jni.c's post-game-over loop for the very first wait) - a
    // direct, reliable "this position is actually settled" signal, unlike
    // onBoard() firing (which just means "an updated estimate exists", not
    // "the search is over").
    private void pollForReady(int attemptId, int ply, boolean requireEval) {
        if (attemptId != currentAttemptId) {
            return;
        }
        boolean settled = engine.getState() == ZebraEngine.ENGINE_STATE.ES_USER_INPUT_WAIT;
        if (cancelled.get()) {
            // Once cancelled, don't hold out for a true final eval for
            // whatever ply is in flight - cancel() already interrupted its
            // search, so this just needs the engine to actually reach
            // ES_USER_INPUT_WAIT (normally within a poll tick or two of
            // that interrupt) before finish() can safely hand the engine
            // back to whatever the caller does next (e.g. jumping to a
            // different move). The half-finished ply's result, if any, is
            // simply dropped rather than added.
            if (settled) {
                finish(true, false);
            } else {
                mainHandler.postDelayed(() -> pollForReady(attemptId, ply, requireEval), POLL_INTERVAL_MILLIS);
            }
            return;
        }
        boolean ready = settled && (!requireEval || latestWhiteScore != null);
        if (ready) {
            int whiteScore = requireEval ? latestWhiteScore : exactFinalScore();
            addResult(ply, whiteScore);
            advance(ply);
            return;
        }
        mainHandler.postDelayed(() -> pollForReady(attemptId, ply, requireEval), POLL_INTERVAL_MILLIS);
    }

    // Self-rescheduling, unlike a plain postDelayed() per sign-of-life: at
    // most one of these is ever in flight per attempt. Each firing re-reads
    // lastActivityAtMillis (bumped by onBoardUpdate/advance/start) and
    // either times out or reschedules itself for exactly the remaining
    // time - so an active search that keeps reporting progress never
    // accumulates more than this one pending callback, no matter how many
    // eval updates arrive.
    private void armInactivityWatchdog(int attemptId) {
        lastActivityAtMillis = System.currentTimeMillis();
        checkInactivity(attemptId);
    }

    private void checkInactivity(int attemptId) {
        if (attemptId != currentAttemptId) {
            return; // this attempt already resolved, or a later one superseded it
        }
        long elapsed = System.currentTimeMillis() - lastActivityAtMillis;
        if (elapsed >= PLY_INACTIVITY_TIMEOUT_MILLIS) {
            finish(true, true);
            return;
        }
        mainHandler.postDelayed(() -> checkInactivity(attemptId), PLY_INACTIVITY_TIMEOUT_MILLIS - elapsed);
    }

    private void advance(int justFinishedPly) {
        if (justFinishedPly <= 1) {
            finish(false, false);
            return;
        }
        if (cancelled.get()) {
            finish(true, false);
            return;
        }
        int nextPly = justFinishedPly - 1;
        final int attemptId = ++currentAttemptId;
        awaitedPly = nextPly;
        latestWhiteScore = null;
        lastEvalUiNotifyAtMillis = 0;
        notifyPlyStarted(nextPly);
        armInactivityWatchdog(attemptId);
        // undoMove() always lands on a position with a real move to search
        // (it internally absorbs any forced pass on the way, same as the
        // live Undo button), so a genuine eval is always expected next.
        engine.undoMove(gameState);
        pollForReady(attemptId, nextPly, true);
    }

    // Same scoring as Zebra's exact endgame evals (see end.c): empty squares
    // left at the end count for the winner.
    private int exactFinalScore() {
        int diff = finalWhiteDiscs - finalBlackDiscs;
        int empties = 64 - finalWhiteDiscs - finalBlackDiscs;
        if (diff > 0) {
            diff += empties;
        } else if (diff < 0) {
            diff -= empties;
        }
        return diff * 128;
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

    private void notifyPlyEvalUpdated(int ply, int whiteScore) {
        MoveEval interim = MoveEval.interim(ply, new Move(moves[ply - 1]), whiteScore);
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onPlyEvalUpdated(ply, totalMoves, interim);
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
        // Stops any poll or watchdog still scheduled for this run - after a
        // timeout the in-flight pollForReady would otherwise keep polling.
        currentAttemptId++;
        List<MoveEval> finalResults = Collections.unmodifiableList(new ArrayList<>(results));
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onFinished(finalResults, wasCancelled, timedOut);
            }
        });
    }
}
