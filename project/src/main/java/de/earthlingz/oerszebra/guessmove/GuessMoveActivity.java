package de.earthlingz.oerszebra.guessmove;

import android.app.ProgressDialog;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import com.shurik.droidzebra.Move;
import com.shurik.droidzebra.ZebraEngine;
import de.earthlingz.oerszebra.AndroidContext;
import de.earthlingz.oerszebra.BoardView.BoardView;
import de.earthlingz.oerszebra.BoardView.BoardViewModel;
import de.earthlingz.oerszebra.GlobalSettingsLoader;
import de.earthlingz.oerszebra.R;

import static com.shurik.droidzebra.ZebraEngine.PLAYER_BLACK;


public class GuessMoveActivity extends FragmentActivity implements BoardView.OnMakeMoveListener {

    private BoardView boardView;
    private BoardViewModel boardViewModel;
    private GuessMoveModeManager manager;
    private ImageView sideToMoveCircle;


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
        sideToMoveCircle = (ImageView) findViewById(R.id.side_to_move_circle);

        newGame();
    }

    private void newGame() {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Generating game");
        progressDialog.show();
        manager.generate(state -> runOnUiThread(() -> {
            boardView.setOnMakeMoveListener(null);
            boardView.setDisplayMoves(false);
            boardView.setDisplayEvals(false);
            boardView.setOnMakeMoveListener(this);

            boardViewModel.update(state);
            if (state.getSideToMove() == PLAYER_BLACK) {
                sideToMoveCircle.setImageResource(R.drawable.black_circle);
            } else {
                sideToMoveCircle.setImageResource(R.drawable.white_circle);
            }

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
