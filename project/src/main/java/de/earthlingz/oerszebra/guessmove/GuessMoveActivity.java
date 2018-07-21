package de.earthlingz.oerszebra.guessmove;

import android.app.ProgressDialog;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.view.View;
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
        boardView.requestFocus();
        findViewById(R.id.new_game_button).setOnClickListener(view -> {
            newGame();
        });

        newGame();
    }

    private void newGame() {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Generating game");
        progressDialog.show();
        manager.generate(state -> runOnUiThread(() -> {
            boardView.setOnMakeMoveListener(null);
            boardViewModel.update(state);
            boardView.setOnMakeMoveListener(this);
            progressDialog.hide();

        }));
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
