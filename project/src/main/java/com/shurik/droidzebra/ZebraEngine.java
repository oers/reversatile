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

package com.shurik.droidzebra;

import android.util.Log;
import de.earthlingz.oerszebra.BuildConfig;
import de.earthlingz.oerszebra.DroidZebraHandler;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import static de.earthlingz.oerszebra.GameSettingsConstants.*;
//import android.util.Log;

// DroidZebra -> ZebraEngine:public -async-> ZebraEngine thread(jni) -> Callback() -async-> DroidZebra:Handler 
public class ZebraEngine extends Thread {

    static public final int BOARD_SIZE = 8;

    private static String PATTERNS_FILE = "coeffs2.bin";
    private static String BOOK_FILE = "book.bin";
    private static String BOOK_FILE_COMPRESSED = "book.cmp.z";

    // board colors
    static public final byte PLAYER_BLACK = 0;
    static public final byte PLAYER_EMPTY = 1; // for board color
    static public final byte PLAYER_WHITE = 2;

    private static final int PLAYER_ZEBRA = 1; // for zebra skill in PlayerInfo

    // default parameters
    private static final int INFINITE_TIME = 10000000;
    private int computerMoveDelay = 0;
    private long mMoveStartTime = 0; //ms

    // messages
    private static final int
            MSG_ERROR = 0,
            MSG_BOARD = 1,
            MSG_CANDIDATE_MOVES = 2,
            MSG_GET_USER_INPUT = 3,
            MSG_PASS = 4,
            MSG_OPENING_NAME = 5,
            MSG_LAST_MOVE = 6,
            MSG_GAME_START = 7,
            MSG_GAME_OVER = 8,
            MSG_MOVE_START = 9,
            MSG_MOVE_END = 10,
            MSG_EVAL_TEXT = 11,
            MSG_PV = 12,
            MSG_CANDIDATE_EVALS = 13,
            MSG_ANALYZE_GAME = 14,
            MSG_NEXT_MOVE = 15,
            MSG_DEBUG = 65535;

    // engine state
    private static final int
            ES_INITIAL = 0,
            ES_READY2PLAY = 1,
            ES_PLAY = 2,
            ES_PLAY_IN_PROGRESS = 3,
            ES_USER_INPUT_WAIT = 4;

    private static final int
            UI_EVENT_EXIT = 0,
            UI_EVENT_MOVE = 1,
            UI_EVENT_UNDO = 2,
            UI_EVENT_SETTINGS_CHANGE = 3,
            UI_EVENT_REDO = 4;
    private PlayerInfo blackPlayerInfo = new PlayerInfo(0, 0, 0);
    private PlayerInfo zebraPlayerInfo = new PlayerInfo(4, 12, 12);
    private PlayerInfo whitePlayerInfo = new PlayerInfo(0, 0, 0);


    public void removeHandler() {
        mHandler = new EmptyHandler();
    }


    private transient GameState initialGameState;
    private transient GameState currentGameState;

    // current move
    private JSONObject mPendingEvent = null;
    private int mValidMoves[] = null;

    // mPlayerInfoChanged must be always inside synchronized block with playerInfoLock
    // :/ we must change how this work in the future (or we could make whole class synchronized? is it slow?)
    private boolean mPlayerInfoChanged = false;
    private final Object playerInfoLock = new Object();

    private int mSideToMove = PLAYER_ZEBRA;

    // context
    private GameContext mContext;

    // message sink
    private ZebraEngineMessageHandler mHandler = new EmptyHandler();

    // files folder
    private File mFilesDir;

    // synchronization
    static private final Object mJNILock = new Object();

    private final transient Object engineStateEventLock = new Object();

    private int mEngineState = ES_INITIAL;

    private boolean isRunning = false;

    private boolean bInCallback = false;

    public ZebraEngine(GameContext context) {
        mContext = context;
    }

    public void setHandler(ZebraEngineMessageHandler mHandler) {
        this.mHandler = mHandler;
    }

