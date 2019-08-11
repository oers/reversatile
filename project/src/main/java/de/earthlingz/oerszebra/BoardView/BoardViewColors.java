package de.earthlingz.oerszebra.BoardView;

import android.content.res.Resources;
import de.earthlingz.oerszebra.R;
import de.earthlingz.oerszebra.Player;
import java.util.EnumMap;

public class BoardViewColors {
    final int HelpersValid;
    final int HelpersInvalid;
    final int SelectionValid;
    final int SelectionInvalid;
    final int ValidMoveIndicator;
    final int Line;
    final int Numbers;

    final int Evals;
    final int EvalsBest;
    final int PlayerBlack;
    final int PlayerWhite;
    final int EmptySquare;
    final int LastMoveMarker;

    final EnumMap<Player, Integer> PlayerColors;

    BoardViewColors(Resources resources) {
        HelpersValid = resources.getColor(R.color.board_color_helpers_valid);
        HelpersInvalid = resources.getColor(R.color.board_color_helpers_invalid);
        SelectionValid = resources.getColor(R.color.board_color_selection_valid);
        SelectionInvalid = resources.getColor(R.color.board_color_selection_invalid);
        ValidMoveIndicator = resources.getColor(R.color.board_color_valid_move_indicator);
        Line = resources.getColor(R.color.board_line);
        Numbers = resources.getColor(R.color.board_numbers);

        Evals = resources.getColor(R.color.Evals);
        EvalsBest = resources.getColor(R.color.EvalsBest);
        EmptySquare = resources.getColor(R.color.EmptySquare);
        LastMoveMarker = resources.getColor(R.color.LastMoveMarker);

        PlayerBlack = resources.getColor(R.color.PlayerBlack);
        PlayerWhite = resources.getColor(R.color.PlayerWhite);

        PlayerColors = initializePlayerColors();
    }

    private EnumMap<Player, Integer> initializePlayerColors() {
        EnumMap<Player, Integer> playerColors = new EnumMap<Player, Integer>(Player.class);

        playerColors.put(Player.PLAYER_BLACK, PlayerBlack);
        playerColors.put(Player.PLAYER_WHITE, PlayerWhite);
        playerColors.put(Player.PLAYER_EMPTY, EmptySquare);

        return playerColors;
    }

    public int playerColor(Player player) {
        return PlayerColors.get(player);
    }
}
