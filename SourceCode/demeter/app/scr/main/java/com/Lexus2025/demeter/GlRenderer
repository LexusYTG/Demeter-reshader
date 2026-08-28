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
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;
import android.view.SurfaceHolder;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Map;

/**
 * GlRenderer con soporte de render loop para shaders animados.
 *
 * SEPARACIÓN upload / draw:
 *   - uploadBitmap(Bitmap)  — sube el bitmap a la textura GL y lo recicla.
 *                             Llamar cuando llega un frame nuevo de captura.
 *   - drawFrame(shader, params) — dibuja la textura que ya está en VRAM.
 *                             Llamar en cada tick del render loop (no recibe Bitmap).
 *
 * La firma original drawFrame(Bitmap, ShaderFilter, Map) se mantiene como
 * conveniencia para el path sin animación (upload + draw en una sola llamada).
 *
 * Esto evita re-subir la textura a VRAM en cada tick del loop —
 * GLUtils.texImage2D() es la operación costosa; el draw puro es barato.
 */
public class GlRenderer {
    private static final String TAG = "GlRenderer";

    private static final String PASSTHROUGH_VERTEX =
	"attribute vec4 aPosition;\n" +
	"attribute vec2 aTexCoord;\n" +
	"varying vec2 vTexCoord;\n" +
	"void main() {\n" +
	"  gl_Position = aPosition;\n" +
	"  vTexCoord = aTexCoord;\n" +
	"}\n";

    private static final String PASSTHROUGH_FRAGMENT =
	"precision mediump float;\n" +
	"varying vec2 vTexCoord;\n" +
	"uniform sampler2D uTexture;\n" +
	"void main() {\n" +
	"  gl_FragColor = texture2D(uTexture, vTexCoord);\n" +
	"}\n";

    private EGL10      mEgl;
    private EGLDisplay mEglDisplay = EGL10.EGL_NO_DISPLAY;
    private EGLContext mEglContext = EGL10.EGL_NO_CONTEXT;
    private EGLSurface mEglSurface = EGL10.EGL_NO_SURFACE;
    private EGLConfig  mEglConfig;
    private boolean    mInitialized = false;

    // true si la textura tiene contenido válido (al menos un uploadBitmap exitoso).
    private boolean mTextureReady = false;

    // Tamaño de la surface cacheado — se actualiza en init() y setSurface().
    // Evita llamar eglQuerySurface en cada frame.
    private int mSurfaceW = 0;
    private int mSurfaceH = 0;

    private int[]        mTexture = new int[1];
    private FloatBuffer  mVertexBuffer;
    private FloatBuffer  mTexCoordBuffer;
    private ShaderFilter mPassthroughShader;

    private static final float[] VERTICES   = { -1f, -1f,  1f, -1f, -1f,  1f,  1f,  1f };
    private static final float[] TEX_COORDS = {  0f,  1f,  1f,  1f,  0f,  0f,  1f,  0f };

