package com.aadventure.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;

public class WorldView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<WorldObject> objects = new ArrayList<>();

    // Current camera position
    private float cameraX = 0f;
    private float cameraY = 0f;

    // Target camera position
    private float targetCameraX = 0f;
    private float targetCameraY = 0f;

    // Touch position
    private float lastTouchX;
    private float lastTouchY;

    private boolean touching = false;

    // Smoothness value.
    // Higher = faster response.
    // Lower = smoother/slower response.
    private static final float CAMERA_SMOOTHING = 0.18f;

    // World limits
    private static final float WORLD_LEFT = -1900f;
    private static final float WORLD_RIGHT = 1900f;
    private static final float WORLD_TOP = -1300f;
    private static final float WORLD_BOTTOM = 1300f;

    static class WorldObject {

        float x;
        float y;
        float scale;
        int type;

        WorldObject(float x, float y, float scale, int type) {
            this.x = x;
            this.y = y;
            this.scale = scale;
            this.type = type;
        }
    }

    public WorldView(Context context) {
        super(context);

        setFocusable(true);
        setFocusableInTouchMode(true);

        createWorld();

        // Request the first frame.
        postInvalidateOnAnimation();
    }

    private void createWorld() {

        objects.clear();

        // Trees
        for (int i = 0; i < 28; i++) {

            float x = -1800f + (i * 347) % 3600;
            float y = -1200f + (i * 521) % 2400;

            float scale = 0.8f + (i % 4) * 0.12f;

            objects.add(
                    new WorldObject(
                            x,
                            y,
                            scale,
                            0
                    )
            );
        }

        // Rocks
        for (int i = 0; i < 34; i++) {

            float x = -1700f + (i * 233) % 3400;
            float y = -1100f + (i * 419) % 2200;

            float scale = 0.7f + (i % 3) * 0.15f;

            objects.add(
                    new WorldObject(
                            x,
                            y,
                            scale,
                            1
                    )
            );
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        // Smooth camera movement.
        updateCameraSmoothly();

        // Background.
        canvas.drawColor(Color.rgb(125, 190, 88));

        float scale = calculateWorldScale();

        canvas.save();

        // Screen center.
        canvas.translate(
                getWidth() / 2f,
                getHeight() / 2f
        );

        // World scale.
        canvas.scale(scale, scale);

        // Camera.
        canvas.translate(
                -cameraX,
                -cameraY
        );

        drawWorld(canvas);

        canvas.restore();

        drawInterface(canvas);

        // Continue drawing smoothly.
        postInvalidateOnAnimation();
    }

    private float calculateWorldScale() {

        float widthScale = getWidth() / 1800f;
        float heightScale = getHeight() / 1100f;

        float scale = Math.min(
                widthScale,
                heightScale
        );

        return Math.max(
                scale,
                0.35f
        );
    }

    private void updateCameraSmoothly() {

        float deltaX = targetCameraX - cameraX;
        float deltaY = targetCameraY - cameraY;

        cameraX += deltaX * CAMERA_SMOOTHING;
        cameraY += deltaY * CAMERA_SMOOTHING;

        // Stop tiny floating-point movements.
        if (Math.abs(deltaX) < 0.01f) {
            cameraX = targetCameraX;
        }

        if (Math.abs(deltaY) < 0.01f) {
            cameraY = targetCameraY;
        }
    }

    private void drawWorld(Canvas canvas) {

        // Main world ground.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(112, 178, 78));

        canvas.drawRect(
                WORLD_LEFT,
                WORLD_TOP,
                WORLD_RIGHT,
                WORLD_BOTTOM,
                paint
        );

        // Objects.
        for (WorldObject object : objects) {

            if (object.type == 0) {
                drawTree(canvas, object);
            } else {
                drawRock(canvas, object);
            }
        }
    }

    private void drawTree(
            Canvas canvas,
            WorldObject object
    ) {

        float x = object.x;
        float y = object.y;
        float s = object.scale;

        // Tree trunk.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(105, 72, 42));

        canvas.drawRect(
                x - 14f * s,
                y,
                x + 14f * s,
                y + 90f * s,
                paint
        );

        // Main leaves.
        paint.setColor(Color.rgb(45, 120, 48));

        canvas.drawCircle(
                x,
                y - 30f * s,
                58f * s,
                paint
        );

        canvas.drawCircle(
                x - 38f * s,
                y - 5f * s,
                45f * s,
                paint
        );

        canvas.drawCircle(
                x + 38f * s,
                y - 5f * s,
                45f * s,
                paint
        );
    }

    private void drawRock(
            Canvas canvas,
            WorldObject object
    ) {

        float x = object.x;
        float y = object.y;
        float s = object.scale;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(105, 105, 100));

        Path path = new Path();

        path.moveTo(
                x - 45f * s,
                y + 20f * s
        );

        path.lineTo(
                x - 28f * s,
                y - 22f * s
        );

        path.lineTo(
                x + 12f * s,
                y - 38f * s
        );

        path.lineTo(
                x + 48f * s,
                y - 5f * s
        );

        path.lineTo(
                x + 28f * s,
                y + 28f * s
        );

        path.close();

        canvas.drawPath(
                path,
                paint
        );
    }

    private void drawInterface(Canvas canvas) {

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);

        paint.setTypeface(
                Typeface.DEFAULT_BOLD
        );

        paint.setTextSize(34f);

        canvas.drawText(
                "A Adventure 0.1.1v",
                28f,
                46f,
                paint
        );

        paint.setTypeface(
                Typeface.DEFAULT
        );

        paint.setTextSize(22f);

        canvas.drawText(
                "Drag the screen to explore the whole map",
                28f,
                78f,
                paint
        );
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        switch (event.getActionMasked()) {

            case MotionEvent.ACTION_DOWN:

                lastTouchX = event.getX();
                lastTouchY = event.getY();

                touching = true;

                return true;

            case MotionEvent.ACTION_MOVE:

                if (!touching) {
                    return true;
                }

                float dx = event.getX() - lastTouchX;
                float dy = event.getY() - lastTouchY;

                float scale = calculateWorldScale();

                if (scale <= 0f) {
                    scale = 1f;
                }

                // Move target instead of moving camera directly.
                targetCameraX -= dx / scale;
                targetCameraY -= dy / scale;

                clampTargetCamera();

                lastTouchX = event.getX();
                lastTouchY = event.getY();

                return true;

            case MotionEvent.ACTION_UP:

                touching = false;

                return true;

            case MotionEvent.ACTION_CANCEL:

                touching = false;

                return true;

            default:

                return true;
        }
    }

    private void clampTargetCamera() {

        targetCameraX = Math.max(
                -900f,
                Math.min(
                        900f,
                        targetCameraX
                )
        );

        targetCameraY = Math.max(
                -650f,
                Math.min(
                        650f,
                        targetCameraY
                )
        );
    }
            }
