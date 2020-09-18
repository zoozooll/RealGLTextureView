package com.aaron.realgltextureview;

public class GLTextureView implements IGLView {
    @Override
    public EGLConfigChooser getEGLConfigChooser() {
        return null;
    }

    @Override
    public int getEGLContextClientVersion() {
        return 0;
    }

    @Override
    public EGLContextFactory getEGLContextFactory() {
        return null;
    }

    @Override
    public EGLWindowSurfaceFactory getEGLWindowSurfaceFactory() {
        return null;
    }

    @Override
    public Object getSurfaceObject() {
        return null;
    }

    @Override
    public GLWrapper getGLWrapper() {
        return null;
    }

    @Override
    public int getDebugFlags() {
        return 0;
    }

    @Override
    public boolean isPreserveEGLContextOnPause() {
        return false;
    }

    @Override
    public Renderer getRenderer() {
        return null;
    }
}
