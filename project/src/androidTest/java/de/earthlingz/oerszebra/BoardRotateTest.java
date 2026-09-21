package de.earthlingz.oerszebra;

import android.content.Intent;
import android.graphics.RectF;
import com.shurik.droidzebra.GameState;
import com.shurik.droidzebra.InvalidMove;
import com.shurik.droidzebra.Move;
import com.shurik.droidzebra.ZebraEngine;
import de.earthlingz.oerszebra.BoardView.BoardView;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;


public class BoardRotateTest extends BasicTest {

    @Test
    public void testRotate() throws InterruptedException {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        intent.putExtra(Intent.EXTRA_TEXT, "F5D6C4D3C3F4F3E3E6C5F6G5H4E7C6F7G8F2G1E2F8H6F1D1D2D8D7C8C7B8G4C1C2H3G6H5G3H2G2B5A5H1B6H7B7A8A7A6G7E8H8A4B4E1B3A3B1");

        zebra.runOnUiThread(() -> zebra.onNewIntent(intent));
        //zebra.getEngine().waitForEngineState(ZebraEngine.ES_USER_INPUT_WAIT);

        waitForOpenendDialogs(false);

        assertSame(3, countSquares(ZebraEngine.PLAYER_EMPTY));
        assertSame(58, countSquares(ZebraEngine.PLAYER_WHITE));
        assertSame(3, countSquares(ZebraEngine.PLAYER_BLACK));
        assertSame(zebra.getState().getBlackScore(), 3);
        assertSame(zebra.getState().getWhiteScore(), 61);

        assertFalse("board should start unrotated", zebra.getBoardView().isRotated());
        GameState gameStateBeforeRotate = zebra.getGameState();

        zebra.runOnUiThread(() -> zebra.rotate());

        Thread.sleep(3000);

        // rotate is a pure view transform: the engine/game state must be the exact
        // same instance afterwards, not a freshly restarted game
        assertSame("rotate must not restart the engine/game", gameStateBeforeRotate, zebra.getGameState());
        assertTrue("rotate must flip the view flag", zebra.getBoardView().isRotated());

        assertSame(3, countSquares(ZebraEngine.PLAYER_EMPTY));
        assertSame(58, countSquares(ZebraEngine.PLAYER_WHITE));
        assertSame(3, countSquares(ZebraEngine.PLAYER_BLACK));
        assertSame(zebra.getState().getBlackScore(), 3);
        assertSame(zebra.getState().getWhiteScore(), 61);

        // rotating again flips back
        zebra.runOnUiThread(() -> zebra.rotate());
        Thread.sleep(500);
        assertFalse("rotating twice returns to unrotated", zebra.getBoardView().isRotated());
    }


    @Test
    public void testRotateWithUndo() throws InterruptedException {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        // A fully legal, pass-free 60-move game (verified by simulation): every
        // undo/redo step below is a plain single-ply turn for both colors, with
        // no forced pass in range. Games with passes hit a separate, pre-existing
        // redo/pass-counting edge case unrelated to rotate - not what this test
        // is about.
        intent.putExtra(Intent.EXTRA_TEXT, "C4C3E6C5B3B4A4F6C2E3F4A3A2C1D1E7E2G4B1E1D6C7F8D8F1C6F3G2C8B5G7D7F2B7B8F5A7B2G3B6F7G5H3G1A1H8H4A8H1A6G6H5G8D3A5E8H7D2H6H2");

        zebra.runOnUiThread(() -> zebra.onNewIntent(intent));
        //zebra.getEngine().waitForEngineState(ZebraEngine.ES_USER_INPUT_WAIT);

        waitForOpenendDialogs(true);

        assertSame(0, countSquares(ZebraEngine.PLAYER_EMPTY));
        assertSame(38, countSquares(ZebraEngine.PLAYER_WHITE));
        assertSame(26, countSquares(ZebraEngine.PLAYER_BLACK));
        assertSame(zebra.getState().getBlackScore(), 26);
        assertSame(zebra.getState().getWhiteScore(), 38);


        zebra.runOnUiThread(() -> zebra.undo());Thread.sleep(500);
        zebra.runOnUiThread(() -> zebra.undo());Thread.sleep(500);
        zebra.runOnUiThread(() -> zebra.undo());Thread.sleep(500);
        zebra.runOnUiThread(() -> zebra.undo());Thread.sleep(500);

        // rotating in the middle of the undo history must not disturb it in any way
        zebra.runOnUiThread(() -> zebra.rotate());Thread.sleep(500);
        assertTrue(zebra.getBoardView().isRotated());


        zebra.runOnUiThread(() -> zebra.undo());Thread.sleep(500);
        zebra.runOnUiThread(() -> zebra.undo());Thread.sleep(500);


        // redo all 6 undone moves - all the way back to the end of the game.
        // This used to be impossible for anything undone before rotate() was
        // called, since rotate() used to restart the whole engine and silently
        // drop the redo history; now it must fully succeed.
        zebra.runOnUiThread(() -> zebra.redo());Thread.sleep(500);
        zebra.runOnUiThread(() -> zebra.redo());Thread.sleep(500);
        zebra.runOnUiThread(() -> zebra.redo());Thread.sleep(500);
        zebra.runOnUiThread(() -> zebra.redo());Thread.sleep(500);
        zebra.runOnUiThread(() -> zebra.redo());Thread.sleep(500);
        zebra.runOnUiThread(() -> zebra.redo());Thread.sleep(500);

        // the last redo's board update arrives asynchronously from the native
        // engine thread; wait for it instead of racing a fixed sleep against it
        waitForSquareCount(ZebraEngine.PLAYER_EMPTY, 0, 5000);

        // fully restored to the original end-of-game position
        assertSame(0, countSquares(ZebraEngine.PLAYER_EMPTY));
        assertSame(38, countSquares(ZebraEngine.PLAYER_WHITE));
        assertSame(26, countSquares(ZebraEngine.PLAYER_BLACK));
        assertSame(zebra.getState().getBlackScore(), 26);
        assertSame(zebra.getState().getWhiteScore(), 38);

        // the view stays rotated independently of the game/undo-redo state
        assertTrue(zebra.getBoardView().isRotated());
    }

    @Test
    public void testRotatedTouchMapsToMirroredCell() throws InvalidMove, InterruptedException {
        BoardView boardView = zebra.getBoardView();

        RectF topLeft = boardView.getCellRect(0, 0);
        Move unrotatedMove = boardView.getMoveFromCoord(topLeft.centerX(), topLeft.centerY());
        assertEquals(0, unrotatedMove.getX());
        assertEquals(0, unrotatedMove.getY());

        zebra.runOnUiThread(() -> zebra.rotate());
        Thread.sleep(200);

        // tapping the exact same physical point must now resolve to the
        // mirrored cell (bottom-right instead of top-left on an 8x8 board)
        Move rotatedMove = boardView.getMoveFromCoord(topLeft.centerX(), topLeft.centerY());
        assertEquals(7, rotatedMove.getX());
        assertEquals(7, rotatedMove.getY());
    }
}
