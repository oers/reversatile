package de.earthlingz.oerszebra;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ActivityScenario;
import de.earthlingz.oerszebra.BoardView.GameStateBoardModel;

import org.awaitility.Awaitility;
import org.junit.Before;

import java.util.List;

class BasicTest {
    DroidZebra zebra = null;

    @Before
    public void init() throws InterruptedException {
        ActivityScenario<DroidZebra> scen = ActivityScenario.launch(DroidZebra.class);
        scen.onActivity(z -> zebra  = z);
        Awaitility.await().until(() -> zebra != null && zebra.initialized());
    }

    void waitForOpenendDialogs(boolean dismiss) throws InterruptedException {
        Awaitility.await().until(() -> hasOpenedDialogs(zebra, dismiss));
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
