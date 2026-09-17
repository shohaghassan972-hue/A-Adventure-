package com.aadventure.app;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class WorldRenderer implements GLSurfaceView.Renderer {

    private final float[] projection = new float[16];
    private final float[] view = new float[16];
    private final float[] model = new float[16];
    private final float[] mvp = new float[16];

    private FloatBuffer cubeBuffer;
    private FloatBuffer sphereBuffer;
    private int sphereVertexCount;

    private int program;
    private int positionHandle;
    private int colorHandle;
    private int mvpHandle;

    // First-person camera
    private float cameraX = 0f;
    private float cameraY = 1.7f;
    private float cameraZ = 8f;

    private float yaw = 0f;

    @Override
    public void onSurfaceCreated(
            javax.microedition.khronos.egl.EGLConfig config) {

        GLES20.glClearColor(
                0.38f,
                0.67f,
                0.88f,
                1f
        );

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        String vertexShaderCode =
                "attribute vec4 aPosition;" +
                "attribute vec4 aColor;" +
                "uniform mat4 uMVP;" +
                "varying vec4 vColor;" +

                "void main() {" +
                "    gl_Position = uMVP * aPosition;" +
                "    vColor = aColor;" +
                "}";

        String fragmentShaderCode =
                "precision mediump float;" +
                "varying vec4 vColor;" +

                "void main() {" +
                "    gl_FragColor = vColor;" +
                "}";

        int vertexShader = loadShader(
                GLES20.GL_VERTEX_SHADER,
                vertexShaderCode
        );

        int fragmentShader = loadShader(
                GLES20.GL_FRAGMENT_SHADER,
                fragmentShaderCode
        );

        program = GLES20.glCreateProgram();

        GLES20.glAttachShader(
                program,
                vertexShader
        );

        GLES20.glAttachShader(
                program,
                fragmentShader
        );

        GLES20.glLinkProgram(program);

        positionHandle =
                GLES20.glGetAttribLocation(
                        program,
                        "aPosition"
                );

        colorHandle =
                GLES20.glGetAttribLocation(
                        program,
                        "aColor"
                );

        mvpHandle =
                GLES20.glGetUniformLocation(
                        program,
                        "uMVP"
                );

        createCube();

        // Low-poly sphere for natural foliage.
        createSphere(8, 12);
    }

    @Override
    public void onSurfaceChanged(
            javax.microedition.khronos.opengles.GL10 gl,
            int width,
            int height) {

        GLES20.glViewport(
                0,
                0,
                width,
                height
        );

        float ratio =
                (float) width /
                (float) height;

        Matrix.perspectiveM(
                projection,
                0,
                70f,
                ratio,
                0.1f,
                150f
        );
    }

    @Override
    public void onDrawFrame(
            javax.microedition.khronos.opengles.GL10 gl) {

        GLES20.glClear(
                GLES20.GL_COLOR_BUFFER_BIT |
                GLES20.GL_DEPTH_BUFFER_BIT
        );

        // Camera looks forward according to yaw.
        float lookX =
                cameraX +
                (float) Math.sin(yaw);

        float lookY =
                cameraY;

        float lookZ =
                cameraZ -
                (float) Math.cos(yaw);

        Matrix.setLookAtM(
                view,
                0,

                cameraX,
                cameraY,
                cameraZ,

                lookX,
                lookY,
                lookZ,

                0f,
                1f,
                0f
        );

        drawGround();

        // Back area
        drawTree(
                -12f,
                0f,
                -12f,
                1.15f
        );

        drawTree(
                -4f,
                0f,
                -16f,
                0.90f
        );

        drawTree(
                5f,
                0f,
                -14f,
                1.05f
        );

        drawTree(
                14f,
                0f,
                -11f,
                0.95f
        );

        // Middle area
        drawTree(
                -16f,
                0f,
                -2f,
                0.85f
        );

        drawTree(
                -7f,
                0f,
                -5f,
                0.72f
        );

        drawTree(
                7f,
                0f,
                -4f,
                0.82f
        );

        drawTree(
                16f,
                0f,
                -1f,
                1.10f
        );

        // Far/back side
        drawTree(
                -13f,
                0f,
                8f,
                0.95f
        );

        drawTree(
                -2f,
                0f,
                11f,
                1.10f
        );

        drawTree(
                10f,
                0f,
                9f,
                0.88f
        );

        drawTree(
                17f,
                0f,
                12f,
                0.75f
        );

        // Rocks
        drawRock(
                -8f,
                0.20f,
                -10f,
                1.0f,
                0.65f,
                0.8f
        );

        drawRock(
                2f,
                0.18f,
                -9f,
                0.8f,
                0.5f,
                0.7f
        );

        drawRock(
                11f,
                0.22f,
                -7f,
                1.1f,
                0.6f,
                0.8f
        );

        drawRock(
                -12f,
                0.18f,
                3f,
                0.9f,
                0.5f,
                0.7f
        );

        drawRock(
                5f,
                0.20f,
                4f,
                1.0f,
                0.55f,
                0.85f
        );

        drawRock(
                14f,
                0.16f,
                6f,
                0.75f,
                0.45f,
                0.65f
        );
    }

    // Movement
    public void addMovement(
            float forward,
            float side) {

        float sin =
                (float) Math.sin(yaw);

        float cos =
                (float) Math.cos(yaw);

        // Forward/backward
        cameraX += sin * forward;

        cameraZ -= cos * forward;

        // Left/right
        cameraX += cos * side;

        cameraZ += sin * side;

        // Keep player inside map.
        cameraX =
                Math.max(
                        -28f,
                        Math.min(
                                28f,
                                cameraX
                        )
                );

        cameraZ =
                Math.max(
                        -28f,
                        Math.min(
                                28f,
                                cameraZ
                        )
                );
    }

    // Camera rotation
    public void addYaw(float amount) {
        yaw += amount;
    }

    // --------------------------------------------------
    // GROUND
    // --------------------------------------------------

    private void drawGround() {

        drawCube(
                0f,
                -0.08f,
                0f,

                60f,
                0.16f,
                60f,

                0.22f,
                0.55f,
                0.20f,
                1f
        );
    }

    // --------------------------------------------------
    // TREE
    // --------------------------------------------------

    private void drawTree(
            float x,
            float y,
            float z,
            float s) {

        // Main trunk
        drawCube(
                x,
                y + 1.35f * s,
                z,

                0.62f * s,
                2.70f * s,
                0.62f * s,

                0.30f,
                0.15f,
                0.07f,
                1f
        );

        // Main lower branch
        drawBranch(
                x - 0.25f * s,
                y + 1.65f * s,
                z,

                0.95f * s,
                0.16f * s,
                0.16f * s,

                -28f,
                0f,
                -32f
        );

        // Upper left branch
        drawBranch(
                x - 0.18f * s,
                y + 2.20f * s,
                z,

                0.75f * s,
                0.14f * s,
                0.14f * s,

                24f,
                0f,
                38f
        );

        // Upper right branch
        drawBranch(
                x + 0.18f * s,
                y + 2.30f * s,
                z,

                0.78f * s,
                0.14f * s,
                0.14f * s,

                -22f,
                0f,
                -42f
        );

        // --------------------------------------------------
        // NATURAL FOLIAGE
        // --------------------------------------------------

        // Main crown
        drawSphere(
                x,
                y + 3.15f * s,
                z,

                1.15f * s,

                0.08f,
                0.43f,
                0.09f,
                1f
        );

        // Left crown
        drawSphere(
                x - 0.72f * s,
                y + 2.85f * s,
                z + 0.12f * s,

                0.82f * s,

                0.06f,
                0.36f,
                0.07f,
                1f
        );

        // Right crown
        drawSphere(
                x + 0.75f * s,
                y + 2.88f * s,
                z - 0.08f * s,

                0.86f * s,

                0.07f,
                0.39f,
                0.08f,
                1f
        );

        // Top crown
        drawSphere(
                x - 0.20f * s,
                y + 3.75f * s,
                z,

                0.78f * s,

                0.09f,
                0.47f,
                0.10f,
                1f
        );

        // Front/right crown
        drawSphere(
                x + 0.48f * s,
                y + 3.52f * s,
                z + 0.25f * s,

                0.65f * s,

                0.07f,
                0.40f,
                0.08f,
                1f
        );

        // Small lower foliage
        drawSphere(
                x - 0.45f * s,
                y + 2.55f * s,
                z - 0.18f * s,

                0.55f * s,

                0.055f,
                0.32f,
                0.065f,
                1f
        );
    }

    // --------------------------------------------------
    // BRANCH
    // --------------------------------------------------

    private void drawBranch(
            float x,
            float y,
            float z,

            float length,
            float thickness,
            float depth,

            float rotX,
            float rotY,
            float rotZ) {

        Matrix.setIdentityM(
                model,
                0
        );

        Matrix.translateM(
                model,
                0,
                x,
                y,
                z
        );

        Matrix.rotateM(
                model,
                0,
                rotY,
                0f,
                1f,
                0f
        );

        Matrix.rotateM(
                model,
                0,
                rotZ,
                0f,
                0f,
                1f
        );

        Matrix.rotateM(
                model,
                0,
                rotX,
                1f,
                0f,
                0f
        );

        Matrix.scaleM(
                model,
                0,
                length,
                thickness,
                depth
        );

        drawCurrentModel(
                0.28f,
                0.13f,
                0.055f,
                1f
        );
    }

    // --------------------------------------------------
    // ROCK
    // --------------------------------------------------

    private void drawRock(
            float x,
            float y,
            float z,

            float sx,
            float sy,
            float sz) {

        Matrix.setIdentityM(
                model,
                0
        );

        Matrix.translateM(
                model,
                0,
                x,
                y,
                z
        );

        Matrix.rotateM(
                model,
                0,
                12f,
                0f,
                1f,
                0f
        );

        Matrix.rotateM(
                model,
                0,
                -8f,
                1f,
                0f,
                0f
        );

        Matrix.scaleM(
                model,
                0,
                sx,
                sy,
                sz
        );

        drawCurrentModel(
                0.28f,
                0.28f,
                0.25f,
                1f
        );
    }

    // --------------------------------------------------
    // CUBE
    // --------------------------------------------------

    private void drawCube(
            float x,
            float y,
            float z,

            float sx,
            float sy,
            float sz,

            float r,
            float g,
            float b,
            float a) {

        Matrix.setIdentityM(
                model,
                0
        );

        Matrix.translateM(
                model,
                0,
                x,
                y,
                z
        );

        Matrix.scaleM(
                model,
                0,
                sx,
                sy,
                sz
        );

        drawCurrentModel(
                r,
                g,
                b,
                a
        );
    }

    // --------------------------------------------------
    // SPHERE
    // --------------------------------------------------

    private void drawSphere(
            float x,
            float y,
            float z,
            float scale,

            float r,
            float g,
            float b,
            float a) {

        Matrix.setIdentityM(
                model,
                0
        );

        Matrix.translateM(
                model,
                0,
                x,
                y,
                z
        );

        Matrix.scaleM(
                model,
                0,
                scale,
                scale,
                scale
        );

        Matrix.multiplyMM(
                mvp,
                0,
                view,
                0,
                model,
                0
        );

        Matrix.multiplyMM(
                mvp,
                0,
                projection,
                0,
                mvp,
                0
        );

        GLES20.glUseProgram(
                program
        );

        GLES20.glUniformMatrix4fv(
                mvpHandle,
                1,
                false,
                mvp,
                0
        );

        sphereBuffer.position(0);

        GLES20.glVertexAttribPointer(
                positionHandle,
                3,
                GLES20.GL_FLOAT,
                false,
                3 * 4,
                sphereBuffer
        );

        GLES20.glEnableVertexAttribArray(
                positionHandle
        );

        float[] colors =
                new float[
                        sphereVertexCount * 4
                ];

        for (
                int i = 0;
                i < sphereVertexCount;
                i++
        ) {

            float shade =
                    0.82f +
                    0.18f *
                    ((i % 7) / 6f);

            colors[i * 4] =
                    r * shade;

            colors[i * 4 + 1] =
                    g * shade;

            colors[i * 4 + 2] =
                    b * shade;

            colors[i * 4 + 3] =
                    a;
        }

        FloatBuffer colorBuffer =
                ByteBuffer
                        .allocateDirect(
                                colors.length * 4
                        )
                        .order(
                                ByteOrder.nativeOrder()
                        )
                        .asFloatBuffer();

        colorBuffer
                .put(colors)
                .position(0);

        GLES20.glVertexAttribPointer(
                colorHandle,
                4,
                GLES20.GL_FLOAT,
                false,
                4 * 4,
                colorBuffer
        );

        GLES20.glEnableVertexAttribArray(
                colorHandle
        );

        GLES20.glDrawArrays(
                GLES20.GL_TRIANGLES,
                0,
                sphereVertexCount
        );

        GLES20.glDisableVertexAttribArray(
                positionHandle
        );

        GLES20.glDisableVertexAttribArray(
                colorHandle
        );
    }

    // --------------------------------------------------
    // DRAW CURRENT MODEL
    // --------------------------------------------------

    private void drawCurrentModel(
            float r,
            float g,
            float b,
            float a) {

        Matrix.multiplyMM(
                mvp,
                0,
                view,
                0,
                model,
                0
        );

        Matrix.multiplyMM(
                mvp,
                0,
                projection,
                0,
                mvp,
                0
        );

        GLES20.glUseProgram(
                program
        );

        GLES20.glUniformMatrix4fv(
                mvpHandle,
                1,
                false,
                mvp,
                0
        );

        cubeBuffer.position(0);

        GLES20.glVertexAttribPointer(
                positionHandle,
                3,
                GLES20.GL_FLOAT,
                false,
                3 * 4,
                cubeBuffer
        );

        GLES20.glEnableVertexAttribArray(
                positionHandle
        );

        float[] colorData =
                new float[
                        36 * 4
                ];

        for (
                int i = 0;
                i < 36;
                i++
        ) {

            float shade =
                    0.86f +
                    0.14f *
                    ((i % 6) / 5f);

            colorData[i * 4] =
                    r * shade;

            colorData[i * 4 + 1] =
                    g * shade;

            colorData[i * 4 + 2] =
                    b * shade;

            colorData[i * 4 + 3] =
                    a;
        }

        FloatBuffer colorBuffer =
                ByteBuffer
                        .allocateDirect(
                                colorData.length * 4
                        )
                        .order(
                                ByteOrder.nativeOrder()
                        )
                        .asFloatBuffer();

        colorBuffer
                .put(colorData)
                .position(0);

        GLES20.glVertexAttribPointer(
                colorHandle,
                4,
                GLES20.GL_FLOAT,
                false,
                4 * 4,
                colorBuffer
        );

        GLES20.glEnableVertexAttribArray(
                colorHandle
        );

        GLES20.glDrawArrays(
                GLES20.GL_TRIANGLES,
                0,
                36
        );

        GLES20.glDisableVertexAttribArray(
                positionHandle
        );

        GLES20.glDisableVertexAttribArray(
                colorHandle
        );
    }

    // --------------------------------------------------
    // CUBE GEOMETRY
    // --------------------------------------------------

    private void createCube() {

        float[] vertices = {

                // Front
                -0.5f,-0.5f, 0.5f,
                 0.5f,-0.5f, 0.5f,
                 0.5f, 0.5f, 0.5f,

                -0.5f,-0.5f, 0.5f,
                 0.5f, 0.5f, 0.5f,
                -0.5f, 0.5f, 0.5f,

                // Back
                 0.5f,-0.5f,-0.5f,
                -0.5f,-0.5f,-0.5f,
                -0.5f, 0.5f,-0.5f,

                 0.5f,-0.5f,-0.5f,
                -0.5f, 0.5f,-0.5f,
                 0.5f, 0.5f,-0.5f,

                // Top
                -0.5f, 0.5f, 0.5f,
                 0.5f, 0.5f, 0.5f,
                 0.5f, 0.5f,-0.5f,

                -0.5f, 0.5f, 0.5f,
                 0.5f, 0.5f,-0.5f,
                -0.5f, 0.5f,-0.5f,

                // Bottom
                -0.5f,-0.5f,-0.5f,
                 0.5f,-0.5f,-0.5f,
                 0.5f,-0.5f, 0.5f,

                -0.5f,-0.5f,-0.5f,
                 0.5f,-0.5f, 0.5f,
                -0.5f,-0.5f, 0.5f,

                // Right
                 0.5f,-0.5f, 0.5f,
                 0.5f,-0.5f,-0.5f,
                 0.5f, 0.5f,-0.5f,

                 0.5f,-0.5f, 0.5f,
                 0.5f, 0.5f,-0.5f,
                 0.5f, 0.5f, 0.5f,

                // Left
                -0.5f,-0.5f,-0.5f,
                -0.5f,-0.5f, 0.5f,
                -0.5f, 0.5f, 0.5f,

                -0.5f,-0.5f,-0.5f,
                -0.5f, 0.5f, 0.5f,
                -0.5f, 0.5f,-0.5f
        };

        cubeBuffer =
                ByteBuffer
                        .allocateDirect(
                                vertices.length * 4
                        )
                        .order(
                                ByteOrder.nativeOrder()
                        )
                        .asFloatBuffer();

        cubeBuffer
                .put(vertices)
                .position(0);
    }

    // --------------------------------------------------
    // SPHERE GEOMETRY
    // --------------------------------------------------

    private void createSphere(
            int stacks,
            int slices) {

        float[] vertices =
                new float[
                        stacks *
                        slices *
                        6 *
                        3
                ];

        int index = 0;

        for (
                int i = 0;
                i < stacks;
                i++
        ) {

            float v0 =
                    (float) i /
                    stacks;

            float v1 =
                    (float) (i + 1) /
                    stacks;

            float phi0 =
                    (float)
                    (Math.PI * v0);

            float phi1 =
                    (float)
                    (Math.PI * v1);

            for (
                    int j = 0;
                    j < slices;
                    j++
            ) {

                float u0 =
                        (float) j /
                        slices;

                float u1 =
                        (float) (j + 1) /
                        slices;

                float theta0 =
                        (float)
                        (2.0 *
                        Math.PI *
                        u0);

                float theta1 =
                        (float)
                        (2.0 *
                        Math.PI *
                        u1);

                index =
                        putSphereVertex(
                                vertices,
                                index,
                                phi0,
                                theta0
                        );

                index =
                        putSphereVertex(
                                vertices,
                                index,
                                phi1,
                                theta0
                        );

                index =
                        putSphereVertex(
                                vertices,
                                index,
                                phi1,
                                theta1
                        );

                index =
                        putSphereVertex(
                                vertices,
                                index,
                                phi0,
                                theta0
                        );

                index =
                        putSphereVertex(
                                vertices,
                                index,
                                phi1,
                                theta1
                        );

                index =
                        putSphereVertex(
                                vertices,
                                index,
                                phi0,
                                theta1
                        );
            }
        }

        sphereVertexCount =
                index / 3;

        sphereBuffer =
                ByteBuffer
                        .allocateDirect(
                                vertices.length * 4
                        )
                        .order(
                                ByteOrder.nativeOrder()
                        )
                        .asFloatBuffer();

        sphereBuffer
                .put(vertices)
                .position(0);
    }

    private int putSphereVertex(
            float[] data,
            int index,
            float phi,
            float theta) {

        float sinPhi =
                (float)
                Math.sin(phi);

        data[index++] =
                sinPhi *
                (float)
                Math.cos(theta);

        data[index++] =
                (float)
                Math.cos(phi);

        data[index++] =
                sinPhi *
                (float)
                Math.sin(theta);

        return index;
    }

    // --------------------------------------------------
    // SHADER
    // --------------------------------------------------

    private int loadShader(
            int type,
            String shaderCode) {

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
            }
