package com.aaronlee.iglview

import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay

/**
 * An interface for customizing the eglCreateContext and eglDestroyContext calls.
 *
 *
 * This interface must be implemented by clients wishing to call
 * [GLSurfaceView.setEGLContextFactory]
 */
interface EGLContextFactory {
    fun createContext(
        display: EGLDisplay?,
        eglConfig: EGLConfig?,
        eglContextClientVersion: Int
    ): EGLContext?

    fun destroyContext(display: EGLDisplay?, context: EGLContext?)
}