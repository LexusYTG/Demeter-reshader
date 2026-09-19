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

import android.opengl.GLES20;
import android.util.Log;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

public class GlProgram {

    private static final String TAG = "GlProgram";

    // ========================================================================
    // Excepción estructurada para fallos de compilación
    // ========================================================================
    public static class CompileException extends RuntimeException {

        public enum Kind { VALIDATION, VERTEX_COMPILE, FRAGMENT_COMPILE, LINK, PROGRAM }

        private final Kind   mKind;
        private final String mInfoLog;
        private final int    mLineNumber;

        public CompileException(Kind kind, String message, String infoLog, int lineNumber) {
            super(message);
            mKind       = kind;
            mInfoLog    = infoLog;
            mLineNumber = lineNumber;
        }

        public Kind   getKind()       { return mKind; }
        public String getInfoLog()    { return mInfoLog; }
        public int    getLineNumber() { return mLineNumber; }

        public String getFriendlyMessage() {
            StringBuilder sb = new StringBuilder();
            switch (mKind) {
                case VALIDATION:        sb.append("Validación del shader");             break;
                case VERTEX_COMPILE:    sb.append("Error en el VERTEX shader");         break;
                case FRAGMENT_COMPILE:  sb.append("Error en el FRAGMENT shader");       break;
                case LINK:              sb.append("Error al enlazar el programa GLSL"); break;
                case PROGRAM:           sb.append("Error al crear el programa GLSL");   break;
            }
            sb.append(":\n\n").append(getMessage());
            if (mLineNumber > 0) sb.append("\n\nLínea aproximada: ").append(mLineNumber);
            if (mInfoLog != null && !mInfoLog.trim().isEmpty()
				&& !mInfoLog.trim().equals(getMessage())) {
                sb.append("\n\n--- Log del driver ---\n").append(mInfoLog.trim());
            }
            return sb.toString();
        }

        public static int parseLineNumber(String infoLog) {
            if (infoLog == null) return 0;
            Matcher m1 = Pattern.compile("(\\d+):(\\d+):").matcher(infoLog);
            if (m1.find()) {
                try { return Integer.parseInt(m1.group(2)); } catch (Exception ignored) {}
            }
            Matcher m2 = Pattern.compile("(?:line|Line)\\s+(\\d+)").matcher(infoLog);
            if (m2.find()) {
                try { return Integer.parseInt(m2.group(1)); } catch (Exception ignored) {}
            }
            return 0;
        }
    }

    // ========================================================================
    // Validación GLSL pre-compilación (sin contexto GL)
    // ========================================================================
    public static final class Checker {

        public static final class Report {
            public final String       source;
            public final String       error;
            public final boolean      requiresEs3;
            public final List<String> warnings;

            Report(String source, String error, boolean requiresEs3, List<String> warnings) {
                this.source      = source;
                this.error       = error;
                this.requiresEs3 = requiresEs3;
                this.warnings    = warnings;
            }
            public boolean isOk() { return error == null; }
        }

        private static final Pattern VERSION_LINE = Pattern.compile(
            "^\\s*#version\\s+([0-9]+)(\\s+es)?\\b.*$", Pattern.MULTILINE);

        private static final Pattern PRECISION_DECL = Pattern.compile(
            "precision\\s+(?:lowp|mediump|highp)\\s+(?:float|int|sampler2D)\\s*;");

        private static final Pattern MAIN_FUNC = Pattern.compile("\\bvoid\\s+main\\s*\\(");

        private Checker() {}

