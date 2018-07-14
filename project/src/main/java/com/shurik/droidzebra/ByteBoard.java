package com.shurik.droidzebra;

import org.json.JSONArray;
import org.json.JSONException;


public class ByteBoard {

    private final byte[] board;
    private final int boardSize;

    public ByteBoard(JSONArray zeboard, int boardSize) throws JSONException {
        board = new byte[boardSize * boardSize];
        for (int i = 0; i < zeboard.length(); i++) {
            JSONArray row = zeboard.getJSONArray(i);
            for (int j = 0; j < row.length(); j++) {
                board[i * boardSize + j] = (byte) row.getInt(j);
            }
        }
        this.boardSize = boardSize;
    }

    public ByteBoard() {
        board = new byte[0];
        this.boardSize = 0;
    }

    public byte get(int i, int j) {
        return board[i * boardSize + j];
    }
}
