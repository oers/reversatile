package de.earthlingz.oerszebra;

import org.junit.Test;

import static de.earthlingz.oerszebra.GameSettingsConstants.FUNCTION_HUMAN_VS_HUMAN;
import static de.earthlingz.oerszebra.GameSettingsConstants.FUNCTION_ZEBRA_BLACK;
import static de.earthlingz.oerszebra.GameSettingsConstants.FUNCTION_ZEBRA_VS_ZEBRA;
import static de.earthlingz.oerszebra.GameSettingsConstants.FUNCTION_ZEBRA_WHITE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayModeTest {

    @Test
    public void readsTheModeFromTheSettings() {
        assertEquals(PlayMode.ANALYSIS, PlayMode.of(FUNCTION_HUMAN_VS_HUMAN, true));
        assertEquals(PlayMode.VS_HUMAN, PlayMode.of(FUNCTION_HUMAN_VS_HUMAN, false));
        assertEquals(PlayMode.VS_COMPUTER, PlayMode.of(FUNCTION_ZEBRA_WHITE, false));
        assertEquals(PlayMode.VS_COMPUTER, PlayMode.of(FUNCTION_ZEBRA_BLACK, true));
        assertEquals(PlayMode.VS_COMPUTER, PlayMode.of(FUNCTION_ZEBRA_VS_ZEBRA, false));
    }

    @Test
    public void cyclesAnalysisComputerHuman() {
        assertEquals(PlayMode.VS_COMPUTER, PlayMode.ANALYSIS.next());
        assertEquals(PlayMode.VS_HUMAN, PlayMode.VS_COMPUTER.next());
        assertEquals(PlayMode.ANALYSIS, PlayMode.VS_HUMAN.next());
    }

    @Test
    public void computerTakesTheSideThatIsNotToMove() {
        assertEquals(FUNCTION_ZEBRA_WHITE, PlayMode.VS_COMPUTER.engineFunction(true));
        assertEquals(FUNCTION_ZEBRA_BLACK, PlayMode.VS_COMPUTER.engineFunction(false));
        assertEquals(FUNCTION_HUMAN_VS_HUMAN, PlayMode.ANALYSIS.engineFunction(true));
        assertEquals(FUNCTION_HUMAN_VS_HUMAN, PlayMode.VS_HUMAN.engineFunction(false));
    }

    @Test
    public void onlyAnalysisShowsHelp() {
        assertTrue(PlayMode.ANALYSIS.showsHelp());
        assertFalse(PlayMode.VS_COMPUTER.showsHelp());
        assertFalse(PlayMode.VS_HUMAN.showsHelp());
    }

    // What the button writes must read back as the same mode.
    @Test
    public void writtenSettingsReadBackAsTheSameMode() {
        for (PlayMode mode : PlayMode.values()) {
            for (boolean blackToMove : new boolean[]{true, false}) {
                assertEquals(mode, PlayMode.of(mode.engineFunction(blackToMove), mode.showsHelp()));
            }
        }
    }
}