        public static Report validate(String source, boolean isVertex, boolean es3Available) {
            List<String> warnings = new ArrayList<String>();
            String kindName = isVertex ? "vertex" : "fragment";

            if (source == null || source.trim().isEmpty()) {
                return new Report(source,
								  "El código del " + kindName + " shader está vacío.", false, warnings);
            }

            String  s            = source;
            boolean hasVersion   = false;
            boolean versionIsEs3 = false;
            int     declaredVer  = 100;

            Matcher vm = VERSION_LINE.matcher(s);
            if (vm.find()) {
                hasVersion = true;
                try { declaredVer = Integer.parseInt(vm.group(1)); }
                catch (NumberFormatException ignored) { declaredVer = 100; }
                versionIsEs3 = declaredVer >= 300;

                if (vm.start() > 0 && s.substring(0, vm.start()).trim().length() > 0) {
                    return new Report(s,
									  "La directiva '#version' debe ser la primera línea del " + kindName
									  + " shader (solo puede haber comentarios o espacios en blanco antes).",
									  versionIsEs3, warnings);
                }
            }

            boolean es3Only = usesEs3OnlySyntax(s);
            String  es2Kw   = usesEs2OnlySyntax(s, isVertex);

            if (!hasVersion && es3Only && es2Kw == null) {
                if (!es3Available) {
                    return new Report(s,
									  "El " + kindName + " shader usa sintaxis de OpenGL ES 3.0 "
									  + "(in/out/texture()) pero el dispositivo no soporta ES 3.0.\n"
									  + "Reescríbelo con sintaxis ES 2.0 "
									  + "(attribute/varying/texture2D/gl_FragColor).",
									  true, warnings);
                }
                s = "#version 300 es\n" + s;
                versionIsEs3 = true;
                warnings.add("Se añadió '#version 300 es' automáticamente.");
            }

            if (versionIsEs3 && !es3Available) {
                return new Report(s,
								  "El " + kindName + " shader requiere OpenGL ES 3.0 (#version "
								  + declaredVer + " es), pero el dispositivo no lo soporta.",
								  true, warnings);
            }

            if (versionIsEs3 && es2Kw != null) {
                if ("texture2D()".equals(es2Kw) || "textureCube()".equals(es2Kw)) {
                    s = Pattern.compile("\\btexture2D\\s*\\(").matcher(s).replaceAll("texture(");
                    s = Pattern.compile("\\btextureCube\\s*\\(").matcher(s).replaceAll("texture(");
                    warnings.add("Convertido 'texture2D()'/'textureCube()' → 'texture()' para ES 3.0.");
                    es2Kw = usesEs2OnlySyntax(s, isVertex);
                }
                if (es2Kw != null) {
                    return new Report(s,
									  "El " + kindName + " shader declara #version 300 es pero usa '"
									  + es2Kw + "', que no existe en ES 3.0.",
									  true, warnings);
                }
            }

            if (hasVersion && !versionIsEs3 && es3Only) {
                s = Pattern.compile("\\btexture\\s*\\(").matcher(s).replaceAll("texture2D(");
                warnings.add("Convertido 'texture()' → 'texture2D()' para ES 2.0.");
            }

            if (!isVertex && !versionIsEs3 && !PRECISION_DECL.matcher(s).find()) {
                s = insertDefaultPrecision(s);
                warnings.add("Se añadió 'precision mediump float;' automáticamente.");
            }

            String braceErr = checkBraceBalance(s, kindName);
            if (braceErr != null) return new Report(s, braceErr, versionIsEs3, warnings);

            if (!MAIN_FUNC.matcher(s).find()) {
                return new Report(s,
								  "El " + kindName + " shader no define 'void main()'.",
								  versionIsEs3, warnings);
            }

            return new Report(s, null, versionIsEs3, warnings);
        }

        private static boolean usesEs3OnlySyntax(String s) {
            String stripped = stripComments(s);
            if (Pattern.compile("\\bin\\s+(?:vec|mat|float|int|uint|sampler)").matcher(stripped).find()) return true;
            if (Pattern.compile("\\bout\\s+(?:vec|mat|float|int|uint)").matcher(stripped).find()) return true;
            if (Pattern.compile("\\btexelFetch\\s*\\(").matcher(stripped).find()) return true;
            if (Pattern.compile("\\btexture\\s*\\(").matcher(stripped).find()) return true;
            return false;
        }

        private static String usesEs2OnlySyntax(String s, boolean isVertex) {
            String stripped = stripComments(s);
            if (Pattern.compile("\\btexture2D\\s*\\(").matcher(stripped).find())    return "texture2D()";
            if (Pattern.compile("\\btextureCube\\s*\\(").matcher(stripped).find())  return "textureCube()";
            if (Pattern.compile("\\bgl_FragColor\\b").matcher(stripped).find())     return "gl_FragColor";
            if (isVertex && Pattern.compile("\\battribute\\b").matcher(stripped).find()) return "attribute";
            if (Pattern.compile("\\bvarying\\b").matcher(stripped).find())          return "varying";
            return null;
        }

        private static String insertDefaultPrecision(String s) {
            Matcher vm = VERSION_LINE.matcher(s);
            if (vm.find()) {
                int endOfLine = s.indexOf('\n', vm.end());
                if (endOfLine < 0) endOfLine = s.length();
                return s.substring(0, endOfLine + 1)
					+ "precision mediump float;\n"
					+ s.substring(endOfLine + 1);
            }
            return "precision mediump float;\n" + s;
        }

