package com.shurik.droidzebra;

public class MoveList {
    private byte[] moves;

    MoveList(byte[] moves) {
        this.moves = moves;
    }

    MoveList() {
        moves = new byte[0];
    }

    public int length() {
        return moves.length;
    }

    public int getMoveInt(int i) {
        return moves[i];
    }

    public String getMoveText(int i) {
        return new Move(moves[i]).getText(); //TODO optimize - get rid of 'new Move'
    }

    public byte getMoveByte(int i) {
        return moves[i];
    }
}
