package de.earthlingz.oerszebra.guessmove;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.shurik.droidzebra.GameState;
import com.shurik.droidzebra.InvalidMove;
import com.shurik.droidzebra.Move;
import com.shurik.droidzebra.ZebraEngine;
import de.earthlingz.oerszebra.AndroidContext;
import de.earthlingz.oerszebra.BoardView.BoardView;
import de.earthlingz.oerszebra.BoardView.GameStateBoardModel;
import de.earthlingz.oerszebra.GlobalSettingsLoader;
import de.earthlingz.oerszebra.R;
import de.earthlingz.oerszebra.SettingsPreferences;

import static com.shurik.droidzebra.ZebraEngine.PLAYER_BLACK;


public class GuessMoveActivity extends FragmentActivity implements BoardView.OnMakeMoveListener {

    private BoardView boardView;
    private GameStateBoardModel boardViewModel;
    private GuessMoveModeManager manager;
    private ImageView sideToMoveCircle;
    private TextView hintText;

    private Boolean guessed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.manager = new GuessMoveModeManager(ZebraEngine.get(
                new AndroidContext(getApplicationContext())),
                new GlobalSettingsLoader(getApplicationContext()).createEngineConfig());
        setContentView(R.layout.activity_guess_move);
        boardView = (BoardView) findViewById(R.id.guess_move_board);
        boardViewModel = new GameStateBoardModel();
        boardView.setBoardViewModel(boardViewModel);
        boardView.requestFocus();
        sideToMoveCircle = (ImageView) findViewById(R.id.side_to_move_circle);
        hintText = (TextView) findViewById(R.id.guess_move_text);
        newGame();
    }

    private void newGame() {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Generating game");
        progressDialog.show();
        manager.generate(new GuessMoveModeManager.GuessMoveListener() {
            @Override
            public void onGenerated(GameState state) {

                runOnUiThread(() -> {
                    boardView.setOnMakeMoveListener(null);
                    boardView.setDisplayMoves(false);
                    boardView.setDisplayEvals(false);
                    boardView.setOnMakeMoveListener(GuessMoveActivity.this);

                    boardViewModel.update(state);
                    if (state.getSideToMove() == PLAYER_BLACK) {
                        sideToMoveCircle.setImageResource(R.drawable.black_circle);
                        hintText.setText(R.string.guess_black_move_hint);
                        hintText.setTextColor(Color.BLACK);

                    } else {
                        sideToMoveCircle.setImageResource(R.drawable.white_circle);
                        hintText.setText(R.string.guess_white_move_hint);
                        hintText.setTextColor(Color.WHITE);
                    }
                    guessed = false;
                    progressDialog.hide();

                });

            }

            @Override
            public void onBoard(GameState state) {
                runOnUiThread(() -> boardViewModel.update(state));

            }
        });
    }


    @Override
    public void onMakeMove(Move move) {
        if (guessed) {
            try {
                manager.move(move);
            } catch (InvalidMove ignored) {
            }
            return;
        }
        if (manager.isBest(move)) {
            boardView.setDisplayMoves(true);
            boardView.setDisplayEvals(true);
            hintText.setTextColor(Color.CYAN);
            hintText.setText(R.string.guess_move_correct);
            guessed = true;
        } else {
            hintText.setTextColor(Color.RED);
            hintText.setText(R.string.guess_move_incorrect);
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
                manager.undoMove();
                return true;
            case R.id.menu_take_redo:
                manager.redoMove();
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
