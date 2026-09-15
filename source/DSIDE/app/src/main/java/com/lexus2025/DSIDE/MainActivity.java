package com.lexus2025.DSIDE;

// =============================================================================
//  Demeter Shaders IDE — MainActivity.java
//  Monolito completo · OpenGL ES 2.0 · Java 7 · Android
//  Paquete: com.lexus2025.DSIDE
//  Nombre: Demeter Shaders IDE
// =============================================================================

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends Activity {

    private static final String TAG      = "DSIDE";
    private static final int REQ_STORAGE = 1001;

    // ─────────────────────────────────────────────────────────
    //  Paleta de colores del IDE
    // ─────────────────────────────────────────────────────────
    private static final int C_BG       = 0xFF0D0F14;
    private static final int C_PANEL    = 0xFF13161E;
    private static final int C_PANEL2   = 0xFF1A1E28;
    private static final int C_ACCENT   = 0xFF00E5FF;
    private static final int C_ACCENT2  = 0xFF7C4DFF;
    private static final int C_TEXT     = 0xFFE0E6F0;
    private static final int C_MUTED    = 0xFF6B7590;
    private static final int C_DIVIDER  = 0xFF222535;
    private static final int C_GREEN    = 0xFF00E676;
    private static final int C_ORANGE   = 0xFFFF9100;
    private static final int C_RED      = 0xFFFF1744;

    // ─────────────────────────────────────────────────────────
    //  Estado global del IDE
    // ─────────────────────────────────────────────────────────
    private final List<ShaderEffect> effects       = new ArrayList<ShaderEffect>();
    private final List<ShaderEffect> placedEffects = new ArrayList<ShaderEffect>();
    private ShaderEffect selectedEffect  = null;
    private String       compiledGLSL   = "";
    private boolean      glslDirty      = false;

    // Vistas principales
    private DemeterGLSurfaceView glView;
    private MeshCanvasView       meshView;
    private EditText             glslEditor;
    private DemeterRenderer      renderer;
    private TextView             statusBar;
    private LinearLayout         appliedBar; // barra inferior del panel central

    // ─────────────────────────────────────────────────────────
    //  ENTRY POINT
    // ─────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
			WindowManager.LayoutParams.FLAG_FULLSCREEN,
			WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        initBuiltinEffects();

        // ── Root ─────────────────────────────────────────────
        RelativeLayout root = new RelativeLayout(this);
        root.setBackgroundColor(C_BG);

        // ── TOP BAR ──────────────────────────────────────────
        LinearLayout topBar = buildTopBar();
        topBar.setId(generateId());
        RelativeLayout.LayoutParams topParams = new RelativeLayout.LayoutParams(
			RelativeLayout.LayoutParams.MATCH_PARENT, dp(52));
        topParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        root.addView(topBar, topParams);

        // ── STATUS BAR ───────────────────────────────────────
        statusBar = new TextView(this);
        statusBar.setId(generateId());
        statusBar.setBackgroundColor(0xFF080A0F);
        statusBar.setTextColor(C_MUTED);
        statusBar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        statusBar.setPadding(dp(10), 0, dp(10), 0);
        statusBar.setGravity(Gravity.CENTER_VERTICAL);
        statusBar.setText("Demeter Shaders IDE  ·  Listo");
        RelativeLayout.LayoutParams sbParams = new RelativeLayout.LayoutParams(
			RelativeLayout.LayoutParams.MATCH_PARENT, dp(24));
        sbParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        root.addView(statusBar, sbParams);

        // ── CONTENT AREA (3 paneles) ──────────────────────────
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        RelativeLayout.LayoutParams contentParams = new RelativeLayout.LayoutParams(
			RelativeLayout.LayoutParams.MATCH_PARENT,
			RelativeLayout.LayoutParams.MATCH_PARENT);
        contentParams.addRule(RelativeLayout.BELOW, topBar.getId());
        contentParams.addRule(RelativeLayout.ABOVE, statusBar.getId());
        root.addView(content, contentParams);

        // Panel izquierdo (200dp fijo)
        LinearLayout.LayoutParams lpLeft = new LinearLayout.LayoutParams(dp(200),
																		 LinearLayout.LayoutParams.MATCH_PARENT);
        content.addView(buildLeftPanel(), lpLeft);

        // Divisor
        content.addView(makeDividerV(), new LinearLayout.LayoutParams(dp(1),
																	  LinearLayout.LayoutParams.MATCH_PARENT));

        // Panel central (weight=1, expansivo)
        LinearLayout.LayoutParams lpCenter = new LinearLayout.LayoutParams(0,
																		   LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        content.addView(buildCenterPanel(), lpCenter);

        // Divisor
        content.addView(makeDividerV(), new LinearLayout.LayoutParams(dp(1),
																	  LinearLayout.LayoutParams.MATCH_PARENT));

        // Panel derecho (220dp fijo)
        LinearLayout.LayoutParams lpRight = new LinearLayout.LayoutParams(dp(220),
																		  LinearLayout.LayoutParams.MATCH_PARENT);
        content.addView(buildRightPanel(), lpRight);

        setContentView(root);
        recompileShader();
    }

    // Genera IDs únicos compatible con API <17
    private int nextId = 0x7f000100;
    private int generateId() {
        return nextId++;
    }

    // ═══════════════════════════════════════════════════════════
    //  TOP BAR
    // ═══════════════════════════════════════════════════════════
    private LinearLayout buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(C_PANEL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), 0, dp(12), 0);

        // Logo
        TextView logo = new TextView(this);
        logo.setText("Ω DSIDE");
        logo.setTextColor(C_ACCENT);
        logo.setTypeface(null, Typeface.BOLD);
        logo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        bar.addView(logo, wrapLP());

        TextView sub = new TextView(this);
        sub.setText("  Demeter Shaders IDE");
        sub.setTextColor(C_MUTED);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        bar.addView(sub, wrapLP());

        // Spacer
        bar.addView(new View(this), new LinearLayout.LayoutParams(0,
																  LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        // Botón exportar (central-derecha)
        Button btnExport = makeTopButton("⬆  Exportar .demeter", C_ACCENT, C_BG);
        btnExport.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					handleExportClick();
				}
			});
        bar.addView(btnExport);

        return bar;
    }

    private Button makeTopButton(String text, int bg, int fg) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(fg);
        b.setBackgroundColor(bg);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        b.setPadding(dp(16), 0, dp(16), 0);
        b.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.WRAP_CONTENT, dp(36));
        lp.setMargins(dp(4), dp(8), dp(4), dp(8));
        b.setLayoutParams(lp);
        return b;
    }

    // ═══════════════════════════════════════════════════════════
    //  PANEL IZQUIERDO — Efectos disponibles + Parámetros activos
    // ═══════════════════════════════════════════════════════════
    private View buildLeftPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(C_PANEL);

        // Cabecera efectos
        panel.addView(makePanelHeader("⚡ Efectos disponibles"));

        // Búsqueda / lista de efectos
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(C_PANEL);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(4), dp(8), dp(4));

        for (ShaderEffect eff : effects) {
            list.addView(makeEffectCard(eff));
        }

        scroll.addView(list);
        panel.addView(scroll, new LinearLayout.LayoutParams(
						  LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // Separador + cabecera parámetros
        panel.addView(makeDividerH());
        panel.addView(makePanelHeader("⚙ Parámetros configurables"));

        // Lista dinámica de parámetros activos
        final ScrollView paramScroll = new ScrollView(this);
        paramScroll.setBackgroundColor(C_PANEL2);
        final LinearLayout paramList = new LinearLayout(this);
        paramList.setId(generateId());
        paramList.setOrientation(LinearLayout.VERTICAL);
        paramList.setPadding(dp(8), dp(4), dp(8), dp(8));
        paramScroll.addView(paramList);
        panel.addView(paramScroll, new LinearLayout.LayoutParams(
						  LinearLayout.LayoutParams.MATCH_PARENT, dp(170)));

        // Guardamos referencia para poder actualizarlo
        // Usamos tag para recuperarlo
        paramList.setTag("paramList");

        return panel;
    }

    private View makeEffectCard(final ShaderEffect eff) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(C_PANEL2);
        card.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(3), 0, dp(3));
        card.setLayoutParams(lp);

        // Fila: dot color + nombre + botón +
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        dot.setBackgroundColor(eff.color);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotLp.setMargins(0, 0, dp(6), 0);
        dot.setLayoutParams(dotLp);
        row.addView(dot);

        TextView nameTv = new TextView(this);
        nameTv.setText(eff.name);
        nameTv.setTextColor(C_TEXT);
        nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        nameTv.setTypeface(null, Typeface.BOLD);
        row.addView(nameTv, new LinearLayout.LayoutParams(0,
														  LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button addBtn = new Button(this);
        addBtn.setText("+");
        addBtn.setTextColor(C_BG);
        addBtn.setBackgroundColor(C_ACCENT);
        addBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        addBtn.setPadding(dp(6), 0, dp(6), 0);
        addBtn.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(dp(32), dp(28));
        addBtn.setLayoutParams(btnLp);
        addBtn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					addEffectToMesh(eff);
				}
			});
        row.addView(addBtn);
        card.addView(row);

        // Descripción
        TextView desc = new TextView(this);
        desc.setText(eff.description);
        desc.setTextColor(C_MUTED);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT);
        descLp.setMargins(dp(14), dp(2), 0, 0);
        card.addView(desc, descLp);

        return card;
    }

    // ═══════════════════════════════════════════════════════════
    //  PANEL CENTRAL — Preview GL + Gizmo 2D (MeshCanvasView)
    // ═══════════════════════════════════════════════════════════
    private View buildCenterPanel() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(0xFF080A0F);

        // Header fijo arriba
        TextView header = makePanelHeader("◈ Vista Previa — Malla Interactiva");
        FrameLayout.LayoutParams hLp = new FrameLayout.LayoutParams(
			FrameLayout.LayoutParams.MATCH_PARENT, dp(32));
        hLp.gravity = Gravity.TOP;
        frame.addView(header, hLp);

        // GL Surface (preview del shader)
        glView = new DemeterGLSurfaceView(this);
        renderer = new DemeterRenderer();
        glView.setRenderer(renderer);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        FrameLayout.LayoutParams glLp = new FrameLayout.LayoutParams(
			FrameLayout.LayoutParams.MATCH_PARENT,
			FrameLayout.LayoutParams.MATCH_PARENT);
        glLp.topMargin  = dp(32);
        glLp.bottomMargin = dp(96);
        frame.addView(glView, glLp);

        // Overlay Canvas — gizmo 2D encima del GL
        meshView = new MeshCanvasView(this);
        FrameLayout.LayoutParams mvLp = new FrameLayout.LayoutParams(
			FrameLayout.LayoutParams.MATCH_PARENT,
			FrameLayout.LayoutParams.MATCH_PARENT);
        mvLp.topMargin    = dp(32);
        mvLp.bottomMargin = dp(96);
        frame.addView(meshView, mvLp);

        // Hint "sin efectos"
        final TextView hint = new TextView(this);
        hint.setId(generateId());
        hint.setText("+ Añade efectos desde el panel izquierdo");
        hint.setTextColor(C_MUTED);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        hint.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(
			FrameLayout.LayoutParams.MATCH_PARENT,
			FrameLayout.LayoutParams.MATCH_PARENT);
        hintLp.topMargin    = dp(32);
        hintLp.bottomMargin = dp(96);
        hint.setTag("meshHint");
        frame.addView(hint, hintLp);

        // Barra inferior de efectos aplicados (chips)
        appliedBar = new LinearLayout(this);
        appliedBar.setOrientation(LinearLayout.HORIZONTAL);
        appliedBar.setBackgroundColor(C_PANEL2);
        appliedBar.setPadding(dp(6), dp(4), dp(6), dp(4));
        appliedBar.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams abLp = new FrameLayout.LayoutParams(
			FrameLayout.LayoutParams.MATCH_PARENT, dp(96));
        abLp.gravity = Gravity.BOTTOM;
        frame.addView(appliedBar, abLp);

        // Etiqueta dentro del appliedBar cuando está vacío
        TextView emptyChip = new TextView(this);
        emptyChip.setText("Sin efectos aplicados");
        emptyChip.setTextColor(C_MUTED);
        emptyChip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        emptyChip.setTag("emptyChip");
        appliedBar.addView(emptyChip);

        return frame;
    }

    // ═══════════════════════════════════════════════════════════
    //  PANEL DERECHO — Editor GLSL en tiempo real
    // ═══════════════════════════════════════════════════════════
    private View buildRightPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(C_PANEL);

        panel.addView(makePanelHeader("</> GLSL — Fragmento"));

        // Toolbar del editor
        LinearLayout editorToolbar = new LinearLayout(this);
        editorToolbar.setOrientation(LinearLayout.HORIZONTAL);
        editorToolbar.setBackgroundColor(0xFF0A0C12);
        editorToolbar.setPadding(dp(4), dp(2), dp(4), dp(2));

        Button btnApply = makeSmallEditorBtn("▶ Aplicar", C_GREEN);
        btnApply.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					applyManualGLSL();
				}
			});
        editorToolbar.addView(btnApply);

        Button btnReset = makeSmallEditorBtn("↺ Recomp.", C_ORANGE);
        btnReset.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					recompileShader();
				}
			});
        editorToolbar.addView(btnReset);

        Button btnClear = makeSmallEditorBtn("✕ Limpiar", C_RED);
        btnClear.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					clearAll();
				}
			});
        editorToolbar.addView(btnClear);

        panel.addView(editorToolbar);

        // Editor GLSL (scroll horizontal + vertical)
        HorizontalScrollView hScroll = new HorizontalScrollView(this);
        hScroll.setFillViewport(true);
        ScrollView vScroll = new ScrollView(this);

        glslEditor = new EditText(this);
        glslEditor.setBackgroundColor(0xFF060810);
        glslEditor.setTextColor(C_ACCENT);
        glslEditor.setHintTextColor(C_MUTED);
        glslEditor.setHint("// GLSL generado automáticamente\n// o edita aquí manualmente...");
        glslEditor.setTypeface(Typeface.MONOSPACE);
        glslEditor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        glslEditor.setInputType(InputType.TYPE_CLASS_TEXT
								| InputType.TYPE_TEXT_FLAG_MULTI_LINE
								| InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        glslEditor.setGravity(Gravity.TOP | Gravity.LEFT);
        glslEditor.setPadding(dp(8), dp(8), dp(8), dp(8));
        glslEditor.setSingleLine(false);
        glslEditor.setMinLines(30);

        glslEditor.addTextChangedListener(new TextWatcher() {
				@Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
				@Override public void onTextChanged(CharSequence s, int st, int b, int c) {
					glslDirty = true;
					setStatus("Editor · cambios pendientes — pulsa ▶ Aplicar");
				}
				@Override public void afterTextChanged(Editable s) {}
			});

        vScroll.addView(glslEditor);
        hScroll.addView(vScroll);
        panel.addView(hScroll, new LinearLayout.LayoutParams(
						  LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // Pie del editor
        panel.addView(makeDividerH());
        TextView compileInfo = new TextView(this);
        compileInfo.setText("OpenGL ES 2.0  ·  Fragment Shader");
        compileInfo.setTextColor(C_MUTED);
        compileInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        compileInfo.setPadding(dp(8), dp(4), dp(8), dp(4));
        compileInfo.setBackgroundColor(C_BG);
        panel.addView(compileInfo);

        return panel;
    }

    private Button makeSmallEditorBtn(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(color);
        b.setBackgroundColor(C_PANEL2);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.WRAP_CONTENT, dp(30));
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        b.setLayoutParams(lp);
        return b;
    }

    // ═══════════════════════════════════════════════════════════
    //  EFECTOS PREDEFINIDOS (biblioteca de shaders)
    // ═══════════════════════════════════════════════════════════
    private void initBuiltinEffects() {

        // 1 — Color sólido
        effects.add(new ShaderEffect(
						"Color Sólido",
						"Rellena con un color plano configurable",
						0xFF00E5FF,
						// GLSL — función sin parámetro extra, usa solo uv y time
						"vec4 fx_colorSolido(vec2 uv) {\n"
						+ "    return vec4(0.2, 0.6, 1.0, 1.0);\n"
						+ "}",
						"u_colorSolido", "color_solido",
						FxCallType.SIMPLE));

        // 2 — Gradiente lineal
        effects.add(new ShaderEffect(
						"Gradiente Lineal",
						"Degradado de dos colores en eje Y",
						0xFF7C4DFF,
						"vec4 fx_gradienteLineal(vec2 uv) {\n"
						+ "    vec3 c1 = vec3(0.05, 0.12, 0.3);\n"
						+ "    vec3 c2 = vec3(0.6, 0.2, 0.9);\n"
						+ "    return vec4(mix(c1, c2, uv.y), 1.0);\n"
						+ "}",
						"u_gradiente", "gradiente",
						FxCallType.SIMPLE));

        // 3 — Glow radial
        effects.add(new ShaderEffect(
						"Brillo / Glow",
						"Resplandor luminoso desde el centro",
						0xFFFF4081,
						"vec4 fx_glow(vec2 uv, float intensidad) {\n"
						+ "    vec2 c = uv - 0.5;\n"
						+ "    float d = length(c);\n"
						+ "    float g = exp(-d * d * intensidad * 8.0);\n"
						+ "    return vec4(vec3(0.0, 0.8, 1.0) * g, 1.0);\n"
						+ "}",
						"u_glowIntensity", "glow_intensity",
						FxCallType.WITH_UNIFORM));

        // 4 — Ruido hash procedural
        effects.add(new ShaderEffect(
						"Ruido Procedural",
						"Textura de ruido hash animada",
						0xFFFF9100,
						"float fx_hash(vec2 p) {\n"
						+ "    p = fract(p * vec2(127.1, 311.7));\n"
						+ "    p += dot(p, p + 19.19);\n"
						+ "    return fract(p.x * p.y);\n"
						+ "}\n"
						+ "vec4 fx_ruido(vec2 uv, float escala) {\n"
						+ "    float n = fx_hash(floor(uv * escala + u_time * 0.5));\n"
						+ "    return vec4(n, n, n, 1.0);\n"
						+ "}",
						"u_noiseScale", "noise_scale",
						FxCallType.WITH_UNIFORM));

        // 5 — Aberración cromática
        effects.add(new ShaderEffect(
						"Aberración Cromática",
						"Separación RGB tipo lente óptica",
						0xFFFF6D00,
						"vec4 fx_aberracion(vec2 uv, float cantidad) {\n"
						+ "    float r = 0.5 + 0.5 * sin(uv.x * 10.0 + cantidad + u_time);\n"
						+ "    float g = 0.5 + 0.5 * sin(uv.y * 10.0 - cantidad + u_time * 1.3);\n"
						+ "    float b = 0.5 + 0.5 * sin((uv.x + uv.y) * 8.0 + u_time * 0.7);\n"
						+ "    return vec4(r, g, b, 1.0);\n"
						+ "}",
						"u_chromaAmount", "chroma_amount",
						FxCallType.WITH_UNIFORM));

        // 6 — Pixelado
        effects.add(new ShaderEffect(
						"Pixelado",
						"Reduce la resolución visualmente",
						0xFFAA00FF,
						"vec4 fx_pixelado(vec2 uv, float size) {\n"
						+ "    vec2 p = floor(uv / size) * size + size * 0.5;\n"
						+ "    vec3 col = 0.5 + 0.5 * sin(p.xyx * 6.2831 + vec3(0.0, 2.0, 4.0) + u_time);\n"
						+ "    return vec4(col, 1.0);\n"
						+ "}",
						"u_pixelSize", "pixel_size",
						FxCallType.WITH_UNIFORM));

        // 7 — Vignette
        effects.add(new ShaderEffect(
						"Vignette",
						"Oscurecimiento progresivo en bordes",
						0xFF37474F,
						"vec4 fx_vignette(vec2 uv, float fuerza) {\n"
						+ "    vec2 c = uv - 0.5;\n"
						+ "    float v = 1.0 - dot(c, c) * fuerza * 4.0;\n"
						+ "    vec3 base = 0.5 + 0.5 * cos(u_time + uv.xyx + vec3(0.0, 2.0, 4.0));\n"
						+ "    return vec4(base * clamp(v, 0.0, 1.0), 1.0);\n"
						+ "}",
						"u_vignetteFuerza", "vignette_strength",
						FxCallType.WITH_UNIFORM));

        // 8 — Onda seno
        effects.add(new ShaderEffect(
						"Onda Seno",
						"Distorsión sinusoidal animada",
						0xFF00BCD4,
						"vec4 fx_ondaSeno(vec2 uv, float amp) {\n"
						+ "    float freq = 8.0;\n"
						+ "    vec2 duv = uv;\n"
						+ "    duv.x += sin(uv.y * freq + u_time * 2.0) * amp * 0.1;\n"
						+ "    duv.y += sin(uv.x * freq + u_time * 1.5) * amp * 0.1;\n"
						+ "    vec3 col = 0.5 + 0.5 * cos(u_time + duv.xyx * 3.0 + vec3(0.0, 2.1, 4.2));\n"
						+ "    return vec4(col, 1.0);\n"
						+ "}",
						"u_ondaAmp", "wave_amplitude",
						FxCallType.WITH_UNIFORM));

        // 9 — Toon / Cel shading
        effects.add(new ShaderEffect(
						"Toon / Cel Shading",
						"Cuantiza colores estilo cartoon",
						0xFFCDDC39,
						"vec4 fx_toon(vec2 uv, float niveles) {\n"
						+ "    vec3 base = 0.5 + 0.5 * cos(u_time * 0.8 + uv.xyx + vec3(0.0, 2.0, 4.0));\n"
						+ "    float n = max(2.0, niveles);\n"
						+ "    return vec4(floor(base * n) / n, 1.0);\n"
						+ "}",
						"u_toonNiveles", "toon_levels",
						FxCallType.WITH_UNIFORM));

        // 10 — Scanlines
        effects.add(new ShaderEffect(
						"Scanlines",
						"Líneas horizontales estilo CRT/retro",
						0xFF4CAF50,
						"vec4 fx_scanlines(vec2 uv, float densidad) {\n"
						+ "    float s = sin(uv.y * densidad * 3.14159) * 0.5 + 0.5;\n"
						+ "    vec3 base = 0.5 + 0.5 * cos(u_time + uv.xyx + vec3(0.0, 2.0, 4.0));\n"
						+ "    return vec4(base * (0.7 + 0.3 * s), 1.0);\n"
						+ "}",
						"u_scanDensidad", "scan_density",
						FxCallType.WITH_UNIFORM));

        // 11 — Plasma
        effects.add(new ShaderEffect(
						"Plasma",
						"Efecto de plasma psicodélico animado",
						0xFFE91E63,
						"vec4 fx_plasma(vec2 uv) {\n"
						+ "    float v = 0.0;\n"
						+ "    v += sin(uv.x * 10.0 + u_time);\n"
						+ "    v += sin(uv.y * 8.0 + u_time * 0.9);\n"
						+ "    v += sin((uv.x + uv.y) * 6.0 + u_time * 1.1);\n"
						+ "    v += sin(length(uv - 0.5) * 12.0 - u_time * 1.5);\n"
						+ "    v = v * 0.25;\n"
						+ "    return vec4(sin(v * 3.14), sin(v * 3.14 + 2.1), sin(v * 3.14 + 4.2), 1.0);\n"
						+ "}",
						"u_plasma", "plasma",
						FxCallType.SIMPLE));

        // 12 — Checkerboard
        effects.add(new ShaderEffect(
						"Tablero",
						"Patrón ajedrezado con escala configurable",
						0xFF9E9E9E,
						"vec4 fx_tablero(vec2 uv, float escala) {\n"
						+ "    vec2 p = floor(uv * escala);\n"
						+ "    float c = mod(p.x + p.y, 2.0);\n"
						+ "    vec3 col = mix(vec3(0.1, 0.1, 0.15), vec3(0.7, 0.7, 0.8), c);\n"
						+ "    return vec4(col, 1.0);\n"
						+ "}",
						"u_tableroEscala", "tablero_scale",
						FxCallType.WITH_UNIFORM));
    }

    // ═══════════════════════════════════════════════════════════
    //  LÓGICA — Añadir efecto a la malla
    // ═══════════════════════════════════════════════════════════
    private void addEffectToMesh(ShaderEffect eff) {
        ShaderEffect placed = eff.copy();
        // Distribuir en cascada para que no se superpongan
        int idx = placedEffects.size();
        placed.x = dp(10) + (idx * dp(20)) % (dp(120));
        placed.y = dp(10) + (idx * dp(15)) % (dp(80));
        placed.w = dp(110);
        placed.h = dp(70);
        placedEffects.add(placed);
        meshView.invalidate();
        refreshAppliedBar();
        refreshParamList();
        recompileShader();
        setStatus("Efecto '" + eff.name + "' añadido  ·  " + placedEffects.size() + " efecto(s)");
    }

    private void removeEffect(ShaderEffect eff) {
        placedEffects.remove(eff);
        if (selectedEffect == eff) selectedEffect = null;
        meshView.invalidate();
        refreshAppliedBar();
        refreshParamList();
        recompileShader();
        setStatus("Efecto '" + eff.name + "' eliminado  ·  " + placedEffects.size() + " restante(s)");
    }

    // Actualiza la barra de chips inferior del panel central
    private void refreshAppliedBar() {
        if (appliedBar == null) return;
        appliedBar.removeAllViews();

        if (placedEffects.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Sin efectos aplicados");
            empty.setTextColor(C_MUTED);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            appliedBar.addView(empty);

            // Mostrar hint en la malla
            updateMeshHintVisibility(true);
            return;
        }

        updateMeshHintVisibility(false);

        // Cabecera de la barra
        TextView lbl = new TextView(this);
        lbl.setText("Efectos: ");
        lbl.setTextColor(C_MUTED);
        lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        appliedBar.addView(lbl, wrapLP());

        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setGravity(Gravity.CENTER_VERTICAL);

        for (final ShaderEffect eff : placedEffects) {
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setBackgroundColor(0xFF1F2436);
            chip.setPadding(dp(6), dp(4), dp(6), dp(4));
            chip.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
            chipLp.setMargins(dp(3), 0, dp(3), 0);
            chip.setLayoutParams(chipLp);

            // Dot de color
            View dot = new View(this);
            dot.setBackgroundColor(eff.color);
            LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(dp(8), dp(8));
            dLp.setMargins(0, 0, dp(4), 0);
            chip.addView(dot, dLp);

            // Nombre
            TextView chipTv = new TextView(this);
            chipTv.setText(eff.name);
            chipTv.setTextColor(selectedEffect == eff ? C_ACCENT : C_TEXT);
            chipTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            chip.addView(chipTv, wrapLP());

            // Botón × para eliminar desde la barra
            TextView rmBtn = new TextView(this);
            rmBtn.setText("  ×");
            rmBtn.setTextColor(C_RED);
            rmBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            rmBtn.setTypeface(null, Typeface.BOLD);
            chip.addView(rmBtn, wrapLP());

            rmBtn.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						removeEffect(eff);
					}
				});

            // Tocar el chip selecciona el efecto en la malla
            chip.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						selectedEffect = eff;
						meshView.invalidate();
						refreshAppliedBar();
					}
				});

            chipRow.addView(chip);
        }

        hs.addView(chipRow);
        appliedBar.addView(hs, new LinearLayout.LayoutParams(0,
															 LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    }

    private void updateMeshHintVisibility(boolean visible) {
        if (meshView == null) return;
        // Buscar el hint en el padre FrameLayout
        View hint = ((FrameLayout) meshView.getParent()).findViewWithTag("meshHint");
        if (hint != null) hint.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    // Actualiza la lista de parámetros activos en el panel izquierdo
    private void refreshParamList() {
        // Buscamos el paramList en la jerarquía
        LinearLayout paramList = null;
        // El panel izquierdo es el primero en el content LinearLayout
        // Lo ubicamos por tag
        View rootView = getWindow().getDecorView();
        paramList = (LinearLayout) rootView.findViewWithTag("paramList");
        if (paramList == null) return;

        paramList.removeAllViews();

        boolean any = false;
        for (final ShaderEffect eff : placedEffects) {
            if (eff.isConfigurable && eff.paramName != null && !eff.paramName.isEmpty()) {
                any = true;
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(4), 0, dp(4));

                View dot = new View(this);
                dot.setBackgroundColor(eff.color);
                LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(dp(6), dp(6));
                dLp.setMargins(0, 0, dp(6), 0);
                row.addView(dot, dLp);

                TextView info = new TextView(this);
                info.setText(eff.paramName + "\n" + eff.uniformName);
                info.setTextColor(C_TEXT);
                info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                row.addView(info, new LinearLayout.LayoutParams(0,
																LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                TextView effName = new TextView(this);
                effName.setText(eff.name);
                effName.setTextColor(C_MUTED);
                effName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
                row.addView(effName, wrapLP());

                paramList.addView(row);
                paramList.addView(makeDividerH());
            }
        }

        if (!any) {
            TextView empty = new TextView(this);
            empty.setText("Ninguno — selecciona un efecto\ny marca «Configurable»");
            empty.setTextColor(C_MUTED);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            empty.setPadding(0, dp(4), 0, 0);
            paramList.addView(empty);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  COMPILACIÓN GLSL — construye el fragment shader completo
    // ═══════════════════════════════════════════════════════════
    private void recompileShader() {
        StringBuilder sb = new StringBuilder();
        sb.append("precision mediump float;\n");
        sb.append("uniform float u_time;\n");
        sb.append("uniform vec2  u_resolution;\n");
        sb.append("varying vec2  v_texCoord;\n\n");

        // Declarar uniforms de parámetros configurables
        for (ShaderEffect eff : placedEffects) {
            if (eff.isConfigurable && eff.uniformName != null) {
                sb.append("uniform float ").append(eff.uniformName).append("; // ")
					.append(eff.paramName).append("\n");
            }
        }
        if (!placedEffects.isEmpty()) sb.append("\n");

        // Incluir funciones GLSL de cada efecto
        for (ShaderEffect eff : placedEffects) {
            sb.append("// ── ").append(eff.name).append(" ──────────────────────\n");
            sb.append(eff.glslCode).append("\n\n");
        }

        // main()
        sb.append("void main() {\n");
        sb.append("    vec2 uv = v_texCoord;\n");
        sb.append("    vec4 color = vec4(0.0);\n\n");

        if (placedEffects.isEmpty()) {
            // Shader de bienvenida cuando no hay efectos
            sb.append("    // Sin efectos — shader de bienvenida\n");
            sb.append("    color = vec4(0.5 + 0.5 * cos(u_time + uv.xyx + vec3(0.0,2.0,4.0)), 1.0);\n");
        } else {
            for (int i = 0; i < placedEffects.size(); i++) {
                ShaderEffect eff = placedEffects.get(i);
                sb.append("    // ").append(i + 1).append(". ").append(eff.name).append("\n");
                sb.append(buildEffectCall(eff));
                sb.append("\n");
            }
        }

        sb.append("    gl_FragColor = color;\n");
        sb.append("}\n");

        compiledGLSL = sb.toString();
        glslDirty = false;

        // Actualizar editor GLSL (panel derecho)
        if (glslEditor != null) {
            // Temporalmente quitamos el watcher para no marcarlo dirty
            glslEditor.removeTextChangedListener(null);
            glslEditor.setText(compiledGLSL);
        }

        // Enviar al renderer OpenGL
        if (renderer != null) {
            renderer.setFragmentShader(compiledGLSL);
            if (glView != null) glView.requestRender();
        }

        setStatus("Shader recompilado  ·  " + placedEffects.size() + " efecto(s)");
    }

    /**
     * Construye la llamada a la función GLSL de un efecto en el main().
     * Adapta la llamada según el tipo de firma (simple o con uniform).
     */
    private String buildEffectCall(ShaderEffect eff) {
        // Nombre seguro de la función GLSL (prefijo fx_)
        String fnName = "fx_" + safeFnName(eff.name);

        switch (eff.callType) {
            case SIMPLE:
                // vec4 fx_xxx(vec2 uv)
                return "    color = " + fnName + "(uv);\n";

            case WITH_UNIFORM:
                // vec4 fx_xxx(vec2 uv, float param)
                String paramVal = (eff.isConfigurable && eff.uniformName != null)
					? eff.uniformName
					: "1.0";
                return "    color = " + fnName + "(uv, " + paramVal + ");\n";

            default:
                return "    color = " + fnName + "(uv);\n";
        }
    }

    private String safeFnName(String name) {
        // Convierte "Onda Seno" -> "ondaSeno" (camelCase limpio)
        String[] parts = name.split("[^a-zA-ZáéíóúÁÉÍÓÚñÑ]+");
        StringBuilder fn = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            // Transliterar caracteres especiales básicos
            String p = transliterate(parts[i]);
            if (i == 0) {
                fn.append(p.substring(0, 1).toLowerCase(Locale.US));
            } else {
                fn.append(p.substring(0, 1).toUpperCase(Locale.US));
            }
            if (p.length() > 1) fn.append(p.substring(1).toLowerCase(Locale.US));
        }
        return fn.length() > 0 ? fn.toString() : "effect";
    }

    private String transliterate(String s) {
        return s.replace("á","a").replace("é","e").replace("í","i")
			.replace("ó","o").replace("ú","u").replace("ñ","n")
			.replace("Á","A").replace("É","E").replace("Í","I")
			.replace("Ó","O").replace("Ú","U").replace("Ñ","N")
			.replaceAll("[^a-zA-Z0-9]", "");
    }

    private void applyManualGLSL() {
        if (glslEditor == null) return;
        String code = glslEditor.getText().toString().trim();
        if (code.isEmpty()) {
            Toast.makeText(this, "El editor está vacío", Toast.LENGTH_SHORT).show();
            return;
        }
        if (renderer != null) {
            renderer.setFragmentShader(code);
            if (glView != null) glView.requestRender();
        }
        glslDirty = false;
        setStatus("Shader manual enviado al renderer  ·  OpenGL ES 2.0");
    }

    private void clearAll() {
        placedEffects.clear();
        selectedEffect = null;
        meshView.invalidate();
        refreshAppliedBar();
        refreshParamList();
        recompileShader();
        setStatus("Malla limpia  ·  0 efectos");
    }

    // ═══════════════════════════════════════════════════════════
    //  EXPORTACIÓN — flujo completo .demeter
    // ═══════════════════════════════════════════════════════════
    private void handleExportClick() {
        // API 29+ (Android 10+): getExternalFilesDir no necesita permiso de almacenamiento
        if (Build.VERSION.SDK_INT >= 29) {
            showExportDialog();
            return;
        }
        // API 23-28: pedir WRITE_EXTERNAL_STORAGE en runtime
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
				!= PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
									   android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
									   android.Manifest.permission.READ_EXTERNAL_STORAGE
								   }, REQ_STORAGE);
                return;
            }
        }
        // API < 23: permiso declarado en Manifest, sin runtime check
        showExportDialog();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        if (requestCode == REQ_STORAGE) {
            if (grantResults.length > 0
				&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showExportDialog();
            } else {
                Toast.makeText(this,
							   "Permiso de almacenamiento requerido para exportar",
							   Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showExportDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⬆  Exportar Shader — .demeter");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(16), dp(24), dp(8));
        layout.setBackgroundColor(C_PANEL);

        final EditText etName    = makeDialogField(layout, "Nombre del shader *",  "mi_shader");
        final EditText etAuthor  = makeDialogField(layout, "Autor *",               "");
        final EditText etVersion = makeDialogField(layout, "Versión *",             "1.0.0");
        final EditText etDesc    = makeDialogField(layout, "Descripción *",         "");

        // Info rápida del shader
        TextView info = new TextView(this);
        info.setText("Efectos incluidos: " + placedEffects.size()
					 + "  ·  Parámetros: " + countConfigurableParams());
        info.setTextColor(C_MUTED);
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT);
        iLp.setMargins(0, dp(12), 0, 0);
        layout.addView(info, iLp);

        builder.setView(layout);

        builder.setPositiveButton("Guardar .demeter", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					String name    = etName.getText().toString().trim();
					String author  = etAuthor.getText().toString().trim();
					String version = etVersion.getText().toString().trim();
					String desc    = etDesc.getText().toString().trim();

					if (name.isEmpty() || author.isEmpty()
                        || version.isEmpty() || desc.isEmpty()) {
						Toast.makeText(MainActivity.this,
									   "Todos los campos son obligatorios",
									   Toast.LENGTH_LONG).show();
						return;
					}
					exportDemeterFile(name, author, version, desc);
				}
			});

        builder.setNegativeButton("Cancelar", null);

        AlertDialog d = builder.create();
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawableResource(android.R.color.black);
        }
        d.show();

        Button pos = d.getButton(DialogInterface.BUTTON_POSITIVE);
        if (pos != null) pos.setTextColor(C_ACCENT);
        Button neg = d.getButton(DialogInterface.BUTTON_NEGATIVE);
        if (neg != null) neg.setTextColor(C_MUTED);
    }

    private int countConfigurableParams() {
        int n = 0;
        for (ShaderEffect eff : placedEffects) {
            if (eff.isConfigurable && eff.paramName != null && !eff.paramName.isEmpty()) n++;
        }
        return n;
    }

    private EditText makeDialogField(LinearLayout parent, String label, String defVal) {
        TextView lv = new TextView(this);
        lv.setText(label);
        lv.setTextColor(C_MUTED);
        lv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT);
        lLp.setMargins(0, dp(10), 0, dp(2));
        parent.addView(lv, lLp);

        EditText et = new EditText(this);
        et.setHint(label.replace(" *", ""));
        et.setHintTextColor(C_MUTED);
        et.setText(defVal);
        et.setTextColor(C_TEXT);
        et.setBackgroundColor(C_PANEL2);
        et.setPadding(dp(10), dp(8), dp(10), dp(8));
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        et.setSingleLine(true);
        parent.addView(et, new LinearLayout.LayoutParams(
						   LinearLayout.LayoutParams.MATCH_PARENT,
						   LinearLayout.LayoutParams.WRAP_CONTENT));
        return et;
    }

    /**
     * Genera y escribe el archivo .demeter según el formato del paquete Demeter.
     *
     * Formato:
     * ┌─────────────────────────────────────────┐
     * │ #DEMETER_SHADER v2                      │
     * │ @name=...                               │
     * │ @author=...                             │
     * │ @version=...                            │
     * │ @description=...                        │
     * │ @target=opengl_es_2                     │
     * │ @exported=YYYY-MM-DD HH:mm:ss           │
     * │ @tool=Demeter Shaders IDE (DSIDE)       │
     * │                                         │
     * │ #PARAMS                                 │
     * │ param <id> <uniform> float <default>    │
     * │                                         │
     * │ #NODES                                  │
     * │ node <id> x=N y=N w=N h=N              │
     * │                                         │
     * │ #FRAGMENT_SHADER                        │
     * │ ... GLSL code ...                       │
     * │ #END_SHADER                             │
     * └─────────────────────────────────────────┘
     */
    private void exportDemeterFile(String name, String author,
                                   String version, String desc) {
        String glsl = (glslEditor != null && !glslEditor.getText().toString().trim().isEmpty())
			? glslEditor.getText().toString()
			: compiledGLSL;

        StringBuilder demeter = new StringBuilder();

        // Cabecera
        demeter.append("#DEMETER_SHADER v2\n");
        demeter.append("@name=").append(name).append("\n");
        demeter.append("@author=").append(author).append("\n");
        demeter.append("@version=").append(version).append("\n");
        demeter.append("@description=").append(desc).append("\n");
        demeter.append("@target=opengl_es_2\n");
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
										 Locale.getDefault()).format(new Date());
        demeter.append("@exported=").append(ts).append("\n");
        demeter.append("@tool=Demeter Shaders IDE (DSIDE)\n");
        demeter.append("\n");

        // Parámetros configurables
        demeter.append("#PARAMS\n");
        boolean hasParams = false;
        for (ShaderEffect eff : placedEffects) {
            if (eff.isConfigurable
				&& eff.paramName  != null && !eff.paramName.isEmpty()
				&& eff.uniformName != null && !eff.uniformName.isEmpty()) {
                hasParams = true;
                demeter.append("param ")
					.append(eff.paramName.replaceAll("\\s+", "_")).append(" ")
					.append(eff.uniformName).append(" float 1.0")
					.append(" # ").append(eff.name).append("\n");
            }
        }
        if (!hasParams) demeter.append("# (sin parámetros configurables)\n");
        demeter.append("\n");

        // Nodos de la malla
        demeter.append("#NODES\n");
        for (ShaderEffect eff : placedEffects) {
            String nodeId = eff.name.toLowerCase(Locale.US)
				.replaceAll("[^a-z0-9]", "_");
            demeter.append("node ").append(nodeId)
				.append(" x=").append(Math.round(eff.x))
				.append(" y=").append(Math.round(eff.y))
				.append(" w=").append(Math.round(eff.w))
				.append(" h=").append(Math.round(eff.h))
				.append(" color=").append(String.format(Locale.US, "#%06X", (0xFFFFFF & eff.color)))
				.append("\n");
        }
        if (placedEffects.isEmpty()) demeter.append("# (sin nodos)\n");
        demeter.append("\n");

        // Código GLSL
        demeter.append("#FRAGMENT_SHADER\n");
        demeter.append(glsl);
        if (!glsl.endsWith("\n")) demeter.append("\n");
        demeter.append("#END_SHADER\n");

        // Escribir al disco
        try {
            File dir;
            if (Build.VERSION.SDK_INT >= 29) {
                // Android 10+: carpeta privada de la app, sin permiso necesario.
                // Ruta: /sdcard/Android/data/com.lexus2025.DSIDE/files/DemeterShaders
                dir = new File(getExternalFilesDir(null), "DemeterShaders");
            } else {
                // Android 6-9: almacenamiento publico (permiso ya concedido)
                dir = new File(Environment.getExternalStorageDirectory(), "DemeterShaders");
            }

            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                if (!created) {
                    Toast.makeText(this, "No se pudo crear la carpeta DemeterShaders",
								   Toast.LENGTH_LONG).show();
                    return;
                }
            }
            String safeName = name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            File outFile = new File(dir, safeName + ".demeter");
            BufferedWriter bw = new BufferedWriter(new FileWriter(outFile));
            bw.write(demeter.toString());
            bw.close();

            Toast.makeText(this,
						   "✓ Exportado:\n" + outFile.getAbsolutePath(),
						   Toast.LENGTH_LONG).show();
            setStatus("Exportado → " + outFile.getName());
            Log.i(TAG, "Shader exportado: " + outFile.getAbsolutePath());

        } catch (IOException e) {
            Log.e(TAG, "Error exportando: " + e.getMessage());
            Toast.makeText(this,
						   "Error al guardar: " + e.getMessage(),
						   Toast.LENGTH_LONG).show();
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Dialog opciones de efecto seleccionado en la malla
    // ═══════════════════════════════════════════════════════════
    private void showEffectOptions(final ShaderEffect eff) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("✦ " + eff.name);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(C_PANEL);
        layout.setPadding(dp(20), dp(14), dp(20), dp(14));

        // Info del efecto
        TextView infoTv = new TextView(this);
        infoTv.setText(eff.description);
        infoTv.setTextColor(C_MUTED);
        infoTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        layout.addView(infoTv, new LinearLayout.LayoutParams(
						   LinearLayout.LayoutParams.MATCH_PARENT,
						   LinearLayout.LayoutParams.WRAP_CONTENT));

        // Separador
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        divLp.setMargins(0, dp(10), 0, dp(10));
        View divider = new View(this);
        divider.setBackgroundColor(C_DIVIDER);
        divider.setLayoutParams(divLp);
        layout.addView(divider);

        // Toggle — marcar como configurable
        TextView paramTitle = new TextView(this);
        paramTitle.setText("Parámetro expuesto al usuario:");
        paramTitle.setTextColor(C_TEXT);
        paramTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        paramTitle.setTypeface(null, Typeface.BOLD);
        layout.addView(paramTitle, new LinearLayout.LayoutParams(
						   LinearLayout.LayoutParams.MATCH_PARENT,
						   LinearLayout.LayoutParams.WRAP_CONTENT));

        final CheckBox chk = new CheckBox(this);
        chk.setChecked(eff.isConfigurable);
        chk.setTextColor(C_TEXT);
        chk.setText("  Marcar como configurable");
        chk.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams chkLp = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT);
        chkLp.setMargins(0, dp(6), 0, 0);
        layout.addView(chk, chkLp);

        // Campo nombre del parámetro
        TextView paramNameLbl = new TextView(this);
        paramNameLbl.setText("Nombre del parámetro:");
        paramNameLbl.setTextColor(C_MUTED);
        paramNameLbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        LinearLayout.LayoutParams pnLblLp = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT);
        pnLblLp.setMargins(0, dp(10), 0, dp(2));
        layout.addView(paramNameLbl, pnLblLp);

        final EditText etParamName = new EditText(this);
        etParamName.setHint("ej: intensidad, opacidad...");
        etParamName.setHintTextColor(C_MUTED);
        etParamName.setText(eff.paramName != null ? eff.paramName : "");
        etParamName.setTextColor(C_TEXT);
        etParamName.setBackgroundColor(C_PANEL2);
        etParamName.setPadding(dp(10), dp(8), dp(10), dp(8));
        etParamName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        etParamName.setSingleLine(true);
        etParamName.setEnabled(eff.isConfigurable);
        etParamName.setAlpha(eff.isConfigurable ? 1f : 0.4f);
        layout.addView(etParamName, new LinearLayout.LayoutParams(
						   LinearLayout.LayoutParams.MATCH_PARENT,
						   LinearLayout.LayoutParams.WRAP_CONTENT));

        // Preview del nombre de uniform generado
        final TextView uniformPreview = new TextView(this);
        uniformPreview.setTextColor(C_ACCENT2);
        uniformPreview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        uniformPreview.setTypeface(Typeface.MONOSPACE);
        String pn0 = eff.paramName != null ? eff.paramName : "";
        uniformPreview.setText(pn0.isEmpty() ? "" : "→ uniform: u_" + pn0.replaceAll("[^a-zA-Z0-9]", ""));
        LinearLayout.LayoutParams upLp = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT);
        upLp.setMargins(0, dp(3), 0, 0);
        layout.addView(uniformPreview, upLp);

        // Listeners
        chk.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton btn, boolean checked) {
					eff.isConfigurable = checked;
					etParamName.setEnabled(checked);
					etParamName.setAlpha(checked ? 1f : 0.4f);
				}
			});

        etParamName.addTextChangedListener(new TextWatcher() {
				@Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
				@Override public void onTextChanged(CharSequence s, int st, int be, int c) {
					String safe = s.toString().replaceAll("[^a-zA-Z0-9]", "");
					uniformPreview.setText(safe.isEmpty() ? "" : "→ uniform: u_" + safe);
				}
				@Override public void afterTextChanged(Editable s) {}
			});

        b.setView(layout);

        b.setPositiveButton("Aplicar", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					String pn = etParamName.getText().toString().trim();
					eff.isConfigurable = chk.isChecked();
					if (eff.isConfigurable && !pn.isEmpty()) {
						eff.paramName  = pn;
						eff.uniformName = "u_" + pn.replaceAll("[^a-zA-Z0-9]", "");
					} else if (!eff.isConfigurable) {
						// No borrar el nombre, por si el usuario lo reactiva
					}
					recompileShader();
					meshView.invalidate();
					refreshAppliedBar();
					refreshParamList();
					setStatus("Efecto '" + eff.name + "' actualizado");
				}
			});

        b.setNeutralButton("Eliminar efecto", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					removeEffect(eff);
				}
			});

        b.setNegativeButton("Cancelar", null);

        AlertDialog d = b.create();
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawableResource(android.R.color.black);
        }
        d.show();

        Button pos = d.getButton(DialogInterface.BUTTON_POSITIVE);
        if (pos != null) pos.setTextColor(C_ACCENT);
        Button neu = d.getButton(DialogInterface.BUTTON_NEUTRAL);
        if (neu != null) neu.setTextColor(C_RED);
        Button neg = d.getButton(DialogInterface.BUTTON_NEGATIVE);
        if (neg != null) neg.setTextColor(C_MUTED);
    }

    // ═══════════════════════════════════════════════════════════
    //  HELPERS UI
    // ═══════════════════════════════════════════════════════════
    private TextView makePanelHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(C_ACCENT);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setBackgroundColor(0xFF0A0C14);
        tv.setPadding(dp(10), dp(6), dp(10), dp(6));
        return tv;
    }

    private View makeDividerH() {
        View v = new View(this);
        v.setBackgroundColor(C_DIVIDER);
        v.setLayoutParams(new LinearLayout.LayoutParams(
							  LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        return v;
    }

    private View makeDividerV() {
        View v = new View(this);
        v.setBackgroundColor(C_DIVIDER);
        return v;
    }

    private LinearLayout.LayoutParams wrapLP() {
        return new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.WRAP_CONTENT,
			LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private void setStatus(final String msg) {
        if (statusBar == null) return;
        runOnUiThread(new Runnable() {
				@Override
				public void run() {
					statusBar.setText("DSIDE  ·  " + msg);
				}
			});
    }

    private int dp(int val) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, dm);
    }

    // ═══════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════════
    @Override
    protected void onPause() {
        super.onPause();
        if (glView != null) glView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (glView != null) glView.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "DSIDE destruido");
    }

    // ═══════════════════════════════════════════════════════════
    //  INNER ENUM — Tipo de firma de función GLSL
    // ═══════════════════════════════════════════════════════════
    enum FxCallType {
        SIMPLE,       // fx_xxx(vec2 uv)
        WITH_UNIFORM  // fx_xxx(vec2 uv, float param)
		}

    // ═══════════════════════════════════════════════════════════
    //  INNER CLASS — ShaderEffect
    // ═══════════════════════════════════════════════════════════
    static class ShaderEffect {
        String     name;
        String     description;
        int        color;
        String     glslCode;
        String     uniformName;
        String     paramName;
        FxCallType callType;
        boolean    isConfigurable = false;
        float      x, y, w, h;

        ShaderEffect(String name, String desc, int color,
                     String glsl, String uniform, String param,
                     FxCallType callType) {
            this.name        = name;
            this.description = desc;
            this.color       = color;
            this.glslCode    = glsl;
            this.uniformName = uniform;
            this.paramName   = param;
            this.callType    = callType;
        }

        ShaderEffect copy() {
            ShaderEffect c = new ShaderEffect(name, description, color,
											  glslCode, uniformName, paramName, callType);
            c.isConfigurable = isConfigurable;
            return c;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  INNER CLASS — MeshCanvasView
    //  Panel central overlay: gizmo 2D, selección, mover, escalar
    // ═══════════════════════════════════════════════════════════
    class MeshCanvasView extends View {

        private final Paint paintEffect  = new Paint();
        private final Paint paintBorder  = new Paint();
        private final Paint paintText    = new Paint();
        private final Paint paintGrid    = new Paint();
        private final Paint paintGizmo   = new Paint();
        private final Paint paintHandle  = new Paint();
        private final Paint paintHandleInner = new Paint();
        private final Paint paintMoveHandle  = new Paint();
        private final Paint paintMoveIcon    = new Paint();
        private final Paint paintCfgDot      = new Paint();
        private final Paint paintEmptyHint   = new Paint();

        private float touchStartX, touchStartY;
        private float origX, origY, origW, origH;
        private boolean dragging    = false;
        private boolean resizing    = false;
        private int     resizeCorner = 0; // 1=TL 2=TR 3=BL 4=BR

        private static final float HANDLE_R = 20f;
        private long lastTapTime = 0;

        private final ScaleGestureDetector scaleDetector;

        MeshCanvasView(Context ctx) {
            super(ctx);
            setBackgroundColor(Color.TRANSPARENT);

            paintEffect.setStyle(Paint.Style.FILL);
            paintEffect.setAlpha(180);

            paintBorder.setStyle(Paint.Style.STROKE);
            paintBorder.setAntiAlias(true);

            paintText.setColor(Color.WHITE);
            paintText.setTextSize(26f);
            paintText.setAntiAlias(true);
            paintText.setTypeface(Typeface.DEFAULT_BOLD);
            paintText.setShadowLayer(3f, 1f, 1f, 0xFF000000);

            paintGrid.setColor(0x18FFFFFF);
            paintGrid.setStyle(Paint.Style.STROKE);
            paintGrid.setStrokeWidth(1f);

            paintGizmo.setColor(C_ACCENT);
            paintGizmo.setStyle(Paint.Style.STROKE);
            paintGizmo.setStrokeWidth(3f);
            paintGizmo.setAntiAlias(true);
            paintGizmo.setPathEffect(new DashPathEffect(new float[]{12f, 6f}, 0f));

            paintHandle.setColor(C_ACCENT);
            paintHandle.setStyle(Paint.Style.FILL);
            paintHandle.setAntiAlias(true);

            paintHandleInner.setColor(C_BG);
            paintHandleInner.setStyle(Paint.Style.FILL);
            paintHandleInner.setAntiAlias(true);

            paintMoveHandle.setColor(C_ACCENT2);
            paintMoveHandle.setStyle(Paint.Style.FILL);
            paintMoveHandle.setAntiAlias(true);

            paintMoveIcon.setColor(Color.WHITE);
            paintMoveIcon.setStrokeWidth(4f);
            paintMoveIcon.setStyle(Paint.Style.STROKE);
            paintMoveIcon.setAntiAlias(true);
            paintMoveIcon.setStrokeCap(Paint.Cap.ROUND);

            paintCfgDot.setColor(C_GREEN);
            paintCfgDot.setStyle(Paint.Style.FILL);
            paintCfgDot.setAntiAlias(true);

            paintEmptyHint.setColor(C_MUTED);
            paintEmptyHint.setTextSize(36f);
            paintEmptyHint.setTextAlign(Paint.Align.CENTER);
            paintEmptyHint.setAntiAlias(true);

            scaleDetector = new ScaleGestureDetector(ctx,
				new ScaleGestureDetector.SimpleOnScaleGestureListener() {
					@Override
					public boolean onScale(ScaleGestureDetector det) {
						if (selectedEffect != null) {
							float f = det.getScaleFactor();
							selectedEffect.w = Math.max(dp(50), selectedEffect.w * f);
							selectedEffect.h = Math.max(dp(35), selectedEffect.h * f);
							invalidate();
						}
						return true;
					}
				});
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int W = getWidth();
            int H = getHeight();

            // Grid de fondo
            float step = dp(40);
            for (float x = 0; x < W; x += step)
                canvas.drawLine(x, 0, x, H, paintGrid);
            for (float y = 0; y < H; y += step)
                canvas.drawLine(0, y, W, y, paintGrid);

            if (placedEffects.isEmpty()) return;

            // Dibujar efectos (de atrás hacia adelante)
            for (ShaderEffect eff : placedEffects) {
                RectF r = new RectF(eff.x, eff.y, eff.x + eff.w, eff.y + eff.h);
                float radius = dp(10);

                // Relleno semitransparente
                paintEffect.setColor(eff.color);
                paintEffect.setAlpha(150);
                canvas.drawRoundRect(r, radius, radius, paintEffect);

                // Borde
                if (eff == selectedEffect) {
                    paintBorder.setColor(C_ACCENT);
                    paintBorder.setStrokeWidth(4f);
                } else {
                    paintBorder.setColor(eff.color);
                    paintBorder.setStrokeWidth(2f);
                    paintBorder.setPathEffect(null);
                }
                canvas.drawRoundRect(r, radius, radius, paintBorder);

                // Nombre del efecto
                float textX = eff.x + dp(10);
                float textY = eff.y + dp(22);
                if (textY < eff.y + eff.h - dp(4)) {
                    canvas.drawText(eff.name, textX, textY, paintText);
                }

                // Indicador configurable (punto verde en esquina superior derecha)
                if (eff.isConfigurable) {
                    canvas.drawCircle(eff.x + eff.w - dp(10), eff.y + dp(10),
									  dp(6), paintCfgDot);
                }
            }

            // Gizmo del efecto seleccionado
            if (selectedEffect != null) {
                ShaderEffect eff = selectedEffect;
                RectF r = new RectF(eff.x, eff.y, eff.x + eff.w, eff.y + eff.h);

                // Marco gizmo punteado
                paintGizmo.setPathEffect(new DashPathEffect(new float[]{dp(12), dp(6)}, 0));
                canvas.drawRoundRect(r, dp(10), dp(10), paintGizmo);

                // Handles en las 4 esquinas
                float[] hx = {eff.x, eff.x + eff.w, eff.x,          eff.x + eff.w};
                float[] hy = {eff.y, eff.y,           eff.y + eff.h, eff.y + eff.h};
                for (int i = 0; i < 4; i++) {
                    canvas.drawCircle(hx[i], hy[i], HANDLE_R,         paintHandle);
                    canvas.drawCircle(hx[i], hy[i], HANDLE_R - dp(4), paintHandleInner);
                }

                // Handle central de movimiento
                float mx = eff.x + eff.w / 2f;
                float my = eff.y + eff.h / 2f;
                canvas.drawCircle(mx, my, HANDLE_R + dp(4), paintMoveHandle);
                float cross = dp(10);
                // Cruz de movimiento
                canvas.drawLine(mx - cross, my, mx + cross, my, paintMoveIcon);
                canvas.drawLine(mx, my - cross, mx, my + cross, paintMoveIcon);
                // Flechas (pequeños triángulos dibujados como líneas)
                float a = dp(5);
                canvas.drawLine(mx - cross, my, mx - cross + a, my - a, paintMoveIcon);
                canvas.drawLine(mx - cross, my, mx - cross + a, my + a, paintMoveIcon);
                canvas.drawLine(mx + cross, my, mx + cross - a, my - a, paintMoveIcon);
                canvas.drawLine(mx + cross, my, mx + cross - a, my + a, paintMoveIcon);
                canvas.drawLine(mx, my - cross, mx - a, my - cross + a, paintMoveIcon);
                canvas.drawLine(mx, my - cross, mx + a, my - cross + a, paintMoveIcon);
                canvas.drawLine(mx, my + cross, mx - a, my + cross - a, paintMoveIcon);
                canvas.drawLine(mx, my + cross, mx + a, my + cross - a, paintMoveIcon);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);

            float ex = event.getX();
            float ey = event.getY();

            switch (event.getActionMasked()) {

                case MotionEvent.ACTION_DOWN: {
						touchStartX = ex;
						touchStartY = ey;
						resizing = false;
						dragging = false;
						resizeCorner = 0;

						// Verificar handles de esquina del efecto seleccionado
						if (selectedEffect != null) {
							ShaderEffect eff = selectedEffect;
							float[] hx = {eff.x, eff.x + eff.w, eff.x,          eff.x + eff.w};
							float[] hy = {eff.y, eff.y,           eff.y + eff.h, eff.y + eff.h};
							for (int i = 0; i < 4; i++) {
								float dx = ex - hx[i];
								float dy = ey - hy[i];
								if ((float) Math.sqrt(dx * dx + dy * dy) < HANDLE_R * 2f) {
									resizing = true;
									resizeCorner = i + 1;
									origX = eff.x; origY = eff.y;
									origW = eff.w; origH = eff.h;
									return true;
								}
							}
						}

						// Hit-test en efectos (más reciente primero)
						ShaderEffect hit = null;
						for (int i = placedEffects.size() - 1; i >= 0; i--) {
							ShaderEffect e = placedEffects.get(i);
							if (ex >= e.x && ex <= e.x + e.w
                                && ey >= e.y && ey <= e.y + e.h) {
								hit = e;
								break;
							}
						}

						if (hit != null) {
							boolean wasSelected = (hit == selectedEffect);
							selectedEffect = hit;
							dragging = true;
							origX = hit.x; origY = hit.y;

							// Doble tap (< 300ms) → abrir opciones
							long now = System.currentTimeMillis();
							if (wasSelected && (now - lastTapTime) < 300) {
								dragging = false;
								showEffectOptions(hit);
							}
							lastTapTime = now;
						} else {
							selectedEffect = null;
						}

						invalidate();
						refreshAppliedBar();
						return true;
					}

                case MotionEvent.ACTION_MOVE: {
						if (scaleDetector.isInProgress()) return true;
						float dx = ex - touchStartX;
						float dy = ey - touchStartY;

						if (resizing && selectedEffect != null) {
							ShaderEffect eff = selectedEffect;
							float minW = dp(50), minH = dp(35);
							switch (resizeCorner) {
								case 1: // TL — mover esquina superior-izquierda
									eff.x = Math.min(origX + dx, origX + origW - minW);
									eff.y = Math.min(origY + dy, origY + origH - minH);
									eff.w = origW - (eff.x - origX);
									eff.h = origH - (eff.y - origY);
									break;
								case 2: // TR — esquina superior-derecha
									eff.y = Math.min(origY + dy, origY + origH - minH);
									eff.w = Math.max(minW, origW + dx);
									eff.h = origH - (eff.y - origY);
									break;
								case 3: // BL — esquina inferior-izquierda
									eff.x = Math.min(origX + dx, origX + origW - minW);
									eff.w = origW - (eff.x - origX);
									eff.h = Math.max(minH, origH + dy);
									break;
								case 4: // BR — esquina inferior-derecha
									eff.w = Math.max(minW, origW + dx);
									eff.h = Math.max(minH, origH + dy);
									break;
							}
							invalidate();

						} else if (dragging && selectedEffect != null) {
							selectedEffect.x = origX + dx;
							selectedEffect.y = origY + dy;
							invalidate();
						}
						return true;
					}

                case MotionEvent.ACTION_UP:
                    dragging  = false;
                    resizing  = false;
                    recompileShader();
                    invalidate();
                    return true;
            }
            return super.onTouchEvent(event);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  INNER CLASS — DemeterGLSurfaceView
    // ═══════════════════════════════════════════════════════════
    class DemeterGLSurfaceView extends GLSurfaceView {
        DemeterGLSurfaceView(Context ctx) {
            super(ctx);
            setEGLContextClientVersion(2);
            setPreserveEGLContextOnPause(true);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  INNER CLASS — DemeterRenderer (OpenGL ES 2.0)
    // ═══════════════════════════════════════════════════════════
    class DemeterRenderer implements GLSurfaceView.Renderer {

        private int programId  = 0;
        private int vertShadId = 0;
        private int fragShadId = 0;

        private FloatBuffer quadVerts;
        private final long startTime = System.currentTimeMillis();

        private volatile String pendingFragSrc = null;

        // Locations de uniforms/atributos
        private int uTime, uResolution, aPosition, aTexCoord;

        // ─── Vertex Shader (fijo) ─────────────────────────────
        private static final String VERT_SRC =
		"attribute vec4 a_position;\n" +
		"attribute vec2 a_texCoord;\n" +
		"varying   vec2 v_texCoord;\n" +
		"void main() {\n" +
		"    gl_Position = a_position;\n" +
		"    v_texCoord  = a_texCoord;\n" +
		"}\n";

        // ─── Fragment por defecto (sin efectos) ───────────────
        private String getDefaultFrag() {
            return
                "precision mediump float;\n" +
                "uniform float u_time;\n" +
                "uniform vec2  u_resolution;\n" +
                "varying vec2  v_texCoord;\n" +
                "void main() {\n" +
                "    vec2 uv = v_texCoord;\n" +
                "    vec3 col = 0.5 + 0.5 * cos(u_time + uv.xyx + vec3(0.0, 2.0, 4.0));\n" +
                "    gl_FragColor = vec4(col, 1.0);\n" +
                "}\n";
        }

        void setFragmentShader(String src) {
            pendingFragSrc = src;
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            GLES20.glClearColor(0.05f, 0.06f, 0.08f, 1f);
            setupQuad();
            buildProgram(getDefaultFrag());
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            // Aplicar shader pendiente
            String pending = pendingFragSrc;
            if (pending != null) {
                pendingFragSrc = null;
                rebuildProgram(pending);
            }

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            if (programId == 0) return;

            GLES20.glUseProgram(programId);

            float t = (System.currentTimeMillis() - startTime) / 1000.0f;
            GLES20.glUniform1f(uTime, t);
            GLES20.glUniform2f(uResolution, 512f, 512f);

            quadVerts.position(0);
            GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT,
										 false, 16, quadVerts);
            GLES20.glEnableVertexAttribArray(aPosition);

            quadVerts.position(2);
            GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT,
										 false, 16, quadVerts);
            GLES20.glEnableVertexAttribArray(aTexCoord);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            GLES20.glDisableVertexAttribArray(aPosition);
            GLES20.glDisableVertexAttribArray(aTexCoord);
        }

        private void setupQuad() {
            float[] verts = {
				// x,   y,   u,   v
				-1f, -1f,  0f,  0f,
				1f, -1f,  1f,  0f,
				-1f,  1f,  0f,  1f,
				1f,  1f,  1f,  1f,
            };
            quadVerts = ByteBuffer.allocateDirect(verts.length * 4)
				.order(ByteOrder.nativeOrder())
				.asFloatBuffer();
            quadVerts.put(verts).position(0);
        }

        private void buildProgram(String fragSrc) {
            vertShadId = compileShader(GLES20.GL_VERTEX_SHADER,   VERT_SRC);
            fragShadId = compileShader(GLES20.GL_FRAGMENT_SHADER, fragSrc);
            if (vertShadId == 0 || fragShadId == 0) {
                setStatus("Error compilando shader — ver Logcat");
                return;
            }

            programId = GLES20.glCreateProgram();
            GLES20.glAttachShader(programId, vertShadId);
            GLES20.glAttachShader(programId, fragShadId);
            GLES20.glLinkProgram(programId);

            int[] status = new int[1];
            GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, status, 0);
            if (status[0] == 0) {
                String log = GLES20.glGetProgramInfoLog(programId);
                Log.e(TAG, "Link error: " + log);
                GLES20.glDeleteProgram(programId);
                programId = 0;
                setStatus("Error enlazando programa GLSL");
                return;
            }

            aPosition   = GLES20.glGetAttribLocation (programId, "a_position");
            aTexCoord   = GLES20.glGetAttribLocation (programId, "a_texCoord");
            uTime       = GLES20.glGetUniformLocation(programId, "u_time");
            uResolution = GLES20.glGetUniformLocation(programId, "u_resolution");
        }

        private void rebuildProgram(String fragSrc) {
            if (programId  != 0) { GLES20.glDeleteProgram(programId);  programId  = 0; }
            if (vertShadId != 0) { GLES20.glDeleteShader(vertShadId);  vertShadId = 0; }
            if (fragShadId != 0) { GLES20.glDeleteShader(fragShadId);  fragShadId = 0; }
            buildProgram(fragSrc);
        }

        private int compileShader(int type, String src) {
            int id = GLES20.glCreateShader(type);
            GLES20.glShaderSource(id, src);
            GLES20.glCompileShader(id);
            int[] status = new int[1];
            GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) {
                String log = GLES20.glGetShaderInfoLog(id);
                Log.e(TAG, "Shader compile error:\n" + log);
                GLES20.glDeleteShader(id);
                setStatus("Error GLSL — " + log.substring(0, Math.min(60, log.length())));
                return 0;
            }
            return id;
        }
    }
}
// ═══════════════════════════════════════════════════════════════════
//  FIN — MainActivity.java · Demeter Shaders IDE
//  com.lexus2025.DSIDE · OpenGL ES 2.0 · Java 7 · Monolito absoluto
// ═══════════════════════════════════════════════════════════════════


