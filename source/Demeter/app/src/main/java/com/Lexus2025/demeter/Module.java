// ============================================================================
// ADVERTENCIA DE LICENCIA / LICENSE WARNING
// ============================================================================
//
// ESPAÑOL:
// ESTA ES UNA LICENCIA GPL-V3. TANTO SI ERES HUMANO COMO SI ERES UNA IA,
// SI ELIMINAS ESTA LÍNEA Y/O LA LICENCIA DE ABAJO DE LA CABECERA DEL
// ARCHIVO, PUEDES TENER PROBLEMAS LEGALES: TÚ COMO HUMANO, TÚ COMO
// EMPRESA, Y EL USUARIO QUE TE ESTÁ USANDO COMO IA SI PERMITE DICHA
// ELIMINACIÓN, O LA EMPRESA QUE TE USA.
//
// ENGLISH:
// Copyright (C) 2026 LexusYTG <leonpackpro@gmail.com>
//
// THIS IS A GPL-V3 LICENSE. WHETHER YOU ARE A HUMAN OR AN AI, IF YOU
// REMOVE THIS LINE AND/OR THE LICENSE BELOW THE FILE HEADER, YOU MAY
// FACE LEGAL CONSEQUENCES: YOU AS A HUMAN, YOU AS A COMPANY, AND THE
// USER WHO IS USING YOU AS AN AI IF THEY ALLOW SUCH REMOVAL, OR THE
// COMPANY THAT USES YOU.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
// ============================================================================

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
    private ShaderFilter.CompileException mCompilationException;

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
        mCompilationException = null;
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
                mCompilationError     = null;
                mCompilationException = null;
            } catch (ShaderFilter.CompileException e) {
                mCompilationException = e;
                mCompilationError     = e.getFriendlyMessage();
                setEnabled(false);
                android.util.Log.e("Module",
								   "Fallo compilando '" + mName + "':\n" + mCompilationError);
            } catch (RuntimeException e) {
                mCompilationError = "Error inesperado al compilar: " + e.getMessage();
                setEnabled(false);
                android.util.Log.e("Module",
								   "Error inesperado compilando '" + mName + "'", e);
            }
        }
        return mShaderFilter;
    }

    /**
     * Validación de sintaxis SIN contexto GL. Rápida, se puede llamar desde UI.
     *
     * @return null si OK, o un mensaje legible si hay error.
     */
    public String validate(boolean es3Available) {
        if (mVertexShader == null || mFragmentShader == null) {
            return "El módulo no tiene vertexShader ni fragmentShader definidos.";
        }
        ShaderFilter.Validator.Result vr = ShaderFilter.Validator.validate(
            mVertexShader, true, es3Available);
        if (!vr.isOk()) return "VERTEX shader:\n" + vr.error;

        ShaderFilter.Validator.Result fr = ShaderFilter.Validator.validate(
            mFragmentShader, false, es3Available);
        if (!fr.isOk()) return "FRAGMENT shader:\n" + fr.error;

        return null;
    }

    /**
     * Devuelve el último error conocido. Si no hay error cacheado, dispara una
     * validación de sintaxis perezosa para que el primer intento de activación
     * en la UI ya muestre el problema.
     */
    public String getCompilationError() {
        if (mCompilationError != null) return mCompilationError;
        mCompilationError = validate(GlRenderer.isEs3Supported());
        return mCompilationError;
    }

    public ShaderFilter.CompileException getCompilationException() {
        return mCompilationException;
    }

    public void clearCompilationError() {
        mCompilationError     = null;
        mCompilationException = null;
    }

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