    private boolean initFiles() {
        mFilesDir = null;

        // first check if files exist on internal device
        File pattern = new File(mContext.getFilesDir(), PATTERNS_FILE);
        File book = new File(mContext.getFilesDir(), BOOK_FILE_COMPRESSED);
        if (pattern.exists() && book.exists()) {
            mFilesDir = mContext.getFilesDir();
            return true;
        }

        // if not - try external folder
        copyAsset(mContext, PATTERNS_FILE, mContext.getFilesDir());
        copyAsset(mContext, BOOK_FILE_COMPRESSED, mContext.getFilesDir());

        if (!pattern.exists() && !book.exists()) {
            // will be recreated from resources, the next time, maybe
            new File(mContext.getFilesDir(), PATTERNS_FILE).delete();
            new File(mContext.getFilesDir(), BOOK_FILE).delete();
            new File(mContext.getFilesDir(), BOOK_FILE_COMPRESSED).delete();
            throw new IllegalStateException("Kann coeeffs.bin und book nicht finden");
        }

        mFilesDir = mContext.getFilesDir();
        return true;
    }

    private void copyAsset(GameContext assetManager,
                           String fromAssetPath, File filesdir) {
        File target = new File(filesdir, fromAssetPath);
        try (InputStream in = assetManager.open(fromAssetPath); OutputStream out = new FileOutputStream(target)) {
            copyFile(in, out);
        } catch (Exception e) {
            Log.e(ZebraEngine.class.getSimpleName(), "copyAsset: " + fromAssetPath, e);
            throw new IllegalStateException("Datei konnte nicht geladen werden: " + fromAssetPath, e);
        }
    }

