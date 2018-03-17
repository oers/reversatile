package de.earthlingz.oerszebra;

import com.shurik.droidzebra.ZebraEngine;

/**
 * Created by stefan on 17.03.2018.
 */

public class BoardState {
    final public static int boardSize = 8;

    private FieldState mBoard[][] = new FieldState[boardSize][boardSize];
    private ZebraEngine.Move mLastMove = null;
    private int mWhiteScore = 0;
    private int mBlackScore = 0;

    public BoardState() {
        super();
    }

    public FieldState[][] getmBoard() {
        return mBoard;
    }

    public void setmBoard(FieldState[][] mBoard) {
        this.mBoard = mBoard;
    }

    public ZebraEngine.Move getmLastMove() {
        return mLastMove;
    }

    public void setmLastMove(ZebraEngine.Move mLastMove) {
        this.mLastMove = mLastMove;
    }

    public int getmWhiteScore() {
        return mWhiteScore;
    }

    public void setmWhiteScore(int mWhiteScore) {
        this.mWhiteScore = mWhiteScore;
    }

    public int getmBlackScore() {
        return mBlackScore;
    }

    public void setmBlackScore(int mBlackScore) {
        this.mBlackScore = mBlackScore;
    }

    public void setBoard(byte[] board) {
        for (int i = 0; i < boardSize; i++)
            for (int j = 0; j < boardSize; j++) {
                mBoard[i][j].set(board[i * boardSize + j]);
            }
    }

    public void reset() {
        mLastMove = null;
        mWhiteScore = mBlackScore = 0;
        for (int i = 0; i < boardSize; i++)
            for (int j = 0; j < boardSize; j++)
                mBoard[i][j] = new FieldState(ZebraEngine.PLAYER_EMPTY);
    }
}
