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

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MyFiltersActivity extends Activity implements Lang.Listener {

    private static final String TAG = "MyFiltersActivity";

    private static final int MP = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WC = ViewGroup.LayoutParams.WRAP_CONTENT;

    public static final String EXTRA_RESULT_CHANGED = "changed";

    public static final String PREFS_NAME        = "demeter_prefs";
    public static final String PREF_FPS_OVERLAY  = "fps_overlay";
    public static final String PREF_FRAMEGEN_MODE = "framegen_mode";

    private static final int REQ_FPS_POSITION = 201;

    private static final int AVAILABLE_PAGE_SIZE = 20;
    private static final long SEARCH_DEBOUNCE_MS = 120L;

    private Mods mMods;
    private LinearLayout  mLlFilters;
    private TextView      mTvCount;
    private boolean       mChanged = false;
    private boolean       mFpsOverlay = false;
    private Switch        mFpsSwitch = null;
    private TextView      mBtnFpsPosition = null;

    private int           mFgMode = 2;
    private TextView      mBtnFgMode = null;

    private EditText             mSearch;
    private HorizontalScrollView mAuthorChipsScroll;
    private LinearLayout         mAuthorChips;
    private HorizontalScrollView mTypeChipsScroll;
    private LinearLayout         mTypeChips;

    private String      mQuery          = "";
    private String      mSelectedAuthor = null;
    private Mod.Type    mSelectedType   = null;

    private int mAvailableLimit = AVAILABLE_PAGE_SIZE;

    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private final Runnable mRebuildRunnable = new Runnable() {
        @Override public void run() { rebuildList(); }
    };

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

        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        mFpsOverlay = sp.getBoolean(PREF_FPS_OVERLAY, false);
        mFgMode     = sp.getInt(PREF_FRAMEGEN_MODE, 2);

        mMods = new Mods(this);

        Lang.init(this);
        Lang.addListener(this);

        setContentView(rootLayout());
        rebuildAuthorChips();
        rebuildTypeChips();
        rebuildList();

        recheckEnabled();
    }

    @Override protected void onResume() {
        super.onResume();
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean fps = sp.getBoolean(PREF_FPS_OVERLAY, false);
        int fg      = sp.getInt(PREF_FRAMEGEN_MODE, 2);

        if (mFpsOverlay != fps) {
            mFpsOverlay = fps;
            if (mFpsSwitch != null) mFpsSwitch.setChecked(fps);
        } else if (mFpsSwitch != null && mFpsSwitch.isChecked() != mFpsOverlay) {
            mFpsSwitch.setChecked(mFpsOverlay);
        }
        if (mFgMode != fg) {
            mFgMode = fg;
            updateFgModeButton();
        }
        updateFpsBtn();
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

    @Override protected void onDestroy() {
        Lang.removeListener(this);
        super.onDestroy();
        mUiHandler.removeCallbacks(mRebuildRunnable);
    }

    @Override
    public void onLanguageChanged() {
        runOnUiThread(new Runnable() {
				@Override public void run() {
					if (mSearch != null) mSearch.setHint(Lang.get(301));
					rebuildAuthorChips();
					rebuildTypeChips();
					rebuildList();
					updateFgModeButton();
				}
			});
    }

    @Override public void finish() {
        Intent r = new Intent();
        r.putExtra(EXTRA_RESULT_CHANGED, mChanged);
        setResult(mChanged ? RESULT_OK : RESULT_CANCELED, r);
        super.finish();
    }

    private void rebuildLater() {
        mUiHandler.removeCallbacks(mRebuildRunnable);
        mUiHandler.postDelayed(mRebuildRunnable, SEARCH_DEBOUNCE_MS);
    }

    private String tryCompile(Mod module) {
        return GlProgram.testCompile(
            module.getVertexShader(), module.getFragmentShader());
    }

    private void recheckEnabled() {
        final List<Mod> toCheck = new ArrayList<Mod>();
        for (Mod m : mMods.getAll()) {
            if (m.isEnabled()) toCheck.add(m);
        }
        if (toCheck.isEmpty()) return;

        new Thread(new Runnable() {
				@Override public void run() {
					final List<String> failed = new ArrayList<String>();
					for (Mod m : toCheck) {
						String err = tryCompile(m);
						if (err != null) failed.add(m.getName());
					}
					if (failed.isEmpty()) return;
					runOnUiThread(new Runnable() {
							@Override public void run() {
								int n = 0;
								for (String name : failed) {
									Mod m = mMods.byName(name);
									if (m != null && m.isEnabled()) {
										mMods.setEnabled(m, false);
										n++;
									}
								}
								if (n > 0) {
									mChanged = true;
									rebuildList();
									Toast.makeText(MyFiltersActivity.this,
												   Lang.f(321, n),
												   Toast.LENGTH_LONG).show();
								}
							}
						});
				}
			}, "RevalidateShaders").start();
    }

    private void turnOn(final Mod module, final CompoundButton buttonView) {
        buttonView.setEnabled(false);
        Toast.makeText(MyFiltersActivity.this,
                       Lang.f(318, module.getName()),
                       Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
				@Override public void run() {
					final String err = tryCompile(module);
					runOnUiThread(new Runnable() {
							@Override public void run() {
								buttonView.setEnabled(true);
								if (err != null) {
									showShaderErrorDialog(module, err);
									buttonView.setChecked(false);
									return;
								}
								mMods.setEnabled(module, true);
								mChanged = true;
								rebuildList();
								Toast.makeText(MyFiltersActivity.this,
											   Lang.f(319, module.getName()),
											   Toast.LENGTH_SHORT).show();
							}
						});
				}
			}, "ShaderToggleTest").start();
    }

    private void turnOff(final Mod module) {
        mMods.setEnabled(module, false);
        mChanged = true;
        rebuildList();
        Toast.makeText(MyFiltersActivity.this,
                       Lang.f(320, module.getName()),
                       Toast.LENGTH_SHORT).show();
    }

    private View rootLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Skin.BG_ROOT);

        root.addView(topBar());
        root.addView(fpsRow());
        root.addView(searchBar());
        root.addView(authorBar());
        root.addView(typeBar());

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setPadding(Skin.dp(this, 12), 0, Skin.dp(this, 12), Skin.dp(this, 12));

        mLlFilters = new LinearLayout(this);
        mLlFilters.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(mLlFilters, new FrameLayout.LayoutParams(MP, WC));

        root.addView(scroll, Skin.lp(MP, 0, 1f));
        return root;
    }

    private View topBar() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int p = Skin.dp(this, 16);
        row.setPadding(p, p, p, p);

        TextView back = makeIconButton("\u2715");
        back.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { finish(); }
            });
        row.addView(back, Skin.lp(Skin.dp(this, 44), Skin.dp(this, 44)));

        mBtnFgMode = new TextView(this);
        mBtnFgMode.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mBtnFgMode.setTypeface(Skin.medium());
        mBtnFgMode.setGravity(Gravity.CENTER);
        mBtnFgMode.setClickable(true);
        mBtnFgMode.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showFgDialog(); }
            });
        LinearLayout.LayoutParams fgLp = Skin.lp(Skin.dp(this, 48), Skin.dp(this, 40));
        fgLp.leftMargin = Skin.dp(this, 8);
        row.addView(mBtnFgMode, fgLp);
        updateFgModeButton();

        TextView title = Skin.text(this, Lang.get(300), 24, Skin.TEXT_PRIMARY, true);
        LinearLayout.LayoutParams lpTitle = Skin.lp(0, WC, 1f);
        lpTitle.leftMargin = Skin.dp(this, 12);
        row.addView(title, lpTitle);

        mTvCount = Skin.text(this, "0", 14, Skin.ACCENT, true);
        mTvCount.setGravity(Gravity.CENTER);
        mTvCount.setBackground(Skin.roundRectStroke(
                                   Skin.ACCENT_SOFT, Skin.ACCENT_SOFT, this, 20, 0));
        int hpad = Skin.dp(this, 14);
        int vpad = Skin.dp(this, 8);
        mTvCount.setPadding(hpad, vpad, hpad, vpad);
        row.addView(mTvCount);
        return row;
    }

    private void updateFgModeButton() {
        if (mBtnFgMode == null) return;
        String label;
        if (mFgMode == 1)      label = "G1";
        else if (mFgMode == 3) label = "G3";
        else                   label = "G2";
        mBtnFgMode.setText(label);
        mBtnFgMode.setTextColor(0xFFFFFFFF);
        mBtnFgMode.setBackground(Skin.buttonBgSolid(this, Skin.ACCENT, Skin.ACCENT_DIM, 10));
    }

    private void showFgDialog() {
        final CharSequence[] options = new CharSequence[]{
            Lang.get(331), Lang.get(332), Lang.get(333)
        };

        int preselected;
        if (mFgMode == 1)      preselected = 0;
        else if (mFgMode == 3) preselected = 2;
        else                   preselected = 1;

        new AlertDialog.Builder(this)
            .setTitle(Lang.get(330))
            .setSingleChoiceItems(options, preselected,
            new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    int mode = which + 1;
                    d.dismiss();
                    if (mode == 3 && mFgMode != 3) {
                        showG3Warning(mode);
                    } else {
                        setFgMode(mode);
                    }
                }
            })
            .setNegativeButton(Lang.get(18), null)
            .show();
    }

    private void showG3Warning(final int mode) {
        new AlertDialog.Builder(this)
            .setTitle(Lang.get(334))
            .setMessage(Lang.get(335))
            .setPositiveButton(Lang.get(336), new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    setFgMode(mode);
                }
            })
            .setNegativeButton(Lang.get(18), null)
            .show();
    }

    private void setFgMode(int mode) {
        if (mFgMode == mode) return;
        mFgMode = mode;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().putInt(PREF_FRAMEGEN_MODE, mode).commit();
        updateFgModeButton();
        sendFgModeBroadcast(mode);
        mChanged = true;
        Toast.makeText(this, Lang.f(337, mode), Toast.LENGTH_SHORT).show();
    }

    private void sendFgModeBroadcast(int mode) {
        Intent intent = new Intent(CaptureService.ACTION_FRAMEGEN_MODE);
        intent.setPackage(getPackageName());
        intent.putExtra(CaptureService.EXTRA_FRAMEGEN_MODE, mode);
        sendBroadcast(intent);
    }

    private TextView makeIconButton(String glyph) {
        TextView tv = new TextView(this);
        tv.setText(glyph);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tv.setTextColor(Skin.TEXT_PRIMARY);
        tv.setGravity(Gravity.CENTER);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setBackground(Skin.buttonBgStroke(this, Skin.BG_ELEV, Skin.DIVIDER, Skin.DIVIDER, 22, 1f));
        tv.setClickable(true);
        return tv;
    }

    private View searchBar() {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setBackground(Skin.roundRectStroke(Skin.BG_SURFACE, Skin.DIVIDER, this, 12, 1f));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Skin.dp(this, 12);
        inner.setPadding(pad, 0, pad, 0);
        wrap.addView(inner, new FrameLayout.LayoutParams(MP, MP));

        TextView icon = Skin.text(this, "\uD83D\uDD0D", 14, Skin.TEXT_TERTIARY, false);
        inner.addView(icon);

        mSearch = new EditText(this);
        mSearch.setHint(Lang.get(301));
        mSearch.setHintTextColor(Skin.TEXT_TERTIARY);
        mSearch.setTextColor(Skin.TEXT_PRIMARY);
        mSearch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mSearch.setBackgroundColor(0x00000000);
        mSearch.setSingleLine(true);
        mSearch.setPadding(Skin.dp(this, 10), 0, 0, 0);
        inner.addView(mSearch, Skin.lp(0, MP, 1f));

        mSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void afterTextChanged(Editable s) {
                    mQuery = s.toString().trim().toLowerCase();
                    mAvailableLimit = AVAILABLE_PAGE_SIZE;
                    rebuildLater();
                }
            });

        LinearLayout.LayoutParams lp = Skin.lp(MP, Skin.dp(this, 44));
        lp.leftMargin = lp.rightMargin = Skin.dp(this, 14);
        lp.topMargin = Skin.dp(this, 4);
        lp.bottomMargin = Skin.dp(this, 6);
        wrap.setLayoutParams(lp);
        return wrap;
    }

    private View authorBar() {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.HORIZONTAL);
        int side = Skin.dp(this, 14);
        holder.setPadding(side, Skin.dp(this, 2), side, 0);

        mAuthorChipsScroll = new HorizontalScrollView(this);
        mAuthorChipsScroll.setHorizontalScrollBarEnabled(false);
        mAuthorChips = new LinearLayout(this);
        mAuthorChips.setOrientation(LinearLayout.HORIZONTAL);
        mAuthorChipsScroll.addView(mAuthorChips, new FrameLayout.LayoutParams(WC, WC));

        LinearLayout.LayoutParams scrollLp = Skin.lp(0, Skin.dp(this, 34), 1f);
        holder.addView(mAuthorChipsScroll, scrollLp);
        return holder;
    }

    private void rebuildAuthorChips() {
        if (mAuthorChips == null) return;
        mAuthorChips.removeAllViews();

        TreeSet<String> authors = new TreeSet<String>();
        for (Mod m : mMods.getAll()) {
            String a = m.getAuthor();
            if (a != null && !a.isEmpty()) authors.add(a);
        }

        if (mSelectedAuthor != null && !authors.contains(mSelectedAuthor)) mSelectedAuthor = null;

        mAuthorChips.addView(makeFilterChip(Lang.get(302), mSelectedAuthor == null,
								 new Runnable() {
									 @Override public void run() {
										 mSelectedAuthor = null;
										 mAvailableLimit = AVAILABLE_PAGE_SIZE;
										 rebuildAuthorChips();
										 rebuildList();
									 }
								 }));

        for (final String a : authors) {
            boolean sel = a.equals(mSelectedAuthor);
            mAuthorChips.addView(makeFilterChip(a, sel, new Runnable() {
										 @Override public void run() {
											 mSelectedAuthor = a;
											 mAvailableLimit = AVAILABLE_PAGE_SIZE;
											 rebuildAuthorChips();
											 rebuildList();
										 }
									 }));
        }
    }

    private View typeBar() {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.HORIZONTAL);
        int side = Skin.dp(this, 14);
        holder.setPadding(side, Skin.dp(this, 2), side, Skin.dp(this, 4));

        mTypeChipsScroll = new HorizontalScrollView(this);
        mTypeChipsScroll.setHorizontalScrollBarEnabled(false);
        mTypeChips = new LinearLayout(this);
        mTypeChips.setOrientation(LinearLayout.HORIZONTAL);
        mTypeChipsScroll.addView(mTypeChips, new FrameLayout.LayoutParams(WC, WC));

        LinearLayout.LayoutParams scrollLp = Skin.lp(0, Skin.dp(this, 34), 1f);
        holder.addView(mTypeChipsScroll, scrollLp);
        return holder;
    }

    private void rebuildTypeChips() {
        if (mTypeChips == null) return;
        mTypeChips.removeAllViews();

        mTypeChips.addView(makeFilterChip(Lang.get(302), mSelectedType == null, new Runnable() {
								   @Override public void run() {
									   mSelectedType = null;
									   mAvailableLimit = AVAILABLE_PAGE_SIZE;
									   rebuildTypeChips();
									   rebuildList();
								   }
							   }));

        mTypeChips.addView(makeFilterChip(Lang.get(303), mSelectedType == Mod.Type.MODIFIER,
							   new Runnable() {
								   @Override public void run() {
									   mSelectedType = Mod.Type.MODIFIER;
									   mAvailableLimit = AVAILABLE_PAGE_SIZE;
									   rebuildTypeChips();
									   rebuildList();
								   }
							   }));

        mTypeChips.addView(makeFilterChip(Lang.get(304), mSelectedType == Mod.Type.RENDERER,
							   new Runnable() {
								   @Override public void run() {
									   mSelectedType = Mod.Type.RENDERER;
									   mAvailableLimit = AVAILABLE_PAGE_SIZE;
									   rebuildTypeChips();
									   rebuildList();
								   }
							   }));

        mTypeChips.addView(makeFilterChip(Lang.get(305), mSelectedType == Mod.Type.FRAMEGEN,
							   new Runnable() {
								   @Override public void run() {
									   mSelectedType = Mod.Type.FRAMEGEN;
									   mAvailableLimit = AVAILABLE_PAGE_SIZE;
									   rebuildTypeChips();
									   rebuildList();
								   }
							   }));

        mTypeChips.addView(makeFilterChip(Lang.get(306), mSelectedType == Mod.Type.FRAMEGEN_G3,
							   new Runnable() {
								   @Override public void run() {
									   mSelectedType = Mod.Type.FRAMEGEN_G3;
									   mAvailableLimit = AVAILABLE_PAGE_SIZE;
									   rebuildTypeChips();
									   rebuildList();
								   }
							   }));
    }

    private TextView makeFilterChip(String label, boolean selected, final Runnable onSelect) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        chip.setTypeface(Skin.medium());
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        int hp = Skin.dp(this, 14);
        int vp = Skin.dp(this, 6);
        chip.setPadding(hp, vp, hp, vp);
        if (selected) {
            chip.setTextColor(0xFFFFFFFF);
            chip.setBackground(Skin.roundRect(Skin.ACCENT, this, 20));
        } else {
            chip.setTextColor(Skin.TEXT_SECOND);
            chip.setBackground(Skin.roundRectStroke(Skin.BG_SURFACE, Skin.DIVIDER, this, 20, 1f));
        }
        chip.setClickable(true);
        chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { onSelect.run(); }
            });
        LinearLayout.LayoutParams lp = Skin.lp(WC, WC);
        lp.rightMargin = Skin.dp(this, 6);
        chip.setLayoutParams(lp);
        return chip;
    }

    private boolean matchesFilter(Mod m) {
        if (mSelectedAuthor != null) {
            if (m.getAuthor() == null || !m.getAuthor().equals(mSelectedAuthor)) return false;
        }
        if (mSelectedType != null) {
            if (m.getType() != mSelectedType) return false;
        }
        if (!mQuery.isEmpty()) {
            boolean hit = false;
            if (m.getName() != null && m.getName().toLowerCase().contains(mQuery)) hit = true;
            if (!hit && m.getAuthor() != null
                && m.getAuthor().toLowerCase().contains(mQuery)) hit = true;
            if (!hit) return false;
        }
        return true;
    }

    private boolean isFrameGen(Mod.Type t) {
        return t == Mod.Type.FRAMEGEN || t == Mod.Type.FRAMEGEN_G3;
    }

    private void rebuildList() {
        mLlFilters.removeAllViews();

        final List<Mod> active = mMods.getEnabledChain();
        final List<Mod> all    = mMods.getAll();

        List<Mod> frameGenMods = new ArrayList<Mod>();
        for (Mod m : all) if (isFrameGen(m.getType())) frameGenMods.add(m);

        List<Mod> available = new ArrayList<Mod>();
        for (Mod m : all) {
            if (isFrameGen(m.getType())) continue;
            boolean isActive = false;
            for (Mod a : active) {
                if (a.getName().equals(m.getName())) { isActive = true; break; }
            }
            if (!isActive) available.add(m);
        }

        List<Mod> activeVisible    = new ArrayList<Mod>();
        List<Mod> availableVisible = new ArrayList<Mod>();
        List<Mod> fgVisible        = new ArrayList<Mod>();

        for (int i = 0; i < active.size(); i++) {
            Mod m = active.get(i);
            if (matchesFilter(m)) activeVisible.add(m);
        }
        for (Mod m : available) if (matchesFilter(m)) availableVisible.add(m);
        for (Mod m : frameGenMods) if (matchesFilter(m)) fgVisible.add(m);

        boolean anyFilter = !mQuery.isEmpty() || mSelectedAuthor != null || mSelectedType != null;

        if (active.isEmpty() && available.isEmpty() && frameGenMods.isEmpty()) {
            TextView tv = Skin.text(this,
									Lang.get(307),
									14, Skin.TEXT_TERTIARY, false);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, Skin.dp(this, 60), 0, 0);
            mLlFilters.addView(tv);
            mTvCount.setText("0");
            return;
        }

        if (anyFilter && activeVisible.isEmpty() && availableVisible.isEmpty()
            && fgVisible.isEmpty()) {
            TextView tv = Skin.text(this, Lang.get(308),
									14, Skin.TEXT_TERTIARY, false);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, Skin.dp(this, 60), 0, 0);
            mLlFilters.addView(tv);
            mTvCount.setText(String.valueOf(active.size()));
            return;
        }

        if (!fgVisible.isEmpty()) {
            mLlFilters.addView(sectionHeader(Lang.f(309, frameGenMods.size())));
            for (Mod m : fgVisible) mLlFilters.addView(frameGenRow(m));
        }

        if (!activeVisible.isEmpty()) {
            mLlFilters.addView(sectionHeader(Lang.f(310, active.size())));
            for (int i = 0; i < active.size(); i++) {
                Mod m = active.get(i);
                if (!matchesFilter(m)) continue;
                mLlFilters.addView(filterRow(m, i, active.size(), true));
            }
        }

        if (!availableVisible.isEmpty()) {
            int total  = availableVisible.size();
            int toShow = Math.min(total, mAvailableLimit);
            mLlFilters.addView(sectionHeader(Lang.f(311, available.size())));
            for (int i = 0; i < toShow; i++)
                mLlFilters.addView(filterRow(availableVisible.get(i), -1, 0, false));

            if (toShow < total) {
                final int remaining = total - toShow;
                TextView btn = Skin.text(this, Lang.f(312, remaining),
										 13, Skin.ACCENT, true);
                btn.setGravity(Gravity.CENTER);
                btn.setBackground(Skin.roundRectStroke(Skin.BG_ELEV, Skin.ACCENT_SOFT, this, 10, 1f));
                int hp = Skin.dp(this, 16);
                int vp = Skin.dp(this, 12);
                btn.setPadding(hp, vp, hp, vp);
                btn.setClickable(true);
                btn.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            mAvailableLimit += AVAILABLE_PAGE_SIZE;
                            rebuildList();
                        }
                    });
                LinearLayout.LayoutParams lp = Skin.lp(MP, WC);
                lp.topMargin = Skin.dp(this, 10);
                mLlFilters.addView(btn, lp);
            }
        }

        mTvCount.setText(String.valueOf(active.size()));
    }

    private TextView sectionHeader(String s) {
        TextView tv = Skin.text(this, s, 11, Skin.TEXT_TERTIARY, true);
        tv.setLetterSpacing(0.15f);
        tv.setPadding(Skin.dp(this, 8), Skin.dp(this, 18), Skin.dp(this, 8), Skin.dp(this, 8));
        return tv;
    }

    private View filterRow(final Mod module, final int position,
						   final int activeCount, final boolean isActive) {
        FrameLayout outer = new FrameLayout(this);
        LinearLayout.LayoutParams outerLp = Skin.lp(MP, WC);
        outerLp.topMargin    = Skin.dp(this, 4);
        outerLp.bottomMargin = Skin.dp(this, 4);
        outer.setLayoutParams(outerLp);
        outer.setBackground(Skin.roundRect(Skin.BG_ELEV, this, 14));

        if (isActive) {
            View accentBar = new View(this);
            accentBar.setBackground(accentStripe());
            FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                Skin.dp(this, 3), FrameLayout.LayoutParams.MATCH_PARENT);
            outer.addView(accentBar, barLp);
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Skin.dp(this, 14);
        int leftPad = isActive ? Skin.dp(this, 17) : pad;
        row.setPadding(leftPad, pad, pad, pad);
        outer.addView(row, new FrameLayout.LayoutParams(MP, WC));

        if (isActive) {
            TextView badge = Skin.text(this, String.valueOf(position + 1), 12, 0xFFFFFFFF, true);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(Skin.roundRect(Skin.ACCENT, this, 6));
            row.addView(badge, Skin.lp(Skin.dp(this, 26), Skin.dp(this, 26)));
        }

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colLp = Skin.lp(0, WC, 1f);
        colLp.leftMargin  = Skin.dp(this, isActive ? 12 : 0);
        colLp.rightMargin = Skin.dp(this, 8);
        row.addView(col, colLp);

        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = Skin.text(this, module.getName(), 15, Skin.TEXT_PRIMARY, true);
        nameRow.addView(name, Skin.lp(WC, WC));

        if (module.getType() != Mod.Type.MODIFIER) {
            TextView typeChip = typeBadge(module.getType());
            LinearLayout.LayoutParams chipLp = Skin.lp(WC, WC);
            chipLp.leftMargin = Skin.dp(this, 8);
            nameRow.addView(typeChip, chipLp);
        }
        col.addView(nameRow);

        String subtitle = (module.getVersion() != null && !module.getVersion().isEmpty())
            ? Lang.f(314, module.getAuthor(), module.getVersion())
            : Lang.f(313, module.getAuthor());
        TextView author = Skin.text(this, subtitle, 12, Skin.TEXT_TERTIARY, false);
        LinearLayout.LayoutParams authLp = Skin.lp(MP, WC);
        authLp.topMargin = Skin.dp(this, 2);
        col.addView(author, authLp);

        if (!isActive && !module.getParamDefs().isEmpty()) {
            TextView hint = Skin.text(this,
									  Lang.f(module.getParamDefs().size() == 1 ? 315 : 316,
											 module.getParamDefs().size()),
									  11, Skin.ACCENT, false);
            LinearLayout.LayoutParams hintLp = Skin.lp(MP, WC);
            hintLp.topMargin = Skin.dp(this, 3);
            col.addView(hint, hintLp);
        }

        if (isActive) {
            if (position > 0) {
                TextView up = makeIconButton("\u2191");
                up.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                up.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            moveInChain(position, position - 1);
                            rebuildList();
                        }
                    });
                LinearLayout.LayoutParams lpU = Skin.lp(Skin.dp(this, 36), Skin.dp(this, 36));
                lpU.rightMargin = Skin.dp(this, 4);
                row.addView(up, lpU);
            }
            if (position < activeCount - 1) {
                TextView down = makeIconButton("\u2193");
                down.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                down.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            moveInChain(position, position + 1);
                            rebuildList();
                        }
                    });
                LinearLayout.LayoutParams lpD = Skin.lp(Skin.dp(this, 36), Skin.dp(this, 36));
                lpD.rightMargin = Skin.dp(this, 4);
                row.addView(down, lpD);
            }
        }

        if (!module.getParamDefs().isEmpty()) {
            boolean hasCustomParams = hasModifiedParams(module);
            FrameLayout gearWrapper = new FrameLayout(this);
            LinearLayout.LayoutParams gwLp = Skin.lp(Skin.dp(this, 40), Skin.dp(this, 40));
            gwLp.rightMargin = Skin.dp(this, 4);

            TextView gear = makeIconButton("\u2699");
            gear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            gear.setTextColor(hasCustomParams ? Skin.ACCENT : Skin.TEXT_SECOND);
            gear.setBackground(Skin.buttonBgStroke(
								   this,
								   hasCustomParams ? Skin.ACCENT_SOFT : Skin.BG_ELEV,
								   hasCustomParams ? Skin.ACCENT_SOFT : Skin.DIVIDER,
								   hasCustomParams ? Skin.ACCENT      : Skin.DIVIDER,
								   22, 1f));
            gear.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { showParamsDialog(module); }
                });
            gearWrapper.addView(gear, new FrameLayout.LayoutParams(MP, MP));

            if (hasCustomParams) {
                View dot = new View(this);
                dot.setBackground(Skin.circle(Skin.ACCENT));
                FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(
                    Skin.dp(this, 8), Skin.dp(this, 8));
                dotLp.gravity  = Gravity.TOP | Gravity.END;
                dotLp.topMargin   = Skin.dp(this, 2);
                dotLp.rightMargin = Skin.dp(this, 2);
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
                    if (isChecked) {
                        turnOn(module, buttonView);
                    } else {
                        turnOff(module);
                    }
                }
            });
        row.addView(sw);
        return outer;
    }

    private View frameGenRow(final Mod module) {
        final boolean isActiveFg = (mMods.getActiveFgMod() == module);

        FrameLayout outer = new FrameLayout(this);
        LinearLayout.LayoutParams outerLp = Skin.lp(MP, WC);
        outerLp.topMargin    = Skin.dp(this, 4);
        outerLp.bottomMargin = Skin.dp(this, 4);
        outer.setLayoutParams(outerLp);
        outer.setBackground(Skin.roundRect(Skin.BG_ELEV, this, 14));

        if (isActiveFg) {
            View accentBar = new View(this);
            accentBar.setBackground(accentStripe());
            FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                Skin.dp(this, 3), FrameLayout.LayoutParams.MATCH_PARENT);
            outer.addView(accentBar, barLp);
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Skin.dp(this, 14);
        int leftPad = isActiveFg ? Skin.dp(this, 17) : pad;
        row.setPadding(leftPad, pad, pad, pad);
        outer.addView(row, new FrameLayout.LayoutParams(MP, WC));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colLp = Skin.lp(0, WC, 1f);
        colLp.rightMargin = Skin.dp(this, 8);
        row.addView(col, colLp);

        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = Skin.text(this, module.getName(), 15, Skin.TEXT_PRIMARY, true);
        nameRow.addView(name, Skin.lp(WC, WC));

        TextView typeChip = typeBadge(module.getType());
        LinearLayout.LayoutParams chipLp = Skin.lp(WC, WC);
        chipLp.leftMargin = Skin.dp(this, 8);
        nameRow.addView(typeChip, chipLp);

        if (isActiveFg) {
            TextView chip = Skin.text(this, Lang.get(317), 9, 0xFF4ADE80, true);
            chip.setLetterSpacing(0.10f);
            chip.setBackground(Skin.roundRect(0xFF1A3A2A, this, 4));
            int hp = Skin.dp(this, 6);
            int vp = Skin.dp(this, 3);
            chip.setPadding(hp, vp, hp, vp);
            LinearLayout.LayoutParams chipLp2 = Skin.lp(WC, WC);
            chipLp2.leftMargin = Skin.dp(this, 6);
            nameRow.addView(chip, chipLp2);
        }
        col.addView(nameRow);

        String subtitle = (module.getVersion() != null && !module.getVersion().isEmpty())
            ? Lang.f(314, module.getAuthor(), module.getVersion())
            : Lang.f(313, module.getAuthor());
        TextView author = Skin.text(this, subtitle, 12, Skin.TEXT_TERTIARY, false);
        LinearLayout.LayoutParams authLp = Skin.lp(MP, WC);
        authLp.topMargin = Skin.dp(this, 2);
        col.addView(author, authLp);

        if (!module.getParamDefs().isEmpty()) {
            boolean hasCustomParams = hasModifiedParams(module);
            FrameLayout gearWrapper = new FrameLayout(this);
            LinearLayout.LayoutParams gwLp = Skin.lp(Skin.dp(this, 40), Skin.dp(this, 40));
            gwLp.rightMargin = Skin.dp(this, 4);

            TextView gear = makeIconButton("\u2699");
            gear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            gear.setTextColor(hasCustomParams ? Skin.ACCENT : Skin.TEXT_SECOND);
            gear.setBackground(Skin.buttonBgStroke(
								   this,
								   hasCustomParams ? Skin.ACCENT_SOFT : Skin.BG_ELEV,
								   hasCustomParams ? Skin.ACCENT_SOFT : Skin.DIVIDER,
								   hasCustomParams ? Skin.ACCENT      : Skin.DIVIDER,
								   22, 1f));
            gear.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { showParamsDialog(module); }
                });
            gearWrapper.addView(gear, new FrameLayout.LayoutParams(MP, MP));

            if (hasCustomParams) {
                View dot = new View(this);
                dot.setBackground(Skin.circle(Skin.ACCENT));
                FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(
                    Skin.dp(this, 8), Skin.dp(this, 8));
                dotLp.gravity  = Gravity.TOP | Gravity.END;
                dotLp.topMargin   = Skin.dp(this, 2);
                dotLp.rightMargin = Skin.dp(this, 2);
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
                    if (isChecked) {
                        turnOn(module, buttonView);
                    } else {
                        turnOff(module);
                    }
                }
            });
        row.addView(sw);
        return outer;
    }

    private Drawable accentStripe() {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(Skin.ACCENT);
        float r = Skin.dp(this, 3);
        d.setCornerRadii(new float[]{0, 0, r, r, r, r, 0, 0});
        return d;
    }

    private TextView typeBadge(Mod.Type type) {
        int chipColor;
        String label;
        int textColor = Skin.ACCENT;
        switch (type) {
            case FRAMEGEN:
                chipColor = 0xFF1A3A2A;
                label = "FRAMEGEN";
                textColor = 0xFF4ADE80;
                break;
            case FRAMEGEN_G3:
                chipColor = 0xFF1A2A3A;
                label = "FG-G3";
                textColor = 0xFF60A5FA;
                break;
            case RENDERER:
                chipColor = 0xFF2A1A3A;
                label = "RENDERER";
                break;
            default:
                chipColor = Skin.ACCENT_SOFT;
                label = "MOD";
        }
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        chip.setTextColor(textColor);
        chip.setTypeface(Skin.medium());
        chip.setLetterSpacing(0.12f);
        chip.setBackground(Skin.roundRect(chipColor, this, 4));
        int hp = Skin.dp(this, 6);
        int vp = Skin.dp(this, 3);
        chip.setPadding(hp, vp, hp, vp);
        return chip;
    }

    private boolean hasModifiedParams(Mod module) {
        Map<String, Float>           params = module.getParams();
        Map<String, Mod.Param> defs   = module.getParamDefs();
        for (Map.Entry<String, Mod.Param> e : defs.entrySet()) {
            Float current = params.get(e.getKey());
            if (current != null && Math.abs(current - e.getValue().defaultValue) > 0.0001f) {
                return true;
            }
        }
        return false;
    }

    private void moveInChain(int from, int to) {
        List<Mod> active = mMods.getEnabledChain();
        if (from < 0 || from >= active.size()) return;
        if (to   < 0 || to   >= active.size()) return;
        List<String> names = new ArrayList<String>();
        for (Mod m : active) names.add(m.getName());
        String moved = names.remove(from);
        names.add(to, moved);
        mMods.setChainOrder(names);
        mChanged = true;
    }

    private void showParamsDialog(final Mod module) {
        if (module.getParamDefs().isEmpty()) return;

        final Map<String, Mod.Param> defs    = module.getParamDefs();
        final Map<String, Float>           current = module.getParams();

        ScrollView scroll = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = Skin.dp(this, 20);
        container.setPadding(pad, pad, pad, pad);
        scroll.addView(container, new FrameLayout.LayoutParams(MP, WC));

        for (final Map.Entry<String, Mod.Param> entry : defs.entrySet()) {
            final String uniformName = entry.getKey();
            final Mod.Param def = entry.getValue();
            float initialValue = current.containsKey(uniformName)
                ? current.get(uniformName) : def.defaultValue;
            final boolean isModified = Math.abs(initialValue - def.defaultValue) > 0.0001f;

            LinearLayout labelRow = new LinearLayout(this);
            labelRow.setOrientation(LinearLayout.HORIZONTAL);
            labelRow.setGravity(Gravity.CENTER_VERTICAL);
            labelRow.setPadding(0, Skin.dp(this, 8), 0, Skin.dp(this, 4));

            final TextView label = Skin.text(this, "", 14,
											 isModified ? Skin.ACCENT : Skin.TEXT_PRIMARY, false);
            final float range = def.max - def.min;
            final boolean showDecimal = range <= 10f;
            final int maxProgress = (range <= 100f)
                ? 1000
                : Math.max(1, (int)Math.round(range));

            updateParamLabel(label, def.label, initialValue, showDecimal, isModified);
            labelRow.addView(label, Skin.lp(0, WC, 1f));

            final TextView resetBtn = Skin.text(this, "\u21BA", 16, Skin.TEXT_TERTIARY, false);
            resetBtn.setGravity(Gravity.CENTER);
            resetBtn.setPadding(Skin.dp(this, 8), 0, 0, 0);
            resetBtn.setVisibility(isModified ? View.VISIBLE : View.INVISIBLE);
            labelRow.addView(resetBtn);
            container.addView(labelRow);

            final SeekBar seekBar = new SeekBar(this);
            seekBar.setMax(maxProgress);
            seekBar.setProgress(Math.round((initialValue - def.min) / range * maxProgress));
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                        float value = def.min + (progress / (float) maxProgress) * range;
                        boolean mod = Math.abs(value - def.defaultValue) > 0.0001f;
                        label.setTextColor(mod ? Skin.ACCENT : Skin.TEXT_PRIMARY);
                        updateParamLabel(label, def.label, value, showDecimal, mod);
                        resetBtn.setVisibility(mod ? View.VISIBLE : View.INVISIBLE);
                        mMods.setParamValue(module, uniformName, value);
                        mChanged = true;
                    }
                    @Override public void onStartTrackingTouch(SeekBar sb) {}
                    @Override public void onStopTrackingTouch(SeekBar sb) {}
                });
            container.addView(seekBar);

            final SeekBar seekBarRef = seekBar;
            final Mod.Param defRef = def;
            final String uniformRef = uniformName;
            resetBtn.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        mMods.setParamValue(module, uniformRef, defRef.defaultValue);
                        int prog = Math.round((defRef.defaultValue - defRef.min) / range * maxProgress);
                        seekBarRef.setProgress(prog);
                        label.setTextColor(Skin.TEXT_PRIMARY);
                        updateParamLabel(label, defRef.label, defRef.defaultValue, showDecimal, false);
                        resetBtn.setVisibility(View.INVISIBLE);
                        mChanged = true;
                    }
                });

            LinearLayout minMax = new LinearLayout(this);
            minMax.setOrientation(LinearLayout.HORIZONTAL);
            TextView tvMin = Skin.text(this,
									   showDecimal ? String.format("%.2f", def.min) : String.valueOf((int) def.min),
									   11, Skin.TEXT_TERTIARY, false);
            String defaultLabel = showDecimal
                ? Lang.f(338, String.format("%.2f", def.defaultValue))
                : Lang.f(338, String.valueOf((int) def.defaultValue));
            TextView tvDefault = Skin.text(this, defaultLabel, 10, Skin.TEXT_TERTIARY, false);
            tvDefault.setGravity(Gravity.CENTER);
            TextView tvMax = Skin.text(this,
									   showDecimal ? String.format("%.2f", def.max) : String.valueOf((int) def.max),
									   11, Skin.TEXT_TERTIARY, false);
            minMax.addView(tvMin, Skin.lp(0, WC, 1f));
            minMax.addView(tvDefault, Skin.lp(0, WC, 1f));
            minMax.addView(tvMax);
            LinearLayout.LayoutParams mmLp = Skin.lp(MP, WC);
            mmLp.bottomMargin = Skin.dp(this, 14);
            container.addView(minMax, mmLp);
        }

        new AlertDialog.Builder(this)
            .setTitle(Lang.f(328, module.getName()))
            .setView(scroll)
            .setPositiveButton(Lang.get(323), null)
            .setNeutralButton(Lang.get(327), new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    for (Map.Entry<String, Mod.Param> e : defs.entrySet()) {
                        mMods.setParamValue(module, e.getKey(), e.getValue().defaultValue);
                    }
                    mChanged = true;
                    Toast.makeText(MyFiltersActivity.this,
								   Lang.f(326, module.getName()),
								   Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }

    private void showShaderErrorDialog(final Mod module, String err) {
        TextView tv = new TextView(this);
        tv.setText(err);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTextColor(Skin.TEXT_PRIMARY);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        int p = Skin.dp(this, 18);
        tv.setPadding(p, p, p, p);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(tv, new FrameLayout.LayoutParams(MP, WC));

        new AlertDialog.Builder(this)
            .setTitle(Lang.f(322, module.getName()))
            .setView(scroll)
            .setPositiveButton(Lang.get(323), null)
            .setNeutralButton(Lang.get(324), new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    mMods.uninstall(module);
                    mChanged = true;
                    rebuildList();
                    Toast.makeText(MyFiltersActivity.this,
                                   Lang.f(325, module.getName()),
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

    private View fpsRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int side = Skin.dp(this, 16);
        int vert = Skin.dp(this, 8);
        row.setPadding(side, vert, side, vert);
        row.setBackground(Skin.roundRect(Skin.BG_SURFACE, this, 12));

        LinearLayout.LayoutParams rowLp = Skin.lp(MP, WC);
        rowLp.leftMargin = rowLp.rightMargin = Skin.dp(this, 14);
        rowLp.bottomMargin = Skin.dp(this, 6);
        row.setLayoutParams(rowLp);

        TextView title = Skin.text(this, Lang.get(329), 14, Skin.TEXT_PRIMARY, true);
        row.addView(title, Skin.lp(0, WC, 1f));

        mBtnFpsPosition = Skin.text(this, "\uD83D\uDCCD", 18, Skin.TEXT_PRIMARY, false);
        mBtnFpsPosition.setGravity(Gravity.CENTER);
        mBtnFpsPosition.setBackground(Skin.buttonBgStroke(
										  this, Skin.BG_ELEV, Skin.ACCENT_SOFT, Skin.DIVIDER, 22, 1f));
        mBtnFpsPosition.setClickable(true);
        mBtnFpsPosition.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    startActivityForResult(
                        new Intent(MyFiltersActivity.this, MoveFpsActivity.class),
                        REQ_FPS_POSITION);
                }
            });
        LinearLayout.LayoutParams btnLp = Skin.lp(Skin.dp(this, 40), Skin.dp(this, 40));
        btnLp.rightMargin = Skin.dp(this, 8);
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
                        .edit().putBoolean(PREF_FPS_OVERLAY, isChecked).commit();
                    sendFpsOverlayBroadcast(isChecked);
                    mChanged = true;
                    updateFpsBtn();
                }
            });
        mFpsSwitch = sw;
        row.addView(sw);

        updateFpsBtn();
        return row;
    }

    private void updateFpsBtn() {
        if (mBtnFpsPosition == null) return;
        mBtnFpsPosition.setEnabled(mFpsOverlay);
        mBtnFpsPosition.setTextColor(mFpsOverlay ? Skin.TEXT_PRIMARY : Skin.TEXT_TERTIARY);
    }

    private void sendFpsOverlayBroadcast(boolean enabled) {
        Intent intent = new Intent(CaptureService.ACTION_FPS_OVERLAY);
        intent.setPackage(getPackageName());
        intent.putExtra(CaptureService.EXTRA_FPS_ENABLED, enabled);
        sendBroadcast(intent);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FPS_POSITION) {
            if (resultCode == RESULT_OK) {
                sendFpsOverlayBroadcast(mFpsOverlay);
                mChanged = true;
            }
        }
    }
}
