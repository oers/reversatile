package de.earthlingz.oerszebra.BoardView;

public interface FieldState {
    byte getStateByte();

    boolean isEmpty();

    boolean isBlack();

    boolean isFlipped();
}
