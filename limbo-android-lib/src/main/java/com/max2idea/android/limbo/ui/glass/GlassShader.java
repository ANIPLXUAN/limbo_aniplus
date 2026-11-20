package com.max2idea.android.limbo.ui.glass;

/**
 * 液态玻璃 GLSL 源码（OpenGL ES 2.0）。
 *
 * 折射是怎么做出来的（对应视频里那三步）：
 *
 *  1) 算距离：sdRoundRect() 是整个面板的圆角矩形 SDF，
 *     每个像素拿到自己到边缘的有符号距离 sd（内部为负）。
 *     法线不再用"最近边"近似，而是拿 SDF 的梯度反推，
 *     所以四个圆角处的折射方向也是连续过渡的，不会在角上跳变。
 *
 *  2) 定偏移：depth = -sd 是进入玻璃的深度。
 *     k = smoothstep(1 - depth/band) 再 pow(1.45)，
 *     中间 k=0（一个字都不动），越靠边 k 越接近 1（扭得最狠），
 *     而且 k 在 depth=band 处导数也趋 0，边界不会出现"折痕"。
 *     位移 = -法线 * uRefract * k，采样点向内偏 → 边缘被压缩，就是凸透镜那圈。
 *
 *  3) 色散：R/G/B 用三个不同的 refract 各采一次（红偏外、蓝偏内），
 *     偏移量本身 ∝ k，所以只有边缘泛红蓝彩边，中间是干净的。
 *
 * 两个细节（视频里强调的）：
 *   - 外圈只加 1~2px 细白边（uRimWidth 控制），受光侧更亮；
 *   - 内部只做极轻模糊（uBlur 通常 1~3px），超过就退化成毛玻璃；
 *   - 不加阴影，加了显脏。
 *
 * 性能：一共 15 次采样（3 通道 × 5 tap），单帧一次性渲染，不跑循环。
 * 参数一律由 Java 侧乘屏幕倍率并封顶 2×（见 GlassStyle）。
 */
final class GlassShader {

    private GlassShader() {
    }

    /** 顶点：全屏三角带，输出面板局部坐标（左上原点，y 向下）。 */
    static final String VERTEX =
            "attribute vec2 aPos;\n" +
            "varying vec2 vLocal;\n" +
            "void main() {\n" +
            "    vLocal = vec2((aPos.x + 1.0) * 0.5, (1.0 - aPos.y) * 0.5);\n" +
            "    gl_Position = vec4(aPos, 0.0, 1.0);\n" +
            "}\n";

