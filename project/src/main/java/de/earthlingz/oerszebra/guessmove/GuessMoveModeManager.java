package de.earthlingz.oerszebra.guessmove;

import com.shurik.droidzebra.*;
import de.earthlingz.oerszebra.GameSettingsConstants;

import java.util.Random;


public class GuessMoveModeManager {

    private final ZebraEngine engine;
    private EngineConfig globalSettings;
    private EngineConfig generatorConfig;
    private EngineConfig guesserConfig;
    private GameState gameState;
    private Random random = new Random();

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

    public void generate(OnGenerated onGenerated) {
        final int movesPlayed = random.nextInt(58) + 1;

        engine.newGame(generatorConfig, new ZebraEngine.OnGameStateReadyListener() {
            @Override
            public void onGameStateReady(GameState gameState) {
                GuessMoveModeManager.this.gameState = gameState;
                gameState.setHandler(new GameStateListener() {
                    @Override
                    public void onBoard(GameState state) {
                        if (movesPlayed == state.getDisksPlayed()) {
                            engine.updateConfig(gameState, guesserConfig);
                            onGenerated.onGenerated(state);
                        }
                    }
                });

            }
        });

    }

    public void guessMove(Move move) {

    }

    public interface OnGenerated {
        void onGenerated(GameState state);
    }
}
