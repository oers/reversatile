package de.earthlingz.oerszebra;

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

    //TODO encapsulate this
    public FieldState[][] getBoard() {
        return board;
    }

    @Nullable
    public Move getLastMove() {
        return lastMove;
    }

    public int getWhiteScore() {
        return whiteScore;
    }

    public int getBlackScore() {
        return blackScore;
    }

    public CandidateMove[] getMoves() {
        return possibleMoves.getMoves();
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

    public Move getNextMove() {
        return nextMove;
    }

    public void processGameOver() {
        possibleMoves.setMoves(new CandidateMove[]{});
        int max = getBoard().length * getBoard().length;
        if (getBlackScore() + getWhiteScore() < max) {
            //adjust result
            if (getBlackScore() > getWhiteScore()) {
                this.blackScore = max - getWhiteScore();
            } else {
                this.whiteScore = max - getBlackScore();
            }
        }
    }

    public boolean update(GameState gameState) {
        boolean boardChanged = updateBoard(gameState.getBoard());

        this.blackScore = gameState.getBlackPlayer().getDiscCount();
        this.whiteScore = gameState.getWhitePlayer().getDiscCount();

        byte lastMove = (byte) gameState.getLastMove();
        this.lastMove = lastMove == Move.PASS ? null : new Move(lastMove);

        byte moveNext = (byte) gameState.getNextMove();
        this.nextMove = moveNext == Move.PASS ? null : new Move(moveNext);


        possibleMoves.setMoves(gameState.getCandidateMoves());

        return boardChanged;
    }
}
