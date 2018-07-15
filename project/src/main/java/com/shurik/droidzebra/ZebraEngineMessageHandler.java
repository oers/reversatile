package com.shurik.droidzebra;

public interface ZebraEngineMessageHandler {
    default void sendBoard(GameState board) {
    }

    default void sendPass() {
    }

    default void sendGameStart() {
    }

    default void sendGameOver() {
    }

    default void sendMoveStart() {
    }

    default void sendMoveEnd() {
    }

    default void sendEval(String eval) {
    }

    default void sendPv(byte[] moves) {
    }
}
