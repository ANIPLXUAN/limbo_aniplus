package com.max2idea.android.limbo.ui.glass;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

/**
 * 液态玻璃视觉参数，浅色 / 深色各一套。
 *
 * 所有"像素类"参数（模糊、折射、白边）都按屏幕倍率放大，
 * 但**倍率封顶 2×**——高分屏上不封顶会明显掉帧，这是视频里点名的坑。
 * 圆角是几何尺寸，按 dp 走，不参与封顶，否则高分屏上圆角会变小。
 */
final class GlassStyle {

    float radiusPx;      // 圆角，px（dp 换算，不封顶）
    float blurPx;        // 内部轻模糊，px（1~3，超过就变毛玻璃）
    float refractPx;     // 边缘最大折射位移，px
    float zoom;          // 整体微小缩放，中间内容几乎不动
    float chroma;        // 色散强度，0~1，边缘泛红蓝彩边
    float rimWidthPx;    // 细白边宽度，px（1~2）
    float rim;           // 白边强度
    float[] tint = new float[]{1f, 1f, 1f};
    float tintAmount;    // 底色占比
    float[] light = new float[]{-0.55f, -0.83f}; // 左上受光

    private GlassStyle() {
    }

    static GlassStyle from(Context ctx) {
        float d = ctx.getResources().getDisplayMetrics().density;
        float k = Math.min(d, 2f);            // 视频里"最多乘 2"
        boolean night = (ctx.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        GlassStyle s = new GlassStyle();
        s.radiusPx = 18f * d;
        s.blurPx = 1.2f * k;                  // ≈1px，只做"一点点"模糊
        s.refractPx = 9f * k;                 // 边缘弯折的量
        s.zoom = 0.008f;
        s.chroma = 0.15f;
        s.rimWidthPx = 1.6f * k;
        s.rim = 0.50f;

        if (night) {
            setTint(s, 0x141720);
            s.tintAmount = 0.34f;
            s.chroma = 0.18f;
            s.rim = 0.35f;
        } else {
            setTint(s, 0xFFFFFF);
            s.tintAmount = 0.20f;
        }
        return s;
    }

    private static void setTint(GlassStyle s, int color) {
        s.tint[0] = Color.red(color) / 255f;
        s.tint[1] = Color.green(color) / 255f;
        s.tint[2] = Color.blue(color) / 255f;
    }
}
