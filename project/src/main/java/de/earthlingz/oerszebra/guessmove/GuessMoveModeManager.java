package de.earthlingz.oerszebra.guessmove;

import androidx.annotation.Nullable;
import com.shurik.droidzebra.*;
import de.earthlingz.oerszebra.BoardView.AbstractBoardViewModel;
import de.earthlingz.oerszebra.GameSettingsConstants;
import de.earthlingz.oerszebra.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;


public class GuessMoveModeManager extends AbstractBoardViewModel {

    private final ZebraEngine engine;
    private EngineConfig generatorConfig;
    private EngineConfig guesserConfig;
    private GameState gameState = new GameState(8);
    private Random random = new Random();
    private CandidateMove[] candidateMoves = new CandidateMove[0];
    private BoardViewModelListener listener = new BoardViewModelListener() {
    };
    private GuessMoveListener guessMoveListener;

    GuessMoveModeManager(ZebraEngine engine, String opening) {

        this.engine = engine;
        initConfigs(opening);
    }

    private void initConfigs(String opening) {
        generatorConfig = createGeneratorConfig(opening);
        guesserConfig = createGuesserConfig();
    }

    private static EngineConfig createGuesserConfig() {
        return new EngineConfig(
                GameSettingsConstants.FUNCTION_HUMAN_VS_HUMAN,
                20, 22, 1, false, "",
                false,
                true,
                false,
                1,
                1,
                0
        );
    }

    private static EngineConfig createGeneratorConfig(String opening) {

        return new EngineConfig(
                GameSettingsConstants.FUNCTION_ZEBRA_VS_ZEBRA,
                8, 12, 1, true,
                opening,
                false,
                false,
                true,
                1,
                1,
                0
        );
    }

    public void generate(int minIn, int max, GuessMoveListener guessMoveListener) {
        int min = Math.max(minIn, 4);
        this.guessMoveListener = guessMoveListener;
        final int movesPlayed = random.nextInt(max - min) + min;
        this.candidateMoves = new CandidateMove[0];
        new GameGenerator(engine).generate(generatorConfig, guesserConfig, movesPlayed, gameState -> {
            GuessMoveModeManager.this.gameState = gameState;
            gameState.setGameStateListener(new GameStateListener() {
                @Override
                public void onBoard(GameState board) {
                    listener.onBoardStateChanged();
                    updateCandidateMoves(board.getCandidateMoves());
                    guessMoveListener.onSideToMoveChanged(board.getSideToMove());
                }
            });
            guessMoveListener.onGenerated(gameState.getSideToMove());
            listener.onBoardStateChanged();

        });


    }

    private void updateCandidateMoves(CandidateMove[] newCandidates) {
        ArrayList<CandidateMove> replacement = new ArrayList<>(this.candidateMoves.length);
        for (CandidateMove newCandidate : newCandidates) {
            for (CandidateMove oldCandidates : this.candidateMoves) {
                if (oldCandidates.getMoveInt() == newCandidate.getMoveInt()) {
                    replacement.add(newCandidate);
                }
            }
        }
        if (this.candidateMoves.length == replacement.size()) {
            this.candidateMoves = replacement.toArray(this.candidateMoves);
        } else {
            this.candidateMoves = replacement.toArray(new CandidateMove[replacement.size()]);
        }
    }

    public void guess(Move move) {
        if (move == null) {
            guessMoveListener.onBadGuess();
            return;
        }

        for (CandidateMove candidateMove : gameState.getCandidateMoves()) {
            if (move.getMoveInt() == candidateMove.getMoveInt() && candidateMove.isBest) {
                showAllMoves();
                gameState.setGameStateListener(new GameStateListener() {
                    @Override
                    public void onBoard(GameState board) {
                        candidateMoves = board.getCandidateMoves();
                        listener.onBoardStateChanged();
                        guessMoveListener.onSideToMoveChanged(board.getSideToMove());
                    }
                });

                this.guessMoveListener.onCorrectGuess();
                return;
            }
        }
        showMove(move);

        guessMoveListener.onBadGuess();
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
    public Player playerAt(int x, int y)
    {
        int player = gameState.getByteBoard().get(x,y);
        return Player.values()[player];
    }

    public void updateGlobalConfig(String opening) {
        initConfigs(opening);
    }

    private void showMove(Move move) {
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

    private void showAllMoves() {
        this.candidateMoves = gameState.getCandidateMoves();
        listener.onCandidateMovesChanged();
    }

    public interface GuessMoveListener {
        void onGenerated(int sideToMove);

        void onSideToMoveChanged(int sideToMove);

        void onCorrectGuess();

        void onBadGuess();

    }
}
