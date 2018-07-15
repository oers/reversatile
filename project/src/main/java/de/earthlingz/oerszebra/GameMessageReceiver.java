package de.earthlingz.oerszebra;

import com.shurik.droidzebra.GameState;

interface GameMessageReceiver {
    default void onBoard(GameState board) {
    }

    default void onPass() {
    }

    default void onGameStart() {
    }

    default void onGameOver() {
    }

    default void onMoveStart() {
    }

    default void onMoveEnd() {
    }

    default void onEval(String eval) {
    }

    default void onPv(byte[] moves) {
    }
}
