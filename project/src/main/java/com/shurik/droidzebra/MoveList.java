package com.shurik.droidzebra;

public class MoveList {
    private byte[] moves;

    MoveList(byte[] moves) {
        this.moves = moves;
    }

    public int length() {
        return moves.length;
    }

    public int getIntMove(int i) {
        return moves[i];
    }
}
