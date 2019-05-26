package de.earthlingz.oerszebra;

import com.shurik.droidzebra.GameState;
import de.earthlingz.oerszebra.parser.ReversiWarsParser;
import org.junit.Test;

public class RotateTest {

    @Test
    public void testRotation() {
        GameState gameState = new GameState(8, new ReversiWarsParser().makeMoveList("F5D6C4D3C3F4F3E3E6C5F6G5H4E7C6F7G8F2G1E2F8H6F1D1D2D8D7C8C7B8G4C1C2H3G6H5G3H2G2B5A5H1B6H7B7A8A7A6G7E8H8A4B4E1B3A3B1"));
        byte[] rotate = gameState.rotate();
        gameState = new GameState(8, rotate, rotate.length);
        String result = gameState.getMoveSequenceAsString();
        System.out.println(result);
    }
}
