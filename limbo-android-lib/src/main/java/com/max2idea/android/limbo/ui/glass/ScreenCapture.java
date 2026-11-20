package com.max2idea.android.limbo.ui.glass;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.PixelCopy;
import android.view.View;
import android.view.Window;

/** 抓取窗口当前画面作为玻璃的背景来源（PixelCopy 走系统合成，属于 GPU 拷贝）。 */
final class ScreenCapture {

    private static final String TAG = "ScreenCapture";

    interface Callback {
        void onCaptured(Bitmap bitmap, int decorWidth, int decorHeight);
    }

    private ScreenCapture() {
    }

    static void capture(final Window window, final View decor, final Callback cb) {
        if (decor == null || cb == null) return;
        final int w = decor.getWidth();
        final int h = decor.getHeight();
        if (w <= 0 || h <= 0) {
            cb.onCaptured(null, w, h);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && window != null) {
            Bitmap bmp = null;
            try {
                bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                final Bitmap target = bmp;
                PixelCopy.request(window, target, new PixelCopy.OnPixelCopyFinishedListener() {
                    public void onPixelCopyFinished(int result) {
                        if (result == PixelCopy.SUCCESS) {
                            cb.onCaptured(target, w, h);
                        } else {
                            Log.w(TAG, "pixelCopy result=" + result + ", fallback to draw");
                            cb.onCaptured(softwareCopy(decor, w, h), w, h);
                        }
                    }
                }, new Handler(Looper.getMainLooper()));
                return;
            } catch (Throwable t) {
                Log.w(TAG, "pixelCopy unavailable: " + t);
                if (bmp != null) bmp.recycle();
            }
        }
        cb.onCaptured(softwareCopy(decor, w, h), w, h);
    }

    private static Bitmap softwareCopy(View decor, int w, int h) {
        try {
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            decor.draw(new Canvas(bmp));
            return bmp;
        } catch (Throwable t) {
            Log.w(TAG, "software copy failed: " + t);
            return null;
        }
    }

    static void captureActivity(Activity activity, Callback cb) {
        if (activity == null) return;
        capture(activity.getWindow(), activity.getWindow().getDecorView(), cb);
    }

    /**
     * 只抓窗口里的一个矩形区域（窗口坐标），用于底部导航这类小面积玻璃。
     * 面积小、开销低，而且不会把整屏大图留在内存里。
     */
    static void captureRect(final Window window, final View decor, final Rect src, final Callback cb) {
        if (decor == null || src == null || cb == null) return;
        final int w = src.width();
        final int h = src.height();
        if (w <= 0 || h <= 0) {
            cb.onCaptured(null, w, h);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && window != null) {
            Bitmap bmp = null;
            try {
                bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                final Bitmap target = bmp;
                PixelCopy.request(window, src, target, new PixelCopy.OnPixelCopyFinishedListener() {
                    public void onPixelCopyFinished(int result) {
                        if (result == PixelCopy.SUCCESS) {
                            cb.onCaptured(target, w, h);
                        } else {
                            Log.w(TAG, "pixelCopy rect result=" + result + ", fallback to draw");
                            target.recycle();
                            cb.onCaptured(rectFromDraw(decor, src), w, h);
                        }
                    }
                }, new Handler(Looper.getMainLooper()));
                return;
            } catch (Throwable t) {
                Log.w(TAG, "pixelCopy rect unavailable: " + t);
                if (bmp != null && !bmp.isRecycled()) bmp.recycle();
            }
        }
        cb.onCaptured(rectFromDraw(decor, src), w, h);
    }

    /** 软件抓图后再裁剪出目标矩形。 */
    private static Bitmap rectFromDraw(View decor, Rect src) {
        try {
            int dw = decor.getWidth();
            int dh = decor.getHeight();
            if (dw <= 0 || dh <= 0) return null;
            Bitmap full = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888);
            decor.draw(new Canvas(full));
            int left = Math.max(0, Math.min(src.left, dw - 1));
            int top = Math.max(0, Math.min(src.top, dh - 1));
            int right = Math.max(left + 1, Math.min(src.right, dw));
            int bottom = Math.max(top + 1, Math.min(src.bottom, dh));
            Bitmap crop = Bitmap.createBitmap(full, left, top, right - left, bottom - top);
            full.recycle();
            return crop;
        } catch (Throwable t) {
            Log.w(TAG, "rect draw failed: " + t);
            return null;
        }
    }

    /**
     * 以宿主 Activity 画面为底，再把浮在其上的弹窗（如 DrivesDialogBox）叠上去，
     * 保证玻璃取样到的就是下拉层正下方真实看到的画面。
     */
    static void captureForGlass(final Activity host, final View overlay, final Callback cb) {
        if (host == null) return;
        final View decor = host.getWindow().getDecorView();
        capture(host.getWindow(), decor, new Callback() {
            public void onCaptured(Bitmap base, int dw, int dh) {
                if (base != null && overlay != null && overlay != decor) {
                    try {
                        int[] ol = new int[2];
                        int[] dl = new int[2];
                        overlay.getLocationOnScreen(ol);
                        decor.getLocationOnScreen(dl);
                        Canvas c = new Canvas(base);
                        c.translate(ol[0] - dl[0], ol[1] - dl[1]);
                        overlay.draw(c);
                    } catch (Throwable t) {
                        Log.w(TAG, "overlay composite failed: " + t);
                    }
                }
                cb.onCaptured(base, dw, dh);
            }
        });
    }
}
