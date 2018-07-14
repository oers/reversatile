package com.shurik.droidzebra;

public class ZebraPlayerStatus {
    private String time;
    private float eval;
    private int discCount;
    private MoveList moveList;

    ZebraPlayerStatus() {
        moveList = new MoveList();
    }

    ZebraPlayerStatus(String time, float eval, int discCount, MoveList moveList) {
        this.time = time;
        this.eval = eval;
        this.discCount = discCount;
        this.moveList = moveList;
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

    public MoveList getMoveList() {
        return moveList;
    }
}
