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
package de.earthlingz.oerszebra.BoardView;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.FontMetrics;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.CountDownTimer;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import com.shurik.droidzebra.CandidateMove;
import com.shurik.droidzebra.InvalidMove;
import com.shurik.droidzebra.Move;
import de.earthlingz.oerszebra.R;
import java.util.concurrent.atomic.AtomicBoolean;

public class BoardView extends View implements BoardViewModel.BoardViewModelListener {

    private float lineWidth = 1;
    private float gridCirclesRadius = 3;

    private final BoardViewColors mColors = new BoardViewColors(this.getResources());

    private float mSizeX = 0;
    private float mSizeY = 0;
    private float mSizeCell = 0;
    private float mDiscRadius;
    private RectF mBoardRect = null;
    private Paint mPaint = null;
    private FontMetrics mFontMetrics = null;
    private Paint mPaintEvalText = null;
    private FontMetrics mEvalFontMetrics = null;

    private BitmapShader mShaderV = null;
    private BitmapShader mShaderH = null;
    private Path mPath = null;

    private Move mMoveSelection = new Move(0, 0);
    private boolean mShowSelection = false; // highlight selection rectangle
    private boolean mShowSelectionHelpers = false;// highlight row/column

    private CountDownTimer mAnimationTimer = null;
    private AtomicBoolean mIsAnimationRunning = new AtomicBoolean(false);
    private double mAnimationProgress = 0;
    private boolean displayAnimations = false;
    private int animationDuration = 500;
    private OnMakeMoveListener onMakeMoveListener = null;

    public void setBoardViewModel(BoardViewModel boardViewModel) {
        if (this.boardViewModel != null) {
            this.boardViewModel.removeBoardViewModeListener();
        }
        this.boardViewModel = boardViewModel;
        boardViewModel.setBoardViewModelListener(this);
    }

    private BoardViewModel boardViewModel;

    public void setDisplayEvals(boolean displayEvals) {
        this.displayEvals = displayEvals;
    }

    private boolean displayEvals = false;

    public void setDisplayLastMove(boolean displayLastMove) {
        this.displayLastMove = displayLastMove;
    }

    public void setDisplayMoves(boolean displayMoves) {
        this.displayMoves = displayMoves;
    }

    private boolean displayLastMove = false;
    private boolean displayMoves = false;

    public BoardView(Context context) {
        super(context);
        initBoardView();
    }

