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

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int MP = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WC = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static final int REQ_OVERLAY      = 101;
    private static final int REQ_PROJECTION   = 102;
    private static final int REQ_STORAGE      = 103;
    private static final int REQ_PICK_MODULE  = 104;
    private static final int REQ_CAPTURE_AREA = 105;
    private static final int REQ_OVERLAY_POS  = 106;
    private static final int REQ_STORE        = 107;
    private static final int REQ_FILTERS      = 108;

    private enum CaptureState { IDLE, PROJECTING }
    private CaptureState mState = CaptureState.IDLE;

    private MediaProjectionManager mProjectionManager;
    private ModuleManager mModuleManager;

    private TextView mTvStatusCaption;
    private TextView mTvStatusFps;
    private TextView mTvStatusChain;
    private View     mStatusDot;
    private Button   mBtnCapture;
    private Button   mBtnArea;
    private Button   mBtnOverlay;
    private Button   mBtnFilters;

    private LinearLayout mParamsPanel;
    private TextView     mTvParamsTitle;
    private LinearLayout mParamsList;

    private boolean mFrameGenMode = false;
    private Rect mCaptureRect = null;
    private Rect mOverlayRect = null;

    private SurfaceView mSurfacePreview;
    private TextView    mTvPreviewHint;

    private HandlerThread mPreviewThread;
    private Handler       mPreviewHandler;
    private GlRenderer    mPreviewRenderer;
    private ShaderFilter  mPreviewShader;
    private volatile boolean mPreviewGlReady     = false;
    private volatile boolean mPreviewLoopRunning = false;

    private Bitmap mPreviewFrameA;
    private Bitmap mPreviewFrameB;

    private static final long PREVIEW_FRAME_INTERVAL_MS = 66;
    private final long mPreviewStartTimeMs = System.currentTimeMillis();

    private Handler mUiHandler;
    private final Runnable mUiUpdater = new Runnable() {
        @Override public void run() {
            updateStatusCard();
            if (mUiHandler != null) mUiHandler.postDelayed(this, 500);
        }
    };

    private final Runnable mPreviewLoop = new Runnable() {
        @Override public void run() {
            if (!mPreviewLoopRunning) return;
            if (mPreviewGlReady && mPreviewRenderer != null) {
                List<Module> previewChain = mModuleManager.getEnabledChain();
                Module active = previewChain.isEmpty() ? null : previewChain.get(0);
                Map<String, Float> params = null;
                ShaderFilter shader = mPreviewShader;
                float time = (System.currentTimeMillis() - mPreviewStartTimeMs) / 1000f;

                if (active != null && shader != null) {
                    params = new HashMap<String, Float>(active.getParams());
                    params.put("uTime", time);
                    if (mFrameGenMode) {
                        params.put("uFrameGen", 1.0f);
                        params.put("uMix", 0.5f + 0.5f * (float)Math.sin(time * 0.5f));
                    } else {
                        params.put("uFrameGen", 0.0f);
                    }
                }

                if (mFrameGenMode && shader != null
					&& mPreviewFrameA != null && mPreviewFrameB != null) {
                    mPreviewRenderer.uploadToGenSlot(0, mPreviewFrameA);
                    mPreviewRenderer.uploadToGenSlot(1, mPreviewFrameB);
                    mPreviewRenderer.drawGenBlendShader(
                        0, 1,
                        params != null && params.containsKey("uMix") ? params.get("uMix") : 0.5f,
                        shader, params);
                } else {
                    mPreviewRenderer.drawFrame(shader, params);
                }
            }
            mPreviewHandler.postDelayed(this, PREVIEW_FRAME_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
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

        mProjectionManager = (MediaProjectionManager)
            getSystemService(MEDIA_PROJECTION_SERVICE);
        mModuleManager = new ModuleManager(this);

        setContentView(buildRoot());

        wireListeners();
        setupPreview();
        setState(CaptureState.IDLE);
        rebuildParamsPanel();

        mUiHandler = new Handler();
        mUiHandler.post(mUiUpdater);
    }

    @Override protected void onResume() {
        super.onResume();
        mModuleManager.reload();
        updateStatusCard();
        rebuildParamsPanel();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (mUiHandler != null) mUiHandler.removeCallbacks(mUiUpdater);
        stopPreviewLoop();
        if (mPreviewHandler != null) {
            mPreviewHandler.post(new Runnable() {
					@Override public void run() {
						if (mPreviewShader   != null) { mPreviewShader.destroy();    mPreviewShader   = null; }
						if (mPreviewRenderer != null) { mPreviewRenderer.release();  mPreviewRenderer = null; }
						if (mPreviewFrameA   != null) { mPreviewFrameA.recycle();    mPreviewFrameA   = null; }
						if (mPreviewFrameB   != null) { mPreviewFrameB.recycle();    mPreviewFrameB   = null; }
					}
				});
        }
        if (mPreviewThread != null) mPreviewThread.quit();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
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

    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG_ROOT);

        int side = Ui.dp(this, 14);

        LinearLayout.LayoutParams headerLp = Ui.lp(MP, WC);
        headerLp.leftMargin   = side;
        headerLp.rightMargin  = side;
        headerLp.topMargin    = Ui.dp(this, 18);
        headerLp.bottomMargin = Ui.dp(this, 10);
        root.addView(buildHeader(), headerLp);

        LinearLayout.LayoutParams statusLp = Ui.lp(MP, WC);
        statusLp.leftMargin   = side;
        statusLp.rightMargin  = side;
        statusLp.bottomMargin = Ui.dp(this, 10);
        root.addView(buildStatusCard(), statusLp);

        LinearLayout.LayoutParams previewLp = Ui.lp(MP, 0, 1f);
        previewLp.leftMargin   = side;
        previewLp.rightMargin  = side;
        View previewCard = buildPreviewCard();
		previewCard.setMinimumHeight(Ui.dp(this, 160));
		root.addView(previewCard, previewLp);

        LinearLayout.LayoutParams paramsLp = Ui.lp(MP, Ui.dp(this, 200));
        paramsLp.leftMargin   = side;
        paramsLp.rightMargin  = side;
        paramsLp.topMargin    = Ui.dp(this, 8);
        root.addView(buildParamsPanel(), paramsLp);

        LinearLayout.LayoutParams captureLp = Ui.lp(MP, Ui.dp(this, 56));
        captureLp.leftMargin   = side;
        captureLp.rightMargin  = side;
        captureLp.topMargin    = Ui.dp(this, 8);
        root.addView(buildCaptureButton(), captureLp);

        LinearLayout.LayoutParams secLp = Ui.lp(MP, WC);
        secLp.leftMargin   = side;
        secLp.rightMargin  = side;
        secLp.topMargin    = Ui.dp(this, 6);
        root.addView(buildSecondaryRow(), secLp);

        LinearLayout.LayoutParams filtersLp = Ui.lp(MP, Ui.dp(this, 52));
        filtersLp.leftMargin   = side;
        filtersLp.rightMargin  = side;
        filtersLp.topMargin    = Ui.dp(this, 6);
        filtersLp.bottomMargin = Ui.dp(this, 14);
        root.addView(buildFiltersButton(), filtersLp);

        return root;
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = Ui.text(this, "Demeter", 26, Ui.TEXT_PRIMARY, true);
        row.addView(title, Ui.lp(0, WC, 1f));

        TextView addBtn = makeIconButton("+");
        addBtn.setId(android.R.id.button1);
        row.addView(addBtn, Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44)));

        return row;
    }

    private View buildStatusCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.roundRect(Ui.BG_SURFACE, this, 14));
        int p = Ui.dp(this, 14);
        card.setPadding(p, p, p, p);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        mStatusDot = new View(this);
        mStatusDot.setBackground(Ui.circle(Ui.TEXT_TERTIARY));
        LinearLayout.LayoutParams dotLp = Ui.lp(Ui.dp(this, 10), Ui.dp(this, 10));
        dotLp.rightMargin = Ui.dp(this, 10);
        row1.addView(mStatusDot, dotLp);

        mTvStatusCaption = Ui.text(this, "INACTIVO", 11, Ui.TEXT_SECOND, true);
        mTvStatusCaption.setLetterSpacing(0.18f);
        row1.addView(mTvStatusCaption, Ui.lp(0, WC, 1f));

        mTvStatusFps = Ui.text(this, "—", 22, Ui.ACCENT, true);
        row1.addView(mTvStatusFps);

        card.addView(row1);

        mTvStatusChain = Ui.text(this, "Ningún filtro activo", 12, Ui.TEXT_SECOND, false);
        mTvStatusChain.setLineSpacing(Ui.dp(this, 2), 1f);
        LinearLayout.LayoutParams chainLp = Ui.lp(MP, WC);
        chainLp.topMargin = Ui.dp(this, 6);
        card.addView(mTvStatusChain, chainLp);

        return card;
    }

    private View buildPreviewCard() {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(Ui.roundRectStroke(Ui.BG_SURFACE, Ui.DIVIDER, this, 14, 1f));
        card.setClipToOutline(true);

        mSurfacePreview = new SurfaceView(this);
        mSurfacePreview.getHolder().setFormat(android.graphics.PixelFormat.OPAQUE);
        card.addView(mSurfacePreview, new FrameLayout.LayoutParams(MP, MP));

        mTvPreviewHint = Ui.text(this, "Selecciona un filtro para previsualizar",
                                 13, Ui.TEXT_TERTIARY, false);
        mTvPreviewHint.setGravity(Gravity.CENTER);
        card.addView(mTvPreviewHint, new FrameLayout.LayoutParams(MP, MP));

        return card;
    }

    private View buildParamsPanel() {
        mParamsPanel = new LinearLayout(this);
        mParamsPanel.setOrientation(LinearLayout.VERTICAL);
        mParamsPanel.setBackground(Ui.roundRect(Ui.BG_SURFACE, this, 14));

        int p = Ui.dp(this, 12);
        mParamsPanel.setPadding(p, p, p, p);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        mTvParamsTitle = Ui.text(this, "Parámetros", 13, Ui.TEXT_TERTIARY, true);
        mTvParamsTitle.setLetterSpacing(0.10f);
        header.addView(mTvParamsTitle, Ui.lp(0, WC, 1f));

        mParamsPanel.addView(header);

        View div = new View(this);
        div.setBackgroundColor(Ui.DIVIDER);
        LinearLayout.LayoutParams divLp = Ui.lp(MP, Ui.dp(this, 1));
        divLp.topMargin    = Ui.dp(this, 8);
        divLp.bottomMargin = Ui.dp(this, 6);
        mParamsPanel.addView(div, divLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);

        mParamsList = new LinearLayout(this);
        mParamsList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(mParamsList, new FrameLayout.LayoutParams(MP, WC));

        mParamsPanel.addView(scroll, Ui.lp(MP, 0, 1f));

        return mParamsPanel;
    }

    private void rebuildParamsPanel() {
        if (mParamsList == null) return;
        mParamsList.removeAllViews();

        List<Module> paramsChain = mModuleManager.getEnabledChain();
        Module active = paramsChain.isEmpty() ? null : paramsChain.get(0);

        if (active == null) {
            mTvParamsTitle.setText("Parámetros");
            TextView empty = Ui.text(this, "Activa un filtro para ver sus parámetros.",
									 13, Ui.TEXT_TERTIARY, false);
            empty.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams emptyLp = Ui.lp(MP, Ui.dp(this, 80));
            mParamsList.addView(empty, emptyLp);
            return;
        }

        Map<String, Module.ParamDef> defs = active.getParamDefs();

        if (defs.isEmpty()) {
            mTvParamsTitle.setText(active.getName() + " — sin parámetros");
            TextView empty = Ui.text(this, "Este filtro no expone parámetros configurables.",
									 13, Ui.TEXT_TERTIARY, false);
            empty.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams emptyLp = Ui.lp(MP, Ui.dp(this, 80));
            mParamsList.addView(empty, emptyLp);
            return;
        }

        mTvParamsTitle.setText(active.getName());

        for (Map.Entry<String, Module.ParamDef> entry : defs.entrySet()) {
            mParamsList.addView(buildParamRow(active, entry.getKey(), entry.getValue()));
        }
    }

    private View buildParamRow(final Module module,
                               final String uniformName,
                               final Module.ParamDef def) {

        final float step = stepForRange(def.max - def.min);
        final Map<String, Float> params = module.getParams();
        final float initial = params.containsKey(uniformName)
			? params.get(uniformName) : def.defaultValue;
        final boolean decimal = (def.max - def.min) <= 20f;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowLp = Ui.lp(MP, WC);
        rowLp.bottomMargin = Ui.dp(this, 10);
        row.setLayoutParams(rowLp);

        TextView label = Ui.text(this, def.label, 13, Ui.TEXT_SECOND, true);
        LinearLayout.LayoutParams labelLp = Ui.lp(MP, WC);
        labelLp.bottomMargin = Ui.dp(this, 4);
        row.addView(label, labelLp);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);

        final TextView btnMinus = makeSmallStepBtn("−");
        controls.addView(btnMinus, Ui.lp(Ui.dp(this, 40), Ui.dp(this, 40)));

        final EditText etValue = new EditText(this);
        etValue.setInputType(InputType.TYPE_CLASS_NUMBER
							 | InputType.TYPE_NUMBER_FLAG_DECIMAL
							 | InputType.TYPE_NUMBER_FLAG_SIGNED);
        etValue.setText(formatValue(initial, decimal));
        etValue.setTextColor(Ui.TEXT_PRIMARY);
        etValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        etValue.setTypeface(Ui.medium());
        etValue.setGravity(Gravity.CENTER);
        etValue.setBackground(Ui.roundRectStroke(Ui.BG_ELEV, Ui.DIVIDER, this, 10, 1f));
        etValue.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        
        etValue.setBackgroundDrawable(
			Ui.roundRectStroke(Ui.BG_ELEV, Ui.DIVIDER, this, 10, 1f));

        LinearLayout.LayoutParams etLp = Ui.lp(0, Ui.dp(this, 40), 1f);
        etLp.leftMargin  = Ui.dp(this, 6);
        etLp.rightMargin = Ui.dp(this, 6);
        controls.addView(etValue, etLp);

        final TextView btnPlus = makeSmallStepBtn("+");
        controls.addView(btnPlus, Ui.lp(Ui.dp(this, 40), Ui.dp(this, 40)));

        row.addView(controls, Ui.lp(MP, WC));

        String rangeHint = formatValue(def.min, decimal) + "  ···  "
			+ "def: " + formatValue(def.defaultValue, decimal) + "  ···  "
			+ formatValue(def.max, decimal);
        TextView tvRange = Ui.text(this, rangeHint, 10, Ui.TEXT_TERTIARY, false);
        tvRange.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rangeLp = Ui.lp(MP, WC);
        rangeLp.topMargin = Ui.dp(this, 2);
        row.addView(tvRange, rangeLp);

        etValue.addTextChangedListener(new TextWatcher() {
				@Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
				@Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
				@Override public void afterTextChanged(Editable s) {
					String txt = s.toString().trim();
					if (txt.isEmpty() || txt.equals("-") || txt.equals(".")) return;
					try {
						float v = Float.parseFloat(txt);
						applyParam(module, uniformName, v);
					} catch (NumberFormatException ignored) {}
				}
			});

        btnMinus.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					float cur = currentValue(etValue, module, uniformName, def);
					float next = cur - step;
					etValue.setText(formatValue(next, decimal));
					etValue.setSelection(etValue.getText().length());
					applyParam(module, uniformName, next);
				}
			});

        btnPlus.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					float cur = currentValue(etValue, module, uniformName, def);
					float next = cur + step;
					etValue.setText(formatValue(next, decimal));
					etValue.setSelection(etValue.getText().length());
					applyParam(module, uniformName, next);
				}
			});

        View.OnLongClickListener resetListener = new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                etValue.setText(formatValue(def.defaultValue, decimal));
                etValue.setSelection(etValue.getText().length());
                applyParam(module, uniformName, def.defaultValue);
                Toast.makeText(MainActivity.this, def.label + " → default", Toast.LENGTH_SHORT).show();
                return true;
            }
        };
        btnMinus.setOnLongClickListener(resetListener);
        btnPlus.setOnLongClickListener(resetListener);

        return row;
    }

    private float stepForRange(float range) {
        if (range <= 2f)   return 0.01f;
        if (range <= 20f)  return 0.1f;
        if (range <= 200f) return 1f;
        return 5f;
    }

    private float currentValue(EditText et, Module module, String uniformName, Module.ParamDef def) {
        try {
            return Float.parseFloat(et.getText().toString().trim());
        } catch (NumberFormatException e) {
            Map<String, Float> p = module.getParams();
            return p.containsKey(uniformName) ? p.get(uniformName) : def.defaultValue;
        }
    }

    private void applyParam(final Module module, final String uniformName, final float value) {
        mModuleManager.setParamValue(module, uniformName, value);
        
    }

    private String formatValue(float v, boolean decimal) {
        if (decimal) return String.format("%.3f", v);
        
        if (v == (int) v) return String.valueOf((int) v);
        return String.format("%.1f", v);
    }

    private Button buildCaptureButton() {
        mBtnCapture = new Button(this);
        mBtnCapture.setText("Iniciar captura");
        mBtnCapture.setTextColor(0xFFFFFFFF);
        mBtnCapture.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        mBtnCapture.setTypeface(Ui.medium());
        mBtnCapture.setAllCaps(false);
        mBtnCapture.setStateListAnimator(null);
        mBtnCapture.setBackground(Ui.buttonBgSolid(this, Ui.ACCENT, Ui.ACCENT_DIM, 14));
        return mBtnCapture;
    }

    private View buildSecondaryRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        mBtnArea = new Button(this);
        mBtnArea.setText("Área");
        styleSecondary(mBtnArea);
        LinearLayout.LayoutParams lpA = Ui.lp(0, Ui.dp(this, 48), 1f);
        lpA.rightMargin = Ui.dp(this, 5);
        row.addView(mBtnArea, lpA);

        mBtnOverlay = new Button(this);
        mBtnOverlay.setText("Overlay");
        styleSecondary(mBtnOverlay);
        LinearLayout.LayoutParams lpB = Ui.lp(0, Ui.dp(this, 48), 1f);
        lpB.leftMargin = Ui.dp(this, 5);
        row.addView(mBtnOverlay, lpB);

        return row;
    }

    private Button buildFiltersButton() {
        mBtnFilters = new Button(this);
        mBtnFilters.setText("Filtros");
        mBtnFilters.setTextColor(Ui.TEXT_PRIMARY);
        mBtnFilters.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mBtnFilters.setTypeface(Ui.medium());
        mBtnFilters.setAllCaps(false);
        mBtnFilters.setStateListAnimator(null);
        mBtnFilters.setBackground(Ui.buttonBgStroke(
									  this, Ui.BG_ELEV, Ui.DIVIDER, Ui.DIVIDER, 14, 1f));
        return mBtnFilters;
    }

    private void styleSecondary(Button b) {
        b.setTextColor(Ui.TEXT_PRIMARY);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        b.setBackground(Ui.buttonBgStroke(
							this, Ui.BG_ELEV, Ui.DIVIDER, Ui.DIVIDER, 14, 1f));
    }

    private TextView makeIconButton(String glyph) {
        TextView tv = new TextView(this);
        tv.setText(glyph);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tv.setTextColor(Ui.TEXT_PRIMARY);
        tv.setGravity(Gravity.CENTER);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setBackground(Ui.buttonBgStroke(
							 this, Ui.BG_ELEV, Ui.DIVIDER, Ui.DIVIDER, 22, 1f));
        tv.setClickable(true);
        return tv;
    }

    private TextView makeSmallStepBtn(String glyph) {
        TextView tv = new TextView(this);
        tv.setText(glyph);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tv.setTextColor(Ui.TEXT_PRIMARY);
        tv.setGravity(Gravity.CENTER);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setBackground(Ui.buttonBgStroke(
							 this, Ui.BG_ELEV, Ui.ACCENT_SOFT, Ui.DIVIDER, 10, 1f));
        tv.setClickable(true);
        tv.setLongClickable(true);
        return tv;
    }

    private void wireListeners() {
        View importBtn = findViewById(android.R.id.button1);
        if (importBtn != null) {
            importBtn.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) { importModule(); }
				});
        }

        mBtnCapture.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { onCaptureBtnClicked(); }
			});
        mBtnCapture.setOnLongClickListener(new View.OnLongClickListener() {
				@Override public boolean onLongClick(View v) { toggleFrameGenMode(); return true; }
			});
        mBtnArea.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { openCaptureAreaSelector(); }
			});
        mBtnOverlay.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { openOverlayPositionSelector(); }
			});
        mBtnFilters.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					startActivityForResult(new Intent(MainActivity.this, FiltersActivity.class),
										   REQ_FILTERS);
				}
			});
    }

    private void updateStatusCard() {
        List<Module> chain = mModuleManager.getEnabledChain();

        if (mState == CaptureState.PROJECTING) {
            mStatusDot.setBackground(Ui.circle(Ui.SUCCESS));
            mTvStatusCaption.setText("CAPTURANDO");
        } else {
            mStatusDot.setBackground(Ui.circle(Ui.TEXT_TERTIARY));
            mTvStatusCaption.setText("INACTIVO");
        }

        if (mState == CaptureState.PROJECTING) {
            CaptureApi api = CaptureService.getCaptureApi();
            if (api != null) {
                float fps = api.getFps();
                mTvStatusFps.setText(fps < 0f ? "—" : String.format("%.0f", fps));
            } else {
                mTvStatusFps.setText("…");
            }
        } else {
            mTvStatusFps.setText("—");
        }

        if (chain.isEmpty()) {
            mTvStatusChain.setText("Ningún filtro activo");
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < chain.size(); i++) {
                if (i > 0) sb.append("  →  ");
                sb.append(chain.get(i).getName());
            }
            mTvStatusChain.setText(sb.toString());
        }

        mBtnFilters.setText(chain.isEmpty()
							? "Filtros"
							: "Filtros · " + chain.size() + (chain.size() == 1 ? " activo" : " activos"));
    }

    private void setState(CaptureState state) {
        mState = state;
        boolean capturing = (state == CaptureState.PROJECTING);
        mBtnCapture.setText(capturing ? "Detener captura" : "Iniciar captura");
        mBtnArea.setEnabled(!capturing);
        mBtnOverlay.setEnabled(!capturing);
        updateStatusCard();
    }

    private void setupPreview() {
        mPreviewFrameA = buildPreviewTestBitmap(0);
        mPreviewFrameB = buildPreviewTestBitmap(1);

        mPreviewThread = new HandlerThread("PreviewGlThread");
        mPreviewThread.start();
        mPreviewHandler = new Handler(mPreviewThread.getLooper());

        mSurfacePreview.getHolder().addCallback(new SurfaceHolder.Callback() {
				@Override public void surfaceCreated(final SurfaceHolder holder) {
					mPreviewHandler.post(new Runnable() {
							@Override public void run() {
								mPreviewRenderer = new GlRenderer();
								mPreviewGlReady  = mPreviewRenderer.init(holder);
								if (mPreviewGlReady) {
									mPreviewRenderer.uploadBitmap(buildPreviewTestBitmap(0));
									refreshPreviewShaderLocked();
									startPreviewLoop();
								}
							}
						});
				}
				@Override public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {}
				@Override public void surfaceDestroyed(SurfaceHolder holder) {
					stopPreviewLoop();
					mPreviewHandler.post(new Runnable() {
							@Override public void run() {
								if (mPreviewShader   != null) { mPreviewShader.destroy();   mPreviewShader   = null; }
								if (mPreviewRenderer != null) { mPreviewRenderer.release(); mPreviewRenderer = null; }
								mPreviewGlReady = false;
							}
						});
				}
			});
    }

    private void startPreviewLoop() {
        if (!mPreviewLoopRunning) {
            mPreviewLoopRunning = true;
            mPreviewHandler.post(mPreviewLoop);
        }
    }

    private void stopPreviewLoop() {
        mPreviewLoopRunning = false;
        if (mPreviewHandler != null) mPreviewHandler.removeCallbacks(mPreviewLoop);
    }

    private void refreshPreviewShaderLocked() {
        if (mPreviewShader != null) { mPreviewShader.destroy(); mPreviewShader = null; }
        List<Module> shaderChain = mModuleManager.getEnabledChain();
        final Module active = shaderChain.isEmpty() ? null : shaderChain.get(0);
        if (active != null
			&& active.getVertexShader() != null && active.getFragmentShader() != null) {
            try {
                mPreviewShader = new ShaderFilter(
                    active.getVertexShader(), active.getFragmentShader(), active.getParams());
            } catch (RuntimeException e) { mPreviewShader = null; }
        }
        runOnUiThread(new Runnable() {
				@Override public void run() {
					if (mTvPreviewHint != null) {
						mTvPreviewHint.setVisibility(
							(active != null && mPreviewShader != null) ? View.GONE : View.VISIBLE);
					}
				}
			});
    }

    private void refreshPreviewForActiveModuleChange() {
        if (mPreviewHandler != null) {
            mPreviewHandler.post(new Runnable() {
					@Override public void run() { refreshPreviewShaderLocked(); }
				});
        }
        runOnUiThread(new Runnable() {
				@Override public void run() { rebuildParamsPanel(); }
			});
    }

    private Bitmap buildPreviewTestBitmap(int variant) {
        int w = 480, h = 300;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        Paint bg = new Paint();
        bg.setShader(new LinearGradient(0, 0, w, h, 0xFF1A237E, 0xFF00695C, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, bg);

        Paint sh = new Paint();
        sh.setAntiAlias(true);

        float cx = (variant == 0) ? w * 0.28f : w * 0.72f;
        float cy = h * 0.35f;
        sh.setColor(0xFFFFFFFF);
        c.drawCircle(cx, cy, 55f, sh);

        sh.setColor(0xFFFFC107);
        c.drawRect(w * 0.55f, h * 0.15f, w * 0.85f, h * 0.55f, sh);

        sh.setColor(0xFFFF5252);
        Path tri = new Path();
        tri.moveTo(w * 0.65f, h * 0.90f);
        tri.lineTo(w * 0.85f, h * 0.90f);
        tri.lineTo(w * 0.75f, h * 0.65f);
        tri.close();
        c.drawPath(tri, sh);

        Paint lp = new Paint();
        lp.setColor(0x33FFFFFF);
        lp.setStrokeWidth(1f);
        for (int y = 0; y < h; y += 6) c.drawLine(0, y, w, y, lp);

        Paint tp = new Paint();
        tp.setAntiAlias(true);
        tp.setColor(0xFFFFFFFF);
        tp.setTextSize(28f);
        tp.setFakeBoldText(true);
        c.drawText("DEMETER", 16f, h - 20f, tp);
        return bmp;
    }

    private void openCaptureAreaSelector() {
        startActivityForResult(new Intent(this, CaptureAreaActivity.class), REQ_CAPTURE_AREA);
    }

    private void openOverlayPositionSelector() {
        startActivityForResult(new Intent(this, OverlayPositionActivity.class), REQ_OVERLAY_POS);
    }

    private void onCaptureBtnClicked() {
        if (mState == CaptureState.IDLE) startCaptureFlow();
        else stopCapture();
    }

    private void startCaptureFlow() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                  Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, REQ_OVERLAY);
        } else {
            requestProjection();
        }
    }

    private void requestProjection() {
        startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQ_PROJECTION);
    }

    private void launchCaptureService(int resultCode, Intent data) {
        CaptureService.setModuleManager(mModuleManager);
        Intent svc = new Intent(this, CaptureService.class);
        svc.putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode);
        svc.putExtra(CaptureService.EXTRA_RESULT_DATA, data);
        if (mCaptureRect != null) {
            svc.putExtra(CaptureService.EXTRA_CAPTURE_LEFT,   mCaptureRect.left);
            svc.putExtra(CaptureService.EXTRA_CAPTURE_TOP,    mCaptureRect.top);
            svc.putExtra(CaptureService.EXTRA_CAPTURE_RIGHT,  mCaptureRect.right);
            svc.putExtra(CaptureService.EXTRA_CAPTURE_BOTTOM, mCaptureRect.bottom);
        }
        if (mOverlayRect != null) {
            svc.putExtra(CaptureService.EXTRA_OVERLAY_LEFT,   mOverlayRect.left);
            svc.putExtra(CaptureService.EXTRA_OVERLAY_TOP,    mOverlayRect.top);
            svc.putExtra(CaptureService.EXTRA_OVERLAY_RIGHT,  mOverlayRect.right);
            svc.putExtra(CaptureService.EXTRA_OVERLAY_BOTTOM, mOverlayRect.bottom);
        }
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);
        setState(CaptureState.PROJECTING);
    }

    private void stopCapture() {
        stopService(new Intent(this, CaptureService.class));
        setState(CaptureState.IDLE);
        CaptureService.setModuleManager(null);
    }

    private void toggleFrameGenMode() {
        mFrameGenMode = !mFrameGenMode;
        CaptureApi api = CaptureService.getCaptureApi();
        if (api != null) api.setFrameGenMode(mFrameGenMode);
        Toast.makeText(this, "Frame-gen: " + (mFrameGenMode ? "ON" : "OFF"),
                       Toast.LENGTH_SHORT).show();
    }

    private void importModule() {
        new AlertDialog.Builder(this)
            .setTitle("Importar shader")
            .setItems(new CharSequence[]{"Archivo local", "Tienda"},
            new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    if (which == 0) importModuleLocal(); else openShaderStore();
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void importModuleLocal() {
        if (Build.VERSION.SDK_INT >= 23
			&& checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
			!= PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE);
        } else {
            openFilePicker();
        }
    }

    private void openShaderStore() {
        startActivityForResult(new Intent(this, ShaderStoreActivity.class), REQ_STORE);
    }

    private void openFilePicker() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(i, "Importar módulo"), REQ_PICK_MODULE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQ_OVERLAY:
                requestProjection();
                break;
            case REQ_PROJECTION:
                if (resultCode == RESULT_OK) launchCaptureService(resultCode, data);
                break;
            case REQ_PICK_MODULE:
                if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                    try {
                        mModuleManager.installFromUri(data.getData());
                        Toast.makeText(this, "Shader instalado", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
                break;
            case REQ_CAPTURE_AREA:
                if (resultCode == RESULT_OK && data != null) {
                    DisplayMetrics m = new DisplayMetrics();
                    getWindowManager().getDefaultDisplay().getRealMetrics(m);
                    int l = data.getIntExtra(CaptureAreaActivity.RESULT_LEFT,   0);
                    int t = data.getIntExtra(CaptureAreaActivity.RESULT_TOP,    0);
                    int r = data.getIntExtra(CaptureAreaActivity.RESULT_RIGHT,  m.widthPixels);
                    int b = data.getIntExtra(CaptureAreaActivity.RESULT_BOTTOM, m.heightPixels);
                    mCaptureRect = new Rect(l, t, r, b);
                    Toast.makeText(this, "Área: " + mCaptureRect.width() + "×" + mCaptureRect.height(),
                                   Toast.LENGTH_SHORT).show();
                }
                break;
            case REQ_OVERLAY_POS:
                if (resultCode == RESULT_OK && data != null) {
                    DisplayMetrics m = new DisplayMetrics();
                    getWindowManager().getDefaultDisplay().getRealMetrics(m);
                    int l = data.getIntExtra(OverlayPositionActivity.RESULT_LEFT,   0);
                    int t = data.getIntExtra(OverlayPositionActivity.RESULT_TOP,    0);
                    int r = data.getIntExtra(OverlayPositionActivity.RESULT_RIGHT,  m.widthPixels);
                    int b = data.getIntExtra(OverlayPositionActivity.RESULT_BOTTOM, m.heightPixels);
                    mOverlayRect = new Rect(l, t, r, b);
                    Toast.makeText(this, "Overlay: " + mOverlayRect.width() + "×" + mOverlayRect.height(),
                                   Toast.LENGTH_SHORT).show();
                }
                break;
            case REQ_STORE:
                if (resultCode == RESULT_OK) {
                    String installedName = data != null
                        ? data.getStringExtra(ShaderStoreActivity.RESULT_EXTRA_NAME) : null;
                    mModuleManager.reload();
                    Toast.makeText(this,
                                   installedName != null ? "Shader instalado: " + installedName
                                   : "Shader instalado",
                                   Toast.LENGTH_SHORT).show();
                    refreshPreviewForActiveModuleChange();
                }
                break;
            case REQ_FILTERS:
                refreshPreviewForActiveModuleChange();
                updateStatusCard();
                break;
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                     int[] grantResults) {
        if (requestCode == REQ_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                openFilePicker();
            else
                Toast.makeText(this, "Permiso denegado", Toast.LENGTH_LONG).show();
        }
    }
}
