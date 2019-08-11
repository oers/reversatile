package de.earthlingz.oerszebra.BoardView;

import de.earthlingz.oerszebra.Player;

public class Disc {
    private int row;
    private int col;
    private Player player;
    private boolean was_flipped;

    public Disc(int x, int y, Player player, boolean was_flipped) {
        this.row = y;
        this.col = x;
        this.player = player;
        this.was_flipped = was_flipped;
    }

    public boolean wasFlipped() {
        return was_flipped;
    }

    public int row() {
        return row;
    }

    public int col() {
        return col;
    }

    public boolean isEmpty() {
        return player == Player.PLAYER_EMPTY;
    }

    public Player getPlayer() {
        return player;
    }

    public Player getOpponent() {
        return player.getOpponent();
    }
}

