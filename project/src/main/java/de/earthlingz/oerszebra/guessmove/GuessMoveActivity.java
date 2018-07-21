package de.earthlingz.oerszebra.guessmove;

import android.app.ProgressDialog;
import android.content.Intent;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
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
import de.earthlingz.oerszebra.SettingsPreferences;

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.guess_move_context_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_new_game:
                newGame();
                return true;
            case R.id.menu_take_back:
//                manager.undoMove(gameState);
                return true;
            case R.id.menu_take_redo:
//                engine.redoMove(gameState);
                return true;
            case R.id.menu_settings: {
                // Launch Preference activity
                Intent i = new Intent(this, SettingsPreferences.class);
                startActivity(i);
            }
            return true;
        }
        return false;
    }

}
