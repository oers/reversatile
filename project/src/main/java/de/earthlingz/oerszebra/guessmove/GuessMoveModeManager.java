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

    public void generate(GuessMoveListener guessMoveListener) {
        final int movesPlayed = random.nextInt(58) + 1;

        engine.newGame(generatorConfig, new ZebraEngine.OnGameStateReadyListener() {
            @Override
            public void onGameStateReady(GameState gameState) {
                GuessMoveModeManager.this.gameState = gameState;
                gameState.setHandler(new GameStateListener() {
                    private boolean generated = false;

                    @Override
                    public void onBoard(GameState state) {
                        if (!generated && movesPlayed == state.getDisksPlayed()) {
                            engine.updateConfig(gameState, guesserConfig);
                            guessMoveListener.onGenerated(state);
                            generated = true;
                        } else {
                            guessMoveListener.onBoard(state);
                        }
                    }

                });

            }
        });

    }

    public boolean isBest(Move move) {
        if (move == null) {
            return false;
        }
        for (CandidateMove candidateMove : gameState.getCandidateMoves()) {
            if(move.getMoveInt() == candidateMove.getMoveInt() && candidateMove.isBest){
                return true;
            }
        }
        return false;
    }

    public void move(Move move) throws InvalidMove {
        this.engine.makeMove(gameState, move);
    }

    public void redoMove() {
        engine.redoMove(gameState);
    }

    public void undoMove() {
        engine.undoMove(gameState);
    }

    public interface GuessMoveListener {
        void onGenerated(GameState state);

        void onBoard(GameState state);
    }
}
