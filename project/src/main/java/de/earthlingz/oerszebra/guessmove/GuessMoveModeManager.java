package de.earthlingz.oerszebra.guessmove;

import com.shurik.droidzebra.EngineConfig;
import com.shurik.droidzebra.ZebraEngine;
import de.earthlingz.oerszebra.GameSettingsConstants;

public class GuessMoveModeManager {

    private final ZebraEngine engine;
    private EngineConfig globalSettings;
    private EngineConfig generatorConfig;
    private EngineConfig guesserConfig;

    GuessMoveModeManager(ZebraEngine engine, EngineConfig globalSettings) {

        this.engine = engine;
        this.globalSettings = globalSettings;
        this.generatorConfig = createGeneratorConfig(globalSettings);
        guesserConfig = createGuesserConfig(globalSettings);
    }

    private static EngineConfig createGuesserConfig(EngineConfig gs) {
        return new EngineConfig(
                GameSettingsConstants.FUNCTION_HUMAN_VS_HUMAN,
                20, 22, 1, true,
                gs.randomness,
                gs.forcedOpening,
                gs.humanOpenings,
                true,
                gs.useBook,
                gs.slack,
                gs.perturbation,
                0
        );
    }

    private static EngineConfig createGeneratorConfig(EngineConfig gs) {

        return new EngineConfig(
                GameSettingsConstants.FUNCTION_ZEBRA_VS_ZEBRA,
                6, 6, 1, true,
                gs.randomness,
                gs.forcedOpening,
                gs.humanOpenings,
                false,
                gs.useBook,
                gs.slack,
                gs.perturbation,
                0
        );
    }

    public void generate() {

    }
}
