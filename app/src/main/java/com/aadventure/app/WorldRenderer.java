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

    private FloatBuffer cubeBuffer;
    private FloatBuffer groundBuffer;

    private final float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] modelMatrix = new float[16];
    private final float[] mvpMatrix = new float[16];

    // --------------------------------------------------
    // FIRST PERSON CAMERA
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
    private static final float MOVE_SPEED = 0.18f;

    // --------------------------------------------------
    // WORLD
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

    // ==================================================
    // OPENGL
    // ==================================================

    @Override
    public void onSurfaceCreated(
            GL10 gl,
            EGLConfig config
    ) {

        /*
         * Sky colour.
         */
        GLES20.glClearColor(
                0.42f,
                0.70f,
                0.92f,
                1.0f
        );

        /*
         * Enable depth.
         */
        GLES20.glEnable(
                GLES20.GL_DEPTH_TEST
        );

        GLES20.glDepthFunc(
                GLES20.GL_LEQUAL
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
        createCube();
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

        /*
         * Perspective camera.
         */
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

        updateMovement();
        updateCamera();

        /*
         * Draw the complete first-person scene.
         */
        drawGround();

        drawWorldObjects();
    }

    // ==================================================
    // CAMERA
    // ==================================================

    private void updateCamera() {

        cameraY = 1.7f;

        float lookDistance = 2.0f;

        float lookX =
                cameraX +
                (float) Math.sin(yaw) *
                lookDistance;

        float lookZ =
                cameraZ -
                (float) Math.cos(yaw) *
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

    // ==================================================
    // MOVEMENT INPUT
    // ==================================================

    public synchronized void addMovement(
            float forward,
            float side
    ) {

        targetMoveForward += forward;
        targetMoveSide += side;

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

        if (yaw > Math.PI * 2f) {
            yaw -= (float) (Math.PI * 2f);
        }

        if (yaw < -Math.PI * 2f) {
            yaw += (float) (Math.PI * 2f);
        }
    }

    // ==================================================
    // SMOOTH MOVEMENT
    // ==================================================

    private synchronized void updateMovement() {

        moveForward +=
                (targetMoveForward - moveForward)
                * MOVEMENT_SMOOTHING;

        moveSide +=
                (targetMoveSide - moveSide)
                * MOVEMENT_SMOOTHING;

        targetMoveForward *= 0.88f;
        targetMoveSide *= 0.88f;

        float forwardX =
                (float) Math.sin(yaw);

        float forwardZ =
                -(float) Math.cos(yaw);

        float rightX =
                (float) Math.cos(yaw);

        float rightZ =
                (float) Math.sin(yaw);

        cameraX +=
                forwardX *
                moveForward *
                MOVE_SPEED;

        cameraZ +=
                forwardZ *
                moveForward *
                MOVE_SPEED;

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

    // ==================================================
    // GROUND
    // ==================================================

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

        buildMVP();

        GLES20.glUniformMatrix4fv(
                mvpMatrixHandle,
                1,
                false,
                mvpMatrix,
                0
        );

        /*
         * Main grass colour.
         */
        GLES20.glUniform4f(
                colorHandle,

                0.24f,
                0.52f,
                0.20f,
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

    // ==================================================
    // CUBE
    // ==================================================

    private void createCube() {

        float[] vertices = {

                // Front
                -0.5f, -0.5f,  0.5f,
                 0.5f, -0.5f,  0.5f,
                 0.5f,  0.5f,  0.5f,

                -0.5f, -0.5f,  0.5f,
                 0.5f,  0.5f,  0.5f,
                -0.5f,  0.5f,  0.5f,

                // Back
                -0.5f, -0.5f, -0.5f,
                -0.5f,  0.5f, -0.5f,
                 0.5f,  0.5f, -0.5f,

                -0.5f, -0.5f, -0.5f,
                 0.5f,  0.5f, -0.5f,
                 0.5f, -0.5f, -0.5f,

                // Left
                -0.5f, -0.5f, -0.5f,
                -0.5f, -0.5f,  0.5f,
                -0.5f,  0.5f,  0.5f,

                -0.5f, -0.5f, -0.5f,
                -0.5f,  0.5f,  0.5f,
                -0.5f,  0.5f, -0.5f,

                // Right
                 0.5f, -0.5f, -0.5f,
                 0.5f,  0.5f, -0.5f,
                 0.5f,  0.5f,  0.5f,

                 0.5f, -0.5f, -0.5f,
                 0.5f,  0.5f,  0.5f,
                 0.5f, -0.5f,  0.5f,

                // Top
                -0.5f,  0.5f, -0.5f,
                -0.5f,  0.5f,  0.5f,
                 0.5f,  0.5f,  0.5f,

                -0.5f,  0.5f, -0.5f,
                 0.5f,  0.5f,  0.5f,
                 0.5f,  0.5f, -0.5f,

                // Bottom
                -0.5f, -0.5f, -0.5f,
                 0.5f, -0.5f, -0.5f,
                 0.5f, -0.5f,  0.5f,

                -0.5f, -0.5f, -0.5f,
                 0.5f, -0.5f,  0.5f,
                -0.5f, -0.5f,  0.5f
        };

        ByteBuffer buffer =
                ByteBuffer.allocateDirect(
                        vertices.length * 4
                );

        buffer.order(
                ByteOrder.nativeOrder()
        );

        cubeBuffer =
                buffer.asFloatBuffer();

        cubeBuffer.put(vertices);
        cubeBuffer.position(0);
    }

    // ==================================================
    // WORLD OBJECTS
    // ==================================================

    private void drawWorldObjects() {

        /*
         * Trees placed in front of the player.
         */

        drawTree(-7f, -12f, 1.3f);
        drawTree(5f, -18f, 1.6f);
        drawTree(-12f, -28f, 1.8f);
        drawTree(13f, -35f, 2.0f);

        drawTree(-22f, -48f, 2.4f);
        drawTree(22f, -55f, 2.5f);

        /*
         * Smaller trees.
         */

        drawTree(8f, -70f, 2.2f);
        drawTree(-18f, -78f, 2.0f);

        /*
         * Rocks.
         */

        drawRock(-3f, -9f, 0.8f);
        drawRock(3f, -15f, 0.6f);
        drawRock(-8f, -23f, 1.0f);
        drawRock(10f, -30f, 0.9f);

        drawRock(-16f, -42f, 1.2f);
        drawRock(17f, -50f, 1.1f);
    }

    // ==================================================
    // TREE
    // ==================================================

    private void drawTree(
            float x,
            float z,
            float scale
    ) {

        /*
         * Trunk.
         */

        drawCube(
                x,
                1.5f * scale,
                z,

                0.7f * scale,
                3.0f * scale,
                0.7f * scale,

                0.34f,
                0.20f,
                0.10f,
                1.0f
        );

        /*
         * Lower leaves.
         */

        drawCube(
                x,
                3.6f * scale,
                z,

                3.2f * scale,
                2.2f * scale,
                3.2f * scale,

                0.10f,
                0.42f,
                0.10f,
                1.0f
        );

        /*
         * Upper leaves.
         */

        drawCube(
                x,
                5.3f * scale,
                z,

                2.4f * scale,
                2.0f * scale,
                2.4f * scale,

                0.12f,
                0.50f,
                0.12f,
                1.0f
        );
    }

    // ==================================================
    // ROCK
    // ==================================================

    private void drawRock(
            float x,
            float z,
            float scale
    ) {

        drawCube(
                x,
                0.45f * scale,
                z,

                1.8f * scale,
                0.9f * scale,
                1.4f * scale,

                0.34f,
                0.34f,
                0.31f,
                1.0f
        );
    }

    // ==================================================
    // DRAW CUBE
    // ==================================================

    private void drawCube(
            float x,
            float y,
            float z,

            float scaleX,
            float scaleY,
            float scaleZ,

            float red,
            float green,
            float blue,
            float alpha
    ) {

        GLES20.glUseProgram(
                program
        );

        Matrix.setIdentityM(
                modelMatrix,
                0
        );

        Matrix.translateM(
                modelMatrix,
                0,
                x,
                y,
                z
        );

        Matrix.scaleM(
                modelMatrix,
                0,
                scaleX,
                scaleY,
                scaleZ
        );

        buildMVP();

        GLES20.glUniformMatrix4fv(
                mvpMatrixHandle,
                1,
                false,
                mvpMatrix,
                0
        );

        GLES20.glUniform4f(
                colorHandle,
                red,
                green,
                blue,
                alpha
        );

        cubeBuffer.position(0);

        GLES20.glEnableVertexAttribArray(
                positionHandle
        );

        GLES20.glVertexAttribPointer(
                positionHandle,
                3,
                GLES20.GL_FLOAT,
                false,
                12,
                cubeBuffer
        );

        GLES20.glDrawArrays(
                GLES20.GL_TRIANGLES,
                0,
                36
        );

        GLES20.glDisableVertexAttribArray(
                positionHandle
        );
    }

    // ==================================================
    // MVP
    // ==================================================

    private void buildMVP() {

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
    }

    // ==================================================
    // LIMITS
    // ==================================================

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

    // ==================================================
    // SHADERS
    // ==================================================

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
