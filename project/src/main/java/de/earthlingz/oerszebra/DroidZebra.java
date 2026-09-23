/* Copyright (C) 2010 by Alex Kompel  */
/* This file is part of DroidZebra.

	DroidZebra is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	DroidZebra is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with DroidZebra.  If not, see <http://www.gnu.org/licenses/>
*/

package de.earthlingz.oerszebra;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.shurik.droidzebra.EngineConfig;
import com.shurik.droidzebra.GameState;
import com.shurik.droidzebra.GameStateListener;
import com.shurik.droidzebra.InvalidMove;
import com.shurik.droidzebra.Move;
import com.shurik.droidzebra.ZebraEngine;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;

import de.earthlingz.oerszebra.BoardView.BoardView;
import de.earthlingz.oerszebra.BoardView.GameStateBoardModel;
import de.earthlingz.oerszebra.analysis.AnalysisAdapter;
import de.earthlingz.oerszebra.analysis.GameAnalyzer;
import de.earthlingz.oerszebra.analysis.MoveEval;
import de.earthlingz.oerszebra.guessmove.GuessMoveActivity;
import de.earthlingz.oerszebra.parser.GameParser;

import static de.earthlingz.oerszebra.GameSettingsConstants.FUNCTION_HUMAN_VS_HUMAN;
import static de.earthlingz.oerszebra.GameSettingsConstants.FUNCTION_ZEBRA_BLACK;
import static de.earthlingz.oerszebra.GameSettingsConstants.FUNCTION_ZEBRA_VS_ZEBRA;
import static de.earthlingz.oerszebra.GameSettingsConstants.FUNCTION_ZEBRA_WHITE;
import static de.earthlingz.oerszebra.GlobalSettingsLoader.DEFAULT_SETTING_SENDMAIL;
import static de.earthlingz.oerszebra.GlobalSettingsLoader.OnSettingsChangedListener;
import static de.earthlingz.oerszebra.GlobalSettingsLoader.SETTINGS_KEY_FUNCTION;
import static de.earthlingz.oerszebra.GlobalSettingsLoader.SETTINGS_KEY_SENDMAIL;
import static de.earthlingz.oerszebra.GlobalSettingsLoader.SHARED_PREFS_NAME;


