package com.shurik.droidzebra;

import org.json.JSONArray;
import org.json.JSONException;


public class ByteBoard {

    private final byte[] board;
    private final int boardSize;

    ByteBoard(JSONArray jsonByteBoard, int boardSize) throws JSONException {
        board = new byte[boardSize * boardSize];
        for (int i = 0; i < jsonByteBoard.length(); i++) {
            JSONArray row = jsonByteBoard.getJSONArray(i);
            for (int j = 0; j < row.length(); j++) {
                board[i * boardSize + j] = (byte) row.getInt(j);
            }
        }
        this.boardSize = boardSize;
    }

    ByteBoard(int boardSize) {
        board = new byte[boardSize * boardSize];
        this.boardSize = boardSize;
    }

    public byte get(int i, int j) {
        return board[i * boardSize + j];
    }
}
