package com.max2idea.android.limbo.ui.glass;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import java.util.ArrayList;

/**
 * 卡片版液态玻璃：把 border / toolbar_rounded / rounded_button / machine_item_background
 * 这些 XML 背景对应的 View，统一换成真的 SDF 折射玻璃。
 *
 * 做法：
 *   1) 在窗口根视图最底层插一块全屏 GlassCardsView（TextureView + GLES20）；
 *   2) 扫描所有打了 android:tag="glass_card" 的 View，算出它们在窗口里的矩形；
 *   3) 抓一帧"没有这些卡片"的背景（临时把卡片设 INVISIBLE，draw 到 Bitmap，再恢复）；
 *   4) 把矩形、圆角和背景纹理一起丢给 shader，一次 draw 画完全部玻璃块。
 *
 * 所以卡片区域看到的是：背后的真实界面被 SDF 按"越靠边偏得越多"弯折，
 * 边缘红蓝彩边 + 1~2px 白边 + 约 1px 轻模糊，无阴影 —— 跟弹窗、底栏、下拉同一套 shader。
 *
 * 一帧定格，不跑渲染循环；布局变化/滚动结束后延迟重绘一次。
 */
public final class GlassCards {

    private static final String TAG = "GlassCards";
    static final String GLASS_TAG = "glass_card";

    /** 一屏最多上多少块玻璃（超出的丢在视口外的，不额外开销）。 */
    private static final int MAX_VIEWS = GlassCardsShader.MAX;

    private static GlassCardsView view;
    private static Activity activity;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static Runnable pending;
    private static boolean installed;

    private GlassCards() {
    }

    public static void install(Activity act) {
        if (act == null || installed) return;
        try {
            final View decor = act.getWindow().getDecorView();
            if (!(decor instanceof ViewGroup)) return;
            final ViewGroup root = (ViewGroup) decor;

            view = new GlassCardsView(act);
            root.addView(view, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            ViewTreeObserver vto = root.getViewTreeObserver();
            vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                public void onGlobalLayout() {
                    schedule(120);
                }
            });
            vto.addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
                public void onScrollChanged() {
                    schedule(240);
                }
            });

            activity = act;
            installed = true;
            schedule(300);
        } catch (Throwable t) {
            Log.w(TAG, "install failed: " + t);
        }
    }

    private static void schedule(long delay) {
        if (handler == null) return;
        if (pending != null) handler.removeCallbacks(pending);
        pending = new Runnable() {
            public void run() {
                refresh();
            }
        };
        handler.postDelayed(pending, delay);
    }

    private static void refresh() {
        Activity act = activity;
        GlassCardsView gv = view;
        if (act == null || gv == null || !gv.isAttachedToWindow()) return;

        try {
            final ViewGroup root = (ViewGroup) act.getWindow().getDecorView();
            final int rw = root.getWidth();
            final int rh = root.getHeight();
            if (rw <= 0 || rh <= 0) return;

            ArrayList<View> targets = new ArrayList<>();
            collect(root, targets);

            int[] rloc = new int[2];
            root.getLocationOnScreen(rloc);

            float[] rects = new float[MAX_VIEWS * 4];
            float[] radii = new float[MAX_VIEWS];
            int n = 0;

            float density = act.getResources().getDisplayMetrics().density;
            for (int i = 0; i < targets.size() && n < MAX_VIEWS; i++) {
                View v = targets.get(i);
                if (!v.isShown()) continue;
                int[] loc = new int[2];
                v.getLocationOnScreen(loc);
                float x = loc[0] - rloc[0];
                float y = loc[1] - rloc[1];
                float w = v.getWidth();
                float h = v.getHeight();
                if (w <= 0 || h <= 0) continue;
                // 完全在视口外的跳过
                if (y + h <= 0 || y >= rh) continue;
                rects[n * 4] = x;
                rects[n * 4 + 1] = y;
                rects[n * 4 + 2] = w;
                rects[n * 4 + 3] = h;
                radii[n] = Math.min(10f * density, Math.min(w, h) * 0.5f);
                n++;
            }
            if (n == 0) return;

            Bitmap backdrop = capture(root, targets, gv, rw, rh);
            if (backdrop == null) return;

            GlassStyle st = GlassStyle.from(act);
            float k = Math.min(density, 2f);
            // 卡片比弹窗小得多，折射量收一点，不然整块都在"折射带"里反而看不出来
            st.refractPx = 4f * k;
            st.blurPx = 1.2f * k;
            st.rimWidthPx = 1.2f * k;
            st.tintAmount = isNight(act) ? 0.20f : 0.10f;
            st.rim = isNight(act) ? 0.38f : 0.52f;
            st.chroma = 0.16f;

            gv.setData(backdrop, rects, radii, n, st);
            gv.setVisibility(View.VISIBLE);
        } catch (Throwable t) {
            Log.w(TAG, "refresh failed: " + t);
        }
    }

    /** 递归收集打了 glass_card 标签的 View。 */
    private static void collect(View v, ArrayList<View> out) {
        if (v == null) return;
        if (v == view) return;
        Object tag = v.getTag();
        if (tag instanceof String && GLASS_TAG.equals(tag)) {
            out.add(v);
            return;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                collect(g.getChildAt(i), out);
            }
        }
    }

    /** 抓一帧没有卡片自身内容的背景（用 alpha 0 临时隐身，不触发重新布局）。 */
    private static Bitmap capture(ViewGroup root, ArrayList<View> targets,
                                  GlassCardsView gv, int w, int h) {
        float oldGlass = gv.getAlpha();
        float[] oldAlpha = new float[targets.size()];
        try {
            gv.setAlpha(0f);
            for (int i = 0; i < targets.size(); i++) {
                View v = targets.get(i);
                oldAlpha[i] = v.getAlpha();
                v.setAlpha(0f);
            }
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(bmp));
            return bmp;
        } catch (Throwable t) {
            Log.w(TAG, "capture failed: " + t);
            return null;
        } finally {
            for (int i = 0; i < targets.size(); i++) {
                targets.get(i).setAlpha(oldAlpha[i]);
            }
            gv.setAlpha(oldGlass);
        }
    }

    private static boolean isNight(Activity act) {
        return (act.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }
}
