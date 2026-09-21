package com.shurik.droidzebra;

/**
 * Created by stefan on 18.03.2018.
 */ // candidate move with evals
public class CandidateMove extends Move {
    public final boolean hasEval;
    public final String evalShort;
    private final String evalLong;
    public final boolean isBest;
    // raw engine score (128ths of a disc, from the mover's perspective); only
    // meaningful when hasEval is true
    public final int score;

    CandidateMove(int move) {
        super(move);
        hasEval = false;
        evalShort = null;
        isBest = false;
        evalLong = null;
        score = 0;
    }

    CandidateMove(int move, String evalShort, String evalLong, boolean best, int score) {
        super(move);
        this.evalShort = evalShort;
        this.evalLong = evalLong;
        isBest = best;
        hasEval = true;
        this.score = score;
    }

}
