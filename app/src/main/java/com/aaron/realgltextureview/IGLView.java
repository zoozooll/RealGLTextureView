package com.aaron.realgltextureview;

public interface IGLView {

    EGLConfigChooser getEGLConfigChooser();

    int getEGLContextClientVersion();

    EGLContextFactory getEGLContextFactory();

    EGLWindowSurfaceFactory getEGLWindowSurfaceFactory();

    Object getSurfaceObject();

    GLWrapper getGLWrapper();

    int getDebugFlags();

    boolean isPreserveEGLContextOnPause();

    Renderer getRenderer();
}