        private static String checkBraceBalance(String s, String kindName) {
            String stripped = stripComments(s);
            int depth = 0, line = 1;
            for (int i = 0; i < stripped.length(); i++) {
                char c = stripped.charAt(i);
                if (c == '\n') line++;
                else if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth < 0) {
                        return "Llave '}' de cierre sin apertura. Línea " + line
							+ " del " + kindName + " shader.";
                    }
                }
            }
            if (depth > 0) {
                return "Faltan " + depth + " llave(s) '}' de cierre en el "
					+ kindName + " shader.";
            }
            return null;
        }

        private static String stripComments(String s) {
            StringBuilder sb = new StringBuilder(s.length());
            int i = 0, n = s.length();
            while (i < n) {
                char c = s.charAt(i);
                if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                    while (i < n && s.charAt(i) != '\n') i++;
                } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                    i += 2;
                    while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) i++;
                    i += 2;
                    sb.append(' ');
                } else if (c == '"') {
                    sb.append(' ');
                    i++;
                    while (i < n && s.charAt(i) != '"') {
                        if (s.charAt(i) == '\\' && i + 1 < n) i++;
                        i++;
                    }
                    i++;
                } else {
                    sb.append(c);
                    i++;
                }
            }
            return sb.toString();
        }
    }

    // ========================================================================
    // Compilador offscreen — prueba REAL contra el driver GL
    // ========================================================================
    //
    // Crea un contexto EGL con pbuffer 16x16 en un hilo dedicado
    // (daemon, "ShaderCompiler"). Cualquier llamada a testCompile() se
    // serializa en ese hilo, así el contexto siempre está "current" allí.
    //
    // Si el dispositivo no soporta pbuffer (raro en Android moderno),
    // testCompile() devuelve null y el caller decide qué hacer.

    private static final Object sCompileLock = new Object();

    private static final ExecutorService sCompilePool =
	Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "ShaderCompiler");
                t.setDaemon(true);
                return t;
            }
        });

    private static boolean sOffscreenReady  = false;
    private static boolean sOffscreenFailed = false;
    private static EGL10         sEgl;
    private static EGLDisplay    sEglDisplay = EGL10.EGL_NO_DISPLAY;
    private static EGLContext    sEglContext = EGL10.EGL_NO_CONTEXT;
    private static EGLSurface    sEglSurface = EGL10.EGL_NO_SURFACE;

    /**
     * Prueba REAL de compilación + linkeo en un contexto GL offscreen.
     * Bloquea al hilo llamante hasta tener el resultado (normalmente <100 ms).
     *
     * @return null si compila y enlaza correctamente; mensaje de error legible si falla;
     *         null también si el contexto offscreen no pudo crearse (asumimos OK).
     */
    public static String testCompile(final String vertexSource, final String fragmentSource) {
        try {
            return sCompilePool.submit(new Callable<String>() {
					@Override public String call() {
						synchronized (sCompileLock) {
							if (!ensureOffscreenContext()) return null;
							try {
								return offscreenTest(vertexSource, fragmentSource);
							} catch (OffscreenFail e) {
								return e.getMessage();
							} catch (Throwable t) {
								return "Error inesperado probando el shader: " + t.getMessage();
							}
						}
					}
				}).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            return "Error al probar el shader: " + e.getMessage();
        }
    }

    private static boolean ensureOffscreenContext() {
        if (sOffscreenReady)  return true;
        if (sOffscreenFailed) return false;

        try {
            sEgl = (EGL10) EGLContext.getEGL();
            sEglDisplay = sEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            int[] version = new int[2];
            if (!sEgl.eglInitialize(sEglDisplay, version)) {
                sOffscreenFailed = true;
                return false;
            }

            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs    = new int[1];
            int[] attribs = {
                EGL10.EGL_RED_SIZE,   8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE,  8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
                EGL10.EGL_NONE
            };
            if (!sEgl.eglChooseConfig(sEglDisplay, attribs, configs, 1, numConfigs)
				|| numConfigs[0] == 0) {
                sOffscreenFailed = true;
                return false;
            }

            // Mismo orden que GlPainter.init: primero ES 3.0, fallback a ES 2.0.
            int[] ctx3 = { 0x3098, 3, EGL10.EGL_NONE };
            int[] ctx2 = { 0x3098, 2, EGL10.EGL_NONE };
            sEglContext = sEgl.eglCreateContext(sEglDisplay, configs[0],
                                                EGL10.EGL_NO_CONTEXT, ctx3);
            if (sEglContext == null || sEglContext == EGL10.EGL_NO_CONTEXT) {
                sEglContext = sEgl.eglCreateContext(sEglDisplay, configs[0],
                                                    EGL10.EGL_NO_CONTEXT, ctx2);
            }
            if (sEglContext == null || sEglContext == EGL10.EGL_NO_CONTEXT) {
                sOffscreenFailed = true;
                return false;
            }

            int[] pbAttribs = {
                EGL10.EGL_WIDTH,  16,
                EGL10.EGL_HEIGHT, 16,
                EGL10.EGL_NONE
            };
            sEglSurface = sEgl.eglCreatePbufferSurface(sEglDisplay, configs[0], pbAttribs);
            if (sEglSurface == null || sEglSurface == EGL10.EGL_NO_SURFACE) {
                sOffscreenFailed = true;
                return false;
            }

            if (!sEgl.eglMakeCurrent(sEglDisplay, sEglSurface, sEglSurface, sEglContext)) {
                sOffscreenFailed = true;
                return false;
            }

            sOffscreenReady = true;
            Log.d(TAG, "Contexto offscreen listo para pruebas de shader");
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "No se pudo crear el contexto offscreen: " + t.getMessage());
            sOffscreenFailed = true;
            return false;
        }
    }

    private static String offscreenTest(String vs, String fs) {
        boolean es3 = GlPainter.isEs3Supported();

        Checker.Report vr = Checker.validate(vs, true, es3);
        if (!vr.isOk()) return "VERTEX shader:\n" + vr.error;

        Checker.Report fr = Checker.validate(fs, false, es3);
        if (!fr.isOk()) return "FRAGMENT shader:\n" + fr.error;

        int vsh = 0, fsh = 0, prog = 0;
        try {
            vsh = compileOffscreen(GLES20.GL_VERTEX_SHADER,   vr.source, "VERTEX");
            fsh = compileOffscreen(GLES20.GL_FRAGMENT_SHADER, fr.source, "FRAGMENT");

            prog = GLES20.glCreateProgram();
            if (prog == 0) {
                throw new OffscreenFail(
                    "glCreateProgram() devolvió 0 en el contexto offscreen.");
            }
            GLES20.glAttachShader(prog, vsh);
            GLES20.glAttachShader(prog, fsh);
            GLES20.glLinkProgram(prog);

            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] == GLES20.GL_FALSE) {
                String log = GLES20.glGetProgramInfoLog(prog);
                throw new OffscreenFail(
                    "Error de LINKEO:\n\n" + (log == null ? "" : log)
                    + "\n\nSugerencia: revisa que el vertex y el fragment declaren "
                    + "los mismos varyings / in-out con tipos idénticos.");
            }
            return null;
        } finally {
            if (prog != 0) GLES20.glDeleteProgram(prog);
            if (vsh  != 0) GLES20.glDeleteShader(vsh);
            if (fsh  != 0) GLES20.glDeleteShader(fsh);
        }
    }

    private static int compileOffscreen(int type, String src, String label) {
        int shader = GLES20.glCreateShader(type);
        if (shader == 0) {
            throw new OffscreenFail(
                "glCreateShader() devolvió 0 para el " + label + " shader.");
        }
        GLES20.glShaderSource(shader, src);
        GLES20.glCompileShader(shader);

        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == GLES20.GL_FALSE) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new OffscreenFail(
                "Error en el " + label + " shader:\n\n" + (log == null ? "" : log));
        }
        return shader;
    }

    private static class OffscreenFail extends RuntimeException {
        OffscreenFail(String msg) { super(msg); }
    }

    // ========================================================================
    // Instancia real
    // ========================================================================

    private int mProgram;
    private int mPositionHandle;
    private int mTexCoordHandle;
    private int mTextureHandle;
    private Map<String, Integer> mUniformLocations;

    public GlProgram(String vertexSource, String fragmentSource, Map<String, Float> params) {
        mUniformLocations = new HashMap<String, Integer>();

        boolean es3 = GlPainter.isEs3Supported();

        Checker.Report vr = Checker.validate(vertexSource, true, es3);
        if (!vr.isOk()) {
            throw new CompileException(CompileException.Kind.VALIDATION,
									   "Vertex shader: " + vr.error, null, 0);
        }
        Checker.Report fr = Checker.validate(fragmentSource, false, es3);
        if (!fr.isOk()) {
            throw new CompileException(CompileException.Kind.VALIDATION,
									   "Fragment shader: " + fr.error, null, 0);
        }
        for (String w : vr.warnings) Log.i(TAG, "Vertex: " + w);
        for (String w : fr.warnings) Log.i(TAG, "Fragment: " + w);

        mProgram = createProgram(vr.source, fr.source);

        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
        mTexCoordHandle = GLES20.glGetAttribLocation(mProgram, "aTexCoord");
        mTextureHandle  = GLES20.glGetUniformLocation(mProgram, "uTexture");

        if (mPositionHandle < 0) Log.w(TAG, "El shader no declara 'aPosition'");
        if (mTexCoordHandle < 0) Log.w(TAG, "El shader no declara 'aTexCoord'");
        if (mTextureHandle  < 0) Log.w(TAG, "El shader no declara 'uTexture'");

        if (params != null) {
            Iterator<Map.Entry<String, Float>> it = params.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Float> entry = it.next();
                int loc = GLES20.glGetUniformLocation(mProgram, entry.getKey());
                if (loc >= 0) mUniformLocations.put(entry.getKey(), loc);
            }
        }
    }

    public void draw(int textureId, FloatBuffer vertexBuffer, FloatBuffer texCoordBuffer,
                     Map<String, Float> params) {
        GLES20.glUseProgram(mProgram);
        vertexBuffer.position(0);
        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glVertexAttribPointer(mPositionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        texCoordBuffer.position(0);
        GLES20.glEnableVertexAttribArray(mTexCoordHandle);
        GLES20.glVertexAttribPointer(mTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(mTextureHandle, 0);
        if (params != null) {
            Iterator<Map.Entry<String, Float>> it = params.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Float> entry = it.next();
                Integer loc = mUniformLocations.get(entry.getKey());
                if (loc != null && loc >= 0) GLES20.glUniform1f(loc, entry.getValue());
            }
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(mPositionHandle);
        GLES20.glDisableVertexAttribArray(mTexCoordHandle);
    }

    public void draw(int[] textureIds, FloatBuffer vertexBuffer, FloatBuffer texCoordBuffer,
                     Map<String, Float> params) {
        GLES20.glUseProgram(mProgram);
        vertexBuffer.position(0);
        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glVertexAttribPointer(mPositionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        texCoordBuffer.position(0);
        GLES20.glEnableVertexAttribArray(mTexCoordHandle);
        GLES20.glVertexAttribPointer(mTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer);
        int numTex = textureIds.length;
        for (int i = 0; i < numTex; i++) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[i]);
            String uniformName = (i == 0) ? "uTexture" : "uTexture" + i;
            int loc = GLES20.glGetUniformLocation(mProgram, uniformName);
            if (loc >= 0) GLES20.glUniform1i(loc, i);
        }
        if (params != null) {
            Iterator<Map.Entry<String, Float>> it = params.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Float> entry = it.next();
                Integer loc = mUniformLocations.get(entry.getKey());
                if (loc == null) {
                    loc = GLES20.glGetUniformLocation(mProgram, entry.getKey());
                    if (loc >= 0) mUniformLocations.put(entry.getKey(), loc);
                }
                if (loc != null && loc >= 0) GLES20.glUniform1f(loc, entry.getValue());
            }
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
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
        int vs = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource,
                            CompileException.Kind.VERTEX_COMPILE, "vertex");
        int fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource,
                            CompileException.Kind.FRAGMENT_COMPILE, "fragment");

        int program = GLES20.glCreateProgram();
        if (program == 0) {
            GLES20.glDeleteShader(vs);
            GLES20.glDeleteShader(fs);
            throw new CompileException(CompileException.Kind.PROGRAM,
									   "glCreateProgram() devolvió 0. El contexto GL puede estar perdido.",
									   null, 0);
        }

        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == GLES20.GL_FALSE) {
            String log = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            GLES20.glDeleteShader(vs);
            GLES20.glDeleteShader(fs);
            throw new CompileException(CompileException.Kind.LINK,
									   "El linker rechazó el programa. Verifica que vertex y fragment "
									   + "declaren los mismos varyings/in-out con tipos idénticos.",
									   log, CompileException.parseLineNumber(log));
        }

        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);
        return program;
    }

    private int loadShader(int type, String source,
                           CompileException.Kind kind, String label) {
        int shader = GLES20.glCreateShader(type);
        if (shader == 0) {
            throw new CompileException(kind,
									   "glCreateShader() devolvió 0 para el " + label + " shader.", null, 0);
        }
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == GLES20.GL_FALSE) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new CompileException(kind,
									   "El compilador rechazó el " + label + " shader.",
									   log, CompileException.parseLineNumber(log));
        }
        return shader;
    }
}
