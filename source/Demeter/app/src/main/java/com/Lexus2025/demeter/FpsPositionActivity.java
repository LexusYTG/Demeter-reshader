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
// =============================================================================

package com.Lexus2025.demeter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

public class FpsPositionActivity extends Activity {

    public static final String PREF_POS_X = "fps_pos_x";
    public static final String PREF_POS_Y = "fps_pos_y";

    public static final String RESULT_X = "fps_pos_x";
    public static final String RESULT_Y = "fps_pos_y";

    private static final int MARGIN_DP = 8;

    private int mScreenW, mScreenH, mMarginPx, mOvW, mOvH;
    private PinView mPinView;
    private TextView mTvInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
        mScreenW  = metrics.widthPixels;
        mScreenH  = metrics.heightPixels;
        float d   = metrics.density;
        mMarginPx = (int)(MARGIN_DP * d + 0.5f);
        mOvW      = (int)(CaptureService.FPS_OVERLAY_WIDTH_DP  * d + 0.5f);
        mOvH      = (int)(CaptureService.FPS_OVERLAY_HEIGHT_DP * d + 0.5f);

        SharedPreferences prefs = getSharedPreferences(
            FiltersActivity.PREFS_NAME, MODE_PRIVATE);
        int savedX = prefs.getInt(PREF_POS_X, -1);
        int savedY = prefs.getInt(PREF_POS_Y, -1);

        int defaultX = mScreenW - mOvW - mMarginPx;
        int defaultY = mMarginPx;

        int startX = (savedX >= 0) ? savedX : defaultX;
        int startY = (savedY >= 0) ? savedY : defaultY;

        startX = clamp(startX, 0, mScreenW - mOvW);
        startY = clamp(startY, 0, mScreenH - mOvH);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x00000000);

        mPinView = new PinView(this, startX, startY);
        root.addView(mPinView, new FrameLayout.LayoutParams(
                         FrameLayout.LayoutParams.MATCH_PARENT,
                         FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setBackgroundColor(0xDD000000);
        panel.setPadding(16, 12, 16, 12);

        mTvInfo = new TextView(this);
        mTvInfo.setTextColor(Color.WHITE);
        mTvInfo.setTextSize(12f);
        mTvInfo.setText("Arrastra el pin para posicionar el overlay de FPS");
        LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        panel.addView(mTvInfo, tvParams);

        Button btnDefault = new Button(this);
        btnDefault.setText("Default");
        btnDefault.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    mPinView.setPosition(mScreenW - mOvW - mMarginPx, mMarginPx);
                }
            });
        panel.addView(btnDefault);

        Button btnCancel = new Button(this);
        btnCancel.setText("Cancelar");
        btnCancel.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    setResult(RESULT_CANCELED);
                    finish();
                }
            });
        panel.addView(btnCancel);

        Button btnOk = new Button(this);
        btnOk.setText("OK");
        btnOk.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { confirm(); }
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

    private void confirm() {
        Rect r = mPinView.getRect();
        getSharedPreferences(FiltersActivity.PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putInt(PREF_POS_X, r.left)
            .putInt(PREF_POS_Y, r.top)
            .commit();

        Intent result = new Intent();
        result.putExtra(RESULT_X, r.left);
        result.putExtra(RESULT_Y, r.top);
        setResult(RESULT_OK, result);
        finish();
    }

    private static int clamp(int v, int lo, int hi) {
        if (hi < lo) hi = lo;
        return Math.max(lo, Math.min(v, hi));
    }

    private class PinView extends View {

        private final Paint mDimPaint;
        private final Paint mFillPaint;
        private final Paint mBorderPaint;
        private final Paint mCrosshairPaint;
        private final Paint mLabelPaint;

        private final Rect mRect;

        private boolean mDragging = false;
        private float mOffsetX, mOffsetY;

        PinView(Context ctx, int x, int y) {
            super(ctx);
            float d = getResources().getDisplayMetrics().density;

            mRect = new Rect(x, y, x + mOvW, y + mOvH);

            mDimPaint = new Paint();
            mDimPaint.setColor(0x99000000);
            mDimPaint.setStyle(Paint.Style.FILL);

            mFillPaint = new Paint();
            mFillPaint.setColor(0x554FC3F7);
            mFillPaint.setStyle(Paint.Style.FILL);

            mBorderPaint = new Paint();
            mBorderPaint.setColor(0xFF4FC3F7);
            mBorderPaint.setStyle(Paint.Style.STROKE);
            mBorderPaint.setStrokeWidth(2.5f * d);
            mBorderPaint.setAntiAlias(true);

            mCrosshairPaint = new Paint();
            mCrosshairPaint.setColor(0xFFFFFFFF);
            mCrosshairPaint.setStyle(Paint.Style.STROKE);
            mCrosshairPaint.setStrokeWidth(1.2f * d);
            mCrosshairPaint.setAntiAlias(true);

            mLabelPaint = new Paint();
            mLabelPaint.setColor(Color.WHITE);
            mLabelPaint.setTextSize(13f * d);
            mLabelPaint.setAntiAlias(true);
            mLabelPaint.setFakeBoldText(true);
        }

        void setPosition(int x, int y) {
            x = clamp(x, 0, mScreenW - mOvW);
            y = clamp(y, 0, mScreenH - mOvH);
            mRect.set(x, y, x + mOvW, y + mOvH);
            updateInfo();
            invalidate();
        }

        Rect getRect() { return mRect; }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX(), y = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:

                    if (mRect.contains((int) x, (int) y)) {
                        mDragging = true;
                        mOffsetX  = x - mRect.left;
                        mOffsetY  = y - mRect.top;
                    }
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (mDragging) {
                        int nx = (int)(x - mOffsetX);
                        int ny = (int)(y - mOffsetY);
                        nx = clamp(nx, 0, mScreenW - mOvW);
                        ny = clamp(ny, 0, mScreenH - mOvH);
                        mRect.set(nx, ny, nx + mOvW, ny + mOvH);
                        updateInfo();
                        invalidate();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mDragging = false;
                    return true;
            }
            return false;
        }

        @Override
        protected void onDraw(Canvas canvas) {

            canvas.drawRect(0, 0, getWidth(), getHeight(), mDimPaint);

            canvas.drawRect(mRect, mFillPaint);
            canvas.drawRect(mRect, mBorderPaint);

            canvas.drawLine(mRect.centerX(), mRect.top,
                            mRect.centerX(), mRect.bottom, mCrosshairPaint);
            canvas.drawLine(mRect.left, mRect.centerY(),
                            mRect.right, mRect.centerY(), mCrosshairPaint);

            float ly = (mRect.top > 26) ? mRect.top - 8 : mRect.bottom + 22;
            canvas.drawText("FPS", mRect.left + 4, ly, mLabelPaint);
        }

        private void updateInfo() {
            if (mTvInfo == null) return;
            mTvInfo.setText("x:" + mRect.left + "  y:" + mRect.top
                            + "   " + mOvW + "×" + mOvH + " px");
        }
    }
}
