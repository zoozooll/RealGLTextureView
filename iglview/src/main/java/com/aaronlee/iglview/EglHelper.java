package com.aaronlee.iglview;

import android.opengl.EGL14;
import android.opengl.EGL15;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLDebugHelper;
import android.util.Log;

import java.io.Writer;
import java.lang.ref.WeakReference;

/**
 * An EGL helper class.
 */
class EglHelper {

    private WeakReference<? extends IGLView> mGLSurfaceViewWeakRef;
    EGLDisplay mEglDisplay;
    EGLSurface mEglSurface;
    EGLConfig mEglConfig;
    EGLContext mEglContext;

    public EglHelper(WeakReference<? extends IGLView> glSurfaceViewWeakRef) {
        mGLSurfaceViewWeakRef = glSurfaceViewWeakRef;
    }
    /**
     * Initialize EGL for a given configuration spec.
     */
    public void start() {
        if (GLConstant.LOG_EGL) {
            Log.w("EglHelper", "start() tid=" + Thread.currentThread().getId());
        }

        mEglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("eglGetDisplay failed");
        }
        /*
         * We can now initialize EGL for that display
         */
        int[] version = new int[2];
        if(!EGL14.eglInitialize(mEglDisplay, version, 0, version, 1)) {
            throw new RuntimeException("eglInitialize failed");
        }
        IGLView view = mGLSurfaceViewWeakRef.get();
        if (view == null) {
            mEglConfig = null;
            mEglContext = null;
        } else {
            mEglConfig = view.getEGLConfigChooser().chooseConfig(mEglDisplay);
            /*
             * Create an EGL context. We want to do this as rarely as we can, because an
             * EGL context is a somewhat heavy object.
             */
            int eglContextVersion = view.getEGLContextClientVersion();
            mEglContext = view.getEGLContextFactory().createContext(mEglDisplay, mEglConfig, eglContextVersion);
        }
        if (mEglContext == null || mEglContext == EGL14.EGL_NO_CONTEXT) {
            mEglContext = null;
            throwEglException("createContext");
        }
        if (GLConstant.LOG_EGL) {
            Log.w("EglHelper", "createContext " + mEglContext + " tid=" + Thread.currentThread().getId());
        }
        mEglSurface = null;
    }
    /**
     * Create an egl surface for the current SurfaceHolder surface. If a surface
     * already exists, destroy it before creating the new surface.
     *
     * @return true if the surface was created successfully.
     */
    public boolean createSurface() {
        if (GLConstant.LOG_EGL) {
            Log.w("EglHelper", "createSurface()  tid=" + Thread.currentThread().getId());
        }
        /*
         * Check preconditions.
         */
        if (mEglDisplay == null) {
            throw new RuntimeException("eglDisplay not initialized");
        }
        if (mEglConfig == null) {
            throw new RuntimeException("mEglConfig not initialized");
        }
        /*
         *  The window size has changed, so we need to create a new
         *  surface.
         */
        destroySurfaceImp();
        /*
         * Create an EGL surface we can render into.
         */
        IGLView view = mGLSurfaceViewWeakRef.get();
        if (view != null) {
            mEglSurface = view.getEGLWindowSurfaceFactory().createWindowSurface(
                    mEglDisplay, mEglConfig, view.getSurfaceObject());
        } else {
            mEglSurface = null;
        }
        if (mEglSurface == null || mEglSurface == EGL14.EGL_NO_SURFACE) {
            int error = EGL14.eglGetError();
            if (error == EGL14.EGL_BAD_NATIVE_WINDOW) {
                Log.e("EglHelper", "createWindowSurface returned EGL_BAD_NATIVE_WINDOW.");
            }
            return false;
        }
        /*
         * Before we can issue GL commands, we need to make sure
         * the context is current and bound to a surface.
         */
        if (!EGL14.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) {
            /*
             * Could not make the context current, probably because the underlying
             * SurfaceView surface has been destroyed.
             */
            logEglErrorAsWarning("EGLHelper", "eglMakeCurrent", EGL14.eglGetError());
            return false;
        }
        return true;
    }
    /**
     * Display the current render surface.
     * @return the EGL error code from eglSwapBuffers.
     */
    public int swap() {
        if (! EGL14.eglSwapBuffers(mEglDisplay, mEglSurface)) {
            return EGL14.eglGetError();
        }
        return EGL14.EGL_SUCCESS;
    }
    public void destroySurface() {
        if (GLConstant.LOG_EGL) {
            Log.w("EglHelper", "destroySurface()  tid=" + Thread.currentThread().getId());
        }
        destroySurfaceImp();
    }
    private void destroySurfaceImp() {
        if (mEglSurface != null && mEglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(mEglDisplay, EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT);
            IGLView view = mGLSurfaceViewWeakRef.get();
            if (view != null) {
                view.getEGLWindowSurfaceFactory().destroySurface(mEglDisplay, mEglSurface);
            }
            mEglSurface = null;
        }
    }
    public void finish() {
        if (GLConstant.LOG_EGL) {
            Log.w("EglHelper", "finish() tid=" + Thread.currentThread().getId());
        }
        if (mEglContext != null) {
            IGLView view = mGLSurfaceViewWeakRef.get();
            if (view != null) {
                view.getEGLContextFactory().destroyContext(mEglDisplay, mEglContext);
            }
            mEglContext = null;
        }
        if (mEglDisplay != null) {
            EGL14.eglTerminate(mEglDisplay);
            mEglDisplay = null;
        }
    }
    private void throwEglException(String function) {
        throwEglException(function, EGL14.eglGetError());
    }
    public static void throwEglException(String function, int error) {
        String message = formatEglError(function, error);
        if (GLConstant.LOG_THREADS) {
            Log.e("EglHelper", "throwEglException tid=" + Thread.currentThread().getId() + " "
                    + message);
        }
        throw new RuntimeException(message);
    }
    public static void logEglErrorAsWarning(String tag, String function, int error) {
        Log.w(tag, formatEglError(function, error));
    }
    public static String formatEglError(String function, int error) {
        return function + " failed: " + getErrorString(error);
    }
    public static String getErrorString(int error) {
        switch (error) {
            case EGL14.EGL_SUCCESS:
                return "EGL_SUCCESS";
            case EGL14.EGL_NOT_INITIALIZED:
                return "EGL_NOT_INITIALIZED";
            case EGL14.EGL_BAD_ACCESS:
                return "EGL_BAD_ACCESS";
            case EGL14.EGL_BAD_ALLOC:
                return "EGL_BAD_ALLOC";
            case EGL14.EGL_BAD_ATTRIBUTE:
                return "EGL_BAD_ATTRIBUTE";
            case EGL14.EGL_BAD_CONFIG:
                return "EGL_BAD_CONFIG";
            case EGL14.EGL_BAD_CONTEXT:
                return "EGL_BAD_CONTEXT";
            case EGL14.EGL_BAD_CURRENT_SURFACE:
                return "EGL_BAD_CURRENT_SURFACE";
            case EGL14.EGL_BAD_DISPLAY:
                return "EGL_BAD_DISPLAY";
            case EGL14.EGL_BAD_MATCH:
                return "EGL_BAD_MATCH";
            case EGL14.EGL_BAD_NATIVE_PIXMAP:
                return "EGL_BAD_NATIVE_PIXMAP";
            case EGL14.EGL_BAD_NATIVE_WINDOW:
                return "EGL_BAD_NATIVE_WINDOW";
            case EGL14.EGL_BAD_PARAMETER:
                return "EGL_BAD_PARAMETER";
            case EGL14.EGL_BAD_SURFACE:
                return "EGL_BAD_SURFACE";
            case EGL14.EGL_CONTEXT_LOST:
                return "EGL_CONTEXT_LOST";
            default:
                return getHex(error);
        }
    }

    private static String getHex(int value) {
        return "0x" + Integer.toHexString(value);
    }
}
