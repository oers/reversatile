package de.earthlingz.oerszebra;

import com.shurik.droidzebra.EngineConfig;

public interface SettingsProvider {

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

    String getSettingAnalysisDrawerSide();

    int getSettingAnimationDuration();

    int getSettingZebraDepth();

    int getSettingZebraDepthExact();

    int getSettingZebraDepthWLD();

    /** Search depth used for "Analyze Game" specifically - independent of live play's strength setting. */
    int getSettingAnalysisDepth();

    int getSettingAnalysisDepthExact();

    int getSettingAnalysisDepthWLD();

    int getSettingSlack();

    int getSettingPerturbation();

    int getComputerMoveDelay();

    EngineConfig createEngineConfig();

    interface OnSettingsChangedListener {
        void onSettingsChanged();
    }
}
