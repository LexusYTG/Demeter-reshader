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

import android.graphics.Bitmap;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public final class BitmapPool {

    private static final String TAG = "BitmapPool";
    private static final int MAX_PER_SIZE = 6;

    private static final Map<String, Deque<Bitmap>> sPool =
	new HashMap<String, Deque<Bitmap>>();

    private BitmapPool() {}

    public static synchronized Bitmap acquire(int w, int h) {
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("BitmapPool.acquire: size inválido " + w + "x" + h);
        }
        String key = key(w, h);
        Deque<Bitmap> q = sPool.get(key);
        if (q != null) {
            while (!q.isEmpty()) {
                Bitmap b = q.pollFirst();
                if (b != null && !b.isRecycled() && b.isMutable()) {
                    b.eraseColor(0);
                    return b;
                }
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    }

    public static synchronized void release(Bitmap b) {
        if (b == null || b.isRecycled() || !b.isMutable()) return;
        String key = key(b.getWidth(), b.getHeight());
        Deque<Bitmap> q = sPool.get(key);
        if (q == null) {
            q = new ArrayDeque<Bitmap>();
            sPool.put(key, q);
        }

        if (q.contains(b)) return;

        if (q.size() < MAX_PER_SIZE) {
            q.offerFirst(b);
        } else {
            b.recycle();
        }
    }

    public static synchronized void clear() {
        int n = 0;
        for (Deque<Bitmap> q : sPool.values()) {
            while (!q.isEmpty()) {
                Bitmap b = q.pollFirst();
                if (b != null && !b.isRecycled()) { b.recycle(); n++; }
            }
        }
        sPool.clear();
        if (n > 0) Log.d(TAG, "Pool vaciado: " + n + " bitmaps reciclados");
    }

    private static String key(int w, int h) {
        return w + "x" + h;
    }
}
