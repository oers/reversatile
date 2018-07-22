package de.earthlingz.oerszebra.BoardView;

interface BoardModelListener {
    default void onBoardStateChanged() {
    }
    default void onCandidateMovesChanged() {
    }
}
