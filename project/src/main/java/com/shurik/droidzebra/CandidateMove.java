package com.shurik.droidzebra;

/**
 * Created by stefan on 18.03.2018.
 */ // candidate move with evals
public class CandidateMove {
    public final Move mMove;
    public final boolean mHasEval;
    public final String mEvalShort;
    public final boolean mBest;

    public CandidateMove(Move move) {
        mMove = move;
        mHasEval = false;
        mEvalShort = null;
        mBest = false;
    }

    public CandidateMove(Move move, String evalShort, String evalLong, boolean best) {
        mMove = move;
        mEvalShort = evalShort;
        mBest = best;
        mHasEval = true;
    }
}
