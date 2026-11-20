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
 * 用 GLES2 渲染一帧液态玻璃并定格到 TextureView。
 *
 * 为什么是 TextureView：它是普通 View，参与正常视图合成，天然支持 alpha 与圆角外透出，
 * 不会像 SurfaceView 那样在 Popup / Dialog 里打洞或盖住列表。
 * 下拉内容在弹出期间是静止的，所以只渲染一帧，不跑渲染循环，零持续 GPU 占用。
 */
final class LiquidGlassView extends TextureView implements TextureView.SurfaceTextureListener {

    private static final String TAG = "LiquidGlassView";

    private Bitmap backdrop;
    private float capRectX0, capRectY0, capRectX1, capRectY1;
    private GlassStyle style;

    private boolean renderPending;
    private boolean destroyed;

    LiquidGlassView(Context context) {
        super(context);
        setOpaque(false);
        setSurfaceTextureListener(this);
    }

    void setBackdrop(Bitmap bmp, float x0, float y0, float x1, float y1, GlassStyle st) {
        this.backdrop = bmp;
        this.capRectX0 = x0;
        this.capRectY0 = y0;
        this.capRectX1 = x1;
        this.capRectY1 = y1;
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
                if (getWidth() <= 0 || getHeight() <= 0 || backdrop == null || style == null) return;
                try {
                    drawOnce(backdrop, getWidth(), getHeight(), style);
                    invalidate();
                } catch (Throwable t) {
                    Log.w(TAG, "glass render failed: " + t);
                }
            }
        });
    }

    /* ---------------- TextureView 回调 ---------------- */

    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        // 表面重建（页面切走又切回来、TextureView 重新 attach）后要恢复可渲染状态
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

    private void drawOnce(Bitmap bmp, int w, int h, GlassStyle st) {
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (display == EGL14.EGL_NO_DISPLAY) {
            Log.w(TAG, "no EGL display");
            return;
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            Log.w(TAG, "eglInitialize failed");
            return;
        }

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
                    || numConfigs[0] == 0) {
                Log.w(TAG, "no EGL config");
                return;
            }

            int[] ctxAttrs = new int[]{
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                    EGL14.EGL_NONE
            };
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttrs, 0);
            if (context == null || context == EGL14.EGL_NO_CONTEXT) {
                Log.w(TAG, "no EGL context");
                return;
            }

            SurfaceTexture stex = getSurfaceTexture();
            if (stex == null) return;
            stex.setDefaultBufferSize(w, h);

            int[] surfAttrs = new int[]{EGL14.EGL_NONE};
            surface = EGL14.eglCreateWindowSurface(display, configs[0], stex, surfAttrs, 0);
            if (surface == null || surface == EGL14.EGL_NO_SURFACE) {
                Log.w(TAG, "no EGL surface");
                return;
            }
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                Log.w(TAG, "eglMakeCurrent failed");
                return;
            }

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
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);

            int hTex = GLES20.glGetUniformLocation(program, "uTex");
            int hSize = GLES20.glGetUniformLocation(program, "uSize");
            int hCapRect = GLES20.glGetUniformLocation(program, "uCapRect");
            int hRadius = GLES20.glGetUniformLocation(program, "uRadius");
            int hBlur = GLES20.glGetUniformLocation(program, "uBlur");
            int hRefract = GLES20.glGetUniformLocation(program, "uRefract");
            int hZoom = GLES20.glGetUniformLocation(program, "uZoom");
            int hChroma = GLES20.glGetUniformLocation(program, "uChroma");
            int hRimWidth = GLES20.glGetUniformLocation(program, "uRimWidth");
            int hTint = GLES20.glGetUniformLocation(program, "uTint");
            int hTintAmount = GLES20.glGetUniformLocation(program, "uTintAmount");
            int hLight = GLES20.glGetUniformLocation(program, "uLight");
            int hRim = GLES20.glGetUniformLocation(program, "uRim");

            GLES20.glUniform1i(hTex, 0);
            GLES20.glUniform2f(hSize, w, h);
            GLES20.glUniform4f(hCapRect, capRectX0, capRectY0, capRectX1, capRectY1);
            GLES20.glUniform1f(hRadius, Math.min(st.radiusPx, Math.min(w, h) * 0.5f));
            GLES20.glUniform1f(hBlur, st.blurPx);
            GLES20.glUniform1f(hRefract, st.refractPx);
            GLES20.glUniform1f(hZoom, st.zoom);
            GLES20.glUniform1f(hChroma, st.chroma);
            GLES20.glUniform1f(hRimWidth, st.rimWidthPx);
            GLES20.glUniform3f(hTint, st.tint[0], st.tint[1], st.tint[2]);
            GLES20.glUniform1f(hTintAmount, st.tintAmount);
            GLES20.glUniform2f(hLight, st.light[0], st.light[1]);
            GLES20.glUniform1f(hRim, st.rim);

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

    private static int buildProgram() {
        int vs = compile(GLES20.GL_VERTEX_SHADER, GlassShader.VERTEX);
        int fs = compile(GLES20.GL_FRAGMENT_SHADER, GlassShader.FRAGMENT);
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
