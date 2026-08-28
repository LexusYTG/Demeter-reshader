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

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.SurfaceHolder;
import java.util.HashMap;
import java.util.Map;

/**
 * TargetRenderer con render loop independiente para shaders animados.
 *
 * FLUJO CON ANIMACIÓN (módulo con uTime en paramDefs):
 *   receiveFrame()  →  GlRenderer.uploadBitmap()   (sube a VRAM, recicla bitmap)
 *   mRenderLoop     →  GlRenderer.drawFrame(shader, params)  (dibuja ~30 fps)
 *
 *   La textura en VRAM persiste entre ticks; el loop solo redibuja con uTime
 *   fresco. La animación corre aunque la pantalla capturada esté quieta.
 *
 * FLUJO SIN ANIMACIÓN (módulo sin uTime, o sin módulo activo):
 *   receiveFrame()  →  GlRenderer.drawFrame(bitmap, shader, params)
 *
 *   Path original: upload + draw en una llamada, sin loop, sin overhead.
 *
 * THREAD SAFETY:
 *   Todo acceso a GlRenderer ocurre en mGlThread (hilo GL).
 *   Los campos volátiles (mHolder, mModuleManager, mNeedsScale…) son leídos
 *   en el hilo GL como snapshot al inicio de cada operación.
 */
public class TargetRenderer {
    private static final String TAG = "TargetRenderer";

    /** Intervalo del render loop: 33 ms ≈ 30 fps. Bajar a 16 para 60 fps. */
    private static final long FRAME_INTERVAL_MS = 33;

    private volatile SurfaceHolder mHolder;
    private volatile ModuleManager mModuleManager;
    private GlRenderer mGlRenderer;
    private boolean mGlReady = false;

    private HandlerThread mGlThread;
    private Handler       mGlHandler;

    // uTime relativo al inicio de sesión: evita pérdida de precisión mediump
    // float que ocurriría con el tiempo absoluto de época (~1.7e6 segundos).
    private final long mStartTimeMs = System.currentTimeMillis();

    // Controla si el render loop está corriendo.
    private volatile boolean mRenderLoopRunning = false;

    // Info de escala: capture vs overlay, usada en el vertex shader (uScale).
    // Ya no se escala en CPU — GL escala la textura al viewport del overlay.
    private volatile int     mCaptureW   = 0;
    private volatile int     mCaptureH   = 0;
    private volatile int     mOverlayW   = 0;
    private volatile int     mOverlayH   = 0;
    private volatile boolean mNeedsScale = false;

    // HashMap reutilizable para params por frame — evita GC pressure.
    // Solo se accede desde mGlThread, sin necesidad de sincronización.
    private final Map<String, Float> mParamsCache = new HashMap<String, Float>();

    /**
     * Render loop: redibuja la textura en VRAM con uTime fresco cada tick.
     * No recibe ni recicla ningún Bitmap — la textura ya está en GPU.
     * Se autoposta mientras mRenderLoopRunning == true.
     */
    private final Runnable mRenderLoop = new Runnable() {
        @Override
        public void run() {
            if (!mRenderLoopRunning) return;

            if (mHolder != null && mGlReady) {
                ModuleManager mm    = mModuleManager;
                Module        active = (mm != null) ? mm.getActiveModule() : null;
                ShaderFilter  shader = null;
                Map<String, Float> params = null;

                if (active != null && active.isEnabled()) {
                    shader = active.getShaderFilter();
                    if (shader != null) {
                        // Reusar mParamsCache — sin alloc por tick del loop
                        mParamsCache.clear();
                        mParamsCache.putAll(active.getParams());
                        mParamsCache.put("uTime",
										 (System.currentTimeMillis() - mStartTimeMs) / 1000f);
                        params = mParamsCache;
                    }
                }

                mGlRenderer.drawFrame(shader, params);
            }

            mGlHandler.postDelayed(this, FRAME_INTERVAL_MS);
        }
    };

    public TargetRenderer(SurfaceHolder holder, ModuleManager moduleManager) {
        mHolder        = holder;
        mModuleManager = moduleManager;

        mGlThread = new HandlerThread("GlThread");
        mGlThread.start();
        mGlHandler = new Handler(mGlThread.getLooper());

        mGlHandler.post(new Runnable() {
				@Override
				public void run() {
					mGlRenderer = new GlRenderer();
					mGlReady    = mGlRenderer.init(mHolder);
					if (mGlReady) Log.d(TAG, "GlRenderer initialized on GL thread");
					else          Log.e(TAG, "GlRenderer init failed");
				}
			});
    }

