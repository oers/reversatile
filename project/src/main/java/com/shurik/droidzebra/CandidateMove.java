package com.shurik.droidzebra;

/**
 * Created by stefan on 18.03.2018.
 */ // candidate move with evals
public class CandidateMove extends Move {
    public final boolean mHasEval;
    public final String mEvalShort;
    public final boolean mBest;

    public CandidateMove(int move) {
        super(move);
        mHasEval = false;
        mEvalShort = null;
        mBest = false;
    }

    public CandidateMove(int move, String evalShort, String evalLong, boolean best) {
        super(move);
        mEvalShort = evalShort;
        mBest = best;
        mHasEval = true;
    }

}
