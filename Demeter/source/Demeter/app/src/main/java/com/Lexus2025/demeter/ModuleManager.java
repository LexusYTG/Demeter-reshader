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
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ModuleManager {

    private static final String PREFS_NAME = "demeter_modules";
    private static final String KEY_MODULES = "modules";
    private static final String KEY_ACTIVE_MODULE = "active_module";
    private static final String KEY_ACTIVE_FRAMEGEN_MODULE = "active_framegen_module";
    private static final String KEY_CHAIN_ORDER = "chain_order";

    private final Context mContext;
    private final List<Module> mModules = new ArrayList<>();
    private Module mActiveModule = null;
    private Module mActiveFrameGenModule = null;
    private List<String> mChainOrder = new ArrayList<>();

    public ModuleManager(Context context) {
        mContext = context.getApplicationContext();
        load();
    }

    public List<Module> getAll() { return mModules; }
    public List<Module> getByType(Module.Type type) {
        List<Module> result = new ArrayList<>();
        for (Module m : mModules) {
            if (m.getType() == type) result.add(m);
        }
        return result;
    }

    public Module getActiveModule() { return mActiveModule; }

    public void setActiveModule(Module module) {
        mActiveModule = module;
        save();
    }

    public Module getActiveFrameGenModule() { return mActiveFrameGenModule; }

    public void setActiveFrameGenModule(Module module) {
        mActiveFrameGenModule = module;
        save();
    }

    public List<Module> getEnabledChain() {
        List<Module> result = new ArrayList<>();
        for (String name : mChainOrder) {
            Module m = getModuleByName(name);
            if (m != null && m.isEnabled() && m.getType() == Module.Type.MODIFIER) {
                result.add(m);
            }
        }
        for (Module m : mModules) {
            if (m.getType() == Module.Type.MODIFIER && m.isEnabled()
				&& !containsName(result, m.getName())) {
                result.add(m);
            }
        }
        return result;
    }

    public List<String> getChainOrder() {
        return new ArrayList<>(mChainOrder);
    }

    public void setChainOrder(List<String> names) {
        mChainOrder = (names != null) ? new ArrayList<>(names) : new ArrayList<String>();
        save();
    }

    private boolean containsName(List<Module> list, String name) {
        for (Module m : list) {
            if (m.getName().equals(name)) return true;
        }
        return false;
    }

    public void installFromJson(String jsonString) throws Exception {
        JSONObject obj;
        try {
            obj = new JSONObject(jsonString);
        } catch (JSONException e) {
            throw new IllegalArgumentException("El archivo no es un JSON válido: " + e.getMessage());
        }
        installFromJsonObject(obj);
    }

    public void installFromUri(Uri uri) throws Exception {
        String jsonString;
        try {
            jsonString = readUriAsString(uri);
        } catch (Exception e) {
            throw new Exception("Error al leer el archivo: " + e.getMessage());
        }
        JSONObject obj;
        try {
            obj = new JSONObject(jsonString);
        } catch (JSONException e) {
            throw new IllegalArgumentException("El archivo no es un JSON válido: " + e.getMessage());
        }
        installFromJsonObject(obj);
    }

    private void installFromJsonObject(JSONObject obj) throws Exception {
        String name = obj.optString("name", "modulo");
        String author = obj.optString("author", "Desconocido");
        String version = obj.optString("version", "1.0");
        String typeStr = obj.optString("type", "MODIFIER");
        String vertexShader = obj.optString("vertexShader");
        String fragmentShader = obj.optString("fragmentShader");
        JSONObject paramsObj    = obj.optJSONObject("params");
        JSONObject paramDefsObj = obj.optJSONObject("paramDefs");

        if (vertexShader.isEmpty() || fragmentShader.isEmpty()) {
            throw new IllegalArgumentException("El módulo debe contener vertexShader y fragmentShader");
        }

        Module.Type type;
        try {
            type = Module.Type.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            type = Module.Type.MODIFIER;
        }

        Map<String, Module.ParamDef> paramDefs = new HashMap<>();
        if (paramDefsObj != null) {
            Iterator<String> keys = paramDefsObj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                try {
                    JSONObject defObj = paramDefsObj.getJSONObject(key);
                    String label = defObj.optString("label", key);
                    float min    = (float) defObj.optDouble("min", 0.0);
                    float max    = (float) defObj.optDouble("max", 1.0);
                    float def    = (float) defObj.optDouble("default", min);
                    paramDefs.put(key, new Module.ParamDef(label, min, max, def));
                } catch (JSONException e) {  }
            }
        }

        Map<String, Float> params = new HashMap<>();
        for (Map.Entry<String, Module.ParamDef> e : paramDefs.entrySet()) {
            params.put(e.getKey(), e.getValue().defaultValue);
        }
        if (paramsObj != null) {
            Iterator<String> keys = paramsObj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                try {
                    params.put(key, (float) paramsObj.getDouble(key));
                } catch (JSONException e) {  }
            }
        }

        for (Module m : mModules) {
            if (m.getName().equals(name)) return;
        }

        Module module = new Module(name, author, version, type,
								   vertexShader, fragmentShader, params, paramDefs);
        mModules.add(module);
        
        save();
    }

    public void uninstall(Module module) {
        module.destroyShader();
        if (mActiveModule == module) mActiveModule = null;
        if (mActiveFrameGenModule == module) mActiveFrameGenModule = null;
        mModules.remove(module);
        mChainOrder.remove(module.getName());
        save();
    }

    public void setEnabled(Module module, boolean enabled) {
        module.setEnabled(enabled);
        if (!enabled) {
            module.destroyShader();
            if (mActiveModule == module) mActiveModule = null;
            if (mActiveFrameGenModule == module) mActiveFrameGenModule = null;
        }
        save();
    }

    public void setParamValue(Module module, String uniformName, float value) {
        module.setParamValue(uniformName, value);
        save();
    }

    public Module getModuleByName(String name) {
        for (Module m : mModules) {
            if (m.getName().equals(name)) return m;
        }
        return null;
    }

    private void save() {
        try {
            JSONArray arr = new JSONArray();
            JSONObject builtinStates = new JSONObject();

            for (Module m : mModules) {
                if (BUILTIN_NAMES.contains(m.getName())) {
                    
                    builtinStates.put(m.getName(), m.isEnabled());
                    continue;
                }

                JSONObject obj = new JSONObject();
                obj.put("name", m.getName());
                obj.put("author", m.getAuthor());
                obj.put("version", m.getVersion());
                obj.put("type", m.getType().name());
                obj.put("vertexShader", m.getVertexShader());
                obj.put("fragmentShader", m.getFragmentShader());
                obj.put("enabled", m.isEnabled());

                JSONObject params = new JSONObject();
                for (Map.Entry<String, Float> entry : m.getParams().entrySet()) {
                    params.put(entry.getKey(), entry.getValue());
                }
                obj.put("params", params);

                JSONObject paramDefs = new JSONObject();
                for (Map.Entry<String, Module.ParamDef> entry : m.getParamDefs().entrySet()) {
                    Module.ParamDef d = entry.getValue();
                    JSONObject defObj = new JSONObject();
                    defObj.put("label",   d.label);
                    defObj.put("min",     d.min);
                    defObj.put("max",     d.max);
                    defObj.put("default", d.defaultValue);
                    paramDefs.put(entry.getKey(), defObj);
                }
                obj.put("paramDefs", paramDefs);

                arr.put(obj);
            }
            JSONArray chainArr = new JSONArray();
            for (String n : mChainOrder) chainArr.put(n);

            SharedPreferences.Editor editor = prefs().edit();
            editor.putString(KEY_MODULES, arr.toString());
            editor.putString(KEY_CHAIN_ORDER, chainArr.toString());
            editor.putString("builtin_states", builtinStates.toString());
            if (mActiveModule != null) editor.putString(KEY_ACTIVE_MODULE, mActiveModule.getName());
            else                       editor.remove(KEY_ACTIVE_MODULE);
            if (mActiveFrameGenModule != null) editor.putString(KEY_ACTIVE_FRAMEGEN_MODULE, mActiveFrameGenModule.getName());
            else                                editor.remove(KEY_ACTIVE_FRAMEGEN_MODULE);
            editor.apply();
        } catch (JSONException e) {  }
    }

    public void reload() {
        mModules.clear();
        mActiveModule = null;
        mActiveFrameGenModule = null;
        mChainOrder.clear();
        load();
    }

    private static final java.util.Set<String> BUILTIN_NAMES =
	new java.util.HashSet<>(java.util.Arrays.asList(
								"Frame Generation"
							
							));

    private void loadBuiltins(JSONObject states) {
        addBuiltin(new Module(
					   "Frame Generation",
					   "Demeter",
					   "1.0",
					   Module.Type.FRAMEGEN,
					   "attribute vec4 aPosition;\n" +
					   "attribute vec2 aTexCoord;\n" +
					   "varying vec2 vTexCoord;\n" +
					   "void main() {\n" +
					   "    gl_Position = aPosition;\n" +
					   "    vTexCoord = aTexCoord;\n" +
					   "}\n",
					   "precision mediump float;\n" +
					   "varying vec2 vTexCoord;\n" +
					   "uniform sampler2D uTexture0;\n" +
					   "uniform sampler2D uTexture1;\n" +
					   "uniform float uMix;\n" +
					   "void main() {\n" +
					   "    vec4 a = texture2D(uTexture0, vTexCoord);\n" +
					   "    vec4 b = texture2D(uTexture1, vTexCoord);\n" +
					   "    gl_FragColor = mix(a, b, uMix);\n" +
					   "}\n",
					   new HashMap<String, Float>(),
					   new HashMap<String, Module.ParamDef>()
				   ), states);
        
    }

    private void addBuiltin(Module m, JSONObject states) {
        for (Module existing : mModules) {
            if (existing.getName().equals(m.getName())) return;
        }
        try {
            if (states.has(m.getName())) {
                m.setEnabled(states.getBoolean(m.getName()));
            }
        } catch (JSONException e) {  }
        mModules.add(m);
    }

    private void load() {
        String json = prefs().getString(KEY_MODULES, "[]");
        String activeName = prefs().getString(KEY_ACTIVE_MODULE, null);
        String activeFrameGenName = prefs().getString(KEY_ACTIVE_FRAMEGEN_MODULE, null);
        String chainJson = prefs().getString(KEY_CHAIN_ORDER, "[]");

        try {
            JSONArray chainArr = new JSONArray(chainJson);
            for (int i = 0; i < chainArr.length(); i++) {
                mChainOrder.add(chainArr.getString(i));
            }
        } catch (JSONException e) {  }

        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Module.Type type = Module.Type.valueOf(
					obj.optString("type", Module.Type.MODIFIER.name()));
                String vertexShader = obj.optString("vertexShader");
                String fragmentShader = obj.optString("fragmentShader");

                Map<String, Float> params = new HashMap<>();
                JSONObject paramsObj = obj.optJSONObject("params");
                if (paramsObj != null) {
                    Iterator<String> keys = paramsObj.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        try {
                            params.put(key, (float) paramsObj.getDouble(key));
                        } catch (JSONException e) {  }
                    }
                }

                Map<String, Module.ParamDef> paramDefs = new HashMap<>();
                JSONObject paramDefsObj = obj.optJSONObject("paramDefs");
                if (paramDefsObj != null) {
                    Iterator<String> defKeys = paramDefsObj.keys();
                    while (defKeys.hasNext()) {
                        String key = defKeys.next();
                        try {
                            JSONObject defObj = paramDefsObj.getJSONObject(key);
                            String label = defObj.optString("label", key);
                            float min    = (float) defObj.optDouble("min", 0.0);
                            float max    = (float) defObj.optDouble("max", 1.0);
                            float def    = (float) defObj.optDouble("default", min);
                            paramDefs.put(key, new Module.ParamDef(label, min, max, def));
                        } catch (JSONException e) {  }
                    }
                }

                Module m = new Module(
					obj.getString("name"),
					obj.optString("author", "Desconocido"),
					obj.optString("version", "1.0"),
					type,
					vertexShader,
					fragmentShader,
					params,
					paramDefs
                );
                m.setEnabled(obj.optBoolean("enabled", false));
                mModules.add(m);

                if (activeName != null && m.getName().equals(activeName)) mActiveModule = m;
                if (activeFrameGenName != null && m.getName().equals(activeFrameGenName))
                    mActiveFrameGenModule = m;
            }
        } catch (JSONException e) {  }

        String builtinStatesJson = prefs().getString("builtin_states", "{}");
        JSONObject builtinStates;
        try { builtinStates = new JSONObject(builtinStatesJson); }
        catch (JSONException e) { builtinStates = new JSONObject(); }
        loadBuiltins(builtinStates);
    }

    private SharedPreferences prefs() {
        return mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private String readUriAsString(Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        InputStream is = mContext.getContentResolver().openInputStream(uri);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }
}
