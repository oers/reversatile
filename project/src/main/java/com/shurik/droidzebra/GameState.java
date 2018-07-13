package com.shurik.droidzebra;

public class GameState {
    private byte[] board;
    private int sideToMove;
    private ZebraPlayerStatus blackPlayer = new ZebraPlayerStatus();
    private ZebraPlayerStatus whitePlayer = new ZebraPlayerStatus();
    private int disksPlayed;
    private byte[] moveSequence = new byte[0];
    private CandidateMove[] candidateMoves = new CandidateMove[0];
    private String opening;
    private int lastMove;
    private int nextMove;

    void setBoard(byte[] board) {
        this.board = board;
    }

    public byte[] getBoard() {
        return board;
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

    void setMoveSequence(byte[] moveSequence) {
        this.moveSequence = moveSequence;
    }

    public byte[] getMoveSequence() {
        return moveSequence;
    }

    void setCandidateMoves(CandidateMove[] candidateMoves) {
        this.candidateMoves = candidateMoves;
    }

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
}
