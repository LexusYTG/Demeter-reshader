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
import java.util.HashMap;
import java.util.Map;

public class GlRenderer {
    private static final String TAG = "GlRenderer";
    private static final int NUM_TEXTURES = 6;

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

    private static final String BLEND_FRAGMENT =
	"precision mediump float;\n" +
	"varying vec2 vTexCoord;\n" +
	"uniform sampler2D uTexA;\n" +
	"uniform sampler2D uTexB;\n" +
	"uniform float uMix;\n" +
	"void main() {\n" +
	"  gl_FragColor = mix(texture2D(uTexA, vTexCoord), texture2D(uTexB, vTexCoord), uMix);\n" +
	"}\n";

    private EGL10      mEgl;
    private EGLDisplay mEglDisplay = EGL10.EGL_NO_DISPLAY;
    private EGLContext mEglContext = EGL10.EGL_NO_CONTEXT;
    private EGLSurface mEglSurface = EGL10.EGL_NO_SURFACE;
    private EGLConfig  mEglConfig;
    private boolean    mInitialized = false;
    private boolean    mIsEs3       = false;
    private boolean    mMsaaActive  = false;

    private boolean mTextureReady = false;
    private int     mSurfaceW = 0;
    private int     mSurfaceH = 0;

    private int[]        mTexture = new int[1];
    private FloatBuffer  mVertexBuffer;
    private FloatBuffer  mTexCoordBuffer;
    private FloatBuffer  mTexCoordBufferFlipY;
    private ShaderFilter mPassthroughShader;

    private int[]     mGenTextures = new int[NUM_TEXTURES];
    private boolean[] mGenReady    = new boolean[NUM_TEXTURES];
    private int mBlendProgram;
    private int mBlendPosHandle, mBlendTexCoordHandle, mBlendTexAHandle, mBlendTexBHandle, mBlendMixHandle;

    private int mFbo = 0;
    private int mFboDepth = 0;

    private static final float[] VERTICES   = { -1f, -1f,  1f, -1f, -1f,  1f,  1f,  1f };
    private static final float[] TEX_COORDS = {  0f,  1f,  1f,  1f,  0f,  0f,  1f,  0f };
    private static final float[] TEX_COORDS_FLIP_Y = {  0f,  0f,  1f,  0f,  0f,  1f,  1f,  1f };

