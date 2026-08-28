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
 * Actividad de posicionamiento del overlay.
 *
 * FIX 1: Modo inmersivo sticky para que getWidth/Height de la View
 * sean iguales a las dimensiones reales de pantalla. Sin esto, las
 * coordenadas devueltas difieren en statusBarHeight + navBarHeight
 * respecto a lo que espera WindowManager al posicionar el overlay.
 *
 * FIX 2: clamp() usa mScreenW/H en lugar de getWidth/Height() por
 * la misma razón.
 *
 * NUEVO - Modo adaptativo: si el Intent trae EXTRA_CAPTURE_* (el
 * rectángulo de captura ya elegido), se muestra como referencia
 * semitransparente y el botón "Adaptar a captura" copia ese rect
 * como posición del overlay → captura y render 1:1 sin distorsión.
 */
public class OverlayPositionActivity extends Activity {

    public static final String RESULT_LEFT   = CaptureService.EXTRA_OVERLAY_LEFT;
    public static final String RESULT_TOP    = CaptureService.EXTRA_OVERLAY_TOP;
    public static final String RESULT_RIGHT  = CaptureService.EXTRA_OVERLAY_RIGHT;
    public static final String RESULT_BOTTOM = CaptureService.EXTRA_OVERLAY_BOTTOM;

    /** Extras opcionales: rect de captura para mostrarlo como referencia. */
    public static final String EXTRA_CAP_LEFT   = CaptureService.EXTRA_CAPTURE_LEFT;
    public static final String EXTRA_CAP_TOP    = CaptureService.EXTRA_CAPTURE_TOP;
    public static final String EXTRA_CAP_RIGHT  = CaptureService.EXTRA_CAPTURE_RIGHT;
    public static final String EXTRA_CAP_BOTTOM = CaptureService.EXTRA_CAPTURE_BOTTOM;

    private static final int MIN_SIZE_DP = 48;

    private int mScreenW, mScreenH, mMinPx;
    private OverlayDragView mDragView;
    private TextView mTvInfo;

    /** Rect de captura (referencia visual, puede ser null). */
    private Rect mCaptureRef = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // FIX: modo inmersivo completo para coordenadas == pantalla real
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        mScreenW = metrics.widthPixels;
        mScreenH = metrics.heightPixels;
        mMinPx   = (int) (MIN_SIZE_DP * metrics.density);

        // Leer rect de captura de referencia (si viene del Intent)
        Intent in = getIntent();
        if (in != null && in.hasExtra(EXTRA_CAP_LEFT)) {
            mCaptureRef = new Rect(
                in.getIntExtra(EXTRA_CAP_LEFT,   0),
                in.getIntExtra(EXTRA_CAP_TOP,    0),
                in.getIntExtra(EXTRA_CAP_RIGHT,  mScreenW),
                in.getIntExtra(EXTRA_CAP_BOTTOM, mScreenH));
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xCC000000);

        mDragView = new OverlayDragView(this);
        root.addView(mDragView, new FrameLayout.LayoutParams(
						 FrameLayout.LayoutParams.MATCH_PARENT,
						 FrameLayout.LayoutParams.MATCH_PARENT));

        // Panel inferior
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setBackgroundColor(0xDD000000);
        panel.setPadding(16, 12, 16, 12);

        mTvInfo = new TextView(this);
        mTvInfo.setTextColor(Color.WHITE);
        mTvInfo.setTextSize(12f);
        mTvInfo.setText("Arrastra el overlay · esquinas para redimensionar");
        LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        panel.addView(mTvInfo, tvParams);

