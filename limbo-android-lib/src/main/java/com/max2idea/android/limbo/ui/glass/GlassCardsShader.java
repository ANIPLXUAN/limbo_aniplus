package com.max2idea.android.limbo.ui.glass;

/**
 * 多矩形液态玻璃 GLSL（OpenGL ES 2.0）。
 *
 * 和单块的 GlassShader 完全同一套数学：SDF 算距离 → 梯度反推法线 → 越靠边偏移越大
 * → R/G/B 三通道分开采（红偏外、蓝偏内）→ 1~2px 细白边 → 内部约 1px 轻模糊 → 无阴影。
 *
 * 区别只有一点：这里一次能画 MAX 个圆角矩形（卡片、按钮、工具条、列表项都算一块），
 * 因为整层玻璃和截图是一比一，uv 直接就是屏幕坐标，不需要再拿 capRect 换算。
 * 一块 TextureView、一次 draw、一张纹理 —— 一屏十几块不会各开一个 GL 上下文。
 */
final class GlassCardsShader {

    /** 一屏最多同时画的玻璃块。超出的按可见区域优先。 */
    static final int MAX = 12;

    private GlassCardsShader() {
    }

    static final String VERTEX =
            "attribute vec2 aPos;\n" +
            "varying vec2 vLocal;\n" +
            "void main() {\n" +
            "    vLocal = vec2((aPos.x + 1.0) * 0.5, (1.0 - aPos.y) * 0.5);\n" +
            "    gl_Position = vec4(aPos, 0.0, 1.0);\n" +
            "}\n";

    static final String FRAGMENT = build();

