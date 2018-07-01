package de.earthlingz.oerszebra;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.shurik.droidzebra.*;

/**
 * Created by stefan on 17.03.2018.
 */

public class BoardState {
    final public static int boardSize = 8;

    private FieldState board[][] = new FieldState[boardSize][boardSize];
    private Move lastMove = null;
    private int whiteScore = 0;
    private int blackScore = 0;
    private final CandidateMoves possibleMoves = new CandidateMoves();
    private Move nextMove;

    public BoardState() {
        super();
    }

    public FieldState[][] getBoard() {
        return board;
    }

    @Nullable
    public Move getLastMove() {
        return lastMove;
    }

    private void setLastMove(Move lastMove) {
        this.lastMove = lastMove;
    }

    public int getWhiteScore() {
        return whiteScore;
    }

    private void setWhiteScore(int whiteScore) {
        this.whiteScore = whiteScore;
    }

    public int getBlackScore() {
        return blackScore;
    }

    private void setBlackScore(int blackScore) {
        this.blackScore = blackScore;
    }

    public CandidateMove[] getMoves() {
        return possibleMoves.getMoves();
    }

    private void setPossibleMoves(@NonNull CandidateMove[] moves) {
        possibleMoves.setMoves(moves);
    }

    public boolean isValidMove(Move move) {
        for (CandidateMove m : possibleMoves.getMoves()) {
            if (m.mMove.getX() == move.getX() && m.mMove.getY() == move.getY()) {
                return true;
            }
        }
        return false;
    }

    private boolean updateBoard(byte[] board) {
        if (board == null) {
            return false;
        }

        boolean changed = false;
        //only update the board if anything has changed
        for (int i = 0; !changed && i < boardSize; i++) {
            for (int j = 0; !changed && j < boardSize; j++) {
                byte newState = board[i * boardSize + j];
                if (this.board[i][j].mState != newState) {
                    changed = true;
                }
            }
        }

        if (changed) {
            for (int i = 0; i < boardSize; i++) {
                for (int j = 0; j < boardSize; j++) {
                    byte newState = board[i * boardSize + j];
                    this.board[i][j].set(newState); //this also remembers if a flip has happened
                }
            }
        }

        return changed;

    }

    public void reset() {
        lastMove = null;
        whiteScore = blackScore = 0;
        for (int i = 0; i < boardSize; i++)
            for (int j = 0; j < boardSize; j++)
                board[i][j] = new FieldState(ZebraEngine.PLAYER_EMPTY);
    }

    private void setNextMove(Move nextMove) {
        this.nextMove = nextMove;
    }

    public Move getNextMove() {
        return nextMove;
    }

    public void processGameOver() {
        setPossibleMoves(new CandidateMove[]{});
        int max = getBoard().length * getBoard().length;
        if (getBlackScore() + getWhiteScore() < max) {
            //adjust result
            if (getBlackScore() > getWhiteScore()) {
                setBlackScore(max - getWhiteScore());
            } else {
                setWhiteScore(max - getBlackScore());
            }
        }
    }

    public boolean update(ZebraBoard zebraBoard) {
        boolean boardChanged = updateBoard(zebraBoard.getBoard());

        setBlackScore(zebraBoard.getBlackPlayer().getDiscCount());
        setWhiteScore(zebraBoard.getWhitePlayer().getDiscCount());
        byte lastMove = (byte) zebraBoard.getLastMove();
        setLastMove(lastMove == Move.PASS ? null : new Move(lastMove));

        byte moveNext = (byte) zebraBoard.getNextMove();
        setNextMove(moveNext == Move.PASS ? null : new Move(moveNext));


        setPossibleMoves(zebraBoard.getCandidateMoves());

        return boardChanged;
    }
}
