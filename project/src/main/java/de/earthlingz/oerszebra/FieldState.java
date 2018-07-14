package de.earthlingz.oerszebra;

import com.shurik.droidzebra.ZebraEngine;

/**
 * Created by stefan on 17.03.2018.
 */
public class FieldState {
    private final static byte ST_FLIPPED = 0x01;
    private byte state;
    private byte flags;

    FieldState(byte state) {
        this.state = state;
        flags = 0;
    }

    public void set(byte newState) { //TODO encapsulation leak
        if (newState != ZebraEngine.PLAYER_EMPTY && state != ZebraEngine.PLAYER_EMPTY && state != newState)
            flags |= ST_FLIPPED;
        else
            flags &= ~ST_FLIPPED;
        state = newState;
    }

    public byte getState() {
        return state;
    }

    public boolean isEmpty() {
        return state == ZebraEngine.PLAYER_EMPTY;
    }

    public boolean isBlack() {
        return state == ZebraEngine.PLAYER_BLACK;
    }

    public boolean isFlipped() {
        return (flags & ST_FLIPPED) > 0;
    }
}
