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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TargetRenderer {
    private static final String TAG = "TargetRenderer";
    private static final long FRAME_DELAY_MS = 16;

    // Slots de FrameGen: 0=A (previo), 1=C (actual), 2=libre (rotativo),
    // 5=fake output (persistente, sobrescrito).
    private static final int FG_SLOT_FAKE = 5;

    // Chain slots (modo normal): input en 0, salidas en 3, 4, 5.
    private static final int CHAIN_INPUT_SLOT = 0;
    private static final int CHAIN_FIRST_OUT  = 3;

    private static final long DEFAULT_INPUT_INTERVAL_NS   = 33_000_000L;
    private static final long DEFAULT_DISPLAY_INTERVAL_NS = 16_666_667L;

    private final Context mContext;

    private volatile SurfaceHolder mHolder;
    private volatile ModuleManager mModuleManager;
    private GlRenderer mGlRenderer;
    private boolean mGlReady = false;

    private HandlerThread mGlThread;
    private Handler       mGlHandler;
    private final long mStartTimeMs = System.currentTimeMillis();

    private static final int FPS_WINDOW = 30;
    private final long[] mFrameTimesNs = new long[FPS_WINDOW];
    private int          mFrameTimeIdx = 0;
    private volatile float mFps        = -1f;

    private volatile boolean mFpsOverlay = false;

    // Pacing
    private volatile long mLastInputNs       = 0L;
    private volatile long mInputIntervalNs   = DEFAULT_INPUT_INTERVAL_NS;
    private volatile long mDisplayIntervalNs = DEFAULT_DISPLAY_INTERVAL_NS;

    // FrameGen state
    private volatile boolean mFrameGenMode = false;
    private int mFgSlotA = -1;
    private int mFgSlotC = -1;
    private volatile boolean mFgFakeReady   = false;
    private boolean          mFgShowFake    = false;
    private boolean          mFgGenRunning  = false;
    private boolean          mFgPresRunning = false;

    private volatile boolean mRenderLoopRunning = false;
    private final Runnable mRenderLoop = new Runnable() {
        @Override
        public void run() {
            if (!mRenderLoopRunning) return;
            if (mHolder != null && mGlReady && mGlRenderer != null) {
                drawCurrentFrame(null);
                recordFrame();
            }
            mGlHandler.postDelayed(this, FRAME_DELAY_MS * 2);
        }
    };

    private void drawCurrentFrame(Bitmap frameIfNew) {
        List<Module> chain = (mModuleManager != null)
            ? mModuleManager.getEnabledChain()
            : new ArrayList<Module>();

        if (frameIfNew != null) {
            if (!mGlRenderer.uploadToGenSlot(CHAIN_INPUT_SLOT, frameIfNew)) return;
        }

        if (chain.isEmpty()) {
            mGlRenderer.drawGenSlot(CHAIN_INPUT_SLOT, null, null);
            return;
        }

        if (chain.size() == 1) {
            Module m = chain.get(0);
            ShaderFilter shader = m.getShaderFilter();
            if (shader == null) {
                mGlRenderer.drawGenSlot(CHAIN_INPUT_SLOT, null, null);
                return;
            }
            Map<String, Float> params = buildParams(m);
            mGlRenderer.drawGenSlot(CHAIN_INPUT_SLOT, shader, params);
            return;
        }

        int src = CHAIN_INPUT_SLOT;
        int dst = CHAIN_FIRST_OUT;
        boolean anyApplied = false;

        for (Module m : chain) {
            ShaderFilter shader = m.getShaderFilter();
            if (shader == null) continue;
            Map<String, Float> params = buildParams(m);
            if (!mGlRenderer.drawGenSlotToTexture(src, dst, shader, params)) {
                Log.w(TAG, "Chain: falló " + m.getName());
                break;
            }
            src = dst;
            dst = (dst == 3) ? 4 : (dst == 4 ? 5 : 3);
            if (dst == src) dst = (dst == 3) ? 4 : (dst == 4 ? 5 : 3);
            anyApplied = true;
        }

        if (anyApplied) {
            mGlRenderer.drawGenSlot(src, null, null);
        } else {
            mGlRenderer.drawGenSlot(CHAIN_INPUT_SLOT, null, null);
        }
    }

    private Map<String, Float> buildParams(Module m) {
        mParamsCache.clear();
        mParamsCache.putAll(m.getParams());
        if (m.getParamDefs().containsKey("uTime")) {
            mParamsCache.put("uTime", (System.currentTimeMillis() - mStartTimeMs) / 1000f);
        }
        return mParamsCache;
    }

    private final Map<String, Float> mParamsCache = new HashMap<String, Float>();

    public TargetRenderer(Context context, SurfaceHolder holder, ModuleManager moduleManager) {
        mContext = context.getApplicationContext();
        mHolder = holder;
        mModuleManager = moduleManager;

        try {
            WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                Display d = wm.getDefaultDisplay();
                if (d != null) {
                    float hz = d.getRefreshRate();
                    if (hz > 0f) {
                        mDisplayIntervalNs = (long)(1_000_000_000L / hz);
                        Log.d(TAG, "Display refresh: " + hz + "Hz → "
                              + (mDisplayIntervalNs / 1_000_000L) + "ms por frame");
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "No se pudo leer refresh rate: " + e.getMessage());
        }

        mGlThread = new HandlerThread("GlThread");
        mGlThread.start();
        mGlHandler = new Handler(mGlThread.getLooper());

        mGlHandler.post(new Runnable() {
                @Override
                public void run() {
                    mGlRenderer = new GlRenderer();
                    mGlReady = mGlRenderer.init(mHolder);
                    if (mGlReady) Log.d(TAG, "GlRenderer initialized on GL thread");
                    else          Log.e(TAG, "GlRenderer init failed");
                }
            });
    }

    public float getFps() { return mFps; }

    public void setFpsOverlay(boolean enabled) { mFpsOverlay = enabled; }

    private void recordFrame() {
        long now = System.nanoTime();
        mFrameTimesNs[mFrameTimeIdx] = now;
        mFrameTimeIdx = (mFrameTimeIdx + 1) % FPS_WINDOW;

        int count = 0;
        long oldest = Long.MAX_VALUE;
        long newest = 0;
        for (int i = 0; i < FPS_WINDOW; i++) {
            long t = mFrameTimesNs[i];
            if (t > 0) {
                count++;
                if (t < oldest) oldest = t;
                if (t > newest) newest = t;
            }
        }
        if (count >= 2 && oldest != Long.MAX_VALUE && newest > oldest) {
            float elapsedSec = (newest - oldest) / 1_000_000_000f;
            if (elapsedSec > 0f) mFps = (count - 1) / elapsedSec;
        } else {
            mFps = -1f;
        }
    }

    public void setTestMode(final boolean testMode) {
        mTestMode = testMode;
        mGlHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (testMode) {
                        if (!mTestLoopRunning) {
                            mTestLoopRunning = true;
                            mGlHandler.post(mTestFrameRunnable);
                        }
                    } else {
                        mTestLoopRunning = false;
                        mGlHandler.removeCallbacks(mTestFrameRunnable);
                    }
                }
            });
    }

    private volatile boolean mTestMode = false;
    private boolean mTestLoopRunning = false;
    private final Runnable mTestFrameRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mTestMode || mHolder == null) {
                mTestLoopRunning = false;
                return;
            }
            receiveFrame(generateTestFrame());
            mGlHandler.postDelayed(this, FRAME_DELAY_MS);
        }
    };

    private Bitmap generateTestFrame() {
        Rect frameRect = mHolder.getSurfaceFrame();
        int w = frameRect.width()  > 0 ? frameRect.width()  : 720;
        int h = frameRect.height() > 0 ? frameRect.height() : 1280;

        Bitmap bmp = BitmapPool.acquire(w, h);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.BLACK);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTextSize(h / 4f);
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float textY = (h / 2f) - (fm.ascent + fm.descent) / 2f;
        canvas.drawText("1", w / 2f, textY, paint);
        return bmp;
    }

    public void receiveFrame(final Bitmap frame) {
        if (frame == null) return;
        mGlHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mHolder == null || !mGlReady || mGlRenderer == null) {
                        BitmapPool.release(frame);
                        return;
                    }
                    syncFrameGenMode();
                    if (mFrameGenMode) {
                        receiveFrameGen(frame);
                    } else {
                        receiveFrameNormal(frame);
                    }
                }
            });
    }

    private void receiveFrameNormal(Bitmap frame) {
        drawCurrentFrame(frame);
        recordFrame();

        if (hasAnimatedModule()) {
            startNormalRenderLoopIfNeeded();
        } else {
            stopNormalRenderLoop();
        }
    }

    /**
     * Nuevo input de FrameGen. Nunca espera: sube al slot libre, rota, y
     * dispara generación si no hay una corriendo. Si hay una corriendo,
     * simplemente queda listo para la próxima vuelta — no se encola.
     */
    private void receiveFrameGen(Bitmap frame) {
        long now = System.nanoTime();
        if (mLastInputNs > 0L) {
            long delta = now - mLastInputNs;
            if (delta > 0L && delta < 2_000_000_000L) {
                mInputIntervalNs = (mInputIntervalNs * 3L + delta) / 4L;
            }
        }
        mLastInputNs = now;

        // Elegir slot libre: no puede ser A ni C.
        int freeSlot;
        if (mFgSlotC < 0) {
            freeSlot = 0;
        } else if (mFgSlotA < 0) {
            freeSlot = (mFgSlotC + 1) % 3;
        } else {
            freeSlot = 3 - mFgSlotA - mFgSlotC;  // suma de slots = 0+1+2 = 3
        }

        if (mGlRenderer == null) {
            BitmapPool.release(frame);
            return;
        }
        mGlRenderer.uploadToGenSlot(freeSlot, frame);

        // Rotar: A ← C, C ← libre
        mFgSlotA = mFgSlotC;
        mFgSlotC = freeSlot;

        // El fake anterior queda obsoleto
        mFgFakeReady = false;

        // Disparar generación si tenemos A y C y no hay una corriendo
        if (mFgSlotA >= 0 && !mFgGenRunning) {
            mFgGenRunning = true;
            mGlHandler.post(mFgGenerationRunnable);
        }
    }

    private final Runnable mFgGenerationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mFrameGenMode || !mGlReady || mHolder == null || mGlRenderer == null) {
                mFgGenRunning = false;
                return;
            }

            final int a = mFgSlotA;
            final int c = mFgSlotC;
            if (a < 0 || c < 0) {
                mFgGenRunning = false;
                return;
            }

            ModuleManager mm = mModuleManager;
            Module fgModule = (mm != null) ? mm.getActiveFrameGenModule() : null;

            float mix = 0.5f;
            if (fgModule != null) {
                Float mv = fgModule.getParams().get("uMix");
                if (mv != null) mix = mv;
            }

            ShaderFilter fgShader = null;
            Map<String, Float> fgParams = null;
            if (fgModule != null && fgModule.isEnabled()) {
                fgShader = fgModule.getShaderFilter();
                if (fgShader != null) {
                    fgParams = new HashMap<String, Float>(fgModule.getParams());
                    fgParams.put("uTime", (System.currentTimeMillis() - mStartTimeMs) / 1000f);
                    fgParams.put("uFrameGen", 1f);
                }
            }

            boolean ok;
            if (fgShader != null) {
                ok = mGlRenderer.drawGenBlendShaderToTexture(
                    a, c, mix, fgShader, fgParams, FG_SLOT_FAKE);
            } else {
                ok = mGlRenderer.drawGenBlendToTexture(a, c, mix, FG_SLOT_FAKE);
            }

            mFgFakeReady = ok;
            mFgGenRunning = false;

            // Si mientras generábamos llegó un nuevo input, disparamos otra
            // generación inmediatamente con los datos frescos.
            if (mFgSlotA >= 0 && mFgSlotC >= 0 && mFgSlotA != a && !mFgFakeReady) {
                mFgGenRunning = true;
                mGlHandler.post(this);
            }
        }
    };

    private final Runnable mFgPresentationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mFrameGenMode || !mGlReady || mHolder == null || mGlRenderer == null) {
                mFgPresRunning = false;
                return;
            }

            int slotToShow = -1;
            if (mFgShowFake && mFgFakeReady) {
                slotToShow = FG_SLOT_FAKE;
                mFgFakeReady = false;
                mFgShowFake = false;
            } else if (mFgSlotC >= 0) {
                slotToShow = mFgSlotC;
                mFgShowFake = true;
            }

            if (slotToShow >= 0) {
                mGlRenderer.drawGenSlot(slotToShow, null, null);
                recordFrame();
            }

            mGlHandler.postDelayed(this, computePresentationDelayMs());
        }
    };

    private long computePresentationDelayMs() {
        long half = mInputIntervalNs / 2;
        long target = Math.max(half, mDisplayIntervalNs);
        return Math.max(1L, target / 1_000_000L);
    }

    private void syncFrameGenMode() {
        ModuleManager mm = mModuleManager;
        Module fg = (mm != null) ? mm.getActiveFrameGenModule() : null;
        boolean want = (fg != null && fg.isEnabled());

        if (want == mFrameGenMode) return;

        mFrameGenMode = want;
        Log.d(TAG, "FrameGen mode: " + (want ? "ON" : "OFF"));

        if (want) {
            stopNormalRenderLoop();
            mFgSlotA = -1;
            mFgSlotC = -1;
            mFgFakeReady = false;
            mFgShowFake = false;
            mFgGenRunning = false;
            mLastInputNs = 0L;
            mInputIntervalNs = DEFAULT_INPUT_INTERVAL_NS;
            if (!mFgPresRunning) {
                mFgPresRunning = true;
                mGlHandler.removeCallbacks(mFgPresentationRunnable);
                mGlHandler.post(mFgPresentationRunnable);
            }
        } else {
            mFgPresRunning = false;
            mGlHandler.removeCallbacks(mFgPresentationRunnable);
            mGlHandler.removeCallbacks(mFgGenerationRunnable);
            mFgGenRunning = false;
            startNormalRenderLoopIfNeeded();
        }
    }

    public void setScaleInfo(Rect captureRect, Rect overlayRect) { }

    private boolean hasAnimatedModule() {
        ModuleManager mm = mModuleManager;
        if (mm == null) return false;
        for (Module m : mm.getEnabledChain()) {
            if (m.getParamDefs().containsKey("uTime")) return true;
        }
        return false;
    }

    private void startNormalRenderLoopIfNeeded() {
        if (!mRenderLoopRunning) {
            mRenderLoopRunning = true;
            mGlHandler.post(mRenderLoop);
            Log.d(TAG, "Normal render loop iniciado");
        }
    }

    private void stopNormalRenderLoop() {
        if (mRenderLoopRunning) {
            mRenderLoopRunning = false;
            mGlHandler.removeCallbacks(mRenderLoop);
            Log.d(TAG, "Normal render loop detenido");
        }
    }

    public void setHolder(final SurfaceHolder holder) {
        mHolder = holder;
        mGlHandler.post(new Runnable() {
                @Override
                public void run() {
                    stopNormalRenderLoop();
                    if (mFrameGenMode) {
                        mFgPresRunning = false;
                        mGlHandler.removeCallbacks(mFgPresentationRunnable);
                        mGlHandler.removeCallbacks(mFgGenerationRunnable);
                        mFgGenRunning = false;
                    }
                    if (mGlRenderer == null) return;
                    if (holder != null) {
                        destroyAllModuleShaders();
                        mGlRenderer.setSurface(holder);
                        mGlReady = true;
                        if (mFrameGenMode) {
                            mFgPresRunning = true;
                            mGlHandler.post(mFgPresentationRunnable);
                        }
                    } else {
                        mGlRenderer.setSurface(null);
                        mGlReady = false;
                    }
                }
            });
    }

    public void setModuleManager(ModuleManager moduleManager) {
        mModuleManager = moduleManager;
    }

    private void destroyAllModuleShaders() {
        ModuleManager mm = mModuleManager;
        if (mm == null) return;
        for (Module m : mm.getAll()) {
            m.destroyShader();
        }
    }

    public void release() {
        final CountDownLatch latch = new CountDownLatch(1);
        mGlHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        stopNormalRenderLoop();
                        if (mFrameGenMode) {
                            mFgPresRunning = false;
                            mGlHandler.removeCallbacks(mFgPresentationRunnable);
                            mGlHandler.removeCallbacks(mFgGenerationRunnable);
                            mFgGenRunning = false;
                        }
                        if (mGlRenderer != null) {
                            destroyAllModuleShaders();
                            mGlRenderer.release();
                            mGlRenderer = null;
                        }
                        mGlReady = false;
                    } catch (Exception e) {
                        Log.e(TAG, "Error en release()", e);
                    } finally {
                        latch.countDown();
                    }
                }
            });
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                Log.w(TAG, "release(): timeout esperando al hilo GL");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        mGlThread.quitSafely();
    }
}
