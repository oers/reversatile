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
import com.shurik.droidzebra.*;
import de.earthlingz.oerszebra.*;
import de.earthlingz.oerszebra.BoardView.BoardView;
import de.earthlingz.oerszebra.BoardView.BoardViewModel;

import static com.shurik.droidzebra.ZebraEngine.PLAYER_BLACK;


public class GuessMoveActivity extends FragmentActivity {

    private BoardView boardView;
    private BoardViewModel boardViewModel;
    private GuessMoveModeManager manager;
    private ImageView sideToMoveCircle;
    private TextView hintText;

    private EngineConfig engineConfig;
    private GlobalSettingsLoader globalSettingsLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        globalSettingsLoader = new GlobalSettingsLoader(getApplicationContext());
        engineConfig = globalSettingsLoader.createEngineConfig();


        this.manager = new GuessMoveModeManager(ZebraEngine.get(
                new AndroidContext(getApplicationContext())),
                engineConfig);
        setContentView(R.layout.activity_guess_move);
        boardView = (BoardView) findViewById(R.id.guess_move_board);
        boardViewModel = manager;
        boardView.setBoardViewModel(boardViewModel);
        boardView.requestFocus();
        sideToMoveCircle = (ImageView) findViewById(R.id.side_to_move_circle);
        hintText = (TextView) findViewById(R.id.guess_move_text);
        newGame();
    }

    @Override
    protected void onResume() {
        super.onResume();
        globalSettingsLoader.setOnSettingsChangedListener(() -> {
            this.engineConfig = globalSettingsLoader.createEngineConfig();
            this.manager.updateGlobalConfig(engineConfig);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        globalSettingsLoader.setOnSettingsChangedListener(null);

    }

    private void newGame() {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Generating game");
        progressDialog.show();
        boardView.setOnMakeMoveListener(null);

        boardView.setDisplayMoves(false);
        boardView.setDisplayEvals(true);

        manager.generate(new GuessMoveModeManager.GuessMoveListener() {
            @Override
            public void onGenerated(GameState state) {

                runOnUiThread(() -> {
                    boardView.setOnMakeMoveListener(move -> manager.guess(move));
                    updateSideToMove(state);
                    progressDialog.hide();

                });

            }

            @Override
            public void onSideToMoveChanged(GameState state) {
                runOnUiThread(() -> updateSideToMove(state));
            }

            @Override
            public void onCorrectGuess() {
                setHintText(Color.CYAN, R.string.guess_move_correct);
                setBoardViewPlayable();
            }

            @Override
            public void onBadGuess() {
                setHintText(Color.RED, R.string.guess_move_incorrect);

            }

        });
    }

    private void setBoardViewPlayable() {
        boardView.setDisplayMoves(true);
        boardView.setDisplayEvals(true);
        boardView.setOnMakeMoveListener(move1 -> {
            try {
                manager.move(move1);
            } catch (InvalidMove ignored) {
            }
        });
    }

    private void setHintText(int cyan, int guess_move_correct) {
        hintText.setTextColor(cyan);
        hintText.setText(guess_move_correct);
    }

    private void updateSideToMove(GameState state) {
        if (state.getSideToMove() == PLAYER_BLACK) {
            sideToMoveCircle.setImageResource(R.drawable.black_circle);
            hintText.setText(R.string.guess_black_move_hint);
            hintText.setTextColor(Color.BLACK);

        } else {
            sideToMoveCircle.setImageResource(R.drawable.white_circle);
            hintText.setText(R.string.guess_white_move_hint);
            hintText.setTextColor(Color.WHITE);
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
