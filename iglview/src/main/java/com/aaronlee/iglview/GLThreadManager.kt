package com.aaronlee.iglview

import android.util.Log

internal class GLThreadManager : java.lang.Object() {
    @Synchronized
    fun threadExiting(thread: GLThread) {
        if (GLConstant.LOG_THREADS) {
            Log.i("GLThread", "exiting tid=" + thread.id)
        }
        thread.mExited = true
        notifyAll()
    }

    /*
     * Releases the EGL context. Requires that we are already in the
     * sGLThreadManager monitor when this is called.
     */
    fun releaseEglContextLocked(thread: GLThread?) {
        notifyAll()
    }

    companion object {
        private const val TAG = "GLThreadManager"
    }
}