package de.earthlingz.oerszebra;

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
        GlobalSettingsLoader.testSearchDepth ="22|20|0";
        ActivityScenario<DroidZebra> scen = ActivityScenario.launch(DroidZebra.class);
        scen.onActivity(z -> zebra  = z);
        while (zebra == null && !zebra.initialized()) {
            Thread.sleep(100);
        }
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
