package com.max2idea.android.limbo.ui.glass;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.FrameLayout;

/**
 * 弹窗液态玻璃：套用同一套 GlassShader（SDF 距离 → 边缘折射 → RGB 三通道色散
 * → 1~2px 细白边 → 内部约 1px 轻模糊 → 无阴影）。
 *
 * 为什么之前是一块灰：
 *   弹窗窗口自己已经被刷成透明了，PixelCopy 抓它自己只会抓到一堆透明像素，
 *   shader 采到的就是透明/黑，再叠 tint 就是一块灰板。
 *
 * 正确做法：
 *   抓**弹窗背后 Activity 的画面**拿到真实背景，再按弹窗在屏幕上的位置切出
 *   对应子区域（uCapRect）喂给 shader。玻璃采样的就是它真正盖住的那片界面，
 *   边缘的弯折和彩边才对得上。
 *
 * 玻璃插在窗口根视图最底层，弹窗内容全程可见 —— 不用把内容藏起来再抓图，
 * 所以没有闪烁，也不会"文字被折射两遍"。
 *
 * 用法（只此一种，不接收 Builder，避免 android.app / androidx 两种 Builder 打架）：
 *   AlertDialog dlg = new AlertDialog.Builder(x)...create();
 *   GlassDialogs.applyOnShow(dlg);
 *   dlg.show();
 */
public final class GlassDialogs {

    private static final String TAG = "GlassDialogs";

    private GlassDialogs() {
    }

