package com.aadventure.app;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WorldRenderer implements GLSurfaceView.Renderer {

    private static final float DEG = (float) Math.PI / 180.0f;

    private final float[] viewMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] vpMatrix = new float[16];
    private final float[] modelMatrix = new float[16];
    private final float[] mvpMatrix = new float[16];

    private float cameraX = 0.0f;
    private float cameraY = 1.65f;
    private float cameraZ = 4.0f;

    private float yaw = 0.0f;
    private float pitch = 0.0f;

    private final List<Tree> trees = new ArrayList<>();
    private final List<Rock> rocks = new ArrayList<>();

    private int program;
    private int positionHandle;
    private int colorHandle;
    private int mvpHandle;

    private FloatBuffer cubeBuffer;
    private FloatBuffer rockBuffer;

    private static class Tree {
        float x, z, s;
        Tree(float x, float z, float s) {
            this.x = x;
            this.z = z;
            this.s = s;
        }
    }

    private static class Rock {
        float x, z, s;
        Rock(float x, float z, float s) {
            this.x = x;
            this.z = z;
            this.s = s;
        }
    }

    @Override
    public void onSurfaceCreated(javax.microedition.khronos.egl.EGLConfig config) {
        GLES20.glClearColor(0.42f, 0.68f, 0.92f, 1.0f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
        colorHandle = GLES20.glGetUniformLocation(program, "uColor");
        mvpHandle = GLES20.glGetUniformLocation(program, "uMVP");

        cubeBuffer = createFloatBuffer(CUBE_VERTICES);
        rockBuffer = createFloatBuffer(ROCK_VERTICES);

        generateWorld();
    }

    @Override
    public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);

        float aspect = (float) width / Math.max(1, height);
        Matrix.perspectiveM(projectionMatrix, 0, 70.0f, aspect, 0.1f, 300.0f);
    }

    @Override
    public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        updateCameraMatrix();

        GLES20.glUseProgram(program);

        drawGround();
        drawWorldObjects();
    }

    private void updateCameraMatrix() {
        float yawRad = yaw * DEG;
        float pitchRad = pitch * DEG;

        float cosPitch = (float) Math.cos(pitchRad);
        float sinPitch = (float) Math.sin(pitchRad);
        float sinYaw = (float) Math.sin(yawRad);
        float cosYaw = (float) Math.cos(yawRad);

        float lookX = cameraX + cosPitch * sinYaw;
        float lookY = cameraY + sinPitch;
        float lookZ = cameraZ - cosPitch * cosYaw;

        Matrix.setLookAtM(
                viewMatrix,
                0,
                cameraX, cameraY, cameraZ,
                lookX, lookY, lookZ,
                0.0f, 1.0f, 0.0f
        );

        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
    }

    public void addYaw(float amount) {
        yaw += amount;
        if (yaw > 360.0f) yaw -= 360.0f;
        if (yaw < -360.0f) yaw += 360.0f;
    }

    public void addPitch(float amount) {
        pitch += amount;

        // Nearly straight up/down, avoiding the look-at singularity at exactly 90°.
        if (pitch > 89.0f) pitch = 89.0f;
        if (pitch < -89.0f) pitch = -89.0f;
    }

    public void addMovement(float forward, float side) {
        float yawRad = yaw * DEG;

        float forwardX = (float) Math.sin(yawRad);
        float forwardZ = -(float) Math.cos(yawRad);

        float rightX = (float) Math.cos(yawRad);
        float rightZ = (float) Math.sin(yawRad);

        cameraX += forwardX * forward + rightX * side;
        cameraZ += forwardZ * forward + rightZ * side;

        // Keep the camera inside the simple test world.
        cameraX = clamp(cameraX, -90.0f, 90.0f);
        cameraZ = clamp(cameraZ, -90.0f, 90.0f);
    }

    private void generateWorld() {
        Random random = new Random(20260917L);

        trees.clear();
        rocks.clear();

        for (int i = 0; i < 55; i++) {
            float x = -75.0f + random.nextFloat() * 150.0f;
            float z = -75.0f + random.nextFloat() * 150.0f;

            if (Math.abs(x) < 7.0f && Math.abs(z - 4.0f) < 7.0f) {
                continue;
            }

            float scale = 0.8f + random.nextFloat() * 0.8f;
            trees.add(new Tree(x, z, scale));
        }

        for (int i = 0; i < 45; i++) {
            float x = -80.0f + random.nextFloat() * 160.0f;
            float z = -80.0f + random.nextFloat() * 160.0f;

            float scale = 0.5f + random.nextFloat() * 0.9f;
            rocks.add(new Rock(x, z, scale));
        }
    }

    private void drawGround() {
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, 0.0f, -0.08f, 0.0f);
        Matrix.scaleM(modelMatrix, 0, 180.0f, 0.1f, 180.0f);

        drawCube(modelMatrix, 0.25f, 0.52f, 0.20f, 1.0f);
    }

    private void drawWorldObjects() {
        for (Tree tree : trees) {
            drawTree(tree);
        }

        for (Rock rock : rocks) {
            drawRock(rock);
        }
    }

    private void drawTree(Tree tree) {
        // Trunk
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, tree.x, 1.0f * tree.s, tree.z);
        Matrix.scaleM(modelMatrix, 0, 0.35f * tree.s, 1.0f * tree.s, 0.35f * tree.s);
        drawCube(modelMatrix, 0.32f, 0.18f, 0.08f, 1.0f);

        // Main foliage
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, tree.x, 2.15f * tree.s, tree.z);
        Matrix.scaleM(modelMatrix, 0, 1.25f * tree.s, 1.25f * tree.s, 1.25f * tree.s);
        drawCube(modelMatrix, 0.08f, 0.38f, 0.10f, 1.0f);

        // Side foliage masses
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0,
                tree.x - 0.75f * tree.s,
                1.85f * tree.s,
                tree.z);
        Matrix.scaleM(modelMatrix, 0,
                0.75f * tree.s,
                0.75f * tree.s,
                0.75f * tree.s);
        drawCube(modelMatrix, 0.10f, 0.42f, 0.12f, 1.0f);

        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0,
                tree.x + 0.75f * tree.s,
                1.85f * tree.s,
                tree.z);
        Matrix.scaleM(modelMatrix, 0,
                0.75f * tree.s,
                0.75f * tree.s,
                0.75f * tree.s);
        drawCube(modelMatrix, 0.10f, 0.42f, 0.12f, 1.0f);
    }

    private void drawRock(Rock rock) {
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, rock.x, 0.35f * rock.s, rock.z);
        Matrix.scaleM(modelMatrix, 0,
                1.15f * rock.s,
                0.55f * rock.s,
                0.95f * rock.s);

        drawRockMesh(modelMatrix, 0.35f, 0.34f, 0.30f, 1.0f);
    }

    private void drawCube(float[] model, float r, float g, float b, float a) {
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, model, 0);
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0);
        GLES20.glUniform4f(colorHandle, r, g, b, a);

        cubeBuffer.position(0);
        GLES20.glVertexAttribPointer(
                positionHandle,
                3,
                GLES20.GL_FLOAT,
                false,
                0,
                cubeBuffer
        );
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, CUBE_VERTICES.length / 3);
        GLES20.glDisableVertexAttribArray(positionHandle);
    }

    private void drawRockMesh(float[] model, float r, float g, float b, float a) {
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, model, 0);
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0);
        GLES20.glUniform4f(colorHandle, r, g, b, a);

        rockBuffer.position(0);
        GLES20.glVertexAttribPointer(
                positionHandle,
                3,
                GLES20.GL_FLOAT,
                false,
                0,
                rockBuffer
        );
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, ROCK_VERTICES.length / 3);
        GLES20.glDisableVertexAttribArray(positionHandle);
    }

    private static FloatBuffer createFloatBuffer(float[] data) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4);
        bb.order(ByteOrder.nativeOrder());

        FloatBuffer buffer = bb.asFloatBuffer();
        buffer.put(data);
        buffer.position(0);
        return buffer;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);

        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, vertexShader);
        GLES20.glAttachShader(p, fragmentShader);
        GLES20.glLinkProgram(p);

        int[] status = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0);

        if (status[0] == 0) {
            String error = GLES20.glGetProgramInfoLog(p);
            GLES20.glDeleteProgram(p);
            throw new RuntimeException("Program link failed: " + error);
        }

        return p;
    }

    private static int loadShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);

        if (status[0] == 0) {
            String error = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new RuntimeException("Shader compile failed: " + error);
        }

        return shader;
    }

    private static final String VERTEX_SHADER =
            "attribute vec4 aPosition;\n" +
            "uniform mat4 uMVP;\n" +
            "void main() {\n" +
            "    gl_Position = uMVP * aPosition;\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n" +
            "uniform vec4 uColor;\n" +
            "void main() {\n" +
            "    gl_FragColor = uColor;\n" +
            "}\n";

    private static final float[] CUBE_VERTICES = {
            -1,-1,-1,  1,-1,-1,  1, 1,-1,
            -1,-1,-1,  1, 1,-1, -1, 1,-1,

            -1,-1, 1,  1, 1, 1,  1,-1, 1,
            -1,-1, 1, -1, 1, 1,  1, 1, 1,

            -1,-1,-1, -1, 1,-1, -1, 1, 1,
            -1,-1,-1, -1, 1, 1, -1,-1, 1,

             1,-1,-1,  1,-1, 1,  1, 1, 1,
             1,-1,-1,  1, 1, 1,  1, 1,-1,

            -1, 1,-1,  1, 1,-1,  1, 1, 1,
            -1, 1,-1,  1, 1, 1, -1, 1, 1,

            -1,-1,-1, -1,-1, 1,  1,-1, 1,
            -1,-1,-1,  1,-1, 1,  1,-1,-1
    };

    private static final float[] ROCK_VERTICES = {
             0, 1, 0,  -1,-0.2f,-0.8f,  0.8f,-0.3f,-0.6f,
             0, 1, 0,   0.8f,-0.3f,-0.6f,  1,-0.1f,0.5f,
             0, 1, 0,   1,-0.1f,0.5f,  -0.2f,-0.25f,1,
             0, 1, 0,  -0.2f,-0.25f,1,  -1,-0.2f,-0.8f,

             0,-0.8f,0,   0.8f,-0.3f,-0.6f,  -1,-0.2f,-0.8f,
             0,-0.8f,0,   1,-0.1f,0.5f,  0.8f,-0.3f,-0.6f,
             0,-0.8f,0,  -0.2f,-0.25f,1,  1,-0.1f,0.5f,
             0,-0.8f,0,  -1,-0.2f,-0.8f,  -0.2f,-0.25f,1
    };
}