    private static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    private void waitForEngineState(int state, int milliseconds) {
        synchronized (engineStateEventLock) {
            if (mEngineState != state)
                try {
                    engineStateEventLock.wait(milliseconds);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

        }
    }

    private void waitForEngineState(int state) {
        synchronized (engineStateEventLock) {
            while (mEngineState != state && isRunning)
                try {
                    engineStateEventLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
        }
    }

    public void setEngineStatePlay() {
        setEngineState(ZebraEngine.ES_PLAY);
    }

    private void setEngineState(int state) {
        synchronized (engineStateEventLock) {
            mEngineState = state;
            engineStateEventLock.notifyAll();
        }
    }

    private int getEngineState() {
        return mEngineState;
    }

    //TODO This method requires JNI call, which is expensive
    //so maybe we should make it private and get this info differently
    public boolean gameInProgress() {
        return zeGameInProgress();
    }

    public void setRunning(boolean b) {
        boolean wasRunning = isRunning;
        isRunning = b;
        if (wasRunning && !isRunning) stopGame();
    }

    // tell zebra to stop thinking
    public void stopMove() {
        zeForceReturn();
    }

    // tell zebra to end current game
    public void stopGame() {
        zeForceExit();
        // if waiting for move - get back into the engine
        // every other state should work itself out
        if (getEngineState() == ES_USER_INPUT_WAIT) {
            mPendingEvent = new JSONObject();
            try {
                mPendingEvent.put("type", UI_EVENT_EXIT);
            } catch (JSONException e) {
                // Log.getStackTraceString(e);
            }
            setEngineState(ES_PLAY);
        }
    }

    public void makeMove(Move move) throws InvalidMove {
        if (!isValidMove(move))
            throw new InvalidMove();

        // if thinking on human time - stop
        if (isHumanToMove()
                && getEngineState() == ZebraEngine.ES_PLAY_IN_PROGRESS) {
            stopMove();
            waitForEngineState(ES_USER_INPUT_WAIT, 1000);
        }

        if (getEngineState() != ES_USER_INPUT_WAIT) {
            // Log.d("ZebraEngine", "Invalid Engine State");
            return;
        }

        // add move the the pending event and tell zebra to pick it up
        mPendingEvent = new JSONObject();
        try {
            mPendingEvent.put("type", UI_EVENT_MOVE);
            mPendingEvent.put("move", move.mMove);
        } catch (JSONException e) {
            // Log.getStackTraceString(e);
        }
        setEngineState(ES_PLAY);
    }

    public void undoMove() {
        // if thinking on human time - stop
        if (isHumanToMove()
                && getEngineState() == ZebraEngine.ES_PLAY_IN_PROGRESS) {
            stopMove();
            waitForEngineState(ES_USER_INPUT_WAIT, 1000);
        }

        if (getEngineState() != ES_USER_INPUT_WAIT) {
            // Log.d("ZebraEngine", "Invalid Engine State");
            return;
        }

        // create pending event and tell zebra to pick it up
        mPendingEvent = new JSONObject();
        try {
            mPendingEvent.put("type", UI_EVENT_UNDO);
        } catch (JSONException e) {
            // Log.getStackTraceString(e);
        }
        setEngineState(ES_PLAY);
    }

    public void redoMove() {
        // if thinking on human time - stop
        if (isHumanToMove()
                && getEngineState() == ZebraEngine.ES_PLAY_IN_PROGRESS) {
            stopMove();
            waitForEngineState(ES_USER_INPUT_WAIT, 1000);
        }

        if (getEngineState() != ES_USER_INPUT_WAIT) {
            // Log.d("ZebraEngine", "Invalid Engine State");
            return;
        }

        // create pending event and tell zebra to pick it up
        mPendingEvent = new JSONObject();
        try {
            mPendingEvent.put("type", UI_EVENT_REDO);
        } catch (JSONException e) {
            // Log.getStackTraceString(e);
        }
        setEngineState(ES_PLAY);
    }

    // notifications that some settings have changes - see if we care
    public void sendSettingsChanged() {
        // if we are waiting for input - restart the move (e.g. if sides switched)
        if (getEngineState() == ES_USER_INPUT_WAIT) {
            mPendingEvent = new JSONObject();
            try {
                mPendingEvent.put("type", UI_EVENT_SETTINGS_CHANGE);
            } catch (JSONException e) {
                // Log.getStackTraceString(e);
            }
            setEngineState(ES_PLAY);
        }
    }

    public void sendReplayMoves(List<Move> moves) {
        if (getEngineState() != ZebraEngine.ES_READY2PLAY) {
            stopGame();
            waitForEngineState(ZebraEngine.ES_READY2PLAY);
        }
        initialGameState = new GameState();
        initialGameState.setDisksPlayed(moves.size());
        initialGameState.setMoveSequence(toByte(moves));
        setEngineState(ES_PLAY);
    }

    // settings helpers


    public void setEngineFunction(int settingFunction, int depth, int depthExact, int depthWLD) {
        switch (settingFunction) {
            case FUNCTION_HUMAN_VS_HUMAN:
                setBlackPlayerInfo(new PlayerInfo(0, 0, 0));
                setWhitePlayerInfo(new PlayerInfo(0, 0, 0));
                break;
            case FUNCTION_ZEBRA_BLACK:
                setBlackPlayerInfo(new PlayerInfo(depth, depthExact, depthWLD));
                setWhitePlayerInfo(new PlayerInfo(0, 0, 0));
                break;
            case FUNCTION_ZEBRA_VS_ZEBRA:
                setBlackPlayerInfo(new PlayerInfo(depth, depthExact, depthWLD));
                setWhitePlayerInfo(new PlayerInfo(depth, depthExact, depthWLD));
                break;
            case FUNCTION_ZEBRA_WHITE:
            default:
                setBlackPlayerInfo(new PlayerInfo(0, 0, 0));
                setWhitePlayerInfo(new PlayerInfo(depth, depthExact, depthWLD));
                break;
        }
        setZebraPlayerInfo(new PlayerInfo(depth + 1, depthExact + 1, depthWLD + 1));
    }

    public void setAutoMakeMoves(boolean _settingAutoMakeForcedMoves) {
        if (_settingAutoMakeForcedMoves)
            zeSetAutoMakeMoves(1);
        else
            zeSetAutoMakeMoves(0);
    }

    public void setSlack(int _slack) {
        zeSetSlack(_slack);
    }

    public void setPerturbation(int _perturbation) {
        zeSetPerturbation(_perturbation);
    }

    public void setForcedOpening(String _openingName) {
        zeSetForcedOpening(_openingName);
    }

    public void setHumanOpenings(boolean _enable) {
        if (_enable)
            zeSetHumanOpenings(1);
        else
            zeSetHumanOpenings(0);
    }

    public void setPracticeMode(boolean _enable) {
        if (_enable)
            zeSetPracticeMode(1);
        else
            zeSetPracticeMode(0);
    }

    public void setUseBook(boolean _enable) {
        if (_enable)
            zeSetUseBook(1);
        else
            zeSetUseBook(0);
    }

    private void setZebraPlayerInfo(PlayerInfo playerInfo) {
        synchronized (playerInfoLock) {
            zebraPlayerInfo = playerInfo;
            mPlayerInfoChanged = true;
        }
    }

    private void setWhitePlayerInfo(PlayerInfo playerInfo) {
        synchronized (playerInfoLock) {
            whitePlayerInfo = playerInfo;
            mPlayerInfoChanged = true;
        }
    }

    private void setBlackPlayerInfo(PlayerInfo playerInfo) {
        synchronized (playerInfoLock) {
            blackPlayerInfo = playerInfo;
            mPlayerInfoChanged = true;
        }
    }

    public void setComputerMoveDelay(int delay) {
        computerMoveDelay = delay;
    }

    public void setInitialGameState(LinkedList<Move> moves) {
        byte[] bytes = toByte(moves);
        setInitialGameState(moves.size(), bytes);
    }

    // gamestate manipulators
    public void setInitialGameState(int moveCount, byte[] moves) {
        initialGameState = new GameState();
        initialGameState.setDisksPlayed(moveCount);
        initialGameState.setMoveSequence(new byte[moveCount]);
        for (int i = 0; i < moveCount; i++) {
            initialGameState.getMoveSequence()[i] = moves[i];
        }
    }

    public GameState getGameState() {
        return currentGameState;
    }

    // zebra thread
    @Override
    public void run() {
        setRunning(true);

        setEngineState(ES_INITIAL);

        // init data files
        if (!initFiles()) return;

        synchronized (mJNILock) {
            zeGlobalInit(mFilesDir.getAbsolutePath());
            zeSetPlayerInfo(PLAYER_BLACK, 0, 0, 0, INFINITE_TIME, 0);
            zeSetPlayerInfo(PLAYER_WHITE, 0, 0, 0, INFINITE_TIME, 0);
        }

        setEngineState(ES_READY2PLAY);

        while (isRunning) {
            waitForEngineState(ES_PLAY);

            if (!isRunning) break; // something may have happened while we were waiting

            setEngineState(ES_PLAY_IN_PROGRESS);

            synchronized (mJNILock) {
                setPlayerInfos();

                currentGameState = new GameState();
                currentGameState.setDisksPlayed(0);
                currentGameState.setMoveSequence(new byte[2 * BOARD_SIZE * BOARD_SIZE]);

                if (initialGameState != null)
                    zePlay(initialGameState.getDisksPlayed(), initialGameState.getMoveSequence());
                else
                    zePlay(0, null);

                initialGameState = null;
            }

            setEngineState(ES_READY2PLAY);
            //setEngineState(ES_PLAY);  // test
        }

        synchronized (mJNILock) {
            zeGlobalTerminate();
        }
    }

    private void setPlayerInfos() {
        zeSetPlayerInfo(
                PLAYER_BLACK,
                getBlackPlayerInfo().skill,
                getBlackPlayerInfo().exactSolvingSkill,
                getBlackPlayerInfo().wldSolvingSkill,
                INFINITE_TIME,
                0
        );
        zeSetPlayerInfo(
                PLAYER_WHITE,
                getWhitePlayerInfo().skill,
                getWhitePlayerInfo().exactSolvingSkill,
                getWhitePlayerInfo().wldSolvingSkill,
                INFINITE_TIME,
                0
        );
        zeSetPlayerInfo(
                PLAYER_ZEBRA,
                getZebraPlayerInfo().skill,
                getZebraPlayerInfo().exactSolvingSkill,
                getZebraPlayerInfo().wldSolvingSkill,
                INFINITE_TIME,
                0
        );
    }

    private PlayerInfo getZebraPlayerInfo() {
        return zebraPlayerInfo;
    }

    private PlayerInfo getWhitePlayerInfo() {
        return whitePlayerInfo;
    }

    private PlayerInfo getBlackPlayerInfo() {
        return blackPlayerInfo;
    }

    public void analyzeGame(List<Move> moves) {
        byte[] bytes = toByte(moves);
        zeAnalyzeGame(moves.size(), bytes);
    }


    private byte[] toByte(List<Move> moves) {
        byte[] moveBytes = new byte[moves.size()];
        for (int i = 0; i < moves.size(); i++) {
            moveBytes[i] = (byte) moves.get(i).mMove;
        }
        return moveBytes;
    }


    // called by native code
    //public void Error(String msg) throws EngineError
    //{
    //	throw new EngineError(msg);
    //}

    // called by native code - see droidzebra-msg.c
    private JSONObject Callback(int msgcode, JSONObject data) {
        JSONObject retval = null;
        // Log.d("ZebraEngine", String.format("Callback(%d,%s)", msgcode, data.toString()));
        if (bInCallback && msgcode != MSG_ERROR) {
            fatalError("Recursive callback call");
            new Exception().printStackTrace();
        }


        try {
            bInCallback = true;
            switch (msgcode) {
                case MSG_ERROR: {
                    if (getEngineState() == ES_INITIAL) {
                        // delete .bin files if initialization failed
                        // will be recreated from resources
                        new File(mFilesDir, PATTERNS_FILE).delete();
                        new File(mFilesDir, BOOK_FILE).delete();
                        new File(mFilesDir, BOOK_FILE_COMPRESSED).delete();
                    }
                    String error = data.getString("error");
                    mHandler.sendError(error);
                }
                break;

                case MSG_DEBUG: {
                    String debug = data.getString("message");
                    mHandler.sendDebug(debug);
                }
                break;

                case MSG_BOARD: {
                    int len;
                    JSONObject info;
                    JSONArray zeArray;
                    byte[] moves;

                    JSONArray zeboard = data.getJSONArray("board");
                    byte newBoard[] = new byte[BOARD_SIZE * BOARD_SIZE];
                    for (int i = 0; i < zeboard.length(); i++) {
                        JSONArray row = zeboard.getJSONArray(i);
                        for (int j = 0; j < row.length(); j++) {
                            newBoard[i * BOARD_SIZE + j] = (byte) row.getInt(j);
                        }
                    }

                    //update the current game state
                    GameState board = getGameState();
                    board.setBoard(newBoard);
                    board.setSideToMove(data.getInt("side_to_move"));
                    board.setDisksPlayed(data.getInt("disks_played"));

                    // black info
                    {
                        ZebraPlayerStatus black = new ZebraPlayerStatus();
                        info = data.getJSONObject("black");
                        black.setTime(info.getString("time"));
                        black.setEval((float) info.getDouble("eval"));
                        black.setDiscCount(info.getInt("disc_count"));

                        zeArray = info.getJSONArray("moves");
                        len = zeArray.length();
                        moves = new byte[len];
                        if (BuildConfig.DEBUG && !(2 * len <= currentGameState.getMoveSequence().length)) {
                            throw new AssertionError();
                        }
                        for (int i = 0; i < len; i++) {
                            moves[i] = (byte) zeArray.getInt(i);
                            currentGameState.getMoveSequence()[2 * i] = moves[i];
                        }
                        black.setMoves(moves);
                        board.setBlackPlayer(black);
                    }

                    // white info
                    {
                        ZebraPlayerStatus white = new ZebraPlayerStatus();
                        info = data.getJSONObject("white");
                        white.setTime(info.getString("time"));
                        white.setEval((float) info.getDouble("eval"));
                        white.setDiscCount(info.getInt("disc_count"));

                        zeArray = info.getJSONArray("moves");
                        len = zeArray.length();
                        moves = new byte[len];
                        if (BuildConfig.DEBUG && !(2 * len <= currentGameState.getMoveSequence().length)) {
                            throw new AssertionError();
                        }
                        for (int i = 0; i < len; i++) {
                            moves[i] = (byte) zeArray.getInt(i);
                            currentGameState.getMoveSequence()[2 * i + 1] = moves[i];
                        }
                        white.setMoves(moves);
                        board.setWhitePlayer(white);
                    }
                    mHandler.sendBoard(board);
                }
                break;

                case MSG_CANDIDATE_MOVES: {
                    JSONArray jscmoves = data.getJSONArray("moves");
                    CandidateMove cmoves[] = new CandidateMove[jscmoves.length()];
                    mValidMoves = new int[jscmoves.length()];
                    for (int i = 0; i < jscmoves.length(); i++) {
                        JSONObject jscmove = jscmoves.getJSONObject(i);
                        mValidMoves[i] = jscmoves.getJSONObject(i).getInt("move");
                        cmoves[i] = new CandidateMove(new Move(jscmove.getInt("move")));
                    }
                    getGameState().setCandidateMoves(cmoves);
                }
                break;

                case MSG_GET_USER_INPUT: {

                    setEngineState(ES_USER_INPUT_WAIT);

                    waitForEngineState(ES_PLAY);

                    while (mPendingEvent == null) {
                        setEngineState(ES_USER_INPUT_WAIT);
                        waitForEngineState(ES_PLAY);
                    }

                    retval = mPendingEvent;

                    setEngineState(ES_PLAY_IN_PROGRESS);

                    mValidMoves = null;
                    mPendingEvent = null;
                }
                break;

                case MSG_PASS: {
                    setEngineState(ES_USER_INPUT_WAIT);
                    mHandler.sendPass();
                    waitForEngineState(ES_PLAY);
                    setEngineState(ES_PLAY_IN_PROGRESS);
                }
                break;
                case MSG_ANALYZE_GAME: {
                    setEngineState(ES_USER_INPUT_WAIT);
                    //mHandler.sendMessage(msg);
                    waitForEngineState(ES_PLAY);
                    setEngineState(ES_PLAY_IN_PROGRESS);
                }
                break;

                case MSG_OPENING_NAME: {
                    getGameState().setOpening(data.getString("opening"));
                    mHandler.sendBoard(getGameState());
                }
                break;

                case MSG_LAST_MOVE: {
                    getGameState().setLastMove(data.getInt("move"));
                    mHandler.sendBoard(getGameState());
                }
                break;

                case MSG_NEXT_MOVE: {
                    getGameState().setNextMove(data.getInt("move"));
                    mHandler.sendBoard(getGameState());
                }
                break;

                case MSG_GAME_START: {
                    mHandler.sendGameStart();
                }
                break;

                case MSG_GAME_OVER: {
                    mHandler.sendGameOver();
                }
                break;

                case MSG_MOVE_START: {
                    mMoveStartTime = android.os.SystemClock.uptimeMillis();

                    mSideToMove = data.getInt("side_to_move");

                    // can change player info here
                    synchronized (playerInfoLock) {
                        if (mPlayerInfoChanged) {
                            setPlayerInfos();
                            mPlayerInfoChanged = false;
                        }
                    }

                    mHandler.sendMoveStart();
                }
                break;

                case MSG_MOVE_END: {
                    // introduce delay between moves made by the computer without user input
                    // so we can actually to see that the game is being played :)
                    if (computerMoveDelay > 0 && !isHumanToMove()) {
                        long moveEnd = android.os.SystemClock.uptimeMillis();
                        if ((moveEnd - mMoveStartTime) < computerMoveDelay) {
                            android.os.SystemClock.sleep(computerMoveDelay - (moveEnd - mMoveStartTime));
                        }
                    }
                    mHandler.sendMoveEnd();
                }
                break;

                case MSG_EVAL_TEXT: {
                    String eval = data.getString("eval");
                    mHandler.sendEval(eval);
                }
                break;

                case MSG_PV: {
                    JSONArray zeArray = data.getJSONArray("pv");
                    int len = zeArray.length();
                    byte[] moves = new byte[len];
                    for (int i = 0; i < len; i++)
                        moves[i] = (byte) zeArray.getInt(i);
                    mHandler.sendPv(moves);
                }
                break;

                case MSG_CANDIDATE_EVALS: {
                    JSONArray jscevals = data.getJSONArray("evals");
                    CandidateMove cmoves[] = new CandidateMove[jscevals.length()];
                    for (int i = 0; i < jscevals.length(); i++) {
                        JSONObject jsceval = jscevals.getJSONObject(i);
                        cmoves[i] = new CandidateMove(
                                new Move(jsceval.getInt("move")),
                                jsceval.getString("eval_s"),
                                jsceval.getString("eval_l"),
                                (jsceval.getInt("best") != 0)
                        );
                    }
                    getGameState().addCandidateMoveEvals(cmoves);
                    mHandler.sendBoard(getGameState());
                }
                break;

                default: {
                    mHandler.sendError(String.format(Locale.getDefault(), "Unknown message ID %d", msgcode));
                }
                break;
            }
        } catch (JSONException e) {
            mHandler.sendError("JSONException:" + e.getMessage());
        } finally {
            bInCallback = false;
        }
        return retval;
    }

    public boolean isThinking() {
        return getEngineState() == ZebraEngine.ES_PLAY_IN_PROGRESS;
    }

    public boolean isValidMove(Move move) {
        if (mValidMoves == null)
            return false;

        boolean valid = false;
        for (int m : mValidMoves)
            if (m == move.mMove) {
                valid = true;
                break;
            }
        return valid;
    }

    public boolean isHumanToMove() {
        return getSideToMovePlayerInfo().skill == 0;
    }

    private PlayerInfo getSideToMovePlayerInfo() {
        if (mSideToMove == PLAYER_BLACK) {
            return getBlackPlayerInfo();
        }
        if (mSideToMove == PLAYER_WHITE) {
            return getWhitePlayerInfo();
        }
        return getZebraPlayerInfo();
    }

    private void fatalError(String message) {
        try {
            JSONObject json = new JSONObject();
            json.put("error", message);
            Callback(MSG_ERROR, json);
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    // JNI
    private native void zeGlobalInit(String filesDir);

    private native void zeGlobalTerminate();

    private native void zeForceReturn();

    private native void zeForceExit();

    private native void zeSetPlayerInfo(
            int player,
            int skill,
            int exactSkill,
            int wldSkill,
            int time,
            int timeIncrement
    );

    private native void zePlay(int providedMoveCount, byte[] providedMoves);

    private native void zeSetAutoMakeMoves(int auto_make_moves);

    private native void zeSetSlack(int slack);

    private native void zeSetPerturbation(int perturbation);

    private native void zeSetForcedOpening(String opening_name);

    private native void zeSetHumanOpenings(int enable);

    private native void zeSetPracticeMode(int enable);

    private native void zeSetUseBook(int enable);

    private native boolean zeGameInProgress();

    private native void zeAnalyzeGame(int providedMoveCount, byte[] providedMoves);

    public native void zeJsonTest(JSONObject json);

    static {
        System.loadLibrary("droidzebra");
    }

    public void waitForReadyToPlay() {
        waitForEngineState(ZebraEngine.ES_READY2PLAY);
    }

    public boolean isReadyToPlay() {
        return getEngineState() == ZebraEngine.ES_READY2PLAY;
    }

    public void kill() {
        boolean retry = true;
        setRunning(false);
        interrupt(); // if waiting
        while (retry) {
            try {
                join();
                retry = false;
            } catch (InterruptedException e) {
                Log.wtf("wtf", e);
            }
        }
        removeHandler();
    }

    private static class EmptyHandler implements ZebraEngineMessageHandler {
        @Override
        public void sendError(String error) {

        }

        @Override
        public void sendDebug(String debug) {

        }

        @Override
        public void sendBoard(GameState board) {

        }

        @Override
        public void sendPass() {

        }

        @Override
        public void sendGameStart() {

        }

        @Override
        public void sendGameOver() {

        }

        @Override
        public void sendMoveStart() {

        }

        @Override
        public void sendMoveEnd() {

        }

        @Override
        public void sendEval(String eval) {

        }

        @Override
        public void sendPv(byte[] moves) {

        }
    }
}
