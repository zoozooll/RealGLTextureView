package com.aaron.realgltextureview;

import android.util.Log;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

class DefaultContextFactory implements EGLContextFactory {
    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
    public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig config, int eglContextClientVersion) {
        int[] attrib_list = {EGL_CONTEXT_CLIENT_VERSION, eglContextClientVersion,
                EGL10.EGL_NONE };
        return egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT,
                eglContextClientVersion != 0 ? attrib_list : null);
    }
    public void destroyContext(EGL10 egl, EGLDisplay display,
                               EGLContext context) {
        if (!egl.eglDestroyContext(display, context)) {
            Log.e("DefaultContextFactory", "display:" + display + " context: " + context);
            if (GLConstant.LOG_THREADS) {
                Log.i("DefaultContextFactory", "tid=" + Thread.currentThread().getId());
            }
            EglHelper.throwEglException("eglDestroyContex", egl.eglGetError());
        }
    }
}