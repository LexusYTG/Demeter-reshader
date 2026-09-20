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
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Paleta y helpers de estilo.
 *
 * Los colores viven como campos estáticos NO finales. Esto permite que
 * {@link Themes} los actualice en caliente vía {@link #refreshFromTheme()},
 * sin que ningún caller tenga que cambiar (todos siguen usando
 * Skin.ACCENT, Skin.BG_ROOT, etc.).
 *
 * Los DEFAULT_* son los valores originales hardcodeados. Se usan como
 * fallback si el tema activo no define ese slot.
 */
public final class Skin {

    // ── Valores por defecto (fallback) ─────────────────────────────────────
    public static final int DEFAULT_BG_ROOT       = 0xFF0F1115;
    public static final int DEFAULT_BG_SURFACE    = 0xFF171A21;
    public static final int DEFAULT_BG_ELEV       = 0xFF1E222B;
    public static final int DEFAULT_DIVIDER       = 0xFF252A33;
    public static final int DEFAULT_ACCENT        = 0xFF7C5CFF;
    public static final int DEFAULT_ACCENT_DIM    = 0xFF5A42C9;
    public static final int DEFAULT_ACCENT_SOFT   = 0xFF2A2547;
    public static final int DEFAULT_TEXT_PRIMARY  = 0xFFF0F2F5;
    public static final int DEFAULT_TEXT_SECOND   = 0xFF9AA0AB;
    public static final int DEFAULT_TEXT_TERTIARY = 0xFF6B7280;
    public static final int DEFAULT_SUCCESS       = 0xFF4ADE80;
    public static final int DEFAULT_DANGER        = 0xFFF87171;

    // ── Colores activos (mutables, actualizados por Themes) ────────────────
    public static int BG_ROOT       = DEFAULT_BG_ROOT;
    public static int BG_SURFACE    = DEFAULT_BG_SURFACE;
    public static int BG_ELEV       = DEFAULT_BG_ELEV;
    public static int DIVIDER       = DEFAULT_DIVIDER;
    public static int ACCENT        = DEFAULT_ACCENT;
    public static int ACCENT_DIM    = DEFAULT_ACCENT_DIM;
    public static int ACCENT_SOFT   = DEFAULT_ACCENT_SOFT;
    public static int TEXT_PRIMARY  = DEFAULT_TEXT_PRIMARY;
    public static int TEXT_SECOND   = DEFAULT_TEXT_SECOND;
    public static int TEXT_TERTIARY = DEFAULT_TEXT_TERTIARY;
    public static int SUCCESS       = DEFAULT_SUCCESS;
    public static int DANGER        = DEFAULT_DANGER;

    private Skin() {}

    /**
     * Relee los colores del tema activo y actualiza los campos estáticos.
     * Llamado por {@link Themes} al inicializar y al aplicar un tema nuevo.
     */
    static void refreshFromTheme() {
        BG_ROOT       = Themes.getColor(Themes.COLOR_BG_ROOT,       DEFAULT_BG_ROOT);
        BG_SURFACE    = Themes.getColor(Themes.COLOR_BG_SURFACE,    DEFAULT_BG_SURFACE);
        BG_ELEV       = Themes.getColor(Themes.COLOR_BG_ELEV,       DEFAULT_BG_ELEV);
        DIVIDER       = Themes.getColor(Themes.COLOR_DIVIDER,       DEFAULT_DIVIDER);
        ACCENT        = Themes.getColor(Themes.COLOR_ACCENT,        DEFAULT_ACCENT);
        ACCENT_DIM    = Themes.getColor(Themes.COLOR_ACCENT_DIM,    DEFAULT_ACCENT_DIM);
        ACCENT_SOFT   = Themes.getColor(Themes.COLOR_ACCENT_SOFT,   DEFAULT_ACCENT_SOFT);
        TEXT_PRIMARY  = Themes.getColor(Themes.COLOR_TEXT_PRIMARY,  DEFAULT_TEXT_PRIMARY);
        TEXT_SECOND   = Themes.getColor(Themes.COLOR_TEXT_SECOND,   DEFAULT_TEXT_SECOND);
        TEXT_TERTIARY = Themes.getColor(Themes.COLOR_TEXT_TERTIARY, DEFAULT_TEXT_TERTIARY);
        SUCCESS       = Themes.getColor(Themes.COLOR_SUCCESS,       DEFAULT_SUCCESS);
        DANGER        = Themes.getColor(Themes.COLOR_DANGER,        DEFAULT_DANGER);
    }

    // ── Helpers de estilo (sin cambios) ────────────────────────────────────

    public static Typeface medium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    public static int dp(Context c, float v) {
        return (int)(v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static int sp(Context c, float v) {
        return (int)(v * c.getResources().getDisplayMetrics().scaledDensity + 0.5f);
    }

    public static float spF(Context c, float v) { return v; }

    public static GradientDrawable roundRect(int color, Context c, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable roundRectStroke(int fill, int stroke, Context c,
												   float radiusDp, float strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        d.setStroke(dp(c, strokeDp), stroke);
        return d;
    }

    public static GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    public static StateListDrawable buttonBgSolid(Context c, int fill, int pressedFill,
                                                  float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed},  roundRect(pressedFill, c, radiusDp));
        s.addState(new int[]{-android.R.attr.state_enabled}, roundRect(BG_ELEV, c, radiusDp));
        s.addState(new int[]{},                              roundRect(fill, c, radiusDp));
        return s;
    }

    public static StateListDrawable buttonBgStroke(Context c, int fill, int pressedFill,
                                                   int stroke, float radiusDp,
                                                   float strokeDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed},
                   roundRectStroke(pressedFill, stroke, c, radiusDp, strokeDp));
        s.addState(new int[]{-android.R.attr.state_enabled},
                   roundRectStroke(BG_SURFACE, DIVIDER, c, radiusDp, strokeDp));
        s.addState(new int[]{},
                   roundRectStroke(fill, stroke, c, radiusDp, strokeDp));
        return s;
    }

    public static TextView text(Context c, String s, float sizeSp, int color, boolean medium) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        tv.setTextColor(color);
        if (medium) tv.setTypeface(medium());
        return tv;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    public static int darker(int color, float amount) {
        int a = Color.alpha(color);
        int r = Math.max(0, (int)(Color.red(color)   * (1f - amount)));
        int g = Math.max(0, (int)(Color.green(color) * (1f - amount)));
        int b = Math.max(0, (int)(Color.blue(color)  * (1f - amount)));
        return Color.argb(a, r, g, b);
    }
}
