package de.earthlingz.oerszebra;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ActivityScenario;
import androidx.test.rule.ActivityTestRule;
import de.earthlingz.oerszebra.BoardView.GameStateBoardModel;
import org.junit.Before;
import org.junit.Rule;

import java.util.List;
import java.util.function.BooleanSupplier;

class BasicTest {
    // set on the main thread by onActivity, read by the test thread
    volatile DroidZebra zebra = null;

    @Before
    public void init() throws InterruptedException {
        GlobalSettingsLoader.testSearchDepth = getTestSearchDepth();
        // The first-run consent dialog can't be dismissed without an answer,
        // and it would otherwise sit on top of every test's activity.
        getInstrumentation().getTargetContext()
                .getSharedPreferences(GlobalSettingsLoader.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(Analytics.FIRST_RUN, false).commit();
        ActivityScenario<DroidZebra> scen = ActivityScenario.launch(DroidZebra.class);
        scen.onActivity(z -> zebra  = z);
        // Was "zebra == null && !zebra.initialized()": that threw a
        // NullPointerException when zebra was still null and never waited
        // otherwise, so tests could start before the engine was ready.
        waitUntil(() -> zebra != null && zebra.initialized(), 30000);
        assertTrue("DroidZebra did not finish initializing", zebra != null && zebra.initialized());
        getInstrumentation().waitForIdleSync();
    }

    // Shallow on purpose, and shared by every BasicTest subclass unless it
    // overrides this: practice mode (on by default) recomputes evals for
    // every legal move after every human-vs-human position change -
    // including undo/redo and the WThor replay tests' many per-move steps.
    // At a deeper depth those searches piled up enough under CI load to
    // make replay/undo/redo steps miss their fixed wait windows (seen in
    // CI: WThor redo/move-by-move replay, and plain undo/redo tests using
    // fixed sleeps, consistently landing short). Subclasses that need a
    // different depth (or that assert on evaluation quality) can still
    // override this.
    protected String getTestSearchDepth() {
        return "1|1|1";
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
        waitUntil(() -> countSquares(color) == expectedCount, timeoutMillis);
    }

    // Shared poll-until-timeout shape for any condition that settles
    // asynchronously (native engine callbacks, UI updates) - avoids
    // reimplementing the same deadline/sleep loop at every call site.
    void waitUntil(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
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
