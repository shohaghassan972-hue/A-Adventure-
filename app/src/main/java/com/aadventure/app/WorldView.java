package com.aadventure.app;

import android.content.Context;
import android.opengl.GLSurfaceView;

public class WorldView extends GLSurfaceView {

    private final WorldRenderer renderer;

    public WorldView(Context context) {
        super(context);

        // OpenGL ES 2.0
        setEGLContextClientVersion(2);

        renderer = new WorldRenderer();

        setRenderer(renderer);

        // Render continuously for smooth camera movement.
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
    }
}
