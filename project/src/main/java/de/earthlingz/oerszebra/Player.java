package de.earthlingz.oerszebra;

import com.shurik.droidzebra.ZebraEngine;

public enum Player {
    PLAYER_BLACK (ZebraEngine.PLAYER_BLACK),
    PLAYER_EMPTY (ZebraEngine.PLAYER_EMPTY),
    PLAYER_WHITE (ZebraEngine.PLAYER_WHITE);

    final int zebra_engine_byte;
    Player (int zebra_engine_byte) {
        this.zebra_engine_byte = zebra_engine_byte;
    }

    private Player opponent;
    static {
        PLAYER_EMPTY.opponent = PLAYER_EMPTY;
        PLAYER_BLACK.opponent = PLAYER_WHITE;
        PLAYER_WHITE.opponent = PLAYER_BLACK;
    }

    public Player getOpponent() {
       return opponent;
    }
}
