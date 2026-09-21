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

    public MoveEval(int ply, Move move, int score) {
        this.ply = ply;
        this.move = move;
        this.score = score;
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
