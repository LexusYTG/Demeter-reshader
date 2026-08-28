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

    private final Context mContext;
    private final List<Module> mModules = new ArrayList<>();
    private Module mActiveModule = null;

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

    // ─── Instalación desde string JSON (ShaderStoreActivity) ───────────────────
    // Evita Uri.fromFile() que falla en Android 7+ con ContentResolver.
    public void installFromJson(String jsonString) throws Exception {
        JSONObject obj;
        try {
            obj = new JSONObject(jsonString);
        } catch (JSONException e) {
            throw new IllegalArgumentException("El archivo no es un JSON válido: " + e.getMessage());
        }
        installFromJsonObject(obj);
    }

    // ÚNICO método de instalación: desde .demeter con Uri del sistema de archivos
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

    // Lógica compartida de parseo e instalación
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

        // Parsear definiciones de parámetros configurables
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
                } catch (JSONException e) { /* ignorar entradas malformadas */ }
            }
        }

        // Valores iniciales: primero los defaults de paramDefs, luego params los sobreescribe
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
                } catch (JSONException e) { /* ignorar parámetros no numéricos */ }
            }
        }

        // Evitar duplicados por nombre
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
        mModules.remove(module);
        save();
    }

    public void setEnabled(Module module, boolean enabled) {
        module.setEnabled(enabled);
        if (!enabled) {
            module.destroyShader();
            if (mActiveModule == module) mActiveModule = null;
        }
        save();
    }

    /**
     * Actualiza un parámetro configurable en tiempo real y persiste el cambio.
     * El renderer lo leerá en el siguiente frame a través de module.getParams().
     */
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
            for (Module m : mModules) {
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
            SharedPreferences.Editor editor = prefs().edit();
            editor.putString(KEY_MODULES, arr.toString());
            if (mActiveModule != null) {
                editor.putString(KEY_ACTIVE_MODULE, mActiveModule.getName());
            } else {
                editor.remove(KEY_ACTIVE_MODULE);
            }
            editor.apply();
        } catch (JSONException e) { /* ignore */ }
    }

    /** Recarga los módulos desde SharedPreferences (útil tras instalar desde otra instancia). */
    public void reload() {
        mModules.clear();
        mActiveModule = null;
        load();
    }

    private void load() {
        String json = prefs().getString(KEY_MODULES, "[]");
        String activeName = prefs().getString(KEY_ACTIVE_MODULE, null);

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
                        } catch (JSONException e) { /* ignore */ }
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
                        } catch (JSONException e) { /* ignore */ }
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

                if (activeName != null && m.getName().equals(activeName)) {
                    mActiveModule = m;
                }
            }
        } catch (JSONException e) { /* lista vacía */ }
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

