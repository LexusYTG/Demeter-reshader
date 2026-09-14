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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.SurfaceHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TargetRenderer {
    private static final String TAG = "TargetRenderer";
    private static final long FRAME_DELAY_MS = 16;
    private static final int NUM_CAPTURE_SLOTS = 3;
    private static final int NUM_OUTPUT_SLOTS  = 3;
    private static final int OUTPUT_SLOT_OFFSET = 3;

    private static final int CHAIN_INPUT_SLOT = 0;
    private static final int CHAIN_FIRST_OUT  = 3;

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

    public float getFps() { return mFps; }

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

    private volatile boolean mFrameGenMode = false;

    private int[] captureSlotState = new int[NUM_CAPTURE_SLOTS];
    private int[] logicalOrder     = new int[NUM_CAPTURE_SLOTS];

    private int[] outputSlotState = new int[NUM_OUTPUT_SLOTS];
    private int   outputReadHead  = 0;
    private int   outputCount     = 0;

    private boolean generationRunning = false;
    private final Runnable generationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mFrameGenMode || !mGlReady || mHolder == null || mGlRenderer == null) {
                generationRunning = false;
                return;
            }

            int phys0 = logicalOrder[0];
            int phys1 = logicalOrder[1];
            if (captureSlotState[phys0] != 1 || captureSlotState[phys1] != 1) {
                generationRunning = false;
                return;
            }

            int outIdx1 = -1, outIdx2 = -1;
            for (int i = 0; i < NUM_OUTPUT_SLOTS; i++) {
                if (outputSlotState[i] == 0) {
                    if (outIdx1 == -1) outIdx1 = i;
                    else { outIdx2 = i; break; }
                }
            }
            if (outIdx1 == -1 || outIdx2 == -1) {
                mGlHandler.postDelayed(this, FRAME_DELAY_MS);
                return;
            }

            ModuleManager mm = mModuleManager;
            Module active = (mm != null) ? mm.getActiveModule() : null;
            ShaderFilter shader = null;
            Map<String, Float> baseParams = null;
            if (active != null && active.isEnabled()) {
                shader = active.getShaderFilter();
                if (shader != null) {
                    baseParams = new HashMap<String, Float>(active.getParams());
                    baseParams.put("uTime", (System.currentTimeMillis() - mStartTimeMs) / 1000f);
                }
            }

            int targetPhys1 = OUTPUT_SLOT_OFFSET + outIdx1;
            Map<String, Float> origParams = null;
            if (baseParams != null) {
                origParams = new HashMap<String, Float>(baseParams);
                origParams.put("uFrameGen", 0f);
            }
            if (shader != null) {
                if (!mGlRenderer.drawGenSlotToTexture(phys0, targetPhys1, shader, origParams)) return;
            } else {
                if (!mGlRenderer.drawGenSlotToTexture(phys0, targetPhys1, null, null)) return;
            }
            outputSlotState[outIdx1] = 1;
            outputCount++;

            int targetPhys2 = OUTPUT_SLOT_OFFSET + outIdx2;
            Module frameGenModule = (mm != null) ? mm.getActiveFrameGenModule() : null;
            ShaderFilter fgShader = null;
            Map<String, Float> fgParams = null;
            if (frameGenModule != null && frameGenModule.isEnabled()) {
                fgShader = frameGenModule.getShaderFilter();
                if (fgShader != null) {
                    fgParams = new HashMap<String, Float>(frameGenModule.getParams());
                    fgParams.put("uTime", (System.currentTimeMillis() - mStartTimeMs) / 1000f);
                    fgParams.put("uFrameGen", 1f);
                    fgParams.put("uMix", 0.5f);
                }
            }
            boolean interpOk;
            if (fgShader != null) {
                interpOk = mGlRenderer.drawGenBlendShaderToTexture(
					phys0, phys1, 0.5f, fgShader, fgParams, targetPhys2);
            } else {
                interpOk = mGlRenderer.drawGenBlendToTexture(phys0, phys1, 0.5f, targetPhys2);
            }
            if (!interpOk) {
                outputSlotState[outIdx1] = 0;
                outputCount--;
                return;
            }
            outputSlotState[outIdx2] = 1;
            outputCount++;

            int old0 = logicalOrder[0];
            int old1 = logicalOrder[1];
            int old2 = logicalOrder[2];
            logicalOrder[0] = old1;
            logicalOrder[1] = old2;
            logicalOrder[2] = old0;
            captureSlotState[old0] = 2;

            if (captureSlotState[logicalOrder[0]] == 1 && captureSlotState[logicalOrder[1]] == 1) {
                mGlHandler.postDelayed(this, FRAME_DELAY_MS / 2);
            } else {
                generationRunning = false;
            }
        }
    };

    private boolean presentationRunning = false;
    private final Runnable presentationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mFrameGenMode || !mGlReady || mHolder == null || mGlRenderer == null) {
                presentationRunning = false;
                return;
            }
            if (outputCount > 0) {
                int slot = outputReadHead;
                int physSlot = OUTPUT_SLOT_OFFSET + slot;
                mGlRenderer.drawGenSlot(physSlot, null, null);
                outputSlotState[slot] = 0;
                outputCount--;
                outputReadHead = (outputReadHead + 1) % NUM_OUTPUT_SLOTS;
                recordFrame();
            }
            mGlHandler.postDelayed(this, FRAME_DELAY_MS);
        }
    };

    private final Map<String, Float> mParamsCache = new HashMap<String, Float>();

    public TargetRenderer(SurfaceHolder holder, ModuleManager moduleManager) {
        mHolder = holder;
        mModuleManager = moduleManager;

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

    public void setFrameGenMode(final boolean enabled) {
        mFrameGenMode = enabled;
        mGlHandler.post(new Runnable() {
				@Override
				public void run() {
					if (enabled) {
						stopNormalRenderLoop();
						for (int i = 0; i < NUM_CAPTURE_SLOTS; i++) captureSlotState[i] = 0;
						logicalOrder[0] = 0; logicalOrder[1] = 1; logicalOrder[2] = 2;
						for (int i = 0; i < NUM_OUTPUT_SLOTS; i++) outputSlotState[i] = 0;
						outputReadHead = 0; outputCount = 0;
						generationRunning = false;
						if (!presentationRunning) {
							presentationRunning = true;
							mGlHandler.removeCallbacks(presentationRunnable);
							mGlHandler.post(presentationRunnable);
						}
					} else {
						presentationRunning = false;
						mGlHandler.removeCallbacks(presentationRunnable);
						mGlHandler.removeCallbacks(generationRunnable);
						generationRunning = false;
						startNormalRenderLoopIfNeeded();
					}
				}
			});
    }

    public void setScaleInfo(Rect captureRect, Rect overlayRect) { }

    public void receiveFrame(final Bitmap frame) {
        if (frame == null) return;
        mGlHandler.post(new Runnable() {
				@Override
				public void run() {
					if (mHolder == null || !mGlReady || mGlRenderer == null) {
						BitmapPool.release(frame);
						return;
					}
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

    private void receiveFrameGen(Bitmap frame) {
        int targetSlot = -1;
        for (int i = 0; i < NUM_CAPTURE_SLOTS; i++) {
            if (captureSlotState[i] == 0 || captureSlotState[i] == 2) {
                targetSlot = i;
                break;
            }
        }
        if (targetSlot == -1) {
            BitmapPool.release(frame);
            return;
        }

        if (mGlRenderer != null) {
            mGlRenderer.uploadToGenSlot(targetSlot, frame);
        } else {
            BitmapPool.release(frame);
            return;
        }
        captureSlotState[targetSlot] = 1;

        int fillCount = 0;
        for (int i = 0; i < NUM_CAPTURE_SLOTS; i++) {
            if (captureSlotState[i] == 1) fillCount++;
        }
        if (fillCount == NUM_CAPTURE_SLOTS && !generationRunning) {
            generationRunning = true;
            mGlHandler.post(generationRunnable);
        }
    }

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
						presentationRunning = false;
						mGlHandler.removeCallbacks(presentationRunnable);
						mGlHandler.removeCallbacks(generationRunnable);
						generationRunning = false;
					}
					if (mGlRenderer == null) return;
					if (holder != null) {
						destroyAllModuleShaders();
						mGlRenderer.setSurface(holder);
						mGlReady = true;
						if (mFrameGenMode) {
							presentationRunning = true;
							mGlHandler.post(presentationRunnable);
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
							presentationRunning = false;
							mGlHandler.removeCallbacks(presentationRunnable);
							mGlHandler.removeCallbacks(generationRunnable);
							generationRunning = false;
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
