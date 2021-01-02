package com.aaronlee.iglview

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.util.Log

internal class DefaultContextFactory : EGLContextFactory {
    override fun createContext(
        display: EGLDisplay?,
        config: EGLConfig?,
        eglContextClientVersion: Int
    ): EGLContext? {
        val attrib_list = intArrayOf(
            EGL_CONTEXT_CLIENT_VERSION, eglContextClientVersion,
            EGL14.EGL_NONE
        )
        return EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            if (eglContextClientVersion != 0) attrib_list else null, 0
        )
    }

    override fun destroyContext(
        display: EGLDisplay?,
        context: EGLContext?
    ) {
        if (!EGL14.eglDestroyContext(display, context)) {
            Log.e("DefaultContextFactory", "display:$display context: $context")
            if (GLConstant.LOG_THREADS) {
                Log.i("DefaultContextFactory", "tid=" + Thread.currentThread().id)
            }
            EglHelper.Companion.throwEglException("eglDestroyContex", EGL14.eglGetError())
        }
    }

    companion object {
        private const val EGL_CONTEXT_CLIENT_VERSION = 0x3098
    }
}