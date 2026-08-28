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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import java.nio.ByteBuffer;

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

    /**
     * MODO ADAPTATIVO: si es true, el overlay se posiciona y dimensiona
     * exactamente igual que el área de captura → render 1:1, sin escala ni
     * distorsión. Se activa cuando capture rect == overlay rect, o cuando
     * el Intent no trae ninguno de los dos (ambos null → pantalla completa).
     */
    public static final String EXTRA_ADAPTIVE = "adaptive";

    private static final int  NOTIF_ID          = 1;
    private static final int  BUFFER_SIZE        = 4;
    private static final long FRAME_INTERVAL_MS  = 16L;

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
    private Bitmap[]          mBuffer;
    private int               mBufferHead;
    private int               mBufferCount;
    private int               mScreenWidth;
    private int               mScreenHeight;
    private int               mScreenDensity;
    private Runnable          mFrameDispatcher;
    private boolean           mAdaptive;

    private Rect mCaptureRect;
    private Rect mOverlayRect;

    // Guardamos para poder reiniciar la captura tras rotación o al reutilizar el service.
    private int    mResultCode;
    private Intent mResultData;
    // Rects "base" tal como los configuró el usuario (en coordenadas de la pantalla
    // original). Se usan para recalcular proporciones tras una rotación.
    private Rect   mBaseCaptureRect;
    private Rect   mBaseOverlayRect;
    private int    mBaseScreenWidth;
    private int    mBaseScreenHeight;

    @Override
    public void onCreate() {
        super.onCreate();
        mMainHandler = new Handler();
        mBuffer      = new Bitmap[BUFFER_SIZE];
        mBufferHead  = 0;
        mBufferCount = 0;
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

        // Dimensiones reales de pantalla
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
            Log.d(TAG, "Modo adaptativo: overlay = capture = " + mOverlayRect);
        } else {
            mOverlayRect = rectFromIntent(intent,
                                          EXTRA_OVERLAY_LEFT, EXTRA_OVERLAY_TOP,
                                          EXTRA_OVERLAY_RIGHT, EXTRA_OVERLAY_BOTTOM,
                                          mScreenWidth, mScreenHeight);
        }

        // Guardar los rects y dimensiones originales para rescalar tras rotación
        mBaseCaptureRect  = new Rect(mCaptureRect);
        mBaseOverlayRect  = new Rect(mOverlayRect);
        mBaseScreenWidth  = mScreenWidth;
        mBaseScreenHeight = mScreenHeight;

        setupOverlay();
        setupCapture(mResultCode, mResultData);
        return START_NOT_STICKY;
    }

    private Rect rectFromIntent(Intent intent,
                                String kL, String kT, String kR, String kB,
                                int maxW, int maxH) {
        // FIX: si NO viene ninguno de los 4 extras, devolver pantalla completa.
        // Antes usaba getIntExtra con default = 0/maxW/maxH, lo que podía
        // construir un rect válido aunque el usuario no lo hubiera configurado.
        if (!intent.hasExtra(kL) && !intent.hasExtra(kR)) {
            return new Rect(0, 0, maxW, maxH);
        }
        int left   = intent.getIntExtra(kL, 0);
        int top    = intent.getIntExtra(kT, 0);
        int right  = intent.getIntExtra(kR, maxW);
        int bottom = intent.getIntExtra(kB, maxH);
        if (right  <= left) right  = maxW;
        if (bottom <= top)  bottom = maxH;
        // Sanity: dentro de límites de pantalla
        left   = Math.max(0, Math.min(left,   maxW));
        top    = Math.max(0, Math.min(top,    maxH));
        right  = Math.max(left + 1, Math.min(right,  maxW));
        bottom = Math.max(top  + 1, Math.min(bottom, maxH));
        return new Rect(left, top, right, bottom);
    }

    /**
     * Devuelve la altura en píxeles de la status bar.
     * Con Gravity.TOP|LEFT, WindowManager usa como origen Y el borde inferior
     * de la status bar. Las Activities de selección usan modo inmersivo, así que
     * sus coordenadas parten del pixel físico (0,0). Restando statusBarHeight
     * se alinean ambos sistemas de coordenadas.
     */
    private int getStatusBarHeight() {
        int result = 0;
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) result = getResources().getDimensionPixelSize(resId);
        return result;
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
        mOverlaySurface.setZOrderMediaOverlay(true);

        mOverlaySurface.getHolder().addCallback(new SurfaceHolder.Callback() {
				@Override
				public void surfaceCreated(SurfaceHolder holder) {
					Log.d(TAG, "Surface created");
					mRenderer   = new TargetRenderer(holder, sModuleManager);
					mCaptureApi = new CaptureApi(mRenderer);
					mCaptureApi.setModuleManager(sModuleManager);
					// Informar al renderer las dimensiones de capture y overlay
					// para que pueda calcular la escala adaptativa
					mRenderer.setScaleInfo(mCaptureRect, mOverlayRect);
					sCaptureApi = mCaptureApi;
				}
				@Override
				public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
					Log.d(TAG, "Surface changed: " + w + "x" + h);
					if (mRenderer != null) {
						mRenderer.setHolder(holder);
						mRenderer.setScaleInfo(mCaptureRect, mOverlayRect);
					}
					// FIX rotación: si las dimensiones de pantalla cambiaron,
					// el VirtualDisplay e ImageReader tienen el tamaño incorrecto.
					// surfaceChanged ya se llama con las nuevas dimensiones reales,
					// así que lo usamos directamente sin ningún listener externo.
					WindowManager wm2 = (WindowManager) getSystemService(WINDOW_SERVICE);
					DisplayMetrics dm2 = new DisplayMetrics();
					wm2.getDefaultDisplay().getRealMetrics(dm2);
					if (dm2.widthPixels != mScreenWidth || dm2.heightPixels != mScreenHeight) {
						Log.d(TAG, "Rotación en surfaceChanged: " + mScreenWidth + "x" + mScreenHeight
							  + " → " + dm2.widthPixels + "x" + dm2.heightPixels);
						mMainHandler.post(new Runnable() {
								@Override public void run() { handleRotation(); }
							});
					}
				}
				@Override
				public void surfaceDestroyed(SurfaceHolder holder) {
					Log.d(TAG, "Surface destroyed");
					if (mRenderer != null) mRenderer.setHolder(null);
					sCaptureApi = null;
				}
			});

        mWindowManager.addView(mOverlaySurface, params);

        // Autocorrección del offset vertical: tras añadir la vista medimos dónde
        // la colocó realmente el WindowManager con getLocationOnScreen() y corregimos
        // params.y para que coincida exactamente con mOverlayRect.top.
        // Esto funciona independientemente de la altura de la status bar o del modo
        // de pantalla, sin necesidad de leer recursos ni hardcodear valores.
        mOverlaySurface.post(new Runnable() {
				@Override public void run() {
					int[] loc = new int[2];
					mOverlaySurface.getLocationOnScreen(loc);
					int actualY = loc[1];
					int desiredY = mOverlayRect.top;
					int error = actualY - desiredY;
					if (error != 0) {
						params.y = desiredY - error;
						Log.d(TAG, "Offset corregido: actualY=" + actualY
							  + " desiredY=" + desiredY + " error=" + error
							  + " → params.y=" + params.y);
						try { mWindowManager.updateViewLayout(mOverlaySurface, params); }
						catch (Exception e) { Log.w(TAG, "autocorrección: " + e.getMessage()); }
					}
				}
			});

        Log.d(TAG, "Overlay rect: " + mOverlayRect + "  Capture rect: " + mCaptureRect
			  + "  Adaptive: " + mAdaptive);
    }

    private void setupCapture(int resultCode, Intent resultData) {
        mCaptureThread = new HandlerThread("CaptureThread");
        mCaptureThread.start();
        mCaptureHandler = new Handler(mCaptureThread.getLooper());

        mImageReader = ImageReader.newInstance(
            mScreenWidth, mScreenHeight, PixelFormat.RGBA_8888, 2);

        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
				@Override
				public void onImageAvailable(ImageReader reader) {
					Image image = null;
					try {
						image = reader.acquireLatestImage();
						if (image == null) return;
						Bitmap bmp = imageToBitmap(image, mCaptureRect);
						if (bmp != null) {
							Log.d(TAG, "Frame captured: " + bmp.getWidth() + "x" + bmp.getHeight());
							enqueueFrame(bmp);
						}
					} catch (IllegalStateException e) {
						// FIX crash del log: buffer invalidado cuando MediaProjection se detiene
						Log.w(TAG, "Buffer inaccessible, descartando frame: " + e.getMessage());
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

        mFrameDispatcher = new Runnable() {
            @Override
            public void run() {
                dispatchNextFrame();
                mMainHandler.postDelayed(this, FRAME_INTERVAL_MS);
            }
        };
        mMainHandler.post(mFrameDispatcher);
    }

    /**
     * Convierte Image a Bitmap recortado al área de captura.
     * FIX crash: el try/catch del llamador atrapa IllegalStateException
     * si el buffer ya fue invalidado al detener MediaProjection.
     */
    private Bitmap imageToBitmap(Image image, Rect crop) {
        Image.Plane plane   = image.getPlanes()[0];
        ByteBuffer  buffer  = plane.getBuffer();
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

        ByteBuffer stripped = ByteBuffer.allocate(cropW * cropH * pixelStride);
        for (int row = cropT; row < cropB; row++) {
            int pos = row * rowStride + cropL * pixelStride;
            buffer.position(pos);
            byte[] rowData = new byte[cropW * pixelStride];
            buffer.get(rowData, 0, rowData.length);
            stripped.put(rowData);
        }
        stripped.rewind();

        Bitmap bitmap = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(stripped);
        return bitmap;
    }

    private synchronized void enqueueFrame(Bitmap frame) {
        int writeIdx = (mBufferHead + mBufferCount) % BUFFER_SIZE;
        if (mBuffer[writeIdx] != null) mBuffer[writeIdx].recycle();
        mBuffer[writeIdx] = frame;
        if (mBufferCount < BUFFER_SIZE) mBufferCount++;
        else mBufferHead = (mBufferHead + 1) % BUFFER_SIZE;
    }

    private synchronized void dispatchNextFrame() {
        if (mBufferCount == 0 || mCaptureApi == null) return;
        Bitmap frame = mBuffer[mBufferHead];
        mBuffer[mBufferHead] = null;
        mBufferHead = (mBufferHead + 1) % BUFFER_SIZE;
        mBufferCount--;
        if (frame != null) mCaptureApi.sendFrame(frame);
    }

    /**
     * Para y libera solo la capa de captura (VirtualDisplay + ImageReader + hilo).
     * No toca el overlay ni la MediaProjection, así puede reutilizarse en rotación.
     */
    private void teardownCaptureOnly() {
        mMainHandler.removeCallbacks(mFrameDispatcher);
        mFrameDispatcher = null;
        if (mVirtualDisplay != null) { mVirtualDisplay.release(); mVirtualDisplay = null; }
        if (mImageReader    != null) { mImageReader.close();       mImageReader    = null; }
        if (mCaptureThread  != null) { mCaptureThread.quitSafely(); mCaptureThread = null; }
        mCaptureHandler = null;
        synchronized (this) {
            for (int i = 0; i < mBuffer.length; i++) {
                if (mBuffer[i] != null) { mBuffer[i].recycle(); mBuffer[i] = null; }
            }
            mBufferHead = 0; mBufferCount = 0;
        }
    }

    /**
     * Reinicia captura + overlay tras una rotación.
     * Solo se ocupa de VirtualDisplay/ImageReader: el SurfaceView/GL ya se
     * reinició solo a través de surfaceChanged → setHolder().
     * Debe llamarse desde el hilo principal.
     */
    private void handleRotation() {
        // 1. Parar captura vieja
        teardownCaptureOnly();

        // 2. Nuevas dimensiones reales
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);
        int oldW = mScreenWidth, oldH = mScreenHeight;
        mScreenWidth   = metrics.widthPixels;
        mScreenHeight  = metrics.heightPixels;
        mScreenDensity = metrics.densityDpi;

        // 3. Rescalar rects proporcionalmente
        mCaptureRect = rescaleRect(mBaseCaptureRect, mBaseScreenWidth, mBaseScreenHeight,
                                   mScreenWidth, mScreenHeight);
        if (mAdaptive) {
            mOverlayRect = new Rect(mCaptureRect);
        } else {
            mOverlayRect = rescaleRect(mBaseOverlayRect, mBaseScreenWidth, mBaseScreenHeight,
                                       mScreenWidth, mScreenHeight);
        }

        // 4. Actualizar tamaño del overlay en WindowManager
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

        // 5. Informar al renderer las nuevas dimensiones
        if (mRenderer != null) mRenderer.setScaleInfo(mCaptureRect, mOverlayRect);

        // 6. Relanzar captura con nuevas dimensiones
        setupCapture(mResultCode, mResultData);
        Log.d(TAG, "Rotación manejada: capture=" + mCaptureRect + " overlay=" + mOverlayRect);
    }

    private Rect rescaleRect(Rect base, int fromW, int fromH, int toW, int toH) {
        if (base == null || fromW == 0 || fromH == 0) return new Rect(0, 0, toW, toH);
        // Si era pantalla completa, sigue siendo pantalla completa
        if (base.left == 0 && base.top == 0 && base.right == fromW && base.bottom == fromH) {
            return new Rect(0, 0, toW, toH);
        }
        float sx = (float) toW / fromW, sy = (float) toH / fromH;
        return new Rect(Math.round(base.left * sx), Math.round(base.top * sy),
                        Math.round(base.right * sx), Math.round(base.bottom * sy));
    }

    @Override
    public void onDestroy() {
        teardownCaptureOnly();
        if (mProjection    != null) { mProjection.stop();  mProjection    = null; }
        if (mWindowManager != null && mOverlaySurface != null) {
            try { mWindowManager.removeView(mOverlaySurface); } catch (Exception ignored) {}
        }
        if (mRenderer != null) { mRenderer.release(); mRenderer = null; }
        sCaptureApi = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private Notification buildNotification() {
        Notification.Builder builder = new Notification.Builder(this)
            .setContentTitle("Demeter")
            .setContentText("Captura activa")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true);
        if (Build.VERSION.SDK_INT >= 26) {
            android.app.NotificationChannel ch = new android.app.NotificationChannel(
                "demeter", "Demeter", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .createNotificationChannel(ch);
            builder.setChannelId("demeter");
        }
        return builder.build();
    }
}


