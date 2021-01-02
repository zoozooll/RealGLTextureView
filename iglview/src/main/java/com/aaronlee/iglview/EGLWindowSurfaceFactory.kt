package com.aaronlee.iglview

import android.opengl.EGLConfig
import android.opengl.EGLDisplay
import android.opengl.EGLSurface

/**
 * An interface for customizing the eglCreateWindowSurface and eglDestroySurface calls.
 *
 *
 * This interface must be implemented by clients wishing to call
 * [GLSurfaceView.setEGLWindowSurfaceFactory]
 */
interface EGLWindowSurfaceFactory {
    /**
     * @return null if the surface cannot be constructed.
     */
    fun createWindowSurface(
        display: EGLDisplay?, config: EGLConfig?,
        nativeWindow: Any?
    ): EGLSurface?

    fun destroySurface(display: EGLDisplay?, surface: EGLSurface?)
}