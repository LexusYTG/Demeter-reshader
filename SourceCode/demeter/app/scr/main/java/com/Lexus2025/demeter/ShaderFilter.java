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

import android.opengl.GLES20;
import android.util.Log;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ShaderFilter {
    private static final String TAG = "ShaderFilter";

    private int mProgram;
    private int mPositionHandle;
    private int mTexCoordHandle;
    private int mTextureHandle;
    private Map<String, Integer> mUniformLocations;

    private static final int FLOAT_SIZE_BYTES = 4;

    public ShaderFilter(String vertexSource, String fragmentSource, Map<String, Float> params) {
        mUniformLocations = new HashMap<String, Integer>();
        mProgram = createProgram(vertexSource, fragmentSource);

        if (mProgram == 0) {
            throw new RuntimeException("Failed to create program");
        }

        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
        mTexCoordHandle = GLES20.glGetAttribLocation(mProgram, "aTexCoord");
        mTextureHandle = GLES20.glGetUniformLocation(mProgram, "uTexture");

        // Guardar ubicaciones de uniforms personalizados (uChaos, etc.)
        if (params != null) {
            Iterator<Map.Entry<String, Float>> it = params.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Float> entry = it.next();
                int loc = GLES20.glGetUniformLocation(mProgram, entry.getKey());
                if (loc >= 0) {
                    mUniformLocations.put(entry.getKey(), loc);
                }
            }
        }
    }

    public void draw(int textureId, FloatBuffer vertexBuffer, FloatBuffer texCoordBuffer, Map<String, Float> params) {
        GLES20.glUseProgram(mProgram);

        // Configurar vértices
        vertexBuffer.position(0);
        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glVertexAttribPointer(mPositionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        // Configurar coordenadas de textura
        texCoordBuffer.position(0);
        GLES20.glEnableVertexAttribArray(mTexCoordHandle);
        GLES20.glVertexAttribPointer(mTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer);

        // Configurar textura
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(mTextureHandle, 0);

        // Configurar uniforms personalizados
        if (params != null) {
            Iterator<Map.Entry<String, Float>> it = params.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Float> entry = it.next();
                Integer loc = mUniformLocations.get(entry.getKey());
                if (loc != null && loc >= 0) {
                    GLES20.glUniform1f(loc, entry.getValue());
                }
            }
        }

        // Dibujar
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        // Cleanup
        GLES20.glDisableVertexAttribArray(mPositionHandle);
        GLES20.glDisableVertexAttribArray(mTexCoordHandle);
    }

    public void destroy() {
        if (mProgram != 0) {
            GLES20.glDeleteProgram(mProgram);
            mProgram = 0;
        }
    }

    private int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) return 0;

        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (fragmentShader == 0) {
            GLES20.glDeleteShader(vertexShader);
            return 0;
        }

        int program = GLES20.glCreateProgram();
        if (program != 0) {
            GLES20.glAttachShader(program, vertexShader);
            GLES20.glAttachShader(program, fragmentShader);
            GLES20.glLinkProgram(program);

            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] == GLES20.GL_FALSE) {
                String error = GLES20.glGetProgramInfoLog(program);
                GLES20.glDeleteProgram(program);
                program = 0;
                throw new RuntimeException("Program link failed: " + error);
            }
        }

        return program;
    }

    private int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader != 0) {
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);

            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == GLES20.GL_FALSE) {
                String error = GLES20.glGetShaderInfoLog(shader);
                GLES20.glDeleteShader(shader);
                shader = 0;
                throw new RuntimeException("Shader compile failed: " + error);
            }
        }

        return shader;
    }
}
