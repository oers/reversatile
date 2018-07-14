package de.earthlingz.oerszebra;

interface FieldState {
    byte getState();

    boolean isEmpty();

    boolean isBlack();

    boolean isFlipped();
}
