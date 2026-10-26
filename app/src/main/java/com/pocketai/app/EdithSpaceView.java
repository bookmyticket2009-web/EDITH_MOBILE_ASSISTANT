package com.pocketai.app;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class EdithSpaceView extends GLSurfaceView {

    public EdithSpaceView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        setRenderer(new OrbRenderer());
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        setFocusable(false);
        setFocusableInTouchMode(false);
    }

    private static final class OrbRenderer implements GLSurfaceView.Renderer {

        private static final String VERTEX =
                "attribute vec2 aPosition;" +
                "void main() {" +
                " gl_Position = vec4(aPosition, 0.0, 1.0);" +
                "}";

        private static final String FRAGMENT =
                "precision mediump float;" +
                "uniform vec2 uResolution;" +
                "uniform float uTime;" +
                "void main() {" +
                " vec2 uv = gl_FragCoord.xy / uResolution;" +
                " vec2 p = (uv - 0.5) * 2.0;" +
                " p.x *= uResolution.x / uResolution.y;" +
                " float t = uTime;" +
                " float d = length(p);" +
                " vec3 col = vec3(0.008, 0.012, 0.020);" +
                " col += vec3(0.0, 0.10, 0.14) * exp(-d * 3.0);" +
                " float orbD = length(p - vec2(0.0, 0.08));" +
                " float sphere = 1.0 - smoothstep(0.45, 0.57, orbD);" +
                " float glow = exp(-orbD * 5.0) * 0.24;" +
                " float corona = exp(-abs(orbD - 0.48) * 24.0);" +
                " vec3 orb = vec3(0.02, 0.48, 0.62);" +
                " float light = max(0.0, 1.0 -" +
                " length(p - vec2(-0.19, 0.28)) * 1.7);" +
                " orb += vec3(0.20, 0.72, 0.78) * light;" +
                " float bands = sin(p.y * 75.0 + t * 1.8);" +
                " float scan = smoothstep(0.45, 0.98, bands) * 0.08;" +
                " orb += vec3(0.04, 0.23, 0.28) * scan;" +
                " col += vec3(0.0, 0.32, 0.43) * glow;" +
                " col += vec3(0.05, 0.72, 0.90) * corona * 0.55;" +
                " col = mix(col, orb, sphere * 0.72);" +
                " float ring1 = abs(length(vec2(p.x," +
                " (p.y + 0.08) * 1.75)) - 0.63);" +
                " float ring2 = abs(length(vec2(p.x," +
                " (p.y + 0.08) * 1.75)) - 0.72);" +
                " float r1 = exp(-ring1 * 85.0);" +
                " float r2 = exp(-ring2 * 75.0);" +
                " float sweep = 0.35 + 0.65 *" +
                " (0.5 + 0.5 * sin(atan(p.y + 0.08,p.x)" +
                " * 2.0 - t * 1.2));" +
                " col += vec3(0.03, 0.64, 0.84) * r1 * sweep;" +
                " col += vec3(0.02, 0.30, 0.48) * r2;" +
                " float vignette = 1.0 -" +
                " smoothstep(0.45, 1.45, d) * 0.72;" +
                " col *= vignette;" +
                " gl_FragColor = vec4(col, 1.0);" +
                "}";

        private final FloatBuffer vertices;
        private int program;
        private int positionHandle;
        private int resolutionHandle;
        private int timeHandle;
        private int width = 1;
        private int height = 1;
        private long startTime;

        OrbRenderer() {
            float[] points = {
                    -1f, -1f,
                     1f, -1f,
                    -1f,  1f,
                     1f,  1f
            };
            vertices = ByteBuffer.allocateDirect(points.length * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            vertices.put(points);
            vertices.position(0);
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            GLES20.glClearColor(0.008f, 0.012f, 0.020f, 1f);
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);

            int vs = compile(GLES20.GL_VERTEX_SHADER, VERTEX);
            int fs = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT);
            program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vs);
            GLES20.glAttachShader(program, fs);
            GLES20.glLinkProgram(program);

            int[] result = new int[1];
            GLES20.glGetProgramiv(
                    program, GLES20.GL_LINK_STATUS, result, 0);
            GLES20.glDeleteShader(vs);
            GLES20.glDeleteShader(fs);

            if (result[0] == 0) {
                String log = GLES20.glGetProgramInfoLog(program);
                GLES20.glDeleteProgram(program);
                throw new RuntimeException(
                        "EDITH orb shader link failed: " + log);
            }

            positionHandle = GLES20.glGetAttribLocation(
                    program, "aPosition");
            resolutionHandle = GLES20.glGetUniformLocation(
                    program, "uResolution");
            timeHandle = GLES20.glGetUniformLocation(
                    program, "uTime");
            startTime = System.nanoTime();
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            this.width = Math.max(width, 1);
            this.height = Math.max(height, 1);
            GLES20.glViewport(0, 0, this.width, this.height);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(program);

            float time = (System.nanoTime() - startTime)
                    / 1_000_000_000f;

            GLES20.glUniform2f(resolutionHandle, width, height);
            GLES20.glUniform1f(timeHandle, time);

            vertices.position(0);
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glVertexAttribPointer(
                    positionHandle, 2, GLES20.GL_FLOAT,
                    false, 0, vertices);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glDisableVertexAttribArray(positionHandle);
        }

        private int compile(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);

            int[] status = new int[1];
            GLES20.glGetShaderiv(
                    shader, GLES20.GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) {
                String log = GLES20.glGetShaderInfoLog(shader);
                GLES20.glDeleteShader(shader);
                throw new RuntimeException(
                        "EDITH orb shader compile failed: " + log);
            }
            return shader;
        }
    }
}
