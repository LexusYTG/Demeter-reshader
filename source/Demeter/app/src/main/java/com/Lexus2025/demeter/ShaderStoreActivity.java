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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ShaderStoreActivity extends Activity {

    private static final String TAG = "ShaderStore";
    private static final String INDEX_URL =
	"https://raw.githubusercontent.com/LexusYTG/Demeter-reshader/main/Store/store_index.md";

    private static final String CACHE_PREFS = "shader_store_cache";
    private static final String KEY_INDEX_MD = "index_md";
    private static final String KEY_CACHE_MS = "cache_time_ms";

    public static final String RESULT_EXTRA_NAME = "installed_name";

    private static final int MP = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WC = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static final long SEARCH_DEBOUNCE_MS = 120L;

    private static final int[] ACCENT_PALETTE = {
        0xFF7C5CFF,
        0xFF4ADE80,
        0xFFFBBF24,
        0xFF60A5FA,
        0xFFF472B6,
        0xFFA78BFA,
    };

    private static class ShaderEntry {
        String name;
        String author;
        String downloadUrl;
        String imageUrl;
        String description;
        String category;
    }

    private static class Category {
        String title;
        List<ShaderEntry> entries = new ArrayList<ShaderEntry>();
    }

    private final ExecutorService mPool = Executors.newCachedThreadPool();
    private final Handler         mUi   = new Handler(Looper.getMainLooper());

    private final Runnable mSearchRunnable = new Runnable() {
        @Override public void run() { renderCatalog(); }
    };

    private ModuleManager mModuleManager;
    private List<Category> mCategories = new ArrayList<Category>();

    private FrameLayout  mContent;
    private View         mLoadingView;
    private View         mErrorView;
    private View         mEmptyView;
    private ScrollView   mCatalogScroll;
    private LinearLayout mCatalogContainer;
    private EditText     mSearch;
    private TextView     mTvCount;
    private TextView     mTvErrorMsg;

    private HorizontalScrollView mCategoryChipsScroll;
    private LinearLayout         mCategoryChips;
    private HorizontalScrollView mAuthorChipsScroll;
    private LinearLayout         mAuthorChips;

    private FrameLayout  mRefreshBtnWrap;
    private TextView     mRefreshIcon;
    private ProgressBar  mRefreshSpinner;
    private boolean      mRefreshing = false;

    private volatile boolean mInstalling = false;
    private View     mInstallOverlay;
    private TextView mTvInstallLabel;

    private String mQuery            = "";
    private String mSelectedCategory = null;
    private String mSelectedAuthor   = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        applyFullscreen();

        mModuleManager = new ModuleManager(this);
        setContentView(buildRoot());

        if (loadIndexFromCache()) {
            updateCount();
            rebuildCategoryChips();
            rebuildAuthorChips();
            renderCatalog();
        } else {
            fetchIndex(false);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyFullscreen();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mUi.removeCallbacks(mSearchRunnable);
        mPool.shutdownNow();
    }

    @Override
    public void onBackPressed() {
        if (mInstalling) {
            Toast.makeText(this, "Espera a que termine la instalación",
                           Toast.LENGTH_SHORT).show();
            return;
        }
        super.onBackPressed();
    }

    private void applyFullscreen() {
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
    }

    private SharedPreferences cachePrefs() {
        return getSharedPreferences(CACHE_PREFS, MODE_PRIVATE);
    }

    private void saveIndexToCache(String md) {
        cachePrefs().edit()
            .putString(KEY_INDEX_MD, md)
            .putLong(KEY_CACHE_MS, System.currentTimeMillis())
            .apply();
    }

    private boolean loadIndexFromCache() {
        String md = cachePrefs().getString(KEY_INDEX_MD, null);
        if (md == null || md.isEmpty()) return false;
        try {
            mCategories = parseMarkdown(md);
            return !mCategories.isEmpty();
        } catch (Exception e) {
            Log.w(TAG, "loadIndexFromCache: cache corrupto, se descarta");
            cachePrefs().edit().clear().apply();
            mCategories = new ArrayList<Category>();
            return false;
        }
    }

    private View buildRoot() {

        FrameLayout outer = new FrameLayout(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG_ROOT);

        int side = Ui.dp(this, 14);

        LinearLayout.LayoutParams headerLp = Ui.lp(MP, WC);
        headerLp.leftMargin  = side;
        headerLp.rightMargin = side;
        headerLp.topMargin   = Ui.dp(this, 14);
        root.addView(buildHeader(), headerLp);

        LinearLayout.LayoutParams searchLp = Ui.lp(MP, Ui.dp(this, 44));
        searchLp.leftMargin   = side;
        searchLp.rightMargin  = side;
        searchLp.topMargin    = Ui.dp(this, 10);
        searchLp.bottomMargin = Ui.dp(this, 6);
        root.addView(buildSearchRow(), searchLp);

        root.addView(buildCategoryChipsRow());
        root.addView(buildAuthorChipsRow());

        mContent = new FrameLayout(this);
        LinearLayout.LayoutParams contentLp = Ui.lp(MP, 0, 1f);
        contentLp.topMargin = Ui.dp(this, 6);
        root.addView(mContent, contentLp);

        mCatalogScroll = new ScrollView(this);
        mCatalogScroll.setVerticalScrollBarEnabled(false);
        mCatalogScroll.setPadding(side, 0, side, Ui.dp(this, 20));
        mCatalogContainer = new LinearLayout(this);
        mCatalogContainer.setOrientation(LinearLayout.VERTICAL);
        mCatalogScroll.addView(mCatalogContainer,
                               new FrameLayout.LayoutParams(MP, WC));

        mLoadingView = buildLoadingView();
        mErrorView   = buildErrorView();
        mEmptyView   = buildEmptyView();

        mContent.addView(mCatalogScroll, new FrameLayout.LayoutParams(MP, MP));
        mContent.addView(mLoadingView,   new FrameLayout.LayoutParams(MP, MP));
        mContent.addView(mErrorView,     new FrameLayout.LayoutParams(MP, MP));
        mContent.addView(mEmptyView,     new FrameLayout.LayoutParams(MP, MP));

        outer.addView(root, new FrameLayout.LayoutParams(MP, MP));

        buildInstallOverlay();
        outer.addView(mInstallOverlay, new FrameLayout.LayoutParams(MP, MP));

        showLoading();
        return outer;
    }

    private void buildInstallOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xCC000000);
        overlay.setClickable(true);
        overlay.setFocusable(true);

        overlay.setOnTouchListener(new View.OnTouchListener() {
				@Override public boolean onTouch(View v, MotionEvent e) { return true; }
			});
        overlay.setVisibility(View.GONE);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackground(Ui.roundRect(Ui.BG_SURFACE, this, 14));
        int pad = Ui.dp(this, 26);
        box.setPadding(pad, pad, pad, pad);

        ProgressBar pb = new ProgressBar(this);
        box.addView(pb, Ui.lp(Ui.dp(this, 48), Ui.dp(this, 48)));

        mTvInstallLabel = Ui.text(this, "Instalando…", 15, Ui.TEXT_PRIMARY, true);
        mTvInstallLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lLp = Ui.lp(WC, WC);
        lLp.topMargin = Ui.dp(this, 14);
        box.addView(mTvInstallLabel, lLp);

        TextView hint = Ui.text(this, "No cierres esta pantalla",
                                12, Ui.TEXT_TERTIARY, false);
        LinearLayout.LayoutParams hLp = Ui.lp(WC, WC);
        hLp.topMargin = Ui.dp(this, 4);
        box.addView(hint, hLp);

        FrameLayout.LayoutParams boxLp = new FrameLayout.LayoutParams(WC, WC);
        boxLp.gravity = Gravity.CENTER;
        overlay.addView(box, boxLp);

        mInstallOverlay = overlay;
    }

    private void showInstallOverlay(String name) {
        if (mTvInstallLabel != null) {
            mTvInstallLabel.setText("Instalando: " + name);
        }
        if (mInstallOverlay != null) {
            mInstallOverlay.setVisibility(View.VISIBLE);
            mInstallOverlay.bringToFront();
        }
    }

    private void hideInstallOverlay() {
        if (mInstallOverlay != null) {
            mInstallOverlay.setVisibility(View.GONE);
        }
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = makeIconBtn("\u2715");
        back.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (mInstalling) {
                        Toast.makeText(ShaderStoreActivity.this,
                                       "Espera a que termine la instalación",
                                       Toast.LENGTH_SHORT).show();
                        return;
                    }
                    finish();
                }
            });
        row.addView(back, Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44)));

        TextView title = Ui.text(this, "Tienda", 24, Ui.TEXT_PRIMARY, true);
        LinearLayout.LayoutParams titleLp = Ui.lp(0, WC, 1f);
        titleLp.leftMargin = Ui.dp(this, 12);
        row.addView(title, titleLp);

        mTvCount = Ui.text(this, "—", 13, Ui.ACCENT, true);
        mTvCount.setGravity(Gravity.CENTER);
        mTvCount.setBackground(Ui.roundRectStroke(
                                   Ui.ACCENT_SOFT, Ui.ACCENT_SOFT, this, 20, 0));
        int hp = Ui.dp(this, 12);
        int vp = Ui.dp(this, 6);
        mTvCount.setPadding(hp, vp, hp, vp);
        row.addView(mTvCount);

        return row;
    }

    private View buildSearchRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout searchWrap = new FrameLayout(this);
        searchWrap.setBackground(Ui.roundRectStroke(
                                     Ui.BG_SURFACE, Ui.DIVIDER, this, 12, 1f));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(this, 12);
        inner.setPadding(pad, 0, pad, 0);
        searchWrap.addView(inner, new FrameLayout.LayoutParams(MP, MP));

        TextView icon = Ui.text(this, "\uD83D\uDD0D", 14, Ui.TEXT_TERTIARY, false);
        inner.addView(icon);

        mSearch = new EditText(this);
        mSearch.setHint("Buscar shader o autor…");
        mSearch.setHintTextColor(Ui.TEXT_TERTIARY);
        mSearch.setTextColor(Ui.TEXT_PRIMARY);
        mSearch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mSearch.setBackgroundColor(0x00000000);
        mSearch.setSingleLine(true);
        mSearch.setPadding(Ui.dp(this, 10), 0, 0, 0);
        inner.addView(mSearch, Ui.lp(0, MP, 1f));

        mSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void afterTextChanged(Editable s) {
                    mQuery = s.toString().trim().toLowerCase();
                    mUi.removeCallbacks(mSearchRunnable);
                    mUi.postDelayed(mSearchRunnable, SEARCH_DEBOUNCE_MS);
                }
            });

        LinearLayout.LayoutParams searchLp = Ui.lp(0, MP, 1f);
        searchLp.rightMargin = Ui.dp(this, 8);
        row.addView(searchWrap, searchLp);

        row.addView(buildRefreshBtn(), Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44)));

        return row;
    }

    private View buildRefreshBtn() {
        mRefreshBtnWrap = new FrameLayout(this);
        mRefreshBtnWrap.setBackground(Ui.buttonBgStroke(
                                          this, Ui.BG_ELEV, Ui.DIVIDER, Ui.DIVIDER, 12, 1f));
        mRefreshBtnWrap.setClickable(true);

        mRefreshIcon = new TextView(this);
        mRefreshIcon.setText("\u21BB");
        mRefreshIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        mRefreshIcon.setTextColor(Ui.TEXT_PRIMARY);
        mRefreshIcon.setGravity(Gravity.CENTER);
        mRefreshIcon.setTypeface(Typeface.DEFAULT_BOLD);
        mRefreshBtnWrap.addView(mRefreshIcon,
                                new FrameLayout.LayoutParams(MP, MP));

        mRefreshSpinner = new ProgressBar(this);
        mRefreshSpinner.setVisibility(View.GONE);
        FrameLayout.LayoutParams spLp = new FrameLayout.LayoutParams(
            Ui.dp(this, 24), Ui.dp(this, 24));
        spLp.gravity = Gravity.CENTER;
        mRefreshBtnWrap.addView(mRefreshSpinner, spLp);

        mRefreshBtnWrap.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (mRefreshing || mInstalling) return;
                    fetchIndex(true);
                }
            });

        return mRefreshBtnWrap;
    }

    private void setRefreshing(boolean on) {
        mRefreshing = on;
        if (mRefreshIcon != null) {
            mRefreshIcon.setVisibility(on ? View.GONE : View.VISIBLE);
        }
        if (mRefreshSpinner != null) {
            mRefreshSpinner.setVisibility(on ? View.VISIBLE : View.GONE);
        }
        if (mRefreshBtnWrap != null) {
            mRefreshBtnWrap.setEnabled(!on);
            mRefreshBtnWrap.setAlpha(on ? 0.6f : 1f);
        }
    }

    private View buildCategoryChipsRow() {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.HORIZONTAL);
        int side = Ui.dp(this, 14);
        holder.setPadding(side, Ui.dp(this, 4), side, 0);

        mCategoryChipsScroll = new HorizontalScrollView(this);
        mCategoryChipsScroll.setHorizontalScrollBarEnabled(false);
        mCategoryChips = new LinearLayout(this);
        mCategoryChips.setOrientation(LinearLayout.HORIZONTAL);
        mCategoryChipsScroll.addView(mCategoryChips,
                                     new FrameLayout.LayoutParams(WC, WC));

        LinearLayout.LayoutParams scrollLp = Ui.lp(0, Ui.dp(this, 34), 1f);
        holder.addView(mCategoryChipsScroll, scrollLp);
        return holder;
    }

    private View buildAuthorChipsRow() {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.HORIZONTAL);
        int side = Ui.dp(this, 14);
        holder.setPadding(side, Ui.dp(this, 4), side, Ui.dp(this, 4));

        mAuthorChipsScroll = new HorizontalScrollView(this);
        mAuthorChipsScroll.setHorizontalScrollBarEnabled(false);
        mAuthorChips = new LinearLayout(this);
        mAuthorChips.setOrientation(LinearLayout.HORIZONTAL);
        mAuthorChipsScroll.addView(mAuthorChips,
                                   new FrameLayout.LayoutParams(WC, WC));

        LinearLayout.LayoutParams scrollLp = Ui.lp(0, Ui.dp(this, 34), 1f);
        holder.addView(mAuthorChipsScroll, scrollLp);
        return holder;
    }

    private TextView makeFilterChip(String label, boolean selected,
                                    final Runnable onSelect) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        chip.setTypeface(Ui.medium());
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);

        int hp = Ui.dp(this, 14);
        int vp = Ui.dp(this, 6);
        chip.setPadding(hp, vp, hp, vp);

        if (selected) {
            chip.setTextColor(0xFFFFFFFF);
            chip.setBackground(Ui.roundRect(Ui.ACCENT, this, 20));
        } else {
            chip.setTextColor(Ui.TEXT_SECOND);
            chip.setBackground(Ui.roundRectStroke(
                                   Ui.BG_SURFACE, Ui.DIVIDER, this, 20, 1f));
        }

        chip.setClickable(true);
        chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { onSelect.run(); }
            });

        LinearLayout.LayoutParams lp = Ui.lp(WC, WC);
        lp.rightMargin = Ui.dp(this, 6);
        chip.setLayoutParams(lp);
        return chip;
    }

    private void rebuildCategoryChips() {
        if (mCategoryChips == null) return;
        mCategoryChips.removeAllViews();

        boolean found = false;
        for (Category c : mCategories) {
            if (c.title.equals(mSelectedCategory)) { found = true; break; }
        }
        if (!found) mSelectedCategory = null;

        mCategoryChips.addView(makeFilterChip(
                                   "Todas", mSelectedCategory == null,
                                   new Runnable() {
                                       @Override public void run() {
                                           mSelectedCategory = null;
                                           rebuildCategoryChips();
                                           renderCatalog();
                                       }
                                   }));

        for (final Category c : mCategories) {
            boolean sel = c.title.equals(mSelectedCategory);
            mCategoryChips.addView(makeFilterChip(
                                       c.title, sel,
                                       new Runnable() {
                                           @Override public void run() {
                                               mSelectedCategory = c.title;
                                               rebuildCategoryChips();
                                               renderCatalog();
                                           }
                                       }));
        }
    }

    private void rebuildAuthorChips() {
        if (mAuthorChips == null) return;
        mAuthorChips.removeAllViews();

        TreeSet<String> authors = new TreeSet<String>();
        for (Category c : mCategories) {
            for (ShaderEntry e : c.entries) {
                if (e.author != null && !e.author.isEmpty()) {
                    authors.add(e.author);
                }
            }
        }

        if (mSelectedAuthor != null && !authors.contains(mSelectedAuthor)) {
            mSelectedAuthor = null;
        }

        mAuthorChips.addView(makeFilterChip(
                                 "Todos", mSelectedAuthor == null,
                                 new Runnable() {
                                     @Override public void run() {
                                         mSelectedAuthor = null;
                                         rebuildAuthorChips();
                                         renderCatalog();
                                     }
                                 }));

        for (final String a : authors) {
            boolean sel = a.equals(mSelectedAuthor);
            mAuthorChips.addView(makeFilterChip(
                                     a, sel,
                                     new Runnable() {
                                         @Override public void run() {
                                             mSelectedAuthor = a;
                                             rebuildAuthorChips();
                                             renderCatalog();
                                         }
                                     }));
        }
    }

    private View buildLoadingView() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);

        ProgressBar pb = new ProgressBar(this);
        col.addView(pb, Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44)));

        TextView tv = Ui.text(this, "Cargando catálogo…", 13, Ui.TEXT_SECOND, false);
        LinearLayout.LayoutParams tvLp = Ui.lp(WC, WC);
        tvLp.topMargin = Ui.dp(this, 12);
        col.addView(tv, tvLp);

        return col;
    }

    private View buildErrorView() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(Ui.roundRectStroke(
                               Ui.BG_SURFACE, Ui.DANGER, this, 14, 1f));
        int p = Ui.dp(this, 24);
        card.setPadding(p, p, p, p);

        LinearLayout.LayoutParams cardLp = Ui.lp(Ui.dp(this, 260), WC);
        col.addView(card, cardLp);

        TextView icon = Ui.text(this, "\u26A0", 34, Ui.DANGER, false);
        card.addView(icon);

        TextView title = Ui.text(this, "No se pudo cargar", 15, Ui.TEXT_PRIMARY, true);
        LinearLayout.LayoutParams tLp = Ui.lp(WC, WC);
        tLp.topMargin = Ui.dp(this, 8);
        card.addView(title, tLp);

        mTvErrorMsg = Ui.text(this, "", 12, Ui.TEXT_SECOND, false);
        mTvErrorMsg.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mLp = Ui.lp(WC, WC);
        mLp.topMargin    = Ui.dp(this, 6);
        mLp.bottomMargin = Ui.dp(this, 16);
        card.addView(mTvErrorMsg, mLp);

        TextView retry = Ui.text(this, "Reintentar", 13, 0xFFFFFFFF, true);
        retry.setGravity(Gravity.CENTER);
        retry.setBackground(Ui.buttonBgSolid(this, Ui.ACCENT, Ui.ACCENT_DIM, 10));
        retry.setClickable(true);
        retry.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { fetchIndex(false); }
            });
        card.addView(retry, Ui.lp(Ui.dp(this, 130), Ui.dp(this, 40)));

        return col;
    }

    private View buildEmptyView() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);

        TextView icon = Ui.text(this, "\u2737", 36, Ui.TEXT_TERTIARY, false);
        col.addView(icon);

        TextView msg = Ui.text(this, "No hay shaders que coincidan", 13, Ui.TEXT_SECOND, false);
        LinearLayout.LayoutParams mLp = Ui.lp(WC, WC);
        mLp.topMargin = Ui.dp(this, 8);
        col.addView(msg, mLp);

        return col;
    }

    private void showLoading() {
        mLoadingView.setVisibility(View.VISIBLE);
        mErrorView.setVisibility(View.GONE);
        mEmptyView.setVisibility(View.GONE);
        mCatalogScroll.setVisibility(View.GONE);
    }

    private void showError(String msg) {
        mLoadingView.setVisibility(View.GONE);
        mErrorView.setVisibility(View.VISIBLE);
        mEmptyView.setVisibility(View.GONE);
        mCatalogScroll.setVisibility(View.GONE);
        mTvErrorMsg.setText(msg);
    }

    private void showCatalog() {
        mLoadingView.setVisibility(View.GONE);
        mErrorView.setVisibility(View.GONE);
        mEmptyView.setVisibility(View.GONE);
        mCatalogScroll.setVisibility(View.VISIBLE);
    }

    private void showEmpty() {
        mLoadingView.setVisibility(View.GONE);
        mErrorView.setVisibility(View.GONE);
        mEmptyView.setVisibility(View.VISIBLE);
        mCatalogScroll.setVisibility(View.GONE);
    }

    private TextView makeIconBtn(String glyph) {
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

    private void fetchIndex(final boolean userInitiated) {
        final boolean hasCache = !mCategories.isEmpty();

        if (!hasCache) {
            showLoading();
        } else if (userInitiated) {
            setRefreshing(true);
        }

        mPool.execute(new Runnable() {
                @Override public void run() {
                    try {
                        final String md = downloadString(INDEX_URL);
                        final List<Category> cats = parseMarkdown(md);

                        if (cats.isEmpty()) {
                            throw new Exception("El catálogo llegó vacío");
                        }

                        saveIndexToCache(md);

                        mUi.post(new Runnable() {
                                @Override public void run() {
                                    mCategories = cats;
                                    updateCount();
                                    rebuildCategoryChips();
                                    rebuildAuthorChips();
                                    renderCatalog();
                                    setRefreshing(false);
                                    if (userInitiated) {
                                        Toast.makeText(ShaderStoreActivity.this,
                                                       "Catálogo actualizado",
                                                       Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                    } catch (final Exception e) {
                        Log.w(TAG, "fetchIndex falló: " + e.getMessage());
                        mUi.post(new Runnable() {
                                @Override public void run() {
                                    setRefreshing(false);
                                    if (hasCache) {
                                        Toast.makeText(ShaderStoreActivity.this,
                                                       "No se pudo actualizar: " + e.getMessage(),
                                                       Toast.LENGTH_LONG).show();
                                    } else {
                                        showError(e.getMessage() == null
                                                  ? "Error desconocido"
                                                  : e.getMessage());
                                    }
                                }
                            });
                    }
                }
            });
    }

    private void updateCount() {
        int total = 0;
        for (Category c : mCategories) total += c.entries.size();
        mTvCount.setText(String.valueOf(total));
    }

    private void renderCatalog() {
        if (mCatalogContainer == null) return;
        mCatalogContainer.removeAllViews();

        if (mCategories.isEmpty()) {
            showEmpty();
            return;
        }

        int shown = 0;
        for (Category cat : mCategories) {
            if (mSelectedCategory != null && !mSelectedCategory.equals(cat.title)) {
                continue;
            }

            List<ShaderEntry> visible = new ArrayList<ShaderEntry>();
            for (ShaderEntry e : cat.entries) {
                if (matches(e)) visible.add(e);
            }
            if (visible.isEmpty()) continue;

            mCatalogContainer.addView(buildCategoryHeader(cat.title, visible.size()));

            for (ShaderEntry e : visible) {
                mCatalogContainer.addView(buildEntryCard(e));
                shown++;
            }
        }

        if (shown == 0) showEmpty();
        else            showCatalog();
    }

    private boolean matches(ShaderEntry e) {
        if (mSelectedAuthor != null) {
            if (e.author == null || !e.author.equals(mSelectedAuthor)) return false;
        }
        if (!mQuery.isEmpty()) {
            boolean hit = false;
            if (e.name != null && e.name.toLowerCase().contains(mQuery)) hit = true;
            if (!hit && e.author != null && e.author.toLowerCase().contains(mQuery)) hit = true;
            if (!hit && e.description != null
                && e.description.toLowerCase().contains(mQuery)) hit = true;
            if (!hit) return false;
        }
        return true;
    }

    private View buildCategoryHeader(String title, int count) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int top    = Ui.dp(this, 14);
        int bottom = Ui.dp(this, 6);
        row.setPadding(Ui.dp(this, 4), top, Ui.dp(this, 4), bottom);

        TextView tv = Ui.text(this, title.toUpperCase(), 11, Ui.ACCENT, true);
        tv.setLetterSpacing(0.18f);
        row.addView(tv, Ui.lp(0, WC, 1f));

        TextView badge = Ui.text(this, String.valueOf(count), 11, Ui.TEXT_TERTIARY, true);
        badge.setGravity(Gravity.CENTER);
        row.addView(badge);

        return row;
    }

    private View buildEntryCard(final ShaderEntry e) {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(Ui.roundRect(Ui.BG_ELEV, this, 14));
        LinearLayout.LayoutParams cardLp = Ui.lp(MP, WC);
        cardLp.bottomMargin = Ui.dp(this, 6);
        card.setLayoutParams(cardLp);

        View accent = new View(this);
        GradientDrawable accentBg = new GradientDrawable();
        accentBg.setShape(GradientDrawable.RECTANGLE);
        accentBg.setColor(accentColor(e));
        float r = Ui.dp(this, 3);
        accentBg.setCornerRadii(new float[]{0, 0, r, r, r, r, 0, 0});
        accent.setBackground(accentBg);
        FrameLayout.LayoutParams aLp = new FrameLayout.LayoutParams(
            Ui.dp(this, 3), MP);
        card.addView(accent, aLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 14),
                       Ui.dp(this, 14), Ui.dp(this, 14));
        card.addView(row, new FrameLayout.LayoutParams(MP, WC));

        FrameLayout thumbWrap = new FrameLayout(this);
        thumbWrap.setBackground(Ui.roundRect(0xFF2A2A3E, this, 10));
        if (Build.VERSION.SDK_INT >= 21) thumbWrap.setClipToOutline(true);

        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbWrap.addView(iv, new FrameLayout.LayoutParams(MP, MP));

        final TextView initial = Ui.text(this,
                                         (e.name != null && !e.name.isEmpty())
                                         ? e.name.substring(0, 1).toUpperCase()
                                         : "?",
                                         24, 0x55FFFFFF, true);
        initial.setGravity(Gravity.CENTER);
        thumbWrap.addView(initial, new FrameLayout.LayoutParams(MP, MP));

        int thumb = Ui.dp(this, 64);
        LinearLayout.LayoutParams thumbLp = Ui.lp(thumb, thumb);
        thumbLp.rightMargin = Ui.dp(this, 14);
        row.addView(thumbWrap, thumbLp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        row.addView(col, Ui.lp(0, WC, 1f));

        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = Ui.text(this, e.name, 16, Ui.TEXT_PRIMARY, true);
        nameRow.addView(name, Ui.lp(WC, WC));

        if (mModuleManager.getModuleByName(e.name) != null) {
            LinearLayout.LayoutParams chipLp = Ui.lp(WC, WC);
            chipLp.leftMargin = Ui.dp(this, 8);
            nameRow.addView(buildInstalledChip(), chipLp);
        }
        col.addView(nameRow);

        TextView author = Ui.text(this, "por " + e.author, 12, Ui.TEXT_TERTIARY, false);
        LinearLayout.LayoutParams authorLp = Ui.lp(MP, WC);
        authorLp.topMargin = Ui.dp(this, 2);
        col.addView(author, authorLp);

        if (e.description != null && !e.description.isEmpty()) {
            TextView desc = Ui.text(this, e.description, 12, Ui.TEXT_SECOND, false);
            desc.setMaxLines(2);
            desc.setEllipsize(TextUtils.TruncateAt.END);
            desc.setLineSpacing(Ui.dp(this, 2), 1f);
            LinearLayout.LayoutParams dLp = Ui.lp(MP, WC);
            dLp.topMargin    = Ui.dp(this, 6);
            dLp.bottomMargin = Ui.dp(this, 10);
            col.addView(desc, dLp);
        } else {
            col.addView(new View(this), Ui.lp(MP, Ui.dp(this, 10)));
        }

        col.addView(buildInstallButton(e));

        if (e.imageUrl != null && !e.imageUrl.isEmpty()) {
            loadThumbnail(e.imageUrl, iv, initial);
        }

        return card;
    }

    private int accentColor(ShaderEntry e) {
        if (e.name == null) return Ui.ACCENT;
        int hash = Math.abs(e.name.hashCode());
        return ACCENT_PALETTE[hash % ACCENT_PALETTE.length];
    }

    private View buildInstalledChip() {
        TextView chip = Ui.text(this, "\u2713 INSTALADO", 9, 0xFF4ADE80, true);
        chip.setLetterSpacing(0.10f);
        chip.setBackground(Ui.roundRect(0xFF1A3A2A, this, 4));
        int hp = Ui.dp(this, 6);
        int vp = Ui.dp(this, 3);
        chip.setPadding(hp, vp, hp, vp);
        return chip;
    }

    private View buildInstallButton(final ShaderEntry entry) {
        final TextView btn = new TextView(this);
        boolean installed = mModuleManager.getModuleByName(entry.name) != null;

        btn.setGravity(Gravity.CENTER);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setTypeface(Ui.medium());
        btn.setClickable(true);

        int hp = Ui.dp(this, 16);
        int vp = Ui.dp(this, 8);

        if (installed) {
            btn.setText("Instalado");
            btn.setTextColor(Ui.TEXT_SECOND);
            btn.setBackground(Ui.roundRectStroke(
                                  Ui.BG_SURFACE, Ui.DIVIDER, this, 10, 1f));
            btn.setEnabled(false);
            btn.setPadding(hp, vp, hp, vp);
        } else {
            btn.setText("Instalar");
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackground(Ui.buttonBgSolid(this, Ui.ACCENT, Ui.ACCENT_DIM, 10));
            btn.setPadding(hp, vp, hp, vp);
            btn.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { installShader(entry, btn); }
                });
        }

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.addView(btn, Ui.lp(WC, Ui.dp(this, 36)));
        return wrap;
    }

    private void installShader(final ShaderEntry entry, final TextView btn) {
        if (mInstalling) {
            Toast.makeText(this, "Ya hay una instalación en curso",
                           Toast.LENGTH_SHORT).show();
            return;
        }
        mInstalling = true;

        showInstallOverlay(entry.name);

        btn.setText("Instalando…");
        btn.setEnabled(false);

        mPool.execute(new Runnable() {
                @Override public void run() {
                    try {
                        final String json = downloadString(entry.downloadUrl);

                        mModuleManager.installFromJson(json);

                        mUi.post(new Runnable() {
                                @Override public void run() {

                                    hideInstallOverlay();
                                    mInstalling = false;

                                    btn.setText("Instalado");
                                    btn.setEnabled(false);
                                    btn.setTextColor(Ui.TEXT_SECOND);
                                    btn.setBackground(Ui.roundRectStroke(
														  Ui.BG_SURFACE, Ui.DIVIDER,
														  ShaderStoreActivity.this, 10, 1f));

                                    Intent res = new Intent();
                                    res.putExtra(RESULT_EXTRA_NAME, entry.name);
                                    setResult(RESULT_OK, res);

                                    Toast.makeText(ShaderStoreActivity.this,
                                                   entry.name + " instalado",
                                                   Toast.LENGTH_SHORT).show();
                                }
                            });
                    } catch (final Exception e) {
                        mUi.post(new Runnable() {
                                @Override public void run() {
                                    hideInstallOverlay();
                                    mInstalling = false;

                                    btn.setText("Instalar");
                                    btn.setEnabled(true);
                                    btn.setTextColor(0xFFFFFFFF);
                                    btn.setBackground(Ui.buttonBgSolid(
														  ShaderStoreActivity.this,
														  Ui.ACCENT, Ui.ACCENT_DIM, 10));

                                    Toast.makeText(ShaderStoreActivity.this,
                                                   "Error: " + e.getMessage(),
                                                   Toast.LENGTH_LONG).show();
                                }
                            });
                    }
                }
            });
    }

    private void loadThumbnail(final String url, final ImageView iv,
                               final TextView placeholder) {
        mPool.execute(new Runnable() {
                @Override public void run() {
                    try {
                        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                        c.setConnectTimeout(5000);
                        c.setReadTimeout(8000);
                        c.connect();
                        final Bitmap bmp = BitmapFactory.decodeStream(c.getInputStream());
                        c.disconnect();
                        if (bmp != null) {
                            mUi.post(new Runnable() {
                                    @Override public void run() {
                                        iv.setImageBitmap(bmp);
                                        placeholder.setVisibility(View.GONE);
                                    }
                                });
                        }
                    } catch (Exception ignored) {}
                }
            });
    }

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
        while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        reader.close();
        conn.disconnect();
        return sb.toString();
    }

    private List<Category> parseMarkdown(String md) {
        List<Category> categories = new ArrayList<Category>();
        Category currentCat = null;
        ShaderEntry currentEntry = null;

        for (String rawLine : md.split("\n")) {
            String line = rawLine.trim();

            if (line.startsWith("## Categoría:") || line.startsWith("## Categoria:")) {
                if (currentEntry != null && currentCat != null && isEntryValid(currentEntry)) {
                    currentCat.entries.add(currentEntry);
                }
                currentEntry = null;
                currentCat = new Category();
                currentCat.title = line.replaceFirst("##\\s*Categor[ií]a:\\s*", "").trim();
                categories.add(currentCat);

            } else if (line.startsWith("### ")) {
                if (currentEntry != null && currentCat != null && isEntryValid(currentEntry)) {
                    currentCat.entries.add(currentEntry);
                }
                currentEntry = new ShaderEntry();
                currentEntry.name = line.substring(4).trim();
                if (currentCat != null) currentEntry.category = currentCat.title;

            } else if (currentEntry != null) {
                if (line.startsWith("- **Autor:**")) {
                    currentEntry.author = extractField(line, "Autor");
                } else if (line.startsWith("- **Enlace:**")) {
                    currentEntry.downloadUrl = extractField(line, "Enlace");
                } else if (line.startsWith("- **Imagen:**")) {
                    currentEntry.imageUrl = extractField(line, "Imagen");
                } else if (line.startsWith("- **Descripción:**")
                           || line.startsWith("- **Descripcion:**")) {
                    currentEntry.description = extractField(line, "Descripc?i[oó]n");
                }
            }
        }
        if (currentEntry != null && currentCat != null && isEntryValid(currentEntry)) {
            currentCat.entries.add(currentEntry);
        }
        return categories;
    }

    private String extractField(String line, String fieldRegex) {
        return line.replaceFirst("^-\\s*\\*\\*" + fieldRegex + ":\\*\\*\\s*", "").trim();
    }

    private boolean isEntryValid(ShaderEntry e) {
        return e.name != null && !e.name.isEmpty()
            && e.author != null && !e.author.isEmpty()
            && e.downloadUrl != null && e.downloadUrl.startsWith("http");
    }
}
