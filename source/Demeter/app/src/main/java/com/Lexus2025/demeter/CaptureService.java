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
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import java.nio.*;

public class CaptureService extends Service {
    private static final String TAG = "CaptureService";
    public static final String EXTRA_RESULT_CODE  = "result_code";
    public static final String EXTRA_RESULT_DATA  = "result_data";

    public static final String EXTRA_CAPTURE_LEFT   = "cap_left";
    public static final String EXTRA_CAPTURE_TOP    = "cap_top";
    public static final String EXTRA_CAPTURE_RIGHT  = "cap_right";
    public static final String EXTRA_CAPTURE_BOTTOM = "cap_bottom";

    public static final String EXTRA_OVERLAY_LEFT   = "ov_left";
    public static final String EXTRA_OVERLAY_TOP    = "ov_top";
    public static final String EXTRA_OVERLAY_RIGHT  = "ov_right";
    public static final String EXTRA_OVERLAY_BOTTOM = "ov_bottom";

    public static final String EXTRA_ADAPTIVE   = "adaptive";
    public static final String EXTRA_FPS_OVERLAY = "fps_overlay";

    public static final String ACTION_STOP        = "com.Lexus2025.demeter.STOP_CAPTURE";
    public static final String ACTION_FPS_OVERLAY = "com.Lexus2025.demeter.FPS_OVERLAY";
    public static final String EXTRA_FPS_ENABLED  = "fps_enabled";

    private static final int  NOTIF_ID          = 1;

    public static final int FPS_OVERLAY_WIDTH_DP  = 80;
    public static final int FPS_OVERLAY_HEIGHT_DP = 36;
    private static final int FPS_OVERLAY_MARGIN_DP = 8;

    private static CaptureApi     sCaptureApi;
    private static ModuleManager  sModuleManager;

    public static void setModuleManager(ModuleManager mgr) {
        sModuleManager = mgr;
        if (sCaptureApi != null) sCaptureApi.setModuleManager(mgr);
    }
    public static CaptureApi getCaptureApi() { return sCaptureApi; }

    private MediaProjection   mProjection;
    private VirtualDisplay    mVirtualDisplay;
    private ImageReader       mImageReader;
    private HandlerThread     mCaptureThread;
    private Handler           mCaptureHandler;
    private Handler           mMainHandler;
    private WindowManager     mWindowManager;
    private SurfaceView       mOverlaySurface;
    private TargetRenderer    mRenderer;
    private CaptureApi        mCaptureApi;
    private int               mScreenWidth;
    private int               mScreenHeight;
    private int               mScreenDensity;
    private boolean           mAdaptive;

    private TextView          mFpsView;
    private WindowManager.LayoutParams mFpsParams;
    private boolean           mFpsOverlayVisible = false;
    private Runnable          mFpsUpdater;

    private Rect mCaptureRect;
    private Rect mOverlayRect;

    private int    mResultCode;
    private Intent mResultData;
    private Rect   mBaseCaptureRect;
    private Rect   mBaseOverlayRect;
    private int    mBaseScreenWidth;
    private int    mBaseScreenHeight;
    private DisplayManager                 mDisplayManager;
    private DisplayManager.DisplayListener mDisplayListener;

    private ByteBuffer mStrippedBuffer;
    private byte[]     mRowScratch;

