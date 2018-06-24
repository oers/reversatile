package de.earthlingz.oerszebra;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

import static de.earthlingz.oerszebra.GameSettingsConstants.*;

public class GlobalSettingsLoader implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String SHARED_PREFS_NAME = "droidzebrasettings";

    private static final String DEFAULT_SETTING_STRENGTH = "8|16|0";
    private static final boolean DEFAULT_SETTING_AUTO_MAKE_FORCED_MOVES = false;
    private static final String DEFAULT_SETTING_FORCE_OPENING = "None";
    private static final boolean DEFAULT_SETTING_HUMAN_OPENINGS = false;
    private static final boolean DEFAULT_SETTING_PRACTICE_MODE = true;
    private static final boolean DEFAULT_SETTING_USE_BOOK = true;
    private static final boolean DEFAULT_SETTING_DISPLAY_PV = true;
    private static final boolean DEFAULT_SETTING_DISPLAY_MOVES = true;
    private static final boolean DEFAULT_SETTING_DISPLAY_LAST_MOVE = true;
    public static final String DEFAULT_SETTING_SENDMAIL = "";
    private static final boolean DEFAULT_SETTING_DISPLAY_ENABLE_ANIMATIONS = false;
    public static final String
            SETTINGS_KEY_FUNCTION = "settings_engine_function",
            SETTINGS_KEY_STRENGTH = "settings_engine_strength",
            SETTINGS_KEY_AUTO_MAKE_FORCED_MOVES = "settings_engine_auto_make_moves",
            SETTINGS_KEY_RANDOMNESS = "settings_engine_randomness",
            SETTINGS_KEY_FORCE_OPENING = "settings_engine_force_opening",
            SETTINGS_KEY_HUMAN_OPENINGS = "settings_engine_human_openings",
            SETTINGS_KEY_PRACTICE_MODE = "settings_engine_practice_mode",
            SETTINGS_KEY_USE_BOOK = "settings_engine_use_book",
            SETTINGS_KEY_DISPLAY_PV = "settings_ui_display_pv",
            SETTINGS_KEY_DISPLAY_MOVES = "settings_ui_display_moves",
            SETTINGS_KEY_DISPLAY_LAST_MOVE = "settings_ui_display_last_move",
            SETTINGS_KEY_SENDMAIL = "settings_sendmail",
            SETTINGS_KEY_DISPLAY_ENABLE_ANIMATIONS = "settings_ui_display_enable_animations";


    public static final int
            RANDOMNESS_NONE = 0,
            RANDOMNESS_SMALL = 1,
            RANDOMNESS_MEDIUM = 2,
            RANDOMNESS_LARGE = 3,
            RANDOMNESS_HUGE = 4;

    public static final int DEFAULT_SETTING_RANDOMNESS = RANDOMNESS_LARGE;
    public static final int DEFAULT_SETTING_FUNCTION = FUNCTION_HUMAN_VS_HUMAN;


    public int mSettingFunction = DEFAULT_SETTING_FUNCTION;
    public boolean mSettingAutoMakeForcedMoves = DEFAULT_SETTING_AUTO_MAKE_FORCED_MOVES;
    public int mSettingZebraRandomness = DEFAULT_SETTING_RANDOMNESS;
    public String mSettingZebraForceOpening = DEFAULT_SETTING_FORCE_OPENING;
    public boolean mSettingZebraHumanOpenings = DEFAULT_SETTING_HUMAN_OPENINGS;
    public boolean mSettingZebraPracticeMode = DEFAULT_SETTING_PRACTICE_MODE;
    public boolean mSettingZebraUseBook = DEFAULT_SETTING_USE_BOOK;
    public boolean mSettingDisplayPV = DEFAULT_SETTING_DISPLAY_PV;
    public boolean mSettingDisplayMoves = DEFAULT_SETTING_DISPLAY_MOVES;
    public boolean mSettingDisplayLastMove = DEFAULT_SETTING_DISPLAY_LAST_MOVE;
    public boolean mSettingDisplayEnableAnimations = DEFAULT_SETTING_DISPLAY_ENABLE_ANIMATIONS;
    public int mSettingAnimationDelay = 1000;


    public int mSettingZebraDepth = 1;
    public int mSettingZebraDepthExact = 1;
    public int mSettingZebraDepthWLD = 1;

    private Context context;
    private OnChangeListener onChangeListener;

    public GlobalSettingsLoader(Context context) {

        this.context = context;
        context.getSharedPreferences(SHARED_PREFS_NAME, 0).registerOnSharedPreferenceChangeListener(this);
    }

    public boolean loadSettings() {
        int settingsFunction, settingZebraDepth, settingZebraDepthExact, settingZebraDepthWLD;
        int settingRandomness;
        boolean settingAutoMakeForcedMoves;
        String settingZebraForceOpening;
        boolean settingZebraHumanOpenings;
        boolean settingZebraPracticeMode;
        boolean settingZebraUseBook;

        SharedPreferences settings = context.getSharedPreferences(SHARED_PREFS_NAME, 0);

        settingsFunction = Integer.parseInt(settings.getString(SETTINGS_KEY_FUNCTION, String.format(Locale.getDefault(), "%d", DEFAULT_SETTING_FUNCTION)));
        String[] strength = settings.getString(SETTINGS_KEY_STRENGTH, DEFAULT_SETTING_STRENGTH).split("\\|");
        settingZebraDepth = Integer.parseInt(strength[0]);
        settingZebraDepthExact = Integer.parseInt(strength[1]);
        settingZebraDepthWLD = Integer.parseInt(strength[2]);

        settingAutoMakeForcedMoves = settings.getBoolean(SETTINGS_KEY_AUTO_MAKE_FORCED_MOVES, DEFAULT_SETTING_AUTO_MAKE_FORCED_MOVES);
        settingRandomness = Integer.parseInt(settings.getString(SETTINGS_KEY_RANDOMNESS, String.format("%d", DEFAULT_SETTING_RANDOMNESS)));
        settingZebraForceOpening = settings.getString(SETTINGS_KEY_FORCE_OPENING, DEFAULT_SETTING_FORCE_OPENING);
        settingZebraHumanOpenings = settings.getBoolean(SETTINGS_KEY_HUMAN_OPENINGS, DEFAULT_SETTING_HUMAN_OPENINGS);
        settingZebraPracticeMode = settings.getBoolean(SETTINGS_KEY_PRACTICE_MODE, DEFAULT_SETTING_PRACTICE_MODE);
        settingZebraUseBook = settings.getBoolean(SETTINGS_KEY_USE_BOOK, DEFAULT_SETTING_USE_BOOK);


        boolean bZebraSettingChanged = (
                mSettingFunction != settingsFunction
                        || mSettingZebraDepth != settingZebraDepth
                        || mSettingZebraDepthExact != settingZebraDepthExact
                        || mSettingZebraDepthWLD != settingZebraDepthWLD
                        || mSettingAutoMakeForcedMoves != settingAutoMakeForcedMoves
                        || mSettingZebraRandomness != settingRandomness
                        || !mSettingZebraForceOpening.equals(settingZebraForceOpening)
                        || mSettingZebraHumanOpenings != settingZebraHumanOpenings
                        || mSettingZebraPracticeMode != settingZebraPracticeMode
                        || mSettingZebraUseBook != settingZebraUseBook
        );

        mSettingFunction = settingsFunction;
        mSettingZebraDepth = settingZebraDepth;
        mSettingZebraDepthExact = settingZebraDepthExact;
        mSettingZebraDepthWLD = settingZebraDepthWLD;
        mSettingAutoMakeForcedMoves = settingAutoMakeForcedMoves;
        mSettingZebraRandomness = settingRandomness;
        mSettingZebraForceOpening = settingZebraForceOpening;
        mSettingZebraHumanOpenings = settingZebraHumanOpenings;
        mSettingZebraPracticeMode = settingZebraPracticeMode;
        mSettingZebraUseBook = settingZebraUseBook;

        mSettingDisplayPV = settings.getBoolean(SETTINGS_KEY_DISPLAY_PV, DEFAULT_SETTING_DISPLAY_PV);

        mSettingDisplayMoves = settings.getBoolean(SETTINGS_KEY_DISPLAY_MOVES, DEFAULT_SETTING_DISPLAY_MOVES);
        mSettingDisplayLastMove = settings.getBoolean(SETTINGS_KEY_DISPLAY_LAST_MOVE, DEFAULT_SETTING_DISPLAY_LAST_MOVE);

        mSettingDisplayEnableAnimations = settings.getBoolean(SETTINGS_KEY_DISPLAY_ENABLE_ANIMATIONS, DEFAULT_SETTING_DISPLAY_ENABLE_ANIMATIONS);


        return bZebraSettingChanged;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (loadSettings() && onChangeListener != null) {
            onChangeListener.onChange();
        }
    }

    public void setOnChangeListener(OnChangeListener onChangeListener) {
        this.onChangeListener = onChangeListener;
    }

    public interface OnChangeListener {
        void onChange();
    }
}
