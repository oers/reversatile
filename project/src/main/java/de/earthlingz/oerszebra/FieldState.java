package de.earthlingz.oerszebra;

public interface FieldState {
    byte getState();

    boolean isEmpty();

    boolean isBlack();

    boolean isFlipped();
}
