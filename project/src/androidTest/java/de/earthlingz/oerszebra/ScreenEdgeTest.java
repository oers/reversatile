package de.earthlingz.oerszebra;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.core.app.ActivityScenario;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertTrue;

/**
 * Like {@link SystemBarsTest}, but against the device's own system bars and
 * cutouts instead of made-up ones. CI also runs this on Android 15 with
 * three-button navigation, where the app is drawn edge to edge: the setup
 * the buttons used to end up under the navigation bar in.
 */
public class ScreenEdgeTest extends BasicTest {

    @Test
    public void buttonsBelowTheBoardAreClearOfTheSystemBars() {
        List<String> problems = new ArrayList<>();
        getInstrumentation().runOnMainSync(() -> {
            for (int id : new int[]{R.id.board, R.id.status_first_move, R.id.status_undo,
                    R.id.status_redo, R.id.status_rotate}) {
                checkClear(zebra, zebra.findViewById(id), 0, 0, problems);
            }
        });
        assertTrue(problems.toString(), problems.isEmpty());
    }

    @Test
    public void settingsListIsClearOfTheSystemBars() {
        try (ActivityScenario<SettingsPreferences> scenario =
                     ActivityScenario.launch(SettingsPreferences.class)) {
            List<String> problems = new ArrayList<>();
            scenario.onActivity(activity -> {
                ListView list = activity.getListView();
                // The list scrolls under the bars; what counts is that its
                // padding keeps its first and last rows clear of them.
                checkClear(activity, list, list.getPaddingTop(), list.getPaddingBottom(), problems);
            });
            assertTrue(problems.toString(), problems.isEmpty());
        }
    }

    private static void checkClear(Activity activity, View view, int paddingTop, int paddingBottom,
                                   List<String> problems) {
        View decor = activity.getWindow().getDecorView();
        WindowInsetsCompat windowInsets = ViewCompat.getRootWindowInsets(decor);
        Insets clear = windowInsets == null ? Insets.NONE : windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
        int[] location = new int[2];
        view.getLocationInWindow(location);
        int left = location[0];
        int top = location[1] + paddingTop;
        int right = location[0] + view.getWidth();
        int bottom = location[1] + view.getHeight() - paddingBottom;
        String name = activity.getResources().getResourceEntryName(view.getId());
        String where = " (" + left + "," + top + " - " + right + "," + bottom + " in a "
                + decor.getWidth() + "x" + decor.getHeight() + " window, clear of " + clear + ")";
        // Shows in CI which bars the check ran against.
        Log.i("ScreenEdgeTest", name + where);
        if (view.getWidth() <= 0 || view.getHeight() <= 0) {
            problems.add(name + " is not laid out" + where);
        }
        if (top < clear.top || left < clear.left) {
            problems.add(name + " starts under a system bar or cutout" + where);
        }
        if (bottom > decor.getHeight() - clear.bottom || right > decor.getWidth() - clear.right) {
            problems.add(name + " ends under a system bar or cutout" + where);
        }
    }
}
