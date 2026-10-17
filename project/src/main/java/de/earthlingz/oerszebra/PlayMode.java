package de.earthlingz.oerszebra;

import static de.earthlingz.oerszebra.GameSettingsConstants.FUNCTION_HUMAN_VS_HUMAN;
import static de.earthlingz.oerszebra.GameSettingsConstants.FUNCTION_ZEBRA_BLACK;
import static de.earthlingz.oerszebra.GameSettingsConstants.FUNCTION_ZEBRA_WHITE;

/**
 * The three ways of using the board that the toolbar's mode button cycles
 * through. Not stored on its own: it is read from, and written to, the
 * existing "computer plays" and practice-mode settings, so the button and
 * the settings screen always agree.
 */
enum PlayMode {
    /** Nobody plays against you; evaluations and legal moves are shown. */
    ANALYSIS,
    /** The computer plays one side; no help on the board. */
    VS_COMPUTER,
    /** Two people at one board; no help on the board. */
    VS_HUMAN;

    static PlayMode of(int engineFunction, boolean practiceMode) {
        if (engineFunction != FUNCTION_HUMAN_VS_HUMAN) {
            return VS_COMPUTER;
        }
        return practiceMode ? ANALYSIS : VS_HUMAN;
    }

    PlayMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /**
     * The "computer plays" setting for this mode. The computer takes the
     * side that is not to move, so whoever switches keeps the move.
     */
    int engineFunction(boolean blackToMove) {
        if (this != VS_COMPUTER) {
            return FUNCTION_HUMAN_VS_HUMAN;
        }
        return blackToMove ? FUNCTION_ZEBRA_WHITE : FUNCTION_ZEBRA_BLACK;
    }

    /** Evaluations and legal-move markers on the board. */
    boolean showsHelp() {
        return this == ANALYSIS;
    }
}
