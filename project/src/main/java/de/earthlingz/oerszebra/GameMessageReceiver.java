package de.earthlingz.oerszebra;

import com.shurik.droidzebra.GameState;

interface GameMessageReceiver {
    void onError(String error);

    void onDebug(String debug);

    void onBoard(GameState board);

    void onPass();

    void onGameStart();

    void onGameOver();

    void onMoveStart();

    void onMoveEnd();

    void onEval(String eval);

    void onPv(byte[] moves);
}
