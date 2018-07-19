package com.shurik.droidzebra;

/**
 * Created by stefan on 18.03.2018.
 */ // candidate move with evals
public class CandidateMove extends Move {
    public final boolean hasEval;
    public final String evalShort;
    private String evalLong;
    public final boolean isBest;

    public CandidateMove(int move) {
        super(move);
        hasEval = false;
        evalShort = null;
        isBest = false;
    }

    public CandidateMove(int move, String evalShort, String evalLong, boolean best) {
        super(move);
        this.evalShort = evalShort;
        this.evalLong = evalLong;
        isBest = best;
        hasEval = true;
    }

}
