package com.aadventure.app;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

public class WorldView extends GLSurfaceView {

    private final WorldRenderer renderer;

    private float lastX;
    private float lastY;

    private boolean movingTouch = false;
    private boolean lookingTouch = false;

    public WorldView(Context context) {
        super(context);

        setEGLContextClientVersion(2);

        renderer = new WorldRenderer();

        setRenderer(renderer);

        setRenderMode(
                GLSurfaceView.RENDERMODE_CONTINUOUSLY
        );

        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {

            case MotionEvent.ACTION_DOWN:

                lastX = x;
                lastY = y;

                /*
                 * Left side = movement.
                 * Right side = camera looking.
                 */
                if (x < getWidth() * 0.45f) {
                    movingTouch = true;
                    lookingTouch = false;
                } else {
                    lookingTouch = true;
                    movingTouch = false;
                }

                return true;

            case MotionEvent.ACTION_MOVE:

                float dx = x - lastX;
                float dy = y - lastY;

                if (movingTouch) {

                    /*
                     * Swipe direction controls movement.
                     *
                     * Up    = forward
                     * Down  = backward
                     * Left  = strafe left
                     * Right = strafe right
                     */

                    float moveForward = -dy * 0.035f;
                    float moveSide = dx * 0.035f;

                    renderer.addMovement(
                            moveForward,
                            moveSide
                    );
                }

                if (lookingTouch) {

                    /*
                     * Horizontal drag rotates camera.
                     */
                    float turnAmount = dx * 0.008f;

                    renderer.addYaw(
                            turnAmount
                    );
                }

                lastX = x;
                lastY = y;

                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:

                movingTouch = false;
                lookingTouch = false;

                return true;

            default:
                return true;
        }
    }
        }
