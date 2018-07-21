package de.earthlingz.oerszebra.guessmove;

import com.shurik.droidzebra.EngineConfig;
import com.shurik.droidzebra.ZebraEngine;

public class GuessMoveModeManager {

    private final ZebraEngine engine;
    private final EngineConfig globalSettings;

    public GuessMoveModeManager(ZebraEngine engine, EngineConfig globalSettings) {

        this.engine = engine;
        this.globalSettings = globalSettings;
    }
}
