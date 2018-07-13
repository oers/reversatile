package com.shurik.droidzebra;

public class ZebraPlayerStatus {
    private String time;
    private float eval;
    private int discCount;
    private byte[] moves;

    ZebraPlayerStatus() {
        moves = new byte[0];
    }

    ZebraPlayerStatus(String time, float eval, int discCount, byte[] moves) {
        this.time = time;
        this.eval = eval;
        this.discCount = discCount;
        this.moves = new byte[0];
        this.moves = moves;
    }

    public String getTime() {
        return time;
    }

    public float getEval() {
        return eval;
    }

    public int getDiscCount() {
        return discCount;
    }

    public byte[] getMoves() {
        return moves; //TODO potential encapsulation problem, someone can mutate moves
    }
}
