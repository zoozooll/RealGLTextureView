package com.aaronlee.iglview;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.util.Log;

class DefaultContextFactory implements EGLContextFactory {
    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

    public EGLContext createContext(EGLDisplay display, EGLConfig config, int eglContextClientVersion) {
        int[] attrib_list = {EGL_CONTEXT_CLIENT_VERSION, eglContextClientVersion,
                EGL14.EGL_NONE };
        return EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
                eglContextClientVersion != 0 ? attrib_list : null, 0);
    }
    public void destroyContext(EGLDisplay display,
                               EGLContext context) {
        if (!EGL14.eglDestroyContext(display, context)) {
            Log.e("DefaultContextFactory", "display:" + display + " context: " + context);
            if (GLConstant.LOG_THREADS) {
                Log.i("DefaultContextFactory", "tid=" + Thread.currentThread().getId());
            }
            EglHelper.throwEglException("eglDestroyContex", EGL14.eglGetError());
        }
    }
}