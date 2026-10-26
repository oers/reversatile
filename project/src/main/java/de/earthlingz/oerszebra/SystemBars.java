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
     * Keeps everything in {@code root} clear of the system bars, by padding
     * it by their size.
     */
    public static void keepClear(View root) {
        //https://developer.android.com/develop/ui/views/layout/edge-to-edge?hl=de#java
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
