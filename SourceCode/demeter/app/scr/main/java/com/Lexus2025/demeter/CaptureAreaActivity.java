// =============================================================================
// Demeter Reshader
// Copyright (C) 2025  LexusYTG
//
// This file is part of Demeter Reshader.
//
// Demeter Reshader is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
// ...
// =============================================================================

package com.Lexus2025.demeter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Actividad de selección del área de captura.
 *
 * FIX: La Activity se despliega en modo TRULY fullscreen (incluyendo
 * status bar y navigation bar) para que las coordenadas de la View
 * sean exactamente iguales a las coordenadas de pantalla real que
 * espera CaptureService. De lo contrario el Rect devuelto queda
 * desplazado tantos píxeles como ocupa la status/nav bar.
 *
 * También agrega el botón "Pantalla completa" y un modo adaptativo
 * que vincula automáticamente overlay con capture area.
 */
public class CaptureAreaActivity extends Activity {

    public static final String RESULT_LEFT   = CaptureService.EXTRA_CAPTURE_LEFT;
    public static final String RESULT_TOP    = CaptureService.EXTRA_CAPTURE_TOP;
    public static final String RESULT_RIGHT  = CaptureService.EXTRA_CAPTURE_RIGHT;
    public static final String RESULT_BOTTOM = CaptureService.EXTRA_CAPTURE_BOTTOM;

    private static final int MIN_SIZE_DP = 48;

