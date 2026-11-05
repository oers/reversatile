package de.earthlingz.oerszebra;

import android.content.SharedPreferences;

/**
 * Search depth settings ("Computer strength" and "Analysis strength").
 * <p>
 * Both are stored as "midgame|exact|wld" (see settings_engine_strengh_values).
 * The list entry {@link #CUSTOM} instead takes its depths from a second
 * preference, the list's key plus {@link #CUSTOM_KEY_SUFFIX}, in the same
 * format - so the custom values survive picking a fixed level in between.
 */
public final class SearchDepth {

    public static final String CUSTOM = "custom";
    public static final String CUSTOM_KEY_SUFFIX = "_custom";

    // Zebra itself searches up to MAX_SEARCH_DEPTH (64) plies, but a midgame
    // depth of 60 or more makes droidzebra-jni.c switch to a timed search.
    // These bounds keep well below that; anything past them would not
    // finish on a phone anyway.
    public static final int MIN_MIDGAME = 1;
    public static final int MAX_MIDGAME = 40;
    public static final int MIN_EXACT = 0;
    public static final int MAX_EXACT = 32;

    public static final int DEFAULT_CUSTOM_MIDGAME = 24;
    public static final int DEFAULT_CUSTOM_EXACT = 24;

    public final int midgame;
    public final int exact;
    public final int wld;

    public SearchDepth(int midgame, int exact, int wld) {
        this.midgame = midgame;
        this.exact = exact;
        this.wld = wld;
    }

    public static String customKey(String key) {
        return key + CUSTOM_KEY_SUFFIX;
    }

    /** The stored form of a custom setting, clamped to the allowed range. */
    public static String format(int midgame, int exact) {
        return clamp(midgame, MIN_MIDGAME, MAX_MIDGAME) + "|" + clamp(exact, MIN_EXACT, MAX_EXACT) + "|0";
    }

    /** Parses "midgame|exact|wld", or returns null if it isn't one. */
    public static SearchDepth parse(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split("\\|");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new SearchDepth(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The depths to use for a list value: a fixed level as is, {@link #CUSTOM}
     * from customValue (clamped), and the default if either can't be read.
     */
    public static SearchDepth resolve(String listValue, String customValue, String defaultValue) {
        SearchDepth result;
        if (CUSTOM.equals(listValue)) {
            SearchDepth custom = parse(customValue);
            result = custom == null ? null : parse(format(custom.midgame, custom.exact));
        } else {
            result = parse(listValue);
        }
        return result != null ? result : parse(defaultValue);
    }

    public static SearchDepth read(SharedPreferences prefs, String key, String defaultValue) {
        return resolve(prefs.getString(key, defaultValue), prefs.getString(customKey(key), null), defaultValue);
    }

    /** The custom depths last saved for key, or the defaults for a new one. */
    public static SearchDepth readCustom(SharedPreferences prefs, String key) {
        SearchDepth custom = parse(prefs.getString(customKey(key), null));
        return custom != null ? custom : new SearchDepth(DEFAULT_CUSTOM_MIDGAME, DEFAULT_CUSTOM_EXACT, 0);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
