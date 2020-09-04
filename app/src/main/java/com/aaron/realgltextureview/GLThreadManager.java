package com.aaron.realgltextureview;

import android.util.Log;

class GLThreadManager {
    private static String TAG = "GLThreadManager";
    public synchronized void threadExiting(GLThread thread) {
        if (GLConstant.LOG_THREADS) {
            Log.i("GLThread", "exiting tid=" +  thread.getId());
        }
        thread.mExited = true;
        notifyAll();
    }
    /*
     * Releases the EGL context. Requires that we are already in the
     * sGLThreadManager monitor when this is called.
     */
    public void releaseEglContextLocked(GLThread thread) {
        notifyAll();
    }
}