    public BoardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initBoardView();
    }

    private void initBoardView() {
        Resources r = getResources();
        setFocusable(true); // make sure we get key events


        Bitmap woodtrim = BitmapFactory.decodeResource(r, R.drawable.woodtrim);
        mShaderV = new BitmapShader(woodtrim, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        mShaderH = new BitmapShader(woodtrim, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        Matrix m = new Matrix();
        m.setRotate(90);
        mShaderH.setLocalMatrix(m);
        mPath = new Path();

        mPaint = new Paint();
        mPaintEvalText = new Paint();
        mBoardRect = new RectF();


        initCountDowntimer();
    }

    private void initCountDowntimer() {
        mAnimationProgress = 0;
        mAnimationTimer = new CountDownTimer(getAnimationDuration(), getAnimationDuration() / 10) {

            public void onTick(long millisUntilFinished) {
                mAnimationProgress = 1.0 - (double) millisUntilFinished / getAnimationDuration();
                invalidate();
            }

            public void onFinish() {
                mIsAnimationRunning.set(false);
                invalidate();
            }
        };
    }

    private int getAnimationDuration() {
        return animationDuration;
    }

    public RectF getCellRect(int bx, int by) {
        return new RectF(
                mBoardRect.left + bx * mSizeCell,
                mBoardRect.top + by * mSizeCell,
                mBoardRect.left + bx * mSizeCell + mSizeCell - 1,
                mBoardRect.top + by * mSizeCell + mSizeCell - 1
        );
    }

    public Move getMoveFromCoord(float x, float y) throws InvalidMove {
        int bx = (int) Math.floor((x - mBoardRect.left) / mSizeCell);
        int by = (int) Math.floor((y - mBoardRect.top) / mSizeCell);
        if (bx < 0 || bx >= boardViewModel.getBoardSize() || by < 0 || by >= boardViewModel.getBoardSize()) {
            throw new InvalidMove();
        }
        return new Move(bx, by);
    }

    @Override
    protected void onDraw(Canvas canvas) {

        // draw borders
        mPaint.setShader(mShaderV);

        mPath.reset();
        mPath.moveTo(0, 0);
        mPath.lineTo(0, mSizeY);
        mPath.lineTo(mBoardRect.left, mBoardRect.bottom);
        mPath.lineTo(mBoardRect.left, mBoardRect.top);
        mPath.lineTo(0, 0);
        canvas.drawPath(mPath, mPaint);

        mPath.reset();
        mPath.moveTo(mSizeX, 0);
        mPath.lineTo(mSizeX, mSizeY);
        mPath.lineTo(mBoardRect.right, mBoardRect.bottom);
        mPath.lineTo(mBoardRect.right, mBoardRect.top);
        mPath.lineTo(mSizeX, 0);
        canvas.drawPath(mPath, mPaint);

        mPaint.setShader(mShaderH);

        mPath.reset();
        mPath.moveTo(0, 0);
        mPath.lineTo(mSizeX, 0);
        mPath.lineTo(mBoardRect.right, mBoardRect.top);
        mPath.lineTo(mBoardRect.left, mBoardRect.top);
        mPath.lineTo(0, 0);
        canvas.drawPath(mPath, mPaint);

        mPath.reset();
        mPath.moveTo(0, mSizeY);
        mPath.lineTo(mSizeX, mSizeY);
        mPath.lineTo(mBoardRect.right, mBoardRect.bottom);
        mPath.lineTo(mBoardRect.left, mBoardRect.bottom);
        mPath.lineTo(0, mSizeY);
        canvas.drawPath(mPath, mPaint);

        mPaint.setShader(null);

        // draw the board
        mPaint.setStrokeWidth(lineWidth);
        int boardSize = boardViewModel !=null ? boardViewModel.getBoardSize():8;
        for (int i = 0; i <= boardSize; i++) {
            mPaint.setColor(mColors.Line);
            canvas.drawLine(mBoardRect.left + i * mSizeCell, mBoardRect.top, mBoardRect.left + i * mSizeCell, mBoardRect.top + mSizeCell * boardSize, mPaint);
            canvas.drawLine(mBoardRect.left, mBoardRect.top + i * mSizeCell, mBoardRect.left + mSizeCell * boardSize, mBoardRect.top + i * mSizeCell, mPaint);
        }
        canvas.drawCircle(mBoardRect.left + 2 * mSizeCell, mBoardRect.top + 2 * mSizeCell, gridCirclesRadius, mPaint);
        canvas.drawCircle(mBoardRect.left + 2 * mSizeCell, mBoardRect.top + 6 * mSizeCell, gridCirclesRadius, mPaint);
        canvas.drawCircle(mBoardRect.left + 6 * mSizeCell, mBoardRect.top + 2 * mSizeCell, gridCirclesRadius, mPaint);
        canvas.drawCircle(mBoardRect.left + 6 * mSizeCell, mBoardRect.top + 6 * mSizeCell, gridCirclesRadius, mPaint);

        // draw guides
        for (int i = 0; i < boardSize; i++) {
            mPaint.setTextSize(mSizeCell * 0.3f);
            mPaint.setColor(mColors.Line);
            canvas.drawText(String.valueOf(i + 1), mBoardRect.left / 2 + 1, mBoardRect.top + i * mSizeCell + mSizeCell / 2 - (mFontMetrics.ascent + mFontMetrics.descent) / 2 + 1, mPaint);
            canvas.drawText(Character.toString((char) ('A' + i)), mBoardRect.left + i * mSizeCell + mSizeCell / 2 + 1, mBoardRect.top / 2 - (mFontMetrics.ascent + mFontMetrics.descent) / 2 + 1, mPaint);
            mPaint.setColor(mColors.Numbers);
            canvas.drawText(String.valueOf(i + 1), mBoardRect.left / 2, mBoardRect.top + i * mSizeCell + mSizeCell / 2 - (mFontMetrics.ascent + mFontMetrics.descent) / 2, mPaint);
            canvas.drawText(Character.toString((char) ('A' + i)), mBoardRect.left + i * mSizeCell + mSizeCell / 2, mBoardRect.top / 2 - (mFontMetrics.ascent + mFontMetrics.descent) / 2, mPaint);
        }

        // draw helpers for move selector
        if (mMoveSelection != null) {
            Move selection = mMoveSelection;
            if (mShowSelectionHelpers) {
                if (this.boardViewModel.isValidMove(selection))
                    mPaint.setColor(mColors.HelpersValid);
                else
                    mPaint.setColor(mColors.HelpersInvalid);

                canvas.drawRect(
                        mBoardRect.left + selection.getX() * mSizeCell,
                        mBoardRect.top,
                        mBoardRect.left + (selection.getX() + 1) * mSizeCell,
                        mBoardRect.bottom,
                        mPaint
                );
                canvas.drawRect(
                        mBoardRect.left,
                        mBoardRect.top + selection.getY() * mSizeCell,
                        mBoardRect.right,
                        mBoardRect.top + (selection.getY() + 1) * mSizeCell,
                        mPaint
                );
            } else if (mShowSelection) {
                if (this.boardViewModel.isValidMove(selection))
                    mPaint.setColor(mColors.SelectionValid);
                else
                    mPaint.setColor(mColors.SelectionInvalid);

                canvas.drawRect(
                        mBoardRect.left + selection.getX() * mSizeCell,
                        mBoardRect.top + selection.getY() * mSizeCell,
                        mBoardRect.left + (selection.getX() + 1) * mSizeCell,
                        mBoardRect.top + (selection.getY() + 1) * mSizeCell,
                        mPaint
                );
            }
        }

        // draw next move marker
        if (shouldDisplayLastMove() && this.boardViewModel.getNextMove() != null) {
            Move nextMove = this.boardViewModel.getNextMove();
            mMoveSelection = nextMove;
            RectF cellRT = getCellRect(nextMove.getX(), nextMove.getY());
            mPaint.setColor(mColors.SelectionValid);

            canvas.drawRect(cellRT, mPaint);
        }
        if(boardViewModel == null) {
            return;
        }

        // draw moves
        drawDiscs(canvas, boardViewModel);

        // draw evals if in practive mode
        if ((shouldDisplayMoves() || shouldDisplayEvals())
                && this.boardViewModel.getCandidateMoves() != null) {
            mPaint.setStrokeWidth(lineWidth * 2);
            float lineLength = mSizeCell / 4;
            for (CandidateMove move : this.boardViewModel.getCandidateMoves()) {
                RectF cr = getCellRect(move.getX(), move.getY());
                if (move.hasEval && shouldDisplayEvals()) {
                    if (move.isBest)
                        mPaintEvalText.setColor(mColors.EvalsBest);
                    else
                        mPaintEvalText.setColor(mColors.Evals);
                    canvas.drawText(move.evalShort, cr.centerX(), cr.centerY() - (mEvalFontMetrics.ascent + mEvalFontMetrics.descent) / 2, mPaintEvalText);
                } else {
                    float[] pts =
                            {
                                    cr.centerX() - lineLength / 2,
                                    cr.centerY() - lineLength / 2,
                                    cr.centerX() + lineLength / 2,
                                    cr.centerY() + lineLength / 2,
                                    cr.centerX() + lineLength / 2,
                                    cr.centerY() - lineLength / 2,
                                    cr.centerX() - lineLength / 2,
                                    cr.centerY() + lineLength / 2,
                            };
                    mPaint.setColor(mColors.ValidMoveIndicator);
                    canvas.drawLines(pts, 0, 8, mPaint);
                }
            }
        }

        // draw last move marker
        if (shouldDisplayLastMove() && this.boardViewModel.getLastMove() != null) {
            Move lm = this.boardViewModel.getLastMove();
            RectF cellRT = getCellRect(lm.getX(), lm.getY());
            mPaint.setColor(mColors.LastMoveMarker);
            canvas.drawCircle(cellRT.left + mSizeCell / 10, cellRT.bottom - mSizeCell / 10, mSizeCell / 10, mPaint);
        }
    }

    private void drawDiscs(Canvas canvas, BoardViewModel board_view) {
        int boardSize = board_view.getBoardSize();
        Disc disc;

        for (int i = 0; i < boardSize; i++) {
            for (int j = 0; j < boardSize; j++) {
                disc = board_view.discAt(i, j);
                if (!disc.isEmpty()) {
                    drawDisc(canvas, disc);
                }
           }
        }
    }

    private void drawDisc(Canvas canvas, Disc disc) {
        // flip_angle: 0.0 == opponent side up, 1.0 == our side up
        double flip_angle = 1.0;
        if (disc.wasFlipped() && mIsAnimationRunning.get()) {
            flip_angle = mAnimationProgress;
        }
        PointF center = new PointF(
                mBoardRect.left + (disc.col() * mSizeCell) + (mSizeCell / 2),
                mBoardRect.top + (disc.row() * mSizeCell) + (mSizeCell / 2)
        );

        float oval_adjustment = (float) Math.abs(mDiscRadius * Math.cos(Math.PI * flip_angle));
        RectF oval_bounds = new RectF(
                center.x - oval_adjustment,
                center.y - mDiscRadius,
                center.x + oval_adjustment,
                center.y + mDiscRadius
        );

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(mColors.playerColor(disc.getPlayer()));

        // swap circle color if in animation is less than 50% done (flipping black->white and vice versa)
        if (flip_angle < 0.5) {
            paint.setColor(mColors.playerColor(disc.getOpponent()));
        }

        canvas.drawOval(oval_bounds, paint);
    }

    private boolean shouldDisplayEvals() {
        return displayEvals;
    }

    private boolean shouldDisplayLastMove() {
        return displayLastMove;
    }

    private boolean shouldDisplayMoves() {
        return displayMoves;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        //for the android designer
        int boardSize = boardViewModel != null?boardViewModel.getBoardSize():8;

        mSizeX = mSizeY = Math.min(getMeasuredWidth(), getMeasuredHeight());
        mSizeCell = Math.min(mSizeX / (boardSize + 1), mSizeY / (boardSize + 1));
        lineWidth = Math.max(1f, mSizeCell / 40f);
        mDiscRadius = (mSizeCell / 2) - (lineWidth * 2);

        gridCirclesRadius = Math.max(3f, mSizeCell / 13f);
        mBoardRect.set(
                mSizeX - mSizeCell / 2 - mSizeCell * boardSize,
                mSizeY - mSizeCell / 2 - mSizeCell * boardSize,
                mSizeX - mSizeCell / 2,
                mSizeY - mSizeCell / 2
        );

        mPaint.reset();
        mPaint.setStyle(Paint.Style.FILL);
        Typeface font = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
        mPaint.setTypeface(font);
        mPaint.setAntiAlias(true);
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setTextScaleX(1.0f);
        mPaint.setTextSize(mSizeCell * 0.3f);
        mPaint.setStrokeWidth(lineWidth);
        mFontMetrics = mPaint.getFontMetrics();

        mPaintEvalText.reset();
        mPaintEvalText.setStyle(Paint.Style.FILL);
        font = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
        mPaintEvalText.setTypeface(font);
        mPaintEvalText.setAntiAlias(true);
        mPaintEvalText.setTextAlign(Paint.Align.CENTER);
        mPaintEvalText.setTextScaleX(1.0f);
        mPaintEvalText.setTextSize(mSizeCell * 0.5f);
        mPaintEvalText.setStrokeWidth(lineWidth);
        mEvalFontMetrics = mPaintEvalText.getFontMetrics();

        setMeasuredDimension((int) mSizeX, (int) mSizeY);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent msg) {

        int newMX = mMoveSelection.getX();
        int newMY = mMoveSelection.getY();

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                newMX--;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                newMX++;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                newMY--;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                newMY++;
                break;
        }

        boolean bMakeMove = (
                keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                        || keyCode == KeyEvent.KEYCODE_SPACE);

        updateSelection(newMX, newMY, bMakeMove, true);

        return false;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        int action = event.getAction();
        int bx = (int) Math.floor((event.getX() - mBoardRect.left) / mSizeCell);
        int by = (int) Math.floor((event.getY() - mBoardRect.top) / mSizeCell);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                mShowSelectionHelpers = true;
                updateSelection(bx, by, false, true);
                break;
            case MotionEvent.ACTION_UP:
                //super.performClick(); makes the gui slow. Why?
                mShowSelectionHelpers = false;
                updateSelection(bx, by, true, true);
                break;
        }
        return true;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        float tx = event.getX();
        float ty = event.getY();

        // Log.d("BoardView", String.format("trackball event: %d %f %f", event.getAction(), tx, ty));

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                updateSelection(mMoveSelection.getX(), mMoveSelection.getY(), true, true);
            }
            break;

            case MotionEvent.ACTION_MOVE: {
                int newMX = mMoveSelection.getX();
                int newMY = mMoveSelection.getY();
                if (Math.abs(tx) > Math.abs(ty)) {
                    if (tx > 0)
                        newMX++;
                    else
                        newMX--;
                } else {
                    if (ty > 0)
                        newMY++;
                    else
                        newMY--;
                }
                updateSelection(newMX, newMY, false, true);
            }
            break;

            default:
                return false;
        }

        return true;
    }

    private void updateSelection(int bX, int bY, boolean bMakeMove, boolean bShowSelection) {
        boolean bInvalidate = false;

        if (mMoveSelection == null) {
            mMoveSelection = new Move(bX, bY);
        }

        if (bX < 0 || bX >= boardViewModel.getBoardSize())
            bX = mMoveSelection.getX();

        if (bY < 0 || bY >= boardViewModel.getBoardSize())
            bY = mMoveSelection.getY();

        if (mShowSelection != bShowSelection) {
            mShowSelection = bShowSelection;
            bInvalidate = true;
        }

        if (bX != mMoveSelection.getX() || bY != mMoveSelection.getY()) {
            mMoveSelection = new Move(bX, bY);
            bInvalidate = true;
        }


        if (bMakeMove) {
            bInvalidate = true;
            mShowSelectionHelpers = false;
            cancelAnimation();
            if (this.onMakeMoveListener != null) {
                this.onMakeMoveListener.onMakeMove(mMoveSelection);
            }

        }

        if (bInvalidate) {
            invalidate();
        }
    }

    private void cancelAnimation() {
        if (mIsAnimationRunning.get()) {
            mAnimationTimer.cancel();
            mIsAnimationRunning.set(false);
            mAnimationProgress = 0;
        }
    }

    @Override
    public void onCandidateMovesChanged() {
       postInvalidate();
    }

    @Override
    public void onBoardSizeChanged() {
       postInvalidate();
    }

    @Override
    public void onNextMoveChanged() {
       postInvalidate();
    }

    @Override
    public void onLastMoveChanged() {
       postInvalidate();
    }

    @Override
    public void onBoardStateChanged() {
        mMoveSelection = null;
        if (shouldDisplayAnimations()) {
            if (mIsAnimationRunning.get())
                mAnimationTimer.cancel();
            mIsAnimationRunning.set(true);
            mAnimationProgress = 0;
            mAnimationTimer.start();
            //will call invalidate from the animation threads
        } else {
            postInvalidate();
        }
    }

    private boolean shouldDisplayAnimations() {
        return this.displayAnimations;
    }

    public void setDisplayAnimations(boolean displayAnimations) {
        this.displayAnimations = displayAnimations;
        this.invalidate();
    }

    public void setAnimationDuration(int animationDuration) {
        this.animationDuration = animationDuration;
        cancelAnimation();
        initCountDowntimer();
        this.invalidate();

    }

    public void setOnMakeMoveListener(OnMakeMoveListener listener) {
        this.onMakeMoveListener = listener;
    }

    public interface OnMakeMoveListener {
        void onMakeMove(Move move);
    }
}