    /** show 之前调用：show 之后自动把窗口变成液态玻璃。 */
    public static void applyOnShow(final Dialog dialog) {
        if (dialog == null) return;
        try {
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                public void onShow(DialogInterface d) {
                    attach((Dialog) d);
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static void attach(final Dialog dialog) {
        try {
            final Window win = dialog.getWindow();
            if (win == null) return;
            final View decor = win.getDecorView();
            if (!(decor instanceof ViewGroup)) return;
            final ViewGroup root = (ViewGroup) decor;

            // dismiss 后再 show 复用同一个 Dialog：拆掉上次的玻璃重铺
            for (int i = root.getChildCount() - 1; i >= 0; i--) {
                if (root.getChildAt(i) instanceof LiquidGlassView) {
                    root.removeViewAt(i);
                }
            }

            final View content = root.getChildCount() > 0 ? root.getChildAt(0) : null;

            // 窗口背景、内容背景全透明，圆角/白边/折射全部交给 shader
            try {
                win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            } catch (Throwable ignored) {
            }
            if (content != null) {
                try {
                    content.setBackgroundColor(Color.TRANSPARENT);
                } catch (Throwable ignored) {
                }
            }
            // 背后只压暗一点点，玻璃才有东西可折
            try {
                win.setDimAmount(0.10f);
            } catch (Throwable ignored) {
            }

            final LiquidGlassView glass = new LiquidGlassView(dialog.getContext());
            root.addView(glass, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            glass.setVisibility(View.INVISIBLE);

            final Activity host = findActivity(dialog.getContext());

            glass.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        public void onGlobalLayout() {
                            if (glass.getWidth() <= 0 || glass.getHeight() <= 0) return;
                            if (!glass.isAttachedToWindow()) return;
                            try {
                                glass.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            } catch (Throwable ignored) {
                            }
                            // 等窗口入场动画走完再抓，别抓到缩放/淡入的中间帧
                            glass.postDelayed(new Runnable() {
                                public void run() {
                                    if (!glass.isAttachedToWindow()) return;
                                    grab(dialog, glass, host, win, decor, content);
                                }
                            }, 200L);
                        }
                    });
        } catch (Throwable t) {
            Log.w(TAG, "attach failed: " + t);
        }
    }

    /** 抓背景：优先抓弹窗背后真正的 Activity 画面。 */
    private static void grab(final Dialog dialog, final LiquidGlassView glass,
                             final Activity host, final Window dlgWin, final View dlgDecor,
                             final View content) {
        final float density = dialog.getContext().getResources().getDisplayMetrics().density;

        if (host != null && host.getWindow() != null) {
            final View hostDecor = host.getWindow().getDecorView();
            final int hw = hostDecor.getWidth();
            final int hh = hostDecor.getHeight();
            if (hw > 0 && hh > 0) {
                ScreenCapture.capture(host.getWindow(), hostDecor, new ScreenCapture.Callback() {
                    public void onCaptured(Bitmap base, int bw, int bh) {
                        if (base == null || base.isRecycled() || bw <= 0 || bh <= 0) {
                            fallback(dialog, glass, content, density);
                            return;
                        }
                        float[] rect = capRect(dlgDecor, hostDecor, bw, bh);
                        if (rect == null) {
                            fallback(dialog, glass, content, density);
                            return;
                        }
                        paint(dialog, glass, base, rect[0], rect[1], rect[2], rect[3], density);
                    }
                });
                return;
            }
        }

        // 拿不到 Activity（弹窗不是从 Activity 出来的）：退回抓自己窗口，
        // 折边形变还在，只是背景透明 —— 只当最后手段
        ScreenCapture.capture(dlgWin, dlgDecor, new ScreenCapture.Callback() {
            public void onCaptured(Bitmap b, int w, int h) {
                if (b == null || b.isRecycled()) {
                    fallback(dialog, glass, content, density);
                    return;
                }
                paint(dialog, glass, b, 0f, 0f, 1f, 1f, density);
            }
        });
    }

    /** 弹窗在屏幕上的范围 → Activity 截图里的归一化矩形 (x0,y0,x1,y1)。 */
    private static float[] capRect(View dlgDecor, View hostDecor, int bw, int bh) {
        try {
            int[] dl = new int[2];
            int[] hl = new int[2];
            dlgDecor.getLocationOnScreen(dl);
            hostDecor.getLocationOnScreen(hl);
            int dw = dlgDecor.getWidth();
            int dh = dlgDecor.getHeight();
            if (dw <= 0 || dh <= 0) return null;

            float x0 = clamp((dl[0] - hl[0]) / (float) bw);
            float y0 = clamp((dl[1] - hl[1]) / (float) bh);
            float x1 = clamp((dl[0] - hl[0] + dw) / (float) bw);
            float y1 = clamp((dl[1] - hl[1] + dh) / (float) bh);
            if (x1 - x0 < 0.02f || y1 - y0 < 0.02f) return null;
            return new float[]{x0, y0, x1, y1};
        } catch (Throwable t) {
            Log.w(TAG, "capRect failed: " + t);
            return null;
        }
    }

    private static float clamp(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private static void paint(Dialog dialog, LiquidGlassView glass, Bitmap backdrop,
                              float x0, float y0, float x1, float y1, float density) {
        GlassStyle st = GlassStyle.from(dialog.getContext());
        st.radiusPx = 16f * density;                       // 和弹窗圆角一致
        boolean night = isNight(dialog.getContext());
        // 弹窗要透亮，别糊成灰块：底色占比压很低，白边留足
        st.tintAmount = night ? 0.16f : 0.08f;
        st.rim = night ? 0.42f : 0.55f;
        st.chroma = 0.16f;
        st.blurPx = 1.2f * Math.min(density, 2f);          // 约 1px，超过 3px 就变毛玻璃
        glass.setVisibility(View.VISIBLE);
        glass.setBackdrop(backdrop, x0, y0, x1, y1, st);
    }

    /** 抓图彻底失败：给弹窗补一块圆角玻璃色底，至少不是空壳。 */
    private static void fallback(Dialog dialog, LiquidGlassView glass, View content, float density) {
        try {
            glass.setVisibility(View.GONE);
            if (content != null) {
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(isNight(dialog.getContext()) ? 0xF0141720 : 0xF2FFFFFF);
                gd.setCornerRadius(16f * density);
                content.setBackgroundDrawable(gd);
            }
        } catch (Throwable t) {
            Log.w(TAG, "fallback failed: " + t);
        }
    }

    private static boolean isNight(Context ctx) {
        return (ctx.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /** 从 Dialog 的 Context 上溯拿到宿主 Activity。 */
    private static Activity findActivity(Context c) {
        try {
            for (int guard = 0; guard < 8 && c != null; guard++) {
                if (c instanceof Activity) return (Activity) c;
                if (!(c instanceof ContextWrapper)) return null;
                Context next = ((ContextWrapper) c).getBaseContext();
                if (next == c) return null;
                c = next;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
