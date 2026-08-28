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
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int REQ_OVERLAY        = 101;
    private static final int REQ_PROJECTION     = 102;
    private static final int REQ_STORAGE        = 103;
    private static final int REQ_PICK_MODULE    = 104;
    private static final int REQ_CAPTURE_AREA   = 105; // Nueva: selección de área de captura
    private static final int REQ_OVERLAY_POS    = 106; // Nueva: posición del overlay
    private static final int REQ_STORE           = 107; // Tienda de shaders

    private enum CaptureState { IDLE, PROJECTING }
    private CaptureState mState = CaptureState.IDLE;

    private MediaProjectionManager mProjectionManager;
    private ModuleManager mModuleManager;

    private TextView    mTvStatus;
    private Button      mBtnCapture;
    private Button      mBtnSelect;
    private Button      mBtnCaptureArea;
    private Button      mBtnOverlayPos;
    private Button      mBtnParams;
    private ImageButton mBtnImport;

    private boolean mTestMode = false;

    // Rectángulos guardados entre Activities
    private Rect mCaptureRect = null;  // null = pantalla completa
    private Rect mOverlayRect = null;  // null = pantalla completa

    // ─── Preview del shader activo (área vacía del medio) ──────────────────
    //
    // Usa SU PROPIO GlRenderer/contexto EGL, independiente del que arma
    // CaptureService/TargetRenderer al capturar. Por eso el ShaderFilter acá
    // se compila directo desde el código fuente del módulo (mPreviewShader),
    // en vez de reusar module.getShaderFilter(): ese getter cachea el
    // programa GL compilado en el contexto de la ÚLTIMA vez que se llamó, y
    // los objetos GL no son válidos fuera del contexto EGL donde se crearon
    // (mismo motivo que el FIX Bug A documentado en Module.java). Si el
    // preview reusara ese cache, podría dejar a la captura real con un
    // shader inválido de otro contexto → pantalla negra.
    private SurfaceView mSurfacePreview;
    private TextView    mTvPreviewHint;

    private HandlerThread mPreviewThread;
    private Handler        mPreviewHandler;
    private GlRenderer     mPreviewRenderer;
    private ShaderFilter   mPreviewShader;
    private volatile boolean mPreviewGlReady    = false;
    private volatile boolean mPreviewLoopRunning = false;

    private static final long PREVIEW_FRAME_INTERVAL_MS = 66; // ~15 fps, alcanza para un preview
    private final long mPreviewStartTimeMs = System.currentTimeMillis();

    private final Runnable mPreviewLoop = new Runnable() {
        @Override
        public void run() {
            if (!mPreviewLoopRunning) return;
            if (mPreviewGlReady && mPreviewRenderer != null) {
                Module active = mModuleManager.getActiveModule();
                Map<String, Float> params = null;
                if (active != null && active.isEnabled() && mPreviewShader != null) {
                    params = new HashMap<String, Float>(active.getParams());
                    params.put("uTime", (System.currentTimeMillis() - mPreviewStartTimeMs) / 1000f);
                }
                mPreviewRenderer.drawFrame(mPreviewShader, params);
            }
            mPreviewHandler.postDelayed(this, PREVIEW_FRAME_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // FIX Bug 1: pantalla completa inmersiva en la Activity principal.
        // Sin esto la app muestra status bar + nav bar.
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

        setContentView(R.layout.main);

        mProjectionManager = (MediaProjectionManager)
            getSystemService(MEDIA_PROJECTION_SERVICE);
        mModuleManager = new ModuleManager(this);

        mTvStatus       = (TextView)    findViewById(R.id.tv_status);
        mBtnCapture     = (Button)      findViewById(R.id.btn_capture);
        mBtnSelect      = (Button)      findViewById(R.id.btn_select);
        mBtnImport      = (ImageButton) findViewById(R.id.btn_import);
        mBtnCaptureArea = (Button)      findViewById(R.id.btn_capture_area);
        mBtnOverlayPos  = (Button)      findViewById(R.id.btn_overlay_pos);
        mBtnParams      = (Button)      findViewById(R.id.btn_params);

        mBtnCapture.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { onCaptureBtnClicked(); }
			});
        mBtnSelect.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { showSelectionMenu(); }
			});
        mBtnImport.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { importModule(); }
			});
        mBtnCaptureArea.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { openCaptureAreaSelector(); }
			});
        mBtnOverlayPos.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { openOverlayPositionSelector(); }
			});
        mBtnParams.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { showParamsDialog(); }
			});

        setState(CaptureState.IDLE);
        setupPreview();
    }

    // ─── Preview del shader activo ────────────────────────────────────

    private void setupPreview() {
        mSurfacePreview = (SurfaceView) findViewById(R.id.surface_preview);
        mTvPreviewHint  = (TextView)    findViewById(R.id.tv_preview_hint);

        mPreviewThread = new HandlerThread("PreviewGlThread");
        mPreviewThread.start();
        mPreviewHandler = new Handler(mPreviewThread.getLooper());

        mSurfacePreview.getHolder().addCallback(new SurfaceHolder.Callback() {
				@Override
				public void surfaceCreated(final SurfaceHolder holder) {
					mPreviewHandler.post(new Runnable() {
							@Override
							public void run() {
								mPreviewRenderer = new GlRenderer();
								mPreviewGlReady  = mPreviewRenderer.init(holder);
								if (mPreviewGlReady) {
									mPreviewRenderer.uploadBitmap(buildPreviewTestBitmap());
									refreshPreviewShaderLocked();
									startPreviewLoop();
								}
							}
						});
				}

				@Override
				public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) { }

				@Override
				public void surfaceDestroyed(SurfaceHolder holder) {
					stopPreviewLoop();
					mPreviewHandler.post(new Runnable() {
							@Override
							public void run() {
								if (mPreviewShader != null) {
									mPreviewShader.destroy();
									mPreviewShader = null;
								}
								if (mPreviewRenderer != null) {
									mPreviewRenderer.release();
									mPreviewRenderer = null;
								}
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

    /**
     * Recompila el ShaderFilter del preview a partir del módulo activo.
     * DEBE llamarse en mPreviewHandler (hilo GL del preview).
     * Se dispara al crear la surface y cada vez que cambia el módulo activo.
     */
    private void refreshPreviewShaderLocked() {
        if (mPreviewShader != null) {
            mPreviewShader.destroy();
            mPreviewShader = null;
        }
        final Module active = mModuleManager.getActiveModule();
        if (active != null && active.isEnabled()
            && active.getVertexShader() != null && active.getFragmentShader() != null) {
            try {
                mPreviewShader = new ShaderFilter(
                    active.getVertexShader(), active.getFragmentShader(), active.getParams());
            } catch (RuntimeException e) {
                mPreviewShader = null; // el error de compilación ya se avisa al elegir el módulo
            }
        }
        runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if (mTvPreviewHint != null) {
						mTvPreviewHint.setVisibility(
							(active != null && mPreviewShader != null) ? View.GONE : View.VISIBLE);
					}
				}
			});
    }

    /** Llamar en el hilo de UI cada vez que cambia el módulo activo. */
    private void refreshPreviewForActiveModuleChange() {
        if (mPreviewHandler != null) {
            mPreviewHandler.post(new Runnable() {
					@Override public void run() { refreshPreviewShaderLocked(); }
				});
        }
    }

    /**
     * Imagen de prueba con degradé, formas de bordes definidos y líneas finas:
     * sirve tanto para shaders de suavizado/nitidez (bordes) como para el CRT
     * (las líneas horizontales muestran bien el efecto de scanlines).
     */
    private Bitmap buildPreviewTestBitmap() {
        int w = 480, h = 300;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        Paint bgPaint = new Paint();
        bgPaint.setShader(new LinearGradient(
							  0, 0, w, h, 0xFF1A237E, 0xFF00695C, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, bgPaint);

        Paint shapePaint = new Paint();
        shapePaint.setAntiAlias(true);

        shapePaint.setColor(0xFFFFFFFF);
        c.drawCircle(w * 0.28f, h * 0.35f, 55f, shapePaint);

        shapePaint.setColor(0xFFFFC107);
        c.drawRect(w * 0.55f, h * 0.15f, w * 0.85f, h * 0.55f, shapePaint);

        shapePaint.setColor(0xFFFF5252);
        Path tri = new Path();
        tri.moveTo(w * 0.65f, h * 0.90f);
        tri.lineTo(w * 0.85f, h * 0.90f);
        tri.lineTo(w * 0.75f, h * 0.65f);
        tri.close();
        c.drawPath(tri, shapePaint);

        Paint linePaint = new Paint();
        linePaint.setColor(0x33FFFFFF);
        linePaint.setStrokeWidth(1f);
        for (int y = 0; y < h; y += 6) {
            c.drawLine(0, y, w, y, linePaint);
        }

        Paint textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(28f);
        textPaint.setFakeBoldText(true);
        c.drawText("DEMETER", 16f, h - 20f, textPaint);

        return bmp;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPreviewLoop();
        if (mPreviewHandler != null) {
            mPreviewHandler.post(new Runnable() {
					@Override
					public void run() {
						if (mPreviewShader != null) {
							mPreviewShader.destroy();
							mPreviewShader = null;
						}
						if (mPreviewRenderer != null) {
							mPreviewRenderer.release();
							mPreviewRenderer = null;
						}
					}
				});
        }
        if (mPreviewThread != null) mPreviewThread.quit();
    }

    // ─── Selección de área de captura ─────────────────────────────────────────

    private void openCaptureAreaSelector() {
        startActivityForResult(
            new Intent(this, CaptureAreaActivity.class), REQ_CAPTURE_AREA);
    }

    private void openOverlayPositionSelector() {
        startActivityForResult(
            new Intent(this, OverlayPositionActivity.class), REQ_OVERLAY_POS);
    }

    // ─── Flujo de captura ─────────────────────────────────────────────────────

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
        startActivityForResult(
            mProjectionManager.createScreenCaptureIntent(), REQ_PROJECTION);
    }

    private void launchCaptureService(int resultCode, Intent data) {
        CaptureService.setModuleManager(mModuleManager);
        Intent svc = new Intent(this, CaptureService.class);
        svc.putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode);
        svc.putExtra(CaptureService.EXTRA_RESULT_DATA, data);

        // Pasar rectángulos si el usuario los configuró (null = pantalla completa = default)
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

        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
        else startService(svc);
        setState(CaptureState.PROJECTING);
    }

    private void stopCapture() {
        stopService(new Intent(this, CaptureService.class));
        setState(CaptureState.IDLE);
        CaptureService.setModuleManager(null);
    }

    private void setState(CaptureState state) {
        mState = state;
        boolean capturing = (state == CaptureState.PROJECTING);
        mTvStatus.setText(capturing ? R.string.status_capturing : R.string.status_idle);
        mBtnCapture.setText(capturing ? R.string.btn_cancel : R.string.btn_start);
        // Deshabilitar configuración de área mientras captura
        mBtnCaptureArea.setEnabled(!capturing);
        mBtnOverlayPos.setEnabled(!capturing);
        // Mostrar rectángulos configurados
        updateRectLabels();
        // Botón de parámetros
        updateParamsButton();
    }

    private void updateParamsButton() {
        Module active = mModuleManager.getActiveModule();
        boolean hasParams = active != null && !active.getParamDefs().isEmpty();
        mBtnParams.setVisibility(hasParams ? View.VISIBLE : View.GONE);
        if (hasParams) mBtnParams.setText("⚙ " + active.getName());
    }

    /**
     * Muestra un diálogo con un slider por cada parámetro configurable
     * del módulo activo. Los cambios se aplican en tiempo real.
     */
    private void showParamsDialog() {
        final Module module = mModuleManager.getActiveModule();
        if (module == null || module.getParamDefs().isEmpty()) return;

        final Map<String, Module.ParamDef> defs = module.getParamDefs();
        final Map<String, Float> current = module.getParams();

        // Contenedor con scroll por si hay muchos parámetros
        ScrollView scroll = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, pad);
        scroll.addView(container);

        for (final Map.Entry<String, Module.ParamDef> entry : defs.entrySet()) {
            final String uniformName = entry.getKey();
            final Module.ParamDef def = entry.getValue();
            float initialValue = current.containsKey(uniformName)
                ? current.get(uniformName) : def.defaultValue;

            // Label con nombre y valor actual
            final TextView label = new TextView(this);
            label.setTextColor(0xFF80CBC4);
            label.setPadding(0, pad / 2, 0, 4);
            final float range = def.max - def.min;

            // Formato: mostrar entero si el rango es grande, decimal si es pequeño
            final boolean showDecimal = range <= 10f;
            updateParamLabel(label, def.label, initialValue, showDecimal);
            container.addView(label);

            // SeekBar con 1000 pasos para precisión suficiente
            final SeekBar seekBar = new SeekBar(this);
            seekBar.setMax(1000);
            seekBar.setProgress(Math.round((initialValue - def.min) / range * 1000));
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
					@Override
					public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
						float value = def.min + (progress / 1000f) * range;
						updateParamLabel(label, def.label, value, showDecimal);
						// Aplicar en tiempo real
						mModuleManager.setParamValue(module, uniformName, value);
					}
					@Override public void onStartTrackingTouch(SeekBar sb) {}
					@Override public void onStopTrackingTouch(SeekBar sb) {}
				});
            container.addView(seekBar);

            // Mostrar min y max
            LinearLayout minMax = new LinearLayout(this);
            minMax.setOrientation(LinearLayout.HORIZONTAL);
            TextView tvMin = new TextView(this);
            tvMin.setTextColor(0xFFAAAAAA);
            tvMin.setTextSize(11f);
            tvMin.setText(showDecimal ? String.format("%.2f", def.min)
						  : String.valueOf((int) def.min));
            TextView tvMax = new TextView(this);
            tvMax.setTextColor(0xFFAAAAAA);
            tvMax.setTextSize(11f);
            tvMax.setText(showDecimal ? String.format("%.2f", def.max)
						  : String.valueOf((int) def.max));
            LinearLayout.LayoutParams lpMin = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            LinearLayout.LayoutParams lpMax = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            minMax.addView(tvMin, lpMin);
            minMax.addView(tvMax, lpMax);
            container.addView(minMax);
        }

        new AlertDialog.Builder(this)
            .setTitle("Parámetros: " + module.getName())
            .setView(scroll)
            .setPositiveButton("Cerrar", null)
            .setNeutralButton("Restablecer", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    for (Map.Entry<String, Module.ParamDef> e : defs.entrySet()) {
                        mModuleManager.setParamValue(module, e.getKey(), e.getValue().defaultValue);
                    }
                }
            })
            .show();
    }

    private void updateParamLabel(TextView tv, String label, float value, boolean decimal) {
        String valStr = decimal ? String.format("%.3f", value) : String.valueOf((int) value);
        tv.setText(label + ": " + valStr);
    }

    private void updateRectLabels() {
        String capLabel = "Área captura: "
            + (mCaptureRect == null ? "pantalla completa"
			: mCaptureRect.width() + "×" + mCaptureRect.height()
			+ " @(" + mCaptureRect.left + "," + mCaptureRect.top + ")");
        String ovLabel = "Overlay: "
            + (mOverlayRect == null ? "pantalla completa"
			: mOverlayRect.width() + "×" + mOverlayRect.height()
			+ " @(" + mOverlayRect.left + "," + mOverlayRect.top + ")");
        mBtnCaptureArea.setText(capLabel);
        mBtnOverlayPos.setText(ovLabel);
    }

    // ─── Módulos / shaders ────────────────────────────────────────────────────

    private void showSelectionMenu() {
        final List<Module> modifiers = mModuleManager.getByType(Module.Type.MODIFIER);
        final CharSequence[] items = new CharSequence[modifiers.size() + 2];
        items[0] = getString(R.string.menu_test_mode);
        items[1] = getString(R.string.menu_select_module) + " (ninguno)";
        for (int i = 0; i < modifiers.size(); i++) {
            String name = modifiers.get(i).getName();
            Module active = mModuleManager.getActiveModule();
            if (active != null && active.getName().equals(name)) name += " ✓";
            items[i + 2] = name;
        }
        new AlertDialog.Builder(this)
            .setTitle(R.string.btn_select)
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) {
                        toggleTestMode();
                    } else if (which == 1) {
                        mModuleManager.setActiveModule(null);
                        refreshPreviewForActiveModuleChange();
                        Toast.makeText(MainActivity.this,
									   "Ningún módulo seleccionado", Toast.LENGTH_SHORT).show();
                    } else {
                        Module selected = modifiers.get(which - 2);
                        String error = selected.getCompilationError();
                        if (error != null) {
                            new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Error de compilación")
                                .setMessage("El módulo \"" + selected.getName()
											+ "\" tiene errores:\n" + error)
                                .setPositiveButton("OK", null)
                                .show();
                            return;
                        }
                        mModuleManager.setActiveModule(selected);
                        if (!selected.isEnabled()) mModuleManager.setEnabled(selected, true);
                        updateParamsButton();
                        refreshPreviewForActiveModuleChange();
                        Toast.makeText(MainActivity.this,
									   "Módulo seleccionado: " + selected.getName(),
									   Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton(R.string.btn_cancel, null)
            .show();
    }

    private void toggleTestMode() {
        mTestMode = !mTestMode;
        CaptureApi api = CaptureService.getCaptureApi();
        if (api != null) api.setTestMode(mTestMode);
        Toast.makeText(this, "Modo prueba: " + (mTestMode ? "ON" : "OFF"),
					   Toast.LENGTH_SHORT).show();
    }

    private void importModule() {
        // Preguntar de dónde importar: archivo local o tienda
        new AlertDialog.Builder(this)
            .setTitle("Importar shader")
            .setItems(new CharSequence[]{"Archivo local", "Tienda"},
			new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					if (which == 0) importModuleLocal();
					else            openShaderStore();
				}
			})
            .setNegativeButton(R.string.btn_cancel, null)
            .show();
    }

    private void importModuleLocal() {
        if (Build.VERSION.SDK_INT >= 23
            && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
			!= PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE);
        } else {
            openFilePicker();
        }
    }

    private void openShaderStore() {
        startActivityForResult(
            new Intent(this, ShaderStoreActivity.class), REQ_STORE);
    }

    private void openFilePicker() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(
            Intent.createChooser(i, getString(R.string.import_module)), REQ_PICK_MODULE);
    }

    // ─── Resultados ───────────────────────────────────────────────────────────

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
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
                        Toast.makeText(this, R.string.msg_installed, Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "Error: " + e.getMessage(),
									   Toast.LENGTH_LONG).show();
                    }
                }
                break;

            case REQ_CAPTURE_AREA:
                // Guardar el rectángulo de captura elegido por el usuario
                if (resultCode == RESULT_OK && data != null) {
                    DisplayMetrics m = new DisplayMetrics();
                    getWindowManager().getDefaultDisplay().getRealMetrics(m);
                    int l = data.getIntExtra(CaptureAreaActivity.RESULT_LEFT,   0);
                    int t = data.getIntExtra(CaptureAreaActivity.RESULT_TOP,    0);
                    int r = data.getIntExtra(CaptureAreaActivity.RESULT_RIGHT,  m.widthPixels);
                    int b = data.getIntExtra(CaptureAreaActivity.RESULT_BOTTOM, m.heightPixels);
                    mCaptureRect = new Rect(l, t, r, b);
                    updateRectLabels();
                    Toast.makeText(this,
								   "Área de captura: " + mCaptureRect.width()
								   + "×" + mCaptureRect.height(), Toast.LENGTH_SHORT).show();
                }
                break;

            case REQ_OVERLAY_POS:
                // Guardar el rectángulo del overlay elegido por el usuario
                if (resultCode == RESULT_OK && data != null) {
                    DisplayMetrics m = new DisplayMetrics();
                    getWindowManager().getDefaultDisplay().getRealMetrics(m);
                    int l = data.getIntExtra(OverlayPositionActivity.RESULT_LEFT,   0);
                    int t = data.getIntExtra(OverlayPositionActivity.RESULT_TOP,    0);
                    int r = data.getIntExtra(OverlayPositionActivity.RESULT_RIGHT,  m.widthPixels);
                    int b = data.getIntExtra(OverlayPositionActivity.RESULT_BOTTOM, m.heightPixels);
                    mOverlayRect = new Rect(l, t, r, b);
                    updateRectLabels();
                    Toast.makeText(this,
								   "Overlay: " + mOverlayRect.width()
								   + "×" + mOverlayRect.height(), Toast.LENGTH_SHORT).show();
                }
                break;

            case REQ_STORE:
                // Shader instalado desde la tienda → recargar módulos y refrescar preview
                if (resultCode == RESULT_OK) {
                    String installedName = data != null
                        ? data.getStringExtra(ShaderStoreActivity.RESULT_EXTRA_NAME) : null;
                    // CRÍTICO: la tienda usa su propia instancia de ModuleManager.
                    // Hay que recargar desde SharedPreferences para ver el shader nuevo.
                    mModuleManager.reload();
                    Toast.makeText(this,
								   installedName != null
								   ? "Shader instalado: " + installedName
								   : "Shader instalado",
								   Toast.LENGTH_SHORT).show();
                    refreshPreviewForActiveModuleChange();
                    updateParamsButton();
                }
                break;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // FIX Bug 1: reafirmar modo inmersivo cuando la ventana recupera el foco
        // (p. ej. al volver de otra Activity).
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == REQ_STORAGE) {
            if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                openFilePicker();
            else
                Toast.makeText(this, R.string.perm_denied, Toast.LENGTH_LONG).show();
        }
    }
}



