package com.lexus2025.indexer;


// ═══════════════════════════════════════════════════════════════════════════
//  StoreInjectorActivity — Herramienta de mantenimiento de la tienda
//  Uso: app standalone de una sola Activity (o agrega al manifest de Demeter)
//
//  Flujo:
//    1. Descarga store_index.md desde GitHub (raw)
//    2. Muestra su contenido y la lista de categorías existentes
//    3. El usuario rellena los campos del nuevo shader
//    4. Elige categoría existente o crea una nueva
//    5. Pulsa "Generar" → el MD con el shader inyectado al final aparece
//       en un TextArea seleccionable listo para copiar/compartir
// ═══════════════════════════════════════════════════════════════════════════

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StoreInjectorActivity extends Activity {

    // URL raw del índice en GitHub
    private static final String INDEX_RAW_URL =
	"https://raw.githubusercontent.com/LexusYTG/Demeter-reshader/main/Store/store_index.md";

    // ─── Estado ───────────────────────────────────────────────────────────────
    private String mOriginalMd = "";          // MD descargado tal cual
    private List<String> mCategories = new ArrayList<String>(); // categorías detectadas

    // ─── Vistas ───────────────────────────────────────────────────────────────
    private TextView   mTvStatus;
    private Button     mBtnDownload;

    // Formulario
    private LinearLayout mFormLayout;
    private EditText   mEtName;
    private EditText   mEtAuthor;
    private EditText   mEtDownloadUrl;
    private EditText   mEtImageUrl;
    private EditText   mEtDescription;
    private Spinner    mSpinnerCategory;
    private EditText   mEtNewCategory;    // solo visible si elige "Nueva categoría…"
    private Button     mBtnGenerate;

    // Resultado
    private LinearLayout mResultLayout;
    private EditText   mEtResult;
    private Button     mBtnCopy;
    private Button     mBtnShare;

    private final ExecutorService mPool = Executors.newSingleThreadExecutor();
    private final Handler         mUi   = new Handler(Looper.getMainLooper());

    // ─── Ciclo de vida ────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);

        buildUi();
    }

    // ─── Construcción de UI ───────────────────────────────────────────────────

    private void buildUi() {
        int dp = dp();

        // Raíz con scroll
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF0F0F17);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16*dp, 20*dp, 16*dp, 32*dp);
        scroll.addView(root);

        // ── Título ──
        TextView tvTitle = new TextView(this);
        tvTitle.setText("Store Injector");
        tvTitle.setTextColor(0xFFB388FF);
        tvTitle.setTextSize(22f);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, 4*dp);
        root.addView(tvTitle, matchW());

        TextView tvSub = new TextView(this);
        tvSub.setText("Inyecta un shader en store_index.md");
        tvSub.setTextColor(0xFF666688);
        tvSub.setTextSize(12f);
        tvSub.setGravity(Gravity.CENTER);
        tvSub.setPadding(0, 0, 0, 16*dp);
        root.addView(tvSub, matchW());

        // ── Estado + botón descargar ──
        mTvStatus = new TextView(this);
        mTvStatus.setText("Índice no descargado");
        mTvStatus.setTextColor(0xFF888899);
        mTvStatus.setTextSize(12f);
        mTvStatus.setPadding(0, 0, 0, 8*dp);
        root.addView(mTvStatus, matchW());

        mBtnDownload = makeButton("⬇  Descargar store_index.md", 0xFF7C4DFF);
        mBtnDownload.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { downloadIndex(); }
			});
        root.addView(mBtnDownload, matchW());

        divider(root, 20*dp);

        // ── Formulario ──
        mFormLayout = new LinearLayout(this);
        mFormLayout.setOrientation(LinearLayout.VERTICAL);
        mFormLayout.setVisibility(View.GONE);
        root.addView(mFormLayout, matchW());

        label(mFormLayout, "Nombre del shader *");
        mEtName = input(mFormLayout, "Ej: Glitch Wave", false);

        label(mFormLayout, "Autor *");
        mEtAuthor = input(mFormLayout, "Ej: lexus2025", false);

        label(mFormLayout, "Enlace de descarga * (.demeter, raw GitHub)");
        mEtDownloadUrl = input(mFormLayout, "https://raw.githubusercontent.com/...", false);

        label(mFormLayout, "Enlace de imagen (opcional, PNG/JPG)");
        mEtImageUrl = input(mFormLayout, "https://... o dejar vacío", false);

        label(mFormLayout, "Descripción (opcional)");
        mEtDescription = input(mFormLayout, "Una línea describiendo el efecto", false);

        label(mFormLayout, "Categoría");

        // Spinner de categorías
        mSpinnerCategory = new Spinner(this);
        mSpinnerCategory.setBackgroundColor(0xFF1E1E32);
        LinearLayout.LayoutParams lpSpin = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpSpin.setMargins(0, 4*dp, 0, 8*dp);
        mFormLayout.addView(mSpinnerCategory, lpSpin);

        mSpinnerCategory.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
					boolean isNew = pos == mCategories.size(); // último ítem = "Nueva categoría…"
					mEtNewCategory.setVisibility(isNew ? View.VISIBLE : View.GONE);
				}
				@Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
			});

        label(mFormLayout, "Nombre de nueva categoría");
        mEtNewCategory = input(mFormLayout, "Ej: Efectos de luz", false);
        mEtNewCategory.setVisibility(View.GONE);

        divider(mFormLayout, 8*dp);

        mBtnGenerate = makeButton("✦  Generar MD", 0xFF00BFA5);
        mBtnGenerate.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { generateMd(); }
			});
        mFormLayout.addView(mBtnGenerate, matchW());

        divider(root, 16*dp);

        // ── Resultado ──
        mResultLayout = new LinearLayout(this);
        mResultLayout.setOrientation(LinearLayout.VERTICAL);
        mResultLayout.setVisibility(View.GONE);
        root.addView(mResultLayout, matchW());

        label(mResultLayout, "MD generado — copia y reemplaza en GitHub:");

        mEtResult = new EditText(this);
        mEtResult.setBackgroundColor(0xFF1A1A2E);
        mEtResult.setTextColor(0xFFCCCCDD);
        mEtResult.setTextSize(11f);
        mEtResult.setTypeface(android.graphics.Typeface.MONOSPACE);
        mEtResult.setGravity(Gravity.TOP | Gravity.START);
        mEtResult.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        mEtResult.setMinLines(12);
        mEtResult.setPadding(8*dp, 8*dp, 8*dp, 8*dp);
        LinearLayout.LayoutParams lpRes = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpRes.setMargins(0, 4*dp, 0, 12*dp);
        mResultLayout.addView(mEtResult, lpRes);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        mResultLayout.addView(btnRow, matchW());

        mBtnCopy = makeButton("Copiar", 0xFF5C6BC0);
        mBtnCopy.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
					cm.setPrimaryClip(ClipData.newPlainText("store_index.md", mEtResult.getText().toString()));
					Toast.makeText(StoreInjectorActivity.this, "Copiado al portapapeles", Toast.LENGTH_SHORT).show();
				}
			});
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(0,
																		ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpBtn.setMargins(0, 0, 8*dp, 0);
        btnRow.addView(mBtnCopy, lpBtn);

        mBtnShare = makeButton("Compartir", 0xFF37474F);
        mBtnShare.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					Intent share = new Intent(Intent.ACTION_SEND);
					share.setType("text/plain");
					share.putExtra(Intent.EXTRA_TEXT, mEtResult.getText().toString());
					share.putExtra(Intent.EXTRA_SUBJECT, "store_index.md actualizado");
					startActivity(Intent.createChooser(share, "Compartir MD"));
				}
			});
        btnRow.addView(mBtnShare, new LinearLayout.LayoutParams(0,
																ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        setContentView(scroll);
    }

    // ─── Descarga del índice ──────────────────────────────────────────────────

    private void downloadIndex() {
        mBtnDownload.setEnabled(false);
        mTvStatus.setTextColor(0xFF888899);
        mTvStatus.setText("Descargando…");

        final ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Descargando store_index.md…");
        pd.setCancelable(false);
        pd.show();

        mPool.execute(new Runnable() {
				@Override
				public void run() {
					try {
						final String md = httpGet(INDEX_RAW_URL);
						mUi.post(new Runnable() {
								@Override public void run() {
									pd.dismiss();
									mOriginalMd = md;
									mCategories = extractCategories(md);
									onIndexLoaded();
								}
							});
					} catch (final Exception e) {
						mUi.post(new Runnable() {
								@Override public void run() {
									pd.dismiss();
									mBtnDownload.setEnabled(true);
									mTvStatus.setTextColor(0xFFFF5252);
									mTvStatus.setText("Error: " + e.getMessage());
								}
							});
					}
				}
			});
    }

    private void onIndexLoaded() {
        mTvStatus.setTextColor(0xFF69F0AE);
        mTvStatus.setText("✓ Descargado — " + mOriginalMd.split("\n").length
						  + " líneas, " + mCategories.size() + " categorías");

        mBtnDownload.setText("⬇  Re-descargar");
        mBtnDownload.setEnabled(true);

        // Poblar spinner: categorías existentes + opción nueva
        List<String> spinnerItems = new ArrayList<String>(mCategories);
        spinnerItems.add("+ Nueva categoría…");
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_item, spinnerItems);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerCategory.setAdapter(adapter);

        mFormLayout.setVisibility(View.VISIBLE);
        mResultLayout.setVisibility(View.GONE);
    }

    // ─── Generación del MD ────────────────────────────────────────────────────

    private void generateMd() {
        // Validar campos obligatorios
        String name        = mEtName.getText().toString().trim();
        String author      = mEtAuthor.getText().toString().trim();
        String downloadUrl = mEtDownloadUrl.getText().toString().trim();
        String imageUrl    = mEtImageUrl.getText().toString().trim();
        String description = mEtDescription.getText().toString().trim();

        if (name.isEmpty() || author.isEmpty() || downloadUrl.isEmpty()) {
            Toast.makeText(this, "Nombre, Autor y Enlace son obligatorios", Toast.LENGTH_LONG).show();
            return;
        }
        if (!downloadUrl.startsWith("http")) {
            Toast.makeText(this, "El enlace de descarga debe ser una URL válida", Toast.LENGTH_LONG).show();
            return;
        }

        // Determinar categoría
        int selectedPos = mSpinnerCategory.getSelectedItemPosition();
        String category;
        if (selectedPos == mCategories.size()) {
            // "Nueva categoría…"
            category = mEtNewCategory.getText().toString().trim();
            if (category.isEmpty()) {
                Toast.makeText(this, "Escribe el nombre de la nueva categoría", Toast.LENGTH_LONG).show();
                return;
            }
        } else {
            category = mCategories.get(selectedPos);
        }

        // Construir el bloque del nuevo shader
        StringBuilder block = new StringBuilder();
        block.append("\n### ").append(name).append("\n");
        block.append("- **Autor:** ").append(author).append("\n");
        block.append("- **Enlace:** ").append(downloadUrl).append("\n");
        if (!imageUrl.isEmpty()) {
            block.append("- **Imagen:** ").append(imageUrl).append("\n");
        }
        if (!description.isEmpty()) {
            block.append("- **Descripción:** ").append(description).append("\n");
        }

        // Inyectar en el MD
        String result = injectIntoMd(mOriginalMd, category, block.toString());

        mEtResult.setText(result);
        mResultLayout.setVisibility(View.VISIBLE);

        // Scroll hasta el resultado
        final ScrollView sv = (ScrollView) mEtResult.getParent().getParent().getParent();
        sv.post(new Runnable() {
				@Override public void run() { sv.fullScroll(View.FOCUS_DOWN); }
			});

        Toast.makeText(this, "MD generado. ¡Cópialo y súbelo a GitHub!", Toast.LENGTH_LONG).show();
    }

    /**
     * Inyecta el bloque del shader en el lugar correcto del MD:
     * - Si la categoría ya existe → añade el bloque al final de esa categoría
     *   (antes de la siguiente categoría o al final del archivo).
     * - Si es categoría nueva → la añade al final del archivo con cabecera.
     */
    private String injectIntoMd(String md, String category, String block) {
        String[] lines = md.split("\n", -1);

        // Buscar si la categoría ya existe
        String catHeader1 = "## Categoría: " + category;
        String catHeader2 = "## Categoria: " + category;
        int catLine = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().equals(catHeader1) || lines[i].trim().equals(catHeader2)) {
                catLine = i;
                break;
            }
        }

        StringBuilder sb = new StringBuilder();

        if (catLine >= 0) {
            // Categoría existe: buscar dónde termina (próxima línea ## o fin de archivo)
            int insertBefore = lines.length; // por defecto al final
            for (int i = catLine + 1; i < lines.length; i++) {
                if (lines[i].startsWith("## ")) {
                    insertBefore = i;
                    break;
                }
            }
            // Reconstruir con el bloque insertado antes de insertBefore
            for (int i = 0; i < lines.length; i++) {
                if (i == insertBefore) {
                    // Asegurar línea en blanco antes de la siguiente categoría
                    sb.append(block);
                    sb.append("\n---\n\n");
                }
                sb.append(lines[i]).append("\n");
            }
            if (insertBefore == lines.length) {
                // Era el final del archivo
                sb.append(block);
            }
        } else {
            // Categoría nueva: añadir al final
            // Trim trailing whitespace del original
            sb.append(md.trim());
            sb.append("\n\n---\n\n");
            sb.append("## Categoría: ").append(category).append("\n");
            sb.append(block);
        }

        return sb.toString();
    }

    // ─── Parser de categorías ─────────────────────────────────────────────────

    private List<String> extractCategories(String md) {
        List<String> cats = new ArrayList<String>();
        for (String line : md.split("\n")) {
            line = line.trim();
            if (line.startsWith("## Categoría:") || line.startsWith("## Categoria:")) {
                String name = line.replaceFirst("##\\s*Categor[ií]a:\\s*", "").trim();
                if (!name.isEmpty()) cats.add(name);
            }
        }
        return cats;
    }

    // ─── HTTP helper ──────────────────────────────────────────────────────────

    private String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "DemeterStoreInjector/1.0");
        conn.connect();
        int code = conn.getResponseCode();
        if (code != 200) {
            conn.disconnect();
            throw new Exception("HTTP " + code + " al descargar el índice");
        }
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        reader.close();
        conn.disconnect();
        return sb.toString();
    }

    // ─── Helpers de UI ───────────────────────────────────────────────────────

    private int dp() {
        return (int) getResources().getDisplayMetrics().density;
    }

    private LinearLayout.LayoutParams matchW() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void label(LinearLayout parent, String text) {
        int dp = dp();
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF9E9ECC);
        tv.setTextSize(12f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 12*dp, 0, 2*dp);
        parent.addView(tv, lp);
    }

    private EditText input(LinearLayout parent, String hint, boolean multiline) {
        int dp = dp();
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setHintTextColor(0xFF444455);
        et.setTextColor(0xFFDDDDEE);
        et.setBackgroundColor(0xFF1A1A2E);
        et.setTextSize(13f);
        et.setPadding(10*dp, 8*dp, 10*dp, 8*dp);
        if (multiline) {
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            et.setMinLines(3);
            et.setGravity(Gravity.TOP | Gravity.START);
        } else {
            et.setInputType(InputType.TYPE_CLASS_TEXT);
            et.setSingleLine(true);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 4*dp);
        parent.addView(et, lp);
        return et;
    }

    private Button makeButton(String text, int color) {
        int dp = dp();
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(0xFFFFFFFF);
        btn.setBackgroundColor(color);
        btn.setPadding(12*dp, 10*dp, 12*dp, 10*dp);
        return btn;
    }

    private void divider(LinearLayout parent, int heightPx) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(
						   ViewGroup.LayoutParams.MATCH_PARENT, heightPx));
    }
}

