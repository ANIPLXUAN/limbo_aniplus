package com.max2idea.android.limbo.ui.glass;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;
import android.view.TextureView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 一块全屏 TextureView，一次 draw 里画 MAX 个圆角玻璃（卡片/按钮/工具条/列表项）。
 * 只渲染一帧定格，不跑渲染循环。
 */
final class GlassCardsView extends TextureView implements TextureView.SurfaceTextureListener {

    private static final String TAG = "GlassCardsView";

    private Bitmap backdrop;
    private final float[] rects = new float[GlassCardsShader.MAX * 4];
    private final float[] radii = new float[GlassCardsShader.MAX];
    private final float[] masks = new float[GlassCardsShader.MAX];
    private final float[] bbox = new float[4];
    private int count;
    private GlassStyle style;

    private boolean renderPending;
    private boolean destroyed;

    GlassCardsView(Context context) {
        super(context);
        setOpaque(false);
        setSurfaceTextureListener(this);
    }

    void setData(Bitmap bmp, float[] srcRects, float[] srcRadii, int n, GlassStyle st) {
        this.backdrop = bmp;
        this.count = Math.min(n, GlassCardsShader.MAX);
        for (int i = 0; i < this.count; i++) {
            rects[i * 4] = srcRects[i * 4];
            rects[i * 4 + 1] = srcRects[i * 4 + 1];
            rects[i * 4 + 2] = srcRects[i * 4 + 2];
            rects[i * 4 + 3] = srcRects[i * 4 + 3];
            radii[i] = srcRadii[i];
            masks[i] = 1f;
        }
        for (int i = this.count; i < GlassCardsShader.MAX; i++) {
            masks[i] = 0f;
        }

        // 并集包围盒（带一点余量，避免白边被剔掉）
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE;
        float x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (int i = 0; i < this.count; i++) {
            float lx = rects[i * 4];
            float ly = rects[i * 4 + 1];
            float lw = rects[i * 4 + 2];
            float lh = rects[i * 4 + 3];
            if (lx < x0) x0 = lx;
            if (ly < y0) y0 = ly;
            if (lx + lw > x1) x1 = lx + lw;
            if (ly + lh > y1) y1 = ly + lh;
        }
        bbox[0] = x0 - 2f;
        bbox[1] = y0 - 2f;
        bbox[2] = x1 + 2f;
        bbox[3] = y1 + 2f;

        this.style = st;
        requestRender();
    }

    private void requestRender() {
        if (renderPending || destroyed) return;
        renderPending = true;
        post(new Runnable() {
            public void run() {
                renderPending = false;
                if (!isAvailable() || destroyed) return;
                if (getWidth() <= 0 || getHeight() <= 0) return;
                if (backdrop == null || style == null || count == 0) return;
                try {
                    drawOnce();
                    invalidate();
                } catch (Throwable t) {
                    Log.w(TAG, "glass cards render failed: " + t);
                }
            }
        });
    }

    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        destroyed = false;
        requestRender();
    }

    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        requestRender();
    }

    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        destroyed = true;
        return true;
    }

    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    /* ---------------- GLES2 ---------------- */

    private void drawOnce() {
        final int w = getWidth();
        final int h = getHeight();

        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (display == EGL14.EGL_NO_DISPLAY) return;
        int[] version = new int[2];
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return;

        EGLContext context = null;
        EGLSurface surface = null;
        try {
            int[] cfgAttrs = new int[]{
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_DEPTH_SIZE, 0,
                    EGL14.EGL_STENCIL_SIZE, 0,
                    EGL14.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            if (!EGL14.eglChooseConfig(display, cfgAttrs, 0, configs, 0, 1, numConfigs, 0)
                    || numConfigs[0] == 0) return;

            int[] ctxAttrs = new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttrs, 0);
            if (context == null || context == EGL14.EGL_NO_CONTEXT) return;

            SurfaceTexture stex = getSurfaceTexture();
            if (stex == null) return;
            stex.setDefaultBufferSize(w, h);

            int[] surfAttrs = new int[]{EGL14.EGL_NONE};
            surface = EGL14.eglCreateWindowSurface(display, configs[0], stex, surfAttrs, 0);
            if (surface == null || surface == EGL14.EGL_NO_SURFACE) return;
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return;

            int program = buildProgram();
            if (program == 0) return;

            GLES20.glViewport(0, 0, w, h);
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            GLES20.glDisable(GLES20.GL_BLEND);
            GLES20.glClearColor(0f, 0f, 0f, 0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(program);

            int[] tex = new int[1];
            GLES20.glGenTextures(1, tex, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, backdrop, 0);

            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTex"), 0);
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uSize"), w, h);

            FloatBuffer rb = floatBuffer(rects);
            GLES20.glUniform4fv(GLES20.glGetUniformLocation(program, "uRects"), GlassCardsShader.MAX, rb);
            GLES20.glUniform1fv(GLES20.glGetUniformLocation(program, "uRadii"), GlassCardsShader.MAX, floatBuffer(radii));
            GLES20.glUniform1fv(GLES20.glGetUniformLocation(program, "uMasks"), GlassCardsShader.MAX, floatBuffer(masks));

            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uBlur"), style.blurPx);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uRefract"), style.refractPx);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uZoom"), style.zoom);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uChroma"), style.chroma);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uRimWidth"), style.rimWidthPx);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uRim"), style.rim);
            GLES20.glUniform3f(GLES20.glGetUniformLocation(program, "uTint"),
                    style.tint[0], style.tint[1], style.tint[2]);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uTintAmount"), style.tintAmount);
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uLight"),
                    style.light[0], style.light[1]);
            GLES20.glUniform4f(GLES20.glGetUniformLocation(program, "uBBox"),
                    bbox[0], bbox[1], bbox[2], bbox[3]);

            FloatBuffer quad = ByteBuffer.allocateDirect(8 * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            quad.put(new float[]{-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f}).position(0);

            int hPos = GLES20.glGetAttribLocation(program, "aPos");
            GLES20.glEnableVertexAttribArray(hPos);
            GLES20.glVertexAttribPointer(hPos, 2, GLES20.GL_FLOAT, false, 0, quad);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glDisableVertexAttribArray(hPos);

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glDeleteTextures(1, tex, 0);

            EGL14.eglSwapBuffers(display, surface);
            GLES20.glDeleteProgram(program);
        } finally {
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT);
                if (surface != null && surface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(display, surface);
                }
                if (context != null && context != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(display, context);
                }
                EGL14.eglTerminate(display);
            }
        }
    }

    private static FloatBuffer floatBuffer(float[] data) {
        FloatBuffer fb = ByteBuffer.allocateDirect(data.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        fb.put(data).position(0);
        return fb;
    }

    private static int buildProgram() {
        int vs = compile(GLES20.GL_VERTEX_SHADER, GlassCardsShader.VERTEX);
        int fs = compile(GLES20.GL_FRAGMENT_SHADER, GlassCardsShader.FRAGMENT);
        if (vs == 0 || fs == 0) {
            if (vs != 0) GLES20.glDeleteShader(vs);
            if (fs != 0) GLES20.glDeleteShader(fs);
            return 0;
        }
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);
        int[] status = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);
        if (status[0] != GLES20.GL_TRUE) {
            Log.w(TAG, "link failed: " + GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            return 0;
        }
        return program;
    }

    private static int compile(int type, String src) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, src);
        GLES20.glCompileShader(shader);
        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] != GLES20.GL_TRUE) {
            Log.w(TAG, "compile failed: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }
}