        // Botón modo adaptativo: iguala overlay al área de captura (1:1, sin distorsión)
        Button btnAdapt = new Button(this);
        btnAdapt.setText(mCaptureRef != null ? "= Captura" : "Completa");
        btnAdapt.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					if (mCaptureRef != null) {
						// Modo adaptativo: overlay == captura → render 1:1
						mDragView.setRect(new Rect(mCaptureRef));
					} else {
						mDragView.setRect(new Rect(0, 0, mScreenW, mScreenH));
					}
				}
			});
        panel.addView(btnAdapt);

        Button btnFull = new Button(this);
        btnFull.setText("Completa");
        btnFull.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					mDragView.setRect(new Rect(0, 0, mScreenW, mScreenH));
				}
			});
        panel.addView(btnFull);

        Button btnOk = new Button(this);
        btnOk.setText("OK");
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
        Rect sel = mDragView.getRect();
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

    // ─── Vista de posicionamiento del overlay ─────────────────────────────────

    private class OverlayDragView extends View {

        private final Paint mBgPaint;
        private final Paint mBorderPaint;
        private final Paint mHandlePaint;
        private final Paint mLabelPaint;
        private final Paint mGridPaint;
        private final Paint mCapRefPaint;  // Para dibujar el rect de captura como referencia

        private Rect mRect;

        private static final int NONE      = 0;
        private static final int MOVE      = 1;
        private static final int RESIZE_TL = 2, RESIZE_TR = 3;
        private static final int RESIZE_BL = 4, RESIZE_BR = 5;
        private static final int RESIZE_T  = 6, RESIZE_B  = 7;
        private static final int RESIZE_L  = 8, RESIZE_R  = 9;

        private int   mMode = NONE;
        private float mLastX, mLastY;
        private final int mHandleR;
        private final int mEdgeHit;

        OverlayDragView(Context ctx) {
            super(ctx);
            float d = getResources().getDisplayMetrics().density;
            mHandleR = (int)(22 * d);
            mEdgeHit = (int)(18 * d);

            mBgPaint = new Paint();
            mBgPaint.setColor(0x334FC3F7);
            mBgPaint.setStyle(Paint.Style.FILL);

            mBorderPaint = new Paint();
            mBorderPaint.setColor(0xFF4FC3F7);
            mBorderPaint.setStyle(Paint.Style.STROKE);
            mBorderPaint.setStrokeWidth(2.5f * d);
            mBorderPaint.setAntiAlias(true);

            mHandlePaint = new Paint();
            mHandlePaint.setColor(0xFF81D4FA);
            mHandlePaint.setStyle(Paint.Style.FILL);
            mHandlePaint.setAntiAlias(true);

            mLabelPaint = new Paint();
            mLabelPaint.setColor(Color.WHITE);
            mLabelPaint.setTextSize(13f * d);
            mLabelPaint.setAntiAlias(true);

            mGridPaint = new Paint();
            mGridPaint.setColor(0x334FC3F7);
            mGridPaint.setStyle(Paint.Style.STROKE);
            mGridPaint.setStrokeWidth(1f * d);

            // Rect de captura de referencia: naranja semitransparente
            mCapRefPaint = new Paint();
            mCapRefPaint.setColor(0x55FF8800);
            mCapRefPaint.setStyle(Paint.Style.STROKE);
            mCapRefPaint.setStrokeWidth(3f * d);
            mCapRefPaint.setPathEffect(new android.graphics.DashPathEffect(
										   new float[]{12f * d, 8f * d}, 0));
            mCapRefPaint.setAntiAlias(true);
        }

        void setRect(Rect r) {
            mRect = new Rect(r);
            updateInfo();
            invalidate();
        }

        Rect getRect() { return mRect; }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            if (mRect == null) {
                // Inicial: mitad de pantalla centrado
                // FIX: si hay captureRef, partir de ese rect
                if (mCaptureRef != null) {
                    mRect = new Rect(mCaptureRef);
                } else {
                    int pw = w / 2, ph = h / 2;
                    mRect = new Rect((w - pw) / 2, (h - ph) / 2,
                                     (w + pw) / 2, (h + ph) / 2);
                }
                updateInfo();
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX(), y = e.getY();
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mMode = detectMode(x, y);
                    mLastX = x; mLastY = y;
                    break;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int)(x - mLastX), dy = (int)(y - mLastY);
                    applyMode(dx, dy);
                    mLastX = x; mLastY = y;
                    updateInfo();
                    invalidate();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mMode = NONE;
                    break;
            }
            return true;
        }

        private int detectMode(float x, float y) {
            if (mRect == null) return NONE;
            if (near(x, y, mRect.left,  mRect.top))    return RESIZE_TL;
            if (near(x, y, mRect.right, mRect.top))    return RESIZE_TR;
            if (near(x, y, mRect.left,  mRect.bottom)) return RESIZE_BL;
            if (near(x, y, mRect.right, mRect.bottom)) return RESIZE_BR;
            if (Math.abs(x - mRect.left)   < mEdgeHit && inVRange(y)) return RESIZE_L;
            if (Math.abs(x - mRect.right)  < mEdgeHit && inVRange(y)) return RESIZE_R;
            if (Math.abs(y - mRect.top)    < mEdgeHit && inHRange(x)) return RESIZE_T;
            if (Math.abs(y - mRect.bottom) < mEdgeHit && inHRange(x)) return RESIZE_B;
            if (mRect.contains((int)x, (int)y)) return MOVE;
            return NONE;
        }

        private boolean near(float x, float y, int px, int py) {
            return Math.abs(x - px) < mHandleR && Math.abs(y - py) < mHandleR;
        }
        private boolean inVRange(float y) { return y > mRect.top  && y < mRect.bottom; }
        private boolean inHRange(float x) { return x > mRect.left && x < mRect.right; }

        private void applyMode(int dx, int dy) {
            if (mRect == null) return;
            switch (mMode) {
                case MOVE:      mRect.offset(dx, dy); clamp(); break;
                case RESIZE_TL: mRect.left += dx; mRect.top    += dy; enforceMin(); clamp(); break;
                case RESIZE_TR: mRect.right+= dx; mRect.top    += dy; enforceMin(); clamp(); break;
                case RESIZE_BL: mRect.left += dx; mRect.bottom += dy; enforceMin(); clamp(); break;
                case RESIZE_BR: mRect.right+= dx; mRect.bottom += dy; enforceMin(); clamp(); break;
                case RESIZE_L:  mRect.left   += dx; enforceMin(); clamp(); break;
                case RESIZE_R:  mRect.right  += dx; enforceMin(); clamp(); break;
                case RESIZE_T:  mRect.top    += dy; enforceMin(); clamp(); break;
                case RESIZE_B:  mRect.bottom += dy; enforceMin(); clamp(); break;
            }
        }

        private void enforceMin() {
            if (mRect.width()  < mMinPx) mRect.right  = mRect.left + mMinPx;
            if (mRect.height() < mMinPx) mRect.bottom = mRect.top  + mMinPx;
        }

        private void clamp() {
            // FIX: clamping contra mScreenW/H (coordenadas reales) NO getWidth/Height
            int w = mRect.width(), h = mRect.height();
            if (mRect.left < 0)          { mRect.left  = 0;        mRect.right  = w; }
            if (mRect.top  < 0)          { mRect.top   = 0;        mRect.bottom = h; }
            if (mRect.right  > mScreenW) { mRect.right  = mScreenW; mRect.left  = mRect.right  - w; }
            if (mRect.bottom > mScreenH) { mRect.bottom = mScreenH; mRect.top   = mRect.bottom - h; }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Paint dim = new Paint();
            dim.setColor(0x99000000);
            dim.setStyle(Paint.Style.FILL);

            if (mRect == null || mRect.isEmpty()) {
                canvas.drawRect(0, 0, getWidth(), getHeight(), dim);
                return;
            }

            // Fondo atenuado fuera del overlay
            canvas.drawRect(0, 0, getWidth(), mRect.top, dim);
            canvas.drawRect(0, mRect.bottom, getWidth(), getHeight(), dim);
            canvas.drawRect(0, mRect.top, mRect.left, mRect.bottom, dim);
            canvas.drawRect(mRect.right, mRect.top, getWidth(), mRect.bottom, dim);

            // Dibujar rect de captura como referencia (naranja punteado)
            if (mCaptureRef != null) {
                canvas.drawRect(mCaptureRef, mCapRefPaint);
                Paint capLabel = new Paint(mLabelPaint);
                capLabel.setColor(0xFFFF8800);
                capLabel.setTextSize(capLabel.getTextSize() * 0.85f);
                canvas.drawText("área de captura",
								mCaptureRef.left + 8,
								mCaptureRef.top  > 20 ? mCaptureRef.top - 6 : mCaptureRef.top + 20,
								capLabel);
            }

            // Interior del overlay
            canvas.drawRect(mRect, mBgPaint);

            // Tercios
            int tw = mRect.width() / 3, th = mRect.height() / 3;
            canvas.drawLine(mRect.left + tw,   mRect.top, mRect.left + tw,   mRect.bottom, mGridPaint);
            canvas.drawLine(mRect.left + tw*2, mRect.top, mRect.left + tw*2, mRect.bottom, mGridPaint);
            canvas.drawLine(mRect.left, mRect.top + th,   mRect.right, mRect.top + th,   mGridPaint);
            canvas.drawLine(mRect.left, mRect.top + th*2, mRect.right, mRect.top + th*2, mGridPaint);

            // Borde y handles
            canvas.drawRect(mRect, mBorderPaint);
            canvas.drawCircle(mRect.left,  mRect.top,    mHandleR, mHandlePaint);
            canvas.drawCircle(mRect.right, mRect.top,    mHandleR, mHandlePaint);
            canvas.drawCircle(mRect.left,  mRect.bottom, mHandleR, mHandlePaint);
            canvas.drawCircle(mRect.right, mRect.bottom, mHandleR, mHandlePaint);

            // Indicador de aspect ratio vs captura
            String label = mRect.left + "," + mRect.top
                + "  " + mRect.width() + "×" + mRect.height();
            if (mCaptureRef != null) {
                boolean sameAspect = Math.abs(
                    (float)mRect.width()  / mRect.height() -
                    (float)mCaptureRef.width() / mCaptureRef.height()) < 0.02f;
                label += sameAspect ? "  ✓1:1" : "  ⚠ distorsión";
            }
            float ly = mRect.top > 28 ? mRect.top - 10 : mRect.bottom + 24;
            canvas.drawText(label, mRect.left + 8, ly, mLabelPaint);
        }

        private void updateInfo() {
            if (mTvInfo == null || mRect == null) return;
            String info = "x:" + mRect.left + " y:" + mRect.top
                + "  " + mRect.width() + "×" + mRect.height() + " px";
            if (mCaptureRef != null) {
                float scaleX = (float) mRect.width()  / mCaptureRef.width();
                float scaleY = (float) mRect.height() / mCaptureRef.height();
                info += String.format("  escala %.2fx%.2f", scaleX, scaleY);
            }
            mTvInfo.setText(info);
        }
    }
}
