package com.aadventure.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TouchRoot root = new TouchRoot(this);

        WorldView worldView = new WorldView(this);
        root.addView(worldView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        JoystickOverlay joystick = new JoystickOverlay(this, worldView.getRenderer());
        root.addView(joystick, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        root.setTouchTargets(worldView, joystick);
        setContentView(root);
    }

    /**
     * Sends the same MotionEvent stream to both controls.
     * WorldView owns right-side camera pointers; JoystickOverlay owns
     * left-side movement pointers. This makes two-finger input work
     * regardless of which side is touched first.
     */
    private static final class TouchRoot extends FrameLayout {
        private WorldView worldView;
        private JoystickOverlay joystick;

        TouchRoot(android.content.Context context) {
            super(context);
        }

        void setTouchTargets(WorldView worldView, JoystickOverlay joystick) {
            this.worldView = worldView;
            this.joystick = joystick;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            boolean worldHandled = false;
            boolean joystickHandled = false;

            if (worldView != null) {
                worldHandled = worldView.dispatchTouchEvent(event);
            }

            if (joystick != null) {
                joystickHandled = joystick.dispatchTouchEvent(event);
            }

            return worldHandled || joystickHandled || true;
        }
    }
}
