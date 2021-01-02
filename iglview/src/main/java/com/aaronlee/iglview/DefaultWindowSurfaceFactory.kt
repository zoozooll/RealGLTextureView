package com.aaronlee.iglview

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.util.Log

internal class DefaultWindowSurfaceFactory : EGLWindowSurfaceFactory {
    override fun createWindowSurface(
        display: EGLDisplay?,
        config: EGLConfig?, nativeWindow: Any?
    ): EGLSurface? {
        var result: EGLSurface? = null
        try {
            result = EGL14.eglCreateWindowSurface(display, config, nativeWindow, null, 0)
        } catch (e: IllegalArgumentException) {
            // This exception indicates that the surface flinger surface
            // is not valid. This can happen if the surface flinger surface has
            // been torn down, but the application has not yet been
            // notified via SurfaceHolder.Callback.surfaceDestroyed.
            // In theory the application should be notified first,
            // but in practice sometimes it is not. See b/4588890
            Log.e(TAG, "eglCreateWindowSurface", e)
        }
        return result
    }

    override fun destroySurface(
        display: EGLDisplay?,
        surface: EGLSurface?
    ) {
        EGL14.eglDestroySurface(display, surface)
    }

    companion object {
        private const val TAG = "DefaultSurfaceFactory"
    }
}