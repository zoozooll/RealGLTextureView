package com.aaronlee.iglview;

import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;

/**
 * An interface for customizing the eglCreateContext and eglDestroyContext calls.
 * <p>
 * This interface must be implemented by clients wishing to call
 * {@link GLSurfaceView#setEGLContextFactory(EGLContextFactory)}
 */
public interface EGLContextFactory {
    EGLContext createContext(EGLDisplay display, EGLConfig eglConfig, int eglContextClientVersion);
    void destroyContext(EGLDisplay display, EGLContext context);
}
