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

    // --------------------------------------------------
    // FIRST-PERSON CAMERA
    // --------------------------------------------------

    private float cameraX = 0f;
    private float cameraY = 1.7f;
    private float cameraZ = 8f;

    private float yaw = 0f;

    // --------------------------------------------------
    // MOVEMENT
    // --------------------------------------------------

    private float targetMoveForward = 0f;
    private float targetMoveSide = 0f;

    private float moveForward = 0f;
    private float moveSide = 0f;

    private static final float MOVEMENT_SMOOTHING = 0.18f;

    // Movement speed.
    private static final float MOVE_SPEED = 0.18f;

    // --------------------------------------------------
    // WORLD LIMITS
    // --------------------------------------------------

    private static final float WORLD_LEFT = -1850f;
    private static final float WORLD_RIGHT = 1850f;

    private static final float WORLD_FRONT = -1250f;
    private static final float WORLD_BACK = 1250f;

    // --------------------------------------------------
    // SHADERS
    // --------------------------------------------------

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

    public WorldRenderer() {
    }

    // --------------------------------------------------
    // OPENGL INITIALIZATION
    // --------------------------------------------------

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

        GLES20.glEnable(
                GLES20.GL_DEPTH_TEST
        );

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
                (float) width /
                (float) height;

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

    // --------------------------------------------------
    // MAIN FRAME
    // --------------------------------------------------

    @Override
    public void onDrawFrame(GL10 gl) {

        GLES20.glClear(
                GLES20.GL_COLOR_BUFFER_BIT |
                GLES20.GL_DEPTH_BUFFER_BIT
        );

        updateMovement();

        updateCamera();

        drawGround();
    }

    // --------------------------------------------------
    // MOVEMENT INPUT
    // --------------------------------------------------

    public synchronized void addMovement(
            float forward,
            float side
    ) {

        targetMoveForward += forward;
        targetMoveSide += side;

        // Prevent very large touch jumps.
        targetMoveForward =
                clamp(
                        targetMoveForward,
                        -4f,
                        4f
                );

        targetMoveSide =
                clamp(
                        targetMoveSide,
                        -4f,
                        4f
                );
    }

    public synchronized void addYaw(
            float amount
    ) {

        yaw += amount;

        // Keep yaw inside a normal range.
        if (yaw > Math.PI * 2f) {
            yaw -= (float)(Math.PI * 2f);
        }

        if (yaw < -Math.PI * 2f) {
            yaw += (float)(Math.PI * 2f);
        }
    }

    // --------------------------------------------------
    // SMOOTH MOVEMENT
    // --------------------------------------------------

    private synchronized void updateMovement() {

        moveForward +=
                (targetMoveForward - moveForward)
                * MOVEMENT_SMOOTHING;

        moveSide +=
                (targetMoveSide - moveSide)
                * MOVEMENT_SMOOTHING;

        // Slowly return input toward zero.
        targetMoveForward *= 0.88f;
        targetMoveSide *= 0.88f;

        // Direction vectors.

        float forwardX =
                (float)Math.sin(yaw);

        float forwardZ =
                -(float)Math.cos(yaw);

        float rightX =
                (float)Math.cos(yaw);

        float rightZ =
                (float)Math.sin(yaw);

        // Forward/back movement.
        cameraX +=
                forwardX *
                moveForward *
                MOVE_SPEED;

        cameraZ +=
                forwardZ *
                moveForward *
                MOVE_SPEED;

        // Left/right movement.
        cameraX +=
                rightX *
                moveSide *
                MOVE_SPEED;

        cameraZ +=
                rightZ *
                moveSide *
                MOVE_SPEED;

        clampCameraPosition();
    }

    // --------------------------------------------------
    // CAMERA
    // --------------------------------------------------

    private void updateCamera() {

        // Fixed first-person eye height.
        cameraY = 1.7f;

        float lookDistance = 1f;

        float lookX =
                cameraX +
                (float)Math.sin(yaw) *
                lookDistance;

        float lookZ =
                cameraZ -
                (float)Math.cos(yaw) *
                lookDistance;

        Matrix.setLookAtM(
                viewMatrix,
                0,

                cameraX,
                cameraY,
                cameraZ,

                lookX,
                cameraY,
                lookZ,

                0f,
                1f,
                0f
        );
    }

    // --------------------------------------------------
    // CAMERA LIMIT
    // --------------------------------------------------

    private void clampCameraPosition() {

        cameraX =
                clamp(
                        cameraX,
                        WORLD_LEFT,
                        WORLD_RIGHT
                );

        cameraZ =
                clamp(
                        cameraZ,
                        WORLD_FRONT,
                        WORLD_BACK
                );
    }

    private float clamp(
            float value,
            float minimum,
            float maximum
    ) {

        return Math.max(
                minimum,
                Math.min(
                        maximum,
                        value
                )
        );
    }

    // --------------------------------------------------
    // GROUND
    // --------------------------------------------------

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

        ByteBuffer buffer =
                ByteBuffer.allocateDirect(
                        vertices.length * 4
                );

        buffer.order(
                ByteOrder.nativeOrder()
        );

        groundBuffer =
                buffer.asFloatBuffer();

        groundBuffer.put(vertices);

        groundBuffer.position(0);
    }

    private void drawGround() {

        GLES20.glUseProgram(
                program
        );

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

    // --------------------------------------------------
    // SHADER
    // --------------------------------------------------

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

        GLES20.glCompileShader(
                shader
        );

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

        int createdProgram =
                GLES20.glCreateProgram();

        GLES20.glAttachShader(
                createdProgram,
                vertexShader
        );

        GLES20.glAttachShader(
                createdProgram,
                fragmentShader
        );

        GLES20.glLinkProgram(
                createdProgram
        );

        return createdProgram;
    }
}
