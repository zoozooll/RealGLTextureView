package com.aaronlee.iglview;

import android.opengl.EGLConfig;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;

/**
 * An interface for customizing the eglCreateWindowSurface and eglDestroySurface calls.
 * <p>
 * This interface must be implemented by clients wishing to call
 * {@link GLSurfaceView#setEGLWindowSurfaceFactory(EGLWindowSurfaceFactory)}
 */
public interface EGLWindowSurfaceFactory {
    /**
     *  @return null if the surface cannot be constructed.
     */
    EGLSurface createWindowSurface(EGLDisplay display, EGLConfig config,
                                   Object nativeWindow);
    void destroySurface(EGLDisplay display, EGLSurface surface);
}