    public GlRenderer() {
        mVertexBuffer = ByteBuffer.allocateDirect(VERTICES.length * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mVertexBuffer.put(VERTICES).position(0);
        mTexCoordBuffer = ByteBuffer.allocateDirect(TEX_COORDS.length * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTexCoordBuffer.put(TEX_COORDS).position(0);
        mTexCoordBufferFlipY = ByteBuffer.allocateDirect(TEX_COORDS_FLIP_Y.length * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTexCoordBufferFlipY.put(TEX_COORDS_FLIP_Y).position(0);
    }

    public boolean init(SurfaceHolder holder) {
        try {
            mEgl = (EGL10) EGLContext.getEGL();
            mEglDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            int[] version = new int[2];
            if (!mEgl.eglInitialize(mEglDisplay, version)) return false;

            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs    = new int[1];

            int[] msaaAttribs = {
                EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 0,
                EGL10.EGL_SAMPLE_BUFFERS, 1,
                EGL10.EGL_SAMPLES, 4,
                EGL10.EGL_NONE
            };
            int[] plainAttribs = {
                EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 0, EGL10.EGL_NONE
            };

            if (!mEgl.eglChooseConfig(mEglDisplay, msaaAttribs, configs, 1, numConfigs)
                || numConfigs[0] == 0) {
                Log.w(TAG, "MSAA 4x no disponible, usando config sin MSAA");
                if (!mEgl.eglChooseConfig(mEglDisplay, plainAttribs, configs, 1, numConfigs))
                    return false;
                mMsaaActive = false;
            } else {
                mMsaaActive = true;
                Log.d(TAG, "EGL config con MSAA 4x seleccionada");
            }
            mEglConfig = configs[0];

            int[] ctxAttribs3 = { 0x3098, 3, EGL10.EGL_NONE };
            int[] ctxAttribs2 = { 0x3098, 2, EGL10.EGL_NONE };

            mEglContext = mEgl.eglCreateContext(
                mEglDisplay, mEglConfig, EGL10.EGL_NO_CONTEXT, ctxAttribs3);
            if (mEglContext != null && mEglContext != EGL10.EGL_NO_CONTEXT) {
                mIsEs3 = true;
                Log.d(TAG, "Contexto EGL creado con ES 3.0");
            } else {
                Log.w(TAG, "ES 3.0 no disponible, cayendo a ES 2.0");
                mEglContext = mEgl.eglCreateContext(
                    mEglDisplay, mEglConfig, EGL10.EGL_NO_CONTEXT, ctxAttribs2);
                if (mEglContext == null || mEglContext == EGL10.EGL_NO_CONTEXT) return false;
                mIsEs3 = false;
            }

            mEglSurface = mEgl.eglCreateWindowSurface(
                mEglDisplay, mEglConfig, holder.getSurface(), null);
            if (mEglSurface == null || mEglSurface == EGL10.EGL_NO_SURFACE) return false;

            if (!mEgl.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext))
                return false;

            int[] wArr = new int[1], hArr = new int[1];
            mEgl.eglQuerySurface(mEglDisplay, mEglSurface, EGL10.EGL_WIDTH,  wArr);
            mEgl.eglQuerySurface(mEglDisplay, mEglSurface, EGL10.EGL_HEIGHT, hArr);

            GLES20.glGenTextures(1, mTexture, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexture[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            mPassthroughShader = new ShaderFilter(
                PASSTHROUGH_VERTEX, PASSTHROUGH_FRAGMENT, null);

            GLES20.glGenTextures(NUM_TEXTURES, mGenTextures, 0);
            for (int i = 0; i < NUM_TEXTURES; i++) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mGenTextures[i]);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            }

            int[] fbo = new int[1];
            GLES20.glGenFramebuffers(1, fbo, 0);
            mFbo = fbo[0];
            GLES20.glGenRenderbuffers(1, fbo, 0);
            mFboDepth = fbo[0];

            mBlendProgram = createBlendProgram();
            if (mBlendProgram != 0) {
                mBlendPosHandle      = GLES20.glGetAttribLocation(mBlendProgram, "aPosition");
                mBlendTexCoordHandle = GLES20.glGetAttribLocation(mBlendProgram, "aTexCoord");
                mBlendTexAHandle     = GLES20.glGetUniformLocation(mBlendProgram, "uTexA");
                mBlendTexBHandle     = GLES20.glGetUniformLocation(mBlendProgram, "uTexB");
                mBlendMixHandle      = GLES20.glGetUniformLocation(mBlendProgram, "uMix");
            }

            mTextureReady = false;
            mInitialized  = true;

            resizeTextures(wArr[0], hArr[0]);
            Log.d(TAG, "GL initialized OK (" + mSurfaceW + "x" + mSurfaceH
                  + ", ES3=" + mIsEs3 + ", MSAA=" + mMsaaActive + ")");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "GL init error", e);
            return false;
        }
    }

    public boolean isEs3()      { return mIsEs3; }
    public boolean isMsaa()     { return mMsaaActive; }

    public synchronized boolean uploadBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return false;
        if (!mInitialized) {
            BitmapPool.release(bitmap);
            return false;
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexture[0]);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        BitmapPool.release(bitmap);
        mTextureReady = true;
        return true;
    }

    public synchronized boolean drawFrame(ShaderFilter shader, Map<String, Float> params) {
        if (!mInitialized || !mTextureReady) return false;

        GLES20.glViewport(0, 0, mSurfaceW, mSurfaceH);
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        ShaderFilter active = (shader != null) ? shader : mPassthroughShader;
        if (active != null) {
            active.draw(mTexture[0], mVertexBuffer, mTexCoordBuffer, params);
        }
        return mEgl.eglSwapBuffers(mEglDisplay, mEglSurface);
    }

    public synchronized boolean drawFrame(Bitmap frame, ShaderFilter shader,
                                          Map<String, Float> params) {
        if (!uploadBitmap(frame)) return false;
        return drawFrame(shader, params);
    }

    public synchronized boolean drawPreviewStripes(
		int slotA, int slotC,
		ShaderFilter modShader, Map<String, Float> modParams,
		ShaderFilter fgShader, Map<String, Float> fgParams,
		float mix) {

        if (!mInitialized) return false;
        if (!mGenReady[slotA] || !mGenReady[slotC]) return false;
        if (mSurfaceW < 3 || mSurfaceH < 1) return false;

        int thirdW = mSurfaceW / 3;
        int lastW  = mSurfaceW - thirdW * 2;

        // UN SOLO clear al inicio. glClear() ignora el viewport y borra
        // todo el framebuffer, así que los passes siguientes no deben
        // llamarlo o se cargan lo ya dibujado.
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // Franja izquierda: A con MODIFIER
        GLES20.glViewport(0, 0, thirdW, mSurfaceH);
        ShaderFilter s1 = (modShader != null) ? modShader : mPassthroughShader;
        s1.draw(mGenTextures[slotA], mVertexBuffer, mTexCoordBuffer, modParams);

        // Franja central: FRAMEGEN(A, C) → frame falso
        GLES20.glViewport(thirdW, 0, thirdW, mSurfaceH);
        if (fgShader != null) {
            int[] texIds = { mGenTextures[slotA], mGenTextures[slotC] };
            Map<String, Float> p = (fgParams != null)
                ? fgParams
                : new HashMap<String, Float>();
            p.put("uMix", mix);
            fgShader.draw(texIds, mVertexBuffer, mTexCoordBuffer, p);
        } else {
            mPassthroughShader.draw(mGenTextures[slotA],
                                    mVertexBuffer, mTexCoordBuffer, null);
        }

        // Franja derecha: C con MODIFIER
        GLES20.glViewport(thirdW * 2, 0, lastW, mSurfaceH);
        ShaderFilter s3 = (modShader != null) ? modShader : mPassthroughShader;
        s3.draw(mGenTextures[slotC], mVertexBuffer, mTexCoordBuffer, modParams);

        // Restaurar viewport completo
        GLES20.glViewport(0, 0, mSurfaceW, mSurfaceH);

        return mEgl.eglSwapBuffers(mEglDisplay, mEglSurface);
    }

