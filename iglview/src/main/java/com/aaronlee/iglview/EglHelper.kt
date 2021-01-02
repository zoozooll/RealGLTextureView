package com.aaronlee.iglview

import android.opengl.*
import android.util.Log
import java.lang.ref.WeakReference

/**
 * An EGL helper class.
 */
internal class EglHelper(private val mGLSurfaceViewWeakRef: WeakReference<out IGLView?>) {
    var mEglDisplay: EGLDisplay? = null
    var mEglSurface: EGLSurface? = null
    var mEglConfig: EGLConfig? = null
    var mEglContext: EGLContext? = null

    /**
     * Initialize EGL for a given configuration spec.
     */
    fun start() {
        if (GLConstant.LOG_EGL) {
            Log.w("EglHelper", "start() tid=" + Thread.currentThread().id)
        }
        mEglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (mEglDisplay === EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("eglGetDisplay failed")
        }
        /*
         * We can now initialize EGL for that display
         */
        val version = IntArray(2)
        if (!EGL14.eglInitialize(mEglDisplay, version, 0, version, 1)) {
            throw RuntimeException("eglInitialize failed")
        }
        val view = mGLSurfaceViewWeakRef.get()
        if (view == null) {
            mEglConfig = null
            mEglContext = null
        } else {
            mEglConfig = view.eglConfigChooser?.chooseConfig(mEglDisplay)
            /*
             * Create an EGL context. We want to do this as rarely as we can, because an
             * EGL context is a somewhat heavy object.
             */
            val eglContextVersion = view.eglContextClientVersion
            mEglContext =
                view.eglContextFactory?.createContext(mEglDisplay, mEglConfig, eglContextVersion)
        }
        if (mEglContext == null || mEglContext === EGL14.EGL_NO_CONTEXT) {
            mEglContext = null
            throwEglException("createContext")
        }
        if (GLConstant.LOG_EGL) {
            Log.w("EglHelper", "createContext " + mEglContext + " tid=" + Thread.currentThread().id)
        }
        mEglSurface = null
    }

    /**
     * Create an egl surface for the current SurfaceHolder surface. If a surface
     * already exists, destroy it before creating the new surface.
     *
     * @return true if the surface was created successfully.
     */
    fun createSurface(): Boolean {
        if (GLConstant.LOG_EGL) {
            Log.w("EglHelper", "createSurface()  tid=" + Thread.currentThread().id)
        }
        /*
         * Check preconditions.
         */if (mEglDisplay == null) {
            throw RuntimeException("eglDisplay not initialized")
        }
        if (mEglConfig == null) {
            throw RuntimeException("mEglConfig not initialized")
        }
        /*
         *  The window size has changed, so we need to create a new
         *  surface.
         */destroySurfaceImp()
        /*
         * Create an EGL surface we can render into.
         */
        val view = mGLSurfaceViewWeakRef.get()
        mEglSurface = view?.eglWindowSurfaceFactory?.createWindowSurface(
            mEglDisplay, mEglConfig, view.surfaceObject
        )
        if (mEglSurface == null || mEglSurface === EGL14.EGL_NO_SURFACE) {
            val error = EGL14.eglGetError()
            if (error == EGL14.EGL_BAD_NATIVE_WINDOW) {
                Log.e("EglHelper", "createWindowSurface returned EGL_BAD_NATIVE_WINDOW.")
            }
            return false
        }
        /*
         * Before we can issue GL commands, we need to make sure
         * the context is current and bound to a surface.
         */if (!EGL14.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) {
            /*
             * Could not make the context current, probably because the underlying
             * SurfaceView surface has been destroyed.
             */
            logEglErrorAsWarning("EGLHelper", "eglMakeCurrent", EGL14.eglGetError())
            return false
        }
        return true
    }

    /**
     * Display the current render surface.
     * @return the EGL error code from eglSwapBuffers.
     */
    fun swap(): Int {
        return if (!EGL14.eglSwapBuffers(mEglDisplay, mEglSurface)) {
            EGL14.eglGetError()
        } else EGL14.EGL_SUCCESS
    }

    fun destroySurface() {
        if (GLConstant.LOG_EGL) {
            Log.w("EglHelper", "destroySurface()  tid=" + Thread.currentThread().id)
        }
        destroySurfaceImp()
    }

    private fun destroySurfaceImp() {
        if (mEglSurface != null && mEglSurface !== EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(
                mEglDisplay, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            val view = mGLSurfaceViewWeakRef.get()
            view?.eglWindowSurfaceFactory?.destroySurface(mEglDisplay, mEglSurface)
            mEglSurface = null
        }
    }

    fun finish() {
        if (GLConstant.LOG_EGL) {
            Log.w("EglHelper", "finish() tid=" + Thread.currentThread().id)
        }
        if (mEglContext != null) {
            val view = mGLSurfaceViewWeakRef.get()
            view?.eglContextFactory?.destroyContext(mEglDisplay, mEglContext!!)
            mEglContext = null
        }
        if (mEglDisplay != null) {
            EGL14.eglTerminate(mEglDisplay)
            mEglDisplay = null
        }
    }

    private fun throwEglException(function: String) {
        throwEglException(function, EGL14.eglGetError())
    }

    companion object {
        fun throwEglException(function: String, error: Int) {
            val message = formatEglError(function, error)
            if (GLConstant.LOG_THREADS) {
                Log.e(
                    "EglHelper", "throwEglException tid=" + Thread.currentThread().id + " "
                            + message
                )
            }
            throw RuntimeException(message)
        }

        fun logEglErrorAsWarning(tag: String?, function: String, error: Int) {
            Log.w(tag, formatEglError(function, error))
        }

        fun formatEglError(function: String, error: Int): String {
            return function + " failed: " + getErrorString(error)
        }

        fun getErrorString(error: Int): String {
            return when (error) {
                EGL14.EGL_SUCCESS -> "EGL_SUCCESS"
                EGL14.EGL_NOT_INITIALIZED -> "EGL_NOT_INITIALIZED"
                EGL14.EGL_BAD_ACCESS -> "EGL_BAD_ACCESS"
                EGL14.EGL_BAD_ALLOC -> "EGL_BAD_ALLOC"
                EGL14.EGL_BAD_ATTRIBUTE -> "EGL_BAD_ATTRIBUTE"
                EGL14.EGL_BAD_CONFIG -> "EGL_BAD_CONFIG"
                EGL14.EGL_BAD_CONTEXT -> "EGL_BAD_CONTEXT"
                EGL14.EGL_BAD_CURRENT_SURFACE -> "EGL_BAD_CURRENT_SURFACE"
                EGL14.EGL_BAD_DISPLAY -> "EGL_BAD_DISPLAY"
                EGL14.EGL_BAD_MATCH -> "EGL_BAD_MATCH"
                EGL14.EGL_BAD_NATIVE_PIXMAP -> "EGL_BAD_NATIVE_PIXMAP"
                EGL14.EGL_BAD_NATIVE_WINDOW -> "EGL_BAD_NATIVE_WINDOW"
                EGL14.EGL_BAD_PARAMETER -> "EGL_BAD_PARAMETER"
                EGL14.EGL_BAD_SURFACE -> "EGL_BAD_SURFACE"
                EGL14.EGL_CONTEXT_LOST -> "EGL_CONTEXT_LOST"
                else -> getHex(error)
            }
        }

        private fun getHex(value: Int): String {
            return "0x" + Integer.toHexString(value)
        }
    }
}