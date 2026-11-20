package com.max2idea.android.limbo.ui.glass;

import android.app.Activity;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * 给底部导航栏（layout/bottom_nav.xml 里 id=bottom_nav 的那个 FrameLayout）铺一层液态玻璃。
 *
 * 做法：
 *   1. 找到 bottom_nav，取它里面那根导航条（第一个子 View）的 LayoutParams
 *      （这样外层留的 margin 会被完整继承，玻璃和导航条严丝合缝）；
 *   2. 把 LiquidGlassView 插到 index 0，也就是压在导航条下面；
 *   3. 导航条和容器的背景改成透明，让玻璃透出来；
 *   4. 布局完成后抓一次窗口画面，算出这块区域的取样范围，渲染一帧就定格。
 *
 * 底部导航背后的画面是静止的（列表没有延伸到它下面滚动），
 * 所以只渲染一帧就够，不会持续占用 GPU。需要重画时手动调 refresh()。
 */
public final class LiquidGlassBottomNav {

    private static final String TAG = "LiquidGlassBottomNav";

    /** 布局里底部导航的 id 名，运行时按名字取，避免依赖 R.id。 */
    private static final String NAV_ID_NAME = "bottom_nav";

    private static final WeakHashMap<View, State> STATES = new WeakHashMap<View, State>();

    private LiquidGlassBottomNav() {
    }

    private static final class State {
        LiquidGlassView glass;
        View bar;
        Bitmap backdrop;
    }

    /**
     * Activity 入口：setContentView 之后调用。
     * 找不到 bottom_nav 时静默返回，不影响其他界面。
     */
    public static void install(Activity host) {
        if (host == null) return;
        final WeakReference<Activity> ref = new WeakReference<Activity>(host);

        View decor = host.getWindow() != null ? host.getWindow().getDecorView() : null;
        if (decor == null) return;

        decor.post(new Runnable() {
            public void run() {
                Activity a = ref.get();
                if (a == null) return;
                View nav = findNav(a);
                if (nav == null) {
                    Log.d(TAG, "bottom_nav not found, skip");
                    return;
                }
                attach(a, nav);
            }
        });
    }

    /** 按 id 名找 bottom_nav；找不到再退回按类名找 BottomNavigationView。 */
    private static View findNav(Activity host) {
        try {
            int id = host.getResources().getIdentifier(
                    NAV_ID_NAME, "id", host.getPackageName());
            if (id != 0) {
                View v = host.findViewById(id);
                if (v != null) return v;
            }
        } catch (Throwable t) {
            Log.w(TAG, "resolve id failed: " + t);
        }
        View decor = host.getWindow() != null ? host.getWindow().getDecorView() : null;
        if (decor != null) {
            View v = findBottomNavByClass(decor);
            if (v != null) return v;
        }
        return null;
    }