    public void setTestMode(boolean testMode) {
        // TODO: Implement this method
    }

    public void setScaleInfo(Rect captureRect, Rect overlayRect) {
        if (captureRect == null || overlayRect == null) {
            mNeedsScale = false;
            return;
        }
        mCaptureW = captureRect.width();
        mCaptureH = captureRect.height();
        mOverlayW = overlayRect.width();
        mOverlayH = overlayRect.height();
        mNeedsScale = (mCaptureW != mOverlayW || mCaptureH != mOverlayH);
        Log.d(TAG, "ScaleInfo: capture=" + mCaptureW + "x" + mCaptureH
              + " overlay=" + mOverlayW + "x" + mOverlayH
              + " needsScale=" + mNeedsScale);
    }

    /**
     * Recibe un frame de captura y lo encola en el hilo GL.
     *
     * Con módulo animado: sube el bitmap a VRAM y lo recicla; el render loop
     * se encarga de redibujar con uTime fresco sin volver a tocar el bitmap.
     *
     * Sin módulo animado: upload + draw en una sola llamada, loop detenido.
     */
    public void receiveFrame(final Bitmap frame) {
        if (frame == null) return;

        mGlHandler.post(new Runnable() {
				@Override
				public void run() {
					if (mHolder == null || !mGlReady) {
						if (!frame.isRecycled()) frame.recycle();
						return;
					}

					// Escalado eliminado de CPU: GL mapea la textura al viewport
					// del overlay directamente. Sin createScaledBitmap, sin copia
					// en RAM, sin presión de GC. El resultado visual es idéntico.

					if (hasAnimatedModule()) {
						// Subir a VRAM y reciclar el bitmap. El loop dibuja desde GPU.
						mGlRenderer.uploadBitmap(frame);
						startRenderLoopIfNeeded();
					} else {
						// Sin animación: upload + draw juntos, sin loop.
						stopRenderLoop();
						ModuleManager mm     = mModuleManager;
						Module        active = (mm != null) ? mm.getActiveModule() : null;
						ShaderFilter  shader = null;
						Map<String, Float> params = null;

						if (active != null && active.isEnabled()) {
							shader = active.getShaderFilter();
							if (shader != null) {
								// Reusar mParamsCache en lugar de new HashMap cada frame
								mParamsCache.clear();
								mParamsCache.putAll(active.getParams());
								mParamsCache.put("uTime",
												 (System.currentTimeMillis() - mStartTimeMs) / 1000f);
								params = mParamsCache;
							}
						}

						mGlRenderer.drawFrame(frame, shader, params);
					}
				}
			});
    }

    /**
     * Devuelve true si el módulo activo declara uTime en sus paramDefs,
     * indicando que el shader usa animación temporal.
     */
    private boolean hasAnimatedModule() {
        ModuleManager mm = mModuleManager;
        if (mm == null) return false;
        Module active = mm.getActiveModule();
        return active != null
			&& active.isEnabled()
			&& active.getParamDefs().containsKey("uTime");
    }

    private void startRenderLoopIfNeeded() {
        if (!mRenderLoopRunning) {
            mRenderLoopRunning = true;
            mGlHandler.post(mRenderLoop);
            Log.d(TAG, "Render loop iniciado");
        }
    }

    private void stopRenderLoop() {
        if (mRenderLoopRunning) {
            mRenderLoopRunning = false;
            mGlHandler.removeCallbacks(mRenderLoop);
            Log.d(TAG, "Render loop detenido");
        }
    }

    public void setHolder(final SurfaceHolder holder) {
        mHolder = holder;
        mGlHandler.post(new Runnable() {
				@Override
				public void run() {
					stopRenderLoop();
					if (mGlRenderer == null) return;

					if (holder != null) {
						// Reusar el GlRenderer existente: solo swap de EGLSurface.
						// Evita destruir/recrear el contexto GL y recompilar shaders.
						destroyAllModuleShaders();
						mGlRenderer.setSurface(holder);
						mGlReady = true;
						Log.d(TAG, "GlRenderer: surface actualizada sin recrear contexto");
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
        Log.d(TAG, "Todos los shaders de módulos invalidados (contexto GL viejo)");
    }

    public void release() {
        mGlHandler.post(new Runnable() {
				@Override
				public void run() {
					stopRenderLoop();
					if (mGlRenderer != null) {
						destroyAllModuleShaders();
						mGlRenderer.release();
						mGlRenderer = null;
					}
					mGlReady = false;
				}
			});
        mGlThread.quitSafely();
    }
}

