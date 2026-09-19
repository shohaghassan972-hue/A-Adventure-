package com.aadventure.app;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

public class WorldView extends GLSurfaceView {

    private final WorldRenderer renderer;

    // The joystick overlay owns all left-side movement touches.
    // WorldView only owns right-side camera-look touches.
    private int lookingPointerId = MotionEvent.INVALID_POINTER_ID;

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
                // Left-side movement is handled exclusively by JoystickOverlay.
                // WorldView only processes the active right-side camera pointer.
                updatePointer(event, lookingPointerId);
                return true;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                // Only release the pointer that actually lifted.
                final int pointerId = event.getPointerId(actionIndex);

                if (pointerId == lookingPointerId) {
                    lookingPointerId = MotionEvent.INVALID_POINTER_ID;
                }

                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
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
            // Left-side touch belongs exclusively to JoystickOverlay.
            // Do not register it here; this prevents two movement systems
            // from consuming the same finger and causing timing jitter.
            return;
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
            int pointerId
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
