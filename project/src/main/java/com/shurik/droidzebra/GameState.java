package com.shurik.droidzebra;


import java.util.Arrays;
import java.util.List;

public class GameState {
    private int sideToMove;
    private ZebraPlayerStatus blackPlayer = new ZebraPlayerStatus();
    private ZebraPlayerStatus whitePlayer = new ZebraPlayerStatus();
    private int disksPlayed;
    private byte[] moveSequence = new byte[0];
    private CandidateMove[] candidateMoves = new CandidateMove[0];
    private String opening;
    private int lastMove;
    private int nextMove;
    private ByteBoard byteBoard = new ByteBoard();

    GameState(int boardSize) {
        setDisksPlayed(0);
        this.moveSequence = new byte[2 * boardSize * boardSize];
    }

    GameState(int boardSize, List<Move> moves) {
        setDisksPlayed(moves.size());
        this.moveSequence = toBytesWithBoardSize(moves, boardSize);
    }

    GameState(int boardSize, byte[] moves, int movesPlayed) {
        setDisksPlayed(movesPlayed);
        this.moveSequence = Arrays.copyOf(moves, boardByteLength(boardSize));
    }

    private int boardByteLength(int boardSize) {
        return boardSize * boardSize * 2;
    }

    private byte[] toBytesWithBoardSize(List<Move> moves, int boardSize) {//TODO add boardsize there to be consistent
        byte[] moveBytes = new byte[boardByteLength(boardSize)];
        for (int i = 0; i < moves.size() && i < moveBytes.length; i++) {
            moveBytes[i] = (byte) moves.get(i).getMoveInt();
        }
        return moveBytes;
    }


    public ByteBoard getByteBoard() {
        return byteBoard;
    }

    void setSideToMove(int sideToMove) {
        this.sideToMove = sideToMove;
    }

    public int getSideToMove() {
        return sideToMove;
    }

    void setBlackPlayer(ZebraPlayerStatus blackPlayer) {
        this.blackPlayer = blackPlayer;
    }

    public ZebraPlayerStatus getBlackPlayer() {
        return blackPlayer;
    }

    void setWhitePlayer(ZebraPlayerStatus whitePlayer) {
        this.whitePlayer = whitePlayer;
    }

    public ZebraPlayerStatus getWhitePlayer() {
        return whitePlayer;
    }

    void setDisksPlayed(int disksPlayed) {
        this.disksPlayed = disksPlayed;
    }

    public int getDisksPlayed() {
        return disksPlayed;
    }

    public byte[] exportMoveSequence() {
        return moveSequence.clone();
    }

    void setCandidateMoves(CandidateMove[] candidateMoves) {
        this.candidateMoves = candidateMoves;
    }

    //TODO possible encapsulation leak
    public CandidateMove[] getCandidateMoves() {
        return candidateMoves;
    }

    void setOpening(String opening) {
        this.opening = opening;
    }

    public String getOpening() {
        return opening;
    }

    void setLastMove(int lastMove) {
        this.lastMove = lastMove;
    }

    public int getLastMove() {
        return lastMove;
    }

    void setNextMove(int nextMove) {
        this.nextMove = nextMove;
    }

    public int getNextMove() {
        return nextMove;
    }

    void addCandidateMoveEvals(CandidateMove[] cmoves) {
        for (CandidateMove candidateMoveWithEval : cmoves) {
            for (int i = 0, candidateMovesLength = candidateMoves.length; i < candidateMovesLength; i++) {
                CandidateMove candidateMove = candidateMoves[i];
                if (candidateMove.mMove.getMoveInt() == candidateMoveWithEval.mMove.getMoveInt()) {
                    candidateMoves[i] = candidateMoveWithEval;
                }
            }
        }
    }


    public void updateMoveSequence(MoveList blackMoveList, MoveList whiteMoveList) {
        for (int i = 0; i < blackMoveList.length(); i++) {
            moveSequence[2 * i] = blackMoveList.getMoveByte(i);
        }

        for (int i = 0; i < whiteMoveList.length(); i++) {
            moveSequence[2 * i + 1] = whiteMoveList.getMoveByte(i);
        }
    }

    void setByteBoard(ByteBoard byteBoard) {
        this.byteBoard = byteBoard;
    }


    public String getMoveSequenceAsString() {
        StringBuilder sbMoves = new StringBuilder();

        if (moveSequence != null) {

            for (byte move1 : moveSequence) {
                if (move1 != 0x00) {
                    Move move = new Move(move1);
                    sbMoves.append(move.getText());
                    if (move1 == lastMove) {
                        break;
                    }
                }
            }
        }
        return sbMoves.toString();
    }

}
