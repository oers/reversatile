package de.earthlingz.oerszebra.analysis;

import com.shurik.droidzebra.Move;

/**
 * The engine's evaluation of the position right after one played move,
 * normalized to White's perspective: positive favors White, negative
 * favors Black (matching the app's board-view sign convention).
 */
public class MoveEval {
    private final int ply;
    private final Move move;
    private final int score;
    private final boolean pending;
    private final boolean inProgress;

    public MoveEval(int ply, Move move, int score) {
        this(ply, move, score, false, false);
    }

    private MoveEval(int ply, Move move, int score, boolean pending, boolean inProgress) {
        this.ply = ply;
        this.move = move;
        this.score = score;
        this.pending = pending;
        this.inProgress = inProgress;
    }

    /** A placeholder row for a ply whose evaluation is still being computed. */
    public static MoveEval pending(int ply, Move move) {
        return new MoveEval(ply, move, 0, true, true);
    }

    /**
     * A refined but not-yet-final evaluation for the ply currently being
     * analyzed - unlike {@link #pending}, a real score is already known
     * (iterative deepening reports several of these per ply before it
     * settles), but the ply isn't done yet.
     */
    public static MoveEval interim(int ply, Move move, int score) {
        return new MoveEval(ply, move, score, false, true);
    }

    /** True if this is a {@link #pending} placeholder, not a real evaluation. */
    public boolean isPending() {
        return pending;
    }

    /**
     * True for both {@link #pending} and {@link #interim} rows - the ply
     * currently being analyzed, whether or not a score has arrived for it
     * yet. False once the ply is finalized (its result added to the game's
     * completed list) or for any already-finished ply.
     */
    public boolean isInProgress() {
        return inProgress;
    }

    /** 1-based move number within the game. */
    public int getPly() {
        return ply;
    }

    public Move getMove() {
        return move;
    }

    /** Raw engine score, 128ths of a disc, positive favors White. */
    public int getScore() {
        return score;
    }

    /** Score expressed in disc units (e.g. +3.5), positive favors White. */
    public double getDiscs() {
        return score / 128.0;
    }
}
