package de.earthlingz.oerszebra;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * The app draws edge to edge (enforced from Android 15 on), so the status
 * and navigation bars lie over the layout.
 */
public final class SystemBars {

    private SystemBars() {
    }

    /**
     * Keeps everything in {@code view} clear of the system bars, by adding
     * their size to its padding.
     */
    public static void keepClear(View view) {
        int left = view.getPaddingLeft();
        int top = view.getPaddingTop();
        int right = view.getPaddingRight();
        int bottom = view.getPaddingBottom();
        //https://developer.android.com/develop/ui/views/layout/edge-to-edge?hl=de#java
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(left + insets.left, top + insets.top,
                    right + insets.right, bottom + insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