    private static View findBottomNavByClass(View v) {
        if (v == null) return null;
        String name = v.getClass().getName();
        if (name != null && name.indexOf("BottomNavigationView") >= 0) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View r = findBottomNavByClass(g.getChildAt(i));
                if (r != null) return r;
            }
        }
        return null;
    }

    private static void attach(final Activity host, View navView) {
        if (STATES.containsKey(navView)) return;
        if (!(navView instanceof ViewGroup)) return;

        ViewGroup nav = (ViewGroup) navView;
        View bar = nav.getChildCount() > 0 ? nav.getChildAt(0) : null;

        ViewGroup.LayoutParams srcLp = bar != null ? bar.getLayoutParams() : null;
        FrameLayout.LayoutParams nlp;
        if (srcLp instanceof ViewGroup.MarginLayoutParams) {
            nlp = new FrameLayout.LayoutParams((ViewGroup.MarginLayoutParams) srcLp);
        } else {
            nlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }

        final LiquidGlassView glass = new LiquidGlassView(nav.getContext());
        nav.addView(glass, 0, nlp);

        // 容器与导航条都改成透明，让玻璃透出来
        nav.setBackgroundColor(Color.TRANSPARENT);
        if (bar != null) {
            bar.setBackgroundColor(Color.TRANSPARENT);
            bar.setElevation(0f);
        }

        // 视频第一节的动作：按下压扁、松手弹簧回弹
        hookPress(nav);

        final State st = new State();
        st.glass = glass;
        st.bar = bar;
        STATES.put(navView, st);

        // 等玻璃真正量出宽高后再抓背景，否则拿到的是 0x0 区域
        glass.getViewTreeObserver().addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    public void onGlobalLayout() {
                        if (glass.getWidth() <= 0 || glass.getHeight() <= 0) return;
                        if (!glass.isAttachedToWindow()) return;
                        try {
                            glass.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        } catch (Throwable ignored) {
                        }
                        refreshInternal(host, st);
                    }
                });
    }

    /* ---------------- 按下压扁 / 松手回弹（视频第一节） ---------------- */

    /** 弹簧刚度，越大回得越快。 */
    private static final float SPRING_STIFFNESS = 300f;
    /** 阻尼比，0.5 ≈ 冲过头再晃两下停住。 */
    private static final float SPRING_ZETA = 0.5f;
    private static final long SPRING_MS = 520L;

    /** 归一化弹簧曲线：t=0 时 1，之后衰减振荡到 0，中间会穿过 0（即过冲）。 */
    private static float springU(float t) {
        float w0 = (float) Math.sqrt(SPRING_STIFFNESS);
        float wd = w0 * (float) Math.sqrt(1f - SPRING_ZETA * SPRING_ZETA);
        float e = (float) Math.exp(-SPRING_ZETA * w0 * t);
        return e * ((float) Math.cos(wd * t)
                + (SPRING_ZETA * w0 / wd) * (float) Math.sin(wd * t));
    }

    /** 整块导航条（含玻璃）一起压扁：横向变宽、纵向变矮，锚点放底边，看起来是"被手指按下去"。 */
    private static void pressDown(View nav) {
        if (nav == null) return;
        cancelSpring(nav);
        nav.setPivotX(nav.getWidth() / 2f);
        nav.setPivotY(nav.getHeight());
        nav.setScaleX(1.05f);
        nav.setScaleY(0.90f);
    }

    private static void releaseUp(final View nav) {
        if (nav == null) return;
        cancelSpring(nav);
        final float sx0 = nav.getScaleX();
        final float sy0 = nav.getScaleY();

        final ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(SPRING_MS);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                float t = (SPRING_MS / 1000f) * ((Float) a.getAnimatedValue()).floatValue();
                float u = springU(t);
                nav.setScaleX(1f + (sx0 - 1f) * u);
                nav.setScaleY(1f + (sy0 - 1f) * u);
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator a) {
                nav.setScaleX(1f);
                nav.setScaleY(1f);
                nav.setTag(null);
            }
        });
        nav.setTag(anim);
        anim.start();
    }

    private static void cancelSpring(View nav) {
        Object tag = nav.getTag();
        if (tag instanceof ValueAnimator) {
            try {
                ((ValueAnimator) tag).cancel();
            } catch (Throwable ignored) {
            }
            nav.setTag(null);
        }
    }

    /**
     * 给导航条里每个可点区域挂触摸监听。
     * 一律 return false，点击事件照常走原逻辑，只是借 DOWN/UP 做形变。
     */
    private static void hookPress(View v) {
        if (v == null) return;
        v.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View view, MotionEvent e) {
                View nav = findNavRoot(view);
                if (nav == null) return false;
                int action = e.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    pressDown(nav);
                } else if (action == MotionEvent.ACTION_UP
                        || action == MotionEvent.ACTION_CANCEL) {
                    releaseUp(nav);
                }
                return false;
            }
        });
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                hookPress(g.getChildAt(i));
            }
        }
    }

    /** 往上找到带玻璃的那个容器（就是 bottom_nav 本身）。 */
    private static View findNavRoot(View v) {
        View cur = v;
        int guard = 0;
        while (cur != null && guard++ < 16) {
            State st = STATES.get(cur);
            if (st != null && st.glass != null) return cur;
            if (!(cur.getParent() instanceof View)) return null;
            cur = (View) cur.getParent();
        }
        return null;
    }

    /** 外部可手动重画（例如切换页签、换主题后）。 */
    public static void refresh(Activity host) {
        if (host == null) return;
        View nav = findNav(host);
        if (nav == null) return;
        State st = STATES.get(nav);
        if (st == null) {
            attach(host, nav);
            return;
        }
        refreshInternal(host, st);
    }

    private static void refreshInternal(final Activity host, final State st) {
        if (st == null || st.glass == null) return;

        st.glass.setVisibility(View.INVISIBLE);
        if (st.bar != null) st.bar.setVisibility(View.INVISIBLE);

        final GlassStyle style = GlassStyle.from(host);
        final View decor = host.getWindow() != null ? host.getWindow().getDecorView() : null;
        if (decor == null) {
            st.glass.setVisibility(View.VISIBLE);
            if (st.bar != null) st.bar.setVisibility(View.VISIBLE);
            return;
        }

        int[] gl = new int[2];
        st.glass.getLocationInWindow(gl);
        int gw = st.glass.getWidth();
        int gh = st.glass.getHeight();

        ScreenCapture.captureRect(host.getWindow(), decor, new Rect(gl[0], gl[1], gl[0] + gw, gl[1] + gh),
                new ScreenCapture.Callback() {
                    public void onCaptured(Bitmap bitmap, int w, int h) {
                        // 先把导航条放回来，避免长时间空白
                        if (st.bar != null) st.bar.setVisibility(View.VISIBLE);
                        st.glass.setVisibility(View.VISIBLE);
                        if (bitmap == null || bitmap.isRecycled()) return;

                        Bitmap old = st.backdrop;
                        st.backdrop = bitmap;
                        // 区域截图就是这块玻璃的范围，归一化坐标直接 0..1
                        st.glass.setBackdrop(bitmap, 0f, 0f, 1f, 1f, style);

                        if (old != null && !old.isRecycled()) {
                            // 旧图可能还在给上一帧做纹理，延后一拍再回收
                            st.glass.postDelayed(new RecycleTask(old), 200L);
                        }
                    }
                });
    }

    private static final class RecycleTask implements Runnable {
        private final Bitmap bmp;

        RecycleTask(Bitmap bmp) {
            this.bmp = bmp;
        }

        public void run() {
            if (bmp != null && !bmp.isRecycled()) bmp.recycle();
        }
    }
}
