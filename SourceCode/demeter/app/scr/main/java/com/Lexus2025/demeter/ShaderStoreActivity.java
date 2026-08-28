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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tienda de shaders. Descarga un índice Markdown desde GitHub (URL hardcodeada)
 * y muestra los shaders agrupados por categoría. Cada entrada puede tener:
 *   - Nombre        (obligatorio)
 *   - Autor         (obligatorio)
 *   - Enlace        (obligatorio) — URL directa al .demeter
 *   - Imagen        (opcional)    — URL a PNG/JPG de preview
 *   - Descripción   (opcional)
 *
 * Formato esperado del .md:
 *
 *   ## Categoría: Nombre de categoría
 *
 *   ### Nombre del shader
 *   - **Autor:** Fulano
 *   - **Enlace:** https://raw.githubusercontent.com/.../shader.demeter
 *   - **Imagen:** https://...imagen.png          ← opcional
 *   - **Descripción:** Texto libre.              ← opcional
 *
 * Resultado: RESULT_OK si se instaló al menos un shader. La Activity
 * devuelve en el Intent el nombre del último instalado (RESULT_EXTRA_NAME).
 */
public class ShaderStoreActivity extends Activity {

    // ─── URL del índice en GitHub ─────────────────────────────────────────────
    // Cambia esto a la URL raw de tu archivo .md en GitHub.
    private static final String INDEX_URL =
	"https://raw.githubusercontent.com/LexusYTG/Demeter-reshader/main/Store/store_index.md";

    public static final String RESULT_EXTRA_NAME = "installed_name";

    // ─── Modelo interno ───────────────────────────────────────────────────────

    private static class ShaderEntry {
        String name;
        String author;
        String downloadUrl;
        String imageUrl;    // puede ser null
        String description; // puede ser null
    }

    private static class Category {
        String title;
        List<ShaderEntry> entries = new ArrayList<>();
    }

    // ─── Estado ───────────────────────────────────────────────────────────────

    private final ExecutorService mPool = Executors.newCachedThreadPool();
    private final Handler         mUi   = new Handler(Looper.getMainLooper());

    private ModuleManager mModuleManager;
    private ScrollView    mScroll;
    private LinearLayout  mContainer;
    private TextView      mTvError;
    private boolean       mInstalledAny = false;

    // ─── Ciclo de vida ────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Pantalla completa, igual que el resto de la app
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        mModuleManager = new ModuleManager(this);