    private int mScreenW, mScreenH, mMinPx;
    private SelectionView mSelView;
    private TextView mTvInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // FIX: ocultar TODA la UI del sistema (status bar + nav bar)
        // para que getWidth()/getHeight() de la View == pantalla real.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // Ocultar navigation bar (inmersivo)
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        // Dimensiones reales (incluyendo barras del sistema)
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        mScreenW = metrics.widthPixels;
        mScreenH = metrics.heightPixels;
        mMinPx   = (int) (MIN_SIZE_DP * metrics.density);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xCC000000);

        mSelView = new SelectionView(this);
        root.addView(mSelView, new FrameLayout.LayoutParams(
						 FrameLayout.LayoutParams.MATCH_PARENT,
						 FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setBackgroundColor(0xDD000000);
        panel.setPadding(16, 12, 16, 12);

        mTvInfo = new TextView(this);
        mTvInfo.setTextColor(Color.WHITE);
        mTvInfo.setTextSize(13f);
        mTvInfo.setText("Arrastra para seleccionar el área a capturar");
        LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        panel.addView(mTvInfo, tvParams);

        Button btnFull = new Button(this);
        btnFull.setText("Completa");
        btnFull.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					mSelView.setSelection(new Rect(0, 0, mScreenW, mScreenH));
				}
			});
        panel.addView(btnFull);

        Button btnReset = new Button(this);
        btnReset.setText("Reset");
        btnReset.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { mSelView.reset(); }
			});
        panel.addView(btnReset);

        Button btnOk = new Button(this);
        btnOk.setText("Confirmar");
        btnOk.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { confirmSelection(); }
			});
        panel.addView(btnOk);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT);
        panelParams.gravity = Gravity.BOTTOM;
        root.addView(panel, panelParams);

        setContentView(root);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Reafirmar modo inmersivo cuando la ventana recupera el foco
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private void confirmSelection() {
        Rect sel = mSelView.getSelection();
        if (sel == null || sel.width() < mMinPx || sel.height() < mMinPx) {
            sel = new Rect(0, 0, mScreenW, mScreenH);
        }
        Intent result = new Intent();
        result.putExtra(RESULT_LEFT,   sel.left);
        result.putExtra(RESULT_TOP,    sel.top);
        result.putExtra(RESULT_RIGHT,  sel.right);
        result.putExtra(RESULT_BOTTOM, sel.bottom);
        setResult(RESULT_OK, result);
        finish();
    }

    // ─── Vista de selección ───────────────────────────────────────────────────

    private class SelectionView extends View {

        private final Paint mDimPaint;
        private final Paint mBorderPaint;
        private final Paint mHandlePaint;
        private final Paint mLabelPaint;

        private Rect mSel = null;

        private static final int NONE      = 0;
        private static final int CREATE    = 1;
        private static final int MOVE      = 2;
        private static final int RESIZE_TL = 3, RESIZE_TR = 4;
        private static final int RESIZE_BL = 5, RESIZE_BR = 6;
        private static final int RESIZE_T  = 7, RESIZE_B  = 8;
        private static final int RESIZE_L  = 9, RESIZE_R  = 10;

        private int   mMode = NONE;
        private float mLastX, mLastY, mStartX, mStartY;
        private final int mHandleR;
        private final int mEdgeHit;

        SelectionView(Context ctx) {
            super(ctx);
            float d = getResources().getDisplayMetrics().density;
            mHandleR = (int)(22 * d);
            mEdgeHit = (int)(18 * d);

            mDimPaint = new Paint();
            mDimPaint.setColor(0x88000000);
            mDimPaint.setStyle(Paint.Style.FILL);

            mBorderPaint = new Paint();
            mBorderPaint.setColor(0xFFFFFFFF);
            mBorderPaint.setStyle(Paint.Style.STROKE);
            mBorderPaint.setStrokeWidth(2f * d);
            mBorderPaint.setAntiAlias(true);

            mHandlePaint = new Paint();
            mHandlePaint.setColor(0xFF4FC3F7);
            mHandlePaint.setStyle(Paint.Style.FILL);
            mHandlePaint.setAntiAlias(true);

            mLabelPaint = new Paint();
            mLabelPaint.setColor(Color.WHITE);
            mLabelPaint.setTextSize(13f * d);
            mLabelPaint.setAntiAlias(true);
        }

        void reset() {
            mSel = null;
            mMode = NONE;
            updateInfo();
            invalidate();
        }

        void setSelection(Rect r) {
            mSel = new Rect(r);
            updateInfo();
            invalidate();
        }

        Rect getSelection() { return mSel; }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX(), y = e.getY();
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (mSel != null) {
                        int hit = detectMode(x, y);
                        if (hit != NONE && hit != MOVE) {
                            mMode = hit;
                        } else if (hit == MOVE) {
                            mMode = MOVE;
                        } else {
                            mMode = CREATE;
                            mStartX = x; mStartY = y;
                            mSel = new Rect((int)x, (int)y, (int)x, (int)y);
                        }
                    } else {
                        mMode = CREATE;
                        mStartX = x; mStartY = y;
                        mSel = new Rect((int)x, (int)y, (int)x, (int)y);
                    }
                    mLastX = x; mLastY = y;
                    break;

                case MotionEvent.ACTION_MOVE:
                    int dx = (int)(x - mLastX), dy = (int)(y - mLastY);
                    if (mMode == CREATE) {
                        mSel = new Rect(
                            (int)Math.min(mStartX, x), (int)Math.min(mStartY, y),
                            (int)Math.max(mStartX, x), (int)Math.max(mStartY, y));
                    } else {
                        applyMode(dx, dy);
                    }
                    mLastX = x; mLastY = y;
                    updateInfo();
                    invalidate();
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (mSel != null && (mSel.width() < mMinPx || mSel.height() < mMinPx))
                        mSel = null;
                    mMode = NONE;
                    updateInfo();
                    invalidate();
                    break;
            }
            return true;
        }

        private int detectMode(float x, float y) {
            if (mSel == null) return NONE;
            if (near(x, y, mSel.left,  mSel.top))    return RESIZE_TL;
            if (near(x, y, mSel.right, mSel.top))    return RESIZE_TR;
            if (near(x, y, mSel.left,  mSel.bottom)) return RESIZE_BL;
            if (near(x, y, mSel.right, mSel.bottom)) return RESIZE_BR;
            if (Math.abs(x - mSel.left)   < mEdgeHit && inVRange(y)) return RESIZE_L;
            if (Math.abs(x - mSel.right)  < mEdgeHit && inVRange(y)) return RESIZE_R;
            if (Math.abs(y - mSel.top)    < mEdgeHit && inHRange(x)) return RESIZE_T;
            if (Math.abs(y - mSel.bottom) < mEdgeHit && inHRange(x)) return RESIZE_B;
            if (mSel.contains((int)x, (int)y)) return MOVE;
            return NONE;
        }

        private boolean near(float x, float y, int px, int py) {
            return Math.abs(x - px) < mHandleR && Math.abs(y - py) < mHandleR;
        }
        private boolean inVRange(float y) { return y > mSel.top  && y < mSel.bottom; }
        private boolean inHRange(float x) { return x > mSel.left && x < mSel.right; }

        private void applyMode(int dx, int dy) {
            if (mSel == null) return;
            switch (mMode) {
                case MOVE:      mSel.offset(dx, dy); clamp(); break;
                case RESIZE_TL: mSel.left += dx; mSel.top    += dy; enforceMin(); clamp(); break;
                case RESIZE_TR: mSel.right+= dx; mSel.top    += dy; enforceMin(); clamp(); break;
                case RESIZE_BL: mSel.left += dx; mSel.bottom += dy; enforceMin(); clamp(); break;
                case RESIZE_BR: mSel.right+= dx; mSel.bottom += dy; enforceMin(); clamp(); break;
                case RESIZE_L:  mSel.left   += dx; enforceMin(); clamp(); break;
                case RESIZE_R:  mSel.right  += dx; enforceMin(); clamp(); break;
                case RESIZE_T:  mSel.top    += dy; enforceMin(); clamp(); break;
                case RESIZE_B:  mSel.bottom += dy; enforceMin(); clamp(); break;
            }
        }

        private void enforceMin() {
            if (mSel.width()  < mMinPx) mSel.right  = mSel.left + mMinPx;
            if (mSel.height() < mMinPx) mSel.bottom = mSel.top  + mMinPx;
        }

        private void clamp() {
            // FIX: clamping contra dimensiones REALES (mScreenW/H), no getWidth/Height().
            // getWidth/Height() puede ser menor si hay insets de sistema visibles.
            int w = mSel.width(), h = mSel.height();
            if (mSel.left < 0)             { mSel.left = 0;       mSel.right  = w; }
            if (mSel.top  < 0)             { mSel.top  = 0;       mSel.bottom = h; }
            if (mSel.right  > mScreenW)    { mSel.right  = mScreenW; mSel.left = mSel.right  - w; }
            if (mSel.bottom > mScreenH)    { mSel.bottom = mScreenH; mSel.top  = mSel.bottom - h; }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (mSel == null || mSel.isEmpty()) {
                canvas.drawRect(0, 0, getWidth(), getHeight(), mDimPaint);
                return;
            }

            canvas.drawRect(0, 0, getWidth(), mSel.top, mDimPaint);
            canvas.drawRect(0, mSel.bottom, getWidth(), getHeight(), mDimPaint);
            canvas.drawRect(0, mSel.top, mSel.left, mSel.bottom, mDimPaint);
            canvas.drawRect(mSel.right, mSel.top, getWidth(), mSel.bottom, mDimPaint);

            canvas.drawRect(mSel, mBorderPaint);

            canvas.drawCircle(mSel.left,  mSel.top,    mHandleR, mHandlePaint);
            canvas.drawCircle(mSel.right, mSel.top,    mHandleR, mHandlePaint);
            canvas.drawCircle(mSel.left,  mSel.bottom, mHandleR, mHandlePaint);
            canvas.drawCircle(mSel.right, mSel.bottom, mHandleR, mHandlePaint);

            // Regla de tercios como guía
            Paint grid = new Paint();
            grid.setColor(0x334FC3F7);
            grid.setStyle(Paint.Style.STROKE);
            grid.setStrokeWidth(1f);
            int tw = mSel.width() / 3, th = mSel.height() / 3;
            canvas.drawLine(mSel.left + tw,   mSel.top, mSel.left + tw,   mSel.bottom, grid);
            canvas.drawLine(mSel.left + tw*2, mSel.top, mSel.left + tw*2, mSel.bottom, grid);
            canvas.drawLine(mSel.left, mSel.top + th,   mSel.right, mSel.top + th,   grid);
            canvas.drawLine(mSel.left, mSel.top + th*2, mSel.right, mSel.top + th*2, grid);

            float lx = mSel.left + 8;
            float ly = mSel.top  > 30 ? mSel.top - 8 : mSel.top + 28;
            canvas.drawText(mSel.left + "," + mSel.top
							+ "  " + mSel.width() + "×" + mSel.height(), lx, ly, mLabelPaint);
        }

        private void updateInfo() {
            if (mTvInfo == null) return;
            if (mSel == null || mSel.isEmpty()) {
                mTvInfo.setText("Arrastra para seleccionar el área a capturar");
            } else {
                mTvInfo.setText(mSel.left + "," + mSel.top
								+ " → " + mSel.right + "," + mSel.bottom
								+ "  (" + mSel.width() + "×" + mSel.height() + ")");
            }
        }
    }
}
