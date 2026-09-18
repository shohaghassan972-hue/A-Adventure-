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
    private FloatBuffer rockBuffer;
    private FloatBuffer skyBuffer;

    private int skyProgram;
    private int skyPositionHandle;
    private int skyMvpHandle;

    // Sun + soft glow (Step 2B)
    private FloatBuffer sunBuffer;
    private int sunProgram;
    private int sunPositionHandle;
    private int sunMvpHandle;
    private int sunCenterHandle;
    private int sunRightHandle;
    private int sunUpHandle;
    private int sunSizeHandle;

    private int sphereVertexCount;
    private int rockVertexCount;

    private int program;
    private int positionHandle;
    private int colorHandle;
    private int mvpHandle;

    // First-person camera
    private float cameraX = 0f;
    private float cameraY = 1.7f;
    private float cameraZ = 8f;

    private float yaw = 0f;
    private float pitch = 0f;

    @Override
    public void onSurfaceCreated(
            javax.microedition.khronos.opengles.GL10 gl,
            javax.microedition.khronos.egl.EGLConfig config) {

        // The sky is rendered as a smooth vertical gradient in Step 2A.
        // Keep a blue fallback clear color underneath it.
        GLES20.glClearColor(
                0.52f,
                0.74f,
                0.92f,
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

        String skyVertexShaderCode =
                "attribute vec4 aPosition;" +
                "uniform mat4 uMVP;" +
                "varying float vSkyY;" +
                "void main() {" +
                "    gl_Position = aPosition;" +
                "    vSkyY = aPosition.y;" +
                "}";

        String skyFragmentShaderCode =
                "precision mediump float;" +
                "varying float vSkyY;" +
                "void main() {" +
                "    float t = clamp((vSkyY + 1.0) * 0.5, 0.0, 1.0);" +
                "    float horizon = smoothstep(0.0, 0.58, t);" +
                "    vec3 horizonColor = vec3(0.78, 0.88, 0.96);" +
                "    vec3 topColor = vec3(0.18, 0.45, 0.78);" +
                "    vec3 skyColor = mix(horizonColor, topColor, horizon);" +
                "    gl_FragColor = vec4(skyColor, 1.0);" +
                "}";

        int skyVertexShader = loadShader(
                GLES20.GL_VERTEX_SHADER,
                skyVertexShaderCode
        );

        int skyFragmentShader = loadShader(
                GLES20.GL_FRAGMENT_SHADER,
                skyFragmentShaderCode
        );

        skyProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(skyProgram, skyVertexShader);
        GLES20.glAttachShader(skyProgram, skyFragmentShader);
        GLES20.glLinkProgram(skyProgram);

        skyPositionHandle =
                GLES20.glGetAttribLocation(
                        skyProgram,
                        "aPosition"
                );

        skyMvpHandle =
                GLES20.glGetUniformLocation(
                        skyProgram,
                        "uMVP"
                );

        String sunVertexShaderCode =
                "attribute vec2 aPosition;" +
                "uniform mat4 uVP;" +
                "uniform vec3 uCenter;" +
                "uniform vec3 uRight;" +
                "uniform vec3 uUp;" +
                "uniform float uSize;" +
                "varying vec2 vLocal;" +
                "void main() {" +
                "    vec3 worldPos = uCenter + uRight * aPosition.x * uSize + uUp * aPosition.y * uSize;" +
                "    gl_Position = uVP * vec4(worldPos, 1.0);" +
                "    vLocal = aPosition;" +
                "}";

        String sunFragmentShaderCode =
                "precision mediump float;" +
                "varying vec2 vLocal;" +
                "void main() {" +
                "    float d = length(vLocal);" +
                "    float core = 1.0 - smoothstep(0.0, 0.42, d);" +
                "    float glow = 1.0 - smoothstep(0.18, 1.0, d);" +
                "    float alpha = max(core * 0.98, glow * 0.34);" +
                "    if (alpha < 0.01) discard;" +
                "    vec3 coreColor = vec3(1.0, 0.96, 0.78);" +
                "    vec3 glowColor = vec3(1.0, 0.76, 0.28);" +
                "    vec3 color = mix(glowColor, coreColor, core);" +
                "    gl_FragColor = vec4(color, alpha);" +
                "}";

        int sunVertexShader = loadShader(
                GLES20.GL_VERTEX_SHADER,
                sunVertexShaderCode
        );

        int sunFragmentShader = loadShader(
                GLES20.GL_FRAGMENT_SHADER,
                sunFragmentShaderCode
        );

        sunProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(sunProgram, sunVertexShader);
        GLES20.glAttachShader(sunProgram, sunFragmentShader);
        GLES20.glLinkProgram(sunProgram);

        sunPositionHandle =
                GLES20.glGetAttribLocation(
                        sunProgram,
                        "aPosition"
                );

        sunMvpHandle =
                GLES20.glGetUniformLocation(
                        sunProgram,
                        "uVP"
                );

        sunCenterHandle =
                GLES20.glGetUniformLocation(
                        sunProgram,
                        "uCenter"
                );

        sunRightHandle =
                GLES20.glGetUniformLocation(
                        sunProgram,
                        "uRight"
                );

        sunUpHandle =
                GLES20.glGetUniformLocation(
                        sunProgram,
                        "uUp"
                );

        sunSizeHandle =
                GLES20.glGetUniformLocation(
                        sunProgram,
                        "uSize"
                );

        createSky();
        createSun();

        createCube();

        createSphere(
                8,
                12
        );

        // New irregular 3D rock geometry
        createRock();
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

        float cosPitch =
                (float) Math.cos(pitch);

        float sinPitch =
                (float) Math.sin(pitch);

        float lookX =
                cameraX +
                cosPitch * (float) Math.sin(yaw);

        float lookY =
                cameraY +
                sinPitch;

        float lookZ =
                cameraZ -
                cosPitch * (float) Math.cos(yaw);

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

        drawSky();
        drawSun();

        drawGround();

        // --------------------------------------------------
        // TREES
        // --------------------------------------------------

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

        // --------------------------------------------------
        // REALISTIC ROCKS
        // --------------------------------------------------

        drawRock(
                -8f,
                0f,
                -10f,
                1.0f,
                0.65f,
                0.8f,
                8f
        );

        drawRock(
                2f,
                0f,
                -9f,
                0.75f,
                0.48f,
                0.65f,
                -12f
        );

        drawRock(
                11f,
                0f,
                -7f,
                1.15f,
                0.62f,
                0.82f,
                18f
        );

        drawRock(
                -12f,
                0f,
                3f,
                0.90f,
                0.52f,
                0.70f,
                -20f
        );

        drawRock(
                5f,
                0f,
                4f,
                1.0f,
                0.55f,
                0.85f,
                10f
        );

        drawRock(
                14f,
                0f,
                6f,
                0.78f,
                0.45f,
                0.68f,
                -15f
        );
    }

    // --------------------------------------------------
    // MOVEMENT
    // --------------------------------------------------

    public void addMovement(
            float forward,
            float side) {

        float sin =
                (float) Math.sin(yaw);

        float cos =
                (float) Math.cos(yaw);

        cameraX += sin * forward;
        cameraZ -= cos * forward;

        cameraX += cos * side;
        cameraZ += sin * side;

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

    // --------------------------------------------------
    // CAMERA
    // --------------------------------------------------

    public void addYaw(float amount) {
        yaw += amount;

        if (yaw > (float) (Math.PI * 2.0)) {
            yaw -= (float) (Math.PI * 2.0);
        }

        if (yaw < (float) (-Math.PI * 2.0)) {
            yaw += (float) (Math.PI * 2.0);
        }
    }

    public void addPitch(float amount) {
        pitch += amount;

        // Keep the camera just short of the exact vertical poles.
        float limit = (float) (Math.PI / 2.0 - 0.02);

        if (pitch > limit) {
            pitch = limit;
        }

        if (pitch < -limit) {
            pitch = -limit;
        }
    }

    // --------------------------------------------------
    // SKY - STEP 2A
    // --------------------------------------------------

    private void createSky() {
        float[] vertices = {
                -1f, -1f, 0f,
                 1f, -1f, 0f,
                -1f,  1f, 0f,
                 1f,  1f, 0f
        };

        skyBuffer =
                ByteBuffer
                        .allocateDirect(vertices.length * 4)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer();

        skyBuffer.put(vertices).position(0);
    }

    private void drawSky() {
        // Draw the gradient behind the entire 3D world.
        // It is deliberately screen-filling in 2A; sun/clouds remain future steps.
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glUseProgram(skyProgram);

        skyBuffer.position(0);
        GLES20.glVertexAttribPointer(
                skyPositionHandle,
                3,
                GLES20.GL_FLOAT,
                false,
                0,
                skyBuffer
        );
        GLES20.glEnableVertexAttribArray(skyPositionHandle);

        GLES20.glDrawArrays(
                GLES20.GL_TRIANGLE_STRIP,
                0,
                4
        );

        GLES20.glDisableVertexAttribArray(skyPositionHandle);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // Restore the normal world shader for ground, trees and rocks.
        GLES20.glUseProgram(program);
    }

    // --------------------------------------------------
    // SUN + NATURAL GLOW - STEP 2B
    // --------------------------------------------------

    private void createSun() {
        float[] vertices = {
                -1f, -1f,
                 1f, -1f,
                -1f,  1f,
                 1f, -1f,
                 1f,  1f,
                -1f,  1f
        };

        sunBuffer =
                ByteBuffer
                        .allocateDirect(vertices.length * 4)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer();

        sunBuffer.put(vertices).position(0);
    }

    private void drawSun() {
        // Fixed world-space position: the sun does not follow the screen.
        final float sunX = 0f;
        final float sunY = 32f;
        final float sunZ = -70f;

        float cosPitch = (float) Math.cos(pitch);
        float sinPitch = (float) Math.sin(pitch);
        float sinYaw = (float) Math.sin(yaw);
        float cosYaw = (float) Math.cos(yaw);

        // Camera-facing billboard basis. The center remains fixed in world space.
        float rightX = cosYaw;
        float rightY = 0f;
        float rightZ = sinYaw;

        float upX = -sinYaw * sinPitch;
        float upY = cosPitch;
        float upZ = cosYaw * sinPitch;

        GLES20.glUseProgram(sunProgram);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(
                GLES20.GL_SRC_ALPHA,
                GLES20.GL_ONE
        );
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        GLES20.glUniformMatrix4fv(
                sunMvpHandle,
                1,
                false,
                getViewProjectionMatrix(),
                0
        );

        GLES20.glUniform3f(
                sunCenterHandle,
                sunX,
                sunY,
                sunZ
        );

        GLES20.glUniform3f(
                sunRightHandle,
                rightX,
                rightY,
                rightZ
        );

        GLES20.glUniform3f(
                sunUpHandle,
                upX,
                upY,
                upZ
        );

        GLES20.glUniform1f(
                sunSizeHandle,
                7.0f
        );

        sunBuffer.position(0);
        GLES20.glVertexAttribPointer(
                sunPositionHandle,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                sunBuffer
        );
        GLES20.glEnableVertexAttribArray(sunPositionHandle);

        GLES20.glDrawArrays(
                GLES20.GL_TRIANGLES,
                0,
                6
        );

        GLES20.glDisableVertexAttribArray(sunPositionHandle);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(program);
    }

    private float[] getViewProjectionMatrix() {
        Matrix.multiplyMM(
                mvp,
                0,
                projection,
                0,
                view,
                0
        );
        return mvp;
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

        // Lower branch
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

        // Lower foliage
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
    // REALISTIC ROCK
    // --------------------------------------------------

    private void drawRock(
            float x,
            float y,
            float z,
            float sx,
            float sy,
            float sz,
            float rotation) {

        Matrix.setIdentityM(
                model,
                0
        );

        Matrix.translateM(
                model,
                0,
                x,
                y + sy * 0.42f,
                z
        );

        Matrix.rotateM(
                model,
                0,
                rotation,
                0f,
                1f,
                0f
        );

        Matrix.scaleM(
                model,
                0,
                sx,
                sy,
                sz
        );

        drawRockModel(
                0.30f,
                0.30f,
                0.27f,
                1f
        );
    }

    // --------------------------------------------------
    // ROCK MODEL
    // --------------------------------------------------

    private void drawRockModel(
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

        rockBuffer.position(0);

        GLES20.glVertexAttribPointer(
                positionHandle,
                3,
                GLES20.GL_FLOAT,
                false,
                3 * 4,
                rockBuffer
        );

        GLES20.glEnableVertexAttribArray(
                positionHandle
        );

        float[] colors =
                new float[
                        rockVertexCount * 4
                ];

        for (
                int i = 0;
                i < rockVertexCount;
                i++
        ) {

            // Different faces get slightly different shades.
            float shade =
                    0.72f +
                    0.28f *
                    ((i % 8) / 7f);

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
                rockVertexCount
        );

        GLES20.glDisableVertexAttribArray(
                positionHandle
        );

        GLES20.glDisableVertexAttribArray(
                colorHandle
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
    // CURRENT CUBE MODEL
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
    // IRREGULAR ROCK GEOMETRY
    // --------------------------------------------------

    private void createRock() {

        /*
         * Rock structure:
         *
         *          top
         *           /\
         *       ___/  \___
         *      /          \
         *     /            \
         *    /______________\
         *
         * Multiple irregular rings make a
         * natural low-poly boulder.
         */

        final int sides = 8;

        // 8 top triangles
        // 8 x 8 middle triangles = 64
        // 8 bottom triangles
        // Total = 80 triangles = 240 vertices
        float[] vertices =
                new float[
                        80 * 3 * 3
                ];

        int index = 0;

        float[] ring1Radius = {
                0.72f,
                0.88f,
                0.80f,
                0.95f,
                0.76f,
                0.90f,
                0.84f,
                0.78f
        };

        float[] ring2Radius = {
                0.92f,
                1.00f,
                0.86f,
                1.05f,
                0.90f,
                0.98f,
                0.88f,
                0.94f
        };

        float[] angleOffset = {
                0.00f,
                0.08f,
                -0.05f,
                0.06f,
                -0.04f,
                0.07f,
                -0.06f,
                0.03f
        };

        // --------------------------------------------------
        // TOP TO RING 1
        // --------------------------------------------------

        for (
                int i = 0;
                i < sides;
                i++
        ) {

            int next =
                    (i + 1) % sides;

            float a0 =
                    (float)
                    (2.0 *
                    Math.PI *
                    i /
                    sides)
                    + angleOffset[i];

            float a1 =
                    (float)
                    (2.0 *
                    Math.PI *
                    next /
                    sides)
                    + angleOffset[next];

            // Top vertex
            index =
                    putVertex(
                            vertices,
                            index,
                            0f,
                            1.05f,
                            0f
                    );

            // Ring 1 vertex
            index =
                    putVertex(
                            vertices,
                            index,
                            (float)
                            Math.cos(a0) *
                            ring1Radius[i],
                            0.45f +
                            ((i % 3) * 0.04f),
                            (float)
                            Math.sin(a0) *
                            ring1Radius[i]
                    );

            // Next ring 1 vertex
            index =
                    putVertex(
                            vertices,
                            index,
                            (float)
                            Math.cos(a1) *
                            ring1Radius[next],
                            0.45f +
                            ((next % 3) * 0.04f),
                            (float)
                            Math.sin(a1) *
                            ring1Radius[next]
                    );
        }

        // --------------------------------------------------
        // RING 1 TO RING 2
        // --------------------------------------------------

        for (
                int i = 0;
                i < sides;
                i++
        ) {

            int next =
                    (i + 1) % sides;

            float a0 =
                    (float)
                    (2.0 *
                    Math.PI *
                    i /
                    sides)
                    + angleOffset[i];

            float a1 =
                    (float)
                    (2.0 *
                    Math.PI *
                    next /
                    sides)
                    + angleOffset[next];

            float r10 =
                    ring1Radius[i];

            float r11 =
                    ring1Radius[next];

            float r20 =
                    ring2Radius[i];

            float r21 =
                    ring2Radius[next];

            float y10 =
                    0.45f +
                    ((i % 3) * 0.04f);

            float y11 =
                    0.45f +
                    ((next % 3) * 0.04f);

            float y20 =
                    0.05f +
                    ((i % 2) * 0.03f);

            float y21 =
                    0.05f +
                    ((next % 2) * 0.03f);

            // Triangle 1
            index =
                    putVertex(
                            vertices,
                            index,
                            (float)
                            Math.cos(a0) * r10,
                            y10,
                            (float)
                            Math.sin(a0) * r10
                    );

            index =
                    putVertex(
                            vertices,
                            index,
                            (float)
                            Math.cos(a0) * r20,
                            y20,
                            (float)
                            Math.sin(a0) * r20
                    );

            index =
                    putVertex(
                            vertices,
                            index,
                            (float)
                            Math.cos(a1) * r21,
                            y21,
                            (float)
                            Math.sin(a1) * r21
                    );

            // Triangle 2
            index =
                    putVertex(
                            vertices,
                            index,
                            (float)
                            Math.cos(a0) * r10,
                            y10,
                            (float)
                            Math.sin(a0) * r10
                    );

            index =
                    putVertex(
                            vertices,
                            index,
                            (float)
                            Math.cos(a1) * r21,
                            y21,
                            (float)
                            Math.sin(a1) * r21
                    );

            index =
                    putVertex(
                            vertices,
                            index,
                            (float)
                            Math.cos(a1) * r11,
                            y11,
                            (float)
                            Math.sin(a1) * r11
                    );
        }

        // --------------------------------------------------
        // RING 2 TO BOTTOM
        // --------------------------------------------------

        for (
                int i = 0;
                i < sides;
                i++
        ) {

            int next =
                    (i + 1) % sides;

            float a0 =
                    (float)
                    (2.0 *
                    Math.PI *
                    i /
                    sides)
                    + angleOffset[i];

            float a1 =
                    (float)
                    (2.0 *
                    Math.PI *
                    next /
                    sides)
                    + angleOffset[next];

            // Ring 2
            index =
                    putVertex(
                            vertices,
                            index,
                            (float)
                            Math.cos(a0) *
                            ring2Radius[i],
                            0.05f,
                            (float)
                            Math.sin(a0) *
                            ring2Radius[i]
                    );

            // Bottom center
            index =
                    putVertex(
                            vertices,
                            index,
                            0f,
                            -0.10f,
                            0f
                    );

            // Next ring 2
            index =
                    putVertex(
                            vertices,
                            index,
                            (float)
                            Math.cos(a1) *
                            ring2Radius[next],
                            0.05f,
                            (float)
                            Math.sin(a1) *
                            ring2Radius[next]
                    );
        }

        rockVertexCount =
                index / 3;

        rockBuffer =
                ByteBuffer
                        .allocateDirect(
                                vertices.length * 4
                        )
                        .order(
                                ByteOrder.nativeOrder()
                        )
                        .asFloatBuffer();

        rockBuffer
                .put(vertices)
                .position(0);
    }

    private int putVertex(
            float[] data,
            int index,
            float x,
            float y,
            float z) {

        data[index++] = x;
        data[index++] = y;
        data[index++] = z;

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