    static final String FRAGMENT =
            "precision highp float;\n" +
            "varying vec2 vLocal;\n" +
            "uniform sampler2D uTex;\n" +
            "uniform vec2  uSize;\n" +        // 面板尺寸 px
            "uniform vec4  uCapRect;\n" +      // 面板在背景纹理里的范围 (x0,y0,x1,y1)
            "uniform float uRadius;\n" +       // 圆角 px
            "uniform float uBlur;\n" +         // 内部轻模糊 px（1~3）
            "uniform float uRefract;\n" +      // 边缘最大折射位移 px
            "uniform float uZoom;\n" +         // 整体微小缩放
            "uniform float uChroma;\n" +       // 色散强度 0~1
            "uniform float uRimWidth;\n" +     // 细白边宽度 px（1~2）
            "uniform float uRim;\n" +          // 白边强度
            "uniform vec3  uTint;\n" +
            "uniform float uTintAmount;\n" +
            "uniform vec2  uLight;\n" +
            "\n" +
            "float sdRoundRect(vec2 p, vec2 b, float r) {\n" +
            "    vec2 q = abs(p) - (b - vec2(r));\n" +
            "    return length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - r;\n" +
            "}\n" +
            "\n" +
            // SDF 梯度 = 指向外部的法线；圆角处自动连续
            "vec2 sdfNormal(vec2 q, vec2 b, float r) {\n" +
            "    float e = 0.75;\n" +
            "    float dx = sdRoundRect(q + vec2(e, 0.0), b, r) - sdRoundRect(q - vec2(e, 0.0), b, r);\n" +
            "    float dy = sdRoundRect(q + vec2(0.0, e), b, r) - sdRoundRect(q - vec2(0.0, e), b, r);\n" +
            "    vec2 g = vec2(dx, dy);\n" +
            "    float len = length(g);\n" +
            "    if (len < 0.0001) return vec2(0.0, 0.0);\n" +
            "    return g / len;\n" +
            "}\n" +
            "\n" +
            // 5 tap 轻模糊：1~3px 的量级，够柔但不糊
            "vec3 sampleSoft(vec2 uv, vec2 bl) {\n" +
            "    vec3 c = texture2D(uTex, uv).rgb * 0.40;\n" +
            "    c += texture2D(uTex, uv + vec2(bl.x, 0.0)).rgb * 0.15;\n" +
            "    c += texture2D(uTex, uv - vec2(bl.x, 0.0)).rgb * 0.15;\n" +
            "    c += texture2D(uTex, uv + vec2(0.0, bl.y)).rgb * 0.15;\n" +
            "    c += texture2D(uTex, uv - vec2(0.0, bl.y)).rgb * 0.15;\n" +
            "    return c;\n" +
            "}\n" +
            "\n" +
            "void main() {\n" +
            "    vec2 p = vLocal * uSize;\n" +
            "    vec2 halfSize = uSize * 0.5;\n" +
            "    vec2 q = p - halfSize;\n" +
            "\n" +
            // 1) 距离场 + 1px 抗锯齿
            "    float sd = sdRoundRect(q, halfSize, uRadius);\n" +
            "    float a = clamp(0.5 - sd, 0.0, 1.0);\n" +
            "    if (a <= 0.0) discard;\n" +
            "\n" +
            "    float depth = max(-sd, 0.0);\n" +
            "    vec2 n = sdfNormal(q, halfSize, uRadius);\n" +
            "\n" +
            // 2) 折射强度：中间 0，边缘最大，band 处平滑归零
            "    float band = max(uRadius * 1.25, 14.0);\n" +
            "    float k = clamp(1.0 - depth / band, 0.0, 1.0);\n" +
            "    k = k * k * (3.0 - 2.0 * k);\n" +
            "    k = pow(k, 1.20);\n" +
            "\n" +
            "    vec2 sp = halfSize + q * (1.0 - uZoom);\n" +
            "    vec2 uvBase = mix(uCapRect.xy, uCapRect.zw, sp / uSize);\n" +
            "    vec2 texel = (uCapRect.zw - uCapRect.xy) / uSize;\n" +
            "    vec2 bl = texel * uBlur;\n" +
            "\n" +
            // 3) 三通道分开采样：红偏外、蓝偏内
            "    vec2 duvR = -n * (uRefract * (1.0 + uChroma) * k) * texel;\n" +
            "    vec2 duvG = -n * (uRefract * k) * texel;\n" +
            "    vec2 duvB = -n * (uRefract * (1.0 - uChroma) * k) * texel;\n" +
            "\n" +
            "    vec3 col;\n" +
            "    col.r = sampleSoft(uvBase + duvR, bl).r;\n" +
            "    col.g = sampleSoft(uvBase + duvG, bl).g;\n" +
            "    col.b = sampleSoft(uvBase + duvB, bl).b;\n" +
            "\n" +
            // 玻璃不是滤镜：轻微降饱和 + 少量底色
            "    float luma = dot(col, vec3(0.299, 0.587, 0.114));\n" +
            "    col = mix(vec3(luma), col, 0.95);\n" +
            "    col = mix(col, uTint, uTintAmount);\n" +
            "\n" +
            // 外圈 1~2px 细白边，受光侧更亮
            "    float lit = clamp(dot(n, normalize(uLight)), 0.0, 1.0);\n" +
            "    float rim = smoothstep(uRimWidth, 0.0, depth);\n" +
            "    col += uRim * rim * (0.55 + 0.45 * lit);\n" +
            "\n" +
            // 内侧极薄柔光（做厚度感），不加阴影
            "    col += smoothstep(uRimWidth * 3.0, 0.0, depth) * 0.06;\n" +
            "\n" +
            // 预乘 alpha：边缘 1px 抗锯齿不会出现亮边
            "    gl_FragColor = vec4(col * a, a);\n" +
            "}\n";
}
