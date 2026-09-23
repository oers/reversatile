package com.shurik.droidzebra;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public interface GameContext {
    InputStream open(String fromAssetPath) throws IOException;

    File getFilesDir();

    /**
     * Identifies the bundled asset files, e.g. the time the app was
     * installed or last updated, so they are only copied into
     * {@link #getFilesDir()} again when they may have changed. Null means
     * unknown: they are copied on every start.
     */
    default String assetVersion() {
        return null;
    }
}
