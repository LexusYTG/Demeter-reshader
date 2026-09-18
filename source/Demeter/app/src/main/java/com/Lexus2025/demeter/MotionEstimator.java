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

public final class MotionEstimator {

    private static final String TAG = "MotionEstimator";

    public static final int MOTION_MAP_W = 32;
    public static final int MOTION_MAP_H = 18;
    public static final int DS_W         = 96;
    public static final int DS_H         = 54;
    private static final int PATCH_RADIUS  = 1;
    private static final int SEARCH_RADIUS = 2;

    private MotionEstimator() {}

    public static Bitmap downsample(Bitmap src) {
        return Bitmap.createScaledBitmap(src, DS_W, DS_H, false);
    }

    public static Bitmap estimate(Bitmap prevDs, Bitmap currDs, float dsToOrigScale) {
        long t0 = System.nanoTime();

        int dsW = prevDs.getWidth();
        int dsH = prevDs.getHeight();

        int[] prev = new int[dsW * dsH];
        int[] curr = new int[dsW * dsH];
        prevDs.getPixels(prev, 0, dsW, 0, 0, dsW, dsH);
        currDs.getPixels(curr, 0, dsW, 0, 0, dsW, dsH);

        int[] prevLum = new int[dsW * dsH];
        int[] currLum = new int[dsW * dsH];
        for (int i = 0; i < prev.length; i++) {
            prevLum[i] = lum(prev[i]);
            currLum[i] = lum(curr[i]);
        }

        float[] dxRaw = new float[MOTION_MAP_W * MOTION_MAP_H];
        float[] dyRaw = new float[MOTION_MAP_W * MOTION_MAP_H];
        float[] confRaw = new float[MOTION_MAP_W * MOTION_MAP_H];

        int cellDsW = dsW / MOTION_MAP_W;
        int cellDsH = dsH / MOTION_MAP_H;

        for (int my = 0; my < MOTION_MAP_H; my++) {
            for (int mx = 0; mx < MOTION_MAP_W; mx++) {
                int cx = mx * cellDsW + cellDsW / 2;
                int cy = my * cellDsH + cellDsH / 2;

                int px0 = cx - PATCH_RADIUS;
                int py0 = cy - PATCH_RADIUS;
                int px1 = cx + PATCH_RADIUS;
                int py1 = cy + PATCH_RADIUS;
                int idx = my * MOTION_MAP_W + mx;

                if (px0 < 0 || py0 < 0 || px1 >= dsW || py1 >= dsH) {
                    dxRaw[idx] = 0f;
                    dyRaw[idx] = 0f;
                    confRaw[idx] = 0f;
                    continue;
                }

                int bestCost = Integer.MAX_VALUE;
                int bestDx = 0, bestDy = 0;

                for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
                    for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                        if (px0 + dx < 0 || py0 + dy < 0) continue;
                        if (px1 + dx >= dsW || py1 + dy >= dsH) continue;

                        int cost = 0;
                        for (int py = py0; py <= py1; py++) {
                            int rowPrev = py * dsW;
                            int rowCurr = (py + dy) * dsW;
                            for (int px = px0; px <= px1; px++) {
                                cost += Math.abs(prevLum[rowPrev + px]
                                                 - currLum[rowCurr + px + dx]);
                            }
                        }
                        if (cost < bestCost) {
                            bestCost = cost;
                            bestDx = dx;
                            bestDy = dy;
                        }
                    }
                }

                dxRaw[idx]   = bestDx * dsToOrigScale;
                dyRaw[idx]   = bestDy * dsToOrigScale;
                confRaw[idx] = 1.0f - Math.min(1.0f, bestCost / 1024.0f);
            }
        }

        float[] kernel = { 1f/16f, 2f/16f, 1f/16f,
			2f/16f, 4f/16f, 2f/16f,
			1f/16f, 2f/16f, 1f/16f };

        float[] dxBlur   = new float[MOTION_MAP_W * MOTION_MAP_H];
        float[] dyBlur   = new float[MOTION_MAP_W * MOTION_MAP_H];
        float[] confBlur = new float[MOTION_MAP_W * MOTION_MAP_H];

        for (int my = 0; my < MOTION_MAP_H; my++) {
            for (int mx = 0; mx < MOTION_MAP_W; mx++) {
                float sumX = 0f, sumY = 0f, sumC = 0f, sumW = 0f;
                for (int ky = -1; ky <= 1; ky++) {
                    for (int kx = -1; kx <= 1; kx++) {
                        int sx = mx + kx;
                        int sy = my + ky;
                        if (sx < 0 || sx >= MOTION_MAP_W) continue;
                        if (sy < 0 || sy >= MOTION_MAP_H) continue;
                        float w = kernel[(ky + 1) * 3 + (kx + 1)];
                        int idx = sy * MOTION_MAP_W + sx;
                        sumX += dxRaw[idx]   * w;
                        sumY += dyRaw[idx]   * w;
                        sumC += confRaw[idx] * w;
                        sumW += w;
                    }
                }
                int idx = my * MOTION_MAP_W + mx;
                dxBlur[idx]   = sumX / sumW;
                dyBlur[idx]   = sumY / sumW;
                confBlur[idx] = sumC / sumW;
            }
        }

        int[] outPixels = new int[MOTION_MAP_W * MOTION_MAP_H];
        for (int i = 0; i < outPixels.length; i++) {
            int dx = Math.round(dxBlur[i]);
            int dy = Math.round(dyBlur[i]);
            if (dx >  127) dx =  127;
            if (dx < -127) dx = -127;
            if (dy >  127) dy =  127;
            if (dy < -127) dy = -127;
            int conf = Math.round(Math.max(0f, Math.min(1f, confBlur[i])) * 255f);
            outPixels[i] = packRGBA(128 + dx, 128 + dy, conf, 255);
        }

        Bitmap out = Bitmap.createBitmap(outPixels, MOTION_MAP_W, MOTION_MAP_H,
                                         Bitmap.Config.ARGB_8888);

        long dt = (System.nanoTime() - t0) / 1_000_000L;
        if (dt > 8) Log.d(TAG, "Motion estimation: " + dt + "ms");
        return out;
    }

    private static int lum(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >>  8) & 0xFF;
        int b =  argb        & 0xFF;
        return (r * 77 + g * 150 + b * 29) >> 8;
    }

    private static int packRGBA(int r, int g, int b, int a) {
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
