package com.max2idea.android.limbo.ui.glass;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Spinner;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * 把工程里所有 Spinner 的下拉层换成液态玻璃，其他界面一律不动。
 *
 * 为什么不用 LayoutInflater Factory 替换 Spinner：
 * AppCompatDelegate 已经装了自己的 Factory2，再 setFactory 会抛
 * "A factory has already been set"，所以改成运行时挂触摸拦截。
 *
 * 触摸策略（"点不动"问题的关键）：
 *   ACTION_DOWN / ACTION_MOVE → 返回 false，让 Spinner 正常进入按下态；
 *   ACTION_UP → 能接管就弹玻璃层并返回 true（吞掉 UP，原生下拉不会出现）；
 *               不能接管（adapter 为空）→ 返回 false，让 Spinner 自己弹原生下拉。
 * 不整段吞事件，也不依赖 Spinner.performClick()。
 */
public final class LiquidGlassSpinnerHook {

    private static final String TAG = "LiquidGlassSpinnerHook";

    /** 已挂过的 Spinner，避免重复 setOnTouchListener。 */
    private static final WeakHashMap<Spinner, Boolean> HOOKED =
            new WeakHashMap<Spinner, Boolean>();

    private static final WeakHashMap<Activity, Boolean> ACTIVITIES =
            new WeakHashMap<Activity, Boolean>();

    private static boolean sLifecycleRegistered;

    private LiquidGlassSpinnerHook() {
    }

    /** Activity 侧入口：onCreate 里调用一次即可。 */
    public static void install(Activity host) {
        if (host == null) return;
        hookActivity(host);
        registerLifecycle(host);
    }

    /**
     * 全局兜底：注册一次 Activity 生命周期回调，
     * 之后新开的 Activity（比如设置页）也自动接管，不用逐个加代码。
     */
    private static void registerLifecycle(Activity host) {
        if (sLifecycleRegistered) return;
        sLifecycleRegistered = true;
        try {
            Application app = host.getApplication();
            if (app == null) {
                sLifecycleRegistered = false;
                return;
            }
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                    try {
                        hookActivity(activity);
                    } catch (Throwable t) {
                        Log.w(TAG, "lifecycle hook failed: " + t);
                    }
                }

                public void onActivityStarted(Activity activity) {
                }

                public void onActivityResumed(Activity activity) {
                }

                public void onActivityPaused(Activity activity) {
                }

                public void onActivityStopped(Activity activity) {
                }

                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                }

                public void onActivityDestroyed(Activity activity) {
                    ACTIVITIES.remove(activity);
                }
            });
        } catch (Throwable t) {
            sLifecycleRegistered = false;
            Log.w(TAG, "register lifecycle failed: " + t);
        }
    }

    private static void hookActivity(final Activity host) {
        if (host == null) return;
        if (ACTIVITIES.containsKey(host)) return;
        ACTIVITIES.put(host, Boolean.TRUE);

        final WeakReference<Activity> ref = new WeakReference<Activity>(host);
        View decor = host.getWindow() != null ? host.getWindow().getDecorView() : null;
        if (decor == null) return;

        decor.post(new Runnable() {
            public void run() {
                Activity a = ref.get();
                if (a == null) return;
                View d = a.getWindow() != null ? a.getWindow().getDecorView() : null;
                if (d == null) return;
                hookAll(a, d, null);
                try {
                    d.getViewTreeObserver().addOnGlobalLayoutListener(
                            new ViewTreeObserver.OnGlobalLayoutListener() {
                                private long last = 0L;

                                public void onGlobalLayout() {
                                    Activity act = ref.get();
                                    if (act == null || act.isFinishing()) return;
                                    long now = System.currentTimeMillis();
                                    // 布局回调很频繁，限流到 200ms 一次，避免反复扫描视图树
                                    if (now - last < 200L) return;
                                    last = now;
                                    View d2 = act.getWindow() != null
                                            ? act.getWindow().getDecorView() : null;
                                    if (d2 != null) hookAll(act, d2, null);
                                }
                            });
                } catch (Throwable t) {
                    Log.w(TAG, "layout listener failed: " + t);
                }
            }
        });
    }

    /** Dialog 侧入口：Dialog 构造末尾调用，只处理这个弹窗里的 Spinner。 */
    public static void installDialog(final Activity host, final Dialog dialog) {
        if (host == null || dialog == null) return;
        final WeakReference<Activity> ref = new WeakReference<Activity>(host);
        final WeakReference<Dialog> dref = new WeakReference<Dialog>(dialog);
        View root = dialog.getWindow() != null ? dialog.getWindow().getDecorView() : null;
        if (root == null) return;

        root.post(new Runnable() {
            public void run() {
                Activity a = ref.get();
                Dialog dlg = dref.get();
                if (a == null || dlg == null) return;
                View r = dlg.getWindow() != null ? dlg.getWindow().getDecorView() : null;
                if (r == null) return;
                // overlay 传弹窗自己，玻璃取样的背景里才会包含弹窗内容
                hookAll(a, r, r);
                try {
                    r.getViewTreeObserver().addOnGlobalLayoutListener(
                            new ViewTreeObserver.OnGlobalLayoutListener() {
                                private long last = 0L;

                                public void onGlobalLayout() {
                                    Activity act = ref.get();
                                    Dialog d2 = dref.get();
                                    if (act == null || act.isFinishing() || d2 == null) return;
                                    if (!d2.isShowing()) return;
                                    long now = System.currentTimeMillis();
                                    if (now - last < 200L) return;
                                    last = now;
                                    View r2 = d2.getWindow() != null
                                            ? d2.getWindow().getDecorView() : null;
                                    if (r2 != null) hookAll(act, r2, r2);
                                }
                            });
                } catch (Throwable t) {
                    Log.w(TAG, "dialog layout listener failed: " + t);
                }
            }
        });
    }

    /** 递归扫视图树，给每个还没挂过的 Spinner 加触摸拦截。 */
    private static void hookAll(Activity host, View v, View overlay) {
        if (v == null) return;
        if (v instanceof Spinner) {
            hookOne(host, (Spinner) v, overlay);
            return;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            int n = g.getChildCount();
            for (int i = 0; i < n; i++) {
                hookAll(host, g.getChildAt(i), overlay);
            }
        }
    }

    private static void hookOne(final Activity host, final Spinner spinner, final View overlay) {
        if (spinner == null) return;
        if (HOOKED.containsKey(spinner)) return;
        HOOKED.put(spinner, Boolean.TRUE);

        spinner.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() != MotionEvent.ACTION_UP) {
                    // 按下 / 移动交给 Spinner 自己处理，保留正常的按下反馈
                    return false;
                }
                if (!LiquidGlassPopup.canHost(spinner)) {
                    // 空列表：不拦截，让 Spinner 自己弹原生下拉
                    return false;
                }
                try {
                    if (LiquidGlassPopup.isShowing(spinner)) {
                        return true;
                    }
                    LiquidGlassPopup.show(host, spinner, overlay);
                } catch (Throwable t) {
                    Log.w(TAG, "show glass drop failed: " + t);
                    // 兜底用公开 API 弹原生式下拉，保证一定能选
                    LiquidGlassPopup.showNative(host, spinner);
                }
                // 吞掉抬起事件，避免系统原生下拉同时出现
                return true;
            }
        });
    }
}
