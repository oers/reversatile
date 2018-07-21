package de.earthlingz.oerszebra.guessmove;

import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import com.shurik.droidzebra.Move;
import de.earthlingz.oerszebra.BoardView.BoardView;
import de.earthlingz.oerszebra.BoardView.BoardViewModel;
import de.earthlingz.oerszebra.R;
import de.earthlingz.oerszebra.StatusView;

public class GuessMoveActivity extends FragmentActivity implements BoardView.OnMakeMoveListener {

    private BoardView boardView;
    private BoardViewModel boardViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guess_move);
        boardView = (BoardView) findViewById(R.id.guess_move_board);
        boardViewModel = new BoardViewModel();
        boardView.setBoardViewModel(boardViewModel);
        boardView.setOnMakeMoveListener(this);
        boardView.requestFocus();
    }

    @Override
    public void onMakeMove(Move move) {

    }
}
