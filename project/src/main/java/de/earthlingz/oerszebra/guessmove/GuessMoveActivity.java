package de.earthlingz.oerszebra.guessmove;

import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.widget.Toast;
import com.shurik.droidzebra.Move;
import com.shurik.droidzebra.ZebraEngine;
import de.earthlingz.oerszebra.AndroidContext;
import de.earthlingz.oerszebra.BoardView.BoardView;
import de.earthlingz.oerszebra.BoardView.BoardViewModel;
import de.earthlingz.oerszebra.GlobalSettingsLoader;
import de.earthlingz.oerszebra.R;


public class GuessMoveActivity extends FragmentActivity implements BoardView.OnMakeMoveListener {

    private BoardView boardView;
    private BoardViewModel boardViewModel;
    private GuessMoveModeManager manager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.manager = new GuessMoveModeManager(ZebraEngine.get(
                new AndroidContext(getApplicationContext())),
                new GlobalSettingsLoader(getApplicationContext()).createEngineConfig());
        setContentView(R.layout.activity_guess_move);
        boardView = (BoardView) findViewById(R.id.guess_move_board);
        boardViewModel = new BoardViewModel();
        boardView.setBoardViewModel(boardViewModel);
        boardView.setOnMakeMoveListener(this);
        boardView.requestFocus();

        manager.generate(state -> runOnUiThread(() -> boardViewModel.update(state)));
    }


    @Override
    public void onMakeMove(Move move) {


        if (manager.isBest(move)) {
            boardView.setDisplayMoves(true);
            boardView.setDisplayEvals(true);
            Toast.makeText(this, "Correct!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Not the best move, try again", Toast.LENGTH_SHORT).show();
        }
    }
}
