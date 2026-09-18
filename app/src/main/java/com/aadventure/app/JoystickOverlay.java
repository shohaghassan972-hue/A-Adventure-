package com.aadventure.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/**
 * Light, touch-position joystick for the left 45% of the screen.
 * The joystick appears exactly where the first left-side touch lands.
 */
public class JoystickOverlay extends View {

    private final WorldRenderer renderer;
    private final Paint outerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int joystickPointerId = MotionEvent.INVALID_POINTER_ID;
    private float centerX;
    private float centerY;
    private float knobX;
    private float knobY;
    private float radius;
    private boolean visible;

    private float density;

    public JoystickOverlay(Context context, WorldRenderer renderer) {
        super(context);
        this.renderer = renderer;
        density = getResources().getDisplayMetrics().density;
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        outerPaint.setStyle(Paint.Style.FILL);
        outerPaint.setColor(0x4DFFFFFF); // light and transparent

        innerPaint.setStyle(Paint.Style.FILL);
        innerPaint.setColor(0x80FFFFFF);

        setWillNotDraw(false);
        setClickable(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!visible) {
            return;
        }

        // 64dp outer radius, with a smaller soft knob.
        radius = 64f * density;
        float knobRadius = 26f * density;

        canvas.drawCircle(centerX, centerY, radius, outerPaint);
        canvas.drawCircle(knobX, knobY, knobRadius, innerPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        final int actionIndex = event.getActionIndex();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                return beginJoystick(event, 0);

            case MotionEvent.ACTION_POINTER_DOWN:
                // If a second pointer starts in the left 45%, give it to the
                // joystick only when the joystick is currently unused.
                if (joystickPointerId == MotionEvent.INVALID_POINTER_ID) {
                    return beginJoystick(event, actionIndex);
                }
                return false;

            case MotionEvent.ACTION_MOVE:
                return updateJoystick(event);

            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerId(actionIndex) == joystickPointerId) {
                    releaseJoystick();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                releaseJoystick();
                return true;

            default:
                return true;
        }
    }

    private boolean beginJoystick(MotionEvent event, int pointerIndex) {
        final float x = event.getX(pointerIndex);
        final float y = event.getY(pointerIndex);

        // Only the left 45% is a joystick touch area.
        if (x >= getWidth() * 0.45f) {
            return false;
        }

        joystickPointerId = event.getPointerId(pointerIndex);
        centerX = x;
        centerY = y;
        knobX = x;
        knobY = y;
        visible = true;

        renderer.setJoystickInput(0f, 0f);
        invalidate();
        return true;
    }

    private boolean updateJoystick(MotionEvent event) {
        if (joystickPointerId == MotionEvent.INVALID_POINTER_ID) {
            return false;
        }

        final int pointerIndex = event.findPointerIndex(joystickPointerId);
        if (pointerIndex < 0) {
            releaseJoystick();
            return true;
        }

        final float x = event.getX(pointerIndex);
        final float y = event.getY(pointerIndex);

        final float dx = x - centerX;
        final float dy = y - centerY;
        final float maxDistance = 64f * density;
        final float distance = (float) Math.sqrt(dx * dx + dy * dy);

        float clampedDx = dx;
        float clampedDy = dy;

        if (distance > maxDistance && distance > 0f) {
            final float scale = maxDistance / distance;
            clampedDx *= scale;
            clampedDy *= scale;
        }

        knobX = centerX + clampedDx;
        knobY = centerY + clampedDy;

        // Up = forward, down = backward, right = strafe right, left = strafe left.
        final float side = clampedDx / maxDistance;
        final float forward = -clampedDy / maxDistance;

        renderer.setJoystickInput(forward, side);
        invalidate();
        return true;
    }

    private void releaseJoystick() {
        joystickPointerId = MotionEvent.INVALID_POINTER_ID;
        visible = false;
        renderer.setJoystickInput(0f, 0f);
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        renderer.setJoystickInput(0f, 0f);
        super.onDetachedFromWindow();
    }
}
