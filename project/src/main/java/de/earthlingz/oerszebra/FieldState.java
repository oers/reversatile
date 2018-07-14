package de.earthlingz.oerszebra;

import com.shurik.droidzebra.ZebraEngine;

/**
 * Created by stefan on 17.03.2018.
 */
public class FieldState {
    private final static byte ST_FLIPPED = 0x01;
    private byte state;
    private byte mFlags;

    FieldState(byte state) {
        this.state = state;
        mFlags = 0;
    }

    public void set(byte newState) {
        if (newState != ZebraEngine.PLAYER_EMPTY && state != ZebraEngine.PLAYER_EMPTY && state != newState)
            mFlags |= ST_FLIPPED;
        else
            mFlags &= ~ST_FLIPPED;
        state = newState;
    }

    public byte getState() {
        return state;
    }

    public boolean isEmpty() {
        return getState() == ZebraEngine.PLAYER_EMPTY;
    }

    public boolean isBlack() {
        return getState() == ZebraEngine.PLAYER_BLACK;
    }

    public boolean isFlipped() {
        return (mFlags & ST_FLIPPED) > 0;
    }
}
