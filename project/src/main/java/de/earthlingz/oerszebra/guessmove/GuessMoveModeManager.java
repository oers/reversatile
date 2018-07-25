package de.earthlingz.oerszebra.guessmove;

import android.support.annotation.Nullable;
import com.shurik.droidzebra.*;
import de.earthlingz.oerszebra.BoardView.BoardViewModel;
import de.earthlingz.oerszebra.GameSettingsConstants;

import java.util.Arrays;
import java.util.Random;


public class GuessMoveModeManager implements BoardViewModel {

    private final ZebraEngine engine;
    private EngineConfig globalSettings;
    private EngineConfig generatorConfig;
    private EngineConfig guesserConfig;
    private GameState gameState;
    private Random random = new Random();
    private CandidateMove[] candidateMoves = new CandidateMove[0];
    private BoardViewModelListener listener = new BoardViewModelListener() {
    };

    GuessMoveModeManager(ZebraEngine engine, EngineConfig globalSettings) {

        this.engine = engine;
        this.globalSettings = globalSettings;
        initConfigs(globalSettings);
    }

    private void initConfigs(EngineConfig globalSettings) {
        generatorConfig = createGeneratorConfig(globalSettings);
        guesserConfig = createGuesserConfig(globalSettings);
    }

    private static EngineConfig createGuesserConfig(EngineConfig gs) {
        return new EngineConfig(
                GameSettingsConstants.FUNCTION_HUMAN_VS_HUMAN,
                20, 22, 1, false,
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
        this.candidateMoves = new CandidateMove[0];
        engine.newGame(generatorConfig, new ZebraEngine.OnGameStateReadyListener() {
            @Override
            public void onGameStateReady(GameState gameState) {
                GuessMoveModeManager.this.gameState = gameState;
                gameState.setGameStateListener(new GameStateListener() {
                    private boolean generated = false;

                    @Override
                    public void onBoard(GameState state) {
                        if ((!generated && movesPlayed == state.getDisksPlayed()) || state.getDisksPlayed() > movesPlayed) {
                            engine.updateConfig(gameState, guesserConfig);
                            guessMoveListener.onGenerated(state);

                            generated = true;
                        } else {
//                            guessMoveListener.onSideToMoveChanged(state);
                        }
                        listener.onBoardStateChanged();

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
            if (move.getMoveInt() == candidateMove.getMoveInt() && candidateMove.isBest) {
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

    @Override
    public int getBoardSize() {
        return 8;
    }

    @Nullable
    @Override
    public Move getLastMove() {
        return new Move(gameState.getLastMove());
    }

    @Override
    public CandidateMove[] getCandidateMoves() {
        return this.candidateMoves;
    }

    @Override
    public boolean isValidMove(Move move) {
        for (CandidateMove candidateMove : gameState.getCandidateMoves()) {
            if (candidateMove.getMoveInt() == move.getMoveInt()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Move getNextMove() {
        return new Move(gameState.getNextMove());
    }

    @Override
    public void setBoardViewModelListener(BoardViewModelListener boardViewModelListener) {
        this.listener = boardViewModelListener;
    }

    @Override
    public void removeBoardViewModeListener() {
        this.listener = new BoardViewModelListener() {
        };
    }

    @Override
    public boolean isFieldFlipped(int x, int y) {
        return false;
    }

    @Override
    public boolean isFieldEmpty(int x, int y) {
        return this.gameState.getByteBoard().isEmpty(x, y);
    }

    @Override
    public boolean isFieldBlack(int x, int y) {
        return this.gameState.getByteBoard().isBlack(x, y);
    }

    public void updateGlobalConfig(EngineConfig engineConfig) {
        globalSettings = engineConfig;
        initConfigs(engineConfig);
        //TODO update config for current game

    }

    public void showMove(Move move) {
        for (CandidateMove candidateMove : this.candidateMoves) {
            if (candidateMove.getMoveInt() == move.getMoveInt()) {
                return;
            }
        }
        for (CandidateMove candidateMove : this.gameState.getCandidateMoves()) {
            if (candidateMove.getMoveInt() == move.getMoveInt()) {

                CandidateMove[] newCandidateMoves = Arrays.copyOf(candidateMoves, candidateMoves.length + 1);
                newCandidateMoves[newCandidateMoves.length - 1] = candidateMove;
                this.candidateMoves = newCandidateMoves;
                this.listener.onCandidateMovesChanged();
                break;
            }
        }

    }

    public void showAllMoves() {
        this.candidateMoves = gameState.getCandidateMoves();
        listener.onCandidateMovesChanged();
    }

    public interface GuessMoveListener {
        void onGenerated(GameState state);

        void onSideToMoveChanged(GameState state);

        void onCorrectGuess();

        void onBadGuess();

    }
}
