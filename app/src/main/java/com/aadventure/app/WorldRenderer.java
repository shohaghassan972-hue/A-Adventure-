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
    private FloatBuffer groundVertexBuffer;
    private FloatBuffer groundColorBuffer;
    private int groundVertexCount;
    private FloatBuffer sphereBuffer;
    private FloatBuffer rockBuffer;
    private FloatBuffer skyBuffer;

    private int skyProgram;
    private int skyPositionHandle;

    // Sun + soft glow (Step 2B)
    private FloatBuffer sunBuffer;
    private int sunProgram;
    private int sunPositionHandle;
    private int sunMvpHandle;
    private int sunCenterHandle;
    private int sunRightHandle;
    private int sunUpHandle;
    private int sunSizeHandle;

    // Natural world-space clouds (Step 2C)
    private FloatBuffer cloudBuffer;
    private int cloudProgram;
    private int cloudPositionHandle;
    private int cloudVpHandle;
    private int cloudCenterHandle;
    private int cloudRightHandle;
    private int cloudUpHandle;
    private int cloudSizeHandle;
    private int cloudColorHandle;

    private static class Cloud {
        final float x, y, z, scale;

        Cloud(float x, float y, float z, float scale) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.scale = scale;
        }
    }

    // Clouds are distributed around the player/world in multiple directions
    // so a 360-degree camera turn does not leave the sky empty on one side.
    private final Cloud[] clouds = {
            new Cloud(0f,   21f, -32f, 1.15f),
            new Cloud(-18f, 24f, -28f, 1.00f),
            new Cloud(20f,  19f, -30f, 1.25f),
            new Cloud(34f,  23f, -12f, 1.35f),
            new Cloud(38f,  20f,  10f, 1.10f),
            new Cloud(30f,  25f,  28f, 1.45f),
            new Cloud(8f,   22f,  36f, 1.20f),
            new Cloud(-14f, 26f,  34f, 1.40f),
            new Cloud(-34f, 21f,  24f, 1.10f),
            new Cloud(-40f, 24f,   4f, 1.35f),
            new Cloud(-36f, 19f, -16f, 1.00f),
            new Cloud(-28f, 27f, -34f, 1.50f),
            new Cloud(12f,  28f, -52f, 1.30f),
            new Cloud(42f,  26f, -42f, 1.20f),
            new Cloud(48f,  22f,  22f, 1.35f),
            new Cloud(-48f, 25f,  30f, 1.25f),
            new Cloud(-50f, 23f, -28f, 1.40f),
            new Cloud(0f,   24f,  52f, 1.10f)
    };

    private int sphereVertexCount;
    private int rockVertexCount;

    private int program;
    private int positionHandle;
    private int colorHandle;
    private int mvpHandle;
    private int viewHandle;
    private int lightDirHandle;
    private int ambientLightHandle;
    private int groundDetailHandle;
    private int groundDaylightHandle;

    // Step 3A-2: keep ground tiles visually flat so tile color variation does not
    // create artificial triangular shading across the field.
    private boolean groundFlatColor = false;

    // First-person camera
    private float cameraX = 0f;
    private float cameraY = 1.7f;
    private float cameraZ = 8f;

    private float yaw = 0f;
    private float pitch = 0f;

    // Step 2G joystick input. Updated by the touch overlay and consumed every frame.
    private volatile float joystickForward = 0f;
    private volatile float joystickSide = 0f;

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
                "uniform mat4 uView;" +
                "uniform vec3 uLightDir;" +
                "uniform float uAmbientLight;" +
                "varying vec4 vColor;" +
                "varying float vDistance;" +
                "varying float vLight;" +
                "varying vec2 vGroundXZ;" +
                "uniform float uGroundDetail;" +
                "void main() {" +
                "    vec4 eyePos = uView * aPosition;" +
                "    gl_Position = uMVP * aPosition;" +
                "    vColor = aColor;" +
                "    vDistance = length(eyePos.xyz);" +
                "    vec3 pseudoNormal = normalize(vec3(aPosition.x, aPosition.y * 1.35, aPosition.z));" +
                "    float diffuse = max(dot(pseudoNormal, normalize(uLightDir)), 0.0);" +
                "    vLight = uAmbientLight + diffuse * (1.0 - uAmbientLight);" +
                "    vGroundXZ = aPosition.xz;" +
                "}";

        String fragmentShaderCode =
                "precision mediump float;" +
                "varying vec4 vColor;" +
                "varying float vDistance;" +
                "varying float vLight;" +
                "varying vec2 vGroundXZ;" +
                "uniform float uGroundDetail;" +
                "uniform float uGroundDaylight;" +
                "void main() {" +
                "    float haze = smoothstep(65.0, 115.0, vDistance);" +
                "    haze *= 0.18;" +
                "    vec3 litColor = vColor.rgb * vLight;" +
                "    vec3 finalColor = litColor;" +
                "    if (uGroundDaylight > 0.5) {" +
                "        float broadA = 0.5 + 0.5 * sin(vGroundXZ.x * 0.105 + vGroundXZ.y * 0.052);" +
                "        float broadB = 0.5 + 0.5 * cos(vGroundXZ.x * 0.071 - vGroundXZ.y * 0.118);" +
                "        float daylightField = broadA * 0.55 + broadB * 0.45;" +
                "        float daylight = 0.90 + daylightField * 0.14;" +
                "        finalColor *= daylight;" +
                "    }" +
                "    if (uGroundDetail > 0.5) {" +
                "        float fineA = 0.5 + 0.5 * sin(vGroundXZ.x * 5.4 + sin(vGroundXZ.y * 1.75) * 1.15);" +
                "        float fineB = 0.5 + 0.5 * cos(vGroundXZ.y * 6.1 - sin(vGroundXZ.x * 1.55) * 1.05);" +
                "        float micro = 0.5 + 0.5 * sin(vGroundXZ.x * 11.5 + vGroundXZ.y * 9.3 + sin(vGroundXZ.x * 2.2 - vGroundXZ.y * 1.8));" +
                "        float surface = fineA * 0.42 + fineB * 0.38 + micro * 0.20;" +
                // Step 3B-1: subtle procedural grass detail. It uses fine
                // // overlapping fields instead of a texture image or extra geometry.
                "        float grassA = 0.5 + 0.5 * sin(vGroundXZ.x * 18.0 + sin(vGroundXZ.y * 3.4) * 1.7);" +
                "        float grassB = 0.5 + 0.5 * cos(vGroundXZ.y * 21.0 - sin(vGroundXZ.x * 2.8) * 1.4);" +
                "        float grassFine = grassA * 0.55 + grassB * 0.45;" +
                "        float grassMask = smoothstep(0.28, 0.78, grassFine);" +
                "        float grassShade = (grassMask - 0.5) * 0.045;" +
                "        float greenVariation = (surface - 0.5) * 0.075 + grassShade;" +
                "        finalColor *= 1.0 + greenVariation;" +
                "        float softEarth = smoothstep(0.79, 0.96, micro) * 0.035;" +
                "        vec3 earthHint = vec3(0.39, 0.31, 0.16) * vLight;" +
                "        finalColor = mix(finalColor, earthHint, softEarth);" +
                // Step 3B-2: tiny irregular soil detail blended into the grass.
                // Keep the mask sparse and low-contrast so there are no large brown patches.
                "        float soilA = 0.5 + 0.5 * sin(vGroundXZ.x * 3.15 + sin(vGroundXZ.y * 1.15) * 1.25);" +
                "        float soilB = 0.5 + 0.5 * cos(vGroundXZ.y * 3.75 - sin(vGroundXZ.x * 1.35) * 1.10);" +
                "        float soilMicro = 0.5 + 0.5 * sin(vGroundXZ.x * 8.2 - vGroundXZ.y * 6.7 + sin(vGroundXZ.x * 1.7 + vGroundXZ.y * 2.1));" +
                "        float soilField = soilA * 0.48 + soilB * 0.37 + soilMicro * 0.15;" +
                "        float soilMask = smoothstep(0.68, 0.84, soilField) * 0.055;" +
                "        vec3 subtleSoil = vec3(0.46, 0.35, 0.18) * vLight;" +
                "        finalColor = mix(finalColor, subtleSoil, soilMask);" +
                "    }" +
                "    vec3 hazeColor = vec3(0.70, 0.82, 0.90);" +
                "    finalColor = mix(finalColor, hazeColor, haze);" +
                "    gl_FragColor = vec4(finalColor, vColor.a);" +
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

        viewHandle =
                GLES20.glGetUniformLocation(
                        program,
                        "uView"
                );

        lightDirHandle =
                GLES20.glGetUniformLocation(
                        program,
                        "uLightDir"
                );

        ambientLightHandle =
                GLES20.glGetUniformLocation(
                        program,
                        "uAmbientLight"
                );

        groundDetailHandle =
                GLES20.glGetUniformLocation(
                        program,
                        "uGroundDetail"
                );

        groundDaylightHandle =
                GLES20.glGetUniformLocation(
                        program,
                        "uGroundDaylight"
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

        String cloudVertexShaderCode =
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

        String cloudFragmentShaderCode =
                "precision mediump float;" +
                "varying vec2 vLocal;" +
                "uniform vec4 uColor;" +
                "void main() {" +
                "    float d = length(vLocal);" +
                "    float alpha = 1.0 - smoothstep(0.45, 1.0, d);" +
                "    alpha *= 0.78;" +
                "    if (alpha < 0.015) discard;" +
                "    gl_FragColor = vec4(uColor.rgb, uColor.a * alpha);" +
                "}";

        int cloudVertexShader = loadShader(
                GLES20.GL_VERTEX_SHADER,
                cloudVertexShaderCode
        );

        int cloudFragmentShader = loadShader(
                GLES20.GL_FRAGMENT_SHADER,
                cloudFragmentShaderCode
        );

        cloudProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(cloudProgram, cloudVertexShader);
        GLES20.glAttachShader(cloudProgram, cloudFragmentShader);
        GLES20.glLinkProgram(cloudProgram);

        cloudPositionHandle = GLES20.glGetAttribLocation(cloudProgram, "aPosition");
        cloudVpHandle = GLES20.glGetUniformLocation(cloudProgram, "uVP");
        cloudCenterHandle = GLES20.glGetUniformLocation(cloudProgram, "uCenter");
        cloudRightHandle = GLES20.glGetUniformLocation(cloudProgram, "uRight");
        cloudUpHandle = GLES20.glGetUniformLocation(cloudProgram, "uUp");
        cloudSizeHandle = GLES20.glGetUniformLocation(cloudProgram, "uSize");
        cloudColorHandle = GLES20.glGetUniformLocation(cloudProgram, "uColor");

        createSky();
        createSun();
        createCloud();

        createCube();
        createGround();

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
        drawClouds();

        // Set the camera matrix once for the shared world shader.
        // The haze is deliberately very subtle and only affects distant geometry.
        GLES20.glUseProgram(program);
        GLES20.glUniformMatrix4fv(
                viewHandle,
                1,
                false,
                view,
                0
        );

        // Step 2E: soft directional daylight from the visible sun direction.
        // A generous ambient level keeps the world readable while the directional
        // component adds gentle light/shade variation to ground, trunks, foliage and rocks.
        GLES20.glUniform3f(
                lightDirHandle,
                0.0f,
                0.42f,
                -0.91f
        );

        GLES20.glUniform1f(
                ambientLightHandle,
                0.72f
        );

        // Apply joystick movement continuously while the finger is held.
        // A centered joystick produces zero movement.
        float currentJoystickForward = joystickForward;
        float currentJoystickSide = joystickSide;
        if (currentJoystickForward != 0f || currentJoystickSide != 0f) {
            addMovement(
                    currentJoystickForward * 0.055f,
                    currentJoystickSide * 0.055f
            );
        }

        // Step 3A-3: ground variation is baked into a smooth mesh. Keep the
        // previous flat-ground lighting treatment so daylight does not introduce
        // artificial diagonal shading over the natural color variation.
        groundFlatColor = true;
        GLES20.glUniform1f(ambientLightHandle, 1.0f);
        GLES20.glUniform1f(groundDetailHandle, 1.0f);
        GLES20.glUniform1f(groundDaylightHandle, 1.0f);
        drawGround();
        GLES20.glUniform1f(groundDaylightHandle, 0.0f);
        GLES20.glUniform1f(groundDetailHandle, 0.0f);
        groundFlatColor = false;
        GLES20.glUniform1f(ambientLightHandle, 0.72f);

        // Trees and rocks are intentionally not rendered in this baseline.
        // Their draw/geometry methods remain in the source for later manual placement.
    }

    // --------------------------------------------------
    // JOYSTICK
    // --------------------------------------------------

    public void setJoystickInput(float forward, float side) {
        joystickForward = Math.max(-1f, Math.min(1f, forward));
        joystickSide = Math.max(-1f, Math.min(1f, side));
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
    // CLOUDS - STEP 2C
    // --------------------------------------------------

    private void createCloud() {
        float[] vertices = {
                -1f, -1f,
                 1f, -1f,
                -1f,  1f,
                 1f, -1f,
                 1f,  1f,
                -1f,  1f
        };

        cloudBuffer =
                ByteBuffer
                        .allocateDirect(vertices.length * 4)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer();

        cloudBuffer.put(vertices).position(0);
    }

    private void drawClouds() {
        float cosPitch = (float) Math.cos(pitch);
        float sinPitch = (float) Math.sin(pitch);
        float sinYaw = (float) Math.sin(yaw);
        float cosYaw = (float) Math.cos(yaw);

        // Camera-facing basis. Cloud centers themselves remain fixed in world space.
        float rightX = cosYaw;
        float rightY = 0f;
        float rightZ = sinYaw;

        float upX = -sinYaw * sinPitch;
        float upY = cosPitch;
        float upZ = cosYaw * sinPitch;

        cloudBuffer.position(0);
        GLES20.glUseProgram(cloudProgram);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(
                GLES20.GL_SRC_ALPHA,
                GLES20.GL_ONE_MINUS_SRC_ALPHA
        );
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(false);

        GLES20.glUniformMatrix4fv(
                cloudVpHandle,
                1,
                false,
                getViewProjectionMatrix(),
                0
        );

        GLES20.glUniform3f(
                cloudRightHandle,
                rightX, rightY, rightZ
        );

        GLES20.glUniform3f(
                cloudUpHandle,
                upX, upY, upZ
        );

        GLES20.glUniform4f(
                cloudColorHandle,
                0.98f, 0.99f, 1.0f, 0.92f
        );

        for (Cloud cloud : clouds) {
            drawCloudPuff(cloud.x, cloud.y, cloud.z, cloud.scale, rightX, rightY, rightZ, upX, upY, upZ);
        }

        GLES20.glDepthMask(true);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(program);
    }

    private void drawCloudPuff(
            float centerX,
            float centerY,
            float centerZ,
            float scale,
            float rightX,
            float rightY,
            float rightZ,
            float upX,
            float upY,
            float upZ) {

        // A compact cluster of overlapping soft puffs makes each cloud less geometric.
        float[][] puffs = {
                {-0.95f, -0.02f, 0.82f},
                {-0.45f,  0.20f, 1.05f},
                { 0.00f,  0.28f, 1.18f},
                { 0.48f,  0.16f, 1.00f},
                { 0.90f, -0.02f, 0.76f},
                { 0.18f, -0.08f, 0.95f}
        };

        for (float[] puff : puffs) {
            float px = centerX + rightX * puff[0] * scale + upX * puff[1] * scale;
            float py = centerY + rightY * puff[0] * scale + upY * puff[1] * scale;
            float pz = centerZ + rightZ * puff[0] * scale + upZ * puff[1] * scale;

            GLES20.glUniform3f(
                    cloudCenterHandle,
                    px, py, pz
            );

            GLES20.glUniform1f(
                    cloudSizeHandle,
                    puff[2] * scale
            );

            cloudBuffer.position(0);
            GLES20.glVertexAttribPointer(
                    cloudPositionHandle,
                    2,
                    GLES20.GL_FLOAT,
                    false,
                    0,
                    cloudBuffer
            );
            GLES20.glEnableVertexAttribArray(cloudPositionHandle);

            GLES20.glDrawArrays(
                    GLES20.GL_TRIANGLES,
                    0,
                    6
            );

            GLES20.glDisableVertexAttribArray(cloudPositionHandle);
        }
    }

    // --------------------------------------------------
    // GROUND
    // --------------------------------------------------

    private void drawGround() {

        // Step 3B-1: keep the continuous ground mesh and add the new grass
        // detail procedurally in the shader, without texture images or extra geometry.
        drawGroundMesh();
    }

    private void createGround() {

        // Step 3A-3: use a finer grid so earth-tone variation stays small,
        // soft, and naturally blended across the field.
        final int cells = 36;
        final float min = -30f;
        final float max = 30f;
        final float step = (max - min) / cells;

        final int verticesPerCell = 6;
        groundVertexCount = cells * cells * verticesPerCell;

        float[] vertices = new float[groundVertexCount * 3];
        float[] colors = new float[groundVertexCount * 4];

        int vi = 0;
        int ci = 0;

        for (int row = 0; row < cells; row++) {
            float z0 = min + row * step;
            float z1 = z0 + step;

            for (int col = 0; col < cells; col++) {
                float x0 = min + col * step;
                float x1 = x0 + step;

                // Two triangles per small ground cell.
                vi = putGroundVertex(vertices, colors, vi, ci, x0, -0.08f, z0);
                ci += 4;
                vi = putGroundVertex(vertices, colors, vi, ci, x1, -0.08f, z0);
                ci += 4;
                vi = putGroundVertex(vertices, colors, vi, ci, x1, -0.08f, z1);
                ci += 4;

                vi = putGroundVertex(vertices, colors, vi, ci, x0, -0.08f, z0);
                ci += 4;
                vi = putGroundVertex(vertices, colors, vi, ci, x1, -0.08f, z1);
                ci += 4;
                vi = putGroundVertex(vertices, colors, vi, ci, x0, -0.08f, z1);
                ci += 4;
            }
        }

        groundVertexBuffer = ByteBuffer
                .allocateDirect(vertices.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        groundVertexBuffer.put(vertices).position(0);

        groundColorBuffer = ByteBuffer
                .allocateDirect(colors.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        groundColorBuffer.put(colors).position(0);
    }

    private int putGroundVertex(
            float[] vertices,
            float[] colors,
            int vertexIndex,
            int colorIndex,
            float x,
            float y,
            float z) {

        vertices[vertexIndex++] = x;
        vertices[vertexIndex++] = y;
        vertices[vertexIndex++] = z;

        /*
         * Step 3A-3: Natural earth-tone variation.
         *
         * Use several smooth, non-directional noise fields.  The earth tone
         * is deliberately muted and localized: from far away the field still
         * reads as green, while nearby ground shows small warm soil variations.
         */
        float noiseA =
                0.5f +
                0.5f * (float) Math.sin(
                        x * 0.82f +
                        1.10f * (float) Math.sin(z * 0.57f + 0.8f)
                );

        float noiseB =
                0.5f +
                0.5f * (float) Math.cos(
                        z * 1.05f -
                        0.90f * (float) Math.sin(x * 0.63f - 0.5f)
                );

        float noiseC =
                0.5f +
                0.5f * (float) Math.sin(
                        x * 1.42f +
                        z * 1.18f +
                        0.75f * (float) Math.cos(x * 0.46f - z * 0.52f)
                );

        float patch =
                0.46f * noiseA +
                0.36f * noiseB +
                0.18f * noiseC;

        // Localized soft patches; the upper limit stays low enough that green
        // remains the dominant field color at normal viewing distance.
        float earthMask =
                smoothStep(
                        0.56f,
                        0.73f,
                        patch
                ) * 0.42f;

        final float baseR = 0.250f;
        final float baseG = 0.552f;
        final float baseB = 0.200f;

        // Warm muted soil/earth tone. It is blended rather than painted on.
        final float earthR = 0.430f;
        final float earthG = 0.325f;
        final float earthB = 0.120f;

        colors[colorIndex] =
                baseR +
                (earthR - baseR) * earthMask;

        colors[colorIndex + 1] =
                baseG +
                (earthG - baseG) * earthMask;

        colors[colorIndex + 2] =
                baseB +
                (earthB - baseB) * earthMask;
        colors[colorIndex + 3] = 1f;

        return vertexIndex;
    }

    private float smoothStep(float edge0, float edge1, float value) {
        float t = (value - edge0) / (edge1 - edge0);
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private void drawGroundMesh() {

        Matrix.setIdentityM(model, 0);

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

        GLES20.glUseProgram(program);

        GLES20.glUniformMatrix4fv(
                mvpHandle,
                1,
                false,
                mvp,
                0
        );

        groundVertexBuffer.position(0);
        GLES20.glVertexAttribPointer(
                positionHandle,
                3,
                GLES20.GL_FLOAT,
                false,
                3 * 4,
                groundVertexBuffer
        );
        GLES20.glEnableVertexAttribArray(positionHandle);

        groundColorBuffer.position(0);
        GLES20.glVertexAttribPointer(
                colorHandle,
                4,
                GLES20.GL_FLOAT,
                false,
                4 * 4,
                groundColorBuffer
        );
        GLES20.glEnableVertexAttribArray(colorHandle);

        GLES20.glDrawArrays(
                GLES20.GL_TRIANGLES,
                0,
                groundVertexCount
        );

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(colorHandle);
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
                    groundFlatColor
                            ? 1.0f
                            : 0.86f +
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
