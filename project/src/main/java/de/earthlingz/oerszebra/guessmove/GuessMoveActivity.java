package de.earthlingz.oerszebra.guessmove;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.innovattic.rangeseekbar.RangeSeekBar;
import com.shurik.droidzebra.EngineConfig;
import com.shurik.droidzebra.InvalidMove;
import com.shurik.droidzebra.ZebraEngine;
import de.earthlingz.oerszebra.AndroidContext;
import de.earthlingz.oerszebra.BoardView.BoardView;
import de.earthlingz.oerszebra.BoardView.BoardViewModel;
import de.earthlingz.oerszebra.GlobalSettingsLoader;
import de.earthlingz.oerszebra.R;
import de.earthlingz.oerszebra.SettingsPreferences;

import static com.shurik.droidzebra.ZebraEngine.PLAYER_BLACK;


public class GuessMoveActivity extends AppCompatActivity implements RangeSeekBar.SeekBarChangeListener {

    private BoardView boardView;
    private BoardViewModel boardViewModel;
    private GuessMoveModeManager manager;
    private ImageView sideToMoveCircle;
    private TextView hintText;
    private GlobalSettingsLoader globalSettingsLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context applicationContext = getApplicationContext();
        globalSettingsLoader = new GlobalSettingsLoader(applicationContext);

        this.manager = new GuessMoveModeManager(ZebraEngine.get(
                new AndroidContext(applicationContext)),
                globalSettingsLoader.getDefaultOpening());
        setContentView(R.layout.activity_guess_move);
        boardView = findViewById(R.id.guess_move_board);
        boardViewModel = manager;
        boardView.setBoardViewModel(boardViewModel);
        boardView.requestFocus();
        sideToMoveCircle = findViewById(R.id.side_to_move_circle);
        hintText = findViewById(R.id.guess_move_text);
        Button button = findViewById(R.id.guess_move_new);
        button.setOnClickListener((a) -> newGame());
        newGame();

        RangeSeekBar range = findViewById(R.id.rangeSeekBar);
        range.setSeekBarChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        globalSettingsLoader.setOnSettingsChangedListener(() -> {
            this.manager.updateGlobalConfig(globalSettingsLoader.getDefaultOpening());
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        globalSettingsLoader.setOnSettingsChangedListener(null);

    }



    private void newGame() {
        TextView minText = findViewById(R.id.minText);
        int min = Integer.valueOf(minText.getText().toString());
        TextView maxText = findViewById(R.id.maxText);
        int max = Integer.valueOf(maxText.getText().toString());
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Generating game");
        progressDialog.show();
        setBoardViewUnplayable();

        manager.generate(min, max, new GuessMoveModeManager.GuessMoveListener() {
            @Override
            public void onGenerated(int sideToMove) {

                runOnUiThread(() -> {
                    boardView.setOnMakeMoveListener(move -> manager.guess(move));
                    updateSideToMoveCircle(sideToMove);
                    setGuessText(sideToMove);
                    progressDialog.dismiss();

                });

            }

            @Override
            public void onSideToMoveChanged(int sideToMove) {
                runOnUiThread(() -> {
                    updateSideToMoveCircle(sideToMove);
                    if (!guessed) {
                        setGuessText(sideToMove);
                    }
                });
            }

            private boolean guessed = false;

            @Override
            public void onCorrectGuess() {
                guessed = true;
                setHintText(Color.CYAN, R.string.guess_move_correct);
                setBoardViewPlayable();
            }

            @Override
            public void onBadGuess() {
                guessed = true;
                setHintText(Color.RED, R.string.guess_move_incorrect);
            }

        });
    }

    private void setBoardViewUnplayable() {
        boardView.setOnMakeMoveListener(null);

        boardView.setDisplayMoves(false);
        boardView.setDisplayEvals(true);
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

    private void setGuessText(int sideToMove) {
        if (sideToMove == PLAYER_BLACK) {
            hintText.setText(R.string.guess_black_move_hint);
            hintText.setTextColor(Color.BLACK);
        } else {
            hintText.setText(R.string.guess_white_move_hint);
            hintText.setTextColor(Color.WHITE);
        }

    }

    private void updateSideToMoveCircle(int sideToMove) {
        if (sideToMove == PLAYER_BLACK) {
            sideToMoveCircle.setImageResource(R.drawable.black_circle);
        } else {
            sideToMoveCircle.setImageResource(R.drawable.white_circle);
        }
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

    @Override
    public void onStartedSeeking() {
        return;
    }

    @Override
    public void onStoppedSeeking() {
        return;
    }

    @Override
    public void onValueChanged(int min, int max) {
        TextView minText = findViewById(R.id.minText);
        minText.setText(String.valueOf(min));
        TextView maxText = findViewById(R.id.maxText);
        maxText.setText(String.valueOf(max));
    }
}
