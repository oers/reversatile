package de.earthlingz.oerszebra.guessmove;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.junit.Test;

import de.earthlingz.oerszebra.R;
import de.earthlingz.oerszebra.SystemBars;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertTrue;

public class GuessMoveLayoutTest {

    private static final int SCREEN_WIDTH = 1080;
    private static final int SCREEN_HEIGHT = 2400;
    private static final int STATUS_BAR = 100;
    private static final int NAVIGATION_BAR = 144;

    // Edge to edge (enforced from Android 15 on), the system bars lie over
    // the layout. With three-button navigation the "New game" button used to
    // end up under the navigation bar, where it can't be tapped (reported on
    // a Samsung S21).
    @Test
    public void newGameButtonStaysClearOfTheSystemBars() {
        Context context = new ContextThemeWrapper(getInstrumentation().getTargetContext(),
                R.style.CustomActionBarTheme);
        int[] buttonTopAndBottom = new int[2];
        getInstrumentation().runOnMainSync(() -> {
            View root = LayoutInflater.from(context).inflate(R.layout.activity_guess_move, null);
            SystemBars.keepClear(root);
            ViewCompat.dispatchApplyWindowInsets(root, new WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.systemBars(),
                            Insets.of(0, STATUS_BAR, 0, NAVIGATION_BAR))
                    .build());
            root.measure(View.MeasureSpec.makeMeasureSpec(SCREEN_WIDTH, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(SCREEN_HEIGHT, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);

            View button = root.findViewById(R.id.guess_move_new);
            int top = 0;
            for (View v = button; v != root; v = (View) v.getParent()) {
                top += v.getTop();
            }
            buttonTopAndBottom[0] = top;
            buttonTopAndBottom[1] = top + button.getHeight();
        });

        assertTrue("the button starts under the status bar: " + buttonTopAndBottom[0],
                buttonTopAndBottom[0] >= STATUS_BAR);
        assertTrue("the button ends under the navigation bar: " + buttonTopAndBottom[1],
                buttonTopAndBottom[1] <= SCREEN_HEIGHT - NAVIGATION_BAR);
    }
}