public class DroidZebra extends AppCompatActivity implements MoveStringConsumer,
        OnSettingsChangedListener, BoardView.OnMakeMoveListener, GameStateListener, ZebraEngine.OnEngineErrorListener {
    private ClipboardManager clipboard;
    private ZebraEngine engine;


    private boolean mBusyDialogUp = false;
    private boolean isHintUp = false;
    private boolean mIsInitCompleted = false;
    private boolean mActivityActive = false;

    private BoardView mBoardView;
    private GameStateBoardModel state = ZebraServices.getBoardState();

    private GameParser parser = ZebraServices.getGameParser();
    private WeakReference<AlertDialog> alert = null;

    public SettingsProvider settingsProvider;
    private GameStateListener handler = new GameStateHandlerProxy(this);
    private GameState gameState;
    private EngineConfig engineConfig;
    private Menu menu;

    private GameAnalyzer gameAnalyzer;
    private boolean suppressNextGameOverDialog = false;
    // Tracks whether the *live* game's board is currently sitting on a
    // game-over position - updated only by onBoard()/onGameOver() below,
    // which (unlike GameAnalyzer's own internal listener) only ever fire
    // for the live gameState. Read by analyzeGame() so it only arranges to
    // suppress a duplicate Game Over dialog when the game it's analyzing
    // was actually finished - not when "Analyze Game" is invoked mid-game
    // via the options menu, where restoring the live position afterwards
    // never re-reaches game-over and the flag would otherwise stay set,
    // silently swallowing the *next* real game-over dialog.
    private boolean liveGameIsOver = false;
    private List<MoveEval> lastAnalysisResults = Collections.emptyList();
    // The full move sequence of the game analysis last ran on, captured once
    // up front - NOT re-derived from gameState.exportMoveSequence() at jump
    // time, since after a first jump gameState only reflects the (shorter)
    // position navigated to, which would truncate any later jump forward.
    private byte[] analyzedGameMoves;
    // Whether that game had ended. jumpToMove() replays it in full, which
    // then re-fires game-over.
    private boolean analyzedGameWasOver;
    // A board action (move, undo, redo, undo all) made while the analysis
    // or a jump still had the engine. Those would silently no-op against
    // the live gameState, so they run once the live game is back instead.
    private Runnable pendingActionAfterAnalysis;
    // True from jumpToMove() until its walk has settled.
    private boolean jumpInProgress = false;
    // Set when a drawer row is tapped while analysis is still running:
    // jumpToMove() can't fire immediately without racing whatever ply's
    // engine.newGame() call is currently in flight, so the tap is deferred
    // until analyzeGame()'s onFinished confirms the analyzer has actually
    // stopped. Null when no jump is pending.
    private Integer pendingAnalysisJumpPly;
    // Used only by jumpToMove()'s walk-back-via-undo (see there for why).
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // Bumped by each jumpToMove() call; see jumpToMove()/walkToPly().
    private int jumpAttemptId = 0;
    private DrawerLayout analysisDrawerLayout;
    private RecyclerView analysisDrawerRecyclerView;
    private Button analysisDrawerHandle;
    private AnalysisAdapter analysisAdapter;
    private TextView analysisProgressView;


    public void resetStatusView() {
        runOnUiThread(() -> {
            TextView viewById = findViewById(R.id.status_opening);
            if (viewById != null) {
                viewById.setText("");
            }
        });
    }

    public boolean evalsDisplayEnabled() {
        return settingsProvider.isSettingPracticeMode() || isHintUp;
    }

    public void startNewGameAndResetUI() {
        startNewGame();

        resetAndLoadOnGuiThread();

    }

    private void startNewGame() {
        clearAnalysisResults();
        engine.newGame(engineConfig, new ZebraEngine.OnGameStateReadyListener() {
            @Override
            public void onGameStateReady(GameState gameState) {
                DroidZebra.this.gameState = gameState;
                gameState.setGameStateListener(handler);
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        Analytics.ask(this);
    }

    /* Creates the menu items */
    @Override
    @SuppressLint("RestrictedApi")
    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);

        if(menu instanceof MenuBuilder){
            MenuBuilder m = (MenuBuilder) menu;
            m.setOptionalIconsVisible(true);
        }

        return super.onCreateOptionsMenu(menu);
    }

    public boolean initialized() {
        return mIsInitCompleted;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!mIsInitCompleted) return false;
        cancelAnalysisIfRunning();
        switch (item.getItemId()) {
            case R.id.menu_new_game:
                startNewGameAndResetUI();
                return true;
            case R.id.menu_quit:
                finish();
                return true;
            case R.id.menu_take_back:
                undo();
                return true;
            case R.id.menu_goto_beginning:
                undoAll();
                return true;
            case R.id.menu_take_redo:
                redo();
                return true;
            case R.id.menu_settings: {
                // Launch Preference activity
                Intent i = new Intent(this, SettingsPreferences.class);
                startActivity(i);
            }
            return true;
            case R.id.menu_guess_move: {
                // Launch GuessMove activity
                Intent i = new Intent(this, GuessMoveActivity.class);
                startActivity(i);
            }
            return true;
            case R.id.menu_switch_sides: {
                switchSides();
            }
            break;
            case R.id.menu_enter_moves: {
                enterMoves();
            }
            break;
            case R.id.menu_mail: {
                sendMail();
            }
            return true;
            case R.id.menu_hint: {
                showHint();
            }
            return true;
            case R.id.menu_rotate: {
                rotate();
            }
            return true;
            case R.id.menu_analyze_game: {
                analyzeGame();
            }
            return true;
        }
        return false;
    }

    void redo() {
        if (deferWhileAnalysisBusy(this::redo)) {
            return;
        }
        engine.redoMove(gameState);
    }

    void undo() {
        if (deferWhileAnalysisBusy(this::undo)) {
            return;
        }
        engine.undoMove(gameState);
    }

    /**
     * While the analysis (or a jump to an analyzed move) has the engine,
     * board actions on the live gameState would be silently dropped. This
     * stops the analysis and keeps {@code action} to run once the live game
     * is back. Returns false if nothing is busy and the caller should just
     * go ahead.
     */
    private boolean deferWhileAnalysisBusy(Runnable action) {
        boolean analyzing = gameAnalyzer != null && gameAnalyzer.isRunning();
        if (!analyzing && !jumpInProgress) {
            return false;
        }
        pendingActionAfterAnalysis = action;
        if (analyzing) {
            pendingAnalysisJumpPly = null;
            gameAnalyzer.cancel();
        }
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        String type = intent.getType();

        Log.i("Intent", type + " " + action);

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            switch (type) {
                case "text/plain":

                    String dataString = intent.getStringExtra(Intent.EXTRA_TEXT);
                    if(Strings.isNullOrEmpty(dataString)) {
                        dataString = Strings.nullToEmpty(intent.getDataString());
                    }
                    Analytics.log("intent", dataString);
                    Analytics.converse("intent", null);
                    consumeMovesString(dataString); // Handle text being sent

                    break;
                case "message/rfc822":
                    String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                    Analytics.converse("intent", null);
                    Analytics.log("intent", text);
                    consumeMovesString(text); // Handle text being sent

                    break;
                default:
                    Log.e("intent", "unknown intent");
                    break;
            }
            getIntent().removeExtra("key");
        } else {
            Log.e("intent", "unknown intent");
        }
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Analytics.setApp(this);
        Analytics.build();

        // Back means "take back a move" here. With predictive back enabled
        // (enableOnBackInvokedCallback in the manifest) Android 13+ no longer
        // delivers KEYCODE_BACK to onKeyDown, so this has to go through the
        // back dispatcher instead.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (analysisDrawerLayout != null && analysisDrawerRecyclerView != null
                        && analysisDrawerLayout.isDrawerOpen(analysisDrawerRecyclerView)) {
                    analysisDrawerLayout.closeDrawer(analysisDrawerRecyclerView);
                    return;
                }
                undo();
            }
        });

        clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

        setContentView(R.layout.spash_layout);
        hideActionBar();

        engine = ZebraEngine.get(new AndroidContext(getApplicationContext()));
        engine.setOnDebugListener(new ZebraEngine.OnEngineDebugListener() {
            @Override
            public void onDebug(String message) {
                Log.d("engine", message);
            }
        });
        this.settingsProvider = new GlobalSettingsLoader(this);
        this.settingsProvider.setOnSettingsChangedListener(this);
        this.engineConfig = settingsProvider.createEngineConfig();

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        Log.i("Intent", type + " " + action);
        engine.setOnErrorListener(this); //TODO don't forget to remove later to avoid memory leak
        if(engine.getState() != ZebraEngine.ENGINE_STATE.ES_INITIAL) {
            onReady(savedInstanceState, action, type, intent);
        } else {
            engine.onReady(() -> {
                onReady(savedInstanceState, action, type, intent);
            });
        }
    }

    private void onReady(Bundle savedInstanceState, String action, String type, Intent intent) {
        setContentView(R.layout.board_layout);
        showActionBar();
        getState().reset();
        mBoardView = findViewById(R.id.board);
        mBoardView.setBoardViewModel(getState());
        mBoardView.setOnMakeMoveListener(this);
        mBoardView.requestFocus();
        setupAnalysisDrawer();
        if (savedInstanceState != null) {
            mBoardView.setRotated(savedInstanceState.getBoolean("board_rotated", false));
        }

        //https://developer.android.com/develop/ui/views/layout/edge-to-edge?hl=de#java
        ViewCompat.setOnApplyWindowInsetsListener(mBoardView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            // Apply the insets as a margin to the view. This solution sets only the
            // bottom, left, and right dimensions, but you can apply whichever insets are
            // appropriate to your layout. You can also update the view padding if that's
            // more appropriate.
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            mlp.topMargin = insets.top;
            mlp.leftMargin = insets.left;
            mlp.bottomMargin = insets.bottom;
            mlp.rightMargin = insets.right;
            v.setLayoutParams(mlp);

            // Return CONSUMED if you don't want want the window insets to keep passing
            // down to descendant views.
            return WindowInsetsCompat.CONSUMED;
        });

        resetStatusView();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            handleIntent(intent);
        } else if (savedInstanceState != null
                && savedInstanceState.containsKey("moves_played_count")
                && savedInstanceState.getInt("moves_played_count") > 0) {
            startNewGameAndResetUI(savedInstanceState.getInt("moves_played_count"), savedInstanceState.getByteArray("moves_played"));
        } else {
            startNewGameAndResetUI();
        }

        mIsInitCompleted = true;
    }

    private void startNewGameAndResetUI(LinkedList<Move> moves) {
        clearAnalysisResults();
        Analytics.log("new_game", new GameState(8, moves).getMoveSequenceAsString());
        engine.newGame(moves, engineConfig, new ZebraEngine.OnGameStateReadyListener() {
            @Override
            public void onGameStateReady(GameState gameState1) {
                DroidZebra.this.gameState = gameState1;
                gameState1.setGameStateListener(handler);

                resetAndLoadOnGuiThread();
            }
        });
    }

    public GameState getGameState() {
        return gameState;
    }

    // Package-private: lets androidTest diagnostics (WthorReplayTest) report
    // the engine's ENGINE_STATE in a timeout failure message, so a silently
    // dropped undo()/redo() (the guard in ZebraEngine#undoMove/redoMove
    // no-ops outside ES_USER_INPUT_WAIT) is distinguishable at a glance from
    // one that was genuinely applied but landed on the wrong position.
    ZebraEngine.ENGINE_STATE getEngineState() {
        return engine.getState();
    }

    private void startNewGameAndResetUI(int moves_played_count, byte[] moves_played) {
        Analytics.log("new_game", new GameState(8, moves_played, moves_played_count).getMoveSequenceAsString());
        engine.newGame(moves_played, moves_played_count, engineConfig, new ZebraEngine.OnGameStateReadyListener() {
            @Override
            public void onGameStateReady(GameState gameState1) {
                DroidZebra.this.gameState = gameState1;
                gameState1.setGameStateListener(handler);
                String moveSequenceAsString = gameState.getMoveSequenceAsString();
                Log.i("start", moveSequenceAsString);

                resetAndLoadOnGuiThread();
            }
        });
    }

    private void resetAndLoadOnGuiThread() {
        runOnUiThread(() -> {
            getState().reset();
            resetStatusView();
            loadUISettings();
        });
    }

    private void showActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.show();
        }
    }

    private void hideActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
    }

    private void loadEngineSettings() {
        this.engineConfig = settingsProvider.createEngineConfig();
        if (engine != null) {
            engine.updateConfig(gameState, engineConfig);
        }
    }

    private void loadUISettings() {
        if (mBoardView != null) {
            mBoardView.setDisplayAnimations(settingsProvider.isSettingDisplayEnableAnimations());
            mBoardView.setAnimationDuration(settingsProvider.getSettingAnimationDuration());
            mBoardView.setDisplayLastMove(settingsProvider.isSettingDisplayLastMove());
            mBoardView.setDisplayMoves(settingsProvider.isSettingDisplayMoves());
            mBoardView.setDisplayEvals(evalsDisplayEnabled());


            setStatus();
        }

    }

    private void setStatus() {
        TextView viewById = findViewById(R.id.status_settings);

        if(viewById != null) {
            int depth = settingsProvider.getSettingZebraDepth();
            int depthExact = settingsProvider.getSettingZebraDepthExact();
            String reachedDepth = "0";
            int moveNumber = 1;
            if(gameState != null) {
                reachedDepth = gameState.getReachedDepth();
                moveNumber = gameState.getDisksPlayed();
            }
            String state = getString(R.string.status_state_idle);
            if(engine != null ) {
                switch (engine.getState()) {
                    case ES_PLAY_IN_PROGRESS:
                        state = getString(R.string.status_state_thinking);
                        break;
                    default:
                        state = getString(R.string.status_state_idle);
                }
            }
            viewById.setText(
                    String.format(getString(R.string.display_depth), depth, depthExact, reachedDepth, moveNumber, state)
            );
        }
    }


    private void sendMail() {
        //GetNowTime
        Calendar calendar = Calendar.getInstance();
        Date nowTime = calendar.getTime();
        StringBuilder sbBlackPlayer = new StringBuilder();
        StringBuilder sbWhitePlayer = new StringBuilder();
        GameState gs = gameState;
        SharedPreferences settings = getSharedPreferences(SHARED_PREFS_NAME, 0);


        Intent intent = new Intent();
        Intent chooser = Intent.createChooser(intent, "");

        intent.setAction(Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        intent.putExtra(
                Intent.EXTRA_EMAIL,
                new String[]{settings.getString(SETTINGS_KEY_SENDMAIL, DEFAULT_SETTING_SENDMAIL)});

        intent.putExtra(Intent.EXTRA_SUBJECT, getResources().getString(R.string.app_name));

        Analytics.converse("send_mail", null);

        //get BlackPlayer and WhitePlayer
        String playerLabel = getResources().getString(R.string.player_label);
        String enginePrefix = getResources().getString(R.string.app_name) + "-";
        switch (settingsProvider.getSettingFunction()) { //TODO this might cause a problem, because settings provider is not a source of truth here. It should be taken from ZebraEngine
            case FUNCTION_HUMAN_VS_HUMAN:
                sbBlackPlayer.append(playerLabel);
                sbWhitePlayer.append(playerLabel);
                break;
            case FUNCTION_ZEBRA_BLACK:
                sbBlackPlayer.append(enginePrefix);
                sbBlackPlayer.append(settingsProvider.getSettingZebraDepth());
                sbBlackPlayer.append("/");
                sbBlackPlayer.append(settingsProvider.getSettingZebraDepthExact());
                sbBlackPlayer.append("/");
                sbBlackPlayer.append(settingsProvider.getSettingZebraDepthWLD());

                sbWhitePlayer.append(playerLabel);
                break;
            case FUNCTION_ZEBRA_WHITE:
                sbBlackPlayer.append(playerLabel);

                sbWhitePlayer.append(enginePrefix);
                sbWhitePlayer.append(settingsProvider.getSettingZebraDepth());
                sbWhitePlayer.append("/");
                sbWhitePlayer.append(settingsProvider.getSettingZebraDepthExact());
                sbWhitePlayer.append("/");
                sbWhitePlayer.append(settingsProvider.getSettingZebraDepthWLD());
                break;
            case FUNCTION_ZEBRA_VS_ZEBRA:
                sbBlackPlayer.append(enginePrefix);
                sbBlackPlayer.append(settingsProvider.getSettingZebraDepth());
                sbBlackPlayer.append("/");
                sbBlackPlayer.append(settingsProvider.getSettingZebraDepthExact());
                sbBlackPlayer.append("/");
                sbBlackPlayer.append(settingsProvider.getSettingZebraDepthWLD());

                sbWhitePlayer.append(enginePrefix);
                sbWhitePlayer.append(settingsProvider.getSettingZebraDepth());
                sbWhitePlayer.append("/");
                sbWhitePlayer.append(settingsProvider.getSettingZebraDepthExact());
                sbWhitePlayer.append("/");
                sbWhitePlayer.append(settingsProvider.getSettingZebraDepthWLD());
            default:
        }
        StringBuilder sb = new StringBuilder();
        sb.append(getResources().getString(R.string.mail_generated));
        sb.append("\r\n");
        sb.append(getResources().getString(R.string.mail_date));
        sb.append(" ");
        sb.append(nowTime);
        sb.append("\r\n\r\n");
        sb.append(getResources().getString(R.string.mail_move));
        sb.append(" ");
        String sbMovesString = gs.getMoveSequenceAsString();
        sb.append(sbMovesString);
        sb.append("\r\n\r\n");
        sb.append(sbBlackPlayer.toString());
        sb.append("  (B)  ");
        sb.append(getState().getBlackScore());
        sb.append(":");
        sb.append(getState().getWhiteScore());
        sb.append("  (W)  ");
        sb.append(sbWhitePlayer.toString());

        intent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        // Intent
        // Verify the original intent will resolve to at least one activity
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(chooser);
        }
    }

    private void switchSides() {
        int newFunction = -1;

        if (settingsProvider.getSettingFunction() == FUNCTION_ZEBRA_WHITE)
            newFunction = FUNCTION_ZEBRA_BLACK;
        else if (settingsProvider.getSettingFunction() == FUNCTION_ZEBRA_BLACK)
            newFunction = FUNCTION_ZEBRA_WHITE;

        if (newFunction > 0) {
            SharedPreferences settings = getSharedPreferences(SHARED_PREFS_NAME, 0);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(SETTINGS_KEY_FUNCTION, String.format(Locale.getDefault(), "%d", newFunction));
            editor.apply();
        }

        loadUISettings();
        loadEngineSettings();

    }

    private void showHint() {
        if (!settingsProvider.isSettingPracticeMode()) {
            setHintUp(true);
            engine.loadEvals(gameState, engineConfig);
        }
    }

    @Override
    protected void onDestroy() {
        // The engine outlives this activity (e.g. on rotation), so an analysis
        // or jump still running must not call back into it later.
        if (gameAnalyzer != null) {
            gameAnalyzer.release();
        }
        jumpAttemptId++;
        jumpInProgress = false;
        pendingActionAfterAnalysis = null;
        pendingAnalysisJumpPly = null;
        mainHandler.removeCallbacksAndMessages(null);

        if(gameState != null) {
            engine.disconnect(gameState);
            gameState.removeGameStateListener();
        }

        super.onDestroy();
    }

    void showDialog(DialogFragment dialog, String tag) {
        if (mActivityActive) {
            runOnUiThread(() -> dialog.show(getSupportFragmentManager(), tag));
        }
    }

    public void showPassDialog() {
        DialogFragment newFragment = DialogPass.newInstance();
        showDialog(newFragment, "dialog_pass");
    }

    public void showGameOverDialog() {
        DialogFragment newFragment = DialogGameOver.newInstance();
        showDialog(newFragment, "dialog_gameover");
    }

    private void enterMoves() {
        DialogFragment newFragment = EnterMovesDialog.newInstance(clipboard);
        showDialog(newFragment, "dialog_moves");
    }

    public void consumeMovesString(String s) {
        final LinkedList<Move> moves = parser.makeMoveList(s);
        startNewGameAndResetUI(moves);
    }

    private void showBusyDialog() {
        if (!mBusyDialogUp && engine.isThinking(gameState)) {
            DialogFragment newFragment = DialogBusy.newInstance();
            mBusyDialogUp = true;
            showDialog(newFragment, "dialog_busy");
        }
    }

    public void dismissBusyDialog() {
        if (mBusyDialogUp) {
            Fragment prev = getSupportFragmentManager().findFragmentByTag("dialog_busy");
            if (prev != null) {
                DialogFragment df = (DialogFragment) prev;
                df.dismiss();
            }
            mBusyDialogUp = false;
        }
    }

    public void setHintUp(boolean value) {
        isHintUp = value;
        this.mBoardView.setDisplayEvals(evalsDisplayEnabled());
    }

    public void showAlertDialog(String msg) {
        Analytics.error(msg, gameState);
        runOnUiThread(DroidZebra.this::startNewGameAndResetUI);
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle(R.string.dialog_error_title);
        alertDialog.setMessage(msg);
        alertDialog.setPositiveButton(R.string.dialog_ok, (dialog, id) -> alert = null);
        runOnUiThread(() -> alert = new WeakReference<>(alertDialog.show()));
    }

    public WeakReference<AlertDialog> getAlert() {
        return alert;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mActivityActive = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mActivityActive = true;
    }

    @Override
    protected void onSaveInstanceState(@Nonnull Bundle outState) {
        GameState gs = gameState;
        if (gs != null) {
            byte[] moves = gs.exportMoveSequence();
            outState.putByteArray("moves_played", moves);
            outState.putInt("moves_played_count", moves.length);
            outState.putInt("version", 1);
        }
        if (mBoardView != null) {
            outState.putBoolean("board_rotated", mBoardView.isRotated());
        }

        super.onSaveInstanceState(outState);
    }

    public GameStateBoardModel getState() {
        return state;
    }

    public BoardView getBoardView() {
        return mBoardView;
    }

    @Override
    public void onSettingsChanged() {
        loadUISettings();
        loadEngineSettings();
    }

    @Override
    public void onMakeMove(Move move) {
        if (deferWhileAnalysisBusy(() -> onMakeMove(move))) {
            return;
        }
        if (getState().isValidMove(move)) {
            // if zebra is still thinking - no move is possible yet - throw a busy dialog
            if (engine.isThinking(gameState) && !engine.isHumanToMove(gameState, engineConfig)) {
                showBusyDialog();
            } else {
                try {
                    engine.makeMove(gameState, move);
                } catch (InvalidMove e) {
                    Log.e("Invalid Move", e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public void onError(String error) {
        this.showAlertDialog(error);
    }

    @Override
    public void onBoard(GameState gameState) {
        // Any live-board update means we're not (or no longer) sitting on a
        // game-over position - onGameOver() below sets this back to true
        // for the one call that actually lands on game-over, which always
        // fires after this.
        liveGameIsOver = false;
        int sideToMove = gameState.getSideToMove();

        //triggers animations
        boolean boardChanged = state.update(gameState);

        setStatusViewScores(sideToMove);

        setStatus();

        if (gameState.getOpening() != null) {
            ((TextView)findViewById(R.id.status_opening)).setText(gameState.getOpening());
        }

        if (!boardChanged) {
            mBoardView.invalidate();
        }
    }

    private void setStatusViewScores(int sideToMove) {
        String scoreText;
        if (sideToMove == ZebraEngine.PLAYER_BLACK) {
            //with dot before
            scoreText = String.format(Locale.getDefault(), "•%d", state.getBlackScore());
        } else {
            scoreText = String.format(Locale.getDefault(), "%d", state.getBlackScore());
        }
        TextView black = findViewById(R.id.blackscore);
        black.setText(scoreText);

        if (sideToMove == ZebraEngine.PLAYER_WHITE) {
            //with dot behind
            scoreText = String.format(Locale.getDefault(), "%d•", state.getWhiteScore());
        } else {
            scoreText = String.format(Locale.getDefault(), "%d", state.getWhiteScore());
        }

        TextView white = findViewById(R.id.whitescore);
        white.setText(scoreText);
    }

    @Override
    public void onPass() {
        this.showPassDialog();
    }

    @Override
    public void onGameOver() {
        liveGameIsOver = true;
        state.processGameOver();
        runOnUiThread(() -> mBoardView.invalidate());//TODO Id doubt runOnUIThread is necessary here
        if (suppressNextGameOverDialog) {
            // reached game-over again as a side effect of restoring the live
            // game after analysis - don't pop the dialog a second time
            suppressNextGameOverDialog = false;
        } else {
            this.showGameOverDialog();
        }
    }

    /**
     * Same as the live game's engineConfig, except for the search depth,
     * which uses the "Analysis Search Depth" setting instead - independent
     * of live play's strength, since it's the deciding factor in how long
     * each of the (up to 60) searches analyzeGame() runs takes, especially
     * in the branchier midgame.
     */
    private EngineConfig buildAnalysisConfig() {
        return engineConfig.alterDepths(
                settingsProvider.getSettingAnalysisDepth(),
                settingsProvider.getSettingAnalysisDepthExact(),
                settingsProvider.getSettingAnalysisDepthWLD());
    }

    /**
     * Evaluates every move of the game (finished, or still in progress when
     * invoked from the options menu) and shows the results in the analysis
     * drawer. The live game is left exactly as it was once this completes
     * (or is cancelled via {@link #cancelAnalysisIfRunning()}).
     */
    public void analyzeGame() {
        if (gameAnalyzer != null && gameAnalyzer.isRunning()) {
            return;
        }
        if (jumpInProgress) {
            // gameState is only partway through the jump's walk right now
            pendingActionAfterAnalysis = this::analyzeGame;
            return;
        }
        final byte[] originalMoves = gameState.exportMoveSequence();
        final List<MoveEval> resultsSoFar = new ArrayList<>();
        // "Analyze Game" is reachable both from the Game Over dialog (the
        // game is always finished there) and from the options menu (which
        // can fire mid-game) - capture which one this is up front, since
        // GameAnalyzer runs its own separate engine session and never
        // touches the live gameState/liveGameIsOver while it runs.
        final boolean wasGameOver = liveGameIsOver;

        analyzedGameMoves = originalMoves;
        analyzedGameWasOver = wasGameOver;

        setAnalysisProgressVisible(true);
        gameAnalyzer = new GameAnalyzer(engine);
        gameAnalyzer.start(gameState, buildAnalysisConfig(), wasGameOver, new GameAnalyzer.Listener() {
            @Override
            public void onPlyStarted(int ply, int total) {
                // Show the ply currently being computed right away, as a
                // placeholder row after whatever's already finished, instead
                // of only appearing once its result lands. Plies are
                // analyzed newest-first (see GameAnalyzer#start), so the one
                // just starting always has a lower number than everything
                // already in resultsSoFar - it belongs at the end of the
                // list, not the front.
                showInFlightRow(MoveEval.pending(ply, new Move(analyzedGameMoves[ply - 1])));
            }

            @Override
            public void onPlyEvalUpdated(int ply, int total, MoveEval interimEval) {
                // The engine reports progressively (iterative deepening) as
                // it searches a position, not just once at the end - replace
                // the in-flight row with each refined estimate as it arrives
                // instead of only showing a value once the ply is fully done.
                showInFlightRow(interimEval);
            }

            private void showInFlightRow(MoveEval inFlightRow) {
                List<MoveEval> withInFlightRow = new ArrayList<>(resultsSoFar.size() + 1);
                withInFlightRow.addAll(resultsSoFar);
                withInFlightRow.add(inFlightRow);
                updateAnalysisDrawer(withInFlightRow, resultsSoFar.isEmpty());
            }

            @Override
            public void onProgress(int done, int total, MoveEval latest) {
                updateAnalysisProgress(done, total);
                resultsSoFar.add(latest);
                updateAnalysisDrawer(new ArrayList<>(resultsSoFar), done == 1);
            }

            @Override
            public void onFinished(List<MoveEval> results, boolean wasCancelled, boolean timedOut) {
                setAnalysisProgressVisible(false);
                lastAnalysisResults = results;
                updateAnalysisDrawer(results, false);
                if (pendingAnalysisJumpPly != null) {
                    int targetPly = pendingAnalysisJumpPly;
                    pendingAnalysisJumpPly = null;
                    jumpToMove(targetPly);
                    return;
                }
                if (timedOut) {
                    showAnalysisTimedOutDialog();
                }
                // Only arrange to swallow the next Game Over dialog when
                // restoring the live game is actually expected to land back
                // on a game-over position and re-fire onGameOver() - i.e.
                // when the game being analyzed was actually finished. For a
                // mid-game "Analyze Game" (from the options menu) the
                // restore never reaches game-over, so leaving this flag set
                // would instead silently suppress the *next real* game-over
                // dialog, whenever this game (or a later one) actually ends.
                if (wasGameOver) {
                    suppressNextGameOverDialog = true;
                }
                Runnable pendingAction = pendingActionAfterAnalysis;
                pendingActionAfterAnalysis = null;
                restoreLiveGame(originalMoves, pendingAction);
            }
        });
    }

    // Reloads the game the analysis ran on, then runs afterRestore (a board
    // action the user made during the analysis, if any) once the engine is
    // actually waiting for input on it.
    private void restoreLiveGame(byte[] moves, Runnable afterRestore) {
        engine.newGame(moves, moves.length, engineConfig, new ZebraEngine.OnGameStateReadyListener() {
            @Override
            public void onGameStateReady(GameState restored) {
                gameState = restored;
                restored.setGameStateListener(handler);
                resetAndLoadOnGuiThread();
                if (afterRestore != null) {
                    runOnUiThread(() -> runWhenSettled(restored, moves.length, afterRestore));
                }
            }
        });
    }

    // Waits until the engine is waiting for input on gs with realMoves real
    // (non-pass) moves played - undo/redo/moves are silently dropped before
    // that - then runs action. Gives up if another game took over meanwhile.
    private void runWhenSettled(GameState gs, int realMoves, Runnable action) {
        if (gs != gameState) {
            return;
        }
        boolean settled = engine.getState() == ZebraEngine.ENGINE_STATE.ES_USER_INPUT_WAIT
                && realMovesPlayed(gs) == realMoves;
        if (!settled) {
            mainHandler.postDelayed(() -> runWhenSettled(gs, realMoves, action), 50);
            return;
        }
        action.run();
    }

    // The engine's disksPlayed counts pass turns too; the analysis and its
    // drawer count real moves only (GameState#exportMoveSequence skips passes).
    private static int realMovesPlayed(GameState gs) {
        return gs.exportMoveSequence().length;
    }

    private void showAnalysisTimedOutDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_error_title)
                .setMessage(R.string.analysis_timed_out)
                .setPositiveButton(R.string.dialog_ok, (dialog, id) -> { })
                .show();
    }

    /** Stops any in-progress analysis; a no-op if none is running. */
    void cancelAnalysisIfRunning() {
        if (gameAnalyzer != null && gameAnalyzer.isRunning()) {
            gameAnalyzer.cancel();
        }
    }

    private void updateAnalysisProgress(int done, int total) {
        if (analysisProgressView != null) {
            analysisProgressView.setText(getString(R.string.analysis_progress, done, total));
        }
    }

    private void setAnalysisProgressVisible(boolean visible) {
        if (analysisProgressView != null) {
            analysisProgressView.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public List<MoveEval> getLastAnalysisResults() {
        return lastAnalysisResults;
    }

    private void setupAnalysisDrawer() {
        analysisDrawerLayout = findViewById(R.id.board_drawer_layout);
        analysisDrawerRecyclerView = findViewById(R.id.analysis_drawer);
        analysisDrawerHandle = findViewById(R.id.analysis_drawer_handle);
        analysisProgressView = findViewById(R.id.status_analysis_progress);

        if (analysisDrawerLayout == null || analysisDrawerRecyclerView == null) {
            return;
        }

        // The drawer is a direct child of the edge-to-edge DrawerLayout (see
        // mBoardView's inset handling above). Padding alone (the original
        // approach) only pushed the *content* down, leaving the drawer's
        // own opaque background painted over the header; a top margin
        // fixed that, but adding the action bar's own height on top of the
        // status bar inset (the first attempt at this) overshot - turns
        // out the action bar already reserves its own space the same way
        // it does for mBoardView's parent, so the *same* insets.top-only
        // margin mBoardView uses is exactly right here too.
        ViewCompat.setOnApplyWindowInsetsListener(analysisDrawerRecyclerView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            mlp.topMargin = insets.top;
            mlp.bottomMargin = insets.bottom;
            v.setLayoutParams(mlp);
            return WindowInsetsCompat.CONSUMED;
        });

        analysisDrawerRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        analysisAdapter = new AnalysisAdapter(moveEval -> requestJumpToMove(moveEval.getPly()));
        analysisDrawerRecyclerView.setAdapter(analysisAdapter);

        boolean openFromLeft = "left".equals(settingsProvider.getSettingAnalysisDrawerSide());
        int gravity = openFromLeft ? GravityCompat.START : GravityCompat.END;

        DrawerLayout.LayoutParams drawerParams =
                (DrawerLayout.LayoutParams) analysisDrawerRecyclerView.getLayoutParams();
        drawerParams.gravity = gravity;
        analysisDrawerRecyclerView.setLayoutParams(drawerParams);

        if (analysisDrawerHandle != null) {
            RelativeLayout.LayoutParams handleParams =
                    (RelativeLayout.LayoutParams) analysisDrawerHandle.getLayoutParams();
            handleParams.addRule(RelativeLayout.ALIGN_PARENT_START, openFromLeft ? RelativeLayout.TRUE : 0);
            handleParams.addRule(RelativeLayout.ALIGN_PARENT_END, openFromLeft ? 0 : RelativeLayout.TRUE);
            analysisDrawerHandle.setLayoutParams(handleParams);

            analysisDrawerHandle.setOnClickListener(v -> {
                if (analysisDrawerLayout.isDrawerOpen(analysisDrawerRecyclerView)) {
                    analysisDrawerLayout.closeDrawer(analysisDrawerRecyclerView);
                } else {
                    analysisDrawerLayout.openDrawer(analysisDrawerRecyclerView);
                }
            });

            analysisDrawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
                @Override
                public void onDrawerOpened(View drawerView) {
                    analysisDrawerHandle.setText(R.string.analysis_drawer_handle_open);
                }

                @Override
                public void onDrawerClosed(View drawerView) {
                    analysisDrawerHandle.setText(R.string.analysis_drawer_handle_closed);
                }
            });
        }
    }

    private void updateAnalysisDrawer(List<MoveEval> results, boolean autoOpen) {
        if (analysisAdapter != null) {
            // GameAnalyzer now analyzes newest-move-first (see its start()),
            // so results already arrive in the newest-first order this
            // drawer wants to display - no reversal needed.
            analysisAdapter.setItems(results);
        }
        if (analysisDrawerHandle != null && !results.isEmpty()) {
            analysisDrawerHandle.setVisibility(View.VISIBLE);
        }
        if (autoOpen && analysisDrawerLayout != null && analysisDrawerRecyclerView != null) {
            analysisDrawerLayout.openDrawer(analysisDrawerRecyclerView);
        }
    }

    // Called whenever a new, unrelated game replaces the live one: whatever
    // the analysis or a jump still had pending belongs to the old game. A
    // running analysis is dropped without reporting back - its onFinished
    // would otherwise restore the old game over the new one.
    private void clearAnalysisResults() {
        if (gameAnalyzer != null) {
            gameAnalyzer.release();
        }
        setAnalysisProgressVisible(false);
        jumpAttemptId++;
        jumpInProgress = false;
        pendingActionAfterAnalysis = null;
        pendingAnalysisJumpPly = null;
        suppressNextGameOverDialog = false;
        lastAnalysisResults = Collections.emptyList();
        analyzedGameMoves = null;
        if (analysisAdapter != null) {
            analysisAdapter.clear();
        }
        if (analysisDrawerHandle != null) {
            analysisDrawerHandle.setVisibility(View.GONE);
        }
        if (analysisDrawerLayout != null && analysisDrawerRecyclerView != null
                && analysisDrawerLayout.isDrawerOpen(analysisDrawerRecyclerView)) {
            analysisDrawerLayout.closeDrawer(analysisDrawerRecyclerView);
        }
    }

    /**
     * Handles a tap on an analysis result. If analysis is still running,
     * this is an action like any other (undo, redo, a move, rotate...) and
     * should cancel it - but unlike those, jumping needs its own
     * engine.newGame() call, which can't safely fire while the analyzer's
     * own in-flight ply might still land and race it. The jump is deferred
     * until analyzeGame()'s onFinished confirms the analyzer has actually
     * settled; otherwise it happens immediately.
     */
    void requestJumpToMove(int targetPly) {
        // the jump replaces whatever board action was still waiting
        pendingActionAfterAnalysis = null;
        if (gameAnalyzer != null && gameAnalyzer.isRunning()) {
            pendingAnalysisJumpPly = targetPly;
            cancelAnalysisIfRunning();
        } else {
            jumpToMove(targetPly);
        }
    }

    /**
     * Navigates the live board to the position right after the
     * {@code targetPly}-th real move (as numbered by {@link GameAnalyzer},
     * passes not counted), while keeping undo/redo working across the
     * *entire* analyzed game afterward - not just back to whichever ply this
     * lands on.
     * <p>
     * Loads the whole analyzed game (not just a prefix up to targetPly) so
     * the engine actually replays every move in this session, then walks
     * back to the target ply with undoMove() - the same primitive the live
     * Undo button uses. Only that way does redoMove()'s own redo-stack know
     * about the moves after the target ply at all; truncating the
     * "provided moves" array up front (the previous approach) left the
     * engine with no memory of anything past the jump target, so redo()
     * could never move past it.
     * <p>
     * The walk itself forces human-vs-human, practice mode off and
     * auto-played forced moves off - not the live engineConfig, which is
     * restored once the walk lands on the target ply. Practice mode makes
     * every individual undo() pause for a full post-move eval search, which
     * at real search depths walking back many plies could take minutes. And
     * if the computer plays either side, or forced moves are auto-played,
     * a single undo() steps back more than one move (existing, intentional
     * behavior for live play), which would overshoot the exact ply this
     * needs to land on.
     */
    void jumpToMove(int targetPly) {
        if (gameState == null || analyzedGameMoves == null
                || (gameAnalyzer != null && gameAnalyzer.isRunning())) {
            return;
        }
        if (analysisDrawerLayout != null && analysisDrawerRecyclerView != null) {
            analysisDrawerLayout.closeDrawer(analysisDrawerRecyclerView);
        }
        // Bumped so a second jumpToMove() (e.g. two drawer taps in quick
        // succession) makes any still-in-flight walk from a previous call
        // recognize itself as stale and stop polling, rather than looping
        // forever on a GameState the engine has since moved on from (its
        // own undoMove()/onGameStateReady calls would otherwise silently
        // no-op against a no-longer-current engine session - see
        // ZebraEngine#undoMove).
        final int attemptId = ++jumpAttemptId;
        jumpInProgress = true;
        // Replaying a finished game in full reaches game-over again; that's
        // navigation, not the game ending, so no Game Over dialog.
        if (analyzedGameWasOver) {
            suppressNextGameOverDialog = true;
        }
        final byte[] moves = analyzedGameMoves;
        EngineConfig walkConfig = engineConfig
                .alterEngineFunction(FUNCTION_HUMAN_VS_HUMAN)
                .alterPracticeMode(false)
                .alterAutoForcedMoves(false);
        engine.newGame(moves, moves.length, walkConfig, new ZebraEngine.OnGameStateReadyListener() {
            @Override
            public void onGameStateReady(GameState freshGameState) {
                if (attemptId != jumpAttemptId) {
                    return;
                }
                gameState = freshGameState;
                gameState.setGameStateListener(handler);
                walkToPly(attemptId, freshGameState, moves.length, targetPly);
            }
        });
    }

    // See jumpToMove() for why this walks back via undoMove() instead of
    // just replaying a prefix. Counts real moves, not the engine's
    // disksPlayed, which also counts pass turns: the target ply comes from
    // the analysis, which skips passes, and each undoMove() takes back
    // exactly one real move (it absorbs a pass on the way). Seeing the
    // expected count - not just ES_USER_INPUT_WAIT alone - is what confirms
    // the most recent undoMove() has landed: it's async, so the engine can
    // still read ES_USER_INPUT_WAIT for a moment right after issuing it.
    private void walkToPly(int attemptId, GameState gs, int expectedMoves, int targetPly) {
        if (attemptId != jumpAttemptId) {
            return; // superseded by a later jumpToMove() call
        }
        boolean ready = engine.getState() == ZebraEngine.ENGINE_STATE.ES_USER_INPUT_WAIT
                && realMovesPlayed(gs) == expectedMoves;
        if (!ready) {
            mainHandler.postDelayed(() -> walkToPly(attemptId, gs, expectedMoves, targetPly), 50);
            return;
        }
        if (expectedMoves <= targetPly) {
            engine.updateConfig(gs, engineConfig);
            awaitJumpSettled(attemptId, gs);
            return;
        }
        engine.undoMove(gs);
        walkToPly(attemptId, gs, expectedMoves - 1, targetPly);
    }

    // Restoring the real engineConfig (e.g. re-enabling practice mode) can
    // itself kick the engine back into computing - practice mode
    // recomputes evals for the landed position as soon as settings change
    // - so ES_USER_INPUT_WAIT has to be reconfirmed before declaring the
    // jump done, the same way each undo step above does. Skipping this and
    // finishing right after updateConfig() left a window where the UI
    // looked ready but a redo()/undo() issued immediately after would
    // silently no-op (ZebraEngine#redoMove/undoMove only act while
    // ES_USER_INPUT_WAIT) - caught by testRedoWorksPastAJumpedToPosition.
    private void awaitJumpSettled(int attemptId, GameState gs) {
        if (attemptId != jumpAttemptId) {
            return;
        }
        if (engine.getState() != ZebraEngine.ENGINE_STATE.ES_USER_INPUT_WAIT) {
            mainHandler.postDelayed(() -> awaitJumpSettled(attemptId, gs), 50);
            return;
        }
        jumpInProgress = false;
        resetAndLoadOnGuiThread();
        Runnable pendingAction = pendingActionAfterAnalysis;
        pendingActionAfterAnalysis = null;
        if (pendingAction != null) {
            runOnUiThread(pendingAction);
        }
    }

    @Override
    public void onMoveEnd() {
        this.dismissBusyDialog();
        if (isHintUp) {
            this.setHintUp(false);
            engine.updateConfig(gameState, engineConfig);
        }

    }

    @Override
    public void onEval(String eval) {
        //do nothing
    }

    @Override
    public void onPv(byte[] pv) {
        //do nothing, happens too fast
    }

    public void rotate() {
        cancelAnalysisIfRunning();
        // A pure view-layer transform (see BoardView#setRotated): the engine, its
        // move history, and the undo/redo stack are never touched, so rotating
        // always works and never loses undo/redo state, regardless of when it's
        // called (mid-game, after undo, while the engine is thinking, ...).
        mBoardView.setRotated(!mBoardView.isRotated());
    }

    public void undo(View view) {
        undo();
    }

    public void undoAll(View view) {
        undoAll();
    }

    public void redo(View view) {
        redo();
    }

    public void rotate(View view) {
        rotate();
    }


    //-------------------------------------------------------------------------
    // Pass Dialog
    public static class DialogPass extends DialogFragment {

        // The engine's MSG_PASS handling (ZebraEngine.java) blocks
        // synchronously in waitForEngineState(ES_PLAY) once it shows this
        // dialog, waiting for engine.pass(...) to be called - the only
        // thing that unblocks it. Passing isn't actually a choice being
        // offered (there's no legal move - it's forced), so tapping outside
        // or pressing back should do exactly what OK does, not nothing:
        // guarded with passed so however this ends up dismissed, pass() is
        // called exactly once.
        private boolean passed = false;

        public static DialogPass newInstance() {
            return new DialogPass();
        }

        public DroidZebra getDroidZebra() {
            return (DroidZebra) getActivity();
        }

        @Override
        @Nonnull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.dialog_pass_text)
                    .setPositiveButton(R.string.dialog_ok, (dialog, id) -> pass())
                    .create();
        }

        // Overriding the fragment's own onCancel/onDismiss instead of
        // relying only on AlertDialog.Builder's setOnCancelListener/
        // setOnDismissListener: those are plain Dialog-level listeners
        // layered on top of DialogFragment's own dismiss/cancel bookkeeping,
        // and didn't reliably end up calling pass() for a tap-outside/back
        // dismissal in practice (reported: the dialog visually closed but
        // the game never continued). These fragment lifecycle callbacks are
        // the dialog's own, guaranteed path, whichever way it closes.
        @Override
        public void onCancel(@Nonnull DialogInterface dialog) {
            super.onCancel(dialog);
            pass();
        }

        @Override
        public void onDismiss(@Nonnull DialogInterface dialog) {
            super.onDismiss(dialog);
            pass();
        }

        private void pass() {
            if (passed) {
                return;
            }
            passed = true;
            DroidZebra zebra = getDroidZebra();
            if (zebra != null) {
                zebra.engine.pass(zebra.gameState, zebra.engineConfig);
            }
        }
    }

    //-------------------------------------------------------------------------
    // Game Over Dialog
    public static class DialogGameOver extends DialogFragment {

        public static DialogGameOver newInstance() {
            return new DialogGameOver();
        }

        public DroidZebra getDroidZebra() {
            return (DroidZebra) getActivity();
        }

        public void refreshContent(View dialog) {
            int winner;
            int blackScore = getDroidZebra().getState().getBlackScore();
            int whiteScore = getDroidZebra().getState().getWhiteScore();
            if (whiteScore > blackScore)
                winner = R.string.gameover_text_white_wins;
            else if (whiteScore < blackScore)
                winner = R.string.gameover_text_black_wins;
            else
                winner = R.string.gameover_text_draw;

            ((TextView) dialog.findViewById(R.id.gameover_text)).setText(winner);

            ((TextView) dialog.findViewById(R.id.gameover_score)).setText(String.format(Locale.getDefault(), "%d : %d", blackScore, whiteScore));
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {

            getDialog().setTitle(R.string.gameover_title);

            View v = inflater.inflate(R.layout.gameover, container, false);

            Button button;
            button = v.findViewById(R.id.gameover_choice_new_game);
            button.setOnClickListener(
                    v15 -> {
                        dismiss();
                        getDroidZebra().startNewGameAndResetUI();
                    });

            button = v.findViewById(R.id.gameover_choice_rotate);
            button.setOnClickListener(
                    click -> {
                        dismiss();
                        getDroidZebra().rotate();
                    });

            button = v.findViewById(R.id.gameover_choice_beginning);
            button.setOnClickListener(
                    click -> {
                        dismiss();
                        getDroidZebra().undoAll();
                    });

            button = v.findViewById(R.id.gameover_choice_analyze);
            button.setOnClickListener(
                    click -> {
                        dismiss();
                        getDroidZebra().analyzeGame();
                    });

            button = v.findViewById(R.id.gameover_choice_switch);
            button.setOnClickListener(
                    v12 -> {
                        dismiss();
                        getDroidZebra().switchSides();
                    });

            button = v.findViewById(R.id.gameover_choice_cancel);
            button.setOnClickListener(
                    v1 -> dismiss());

            button = v.findViewById(R.id.gameover_choice_options);
            button.setOnClickListener(
                    v13 -> {
                        dismiss();

                        // start settings
                        Intent i = new Intent(getDroidZebra(), SettingsPreferences.class);
                        startActivity(i);
                    });

            button = v.findViewById(R.id.gameover_choice_email);
            button.setOnClickListener(
                    v14 -> {
                        dismiss();
                        getDroidZebra().sendMail();
                    });

            refreshContent(v);

            return v;
        }

        @Override
        public void onResume() {
            super.onResume();
            refreshContent(getView());
        }
    }

    void undoAll() {
        if (deferWhileAnalysisBusy(this::undoAll)) {
            return;
        }
        engine.undoAll(gameState);
    }

    //-------------------------------------------------------------------------
    // Pass Dialog
    public static class DialogBusy extends DialogFragment {

        public static DialogBusy newInstance() {
            return new DialogBusy();
        }

        public DroidZebra getDroidZebra() {
            return (DroidZebra) getActivity();
        }

        @Nonnull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            ProgressDialog pd = new ProgressDialog(getActivity()) {
                @Override
                public boolean onKeyDown(int keyCode, KeyEvent event) {
                    stopZebra();
                    return super.onKeyDown(keyCode, event);
                }

                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        stopZebra();
                        return true;
                    }
                    return super.onTouchEvent(event);
                }

                private void stopZebra() {
                    ZebraEngine engine = getDroidZebra().engine;
                    engine.stopIfThinking(getDroidZebra().gameState);

                    getDroidZebra().mBusyDialogUp = false;
                    cancel();
                }
            };
            pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            pd.setMessage(getResources().getString(R.string.dialog_busy_message));
            return pd;
        }

        // With predictive back (targetSdk 36+) a back gesture neither reaches
        // the dialog's onKeyDown nor its onBackPressed - it just cancels the
        // dialog. Stopping the engine here covers that and every other way
        // the dialog gets cancelled.
        @Override
        public void onCancel(@Nonnull DialogInterface dialog) {
            super.onCancel(dialog);
            DroidZebra zebra = getDroidZebra();
            if (zebra != null) {
                zebra.engine.stopIfThinking(zebra.gameState);
                zebra.mBusyDialogUp = false;
            }
        }
    }
}