        buildUi();
        fetchIndex();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPool.shutdownNow();
    }

    // ─── UI construida en código (sin XML extra) ──────────────────────────────

    private void buildUi() {
        int dp = (int) getResources().getDisplayMetrics().density;

        // Raíz
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF121212);

        // ── Barra superior ──
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setBackgroundColor(0xFF1E1E2E);
        toolbar.setPadding(12 * dp, 12 * dp, 12 * dp, 12 * dp);

        Button btnBack = new Button(this);
        btnBack.setText("← Volver");
        btnBack.setTextColor(0xFF80CBC4);
        btnBack.setBackgroundColor(0x00000000);
        btnBack.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { finish(); }
			});
        toolbar.addView(btnBack,
						new LinearLayout.LayoutParams(
							LinearLayout.LayoutParams.WRAP_CONTENT,
							LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Tienda de Shaders");
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTextSize(18f);
        tvTitle.setPadding(12 * dp, 0, 0, 0);
        LinearLayout.LayoutParams lpTitle = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lpTitle.gravity = android.view.Gravity.CENTER_VERTICAL;
        toolbar.addView(tvTitle, lpTitle);

        root.addView(toolbar,
					 new LinearLayout.LayoutParams(
						 LinearLayout.LayoutParams.MATCH_PARENT,
						 LinearLayout.LayoutParams.WRAP_CONTENT));

        // ── Mensaje de error (oculto hasta que falle) ──
        mTvError = new TextView(this);
        mTvError.setTextColor(0xFFFF5252);
        mTvError.setPadding(16 * dp, 16 * dp, 16 * dp, 0);
        mTvError.setVisibility(View.GONE);
        root.addView(mTvError,
					 new LinearLayout.LayoutParams(
						 LinearLayout.LayoutParams.MATCH_PARENT,
						 LinearLayout.LayoutParams.WRAP_CONTENT));

        // ── Scroll con el catálogo ──
        mScroll = new ScrollView(this);
        mContainer = new LinearLayout(this);
        mContainer.setOrientation(LinearLayout.VERTICAL);
        mContainer.setPadding(0, 8 * dp, 0, 24 * dp);
        mScroll.addView(mContainer);
        root.addView(mScroll,
					 new LinearLayout.LayoutParams(
						 LinearLayout.LayoutParams.MATCH_PARENT,
						 0, 1f));

        setContentView(root);
    }

    // ─── Descarga y parseo del índice ─────────────────────────────────────────

    private void fetchIndex() {
        // Mostrar spinner mientras carga
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Cargando catálogo…");
        pd.setCancelable(false);
        pd.show();

        mPool.execute(new Runnable() {
				@Override
				public void run() {
					try {
						final String markdown = downloadString(INDEX_URL);
						final List<Category> categories = parseMarkdown(markdown);
						mUi.post(new Runnable() {
								@Override public void run() {
									pd.dismiss();
									renderCatalog(categories);
								}
							});
					} catch (final Exception e) {
						mUi.post(new Runnable() {
								@Override public void run() {
									pd.dismiss();
									mTvError.setVisibility(View.VISIBLE);
									mTvError.setText("No se pudo cargar el catálogo:\n" + e.getMessage());
								}
							});
					}
				}
			});
    }

    // ─── Parseo Markdown ─────────────────────────────────────────────────────
    //
    // El parser es deliberadamente simple y tolerante a errores.
    // Lee línea por línea; detecta:
    //   ## Categoría: Nombre    →  nueva categoría
    //   ### Nombre del shader   →  nueva entrada dentro de la categoría actual
    //   - **Autor:** xxx        →  campo de la entrada en curso
    //   - **Enlace:** xxx
    //   - **Imagen:** xxx
    //   - **Descripción:** xxx
    //
    private List<Category> parseMarkdown(String md) {
        List<Category> categories = new ArrayList<>();
        Category currentCat  = null;
        ShaderEntry currentEntry = null;

        for (String rawLine : md.split("\n")) {
            String line = rawLine.trim();

            if (line.startsWith("## Categoría:") || line.startsWith("## Categoria:")) {
                // Guardar entrada anterior si existe
                if (currentEntry != null && currentCat != null && isEntryValid(currentEntry)) {
                    currentCat.entries.add(currentEntry);
                }
                currentEntry = null;

                currentCat = new Category();
                currentCat.title = line.replaceFirst("##\\s*Categor[ií]a:\\s*", "").trim();
                categories.add(currentCat);

            } else if (line.startsWith("### ")) {
                // Guardar entrada anterior
                if (currentEntry != null && currentCat != null && isEntryValid(currentEntry)) {
                    currentCat.entries.add(currentEntry);
                }
                currentEntry = new ShaderEntry();
                currentEntry.name = line.substring(4).trim();

            } else if (currentEntry != null) {
                // Campos de la entrada actual
                if (line.startsWith("- **Autor:**")) {
                    currentEntry.author = extractField(line, "Autor");
                } else if (line.startsWith("- **Enlace:**")) {
                    currentEntry.downloadUrl = extractField(line, "Enlace");
                } else if (line.startsWith("- **Imagen:**")) {
                    currentEntry.imageUrl = extractField(line, "Imagen");
                } else if (line.startsWith("- **Descripción:**") || line.startsWith("- **Descripcion:**")) {
                    currentEntry.description = extractField(line, "Descripc?i[oó]n");
                }
            }
        }
        // Última entrada del documento
        if (currentEntry != null && currentCat != null && isEntryValid(currentEntry)) {
            currentCat.entries.add(currentEntry);
        }

        return categories;
    }

    private String extractField(String line, String fieldRegex) {
        // "- **Campo:** valor" → "valor"
        return line.replaceFirst("^-\\s*\\*\\*" + fieldRegex + ":\\*\\*\\s*", "").trim();
    }

    private boolean isEntryValid(ShaderEntry e) {
        return e.name != null && !e.name.isEmpty()
            && e.author != null && !e.author.isEmpty()
            && e.downloadUrl != null && e.downloadUrl.startsWith("http");
    }

    // ─── Renderizado del catálogo ─────────────────────────────────────────────

    private void renderCatalog(List<Category> categories) {
        int dp = (int) getResources().getDisplayMetrics().density;
        mContainer.removeAllViews();

        if (categories.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("El catálogo está vacío.");
            tv.setTextColor(0xFFAAAAAA);
            tv.setPadding(16 * dp, 24 * dp, 16 * dp, 0);
            mContainer.addView(tv);
            return;
        }

        for (Category cat : categories) {
            if (cat.entries.isEmpty()) continue;

            // ── Cabecera de categoría ──
            TextView tvCat = new TextView(this);
            tvCat.setText(cat.title.toUpperCase());
            tvCat.setTextColor(0xFF80CBC4);
            tvCat.setTextSize(12f);
            tvCat.setLetterSpacing(0.1f);
            tvCat.setPadding(16 * dp, 20 * dp, 16 * dp, 8 * dp);
            mContainer.addView(tvCat);

            // Línea divisoria
            View divider = new View(this);
            divider.setBackgroundColor(0x3380CBC4);
            LinearLayout.LayoutParams lpDiv = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
            lpDiv.setMargins(16 * dp, 0, 16 * dp, 8 * dp);
            mContainer.addView(divider, lpDiv);

            // ── Tarjetas de shaders ──
            for (final ShaderEntry entry : cat.entries) {
                mContainer.addView(buildEntryCard(entry, dp));
            }
        }
    }

    /**
     * Construye una tarjeta para un shader:
     *
     *   ┌─────────────────────────────────┐
     *   │ [imagen 80×80 si la tiene]  Nombre
     *   │                              por Autor
     *   │                              Descripción (opcional, 2 líneas)
     *   │                              [Instalar]
     *   └─────────────────────────────────┘
     */
    private View buildEntryCard(final ShaderEntry entry, int dp) {
        // Tarjeta
        FrameLayout card = new FrameLayout(this);
        card.setBackgroundColor(0xFF1E1E2E);
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCard.setMargins(12 * dp, 4 * dp, 12 * dp, 4 * dp);
        card.setLayoutParams(lpCard);

        // Contenido horizontal
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(12 * dp, 12 * dp, 12 * dp, 12 * dp);
        card.addView(row);

        // ── Imagen (opcional) ──
        final ImageView ivThumb = new ImageView(this);
        ivThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivThumb.setBackgroundColor(0xFF2A2A3E);
        int thumbSize = 80 * dp;
        LinearLayout.LayoutParams lpImg = new LinearLayout.LayoutParams(thumbSize, thumbSize);
        lpImg.setMargins(0, 0, 12 * dp, 0);

        if (entry.imageUrl != null && !entry.imageUrl.isEmpty()) {
            ivThumb.setVisibility(View.VISIBLE);
            row.addView(ivThumb, lpImg);
            loadImageAsync(entry.imageUrl, ivThumb);
        }

        // ── Columna de texto + botón ──
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        row.addView(col,
					new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // Nombre
        TextView tvName = new TextView(this);
        tvName.setText(entry.name);
        tvName.setTextColor(0xFFFFFFFF);
        tvName.setTextSize(15f);
        col.addView(tvName);

        // Autor
        TextView tvAuthor = new TextView(this);
        tvAuthor.setText("por " + entry.author);
        tvAuthor.setTextColor(0xFF888888);
        tvAuthor.setTextSize(12f);
        col.addView(tvAuthor);

        // Descripción (opcional)
        if (entry.description != null && !entry.description.isEmpty()) {
            TextView tvDesc = new TextView(this);
            tvDesc.setText(entry.description);
            tvDesc.setTextColor(0xFFAAAAAA);
            tvDesc.setTextSize(12f);
            tvDesc.setMaxLines(2);
            tvDesc.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lpDesc = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            lpDesc.setMargins(0, 4 * dp, 0, 0);
            col.addView(tvDesc, lpDesc);
        }

        // Botón instalar
        final Button btnInstall = new Button(this);
        btnInstall.setText("Instalar");
        btnInstall.setTextColor(0xFF121212);
        btnInstall.setBackgroundColor(0xFF80CBC4);
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        lpBtn.setMargins(0, 8 * dp, 0, 0);
        col.addView(btnInstall, lpBtn);

        // Verificar si ya está instalado
        if (mModuleManager.getModuleByName(entry.name) != null) {
            btnInstall.setText("Instalado ✓");
            btnInstall.setBackgroundColor(0xFF444444);
            btnInstall.setEnabled(false);
        }

        btnInstall.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					installShader(entry, btnInstall);
				}
			});

        return card;
    }

    // ─── Instalación de un shader ──────────────────────────────────────────────

    private void installShader(final ShaderEntry entry, final Button btnInstall) {
        btnInstall.setEnabled(false);
        btnInstall.setText("Descargando…");

        mPool.execute(new Runnable() {
				@Override
				public void run() {
					try {
						final String json = downloadString(entry.downloadUrl);
						mUi.post(new Runnable() {
								@Override public void run() {
									try {
										// Reusar la lógica de instalación existente vía un Uri "in-memory"
										// ModuleManager.installFromUri espera un ContentResolver Uri.
										// En su lugar llamamos installFromJson(), que exponemos abajo.
										installJsonIntoManager(json);
										btnInstall.setText("Instalado ✓");
										btnInstall.setBackgroundColor(0xFF444444);
										mInstalledAny = true;
										android.content.Intent result = new android.content.Intent();
										result.putExtra(RESULT_EXTRA_NAME, entry.name);
										setResult(RESULT_OK, result);
										Toast.makeText(ShaderStoreActivity.this,
													   entry.name + " instalado", Toast.LENGTH_SHORT).show();
									} catch (Exception e) {
										btnInstall.setEnabled(true);
										btnInstall.setText("Instalar");
										Toast.makeText(ShaderStoreActivity.this,
													   "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
									}
								}
							});
					} catch (final Exception e) {
						mUi.post(new Runnable() {
								@Override public void run() {
									btnInstall.setEnabled(true);
									btnInstall.setText("Instalar");
									Toast.makeText(ShaderStoreActivity.this,
												   "Descarga fallida: " + e.getMessage(), Toast.LENGTH_LONG).show();
								}
							});
					}
				}
			});
    }

    /**
     * Instala un módulo directamente desde su JSON (cadena).
     * Usa installFromJson() de ModuleManager, que evita el problema de
     * Uri.fromFile() en Android 7+ (FileUriExposedException / stream nulo).
     */
    private void installJsonIntoManager(String json) throws Exception {
        mModuleManager.installFromJson(json);
    }

    // ─── Carga de imagen asíncrona ────────────────────────────────────────────

    private void loadImageAsync(final String imageUrl, final ImageView iv) {
        mPool.execute(new Runnable() {
				@Override
				public void run() {
					try {
						HttpURLConnection conn = (HttpURLConnection) new URL(imageUrl).openConnection();
						conn.setConnectTimeout(5000);
						conn.setReadTimeout(8000);
						conn.connect();
						final Bitmap bmp = BitmapFactory.decodeStream(conn.getInputStream());
						conn.disconnect();
						if (bmp != null) {
							mUi.post(new Runnable() {
									@Override public void run() { iv.setImageBitmap(bmp); }
								});
						}
					} catch (Exception ignored) {
						// Si falla la imagen simplemente queda el fondo de color
					}
				}
			});
    }

    // ─── HTTP helper ──────────────────────────────────────────────────────────

    private String downloadString(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "DemeterApp/1.0");
        conn.connect();
        int code = conn.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            conn.disconnect();
            throw new Exception("HTTP " + code);
        }
        InputStream is = conn.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        reader.close();
        conn.disconnect();
        return sb.toString();
    }
}
