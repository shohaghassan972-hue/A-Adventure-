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
    private FloatBuffer sphereBuffer;

    private int sphereVertexCount;

    private final float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] modelMatrix = new float[16];
    private final float[] mvpMatrix = new float[16];

    // First-person camera
    private float cameraX = 0f;
    private float cameraY = 1.7f;
    private float cameraZ = 8f;

    private float yaw = 0f;

    // Movement
    private float targetMoveForward = 0f;
    private float targetMoveSide = 0f;

    private float moveForward = 0f;
    private float moveSide = 0f;

    private static final float MOVEMENT_SMOOTHING = 0.18f;
    private static final float MOVE_SPEED = 0.18f;

    // World limits
    private static final float WORLD_LEFT = -1850f;
    private static final float WORLD_RIGHT = 1850f;
    private static final float WORLD_FRONT = -1250f;
    private static final float WORLD_BACK = 1250f;

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
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {

        GLES20.glClearColor(
                0.42f,
                0.70f,
                0.92f,
                1.0f
        );

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);

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
        createSphere();
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

    @Override
    public void onDrawFrame(GL10 gl) {

        GLES20.glClear(
                GLES20.GL_COLOR_BUFFER_BIT |
                GLES20.GL_DEPTH_BUFFER_BIT
        );

        updateMovement();
        updateCamera();

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
    // MOVEMENT
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

    public synchronized void addYaw(float amount) {

        yaw += amount;

        if (yaw > Math.PI * 2f) {
            yaw -= (float) (Math.PI * 2f);
        }

        if (yaw < -Math.PI * 2f) {
            yaw += (float) (Math.PI * 2f);
        }
    }

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

        GLES20.glUseProgram(program);

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

                -0.5f,-0.5f, 0.5f,
                 0.5f,-0.5f, 0.5f,
                 0.5f, 0.5f, 0.5f,

                -0.5f,-0.5f, 0.5f,
                 0.5f, 0.5f, 0.5f,
                -0.5f, 0.5f, 0.5f,

                -0.5f,-0.5f,-0.5f,
                -0.5f, 0.5f,-0.5f,
                 0.5f, 0.5f,-0.5f,

                -0.5f,-0.5f,-0.5f,
                 0.5f, 0.5f,-0.5f,
                 0.5f,-0.5f,-0.5f,

                -0.5f,-0.5f,-0.5f,
                -0.5f,-0.5f, 0.5f,
                -0.5f, 0.5f, 0.5f,

                -0.5f,-0.5f,-0.5f,
                -0.5f, 0.5f, 0.5f,
                -0.5f, 0.5f,-0.5f,

                 0.5f,-0.5f,-0.5f,
                 0.5f, 0.5f,-0.5f,
                 0.5f, 0.5f, 0.5f,

                 0.5f,-0.5f,-0.5f,
                 0.5f, 0.5f, 0.5f,
                 0.5f,-0.5f, 0.5f,

                -0.5f, 0.5f,-0.5f,
                -0.5f, 0.5f, 0.5f,
                 0.5f, 0.5f, 0.5f,

                -0.5f, 0.5f,-0.5f,
                 0.5f, 0.5f, 0.5f,
                 0.5f, 0.5f,-0.5f,

                -0.5f,-0.5f,-0.5f,
                 0.5f,-0.5f,-0.5f,
                 0.5f,-0.5f, 0.5f,

                -0.5f,-0.5f,-0.5f,
                 0.5f,-0.5f, 0.5f,
                -0.5f,-0.5f, 0.5f
        };

        cubeBuffer = createBuffer(vertices);
    }

    // ==================================================
    // SPHERE — LEAF SHAPE
    // ==================================================

    private void createSphere() {

        int latitude = 8;
        int longitude = 12;

        float[] vertices =
                new float[
                        latitude *
                        longitude *
                        6 * 3
                ];

        int index = 0;

        for (int lat = 0; lat < latitude; lat++) {

            float theta1 =
                    (float) Math.PI *
                    lat / latitude;

            float theta2 =
                    (float) Math.PI *
                    (lat + 1) / latitude;

            for (int lon = 0; lon < longitude; lon++) {

                float phi1 =
                        (float) (2.0 * Math.PI) *
                        lon / longitude;

                float phi2 =
                        (float) (2.0 * Math.PI) *
                        (lon + 1) / longitude;

                float x1 =
                        (float)
                        (Math.sin(theta1) *
                        Math.cos(phi1));

                float y1 =
                        (float)
                        Math.cos(theta1);

                float z1 =
                        (float)
                        (Math.sin(theta1) *
                        Math.sin(phi1));

                float x2 =
                        (float)
                        (Math.sin(theta2) *
                        Math.cos(phi1));

                float y2 =
                        (float)
                        Math.cos(theta2);

                float z2 =
                        (float)
                        (Math.sin(theta2) *
                        Math.sin(phi1));

                float x3 =
                        (float)
                        (Math.sin(theta2) *
                        Math.cos(phi2));

                float y3 =
                        (float)
                        Math.cos(theta2);

                float z3 =
                        (float)
                        (Math.sin(theta2) *
                        Math.sin(phi2));

                float x4 =
                        (float)
                        (Math.sin(theta1) *
                        Math.cos(phi2));

                float y4 =
                        (float)
                        Math.cos(theta1);

                float z4 =
                        (float)
                        (Math.sin(theta1) *
                        Math.sin(phi2));

                vertices[index++] = x1;
                vertices[index++] = y1;
                vertices[index++] = z1;

                vertices[index++] = x2;
                vertices[index++] = y2;
                vertices[index++] = z2;

                vertices[index++] = x3;
                vertices[index++] = y3;
                vertices[index++] = z3;

                vertices[index++] = x1;
                vertices[index++] = y1;
                vertices[index++] = z1;

                vertices[index++] = x3;
                vertices[index++] = y3;
                vertices[index++] = z3;

                vertices[index++] = x4;
                vertices[index++] = y4;
                vertices[index++] = z4;
            }
        }

        sphereVertexCount =
                vertices.length / 3;

        sphereBuffer =
                createBuffer(vertices);
    }

    // ==================================================
    // WORLD OBJECTS
    // ==================================================

    private void drawWorldObjects() {

        drawTree(-7f, -12f, 1.3f);
        drawTree(5f, -18f, 1.6f);
        drawTree(-12f, -28f, 1.8f);
        drawTree(13f, -35f, 2.0f);

        drawTree(-22f, -48f, 2.4f);
        drawTree(22f, -55f, 2.5f);

        drawTree(8f, -70f, 2.2f);
        drawTree(-18f, -78f, 2.0f);

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

        // Trunk
        drawCube(
                x,
                1.5f * scale,
                z,
                0.65f * scale,
                3.0f * scale,
                0.65f * scale,
                0.34f,
                0.20f,
                0.10f,
                1.0f
        );

        // Main round foliage
        drawSphere(
                x,
                4.0f * scale,
                z,
                2.2f * scale,
                2.0f * scale,
                2.2f * scale,
                0.08f,
                0.40f,
                0.08f,
                1.0f
        );

        // Upper foliage
        drawSphere(
                x,
                5.7f * scale,
                z,
                1.7f * scale,
                1.6f * scale,
                1.7f * scale,
                0.10f,
                0.48f,
                0.10f,
                1.0f
        );

        // Small upper crown
        drawSphere(
                x,
                7.0f * scale,
                z,
                1.15f * scale,
                1.1f * scale,
                1.15f * scale,
                0.12f,
                0.54f,
                0.12f,
                1.0f
        );
    }

    // ==================================================
    // SPHERE DRAW
    // ==================================================

    private void drawSphere(
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

        GLES20.glUseProgram(program);

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

        sphereBuffer.position(0);

        GLES20.glEnableVertexAttribArray(
                positionHandle
        );

        GLES20.glVertexAttribPointer(
                positionHandle,
                3,
                GLES20.GL_FLOAT,
                false,
                12,
                sphereBuffer
        );

        GLES20.glDrawArrays(
                GLES20.GL_TRIANGLES,
                0,
                sphereVertexCount
        );

        GLES20.glDisableVertexAttribArray(
                positionHandle
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
    // CUBE DRAW
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

        GLES20.glUseProgram(program);

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
    // BUFFER
    // ==================================================

    private FloatBuffer createBuffer(
            float[] vertices
    ) {

        ByteBuffer buffer =
                ByteBuffer.allocateDirect(
                        vertices.length * 4
                );

        buffer.order(
                ByteOrder.nativeOrder()
        );

        FloatBuffer result =
                buffer.asFloatBuffer();

        result.put(vertices);
        result.position(0);

        return result;
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
