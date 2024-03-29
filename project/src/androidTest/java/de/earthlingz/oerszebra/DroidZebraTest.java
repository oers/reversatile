package de.earthlingz.oerszebra;

import android.content.Intent;
import androidx.test.filters.SmallTest;

import com.shurik.droidzebra.CandidateMove;
import com.shurik.droidzebra.ZebraEngine;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@SmallTest
public class DroidZebraTest extends BasicTest{


    @Test
    public void testSkipWithFinalEmptySquares() throws InterruptedException {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        intent.putExtra(Intent.EXTRA_TEXT, "F5D6C4D3C3F4F3E3E6C5F6G5H4E7C6F7G8F2G1E2F8H6F1D1D2D8D7C8C7B8G4C1C2H3G6H5G3H2G2B5A5H1B6H7B7A8A7A6G7E8H8A4B4E1B3A3B1");

        zebra.runOnUiThread(() -> zebra.onNewIntent(intent));

        waitForOpenendDialogs(false);
        assertSame(3, countSquares(ZebraEngine.PLAYER_EMPTY));
        assertSame(58, countSquares(ZebraEngine.PLAYER_WHITE));
        assertSame(3, countSquares(ZebraEngine.PLAYER_BLACK));
        assertSame(zebra.getState().getBlackScore(), 3);
        assertSame(zebra.getState().getWhiteScore(), 61);
    }

    @Test
    public void testSkipCompleteGame() throws InterruptedException {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        intent.putExtra(Intent.EXTRA_TEXT, "E6F4C3C4G3D6B4F5G6G5G4H5D7E3F2H4H3H2F3A4H6H7D3F6F7C5A3A2B5A5A6A7B6C6E7C7C8F1G7D8E8H8B8G8G2F8G1H1E1A8B7E2C2B1C1D1D2B3B2A1");

        zebra.runOnUiThread(() -> zebra.onNewIntent(intent));
        waitForOpenendDialogs(false);
        //this.getActivity().getEngine().waitForEngineState(ZebraEngine.ES_USER_INPUT_WAIT);

        assertSame(0, countSquares(ZebraEngine.PLAYER_EMPTY));
        assertSame(62, countSquares(ZebraEngine.PLAYER_WHITE));
        assertSame(2, countSquares(ZebraEngine.PLAYER_BLACK));
    }

    @Test
    public void testIssue22() throws InterruptedException {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        intent.putExtra(Intent.EXTRA_TEXT, "D3C5F6F5F4C3C4D2E2B4D1F3B5E3F2F1A4D6E6E7F7B6E8C6B3A5D7A3E1A6G1A2C2C7B8D8C8G8G6H6G5H5G4H4H3G7H8F8H7A8A7B7A1B2G3G2H2H1C1B1");

        zebra.runOnUiThread(() -> zebra.onNewIntent(intent));
        waitForOpenendDialogs(false);
        //this.getActivity().getEngine().waitForEngineState(ZebraEngine.ES_USER_INPUT_WAIT);
        assertSame(0, countSquares(ZebraEngine.PLAYER_EMPTY));
        assertSame(32, countSquares(ZebraEngine.PLAYER_WHITE));
        assertSame(32, countSquares(ZebraEngine.PLAYER_BLACK));
    }

    @Test
    public void testCrash1() throws InterruptedException {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        intent.putExtra(Intent.EXTRA_TEXT, "d3c5f6f5e6e3c4c3f4d6c6d7b5b4c7f7a3g5f8g6g3a6b6a5e8e7f2g4f3e2h3f1d8h5h7a7g2");

        zebra.runOnUiThread(() -> zebra.onNewIntent(intent));

        Thread.sleep(2000);
        zebra.runOnUiThread(() -> zebra.undoAll());
        Thread.sleep(10000);
        for(int i = 1; i < 20; i++) {
            zebra.runOnUiThread(() -> zebra.redo());
            Thread.sleep(200);
        }
    }

    @Test
    public void testCrash2() throws InterruptedException {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        intent.putExtra(Intent.EXTRA_TEXT, "d3c5e6f5f6e3d6f7g6e7f4c4b4d2c3b5c2e2f3a4f1e1c1g1f2h6f8g5c6b3a6g4a5a3b6a7b2a1a2b1a8d1g3h4h3c7h5h2g7e8c8h7h8");

        zebra.runOnUiThread(() -> zebra.onNewIntent(intent));

        Thread.sleep(2000);
        zebra.runOnUiThread(() -> zebra.undoAll());
        Thread.sleep(2000);
        for(int i = 1; i < 50; i++) {
            zebra.runOnUiThread(() -> zebra.redo());
            Thread.sleep(500);
            //if(i == 30) {
            CandidateMove[] candidateMoves = zebra.getGameState().getCandidateMoves();
            for(CandidateMove m : candidateMoves) {
                assertTrue(m.getText() + "ist evaluiert", m.hasEval);
            }
            //}
        }


    }

    @Test
    @Ignore
    public void testCrash86() throws InterruptedException {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        intent.putExtra(Intent.EXTRA_TEXT, "c4c3d3c5b3f4b5b4a5a3c6d6f3c2d1e6d2b6g4e2e3f2e1f1g1f5g6a6a7a2c7d7d8e7e8f7f6f8g8g5h4b1b2a1c1a4a8b7b8c8h8g7h7h6h5h3h1g2");

        zebra.runOnUiThread(() -> zebra.onNewIntent(intent));

        Thread.sleep(2000);
        zebra.runOnUiThread(() -> zebra.undoAll());
        Thread.sleep(10000);
        for(int i = 1; i < 50; i++) {
            zebra.runOnUiThread(() -> zebra.redo());
            Thread.sleep(200);
        }


    }

    @Test
    public void testCrash3() throws InterruptedException {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        intent.putExtra(Intent.EXTRA_TEXT, "d3c5e6f5f6e3d6f7g6e7f4c4b4d2c3b5c2e2f3a4f1e1c1g1f2h6f8g5c6b3a6g4a5a3b6a7b2a1a2");

        zebra.runOnUiThread(() -> zebra.onNewIntent(intent));

        Thread.sleep(2000);
        zebra.runOnUiThread(() -> zebra.undoAll());
        Thread.sleep(10000);

    }

    @Test
    public void testCrash4() throws InterruptedException {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        intent.putExtra(Intent.EXTRA_TEXT, "D3C3C4C5B4E3E2D2C6B3B5C2E1C1A3A6A5A4A7B2F4F5D6F3F2E6G4G5G3H4F6G6H3H2H7G1F1D1H5H6H1B6C7F7F8D7");

        zebra.runOnUiThread(() -> zebra.onNewIntent(intent));

        Thread.sleep(2000);
        zebra.runOnUiThread(() -> zebra.undoAll());
        Thread.sleep(10000);

    }


}