    public GlRenderer() {
        mVertexBuffer = ByteBuffer.allocateDirect(VERTICES.length * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mVertexBuffer.put(VERTICES).position(0);
        mTexCoordBuffer = ByteBuffer.allocateDirect(TEX_COORDS.length * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTexCoordBuffer.put(TEX_COORDS).position(0);
    }

    public boolean init(SurfaceHolder holder) {
        try {
            mEgl = (EGL10) EGLContext.getEGL();
            mEglDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            int[] version = new int[2];
            if (!mEgl.eglInitialize(mEglDisplay, version)) return false;

            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs    = new int[1];
            int[] attribs = {
                EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 0, EGL10.EGL_NONE
            };
            if (!mEgl.eglChooseConfig(mEglDisplay, attribs, configs, 1, numConfigs))
                return false;
            mEglConfig = configs[0];

            int[] ctxAttribs = { 0x3098, 2, EGL10.EGL_NONE };
            mEglContext = mEgl.eglCreateContext(
                mEglDisplay, mEglConfig, EGL10.EGL_NO_CONTEXT, ctxAttribs);
            if (mEglContext == null || mEglContext == EGL10.EGL_NO_CONTEXT) return false;

            mEglSurface = mEgl.eglCreateWindowSurface(
                mEglDisplay, mEglConfig, holder.getSurface(), null);
            if (mEglSurface == null || mEglSurface == EGL10.EGL_NO_SURFACE) return false;

            if (!mEgl.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext))
                return false;

            GLES20.glGenTextures(1, mTexture, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexture[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            mPassthroughShader = new ShaderFilter(
                PASSTHROUGH_VERTEX, PASSTHROUGH_FRAGMENT, null);

            // Cachear dimensiones iniciales de la surface
            int[] wArr = new int[1], hArr = new int[1];
            mEgl.eglQuerySurface(mEglDisplay, mEglSurface, EGL10.EGL_WIDTH,  wArr);
            mEgl.eglQuerySurface(mEglDisplay, mEglSurface, EGL10.EGL_HEIGHT, hArr);
            mSurfaceW = wArr[0];
            mSurfaceH = hArr[0];

            mTextureReady = false;
            mInitialized = true;
            Log.d(TAG, "GL initialized successfully (" + mSurfaceW + "x" + mSurfaceH + ")");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "GL init error", e);
            return false;
        }
    }

    /**
     * Sube un bitmap a la textura GL y lo recicla inmediatamente.
     * Llamar cada vez que llega un frame nuevo de captura.
     * DEBE ejecutarse en el hilo GL.
     *
     * @return false si el renderer no está listo o el bitmap es inválido.
     */
    public synchronized boolean uploadBitmap(Bitmap bitmap) {
        if (!mInitialized || bitmap == null || bitmap.isRecycled()) return false;

        // No llamamos eglMakeCurrent aquí: todo ocurre en mGlThread donde
        // el contexto ya fue activado en init(). Llamarlo de nuevo es un
        // round-trip al driver innecesario.
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexture[0]);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        bitmap.recycle();

        mTextureReady = true;
        return true;
    }

    /**
     * Dibuja la textura que ya está en VRAM aplicando el shader y sus params.
     * No toca ningún Bitmap — úsalo en el render loop para animar sin re-subir.
     * DEBE ejecutarse en el hilo GL.
     *
     * @return false si no hay textura válida o el renderer no está listo.
     */
    public synchronized boolean drawFrame(ShaderFilter shader, Map<String, Float> params) {
        if (!mInitialized || !mTextureReady) return false;

        // eglMakeCurrent omitido: contexto ya activo en mGlThread desde init().
        // mSurfaceW/H cacheados en init() y actualizados en setSurface() —
        // sin round-trip al driver por frame.
        GLES20.glViewport(0, 0, mSurfaceW, mSurfaceH);
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        ShaderFilter active = (shader != null) ? shader : mPassthroughShader;
        if (active != null) {
            active.draw(mTexture[0], mVertexBuffer, mTexCoordBuffer, params);
        }

        return mEgl.eglSwapBuffers(mEglDisplay, mEglSurface);
    }

    /**
     * Conveniencia para el path sin animación: upload + draw en una sola llamada.
     * Equivale a uploadBitmap(frame) + drawFrame(shader, params).
     * El bitmap se recicla dentro de uploadBitmap.
     *
     * @deprecated Preferir uploadBitmap() + drawFrame() por separado cuando
     *             hay render loop activo. Este método se mantiene para
     *             compatibilidad con código existente que no usa el loop.
     */
    public synchronized boolean drawFrame(Bitmap frame, ShaderFilter shader,
                                          Map<String, Float> params) {
        if (!uploadBitmap(frame)) return false;
        return drawFrame(shader, params);
    }

    /**
     * Actualiza el EGLSurface y cachea las nuevas dimensiones.
     * Llamar desde TargetRenderer.setHolder() tras recrear la surface.
     * DEBE ejecutarse en el hilo GL con el contexto activo.
     */
    public synchronized void setSurface(SurfaceHolder holder) {
        if (!mInitialized) return;
        // Destruir surface vieja
        if (mEglSurface != EGL10.EGL_NO_SURFACE) {
            mEgl.eglMakeCurrent(mEglDisplay,
								EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
            mEgl.eglDestroySurface(mEglDisplay, mEglSurface);
            mEglSurface = EGL10.EGL_NO_SURFACE;
        }
        if (holder == null) {
            mSurfaceW = 0; mSurfaceH = 0;
            mTextureReady = false;
            return;
        }
        mEglSurface = mEgl.eglCreateWindowSurface(
            mEglDisplay, mEglConfig, holder.getSurface(), null);
        if (mEglSurface == null || mEglSurface == EGL10.EGL_NO_SURFACE) {
            Log.e(TAG, "setSurface: eglCreateWindowSurface falló");
            mSurfaceW = 0; mSurfaceH = 0;
            return;
        }
        mEgl.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext);
        int[] wArr = new int[1], hArr = new int[1];
        mEgl.eglQuerySurface(mEglDisplay, mEglSurface, EGL10.EGL_WIDTH,  wArr);
        mEgl.eglQuerySurface(mEglDisplay, mEglSurface, EGL10.EGL_HEIGHT, hArr);
        mSurfaceW = wArr[0];
        mSurfaceH = hArr[0];
        Log.d(TAG, "setSurface: nueva surface " + mSurfaceW + "x" + mSurfaceH);
    }

    public void release() {
        if (!mInitialized) return;
        mEgl.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext);
        if (mPassthroughShader != null) {
            mPassthroughShader.destroy();
            mPassthroughShader = null;
        }
        GLES20.glDeleteTextures(1, mTexture, 0);
        mEgl.eglMakeCurrent(mEglDisplay,
							EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
        if (mEglSurface != EGL10.EGL_NO_SURFACE)
            mEgl.eglDestroySurface(mEglDisplay, mEglSurface);
        if (mEglContext != EGL10.EGL_NO_CONTEXT)
            mEgl.eglDestroyContext(mEglDisplay, mEglContext);
        mEgl.eglTerminate(mEglDisplay);
        mEglDisplay = EGL10.EGL_NO_DISPLAY;
        mEglContext = EGL10.EGL_NO_CONTEXT;
        mEglSurface = EGL10.EGL_NO_SURFACE;
        mTextureReady = false;
        mInitialized = false;
        Log.d(TAG, "GL released");
    }
}