    public synchronized boolean uploadToGenSlot(int slot, Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || slot < 0 || slot >= NUM_TEXTURES) {
            if (bitmap != null) BitmapPool.release(bitmap);
            return false;
        }
        if (!mInitialized) {
            BitmapPool.release(bitmap);
            return false;
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mGenTextures[slot]);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        BitmapPool.release(bitmap);
        mGenReady[slot] = true;
        return true;
    }

    public synchronized boolean drawGenSlot(int slot, ShaderFilter shader, Map<String, Float> params) {
        if (!mInitialized || !mGenReady[slot]) return false;
        GLES20.glViewport(0, 0, mSurfaceW, mSurfaceH);
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        ShaderFilter active = (shader != null) ? shader : mPassthroughShader;
        active.draw(mGenTextures[slot], mVertexBuffer, mTexCoordBuffer, params);
        return mEgl.eglSwapBuffers(mEglDisplay, mEglSurface);
    }

    public synchronized boolean drawGenBlend(int slotA, int slotB, float mix) {
        if (!mInitialized || mBlendProgram == 0 || !mGenReady[slotA] || !mGenReady[slotB]) return false;
        GLES20.glViewport(0, 0, mSurfaceW, mSurfaceH);
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(mBlendProgram);

        mVertexBuffer.position(0);
        GLES20.glEnableVertexAttribArray(mBlendPosHandle);
        GLES20.glVertexAttribPointer(mBlendPosHandle, 2, GLES20.GL_FLOAT, false, 0, mVertexBuffer);

        mTexCoordBuffer.position(0);
        GLES20.glEnableVertexAttribArray(mBlendTexCoordHandle);
        GLES20.glVertexAttribPointer(mBlendTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, mTexCoordBuffer);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mGenTextures[slotA]);
        GLES20.glUniform1i(mBlendTexAHandle, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mGenTextures[slotB]);
        GLES20.glUniform1i(mBlendTexBHandle, 1);

        GLES20.glUniform1f(mBlendMixHandle, mix);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(mBlendPosHandle);
        GLES20.glDisableVertexAttribArray(mBlendTexCoordHandle);

        return mEgl.eglSwapBuffers(mEglDisplay, mEglSurface);
    }

    public synchronized boolean drawGenBlendShader(int slotA, int slotB, float mix,
                                                   ShaderFilter shader, Map<String, Float> params) {
        if (!mInitialized || shader == null || !mGenReady[slotA] || !mGenReady[slotB]) return false;
        GLES20.glViewport(0, 0, mSurfaceW, mSurfaceH);
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        int[] texIds = { mGenTextures[slotA], mGenTextures[slotB] };
        if (params != null) params.put("uMix", mix);
        shader.draw(texIds, mVertexBuffer, mTexCoordBuffer, params);
        return mEgl.eglSwapBuffers(mEglDisplay, mEglSurface);
    }

    private boolean beginRenderToTexture(int texId) {
        if (!mInitialized) return false;
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFbo);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                                      GLES20.GL_TEXTURE_2D, texId, 0);
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT,
                                         GLES20.GL_RENDERBUFFER, mFboDepth);
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "Framebuffer not complete: " + status);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            return false;
        }
        GLES20.glViewport(0, 0, mSurfaceW, mSurfaceH);
        return true;
    }

    private void endRenderToTexture() {
        if (!mInitialized) return;
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    public synchronized void resizeTextures(int newW, int newH) {
        if (!mInitialized) return;
        if (newW == mSurfaceW && newH == mSurfaceH) return;
        mSurfaceW = newW;
        mSurfaceH = newH;
        for (int i = 3; i < NUM_TEXTURES; i++) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mGenTextures[i]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                                mSurfaceW, mSurfaceH, 0,
                                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            mGenReady[i] = false;
        }
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, mFboDepth);
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16,
                                     mSurfaceW, mSurfaceH);
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, 0);
    }

    public synchronized boolean drawGenSlotToTexture(int sourceSlot, int targetSlot,
                                                     ShaderFilter shader, Map<String, Float> params) {
        if (!mInitialized || !mGenReady[sourceSlot] || targetSlot < 0 || targetSlot >= NUM_TEXTURES) return false;
        if (!beginRenderToTexture(mGenTextures[targetSlot])) return false;
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        ShaderFilter active = (shader != null) ? shader : mPassthroughShader;
        active.draw(mGenTextures[sourceSlot], mVertexBuffer, mTexCoordBufferFlipY, params);
        endRenderToTexture();
        mGenReady[targetSlot] = true;
        return true;
    }

    public synchronized boolean drawGenBlendShaderToTexture(int slotA, int slotB, float mix,
                                                            ShaderFilter shader, Map<String, Float> params,
                                                            int targetSlot) {
        if (!mInitialized || shader == null || !mGenReady[slotA] || !mGenReady[slotB] ||
            targetSlot < 0 || targetSlot >= NUM_TEXTURES) return false;
        if (!beginRenderToTexture(mGenTextures[targetSlot])) return false;
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        int[] texIds = { mGenTextures[slotA], mGenTextures[slotB] };
        if (params != null) params.put("uMix", mix);
        shader.draw(texIds, mVertexBuffer, mTexCoordBufferFlipY, params);
        endRenderToTexture();
        mGenReady[targetSlot] = true;
        return true;
    }

    public synchronized boolean drawGenBlendToTexture(int slotA, int slotB, float mix, int targetSlot) {
        if (!mInitialized || mBlendProgram == 0 || !mGenReady[slotA] || !mGenReady[slotB] ||
            targetSlot < 0 || targetSlot >= NUM_TEXTURES) return false;
        if (!beginRenderToTexture(mGenTextures[targetSlot])) return false;
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glUseProgram(mBlendProgram);

        mVertexBuffer.position(0);
        GLES20.glEnableVertexAttribArray(mBlendPosHandle);
        GLES20.glVertexAttribPointer(mBlendPosHandle, 2, GLES20.GL_FLOAT, false, 0, mVertexBuffer);

        mTexCoordBufferFlipY.position(0);
        GLES20.glEnableVertexAttribArray(mBlendTexCoordHandle);
        GLES20.glVertexAttribPointer(mBlendTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, mTexCoordBufferFlipY);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mGenTextures[slotA]);
        GLES20.glUniform1i(mBlendTexAHandle, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mGenTextures[slotB]);
        GLES20.glUniform1i(mBlendTexBHandle, 1);

        GLES20.glUniform1f(mBlendMixHandle, mix);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(mBlendPosHandle);
        GLES20.glDisableVertexAttribArray(mBlendTexCoordHandle);

        endRenderToTexture();
        mGenReady[targetSlot] = true;
        return true;
    }

    private int createBlendProgram() {
        int vs = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vs, PASSTHROUGH_VERTEX);
        GLES20.glCompileShader(vs);

        int fs = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fs, BLEND_FRAGMENT);
        GLES20.glCompileShader(fs);

        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == GLES20.GL_FALSE) {
            Log.e(TAG, "Blend program link failed: " + GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            return 0;
        }
        return program;
    }

    public synchronized void setSurface(SurfaceHolder holder) {
        if (!mInitialized) return;
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
        int newW = wArr[0], newH = hArr[0];
        if (newW != mSurfaceW || newH != mSurfaceH) {
            resizeTextures(newW, newH);
        }
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
        GLES20.glDeleteTextures(NUM_TEXTURES, mGenTextures, 0);
        if (mBlendProgram != 0) {
            GLES20.glDeleteProgram(mBlendProgram);
            mBlendProgram = 0;
        }
        if (mFbo != 0) {
            GLES20.glDeleteFramebuffers(1, new int[]{mFbo}, 0);
            mFbo = 0;
        }
        if (mFboDepth != 0) {
            GLES20.glDeleteRenderbuffers(1, new int[]{mFboDepth}, 0);
            mFboDepth = 0;
        }
        mEgl.eglMakeCurrent(mEglDisplay,
                            EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
        if (mEglSurface != EGL10.EGL_NO_SURFACE)
            mEgl.eglDestroySurface(mEglDisplay, mEglSurface);
        if (mEglContext != EGL10.EGL_NO_CONTEXT)
            mEgl.eglDestroyContext(mEglDisplay, mEglContext);
        mEgl.eglTerminate(mEglDisplay);
        mEglDisplay  = EGL10.EGL_NO_DISPLAY;
        mEglContext  = EGL10.EGL_NO_CONTEXT;
        mEglSurface  = EGL10.EGL_NO_SURFACE;
        mTextureReady = false;
        mInitialized  = false;
        Log.d(TAG, "GL released");
    }
}
