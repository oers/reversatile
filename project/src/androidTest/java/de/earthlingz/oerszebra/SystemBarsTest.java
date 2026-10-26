package de.earthlingz.oerszebra;

import android.content.Context;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Edge to edge (enforced from Android 15 on) the system bars lie over the
 * layout. With three-button navigation, buttons at the bottom used to end
 * up under the navigation bar, where they can't be tapped (reported for the
 * guess move screen's "New game", on a Samsung S21).
 */
public class SystemBarsTest {

    private static final int STATUS_BAR = 100;
    private static final int NAVIGATION_BAR = 144;

    @Test
    public void guessMoveNewGameStaysClearOfTheSystemBars() {
        assertClear(R.layout.activity_guess_move, Configuration.ORIENTATION_PORTRAIT,
                R.id.guess_move_new);
        assertClear(R.layout.activity_guess_move, Configuration.ORIENTATION_LANDSCAPE,
                R.id.guess_move_new);
    }

    @Test
    public void buttonsBelowTheBoardStayClearOfTheSystemBars() {
        int[] buttons = {R.id.status_first_move, R.id.status_undo, R.id.status_redo, R.id.status_rotate};
        assertClear(R.layout.board_layout, Configuration.ORIENTATION_PORTRAIT, buttons);
        assertClear(R.layout.board_layout, Configuration.ORIENTATION_LANDSCAPE, buttons);
    }

    @Test
    public void settingsListEndsClearOfTheSystemBars() {
        try (ActivityScenario<SettingsPreferences> scenario =
                     ActivityScenario.launch(SettingsPreferences.class)) {
            int[] padding = new int[4];
            boolean[] clipsToPadding = new boolean[1];
            scenario.onActivity(activity -> {
                ListView list = activity.getListView();
                padding[0] = list.getPaddingTop();
                padding[1] = list.getPaddingBottom();
                ViewCompat.dispatchApplyWindowInsets(list, insets(0, STATUS_BAR, 0, NAVIGATION_BAR));
                padding[2] = list.getPaddingTop();
                padding[3] = list.getPaddingBottom();
                clipsToPadding[0] = list.getClipToPadding();
            });
            assertEquals(padding[0] + STATUS_BAR, padding[2]);
            assertEquals(padding[1] + NAVIGATION_BAR, padding[3]);
            assertFalse("the list should scroll under the bars", clipsToPadding[0]);
        }
    }

    // Lays the layout out on a phone-sized screen with a status bar and a
    // navigation bar - at the bottom in portrait, at the side in landscape,
    // as with three-button navigation - and checks each view lies between
    // them.
    private static void assertClear(int layout, int orientation, int... ids) {
        boolean landscape = orientation == Configuration.ORIENTATION_LANDSCAPE;
        int width = landscape ? 2400 : 1080;
        int height = landscape ? 1080 : 2400;
        Insets bars = landscape
                ? Insets.of(0, STATUS_BAR, NAVIGATION_BAR, 0)
                : Insets.of(0, STATUS_BAR, 0, NAVIGATION_BAR);

        Context target = getInstrumentation().getTargetContext();
        Configuration config = new Configuration(target.getResources().getConfiguration());
        config.orientation = orientation;
        Context context = new ContextThemeWrapper(target.createConfigurationContext(config),
                R.style.CustomActionBarTheme);

        List<String> problems = new ArrayList<>();
        getInstrumentation().runOnMainSync(() -> {
            View root = LayoutInflater.from(context).inflate(layout, null);
            SystemBars.keepClear(root.findViewById(R.id.main_frame));
            ViewCompat.dispatchApplyWindowInsets(root, insets(bars.left, bars.top, bars.right, bars.bottom));
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, width, height);

            String where = (landscape ? "landscape " : "portrait ")
                    + target.getResources().getResourceEntryName(layout);
            for (int id : ids) {
                View view = root.findViewById(id);
                int left = 0;
                int top = 0;
                for (View v = view; v != root; v = (View) v.getParent()) {
                    left += v.getLeft();
                    top += v.getTop();
                }
                String name = target.getResources().getResourceEntryName(id);
                if (view.getWidth() <= 0 || view.getHeight() <= 0) {
                    problems.add(name + " is not laid out in " + where);
                }
                if (top < bars.top) {
                    problems.add(name + " starts under the status bar in " + where + ": " + top);
                }
                if (top + view.getHeight() > height - bars.bottom) {
                    problems.add(name + " ends under the navigation bar in " + where + ": "
                            + (top + view.getHeight()));
                }
                if (left + view.getWidth() > width - bars.right) {
                    problems.add(name + " ends under the navigation bar in " + where + ": "
                            + (left + view.getWidth()));
                }
            }
        });
        assertTrue(problems.toString(), problems.isEmpty());
    }

    private static WindowInsetsCompat insets(int left, int top, int right, int bottom) {
        return new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(left, top, right, bottom))
                .build();
    }
}
