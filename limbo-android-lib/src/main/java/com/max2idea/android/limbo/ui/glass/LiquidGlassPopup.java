package com.max2idea.android.limbo.ui.glass;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ListPopupWindow;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * 液态玻璃下拉弹层。
 *
 * 层次（下 → 上）：
 *   root  FrameLayout（全屏透明，负责点击外部关闭）
 *     └ panel FrameLayout（圆角兜底底色）
 *         ├ LiquidGlassView（TextureView：整块连续玻璃，只渲染一帧）
 *         └ ListView（全透明，条目浮在玻璃上）
 *
 * 弹出顺序很重要：**先弹出弹层，再异步抓背景贴上玻璃**。
 * 这样即使 PixelCopy 失败，也只是没有磨砂、保留半透圆角底色，
 * 下拉一定可用，不会出现"点了没反应"。
 */
final class LiquidGlassPopup {

    private static final String TAG = "LiquidGlassPopup";

    private static final WeakHashMap<Spinner, WeakReference<Dialog>> OPEN =
            new WeakHashMap<Spinner, WeakReference<Dialog>>();

    /** 正在做退出动画的面板，避免重复 dismiss。 */
    private static final WeakHashMap<View, Boolean> EXITING =
            new WeakHashMap<View, Boolean>();

    private LiquidGlassPopup() {
    }

    static boolean isShowing(Spinner spinner) {
        if (spinner == null) return false;
        WeakReference<Dialog> ref = OPEN.get(spinner);
        if (ref == null) return false;
        Dialog d = ref.get();
        return d != null && d.isShowing();
    }

    static boolean canHost(Spinner spinner) {
        return spinner != null && spinner.getAdapter() != null
                && spinner.getAdapter().getCount() > 0;
    }

    /** 立刻弹出玻璃弹层；背景抓取是随后的增强步骤，失败不影响下拉可用性。 */
    static void show(final Activity host, final Spinner spinner, final View overlay) {
        if (host == null || spinner == null) return;
        if (host.isFinishing()) return;
        if (!canHost(spinner)) return;
        if (isShowing(spinner)) return;

        try {
            build(host, spinner, overlay);
        } catch (Throwable t) {
            Log.w(TAG, "glass popup failed, fallback to native: " + t);
            showNative(host, spinner);
        }
    }

