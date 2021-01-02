package com.aaronlee.iglview;

public interface IGLView {

    void setDebugFlags(int debugFlags);

    void setPreserveEGLContextOnPause(boolean preserveOnPause);

    void setRenderer(Renderer renderer);

    void setEGLContextFactory(EGLContextFactory factory);

    void setEGLWindowSurfaceFactory(EGLWindowSurfaceFactory factory);

    void setEGLConfigChooser(EGLConfigChooser configChooser);

    void setEGLConfigChooser(boolean needDepth);

    void setEGLConfigChooser(int redSize, int greenSize, int blueSize,
                             int alphaSize, int depthSize, int stencilSize);

    void setEGLContextClientVersion(int version);

    void setRenderMode(int renderMode);

    int getRenderMode();

    void requestRender();

    void onPause();

    void onResume();

    void queueEvent(Runnable r);

    EGLConfigChooser getEGLConfigChooser();

    int getEGLContextClientVersion();

    EGLContextFactory getEGLContextFactory();

    EGLWindowSurfaceFactory getEGLWindowSurfaceFactory();

    Object getSurfaceObject();

    int getDebugFlags();

    boolean isPreserveEGLContextOnPause();

    Renderer getRenderer();
}
