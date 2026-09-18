package com.aadventure.app;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

public class WorldView extends GLSurfaceView {

    private final WorldRenderer renderer;

    // Multi-touch pointer tracking.
    // One finger can control movement while another controls camera look.
    private int movingPointerId = MotionEvent.INVALID_POINTER_ID;
    private int lookingPointerId = MotionEvent.INVALID_POINTER_ID;

    private float movingLastX;
    private float movingLastY;
    private float lookingLastX;
    private float lookingLastY;

    public WorldView(Context context) {
        super(context);

        setEGLContextClientVersion(2);

        renderer = new WorldRenderer();

        setRenderer(renderer);

        // Render continuously for smooth movement and camera.
        setRenderMode(
                GLSurfaceView.RENDERMODE_CONTINUOUSLY
        );

        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
    }

    /**
     * Exposes the renderer to the Step 2G joystick overlay.
     */
    public WorldRenderer getRenderer() {
        return renderer;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        final int action = event.getActionMasked();
        final int actionIndex = event.getActionIndex();

        switch (action) {

            case MotionEvent.ACTION_DOWN: {
                // First finger.
                registerPointer(event, actionIndex);
                return true;
            }

            case MotionEvent.ACTION_POINTER_DOWN: {
                // Additional finger. Keep the first finger's control active.
                registerPointer(event, actionIndex);
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                updatePointer(event, movingPointerId, true);
                updatePointer(event, lookingPointerId, false);
                return true;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                // Only release the pointer that actually lifted.
                final int pointerId = event.getPointerId(actionIndex);

                if (pointerId == movingPointerId) {
                    movingPointerId = MotionEvent.INVALID_POINTER_ID;
                }

                if (pointerId == lookingPointerId) {
                    lookingPointerId = MotionEvent.INVALID_POINTER_ID;
                }

                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                movingPointerId = MotionEvent.INVALID_POINTER_ID;
                lookingPointerId = MotionEvent.INVALID_POINTER_ID;
                return true;
            }

            default:
                return true;
        }
    }

    /**
     * Assigns a new finger to movement or camera look based on where it touches.
     * Left side = movement, right side = camera looking.
     */
    private void registerPointer(MotionEvent event, int pointerIndex) {

        final int pointerId = event.getPointerId(pointerIndex);
        final float x = event.getX(pointerIndex);
        final float y = event.getY(pointerIndex);

        if (x < getWidth() * 0.45f) {

            if (movingPointerId == MotionEvent.INVALID_POINTER_ID) {
                movingPointerId = pointerId;
                movingLastX = x;
                movingLastY = y;
            }

        } else {

            if (lookingPointerId == MotionEvent.INVALID_POINTER_ID) {
                lookingPointerId = pointerId;
                lookingLastX = x;
                lookingLastY = y;
            }
        }
    }

    /**
     * Updates one active pointer without affecting the other finger.
     */
    private void updatePointer(
            MotionEvent event,
            int pointerId,
            boolean isMovement
    ) {

        if (pointerId == MotionEvent.INVALID_POINTER_ID) {
            return;
        }

        final int pointerIndex = event.findPointerIndex(pointerId);

        if (pointerIndex < 0) {
            return;
        }

        final float x = event.getX(pointerIndex);
        final float y = event.getY(pointerIndex);

        if (isMovement) {

            float dx = x - movingLastX;
            float dy = y - movingLastY;

            /*
             * Movement
             *
             * Up    = forward
             * Down  = backward
             * Left  = strafe left
             * Right = strafe right
             */

            float moveForward =
                    -dy * 0.035f;

            float moveSide =
                    dx * 0.035f;

            renderer.addMovement(
                    moveForward,
                    moveSide
            );

            movingLastX = x;
            movingLastY = y;

        } else {

            float dx = x - lookingLastX;
            float dy = y - lookingLastY;

            /*
             * Camera sensitivity remains unchanged.
             * Horizontal = yaw
             * Vertical   = pitch
             */
            float turnAmount =
                    dx * 0.004f;

            float lookAmount =
                    -dy * 0.004f;

            renderer.addYaw(
                    turnAmount
            );

            renderer.addPitch(
                    lookAmount
            );

            lookingLastX = x;
            lookingLastY = y;
        }
    }
}
