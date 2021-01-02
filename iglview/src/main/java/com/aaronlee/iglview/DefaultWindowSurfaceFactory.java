package com.aaronlee.iglview;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.util.Log;

class DefaultWindowSurfaceFactory implements EGLWindowSurfaceFactory {
    private static final String TAG = "DefaultSurfaceFactory";

    public EGLSurface createWindowSurface(EGLDisplay display,
                                          EGLConfig config, Object nativeWindow) {
        EGLSurface result = null;
        try {
            result = EGL14.eglCreateWindowSurface(display, config, nativeWindow, null, 0);
        } catch (IllegalArgumentException e) {
            // This exception indicates that the surface flinger surface
            // is not valid. This can happen if the surface flinger surface has
            // been torn down, but the application has not yet been
            // notified via SurfaceHolder.Callback.surfaceDestroyed.
            // In theory the application should be notified first,
            // but in practice sometimes it is not. See b/4588890
            Log.e(TAG, "eglCreateWindowSurface", e);
        }
        return result;
    }
    public void destroySurface(EGLDisplay display,
                               EGLSurface surface) {
        EGL14.eglDestroySurface(display, surface);
    }
}