    private static String build() {
        StringBuilder sb = new StringBuilder();

        sb.append("precision highp float;\n");
        sb.append("varying vec2 vLocal;\n");
        sb.append("uniform sampler2D uTex;\n");
        sb.append("uniform vec2  uSize;\n");
        sb.append("uniform vec4  uRects[").append(MAX).append("];\n");  // x,y,w,h（px）
        sb.append("uniform float uRadii[").append(MAX).append("];\n");
        sb.append("uniform float uMasks[").append(MAX).append("];\n");
        sb.append("uniform float uBlur;\n");
        sb.append("uniform float uRefract;\n");
        sb.append("uniform float uZoom;\n");
        sb.append("uniform float uChroma;\n");
        sb.append("uniform float uRimWidth;\n");
        sb.append("uniform float uRim;\n");
        sb.append("uniform vec3  uTint;\n");
        sb.append("uniform float uTintAmount;\n");
        sb.append("uniform vec2  uLight;\n");
        sb.append("uniform vec4  uBBox;\n");                              // 所有玻璃块的并集包围盒

        sb.append("\n");
        sb.append("float sdRoundRect(vec2 p, vec2 b, float r) {\n");
        sb.append("    vec2 q = abs(p) - (b - vec2(r));\n");
        sb.append("    return length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - r;\n");
        sb.append("}\n");

        sb.append("\n");
        sb.append("vec2 sdfNormal(vec2 q, vec2 b, float r) {\n");
        sb.append("    float e = 0.75;\n");
        sb.append("    float dx = sdRoundRect(q + vec2(e, 0.0), b, r) - sdRoundRect(q - vec2(e, 0.0), b, r);\n");
        sb.append("    float dy = sdRoundRect(q + vec2(0.0, e), b, r) - sdRoundRect(q - vec2(0.0, e), b, r);\n");
        sb.append("    vec2 g = vec2(dx, dy);\n");
        sb.append("    float len = length(g);\n");
        sb.append("    if (len < 0.0001) return vec2(0.0, 0.0);\n");
        sb.append("    return g / len;\n");
        sb.append("}\n");

        sb.append("\n");
        sb.append("vec3 sampleSoft(vec2 uv, vec2 bl) {\n");
        sb.append("    vec3 c = texture2D(uTex, uv).rgb * 0.40;\n");
        sb.append("    c += texture2D(uTex, uv + vec2(bl.x, 0.0)).rgb * 0.15;\n");
        sb.append("    c += texture2D(uTex, uv - vec2(bl.x, 0.0)).rgb * 0.15;\n");
        sb.append("    c += texture2D(uTex, uv + vec2(0.0, bl.y)).rgb * 0.15;\n");
        sb.append("    c += texture2D(uTex, uv - vec2(0.0, bl.y)).rgb * 0.15;\n");
        sb.append("    return c;\n");
        sb.append("}\n");

        sb.append("\n");
        sb.append("void main() {\n");
        sb.append("    vec2 p = vLocal * uSize;\n");
        // 包围盒外直接丢弃：整屏只有玻璃那块在算 SDF，其余像素零开销
        sb.append("    if (p.x < uBBox.x || p.y < uBBox.y || p.x > uBBox.z || p.y > uBBox.w) discard;\n");
        sb.append("    float sdSel = 1.0e9;\n");
        sb.append("    float hit = 0.0;\n");
        sb.append("    vec2 qSel = vec2(0.0);\n");
        sb.append("    vec2 halfSel = vec2(0.0);\n");
        sb.append("    vec2 orgSel = vec2(0.0);\n");
        sb.append("    float radSel = 0.0;\n");

        // 逐块求 SDF，取"最近"的那块；常量下标，符合 GLES2 规则
        for (int i = 0; i < MAX; i++) {
            sb.append("    if (uMasks[").append(i).append("] > 0.5) {\n");
            sb.append("        vec2 half").append(i).append(" = uRects[").append(i).append("].zw * 0.5;\n");
            sb.append("        vec2 q").append(i).append(" = p - uRects[").append(i).append("].xy - half").append(i).append(";\n");
            sb.append("        float sd").append(i).append(" = sdRoundRect(q").append(i).append(", half").append(i)
                    .append(", uRadii[").append(i).append("]);\n");
            sb.append("        if (sd").append(i).append(" < sdSel) {\n");
            sb.append("            sdSel = sd").append(i).append(";\n");
            sb.append("            qSel = q").append(i).append(";\n");
            sb.append("            halfSel = half").append(i).append(";\n");
            sb.append("            orgSel = uRects[").append(i).append("].xy;\n");
            sb.append("            radSel = uRadii[").append(i).append("];\n");
            sb.append("            hit = 1.0;\n");
            sb.append("        }\n");
            sb.append("    }\n");
        }

        sb.append("\n");
        sb.append("    if (hit < 0.5) discard;\n");
        sb.append("    float a = clamp(0.5 - sdSel, 0.0, 1.0);\n");
        sb.append("    if (a <= 0.0) discard;\n");
        sb.append("    float depth = max(-sdSel, 0.0);\n");
        sb.append("    vec2 n = sdfNormal(qSel, halfSel, radSel);\n");

        sb.append("\n");
        sb.append("    float band = max(radSel * 1.25, 14.0);\n");
        sb.append("    float k = clamp(1.0 - depth / band, 0.0, 1.0);\n");
        sb.append("    k = k * k * (3.0 - 2.0 * k);\n");
        sb.append("    k = pow(k, 1.20);\n");

        sb.append("\n");
        // 整层和截图 1:1，uv 直接是像素坐标除尺寸
        sb.append("    vec2 sp = halfSel + qSel * (1.0 - uZoom);\n");
        sb.append("    vec2 uvBase = (orgSel + sp) / uSize;\n");
        sb.append("    vec2 texel = 1.0 / uSize;\n");
        sb.append("    vec2 bl = texel * uBlur;\n");

        sb.append("\n");
        sb.append("    vec2 duvR = -n * (uRefract * (1.0 + uChroma) * k) * texel;\n");
        sb.append("    vec2 duvG = -n * (uRefract * k) * texel;\n");
        sb.append("    vec2 duvB = -n * (uRefract * (1.0 - uChroma) * k) * texel;\n");

        sb.append("\n");
        sb.append("    vec3 col;\n");
        sb.append("    col.r = sampleSoft(uvBase + duvR, bl).r;\n");
        sb.append("    col.g = sampleSoft(uvBase + duvG, bl).g;\n");
        sb.append("    col.b = sampleSoft(uvBase + duvB, bl).b;\n");

        sb.append("\n");
        sb.append("    float luma = dot(col, vec3(0.299, 0.587, 0.114));\n");
        sb.append("    col = mix(vec3(luma), col, 0.95);\n");
        sb.append("    col = mix(col, uTint, uTintAmount);\n");

        sb.append("\n");
        sb.append("    float lit = clamp(dot(n, normalize(uLight)), 0.0, 1.0);\n");
        sb.append("    float rim = smoothstep(uRimWidth, 0.0, depth);\n");
        sb.append("    col += uRim * rim * (0.55 + 0.45 * lit);\n");
        sb.append("    col += smoothstep(uRimWidth * 3.0, 0.0, depth) * 0.06;\n");

        sb.append("\n");
        sb.append("    gl_FragColor = vec4(col * a, a);\n");
        sb.append("}\n");

        return sb.toString();
    }
}
