package de.earthlingz.oerszebra;

import android.content.SharedPreferences;

interface SettingsProvider {
    void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key);

    void setOnChangeListener(OnChangeListener onChangeListener);

    int getSettingFunction();

    boolean isSettingAutoMakeForcedMoves();

    int getSettingRandomness();

    String getSettingForceOpening();

    boolean isSettingHumanOpenings();

    boolean isSettingPracticeMode();

    boolean isSettingUseBook();

    boolean isSettingDisplayPv();

    boolean isSettingDisplayMoves();

    boolean isSettingDisplayLastMove();

    boolean isSettingDisplayEnableAnimations();

    int getSettingAnimationDelay();

    int getSettingZebraDepth();

    int getSettingZebraDepthExact();

    int getSettingZebraDepthWLD();

    public interface OnChangeListener {
        void onChange();
    }
}
