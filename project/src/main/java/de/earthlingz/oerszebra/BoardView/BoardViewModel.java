package de.earthlingz.oerszebra.BoardView;

import android.support.annotation.Nullable;
import com.shurik.droidzebra.CandidateMove;
import com.shurik.droidzebra.Move;

public interface BoardViewModel {


    int getBoardSize();

    @Nullable
    Move getLastMove();

    //TODO encapsulation leak
    CandidateMove[] getCandidateMoves();

    boolean isValidMove(Move move);

    Move getNextMove();

    void setBoardModelListener(BoardModelListener boardModelListener);

    void removeOnBoardStateChangedListener();

    boolean isFieldFlipped(int x, int y);

    boolean isFieldEmpty(int i, int j);

    boolean isFieldBlack(int i, int j);

    byte getStateByte(int x, int y);
}