    private static void build(final Activity host, final Spinner spinner, final View overlay) {
        final float d = host.getResources().getDisplayMetrics().density;
        int screenW = host.getResources().getDisplayMetrics().widthPixels;
        int screenH = host.getResources().getDisplayMetrics().heightPixels;

        final SpinnerAdapter src = spinner.getAdapter();
        if (src == null || src.getCount() <= 0) {
            showNative(host, spinner);
            return;
        }
        int count = src.getCount();

        // 逐项实测高度（最多量 12 项后取平均），面板高度贴着内容
        int probeCount = Math.min(count, 12);
        int sumH = 0;
        int maxItemW = 0;
        int ok = 0;
        for (int i = 0; i < probeCount; i++) {
            try {
                View v = src.getDropDownView(i, null, spinner);
                if (v == null) continue;
                v.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                int h = v.getMeasuredHeight();
                if (h > 0) {
                    sumH += h;
                    ok++;
                }
                maxItemW = Math.max(maxItemW, v.getMeasuredWidth());
            } catch (Throwable ignored) {
            }
        }
        int avgH = ok > 0 ? (sumH / ok) : Math.round(46 * d);
        if (avgH <= 0) avgH = Math.round(46 * d);

        int maxH = Math.round(screenH * 0.55f);
        int listH = Math.min(avgH * count, maxH);
        if (listH <= 0) listH = Math.round(46 * d);

        int padV = Math.round(6 * d);
        int panelH = listH + padV * 2;

        int panelW = Math.max(spinner.getWidth(), Math.round(200 * d));
        if (maxItemW > 0) {
            panelW = Math.max(panelW, maxItemW + Math.round(44 * d));
        }
        panelW = Math.min(panelW, screenW - Math.round(16 * d));

        int[] loc = new int[2];
        spinner.getLocationInWindow(loc);
        int px = loc[0];
        int pyBottom = loc[1] + spinner.getHeight() + Math.round(3 * d);
        int pyTop = loc[1] - panelH - Math.round(3 * d);
        int py = (pyBottom + panelH <= screenH - Math.round(8 * d)) ? pyBottom : pyTop;
        if (py < Math.round(8 * d)) py = Math.round(8 * d);
        int pxClamped = Math.max(Math.round(8 * d),
                Math.min(px, screenW - panelW - Math.round(8 * d)));

        final Dialog dialog = new Dialog(host,
                android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        Window win = dialog.getWindow();
        if (win == null) {
            showNative(host, spinner);
            return;
        }
        win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        win.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        win.setDimAmount(0f);
        win.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT);

        FrameLayout root = new FrameLayout(host);
        root.setBackgroundColor(Color.TRANSPARENT);

        final FrameLayout panel = new FrameLayout(host);
        panel.setClipToPadding(false);

        boolean night = (host.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        GradientDrawable fallback = new GradientDrawable();
        fallback.setShape(GradientDrawable.RECTANGLE);
        fallback.setColor(night ? 0xB012151A : 0xB8FFFFFF);
        fallback.setCornerRadius(18f * d);
        panel.setBackground(fallback);

        final GlassStyle style = GlassStyle.from(host);
        final LiquidGlassView glass = new LiquidGlassView(host);
        panel.addView(glass, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ListView list = new ListView(host);
        list.setBackgroundColor(Color.TRANSPARENT);
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setVerticalScrollBarEnabled(false);
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setPadding(Math.round(2 * d), padV, Math.round(2 * d), padV);
        list.setClipToPadding(true);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);

        final DropAdapter adapter = new DropAdapter(src);
        list.setAdapter(adapter);
        panel.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(panelW, panelH);
        lp.leftMargin = pxClamped;
        lp.topMargin = py;
        root.addView(panel, lp);

        final int[] rect = new int[]{pxClamped, py, pxClamped + panelW, py + panelH};
        root.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    float x = e.getX();
                    float y = e.getY();
                    if (x < rect[0] || x > rect[2] || y < rect[1] || y > rect[3]) {
                        exitAnim(panel, dialog);
                        return true;
                    }
                }
                return false;
            }
        });

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                spinner.setSelection(position, true);
                exitAnim(panel, dialog);
            }
        });

        dialog.setContentView(root);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            public void onDismiss(DialogInterface dlg) {
                OPEN.remove(spinner);
            }
        });

        // 关键：先显示，再抓背景。抓不到也照样能用
        dialog.show();

        int sel = spinner.getSelectedItemPosition();
        if (sel >= 0 && sel < adapter.getCount()) {
            list.setSelection(sel);
        }

        panel.post(new AlignPanel(host, root, panel, rect));

        // 入场动效：从 Spinner 那里向下"展开"，220ms
        enterAnim(panel, panelW);

        OPEN.put(spinner, new WeakReference<Dialog>(dialog));

        // 背景抓取（增强）：成功就贴一层真玻璃，失败就保持兜底底色
        ScreenCapture.captureForGlass(host, overlay, new ScreenCapture.Callback() {
            public void onCaptured(Bitmap bmp, int dw, int dh) {
                if (bmp == null || bmp.isRecycled()) return;
                if (!dialog.isShowing()) {
                    bmp.recycle();
                    return;
                }
                panel.post(new BindBackdrop(host, root, panel, glass, bmp, dw, dh, style));
            }
        });
    }

    /**
     * 兜底：用系统公开的 ListPopupWindow 弹一个原生下拉。
     * 比 Spinner.performClick() 可靠——后者在部分 ROM 上根本不打开下拉。
     */
    static void showNative(final Activity host, final Spinner spinner) {
        try {
            final SpinnerAdapter src = spinner.getAdapter();
            if (src == null || src.getCount() <= 0) return;

            final float d = host.getResources().getDisplayMetrics().density;
            final ListPopupWindow lpw = new ListPopupWindow(host);
            lpw.setAnchorView(spinner);
            lpw.setAdapter(new DropAdapter(src));
            lpw.setModal(true);
            lpw.setWidth(Math.max(spinner.getWidth(), Math.round(200 * d)));
            int itemH = Math.round(46 * d);
            int h = Math.min(itemH * src.getCount(),
                    host.getResources().getDisplayMetrics().heightPixels / 2);
            lpw.setHeight(h);
            lpw.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    lpw.dismiss();
                    spinner.setSelection(position, true);
                }
            });
            lpw.show();
        } catch (Throwable t) {
            Log.w(TAG, "native fallback failed: " + t);
        }
    }

    /** 入场：从锚点那里纵向展开 + 淡入。用 ValueAnimator 手控，避免 ViewPropertyAnimator 被重新布局打断。 */
    private static void enterAnim(final View panel, int pivotX) {
        if (panel == null) return;
        cancelAnim(panel);
        panel.setPivotX(pivotX > 0 ? pivotX / 2f : panel.getWidth() / 2f);
        panel.setPivotY(0f);
        panel.setAlpha(0f);
        panel.setScaleX(0.94f);
        panel.setScaleY(0.62f);
        panel.setTranslationY(0f);

        final float dy = panel.getResources().getDisplayMetrics().density;
        final ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(220L);
        anim.setInterpolator(new DecelerateInterpolator(1.8f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                float t = ((Float) a.getAnimatedValue()).floatValue();
                panel.setAlpha(t);
                panel.setScaleX(0.94f + 0.06f * t);
                panel.setScaleY(0.62f + 0.38f * t);
                panel.setTranslationY((1f - t) * 8f * dy);
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator a) {
                panel.setAlpha(1f);
                panel.setScaleX(1f);
                panel.setScaleY(1f);
                panel.setTranslationY(0f);
            }
        });
        panel.setTag(anim);
        anim.start();
    }

    /** 出场：纵向收回去 + 淡出，然后才 dismiss，避免"啪"地消失。 */
    private static void exitAnim(final View panel, final Dialog dialog) {
        if (panel == null || dialog == null) return;
        if (!dialog.isShowing()) return;
        if (EXITING.containsKey(panel)) return;
        EXITING.put(panel, Boolean.TRUE);
        cancelAnim(panel);

        final ValueAnimator anim = ValueAnimator.ofFloat(1f, 0f);
        anim.setDuration(130L);
        anim.setInterpolator(new DecelerateInterpolator(1.4f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                float t = ((Float) a.getAnimatedValue()).floatValue();
                panel.setAlpha(t);
                panel.setScaleY(0.62f + 0.38f * t);
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator a) {
                EXITING.remove(panel);
                try {
                    dialog.dismiss();
                } catch (Throwable ignored) {
                }
            }
        });
        anim.start();
    }

    private static void cancelAnim(View panel) {
        Object tag = panel.getTag();
        if (tag instanceof ValueAnimator) {
            try {
                ((ValueAnimator) tag).cancel();
            } catch (Throwable ignored) {
            }
            panel.setTag(null);
        }
    }

    /** 全屏透明 Dialog 的窗口原点偶尔与 Activity 窗口不一致，这里补偿一次。 */
    private static final class AlignPanel implements Runnable {

        private final Activity host;
        private final View root;
        private final View panel;
        private final int[] rect;

        AlignPanel(Activity host, View root, View panel, int[] rect) {
            this.host = host;
            this.root = root;
            this.panel = panel;
            this.rect = rect;
        }

        public void run() {
            try {
                Window win = host.getWindow();
                if (win == null) return;
                View decor = win.getDecorView();
                if (decor == null) return;

                int[] rl = new int[2];
                int[] dl = new int[2];
                root.getLocationOnScreen(rl);
                decor.getLocationOnScreen(dl);

                int dx = rl[0] - dl[0];
                int dy = rl[1] - dl[1];
                if (dx == 0 && dy == 0) return;

                FrameLayout.LayoutParams lp =
                        (FrameLayout.LayoutParams) panel.getLayoutParams();
                if (lp == null) return;
                lp.leftMargin -= dx;
                lp.topMargin -= dy;
                panel.setLayoutParams(lp);

                rect[0] -= dx;
                rect[2] -= dx;
                rect[1] -= dy;
                rect[3] -= dy;
            } catch (Throwable ignored) {
            }
        }
    }

    /** 用面板实际屏幕坐标换算出背景纹理里的归一化取样范围。 */
    private static final class BindBackdrop implements Runnable {

        private final Activity host;
        private final View root;
        private final View panel;
        private final LiquidGlassView glass;
        private final Bitmap backdrop;
        private final int decorW;
        private final int decorH;
        private final GlassStyle style;

        BindBackdrop(Activity host, View root, View panel, LiquidGlassView glass, Bitmap backdrop,
                     int decorW, int decorH, GlassStyle style) {
            this.host = host;
            this.root = root;
            this.panel = panel;
            this.glass = glass;
            this.backdrop = backdrop;
            this.decorW = decorW;
            this.decorH = decorH;
            this.style = style;
        }

        public void run() {
            try {
                Window win = host.getWindow();
                if (win == null || backdrop == null || backdrop.isRecycled()) return;
                View decor = win.getDecorView();
                if (decor == null) return;

                // 不吃动画变换：用 root 的屏幕坐标 + panel 的布局坐标，
                // 否则入场动画的 scale / translation 会让取样范围偏移几个 dp
                int[] dl = new int[2];
                int[] rl = new int[2];
                decor.getLocationOnScreen(dl);
                root.getLocationOnScreen(rl);

                int dw = decor.getWidth() > 0 ? decor.getWidth() : decorW;
                int dh = decor.getHeight() > 0 ? decor.getHeight() : decorH;
                int pw = panel.getWidth() > 0 ? panel.getWidth() : 1;
                int ph = panel.getHeight() > 0 ? panel.getHeight() : 1;
                if (dw <= 0 || dh <= 0) return;

                int x0 = rl[0] + panel.getLeft();
                int y0 = rl[1] + panel.getTop();

                float nx0 = (float) (x0 - dl[0]) / (float) dw;
                float ny0 = (float) (y0 - dl[1]) / (float) dh;
                float nx1 = (float) (x0 - dl[0] + pw) / (float) dw;
                float ny1 = (float) (y0 - dl[1] + ph) / (float) dh;

                glass.setBackdrop(backdrop, nx0, ny0, nx1, ny1, style);
            } catch (Throwable t) {
                Log.w(TAG, "bind backdrop failed: " + t);
            }
        }
    }

    /** 只把原 Spinner 的下拉视图搬过来，不做任何玻璃处理。 */
    private static final class DropAdapter extends BaseAdapter {

        private final SpinnerAdapter src;

        DropAdapter(SpinnerAdapter src) {
            this.src = src;
        }

        public int getCount() {
            return src.getCount();
        }

        public Object getItem(int position) {
            return src.getItem(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View v = src.getDropDownView(position, convertView, parent);
            if (v != null) {
                v.setBackgroundColor(Color.TRANSPARENT);
            }
            return v;
        }
    }
}
