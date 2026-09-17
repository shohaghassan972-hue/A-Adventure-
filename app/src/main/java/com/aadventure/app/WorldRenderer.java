package com.aadventure.app;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class WorldRenderer implements GLSurfaceView.Renderer {

    private int program;

    private int positionHandle;
    private int colorHandle;
    private int mvpMatrixHandle;

    private FloatBuffer groundBuffer;

    private final float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] modelMatrix = new float[16];
    private final float[] mvpMatrix = new float[16];

    // First-person camera position.
    private float cameraX = 0f;
    private float cameraY = 2.0f;
    private float cameraZ = 8f;

    // Camera looks toward negative Z initially.
    private float yaw = 0f;

    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;" +
            "attribute vec4 aPosition;" +
            "void main() {" +
            "    gl_Position = uMVPMatrix * aPosition;" +
            "}";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;" +
            "uniform vec4 uColor;" +
            "void main() {" +
            "    gl_FragColor = uColor;" +
            "}";

    @Override
    public void onSurfaceCreated(
            GL10 gl,
            EGLConfig config
    ) {

        GLES20.glClearColor(
                0.49f,
                0.74f,
                0.34f,
                1.0f
        );

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        program = createProgram(
                VERTEX_SHADER,
                FRAGMENT_SHADER
        );

        positionHandle =
                GLES20.glGetAttribLocation(
                        program,
                        "aPosition"
                );

        colorHandle =
                GLES20.glGetUniformLocation(
                        program,
                        "uColor"
                );

        mvpMatrixHandle =
                GLES20.glGetUniformLocation(
                        program,
                        "uMVPMatrix"
                );

        createGround();
    }

    @Override
    public void onSurfaceChanged(
            GL10 gl,
            int width,
            int height
    ) {

        GLES20.glViewport(
                0,
                0,
                width,
                height
        );

        float ratio =
                (float) width / (float) height;

        Matrix.frustumM(
                projectionMatrix,
                0,
                -ratio,
                ratio,
                -1f,
                1f,
                1f,
                5000f
        );
    }

    @Override
    public void onDrawFrame(GL10 gl) {

        GLES20.glClear(
                GLES20.GL_COLOR_BUFFER_BIT |
                GLES20.GL_DEPTH_BUFFER_BIT
        );

        updateCamera();

        Matrix.setLookAtM(
                viewMatrix,
                0,

                cameraX,
                cameraY,
                cameraZ,

                cameraX + (float)Math.sin(yaw),
                cameraY,
                cameraZ - (float)Math.cos(yaw),

                0f,
                1f,
                0f
        );

        drawGround();
    }

    private void updateCamera() {

        // Camera remains at eye height.
        // Movement will be added in the next step.

        if (cameraY < 1.6f) {
            cameraY = 1.6f;
        }
    }

    private void createGround() {

        float size = 1900f;

        float[] vertices = {

                -size, 0f, -size,
                 size, 0f, -size,
                -size, 0f,  size,

                 size, 0f, -size,
                 size, 0f,  size,
                -size, 0f,  size
        };

        ByteBuffer bb =
                ByteBuffer.allocateDirect(
                        vertices.length * 4
                );

        bb.order(
                ByteOrder.nativeOrder()
        );

        groundBuffer =
                bb.asFloatBuffer();

        groundBuffer.put(vertices);

        groundBuffer.position(0);
    }

    private void drawGround() {

        GLES20.glUseProgram(program);

        Matrix.setIdentityM(
                modelMatrix,
                0
        );

        Matrix.multiplyMM(
                mvpMatrix,
                0,
                viewMatrix,
                0,
                modelMatrix,
                0
        );

        Matrix.multiplyMM(
                mvpMatrix,
                0,
                projectionMatrix,
                0,
                mvpMatrix,
                0
        );

        GLES20.glUniformMatrix4fv(
                mvpMatrixHandle,
                1,
                false,
                mvpMatrix,
                0
        );

        // Green ground.
        GLES20.glUniform4f(
                colorHandle,
                0.30f,
                0.55f,
                0.22f,
                1.0f
        );

        groundBuffer.position(0);

        GLES20.glEnableVertexAttribArray(
                positionHandle
        );

        GLES20.glVertexAttribPointer(
                positionHandle,
                3,
                GLES20.GL_FLOAT,
                false,
                12,
                groundBuffer
        );

        GLES20.glDrawArrays(
                GLES20.GL_TRIANGLES,
                0,
                6
        );

        GLES20.glDisableVertexAttribArray(
                positionHandle
        );
    }

    private int loadShader(
            int type,
            String shaderCode
    ) {

        int shader =
                GLES20.glCreateShader(type);

        GLES20.glShaderSource(
                shader,
                shaderCode
        );

        GLES20.glCompileShader(shader);

        return shader;
    }

    private int createProgram(
            String vertexShaderCode,
            String fragmentShaderCode
    ) {

        int vertexShader =
                loadShader(
                        GLES20.GL_VERTEX_SHADER,
                        vertexShaderCode
                );

        int fragmentShader =
                loadShader(
                        GLES20.GL_FRAGMENT_SHADER,
                        fragmentShaderCode
                );

        int program =
                GLES20.glCreateProgram();

        GLES20.glAttachShader(
                program,
                vertexShader
        );

        GLES20.glAttachShader(
                program,
                fragmentShader
        );

        GLES20.glLinkProgram(
                program
        );

        return program;
    }
          }
