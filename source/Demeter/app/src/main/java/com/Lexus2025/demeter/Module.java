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

import java.util.HashMap;
import java.util.Map;

public class Module {

    public enum Type { MODIFIER, RENDERER, FRAMEGEN, FRAMEGEN_G3 }

    public static class ParamDef {
        public final String label;
        public final float  min;
        public final float  max;
        public final float  defaultValue;

        public ParamDef(String label, float min, float max, float defaultValue) {
            this.label        = label;
            this.min          = min;
            this.max          = max;
            this.defaultValue = defaultValue;
        }
    }

    private final String mName;
    private final String mAuthor;
    private final String mVersion;
    private final Type   mType;
    private final String mVertexShader;
    private final String mFragmentShader;
    private final Map<String, Float>    mParams;
    private final Map<String, Module.ParamDef> mParamDefs;
    private boolean mEnabled;
    private ShaderFilter mShaderFilter;
    private String mCompilationError;

    private volatile String mCachedJson;

    public Module(String name, String author, String version, Type type,
                  String vertexShader, String fragmentShader,
                  Map<String, Float> params, Map<String, ParamDef> paramDefs) {
        mName           = name;
        mAuthor         = author;
        mVersion        = version;
        mType           = type;
        mVertexShader   = vertexShader;
        mFragmentShader = fragmentShader;
        mParams         = params    != null ? params    : new HashMap<String, Float>();
        mParamDefs      = paramDefs != null ? paramDefs : new HashMap<String, ParamDef>();
        mEnabled        = false;
        mCompilationError = null;
        mCachedJson     = null;
    }

    public String getName()           { return mName; }
    public String getAuthor()         { return mAuthor; }
    public String getVersion()        { return mVersion; }
    public Type   getType()           { return mType; }
    public String getVertexShader()   { return mVertexShader; }
    public String getFragmentShader() { return mFragmentShader; }
    public Map<String, Float>    getParams()    { return mParams; }
    public Map<String, Module.ParamDef> getParamDefs() { return mParamDefs; }
    public boolean isEnabled()        { return mEnabled; }

    public void setEnabled(boolean enabled) {
        if (mEnabled == enabled) return;
        mEnabled = enabled;
        mCachedJson = null;
    }

    public void setParamValue(String uniformName, float value) {
        if (!mParamDefs.containsKey(uniformName)) return;
        Float old = mParams.get(uniformName);
        if (old != null && Math.abs(old - value) < 0.0001f) return;
        mParams.put(uniformName, value);
        mCachedJson = null;
    }

    public String getCachedJson()             { return mCachedJson; }
    public void   setCachedJson(String json)  { mCachedJson = json; }

    public ShaderFilter getShaderFilter() {
        if (mShaderFilter == null && mVertexShader != null && mFragmentShader != null) {
            try {
                mShaderFilter = new ShaderFilter(mVertexShader, mFragmentShader, mParams);
                mCompilationError = null;
            } catch (RuntimeException e) {
                mCompilationError = e.getMessage();
                setEnabled(false);
                android.util.Log.e("Module",
                                   "Error compilando shader para " + mName + ": " + mCompilationError);
            }
        }
        return mShaderFilter;
    }

    public String getCompilationError() { return mCompilationError; }

    public void destroyShader() {
        if (mShaderFilter != null) {
            mShaderFilter.destroy();
            mShaderFilter = null;
        }
    }

    @Override
    public String toString() {
        return mName + " v" + mVersion + (mEnabled ? " [ON]" : " [OFF]");
    }
}
