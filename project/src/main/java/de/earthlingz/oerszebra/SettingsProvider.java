package de.earthlingz.oerszebra;

interface SettingsProvider {

    void setOnSettingsChangedListener(OnSettingsChangedListener onSettingsChangedListener);

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

    int getSettingAnimationDuration();

    int getSettingZebraDepth();

    int getSettingZebraDepthExact();

    int getSettingZebraDepthWLD();

    int getSettingSlack();

    int getSettingPerturbation();

    int getComputerMoveDelay();

    interface OnSettingsChangedListener {
        void onSettingsChanged();
    }
}
