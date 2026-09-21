package de.earthlingz.oerszebra;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ActivityScenario;
import androidx.test.rule.ActivityTestRule;
import de.earthlingz.oerszebra.BoardView.GameStateBoardModel;
import org.junit.Before;
import org.junit.Rule;

import java.util.List;

class BasicTest {
    DroidZebra zebra = null;

    @Before
    public void init() throws InterruptedException {
        // Shallow on purpose: none of these tests assert on evaluation quality,
        // and practice mode (on by default) recomputes evals for every legal
        // move after every human-vs-human position change - including undo/
        // redo and the WThor replay tests' many per-move steps. At a deeper
        // depth those searches piled up enough under CI load to make replay
        // steps miss their wait windows late in a game (seen in CI: WThor
        // redo/move-by-move replay consistently landing one move short right
        // at the end of longer games).
        GlobalSettingsLoader.testSearchDepth = "1|1|1";
        ActivityScenario<DroidZebra> scen = ActivityScenario.launch(DroidZebra.class);
        scen.onActivity(z -> zebra  = z);
        while (zebra == null && !zebra.initialized()) {
            Thread.sleep(100);
        }
        getInstrumentation().waitForIdleSync();
    }

    void waitForOpenendDialogs(boolean dismiss) throws InterruptedException {
        while(!hasOpenedDialogs(zebra, dismiss)) {
            Thread.sleep(100);
        }
    }

   private boolean hasOpenedDialogs(FragmentActivity activity, boolean dismiss) {
        List<Fragment> fragments = activity.getSupportFragmentManager().getFragments();
        for (Fragment fragment : fragments) {
            if (fragment instanceof DialogFragment) {
                if(dismiss) {
                    ((DialogFragment) fragment).dismiss();
                }
                return true;
            }
        }


        return false;
    }

    // Polls until the board reaches the expected square count or the timeout
    // elapses, instead of guessing a fixed sleep duration - undo/redo board
    // updates arrive asynchronously from the native engine thread.
    void waitForSquareCount(byte color, int expectedCount, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (countSquares(color) != expectedCount && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
    }

    int countSquares(byte color) {
        GameStateBoardModel state = this.zebra.getState();
        int result = 0;
        for (int y = 0, boardLength = state.getBoardHeight(); y < boardLength; y++) {
            for (int x = 0, rowLength = state.getBoardRowWidth(); x < rowLength; x++) {
                if (color == state.getFieldByte(x,y)) {
                    result++;
                }
            }
        }
        return result;
    }
}
