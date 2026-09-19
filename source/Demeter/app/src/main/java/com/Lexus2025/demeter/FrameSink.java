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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class FrameSink {
    private static final String TAG = "FrameSink";
    private static final long FRAME_DELAY_MS = 16;

    private static final int FG_FAKE_SLOT     = 5;
    private static final int G1_OUT_BASE      = 3;
    private static final int G1_OUT_COUNT     = 2;
    private static final int G3_SLOT_A1       = 0;
    private static final int G3_SLOT_A2       = 1;
    private static final int G3_MOTION_SLOT   = 2;

    private static final int CHAIN_INPUT_SLOT = 0;
    private static final int CHAIN_FIRST_OUT  = 3;

    private static final long DEFAULT_INPUT_INTERVAL_NS   = 33_000_000L;
    private static final long DEFAULT_DISPLAY_INTERVAL_NS = 16_666_667L;
    private static final int  UNDERRUN_LOG_THRESHOLD      = 30;

    private final Context mContext;

    private volatile SurfaceHolder mHolder;
    private volatile Mods mMods;
    private GlPainter mPainter;
    private boolean mGlReady = false;

    private HandlerThread mGlThread;
    private Handler       mGlHandler;
    private final long mStartTimeMs = System.currentTimeMillis();

    private static final int FPS_WINDOW = 30;
    private final long[] mFrameTimesNs = new long[FPS_WINDOW];
    private int          mFrameTimeIdx = 0;
    private volatile float mFps        = -1f;

    private volatile boolean mFpsOverlay = false;

    private volatile long mLastInputNs       = 0L;
    private volatile long mInputIntervalNs   = DEFAULT_INPUT_INTERVAL_NS;
    private volatile long mDisplayIntervalNs = DEFAULT_DISPLAY_INTERVAL_NS;

    private volatile boolean mFrameGenMode = false;
    private volatile int     mFgVersion = 2;

    private int mG2SlotA = -1;
    private int mG2SlotC = -1;
    private volatile boolean mG2FakeReady  = false;
    private boolean          mG2ShowFake   = false;
    private boolean          mG2GenRunning = false;
    private boolean          mG2PresRunning = false;
    private int              mG2UnderrunStreak = 0;

    private static final int G1_QUEUE_SIZE = 3;
    private final int[] mG1QueueC    = new int[G1_QUEUE_SIZE];
    private final int[] mG1QueueFake = new int[G1_QUEUE_SIZE];
    private int  mG1QueueHead    = 0;
    private int  mG1QueueCount   = 0;
    private boolean mG1ShowingFake = false;

    private int mG1SlotA = -1;
    private int mG1SlotC = -1;

    private boolean mG1GenRunning   = false;
    private boolean mG1PresRunning  = false;
    private int     mG1UnderrunStreak = 0;

    private static final int G3_QUEUE_SIZE = 3;
    private final int[] mG3QueueReal = new int[G3_QUEUE_SIZE];
    private final int[] mG3QueueFake = new int[G3_QUEUE_SIZE];
    private int  mG3QueueHead  = 0;
    private int  mG3QueueCount = 0;

    private int  mG3SlotA     = -1;
    private int  mG3SlotNext  = 0;
    private Bitmap mG3PrevDs  = null;

    private boolean mG3PresRunning = false;
    private int     mG3UnderrunStreak = 0;
    private boolean mG3WarnedTypeMismatch = false;

    private volatile boolean mRenderLoopRunning = false;
    private final Runnable mRenderLoop = new Runnable() {
        @Override
        public void run() {
            if (!mRenderLoopRunning) return;
            if (mHolder != null && mGlReady && mPainter != null) {
                drawNow(null);
                tickFps();
            }
            mGlHandler.postDelayed(this, FRAME_DELAY_MS * 2);
        }
    };

    private void drawNow(Bitmap frameIfNew) {
        List<Mod> chain = (mMods != null)
            ? mMods.getEnabledChain()
            : new ArrayList<Mod>();

        if (frameIfNew != null) {
            if (!mPainter.uploadSlot(CHAIN_INPUT_SLOT, frameIfNew)) return;
        }

        if (chain.isEmpty()) {
            mPainter.drawSlot(CHAIN_INPUT_SLOT, null, null);
            return;
        }

        if (chain.size() == 1) {
            Mod m = chain.get(0);
            GlProgram shader = m.getGlProgram();
            if (shader == null) {
                mPainter.drawSlot(CHAIN_INPUT_SLOT, null, null);
                return;
            }
            Map<String, Float> params = paramsFor(m);
            mPainter.drawSlot(CHAIN_INPUT_SLOT, shader, params);
            return;
        }

        int src = CHAIN_INPUT_SLOT;
        int dst = CHAIN_FIRST_OUT;
        boolean anyApplied = false;

        for (Mod m : chain) {
            GlProgram shader = m.getGlProgram();
            if (shader == null) continue;
            Map<String, Float> params = paramsFor(m);
            if (!mPainter.drawSlotInto(src, dst, shader, params)) {
                Log.w(TAG, "Chain: falló " + m.getName());
                break;
            }
            src = dst;
            dst = (dst == 3) ? 4 : (dst == 4 ? 5 : 3);
            if (dst == src) dst = (dst == 3) ? 4 : (dst == 4 ? 5 : 3);
            anyApplied = true;
        }

        if (anyApplied) mPainter.drawSlot(src, null, null);
        else           mPainter.drawSlot(CHAIN_INPUT_SLOT, null, null);
    }

    private Map<String, Float> paramsFor(Mod m) {
        mParamsCache.clear();
        mParamsCache.putAll(m.getParams());
        if (m.getParamDefs().containsKey("uTime")) {
            mParamsCache.put("uTime", (System.currentTimeMillis() - mStartTimeMs) / 1000f);
        }
        return mParamsCache;
    }

    private final Map<String, Float> mParamsCache = new HashMap<String, Float>();

    public FrameSink(Context context, SurfaceHolder holder, Mods mods) {
        mContext = context.getApplicationContext();
        mHolder = holder;
        mMods = mods;

        try {
            WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                Display d = wm.getDefaultDisplay();
                if (d != null) {
                    float hz = d.getRefreshRate();
                    if (hz > 0f) {
                        mDisplayIntervalNs = (long)(1_000_000_000L / hz);
                    }
                }
            }
        } catch (Exception ignored) {}

        mGlThread = new HandlerThread("GlThread");
        mGlThread.start();
        mGlHandler = new Handler(mGlThread.getLooper());

        mGlHandler.post(new Runnable() {
                @Override
                public void run() {
                    mPainter = new GlPainter();
                    mGlReady = mPainter.init(mHolder);
                }
            });
    }

    public float getFps() { return mFps; }
    public void setFpsOverlay(boolean enabled) { mFpsOverlay = enabled; }

    public void setFgVersion(final int version) {
        mGlHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (version == mFgVersion) return;
                    Log.d(TAG, "FrameGen: cambio G" + mFgVersion + " → G" + version);
                    boolean wasActive = mFrameGenMode;
                    stopAllGen();
                    mFrameGenMode = false;
                    mFgVersion = version;
                    mG3WarnedTypeMismatch = false;
                    if (wasActive) {

                    }
                }
            });
    }

    private void tickFps() {
        long now = System.nanoTime();
        mFrameTimesNs[mFrameTimeIdx] = now;
        mFrameTimeIdx = (mFrameTimeIdx + 1) % FPS_WINDOW;

        int count = 0;
        long oldest = Long.MAX_VALUE;
        long newest = 0;
        for (int i = 0; i < FPS_WINDOW; i++) {
            long t = mFrameTimesNs[i];
            if (t > 0) {
                count++;
                if (t < oldest) oldest = t;
                if (t > newest) newest = t;
            }
        }
        if (count >= 2 && oldest != Long.MAX_VALUE && newest > oldest) {
            float elapsedSec = (newest - oldest) / 1_000_000_000f;
            if (elapsedSec > 0f) mFps = (count - 1) / elapsedSec;
        } else {
            mFps = -1f;
        }
    }

    public void setTestMode(final boolean testMode) {
        mTestMode = testMode;
        mGlHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (testMode) {
                        if (!mTestLoopRunning) {
                            mTestLoopRunning = true;
                            mGlHandler.post(mTestFrameRunnable);
                        }
                    } else {
                        mTestLoopRunning = false;
                        mGlHandler.removeCallbacks(mTestFrameRunnable);
                    }
                }
            });
    }

    private volatile boolean mTestMode = false;
    private boolean mTestLoopRunning = false;
    private final Runnable mTestFrameRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mTestMode || mHolder == null) {
                mTestLoopRunning = false;
                return;
            }
            receiveFrame(testFrame());
            mGlHandler.postDelayed(this, FRAME_DELAY_MS);
        }
    };

    private Bitmap testFrame() {
        Rect frameRect = mHolder.getSurfaceFrame();
        int w = frameRect.width()  > 0 ? frameRect.width()  : 720;
        int h = frameRect.height() > 0 ? frameRect.height() : 1280;

        Bitmap bmp = BmpPool.acquire(w, h);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.BLACK);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTextSize(h / 4f);
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float textY = (h / 2f) - (fm.ascent + fm.descent) / 2f;
        canvas.drawText("1", w / 2f, textY, paint);
        return bmp;
    }

    private boolean isFrameGen(Mod.Type t) {
        return t == Mod.Type.FRAMEGEN || t == Mod.Type.FRAMEGEN_G3;
    }

    private boolean shaderFitsVersion(Mod fg) {
        if (fg == null) return false;
        boolean isG3Shader = (fg.getType() == Mod.Type.FRAMEGEN_G3);
        if (mFgVersion == 3) {
            if (!isG3Shader) {
                if (!mG3WarnedTypeMismatch) {
                    Log.w(TAG, "Modo G3 pero shader '" + fg.getName()
                          + "' no es tipo FG-G3. Framegen inactivo.");
                    mG3WarnedTypeMismatch = true;
                }
                return false;
            }
        } else {
            if (isG3Shader) {
                if (!mG3WarnedTypeMismatch) {
                    Log.w(TAG, "Modo G" + mFgVersion + " pero shader '" + fg.getName()
                          + "' es tipo FG-G3. Framegen inactivo.");
                    mG3WarnedTypeMismatch = true;
                }
                return false;
            }
        }
        return true;
    }

    public void receiveFrame(final Bitmap frame) {
        if (frame == null) return;
        mGlHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mHolder == null || !mGlReady || mPainter == null) {
                        BmpPool.release(frame);
                        return;
                    }
                    syncGen();
                    if (!mFrameGenMode) {
                        frameNormal(frame);
                        return;
                    }
                    if (mFgVersion == 3) {
                        frameG3(frame);
                    } else if (mFgVersion == 1) {
                        frameG1(frame);
                    } else {
                        frameG2(frame);
                    }
                }
            });
    }

    private void frameNormal(Bitmap frame) {
        drawNow(frame);
        tickFps();

        if (hasAnimatedMod()) startNormalLoop();
        else                    stopNormalLoop();
    }

    private void frameG2(Bitmap frame) {
        long now = System.nanoTime();
        if (mLastInputNs > 0L) {
            long delta = now - mLastInputNs;
            if (delta > 0L && delta < 2_000_000_000L) {
                mInputIntervalNs = (mInputIntervalNs * 3L + delta) / 4L;
            }
        }
        mLastInputNs = now;

        int freeSlot;
        if (mG2SlotC < 0)         freeSlot = 0;
        else if (mG2SlotA < 0)    freeSlot = (mG2SlotC + 1) % 3;
        else                      freeSlot = 3 - mG2SlotA - mG2SlotC;

        if (mPainter == null) { BmpPool.release(frame); return; }
        mPainter.uploadSlot(freeSlot, frame);

        mG2SlotA = mG2SlotC;
        mG2SlotC = freeSlot;
        mG2FakeReady = false;

        if (mG2SlotA >= 0 && !mG2GenRunning) {
            mG2GenRunning = true;
            mGlHandler.post(mG2GenRunnable);
        }
    }

    private final Runnable mG2GenRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mFrameGenMode || mFgVersion != 2
                || !mGlReady || mHolder == null || mPainter == null) {
                mG2GenRunning = false;
                return;
            }

            final int a = mG2SlotA;
            final int c = mG2SlotC;
            if (a < 0 || c < 0) { mG2GenRunning = false; return; }

            Mods mm = mMods;
            Mod fgMod = (mm != null) ? mm.getActiveFgMod() : null;

            float mix = 0.5f;
            if (fgMod != null) {
                Float mv = fgMod.getParams().get("uMix");
                if (mv != null) mix = mv;
            }

            GlProgram fgShader = null;
            Map<String, Float> fgParams = null;
            if (fgMod != null && fgMod.isEnabled()) {
                fgShader = fgMod.getGlProgram();
                if (fgShader != null) {
                    fgParams = new HashMap<String, Float>(fgMod.getParams());
                    fgParams.put("uTime", (System.currentTimeMillis() - mStartTimeMs) / 1000f);
                    fgParams.put("uFrameGen", 1f);
                }
            }

            boolean ok;
            if (fgShader != null) {
                ok = mPainter.blendShaderInto(
                    a, c, mix, fgShader, fgParams, FG_FAKE_SLOT);
            } else {
                ok = mPainter.blendInto(a, c, mix, FG_FAKE_SLOT);
            }

            mG2FakeReady = ok;
            mG2GenRunning = false;

            if (mG2SlotA >= 0 && mG2SlotC >= 0 && mG2SlotA != a && !mG2FakeReady) {
                mG2GenRunning = true;
                mGlHandler.post(this);
            }
        }
    };

    private final Runnable mG2PresRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mFrameGenMode || mFgVersion != 2
                || !mGlReady || mHolder == null || mPainter == null) {
                mG2PresRunning = false;
                return;
            }

            int slotToShow = -1;
            if (mG2ShowFake && mG2FakeReady) {
                slotToShow = FG_FAKE_SLOT;
                mG2FakeReady = false;
                mG2ShowFake = false;
            } else if (mG2SlotC >= 0) {
                slotToShow = mG2SlotC;
                mG2ShowFake = true;
            }

            if (slotToShow >= 0) {
                mPainter.drawSlot(slotToShow, null, null);
                tickFps();
                mG2UnderrunStreak = 0;
            } else {
                mG2UnderrunStreak++;
                if (mG2UnderrunStreak == UNDERRUN_LOG_THRESHOLD) {
                    Log.w(TAG, "G2 underrun x" + UNDERRUN_LOG_THRESHOLD);
                }
            }

            mGlHandler.postDelayed(this, presentDelayMs());
        }
    };

    private void frameG1(Bitmap frame) {
        long now = System.nanoTime();
        if (mLastInputNs > 0L) {
            long delta = now - mLastInputNs;
            if (delta > 0L && delta < 2_000_000_000L) {
                mInputIntervalNs = (mInputIntervalNs * 3L + delta) / 4L;
            }
        }
        mLastInputNs = now;

        int freeSlot;
        if (mG1SlotC < 0)         freeSlot = 0;
        else if (mG1SlotA < 0)    freeSlot = (mG1SlotC + 1) % 3;
        else                      freeSlot = 3 - mG1SlotA - mG1SlotC;

        if (mPainter == null) { BmpPool.release(frame); return; }
        mPainter.uploadSlot(freeSlot, frame);

        mG1SlotA = mG1SlotC;
        mG1SlotC = freeSlot;

        if (mG1SlotA >= 0 && !mG1GenRunning) {
            mG1GenRunning = true;
            mGlHandler.post(mG1GenRunnable);
        }
    }

    private final Runnable mG1GenRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mFrameGenMode || mFgVersion != 1
                || !mGlReady || mHolder == null || mPainter == null) {
                mG1GenRunning = false;
                return;
            }

            final int a = mG1SlotA;
            final int c = mG1SlotC;
            if (a < 0 || c < 0) { mG1GenRunning = false; return; }

            int fakeSlot = G1_OUT_BASE + ((mG1QueueHead + mG1QueueCount) % G1_OUT_COUNT);

            Mods mm = mMods;
            Mod fgMod = (mm != null) ? mm.getActiveFgMod() : null;

            float mix = 0.5f;
            if (fgMod != null) {
                Float mv = fgMod.getParams().get("uMix");
                if (mv != null) mix = mv;
            }

            GlProgram fgShader = null;
            Map<String, Float> fgParams = null;
            if (fgMod != null && fgMod.isEnabled()) {
                fgShader = fgMod.getGlProgram();
                if (fgShader != null) {
                    fgParams = new HashMap<String, Float>(fgMod.getParams());
                    fgParams.put("uTime", (System.currentTimeMillis() - mStartTimeMs) / 1000f);
                    fgParams.put("uFrameGen", 1f);
                }
            }

            boolean ok;
            if (fgShader != null) {
                ok = mPainter.blendShaderInto(
                    a, c, mix, fgShader, fgParams, fakeSlot);
            } else {
                ok = mPainter.blendInto(a, c, mix, fakeSlot);
            }

            if (ok && mG1QueueCount < G1_QUEUE_SIZE) {
                int tail = (mG1QueueHead + mG1QueueCount) % G1_QUEUE_SIZE;
                int realSlot = G1_OUT_BASE + ((tail + 1) % G1_OUT_COUNT);
                boolean copied = mPainter.drawSlotInto(c, realSlot, null, null);
                if (copied) {
                    mG1QueueC[tail]    = realSlot;
                    mG1QueueFake[tail] = fakeSlot;
                    mG1QueueCount++;
                }
            }

            mG1GenRunning = false;

            if (mG1SlotC != c && mG1SlotA >= 0
                && mG1QueueCount < G1_QUEUE_SIZE) {
                mG1GenRunning = true;
                mGlHandler.post(this);
            }
        }
    };

    private final Runnable mG1PresRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mFrameGenMode || mFgVersion != 1
                || !mGlReady || mHolder == null || mPainter == null) {
                mG1PresRunning = false;
                return;
            }

            if (mG1QueueCount == 0) {
                mG1UnderrunStreak++;
                if (mG1UnderrunStreak == UNDERRUN_LOG_THRESHOLD) {
                    Log.w(TAG, "G1 underrun x" + UNDERRUN_LOG_THRESHOLD);
                }
                mGlHandler.postDelayed(this, 2);
                return;
            }

            int cSlot    = mG1QueueC[mG1QueueHead];
            int fakeSlot = mG1QueueFake[mG1QueueHead];

            int slotToShow;
            if (!mG1ShowingFake) {
                slotToShow = cSlot;
                mG1ShowingFake = true;
            } else {
                slotToShow = fakeSlot;
                mG1ShowingFake = false;
                mG1QueueHead = (mG1QueueHead + 1) % G1_QUEUE_SIZE;
                mG1QueueCount--;
            }

            mPainter.drawSlot(slotToShow, null, null);
            tickFps();
            mG1UnderrunStreak = 0;

            mGlHandler.postDelayed(this, presentDelayMs());
        }
    };

    private void frameG3(Bitmap frameC) {
        long now = System.nanoTime();
        if (mLastInputNs > 0L) {
            long delta = now - mLastInputNs;
            if (delta > 0L && delta < 2_000_000_000L) {
                mInputIntervalNs = (mInputIntervalNs * 3L + delta) / 4L;
            }
        }
        mLastInputNs = now;

        if (mPainter == null) { BmpPool.release(frameC); return; }

        Bitmap cDs = Motion.downsample(frameC);

        if (mG3SlotA < 0) {
            mPainter.uploadSlot(mG3SlotNext, frameC);
            mG3SlotA    = mG3SlotNext;
            mG3SlotNext = (mG3SlotNext == G3_SLOT_A1) ? G3_SLOT_A2 : G3_SLOT_A1;
            if (mG3PrevDs != null) mG3PrevDs.recycle();
            mG3PrevDs = cDs;
            return;
        }

        float dsToOrig = (float) frameC.getWidth() / Motion.DS_W;

        Bitmap motionMap = Motion.estimate(mG3PrevDs, cDs, dsToOrig);

        mPainter.uploadSlot(G3_MOTION_SLOT, motionMap);

        Mods mm = mMods;
        Mod fgMod = (mm != null) ? mm.getActiveFgMod() : null;

        float mix = 1.0f;
        GlProgram fgShader = null;
        Map<String, Float> fgParams = null;
        if (fgMod != null && fgMod.isEnabled()) {
            Float mv = fgMod.getParams().get("uMix");
            if (mv != null) mix = mv;

            fgShader = fgMod.getGlProgram();
            if (fgShader != null) {
                fgParams = new HashMap<String, Float>(fgMod.getParams());
                fgParams.put("uTime", (System.currentTimeMillis() - mStartTimeMs) / 1000f);
                fgParams.put("uFrameGen", 1f);
                fgParams.put("uTexelX", 1.0f / frameC.getWidth());
                fgParams.put("uTexelY", 1.0f / frameC.getHeight());
            }
        }

        int fakeSlot = G1_OUT_BASE + ((mG3QueueHead + mG3QueueCount) % G1_OUT_COUNT);
        boolean ok;
        if (fgShader != null) {
            ok = mPainter.blendShaderInto(
                mG3SlotA, G3_MOTION_SLOT, mix, fgShader, fgParams, fakeSlot);
        } else {
            ok = mPainter.drawSlotInto(mG3SlotA, fakeSlot, null, null);
        }

        if (ok && mG3QueueCount < G3_QUEUE_SIZE) {
            int tail = (mG3QueueHead + mG3QueueCount) % G3_QUEUE_SIZE;
            int realSlot = G1_OUT_BASE + ((tail + 1) % G1_OUT_COUNT);
            boolean copied = mPainter.drawSlotInto(mG3SlotNext, realSlot, null, null);
            if (copied) {
                mG3QueueReal[tail] = realSlot;
                mG3QueueFake[tail] = fakeSlot;
                mG3QueueCount++;
            }
        }

        mPainter.uploadSlot(mG3SlotNext, frameC);

        mG3SlotA    = mG3SlotNext;
        mG3SlotNext = (mG3SlotNext == G3_SLOT_A1) ? G3_SLOT_A2 : G3_SLOT_A1;

        if (mG3PrevDs != null) mG3PrevDs.recycle();
        mG3PrevDs = cDs;
    }

    private final Runnable mG3PresRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mFrameGenMode || mFgVersion != 3
                || !mGlReady || mHolder == null || mPainter == null) {
                mG3PresRunning = false;
                return;
            }

            if (mG3QueueCount == 0) {
                mG3UnderrunStreak++;
                if (mG3UnderrunStreak == UNDERRUN_LOG_THRESHOLD) {
                    Log.w(TAG, "G3 underrun x" + UNDERRUN_LOG_THRESHOLD);
                }
                mGlHandler.postDelayed(this, 2);
                return;
            }

            int realSlot = mG3QueueReal[mG3QueueHead];
            int fakeSlot = mG3QueueFake[mG3QueueHead];

            int slotToShow;
            if (!mG1ShowingFake) {
                slotToShow = fakeSlot;
                mG1ShowingFake = true;
            } else {
                slotToShow = realSlot;
                mG1ShowingFake = false;
                mG3QueueHead = (mG3QueueHead + 1) % G3_QUEUE_SIZE;
                mG3QueueCount--;
            }

            mPainter.drawSlot(slotToShow, null, null);
            tickFps();
            mG3UnderrunStreak = 0;

            mGlHandler.postDelayed(this, presentDelayMs());
        }
    };

    private long presentDelayMs() {
        long half = mInputIntervalNs / 2;
        long target = Math.max(half, mDisplayIntervalNs);
        return Math.max(1L, target / 1_000_000L);
    }

    private void stopAllGen() {
        Log.d(TAG, "stopAllGen");
        mG2PresRunning = false;
        mGlHandler.removeCallbacks(mG2PresRunnable);
        mGlHandler.removeCallbacks(mG2GenRunnable);
        mG2GenRunning = false;
        mG2FakeReady = false;
        mG2SlotA = -1;
        mG2SlotC = -1;
        mG2ShowFake = false;
        mG2UnderrunStreak = 0;

        mG1PresRunning = false;
        mGlHandler.removeCallbacks(mG1PresRunnable);
        mGlHandler.removeCallbacks(mG1GenRunnable);
        mG1GenRunning = false;
        mG1SlotA = -1;
        mG1SlotC = -1;
        mG1QueueHead = 0;
        mG1QueueCount = 0;
        mG1ShowingFake = false;
        mG1UnderrunStreak = 0;

        mG3PresRunning = false;
        mGlHandler.removeCallbacks(mG3PresRunnable);
        mG3QueueHead = 0;
        mG3QueueCount = 0;
        mG3SlotA = -1;
        mG3SlotNext = 0;
        mG3UnderrunStreak = 0;
        if (mG3PrevDs != null) {
            mG3PrevDs.recycle();
            mG3PrevDs = null;
        }
    }

    private void startGen() {
        Log.d(TAG, "startGen G" + mFgVersion);
        mLastInputNs = 0L;
        mInputIntervalNs = DEFAULT_INPUT_INTERVAL_NS;

        if (mFgVersion == 3) {
            mG3SlotA = -1;
            mG3SlotNext = G3_SLOT_A1;
            mG3QueueHead = 0;
            mG3QueueCount = 0;
            mG1ShowingFake = false;
            mG3UnderrunStreak = 0;
            if (mG3PrevDs != null) { mG3PrevDs.recycle(); mG3PrevDs = null; }
            if (!mG3PresRunning) {
                mG3PresRunning = true;
                mGlHandler.removeCallbacks(mG3PresRunnable);
                mGlHandler.post(mG3PresRunnable);
            }
            return;
        }

        if (mFgVersion == 1) {
            mG1SlotA = -1;
            mG1SlotC = -1;
            mG1QueueHead = 0;
            mG1QueueCount = 0;
            mG1ShowingFake = false;
            mG1GenRunning = false;
            mG1UnderrunStreak = 0;
            if (!mG1PresRunning) {
                mG1PresRunning = true;
                mGlHandler.removeCallbacks(mG1PresRunnable);
                mGlHandler.post(mG1PresRunnable);
            }
        } else {
            mG2SlotA = -1;
            mG2SlotC = -1;
            mG2FakeReady = false;
            mG2ShowFake = false;
            mG2GenRunning = false;
            mG2UnderrunStreak = 0;
            if (!mG2PresRunning) {
                mG2PresRunning = true;
                mGlHandler.removeCallbacks(mG2PresRunnable);
                mGlHandler.post(mG2PresRunnable);
            }
        }
    }

    private void syncGen() {
        Mods mm = mMods;
        Mod fg = (mm != null) ? mm.getActiveFgMod() : null;
        boolean haveActiveFg = (fg != null && fg.isEnabled());
        boolean compatible = haveActiveFg && shaderFitsVersion(fg);
        boolean want = compatible;

        if (want == mFrameGenMode) return;

        Log.d(TAG, "syncGen → " + (want ? "ON (G" + mFgVersion + ")" : "OFF"));
        mFrameGenMode = want;

        if (want) {
            stopNormalLoop();
            stopAllGen();
            startGen();
        } else {
            stopAllGen();
            startNormalLoop();
        }
    }

    public void setScaleInfo(Rect captureRect, Rect overlayRect) { }

    private boolean hasAnimatedMod() {
        Mods mm = mMods;
        if (mm == null) return false;
        for (Mod m : mm.getEnabledChain()) {
            if (m.getParamDefs().containsKey("uTime")) return true;
        }
        return false;
    }

    private void startNormalLoop() {
        if (!mRenderLoopRunning) {
            mRenderLoopRunning = true;
            mGlHandler.post(mRenderLoop);
        }
    }

    private void stopNormalLoop() {
        if (mRenderLoopRunning) {
            mRenderLoopRunning = false;
            mGlHandler.removeCallbacks(mRenderLoop);
        }
    }

    public void setHolder(final SurfaceHolder holder) {
        mHolder = holder;
        mGlHandler.post(new Runnable() {
                @Override
                public void run() {
                    stopNormalLoop();
                    stopAllGen();
                    mFrameGenMode = false;
                    if (mPainter == null) return;
                    if (holder != null) {
                        freeAllShaders();
                        mPainter.setSurface(holder);
                        mGlReady = true;
                    } else {
                        mPainter.setSurface(null);
                        mGlReady = false;
                    }
                }
            });
    }

    public void setMods(Mods mods) {
        mMods = mods;
    }

    private void freeAllShaders() {
        Mods mm = mMods;
        if (mm == null) return;
        for (Mod m : mm.getAll()) {
            m.destroyShader();
        }
    }

    public void release() {
        final CountDownLatch latch = new CountDownLatch(1);
        mGlHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        stopNormalLoop();
                        stopAllGen();
                        if (mPainter != null) {
                            freeAllShaders();
                            mPainter.release();
                            mPainter = null;
                        }
                        mGlReady = false;
                    } catch (Exception e) {
                        Log.e(TAG, "Error en release()", e);
                    } finally {
                        latch.countDown();
                    }
                }
            });
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                Log.w(TAG, "release(): timeout esperando al hilo GL");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        mGlThread.quitSafely();
    }
}
