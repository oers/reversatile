package de.earthlingz.oerszebra.BoardView;

import de.earthlingz.oerszebra.Player;

public abstract class AbstractBoardViewModel implements BoardViewModel {

    @Override
    public int getBoardSize() {
        return 8;
    }



    @Override
    public Disc discAt(int x, int y) {
        Player player = playerAt(x,y);
        return new Disc(x, y, player, isFieldFlipped(x, y));
    }
}
