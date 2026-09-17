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

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class FiltersActivity extends Activity {

    private static final String TAG = "FiltersActivity";

    private static final int MP = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WC = ViewGroup.LayoutParams.WRAP_CONTENT;

    public static final String EXTRA_RESULT_CHANGED = "changed";

    public static final String PREFS_NAME    = "demeter_prefs";
    public static final String PREF_FPS_OVERLAY = "fps_overlay";

    private static final int REQ_FPS_POSITION = 201;

    private ModuleManager mModuleManager;
    private LinearLayout  mLlFilters;
    private TextView      mTvCount;
    private boolean       mChanged = false;
    private boolean       mFpsOverlay = false;
    private Switch        mFpsSwitch = null;
    private TextView      mBtnFpsPosition = null;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        mFpsOverlay = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(PREF_FPS_OVERLAY, false);
        Log.d(TAG, "onCreate: pref FPS = " + mFpsOverlay);

        mModuleManager = new ModuleManager(this);

        setContentView(buildRoot());
        rebuildList();
    }

    @Override protected void onResume() {
        super.onResume();
        boolean fps = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(PREF_FPS_OVERLAY, false);
        Log.d(TAG, "onResume: pref=" + fps + " mFpsOverlay=" + mFpsOverlay);

        if (mFpsOverlay != fps) {
            mFpsOverlay = fps;
            if (mFpsSwitch != null) mFpsSwitch.setChecked(fps);
        } else if (mFpsSwitch != null && mFpsSwitch.isChecked() != mFpsOverlay) {
            mFpsSwitch.setChecked(mFpsOverlay);
        }
        updateFpsPositionButtonState();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
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

    @Override public void finish() {
        Intent r = new Intent();
        r.putExtra(EXTRA_RESULT_CHANGED, mChanged);
        setResult(mChanged ? RESULT_OK : RESULT_CANCELED, r);
        super.finish();
    }

    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG_ROOT);

        root.addView(buildHeader());

        TextView desc = Ui.text(this,
                                "Activa los filtros para apilarlos. El orden importa: el resultado de uno es la entrada del siguiente.",
                                13, Ui.TEXT_TERTIARY, false);
        desc.setLineSpacing(Ui.dp(this, 3), 1f);
        int side = Ui.dp(this, 20);
        desc.setPadding(side, 0, side, Ui.dp(this, 14));
        root.addView(desc);

        root.addView(buildFpsOverlayRow());

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 12));

        mLlFilters = new LinearLayout(this);
        mLlFilters.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(mLlFilters, new FrameLayout.LayoutParams(MP, WC));

        root.addView(scroll, Ui.lp(MP, 0, 1f));
        return root;
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(this, 16);
        row.setPadding(p, p, p, p);

        TextView back = makeIconButton("✕");
        back.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { finish(); }
            });
        row.addView(back, Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44)));

        TextView title = Ui.text(this, "Filtros", 24, Ui.TEXT_PRIMARY, true);
        LinearLayout.LayoutParams lpTitle = Ui.lp(0, WC, 1f);
        lpTitle.leftMargin = Ui.dp(this, 12);
        row.addView(title, lpTitle);

        mTvCount = Ui.text(this, "0", 14, Ui.ACCENT, true);
        mTvCount.setGravity(Gravity.CENTER);
        mTvCount.setBackground(Ui.roundRectStroke(
                                   Ui.ACCENT_SOFT, Ui.ACCENT_SOFT, this, 20, 0));
        int hpad = Ui.dp(this, 14);
        int vpad = Ui.dp(this, 8);
        mTvCount.setPadding(hpad, vpad, hpad, vpad);
        row.addView(mTvCount);

        return row;
    }

    private TextView makeIconButton(String glyph) {
        TextView tv = new TextView(this);
        tv.setText(glyph);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tv.setTextColor(Ui.TEXT_PRIMARY);
        tv.setGravity(Gravity.CENTER);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setBackground(Ui.buttonBgStroke(
                             this, Ui.BG_ELEV, Ui.DIVIDER, Ui.DIVIDER, 22, 1f));
        tv.setClickable(true);
        return tv;
    }

    private void rebuildList() {
        mLlFilters.removeAllViews();

        final List<Module> active = mModuleManager.getEnabledChain();
        final List<Module> all    = mModuleManager.getAll();

        List<Module> available = new ArrayList<Module>();
        for (Module m : all) {
            boolean isActive = false;
            for (Module a : active) {
                if (a.getName().equals(m.getName())) { isActive = true; break; }
            }
            if (!isActive) available.add(m);
        }

        if (active.isEmpty() && available.isEmpty()) {
            TextView tv = Ui.text(this,
                                  "No hay filtros instalados.\nUsa el botón + en la pantalla principal para importar uno.",
                                  14, Ui.TEXT_TERTIARY, false);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, Ui.dp(this, 60), 0, 0);
            mLlFilters.addView(tv);
            mTvCount.setText("0");
            return;
        }

        if (!active.isEmpty()) {
            mLlFilters.addView(sectionHeader("CADENA ACTIVA (" + active.size() + ")"));
            for (int i = 0; i < active.size(); i++) {
                final int pos = i;
                mLlFilters.addView(buildRow(active.get(i), pos, active.size(), true));
            }
        }

        if (!available.isEmpty()) {
            mLlFilters.addView(sectionHeader("DISPONIBLES (" + available.size() + ")"));
            for (Module m : available) {
                mLlFilters.addView(buildRow(m, -1, 0, false));
            }
        }

        mTvCount.setText(String.valueOf(active.size()));
    }

    private TextView sectionHeader(String s) {
        TextView tv = Ui.text(this, s, 11, Ui.TEXT_TERTIARY, true);
        tv.setLetterSpacing(0.15f);
        tv.setPadding(Ui.dp(this, 8), Ui.dp(this, 18), Ui.dp(this, 8), Ui.dp(this, 8));
        return tv;
    }

    private View buildRow(final Module module, final int position,
                          final int activeCount, final boolean isActive) {

        FrameLayout outer = new FrameLayout(this);
        LinearLayout.LayoutParams outerLp = Ui.lp(MP, WC);
        outerLp.topMargin    = Ui.dp(this, 4);
        outerLp.bottomMargin = Ui.dp(this, 4);
        outer.setLayoutParams(outerLp);

        GradientDrawable cardBg = Ui.roundRect(Ui.BG_ELEV, this, 14);
        outer.setBackground(cardBg);

        if (isActive) {
            View accentBar = new View(this);
            accentBar.setBackground(buildAccentBar());
            FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                Ui.dp(this, 3), FrameLayout.LayoutParams.MATCH_PARENT);
            outer.addView(accentBar, barLp);
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(this, 14);
        int leftPad = isActive ? Ui.dp(this, 17) : pad;
        row.setPadding(leftPad, pad, pad, pad);
        outer.addView(row, new FrameLayout.LayoutParams(MP, WC));

        if (isActive) {
            TextView badge = Ui.text(this, String.valueOf(position + 1), 12, 0xFFFFFFFF, true);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(Ui.roundRect(Ui.ACCENT, this, 6));
            row.addView(badge, Ui.lp(Ui.dp(this, 26), Ui.dp(this, 26)));
        }

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colLp = Ui.lp(0, WC, 1f);
        colLp.leftMargin  = Ui.dp(this, isActive ? 12 : 0);
        colLp.rightMargin = Ui.dp(this, 8);
        row.addView(col, colLp);

        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = Ui.text(this, module.getName(), 15, Ui.TEXT_PRIMARY, true);
        nameRow.addView(name, Ui.lp(WC, WC));

        if (module.getType() != Module.Type.MODIFIER) {
            TextView typeChip = buildTypeChip(module.getType());
            LinearLayout.LayoutParams chipLp = Ui.lp(WC, WC);
            chipLp.leftMargin = Ui.dp(this, 8);
            nameRow.addView(typeChip, chipLp);
        }

        col.addView(nameRow);

        String subtitle = "por " + module.getAuthor();
        if (module.getVersion() != null && !module.getVersion().isEmpty()) {
            subtitle += "  ·  v" + module.getVersion();
        }
        TextView author = Ui.text(this, subtitle, 12, Ui.TEXT_TERTIARY, false);
        LinearLayout.LayoutParams authLp = Ui.lp(MP, WC);
        authLp.topMargin = Ui.dp(this, 2);
        col.addView(author, authLp);

        if (!isActive && !module.getParamDefs().isEmpty()) {
            TextView hint = Ui.text(this, "· " + module.getParamDefs().size() + " parámetro" +
                                    (module.getParamDefs().size() == 1 ? "" : "s") + " configurables",
                                    11, Ui.ACCENT, false);
            LinearLayout.LayoutParams hintLp = Ui.lp(MP, WC);
            hintLp.topMargin = Ui.dp(this, 3);
            col.addView(hint, hintLp);
        }

        if (isActive) {
            if (position > 0) {
                TextView up = makeIconButton("↑");
                up.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                up.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            moveInChain(position, position - 1);
                            rebuildList();
                        }
                    });
                LinearLayout.LayoutParams lpU = Ui.lp(Ui.dp(this, 36), Ui.dp(this, 36));
                lpU.rightMargin = Ui.dp(this, 4);
                row.addView(up, lpU);
            }
            if (position < activeCount - 1) {
                TextView down = makeIconButton("↓");
                down.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                down.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            moveInChain(position, position + 1);
                            rebuildList();
                        }
                    });
                LinearLayout.LayoutParams lpD = Ui.lp(Ui.dp(this, 36), Ui.dp(this, 36));
                lpD.rightMargin = Ui.dp(this, 4);
                row.addView(down, lpD);
            }
        }

        if (!module.getParamDefs().isEmpty()) {
            boolean hasCustomParams = hasModifiedParams(module);

            FrameLayout gearWrapper = new FrameLayout(this);
            LinearLayout.LayoutParams gwLp = Ui.lp(Ui.dp(this, 40), Ui.dp(this, 40));
            gwLp.rightMargin = Ui.dp(this, 4);

            TextView gear = makeIconButton("⚙");
            gear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);

            gear.setTextColor(hasCustomParams ? Ui.ACCENT : Ui.TEXT_SECOND);
            gear.setBackground(Ui.buttonBgStroke(
                                   this,
                                   hasCustomParams ? Ui.ACCENT_SOFT : Ui.BG_ELEV,
                                   hasCustomParams ? Ui.ACCENT_SOFT : Ui.DIVIDER,
                                   hasCustomParams ? Ui.ACCENT      : Ui.DIVIDER,
                                   22, 1f));
            gear.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { showParamsDialog(module); }
                });
            gearWrapper.addView(gear, new FrameLayout.LayoutParams(MP, MP));

            if (hasCustomParams) {
                View dot = new View(this);
                dot.setBackground(Ui.circle(Ui.ACCENT));
                FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(
                    Ui.dp(this, 8), Ui.dp(this, 8));
                dotLp.gravity  = Gravity.TOP | Gravity.END;
                dotLp.topMargin   = Ui.dp(this, 2);
                dotLp.rightMargin = Ui.dp(this, 2);
                gearWrapper.addView(dot, dotLp);
            }

            row.addView(gearWrapper, gwLp);
        }

        Switch sw = new Switch(this);
        sw.setChecked(module.isEnabled());
        sw.setShowText(false);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (module.isEnabled() == isChecked) return;
                    String err = module.getCompilationError();
                    if (isChecked && err != null) {
                        new AlertDialog.Builder(FiltersActivity.this)
                            .setTitle("Error de compilación")
                            .setMessage("\"" + module.getName() + "\" no se puede activar:\n\n" + err)
                            .setPositiveButton("OK", null)
                            .show();
                        buttonView.setChecked(false);
                        return;
                    }
                    mModuleManager.setEnabled(module, isChecked);
                    mChanged = true;
                    rebuildList();
                    Toast.makeText(FiltersActivity.this,
                                   module.getName() + (isChecked ? " activado" : " desactivado"),
                                   Toast.LENGTH_SHORT).show();
                }
            });
        row.addView(sw);

        return outer;
    }

    private Drawable buildAccentBar() {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(Ui.ACCENT);

        float r = Ui.dp(this, 3);
        d.setCornerRadii(new float[]{0, 0, r, r, r, r, 0, 0});
        return d;
    }

    private TextView buildTypeChip(Module.Type type) {
        int chipColor;
        String label;
        switch (type) {
            case FRAMEGEN:
                chipColor = 0xFF1A3A2A;
                label = "FRAMEGEN";
                break;
            case RENDERER:
                chipColor = 0xFF2A1A3A;
                label = "RENDERER";
                break;
            default:
                chipColor = Ui.ACCENT_SOFT;
                label = "MOD";
        }
        int textColor = (type == Module.Type.FRAMEGEN) ? 0xFF4ADE80 : Ui.ACCENT;

        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        chip.setTextColor(textColor);
        chip.setTypeface(Ui.medium());
        chip.setLetterSpacing(0.12f);
        chip.setBackground(Ui.roundRect(chipColor, this, 4));
        int hp = Ui.dp(this, 6);
        int vp = Ui.dp(this, 3);
        chip.setPadding(hp, vp, hp, vp);
        return chip;
    }

    private boolean hasModifiedParams(Module module) {
        Map<String, Float>           params = module.getParams();
        Map<String, Module.ParamDef> defs   = module.getParamDefs();
        for (Map.Entry<String, Module.ParamDef> e : defs.entrySet()) {
            Float current = params.get(e.getKey());
            if (current != null && Math.abs(current - e.getValue().defaultValue) > 0.0001f) {
                return true;
            }
        }
        return false;
    }

    private void moveInChain(int from, int to) {
        List<Module> active = mModuleManager.getEnabledChain();
        if (from < 0 || from >= active.size()) return;
        if (to   < 0 || to   >= active.size()) return;
        List<String> names = new ArrayList<String>();
        for (Module m : active) names.add(m.getName());
        String moved = names.remove(from);
        names.add(to, moved);
        mModuleManager.setChainOrder(names);
        mChanged = true;
    }

    private void showParamsDialog(final Module module) {
        if (module.getParamDefs().isEmpty()) return;

        final Map<String, Module.ParamDef> defs    = module.getParamDefs();
        final Map<String, Float>           current = module.getParams();

        ScrollView scroll = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        container.setPadding(pad, pad, pad, pad);
        scroll.addView(container, new FrameLayout.LayoutParams(MP, WC));

        for (final Map.Entry<String, Module.ParamDef> entry : defs.entrySet()) {
            final String uniformName = entry.getKey();
            final Module.ParamDef def = entry.getValue();
            float initialValue = current.containsKey(uniformName)
                ? current.get(uniformName) : def.defaultValue;

            final boolean isModified = Math.abs(initialValue - def.defaultValue) > 0.0001f;

            LinearLayout labelRow = new LinearLayout(this);
            labelRow.setOrientation(LinearLayout.HORIZONTAL);
            labelRow.setGravity(Gravity.CENTER_VERTICAL);
            labelRow.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));

            final TextView label = Ui.text(this, "", 14,
                                           isModified ? Ui.ACCENT : Ui.TEXT_PRIMARY, false);
            final float range = def.max - def.min;
            final boolean showDecimal = range <= 10f;
            updateParamLabel(label, def.label, initialValue, showDecimal, isModified);
            labelRow.addView(label, Ui.lp(0, WC, 1f));

            final TextView resetBtn = Ui.text(this, "↺", 16, Ui.TEXT_TERTIARY, false);
            resetBtn.setGravity(Gravity.CENTER);
            resetBtn.setPadding(Ui.dp(this, 8), 0, 0, 0);
            resetBtn.setVisibility(isModified ? View.VISIBLE : View.INVISIBLE);
            labelRow.addView(resetBtn);

            container.addView(labelRow);

            final SeekBar seekBar = new SeekBar(this);
            seekBar.setMax(1000);
            seekBar.setProgress(Math.round((initialValue - def.min) / range * 1000));
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                        float value = def.min + (progress / 1000f) * range;
                        boolean mod = Math.abs(value - def.defaultValue) > 0.0001f;
                        label.setTextColor(mod ? Ui.ACCENT : Ui.TEXT_PRIMARY);
                        updateParamLabel(label, def.label, value, showDecimal, mod);
                        resetBtn.setVisibility(mod ? View.VISIBLE : View.INVISIBLE);
                        mModuleManager.setParamValue(module, uniformName, value);
                        mChanged = true;
                    }
                    @Override public void onStartTrackingTouch(SeekBar sb) {}
                    @Override public void onStopTrackingTouch(SeekBar sb) {}
                });
            container.addView(seekBar);

            final SeekBar seekBarRef = seekBar;
            final Module.ParamDef defRef = def;
            final String uniformRef = uniformName;
            resetBtn.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        mModuleManager.setParamValue(module, uniformRef, defRef.defaultValue);
                        int prog = Math.round((defRef.defaultValue - defRef.min) / range * 1000);
                        seekBarRef.setProgress(prog);
                        label.setTextColor(Ui.TEXT_PRIMARY);
                        updateParamLabel(label, defRef.label, defRef.defaultValue, showDecimal, false);
                        resetBtn.setVisibility(View.INVISIBLE);
                        mChanged = true;
                    }
                });

            LinearLayout minMax = new LinearLayout(this);
            minMax.setOrientation(LinearLayout.HORIZONTAL);
            TextView tvMin = Ui.text(this,
                                     showDecimal ? String.format("%.2f", def.min) : String.valueOf((int) def.min),
                                     11, Ui.TEXT_TERTIARY, false);

            String defaultLabel = showDecimal
                ? String.format("default: %.2f", def.defaultValue)
                : "default: " + (int) def.defaultValue;
            TextView tvDefault = Ui.text(this, defaultLabel, 10, Ui.TEXT_TERTIARY, false);
            tvDefault.setGravity(Gravity.CENTER);

            TextView tvMax = Ui.text(this,
                                     showDecimal ? String.format("%.2f", def.max) : String.valueOf((int) def.max),
                                     11, Ui.TEXT_TERTIARY, false);
            minMax.addView(tvMin, Ui.lp(0, WC, 1f));
            minMax.addView(tvDefault, Ui.lp(0, WC, 1f));
            minMax.addView(tvMax);
            LinearLayout.LayoutParams mmLp = Ui.lp(MP, WC);
            mmLp.bottomMargin = Ui.dp(this, 14);
            container.addView(minMax, mmLp);
        }

        new AlertDialog.Builder(this)
            .setTitle(module.getName() + " — Ajustes")
            .setView(scroll)
            .setPositiveButton("Cerrar", null)
            .setNeutralButton("Restablecer todo", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    for (Map.Entry<String, Module.ParamDef> e : defs.entrySet()) {
                        mModuleManager.setParamValue(module, e.getKey(), e.getValue().defaultValue);
                    }
                    mChanged = true;

                    Toast.makeText(FiltersActivity.this,
                                   module.getName() + ": parámetros restablecidos",
                                   Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }

    private void updateParamLabel(TextView tv, String label, float value,
                                  boolean decimal, boolean modified) {
        String v = decimal ? String.format("%.3f", value) : String.valueOf((int) value);
        String mod = modified ? "  ●" : "";
        tv.setText(label + ": " + v + mod);
    }

    // -------------------------------------------------------------------------
    // Fila del overlay de FPS: switch + botón de posición
    // -------------------------------------------------------------------------

    private View buildFpsOverlayRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int side = Ui.dp(this, 20);
        int vert = Ui.dp(this, 10);
        row.setPadding(side, vert, side, vert);
        row.setBackground(Ui.roundRect(Ui.BG_SURFACE, this, 12));

        LinearLayout.LayoutParams rowLp = Ui.lp(MP, WC);
        rowLp.leftMargin   = Ui.dp(this, 12);
        rowLp.rightMargin  = Ui.dp(this, 12);
        rowLp.bottomMargin = Ui.dp(this, 10);
        row.setLayoutParams(rowLp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView title = Ui.text(this, "Overlay de FPS", 14, Ui.TEXT_PRIMARY, true);
        col.addView(title, Ui.lp(WC, WC));

        TextView sub = Ui.text(this, "Muestra los FPS sobre la pantalla", 12, Ui.TEXT_TERTIARY, false);
        LinearLayout.LayoutParams subLp = Ui.lp(WC, WC);
        subLp.topMargin = Ui.dp(this, 2);
        col.addView(sub, subLp);

        row.addView(col, Ui.lp(0, WC, 1f));

        // Botón de posición (pin)
        mBtnFpsPosition = Ui.text(this, "📍", 18, Ui.TEXT_PRIMARY, false);
        mBtnFpsPosition.setGravity(Gravity.CENTER);
        mBtnFpsPosition.setBackground(Ui.buttonBgStroke(
                                          this, Ui.BG_ELEV, Ui.ACCENT_SOFT, Ui.DIVIDER, 22, 1f));
        mBtnFpsPosition.setClickable(true);
        mBtnFpsPosition.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    startActivityForResult(
                        new Intent(FiltersActivity.this, FpsPositionActivity.class),
                        REQ_FPS_POSITION);
                }
            });
        LinearLayout.LayoutParams btnLp = Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44));
        btnLp.rightMargin = Ui.dp(this, 8);
        row.addView(mBtnFpsPosition, btnLp);

        final Switch sw = new Switch(this);
        sw.setChecked(mFpsOverlay);
        sw.setShowText(false);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (mFpsOverlay == isChecked) return;

                    mFpsOverlay = isChecked;

                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_FPS_OVERLAY, isChecked)
                        .commit();

                    sendFpsOverlayBroadcast(isChecked);
                    mChanged = true;
                    updateFpsPositionButtonState();
                }
            });
        mFpsSwitch = sw;
        row.addView(sw);

        updateFpsPositionButtonState();
        return row;
    }

    private void updateFpsPositionButtonState() {
        if (mBtnFpsPosition == null) return;
        mBtnFpsPosition.setEnabled(mFpsOverlay);
        mBtnFpsPosition.setTextColor(mFpsOverlay ? Ui.TEXT_PRIMARY : Ui.TEXT_TERTIARY);
    }

    private void sendFpsOverlayBroadcast(boolean enabled) {
        Intent intent = new Intent(CaptureService.ACTION_FPS_OVERLAY);
        intent.setPackage(getPackageName());
        intent.putExtra(CaptureService.EXTRA_FPS_ENABLED, enabled);
        Log.d(TAG, "sendFpsOverlayBroadcast enabled=" + enabled);
        sendBroadcast(intent);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FPS_POSITION) {
            if (resultCode == RESULT_OK) {
                // La posición ya se guardó en prefs dentro de FpsPositionActivity.
                // Reemitimos el broadcast para que el servicio re-lea y reposicione.
                Log.d(TAG, "onActivityResult FPS position: reemitiendo overlay");
                sendFpsOverlayBroadcast(mFpsOverlay);
                mChanged = true;
            }
        }
    }
}
