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

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {

    public static final int BG_ROOT       = 0xFF0F1115;
    public static final int BG_SURFACE    = 0xFF171A21;
    public static final int BG_ELEV       = 0xFF1E222B;
    public static final int DIVIDER       = 0xFF252A33;
    public static final int ACCENT        = 0xFF7C5CFF;
    public static final int ACCENT_DIM    = 0xFF5A42C9;
    public static final int ACCENT_SOFT   = 0xFF2A2547;
    public static final int TEXT_PRIMARY  = 0xFFF0F2F5;
    public static final int TEXT_SECOND   = 0xFF9AA0AB;
    public static final int TEXT_TERTIARY = 0xFF6B7280;
    public static final int SUCCESS       = 0xFF4ADE80;
    public static final int DANGER        = 0xFFF87171;

    private Ui() {}

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