    private BroadcastReceiver mStopReceiver;
    private BroadcastReceiver mFpsOverlayReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        mMainHandler = new Handler();
        registerStopReceiver();
        registerFpsOverlayReceiver();
    }

    private void registerStopReceiver() {
        mStopReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_STOP.equals(intent.getAction())) {
                    stopSelf();
                }
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_STOP);
        registerReceiver(mStopReceiver, filter);
    }

    private void registerFpsOverlayReceiver() {
        mFpsOverlayReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!ACTION_FPS_OVERLAY.equals(intent.getAction())) return;

                boolean enabled = getSharedPreferences(
                    FiltersActivity.PREFS_NAME, MODE_PRIVATE)
                    .getBoolean(FiltersActivity.PREF_FPS_OVERLAY,
                                intent.getBooleanExtra(EXTRA_FPS_ENABLED, false));

                Log.d(TAG, "FPS broadcast recibido: enabled=" + enabled);

                if (enabled) showFpsOverlay();
                else         hideFpsOverlay();
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_FPS_OVERLAY);
        registerReceiver(mFpsOverlayReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification,
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIF_ID, notification);
        }

        mAdaptive = intent.getBooleanExtra(EXTRA_ADAPTIVE, false);

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);
        mScreenWidth   = metrics.widthPixels;
        mScreenHeight  = metrics.heightPixels;
        mScreenDensity = metrics.densityDpi;

        mResultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        mResultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);

        mCaptureRect = rectFromIntent(intent,
                                      EXTRA_CAPTURE_LEFT, EXTRA_CAPTURE_TOP,
                                      EXTRA_CAPTURE_RIGHT, EXTRA_CAPTURE_BOTTOM,
                                      mScreenWidth, mScreenHeight);

        if (mAdaptive) {
            mOverlayRect = new Rect(mCaptureRect);
        } else {
            mOverlayRect = rectFromIntent(intent,
                                          EXTRA_OVERLAY_LEFT, EXTRA_OVERLAY_TOP,
                                          EXTRA_OVERLAY_RIGHT, EXTRA_OVERLAY_BOTTOM,
                                          mScreenWidth, mScreenHeight);
        }

        mBaseCaptureRect  = new Rect(mCaptureRect);
        mBaseOverlayRect  = new Rect(mOverlayRect);
        mBaseScreenWidth  = mScreenWidth;
        mBaseScreenHeight = mScreenHeight;

        registerDisplayListener();
        setupOverlay();
        setupCapture(mResultCode, mResultData);

        boolean wantFps = getSharedPreferences(FiltersActivity.PREFS_NAME, MODE_PRIVATE)
            .getBoolean(FiltersActivity.PREF_FPS_OVERLAY,
                        intent.getBooleanExtra(EXTRA_FPS_OVERLAY, false));
        Log.d(TAG, "onStartCommand: wantFps=" + wantFps);
        if (wantFps) showFpsOverlay();

        return START_NOT_STICKY;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return (int)(value * density + 0.5f);
    }

    private void applyFpsPosition() {
        if (mFpsParams == null) return;

        SharedPreferences prefs = getSharedPreferences(
            FiltersActivity.PREFS_NAME, MODE_PRIVATE);
        int posX = prefs.getInt(FpsPositionActivity.PREF_POS_X, -1);
        int posY = prefs.getInt(FpsPositionActivity.PREF_POS_Y, -1);

        if (posX >= 0 && posY >= 0) {
            mFpsParams.gravity = Gravity.TOP | Gravity.LEFT;
            mFpsParams.x = posX;
            mFpsParams.y = posY;
        } else {
            int margin = dp(FPS_OVERLAY_MARGIN_DP);
            int w      = dp(FPS_OVERLAY_WIDTH_DP);
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            DisplayMetrics dm = new DisplayMetrics();
            if (wm != null) {
                wm.getDefaultDisplay().getRealMetrics(dm);
            } else {
                dm.widthPixels  = mScreenWidth;
                dm.heightPixels = mScreenHeight;
            }
            mFpsParams.gravity = Gravity.TOP | Gravity.LEFT;
            mFpsParams.x = dm.widthPixels - w - margin;
            mFpsParams.y = margin;
        }
    }

    private void setupFpsOverlay() {
        if (mFpsView != null) return;

        int overlayType = Build.VERSION.SDK_INT >= 26
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;

        int w = dp(FPS_OVERLAY_WIDTH_DP);
        int h = dp(FPS_OVERLAY_HEIGHT_DP);

        mFpsParams = new WindowManager.LayoutParams(
            w, h, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );

        applyFpsPosition();

        mFpsView = new TextView(this);
        mFpsView.setText("-- FPS");
        mFpsView.setTextColor(Color.WHITE);
        mFpsView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
        mFpsView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mFpsView.setGravity(Gravity.CENTER);
        mFpsView.setPadding(dp(8), dp(4), dp(8), dp(4));

        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xCC0F1115);
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), 0x557C5CFF);
        mFpsView.setBackground(bg);
    }

    private void showFpsOverlay() {
        if (mWindowManager == null) return;

        if (mFpsOverlayVisible && mFpsView != null && mFpsParams != null) {
            applyFpsPosition();
            try {
                mWindowManager.updateViewLayout(mFpsView, mFpsParams);
            } catch (Exception e) {
                Log.w(TAG, "updateFpsLayout: " + e.getMessage());
            }
            return;
        }

        setupFpsOverlay();
        try {
            mWindowManager.addView(mFpsView, mFpsParams);
            mFpsOverlayVisible = true;
            startFpsUpdater();
        } catch (Exception e) {
            Log.w(TAG, "showFpsOverlay error: " + e.getMessage());
        }
    }

    private void hideFpsOverlay() {
        stopFpsUpdater();
        if (mWindowManager != null && mFpsView != null && mFpsOverlayVisible) {
            try { mWindowManager.removeView(mFpsView); }
            catch (Exception e) { Log.w(TAG, "hideFpsOverlay error: " + e.getMessage()); }
        }
        mFpsOverlayVisible = false;
        mFpsView = null;
    }

    private void startFpsUpdater() {
        if (mFpsUpdater != null) return;
        mFpsUpdater = new Runnable() {
            @Override
            public void run() {
                if (!mFpsOverlayVisible || mFpsView == null) return;
                CaptureApi api = sCaptureApi;
                if (api != null) {
                    float fps = api.getFps();
                    if (fps < 0f) mFpsView.setText("-- FPS");
                    else          mFpsView.setText(String.format("%.0f FPS", fps));
                }
                mMainHandler.postDelayed(this, 500);
            }
        };
        mMainHandler.post(mFpsUpdater);
    }

    private void stopFpsUpdater() {
        if (mFpsUpdater != null) {
            mMainHandler.removeCallbacks(mFpsUpdater);
            mFpsUpdater = null;
        }
    }

    private Rect rectFromIntent(Intent intent,
                                String kL, String kT, String kR, String kB,
                                int maxW, int maxH) {
        if (!intent.hasExtra(kL) && !intent.hasExtra(kR)) {
            return new Rect(0, 0, maxW, maxH);
        }
        int left   = intent.getIntExtra(kL, 0);
        int top    = intent.getIntExtra(kT, 0);
        int right  = intent.getIntExtra(kR, maxW);
        int bottom = intent.getIntExtra(kB, maxH);
        if (right  <= left) right  = maxW;
        if (bottom <= top)  bottom = maxH;
        left   = Math.max(0, Math.min(left,   maxW));
        top    = Math.max(0, Math.min(top,    maxH));
        right  = Math.max(left + 1, Math.min(right,  maxW));
        bottom = Math.max(top  + 1, Math.min(bottom, maxH));
        return new Rect(left, top, right, bottom);
    }

    private void registerDisplayListener() {
        if (mDisplayListener != null) return;
        mDisplayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (mDisplayManager == null) return;

        mDisplayListener = new DisplayManager.DisplayListener() {
            @Override public void onDisplayAdded(int displayId) {}
            @Override public void onDisplayRemoved(int displayId) {}
            @Override public void onDisplayChanged(int displayId) {
                if (displayId != Display.DEFAULT_DISPLAY) return;
                WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                DisplayMetrics dm = new DisplayMetrics();
                wm.getDefaultDisplay().getRealMetrics(dm);
                if (dm.widthPixels != mScreenWidth || dm.heightPixels != mScreenHeight) {
                    handleRotation();
                }
            }
        };
        mDisplayManager.registerDisplayListener(mDisplayListener, mMainHandler);
    }

    private void unregisterDisplayListener() {
        if (mDisplayManager != null && mDisplayListener != null) {
            try { mDisplayManager.unregisterDisplayListener(mDisplayListener); }
            catch (Exception ignored) {}
        }
        mDisplayListener = null;
        mDisplayManager  = null;
    }

    private void setupOverlay() {
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        int overlayType = Build.VERSION.SDK_INT >= 26
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;

        int ovW = mOverlayRect.width();
        int ovH = mOverlayRect.height();

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            ovW, ovH, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        );
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = mOverlayRect.left;
        params.y = mOverlayRect.top;

        mOverlaySurface = new SurfaceView(this);
        mOverlaySurface.getHolder().setFormat(PixelFormat.OPAQUE);

        mOverlaySurface.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    mRenderer   = new TargetRenderer(CaptureService.this, holder, sModuleManager);
                    mCaptureApi = new CaptureApi(mRenderer);
                    mCaptureApi.setModuleManager(sModuleManager);
                    mRenderer.setScaleInfo(mCaptureRect, mOverlayRect);
                    sCaptureApi = mCaptureApi;
                }
                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
                    if (mRenderer != null) {
                        mRenderer.setHolder(holder);
                        mRenderer.setScaleInfo(mCaptureRect, mOverlayRect);
                    }
                }
                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    if (mRenderer != null) mRenderer.setHolder(null);
                    sCaptureApi = null;
                }
            });

        mWindowManager.addView(mOverlaySurface, params);

        mOverlaySurface.post(new Runnable() {
                @Override public void run() {
                    int[] loc = new int[2];
                    mOverlaySurface.getLocationOnScreen(loc);
                    int actualY  = loc[1];
                    int desiredY = mOverlayRect.top;
                    int error    = actualY - desiredY;
                    if (error != 0) {
                        params.y = desiredY - error;
                        try { mWindowManager.updateViewLayout(mOverlaySurface, params); }
                        catch (Exception e) { Log.w(TAG, "autocorrección: " + e.getMessage()); }
                    }
                }
            });
    }

    private void setupCapture(int resultCode, Intent resultData) {
        mCaptureThread = new HandlerThread("CaptureThread");
        mCaptureThread.start();
        mCaptureHandler = new Handler(mCaptureThread.getLooper());

        mImageReader = ImageReader.newInstance(
            mScreenWidth, mScreenHeight, PixelFormat.RGBA_8888, 2);

        // CLAVE: enviamos el frame directo al renderer desde este mismo hilo.
        // Sin dispatcher, sin buffer, sin salto por el main thread.
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        if (image == null) return;
                        android.graphics.Bitmap bmp = imageToBitmap(image, mCaptureRect);
                        if (bmp == null) return;

                        CaptureApi api = sCaptureApi;
                        if (api != null) {
                            api.sendFrame(bmp);
                        } else {
                            BitmapPool.release(bmp);
                        }
                    } catch (IllegalStateException e) {
                        Log.w(TAG, "Buffer inaccesible: " + e.getMessage());
                    } finally {
                        if (image != null) image.close();
                    }
                }
            }, mCaptureHandler);

        MediaProjectionManager pm =
            (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mProjection = pm.getMediaProjection(resultCode, resultData);

        int dispFlags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            dispFlags |= DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
        }

        mVirtualDisplay = mProjection.createVirtualDisplay(
            "DemeterCapture", mScreenWidth, mScreenHeight, mScreenDensity,
            dispFlags, mImageReader.getSurface(), null, null);
    }

    private android.graphics.Bitmap imageToBitmap(Image image, Rect crop) {
        Image.Plane plane   = image.getPlanes()[0];
        ByteBuffer  srcBuf  = plane.getBuffer();
        int imgW        = image.getWidth();
        int imgH        = image.getHeight();
        int rowStride   = plane.getRowStride();
        int pixelStride = plane.getPixelStride();

        int cropL = Math.max(0, crop.left);
        int cropT = Math.max(0, crop.top);
        int cropR = Math.min(imgW, crop.right);
        int cropB = Math.min(imgH, crop.bottom);
        int cropW = cropR - cropL;
        int cropH = cropB - cropT;
        if (cropW <= 0 || cropH <= 0) return null;

        int needStripped = cropW * cropH * pixelStride;
        if (mStrippedBuffer == null || mStrippedBuffer.capacity() < needStripped) {
            mStrippedBuffer = ByteBuffer.allocateDirect(needStripped);
        } else {
            mStrippedBuffer.clear();
        }

        int rowBytes = cropW * pixelStride;
        if (mRowScratch == null || mRowScratch.length < rowBytes) {
            mRowScratch = new byte[rowBytes];
        }

        for (int row = cropT; row < cropB; row++) {
            int pos = row * rowStride + cropL * pixelStride;
            srcBuf.position(pos);
            srcBuf.get(mRowScratch, 0, rowBytes);
            mStrippedBuffer.put(mRowScratch, 0, rowBytes);
        }
        mStrippedBuffer.rewind();

        android.graphics.Bitmap bmp = BitmapPool.acquire(cropW, cropH);
        bmp.copyPixelsFromBuffer(mStrippedBuffer);
        return bmp;
    }

    private synchronized void teardownCaptureOnly() {
        if (mVirtualDisplay != null) { mVirtualDisplay.release(); mVirtualDisplay = null; }
        if (mImageReader    != null) { mImageReader.close();       mImageReader    = null; }
        if (mCaptureThread  != null) { mCaptureThread.quitSafely(); mCaptureThread = null; }
        mCaptureHandler = null;
    }

    private void handleRotation() {
        teardownCaptureOnly();

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);
        mScreenWidth   = metrics.widthPixels;
        mScreenHeight  = metrics.heightPixels;
        mScreenDensity = metrics.densityDpi;

        mCaptureRect = rescaleRect(mBaseCaptureRect, mBaseScreenWidth, mBaseScreenHeight,
                                   mScreenWidth, mScreenHeight);
        if (mAdaptive) {
            mOverlayRect = new Rect(mCaptureRect);
        } else {
            mOverlayRect = rescaleRect(mBaseOverlayRect, mBaseScreenWidth, mBaseScreenHeight,
                                       mScreenWidth, mScreenHeight);
        }

        if (mWindowManager != null && mOverlaySurface != null) {
            WindowManager.LayoutParams lp =
                (WindowManager.LayoutParams) mOverlaySurface.getLayoutParams();
            if (lp != null) {
                lp.gravity = Gravity.TOP | Gravity.LEFT;
                lp.width  = mOverlayRect.width();
                lp.height = mOverlayRect.height();
                lp.x      = mOverlayRect.left;
                lp.y      = mOverlayRect.top;
                try { mWindowManager.updateViewLayout(mOverlaySurface, lp); }
                catch (Exception e) { Log.w(TAG, "updateViewLayout: " + e.getMessage()); }
            }
        }

        if (mRenderer != null) mRenderer.setScaleInfo(mCaptureRect, mOverlayRect);

        BitmapPool.clear();
        setupCapture(mResultCode, mResultData);
    }

    private Rect rescaleRect(Rect base, int fromW, int fromH, int toW, int toH) {
        if (base == null || fromW == 0 || fromH == 0) return new Rect(0, 0, toW, toH);
        if (base.left == 0 && base.top == 0 && base.right == fromW && base.bottom == fromH) {
            return new Rect(0, 0, toW, toH);
        }
        float sx = (float) toW / fromW, sy = (float) toH / fromH;
        return new Rect(Math.round(base.left * sx), Math.round(base.top * sy),
                        Math.round(base.right * sx), Math.round(base.bottom * sy));
    }

    @Override
    public void onDestroy() {
        if (mStopReceiver != null) {
            try { unregisterReceiver(mStopReceiver); } catch (Exception ignored) {}
            mStopReceiver = null;
        }
        if (mFpsOverlayReceiver != null) {
            try { unregisterReceiver(mFpsOverlayReceiver); } catch (Exception ignored) {}
            mFpsOverlayReceiver = null;
        }
        hideFpsOverlay();
        unregisterDisplayListener();
        teardownCaptureOnly();
        if (mProjection    != null) { mProjection.stop(); mProjection = null; }
        if (mWindowManager != null && mOverlaySurface != null) {
            try { mWindowManager.removeView(mOverlaySurface); } catch (Exception ignored) {}
        }
        if (mRenderer != null) { mRenderer.release(); mRenderer = null; }

        mStrippedBuffer = null;
        mRowScratch     = null;

        BitmapPool.clear();

        sCaptureApi = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            android.app.NotificationChannel ch = new android.app.NotificationChannel(
                "demeter", "Demeter", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .createNotificationChannel(ch);
        }

        Intent stopIntent = new Intent(ACTION_STOP);
        stopIntent.setPackage(getPackageName());
        int piFlags = Build.VERSION.SDK_INT >= 23
            ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent stopPi = PendingIntent.getBroadcast(this, 0, stopIntent, piFlags);

        Notification.Builder builder = new Notification.Builder(this)
            .setContentTitle("Demeter")
            .setContentText("Captura activa")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Detener", stopPi);

        if (Build.VERSION.SDK_INT >= 26) {
            builder.setChannelId("demeter");
        }
        return builder.build();
    }
}
