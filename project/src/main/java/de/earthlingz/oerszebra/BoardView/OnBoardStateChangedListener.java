package de.earthlingz.oerszebra.BoardView;

interface OnBoardStateChangedListener {
    default void onBoardStateChanged() {
    }
    default void onCandidateMovesChanged() {
    }
}
