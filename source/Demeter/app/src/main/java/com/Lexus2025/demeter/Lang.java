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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Lang {

    private static final String TAG = "Lang";
    private static final String PREFS_NAME       = "demeter_i18n";
    private static final String KEY_ACTIVE_LANG  = "active_lang";
    private static final String KEY_DATA_PREFIX  = "lang_data_";
    private static final String KEY_LANGS_LIST   = "langs_list";
    private static final String REMOTE_URL =
	"https://raw.githubusercontent.com/LexusYTG/Demeter-reshader/main/Store/lang.json";

    public static final String DEFAULT_LANG = "es";

    public interface Listener { void onLanguageChanged(); }

    private static final List<Listener> sListeners = new CopyOnWriteArrayList<Listener>();
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());
    private static Context sAppContext;
    private static String  sActiveLang = DEFAULT_LANG;
    private static final Map<Integer, String> sStrings = new HashMap<Integer, String>();

    private Lang() { }

    public static synchronized void init(Context ctx) {
        if (ctx == null) return;
        sAppContext = ctx.getApplicationContext();
        SharedPreferences sp = prefs();
        sActiveLang = sp.getString(KEY_ACTIVE_LANG, DEFAULT_LANG);
        if (!loadFromPrefs(sActiveLang)) loadFallback();
        fetchRemoteAsync();
    }

    public static String get(int id) {
        String s = sStrings.get(id);
        if (s != null) return s;
        String fb = FALLBACK.get(id);
        return fb != null ? fb : "[" + id + "]";
    }

    public static String f(int id, Object... args) {
        String t = get(id);
        try { return String.format(t, args); } catch (Exception e) { return t; }
    }

    public static String getActiveLanguage() { return sActiveLang; }

    public static List<String> getAvailableLanguages() {
        TreeSet<String> set = new TreeSet<String>();
        set.add(DEFAULT_LANG);
        String csv = prefs().getString(KEY_LANGS_LIST, "");
        if (!csv.isEmpty()) {
            for (String s : csv.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) set.add(t);
            }
        }
        return new ArrayList<String>(set);
    }

    public static String getDisplayName(String langId) {
        if ("es".equals(langId)) return get(ID_LANG_ES);
        if ("en".equals(langId)) return get(ID_LANG_EN);
        if ("pt".equals(langId)) return get(ID_LANG_PT);
        if ("fr".equals(langId)) return get(ID_LANG_FR);
        if ("de".equals(langId)) return get(ID_LANG_DE);
        if ("it".equals(langId)) return get(ID_LANG_IT);
        if ("ja".equals(langId)) return get(ID_LANG_JA);
        if ("zh".equals(langId)) return get(ID_LANG_ZH);
        if ("ru".equals(langId)) return get(ID_LANG_RU);
        return langId.toUpperCase();
    }

    public static synchronized void setLanguage(Context ctx, String langId) {
        if (langId == null || langId.isEmpty()) return;
        if (langId.equals(sActiveLang)) return;
        sActiveLang = langId;
        prefs().edit().putString(KEY_ACTIVE_LANG, langId).apply();
        if (!loadFromPrefs(langId)) loadFallback();
        notifyListeners();
    }

    public static void addListener(Listener l) {
        if (l != null && !sListeners.contains(l)) sListeners.add(l);
    }
    public static void removeListener(Listener l) {
        if (l != null) sListeners.remove(l);
    }

    private static SharedPreferences prefs() {
        return sAppContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static void notifyListeners() {
        sMainHandler.post(new Runnable() {
				@Override public void run() {
					for (Listener l : sListeners) {
						try { l.onLanguageChanged(); }
						catch (Exception e) { Log.w(TAG, "listener: " + e.getMessage()); }
					}
				}
			});
    }

    private static boolean loadFromPrefs(String langId) {
        String json = prefs().getString(KEY_DATA_PREFIX + langId, null);
        if (json == null || json.isEmpty()) return false;
        try {
            JSONObject obj = new JSONObject(json);
            Map<Integer, String> map = new HashMap<Integer, String>();
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                try { map.put(Integer.parseInt(k), obj.getString(k)); }
                catch (NumberFormatException ignored) { }
            }
            if (map.isEmpty()) return false;
            synchronized (sStrings) { sStrings.clear(); sStrings.putAll(map); }
            return true;
        } catch (Exception e) {
            Log.w(TAG, "loadFromPrefs(" + langId + "): " + e.getMessage());
            return false;
        }
    }

    private static void loadFallback() {
        synchronized (sStrings) { sStrings.clear(); sStrings.putAll(FALLBACK); }
    }

    private static void fetchRemoteAsync() {
        new Thread(new Runnable() {
				@Override public void run() {
					try {
						String body = download(REMOTE_URL);
						if (body == null || body.isEmpty()) return;
						Map<String, Map<Integer, String>> parsed = parseLangFile(body);
						if (parsed.isEmpty()) return;
						SharedPreferences.Editor ed = prefs().edit();
						StringBuilder csv = new StringBuilder();
						boolean first = true;
						for (Map.Entry<String, Map<Integer, String>> e : parsed.entrySet()) {
							JSONObject obj = new JSONObject();
							for (Map.Entry<Integer, String> s : e.getValue().entrySet()) {
								obj.put(String.valueOf(s.getKey()), s.getValue());
							}
							ed.putString(KEY_DATA_PREFIX + e.getKey(), obj.toString());
							if (!first) csv.append(',');
							csv.append(e.getKey());
							first = false;
						}
						ed.putString(KEY_LANGS_LIST, csv.toString());
						ed.apply();
						if (loadFromPrefs(sActiveLang)) notifyListeners();
					} catch (Exception e) {
						Log.w(TAG, "fetchRemote: " + e.getMessage());
					}
				}
			}, "LangFetch").start();
    }

    private static String download(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "DemeterApp/1.0");
        c.connect();
        try {
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK)
                throw new Exception("HTTP " + c.getResponseCode());
            BufferedReader r = new BufferedReader(
                new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            r.close();
            return sb.toString();
        } finally { c.disconnect(); }
    }

    private static Map<String, Map<Integer, String>> parseLangFile(String body) {
        Map<String, Map<Integer, String>> result = new HashMap<String, Map<Integer, String>>();
        String currentLang = null;
        Map<Integer, String> currentMap = null;
        for (String rawLine : body.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            String lower = line.toLowerCase();
            if (lower.startsWith("langid=")) {
                int eq = line.indexOf('=');
                int brace = line.indexOf('{', eq);
                if (eq >= 0 && brace > eq) {
                    currentLang = line.substring(eq + 1, brace).trim();
                    currentMap = new HashMap<Integer, String>();
                } else if (eq >= 0) {
                    currentLang = line.substring(eq + 1).trim();
                    currentMap = new HashMap<Integer, String>();
                }
            } else if ("}".equals(line) && currentMap != null && currentLang != null) {
                if (!currentMap.isEmpty()) result.put(currentLang, currentMap);
                currentLang = null; currentMap = null;
            } else if (currentMap != null && lower.startsWith("textid=")) {
                int eq = line.indexOf('=');
                int br1 = line.indexOf('[', eq);
                int br2 = line.lastIndexOf(']');
                if (eq >= 0 && br1 > eq && br2 > br1) {
                    try {
                        int id = Integer.parseInt(line.substring(eq + 1, br1).trim());
                        currentMap.put(id, line.substring(br1 + 1, br2));
                    } catch (NumberFormatException ignored) { }
                }
            }
        }
        return result;
    }

    public static final int ID_LANG_ES = 900, ID_LANG_EN = 901, ID_LANG_PT = 902,
	ID_LANG_FR = 903, ID_LANG_DE = 904, ID_LANG_IT = 905,
	ID_LANG_JA = 906, ID_LANG_ZH = 907, ID_LANG_RU = 908;

    private static final Map<Integer, String> FALLBACK = new HashMap<Integer, String>();
    static {
        FALLBACK.put(1, "Iniciar captura");
        FALLBACK.put(2, "Detener captura");
        FALLBACK.put(3, "Área");
        FALLBACK.put(4, "Overlay");
        FALLBACK.put(5, "Filtros");
        FALLBACK.put(6, "INACTIVO");
        FALLBACK.put(7, "CAPTURANDO");
        FALLBACK.put(8, "Ningún filtro activo");
        FALLBACK.put(9, "Selecciona un filtro para previsualizar");
        FALLBACK.put(10, "Parámetros");
        FALLBACK.put(11, "Activa un filtro o un framegen para ver sus parámetros.");
        FALLBACK.put(12, "def: %s");
        FALLBACK.put(13, "Filtros · %d activos");
        FALLBACK.put(14, "Filtros · %d activo");
        FALLBACK.put(15, "Importar shader");
        FALLBACK.put(16, "Archivo local");
        FALLBACK.put(17, "Tienda");
        FALLBACK.put(18, "Cancelar");
        FALLBACK.put(19, "Permiso denegado");
        FALLBACK.put(20, "Shader instalado");
        FALLBACK.put(21, "Error: %s");
        FALLBACK.put(22, "Área: %d×%d");
        FALLBACK.put(23, "Overlay: %d×%d");
        FALLBACK.put(24, "Shader instalado: %s");
        FALLBACK.put(25, "Seleccionar idioma");
        FALLBACK.put(26, "Importar módulo");
        FALLBACK.put(27, "FRAMEGEN · %s");
        FALLBACK.put(28, "MOD · %s");
        FALLBACK.put(29, "Ajustes del filtro");
        FALLBACK.put(30, "default");
        FALLBACK.put(31, "Rango");
        FALLBACK.put(32, "%s  ···  def: %s  ···  %s");
        FALLBACK.put(100, "Demeter");
        FALLBACK.put(101, "Captura activa");
        FALLBACK.put(102, "Detener");
        FALLBACK.put(103, "-- FPS");
        FALLBACK.put(104, "%.0f FPS");
        FALLBACK.put(150, "Arrastra para seleccionar el área a capturar");
        FALLBACK.put(151, "Completa");
        FALLBACK.put(152, "Reset");
        FALLBACK.put(153, "Confirmar");
        FALLBACK.put(200, "Arrastra el pin para posicionar el overlay de FPS");
        FALLBACK.put(201, "Default");
        FALLBACK.put(202, "Cancelar");
        FALLBACK.put(203, "OK");
        FALLBACK.put(204, "FPS");
        FALLBACK.put(205, "x:%d  y:%d   %d×%d px");
        FALLBACK.put(250, "Arrastra el overlay · esquinas para redimensionar");
        FALLBACK.put(251, "= Captura");
        FALLBACK.put(252, "Completa");
        FALLBACK.put(253, "OK");
        FALLBACK.put(254, "área de captura");
        FALLBACK.put(255, "✓1:1");
        FALLBACK.put(256, "⚠ distorsión");
        FALLBACK.put(257, "x:%d y:%d  %d×%d px");
        FALLBACK.put(258, "escala %.2fx%.2f");
        FALLBACK.put(300, "Filtros");
        FALLBACK.put(301, "Buscar filtro o autor…");
        FALLBACK.put(302, "Todos");
        FALLBACK.put(303, "MOD");
        FALLBACK.put(304, "RENDERER");
        FALLBACK.put(305, "FRAMEGEN");
        FALLBACK.put(306, "FG-G3");
        FALLBACK.put(307, "No hay filtros instalados.\nUsa el botón + en la pantalla principal para importar uno.");
        FALLBACK.put(308, "No hay filtros que coincidan con la búsqueda");
        FALLBACK.put(309, "FRAME GENERATION (%d)");
        FALLBACK.put(310, "CADENA ACTIVA (%d)");
        FALLBACK.put(311, "DISPONIBLES (%d)");
        FALLBACK.put(312, "Mostrar más (%d restantes)");
        FALLBACK.put(313, "por %s");
        FALLBACK.put(314, "por %s  ·  v%s");
        FALLBACK.put(315, "· %d parámetro configurable");
        FALLBACK.put(316, "· %d parámetros configurables");
        FALLBACK.put(317, "EN USO");
        FALLBACK.put(318, "Compilando %s…");
        FALLBACK.put(319, "%s activado");
        FALLBACK.put(320, "%s desactivado");
        FALLBACK.put(321, "Se desactivaron %d filtro(s) que ya no compilan");
        FALLBACK.put(322, "No se puede activar: %s");
        FALLBACK.put(323, "Cerrar");
        FALLBACK.put(324, "Desinstalar");
        FALLBACK.put(325, "%s desinstalado");
        FALLBACK.put(326, "%s: parámetros restablecidos");
        FALLBACK.put(327, "Restablecer todo");
        FALLBACK.put(328, "%s — Ajustes");
        FALLBACK.put(329, "Overlay de FPS");
        FALLBACK.put(330, "Motor de Frame Generation");
        FALLBACK.put(331, "Generación 1 — orden estricto, más delay, sin jitter");
        FALLBACK.put(332, "Generación 2 — sin cola, baja latencia, algo de jitter");
        FALLBACK.put(333, "Generación 3 — motion vectors precalculados, requiere shader G3");
        FALLBACK.put(334, "Advertencia — Generación 3");
        FALLBACK.put(335, "La Generación 3 usa un pipeline distinto: la app calcula los motion vectors por ti y se los entrega al shader. Los shaders para G1 y G2 NO funcionan con G3, y viceversa.\n\nSolo los shaders marcados explícitamente como FG-G3 son compatibles. Si activas un shader que no es G3, el framegen no arrancará.\n\nUsa únicamente shaders G3 de la tienda oficial. Un shader G3 de terceros tiene acceso a los datos de movimiento de la pantalla. Demeter no se hace responsable por shaders que no hayan pasado por revisión.\n\n¿Quieres activar la Generación 3?");
        FALLBACK.put(336, "Activar");
        FALLBACK.put(337, "FrameGen: Generación %d");
        FALLBACK.put(338, "default: %s");
        FALLBACK.put(400, "Tienda");
        FALLBACK.put(401, "Buscar shader o autor…");
        FALLBACK.put(402, "Todas");
        FALLBACK.put(403, "Todos");
        FALLBACK.put(404, "Cargando catálogo…");
        FALLBACK.put(405, "No se pudo cargar");
        FALLBACK.put(406, "Reintentar");
        FALLBACK.put(407, "No hay shaders que coincidan");
        FALLBACK.put(408, "✓ INSTALADO");
        FALLBACK.put(409, "Instalado");
        FALLBACK.put(410, "Instalar");
        FALLBACK.put(411, "Instalando…");
        FALLBACK.put(412, "Instalando: %s");
        FALLBACK.put(413, "No cierres esta pantalla");
        FALLBACK.put(414, "%s instalado");
        FALLBACK.put(415, "Catálogo actualizado");
        FALLBACK.put(416, "No se pudo actualizar: %s");
        FALLBACK.put(417, "Espera a que termine la instalación");
        FALLBACK.put(418, "Ya hay una instalación en curso");
        FALLBACK.put(ID_LANG_ES, "Español");
        FALLBACK.put(ID_LANG_EN, "English");
        FALLBACK.put(ID_LANG_PT, "Português");
        FALLBACK.put(ID_LANG_FR, "Français");
        FALLBACK.put(ID_LANG_DE, "Deutsch");
        FALLBACK.put(ID_LANG_IT, "Italiano");
        FALLBACK.put(ID_LANG_JA, "日本語");
        FALLBACK.put(ID_LANG_ZH, "中文");
        FALLBACK.put(ID_LANG_RU, "Русский");
    }
}
