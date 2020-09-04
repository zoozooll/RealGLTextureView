package com.aaron.realgltextureview;

import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGL11;
import javax.microedition.khronos.opengles.GL10;

/**
 * A generic GL Thread. Takes care of initializing EGL and GL. Delegates
 * to a Renderer instance to do the actual drawing. Can be configured to
 * render continuously or on request.
 *
 * All potentially blocking synchronization is done through the
 * sGLThreadManager object. This avoids multiple-lock ordering issues.
 *
 */
class GLThread extends Thread {
    private static final String TAG = "GLThread";

    GLThread(WeakReference<GLSurfaceView> glSurfaceViewWeakRef) {
        super();
        mWidth = 0;
        mHeight = 0;
        mRequestRender = true;
        mRenderMode = GLConstant.RENDERMODE_CONTINUOUSLY;
        mWantRenderNotification = false;
        mGLSurfaceViewWeakRef = glSurfaceViewWeakRef;
    }
    @Override
    public void run() {
        setName("GLThread " + getId());
        if (GLConstant.LOG_THREADS) {
            Log.i("GLThread", "starting tid=" + getId());
        }
        try {
            guardedRun();
        } catch (InterruptedException e) {
            // fall thru and exit normally
        } finally {
            sGLThreadManager.threadExiting(this);
        }
    }
    /*
     * This private method should only be called inside a
     * synchronized(sGLThreadManager) block.
     */
    private void stopEglSurfaceLocked() {
        if (mHaveEglSurface) {
            mHaveEglSurface = false;
            mEglHelper.destroySurface();
        }
    }
    /*
     * This private method should only be called inside a
     * synchronized(sGLThreadManager) block.
     */
    private void stopEglContextLocked() {
        if (mHaveEglContext) {
            mEglHelper.finish();
            mHaveEglContext = false;
            sGLThreadManager.releaseEglContextLocked(this);
        }
    }
    private void guardedRun() throws InterruptedException {
        mEglHelper = new EglHelper(mGLSurfaceViewWeakRef);
        mHaveEglContext = false;
        mHaveEglSurface = false;
        mWantRenderNotification = false;
        try {
            GL10 gl = null;
            boolean createEglContext = false;
            boolean createEglSurface = false;
            boolean createGlInterface = false;
            boolean lostEglContext = false;
            boolean sizeChanged = false;
            boolean wantRenderNotification = false;
            boolean doRenderNotification = false;
            boolean askedToReleaseEglContext = false;
            int w = 0;
            int h = 0;
            Runnable event = null;
            Runnable finishDrawingRunnable = null;
            while (true) {
                synchronized (sGLThreadManager) {
                    while (true) {
                        if (mShouldExit) {
                            return;
                        }
                        if (! mEventQueue.isEmpty()) {
                            event = mEventQueue.remove(0);
                            break;
                        }
                        // Update the pause state.
                        boolean pausing = false;
                        if (mPaused != mRequestPaused) {
                            pausing = mRequestPaused;
                            mPaused = mRequestPaused;
                            sGLThreadManager.notifyAll();
                            if (GLConstant.LOG_PAUSE_RESUME) {
                                Log.i("GLThread", "mPaused is now " + mPaused + " tid=" + getId());
                            }
                        }
                        // Do we need to give up the EGL context?
                        if (mShouldReleaseEglContext) {
                            if (GLConstant.LOG_SURFACE) {
                                Log.i("GLThread", "releasing EGL context because asked to tid=" + getId());
                            }
                            stopEglSurfaceLocked();
                            stopEglContextLocked();
                            mShouldReleaseEglContext = false;
                            askedToReleaseEglContext = true;
                        }
                        // Have we lost the EGL context?
                        if (lostEglContext) {
                            stopEglSurfaceLocked();
                            stopEglContextLocked();
                            lostEglContext = false;
                        }
                        // When pausing, release the EGL surface:
                        if (pausing && mHaveEglSurface) {
                            if (GLConstant.LOG_SURFACE) {
                                Log.i("GLThread", "releasing EGL surface because paused tid=" + getId());
                            }
                            stopEglSurfaceLocked();
                        }
                        // When pausing, optionally release the EGL Context:
                        if (pausing && mHaveEglContext) {
                            GLSurfaceView view = mGLSurfaceViewWeakRef.get();
                            boolean preserveEglContextOnPause = view == null ?
                                    false : view.mPreserveEGLContextOnPause;
                            if (!preserveEglContextOnPause) {
                                stopEglContextLocked();
                                if (GLConstant.LOG_SURFACE) {
                                    Log.i("GLThread", "releasing EGL context because paused tid=" + getId());
                                }
                            }
                        }
                        // Have we lost the SurfaceView surface?
                        if ((! mHasSurface) && (! mWaitingForSurface)) {
                            if (GLConstant.LOG_SURFACE) {
                                Log.i("GLThread", "noticed surfaceView surface lost tid=" + getId());
                            }
                            if (mHaveEglSurface) {
                                stopEglSurfaceLocked();
                            }
                            mWaitingForSurface = true;
                            mSurfaceIsBad = false;
                            sGLThreadManager.notifyAll();
                        }
                        // Have we acquired the surface view surface?
                        if (mHasSurface && mWaitingForSurface) {
                            if (GLConstant.LOG_SURFACE) {
                                Log.i("GLThread", "noticed surfaceView surface acquired tid=" + getId());
                            }
                            mWaitingForSurface = false;
                            sGLThreadManager.notifyAll();
                        }
                        if (doRenderNotification) {
                            if (GLConstant.LOG_SURFACE) {
                                Log.i("GLThread", "sending render notification tid=" + getId());
                            }
                            mWantRenderNotification = false;
                            doRenderNotification = false;
                            mRenderComplete = true;
                            sGLThreadManager.notifyAll();
                        }
                        if (mFinishDrawingRunnable != null) {
                            finishDrawingRunnable = mFinishDrawingRunnable;
                            mFinishDrawingRunnable = null;
                        }
                        // Ready to draw?
                        if (readyToDraw()) {
                            // If we don't have an EGL context, try to acquire one.
                            if (! mHaveEglContext) {
                                if (askedToReleaseEglContext) {
                                    askedToReleaseEglContext = false;
                                } else {
                                    try {
                                        mEglHelper.start();
                                    } catch (RuntimeException t) {
                                        sGLThreadManager.releaseEglContextLocked(this);
                                        throw t;
                                    }
                                    mHaveEglContext = true;
                                    createEglContext = true;
                                    sGLThreadManager.notifyAll();
                                }
                            }
                            if (mHaveEglContext && !mHaveEglSurface) {
                                mHaveEglSurface = true;
                                createEglSurface = true;
                                createGlInterface = true;
                                sizeChanged = true;
                            }
                            if (mHaveEglSurface) {
                                if (mSizeChanged) {
                                    sizeChanged = true;
                                    w = mWidth;
                                    h = mHeight;
                                    mWantRenderNotification = true;
                                    if (GLConstant.LOG_SURFACE) {
                                        Log.i("GLThread",
                                                "noticing that we want render notification tid="
                                                        + getId());
                                    }
                                    // Destroy and recreate the EGL surface.
                                    createEglSurface = true;
                                    mSizeChanged = false;
                                }
                                mRequestRender = false;
                                sGLThreadManager.notifyAll();
                                if (mWantRenderNotification) {
                                    wantRenderNotification = true;
                                }
                                break;
                            }
                        } else {
                            if (finishDrawingRunnable != null) {
                                Log.w(TAG, "Warning, !readyToDraw() but waiting for " +
                                        "draw finished! Early reporting draw finished.");
                                finishDrawingRunnable.run();
                                finishDrawingRunnable = null;
                            }
                        }
                        // By design, this is the only place in a GLThread thread where we wait().
                        if (GLConstant.LOG_THREADS) {
                            Log.i("GLThread", "waiting tid=" + getId()
                                    + " mHaveEglContext: " + mHaveEglContext
                                    + " mHaveEglSurface: " + mHaveEglSurface
                                    + " mFinishedCreatingEglSurface: " + mFinishedCreatingEglSurface
                                    + " mPaused: " + mPaused
                                    + " mHasSurface: " + mHasSurface
                                    + " mSurfaceIsBad: " + mSurfaceIsBad
                                    + " mWaitingForSurface: " + mWaitingForSurface
                                    + " mWidth: " + mWidth
                                    + " mHeight: " + mHeight
                                    + " mRequestRender: " + mRequestRender
                                    + " mRenderMode: " + mRenderMode);
                        }
                        sGLThreadManager.wait();
                    }
                } // end of synchronized(sGLThreadManager)
                if (event != null) {
                    event.run();
                    event = null;
                    continue;
                }
                if (createEglSurface) {
                    if (GLConstant.LOG_SURFACE) {
                        Log.w("GLThread", "egl createSurface");
                    }
                    if (mEglHelper.createSurface()) {
                        synchronized(sGLThreadManager) {
                            mFinishedCreatingEglSurface = true;
                            sGLThreadManager.notifyAll();
                        }
                    } else {
                        synchronized(sGLThreadManager) {
                            mFinishedCreatingEglSurface = true;
                            mSurfaceIsBad = true;
                            sGLThreadManager.notifyAll();
                        }
                        continue;
                    }
                    createEglSurface = false;
                }
                if (createGlInterface) {
                    gl = (GL10) mEglHelper.createGL();
                    createGlInterface = false;
                }
                if (createEglContext) {
                    if (GLConstant.LOG_RENDERER) {
                        Log.w("GLThread", "onSurfaceCreated");
                    }
                    GLSurfaceView view = mGLSurfaceViewWeakRef.get();
                    if (view != null) {
                        try {
                            view.mRenderer.onSurfaceCreated(gl, mEglHelper.mEglConfig);
                        } finally {
                        }
                    }
                    createEglContext = false;
                }
                if (sizeChanged) {
                    if (GLConstant.LOG_RENDERER) {
                        Log.w("GLThread", "onSurfaceChanged(" + w + ", " + h + ")");
                    }
                    GLSurfaceView view = mGLSurfaceViewWeakRef.get();
                    if (view != null) {
                        try {
                            view.mRenderer.onSurfaceChanged(gl, w, h);
                        } finally {
                        }
                    }
                    sizeChanged = false;
                }
                if (GLConstant.LOG_RENDERER_DRAW_FRAME) {
                    Log.w("GLThread", "onDrawFrame tid=" + getId());
                }
                {
                    GLSurfaceView view = mGLSurfaceViewWeakRef.get();
                    if (view != null) {
                        try {
                            view.mRenderer.onDrawFrame(gl);
                            if (finishDrawingRunnable != null) {
                                finishDrawingRunnable.run();
                                finishDrawingRunnable = null;
                            }
                        } finally {
                        }
                    }
                }
                int swapError = mEglHelper.swap();
                switch (swapError) {
                    case EGL10.EGL_SUCCESS:
                        break;
                    case EGL11.EGL_CONTEXT_LOST:
                        if (GLConstant.LOG_SURFACE) {
                            Log.i("GLThread", "egl context lost tid=" + getId());
                        }
                        lostEglContext = true;
                        break;
                    default:
                        // Other errors typically mean that the current surface is bad,
                        // probably because the SurfaceView surface has been destroyed,
                        // but we haven't been notified yet.
                        // Log the error to help developers understand why rendering stopped.
                        EglHelper.logEglErrorAsWarning("GLThread", "eglSwapBuffers", swapError);
                        synchronized(sGLThreadManager) {
                            mSurfaceIsBad = true;
                            sGLThreadManager.notifyAll();
                        }
                        break;
                }
                if (wantRenderNotification) {
                    doRenderNotification = true;
                    wantRenderNotification = false;
                }
            }
        } finally {
            /*
             * clean-up everything...
             */
            synchronized (sGLThreadManager) {
                stopEglSurfaceLocked();
                stopEglContextLocked();
            }
        }
    }
    public boolean ableToDraw() {
        return mHaveEglContext && mHaveEglSurface && readyToDraw();
    }
    private boolean readyToDraw() {
        return (!mPaused) && mHasSurface && (!mSurfaceIsBad)
                && (mWidth > 0) && (mHeight > 0)
                && (mRequestRender || (mRenderMode == GLConstant.RENDERMODE_CONTINUOUSLY));
    }
    public void setRenderMode(int renderMode) {
        if ( !((GLConstant.RENDERMODE_WHEN_DIRTY <= renderMode) && (renderMode <= GLConstant.RENDERMODE_CONTINUOUSLY)) ) {
            throw new IllegalArgumentException("renderMode");
        }
        synchronized(sGLThreadManager) {
            mRenderMode = renderMode;
            sGLThreadManager.notifyAll();
        }
    }
    public int getRenderMode() {
        synchronized(sGLThreadManager) {
            return mRenderMode;
        }
    }
    public void requestRender() {
        synchronized(sGLThreadManager) {
            mRequestRender = true;
            sGLThreadManager.notifyAll();
        }
    }
    public void requestRenderAndNotify(Runnable finishDrawing) {
        synchronized(sGLThreadManager) {
            // If we are already on the GL thread, this means a client callback
            // has caused reentrancy, for example via updating the SurfaceView parameters.
            // We will return to the client rendering code, so here we don't need to
            // do anything.
            if (Thread.currentThread() == this) {
                return;
            }
            mWantRenderNotification = true;
            mRequestRender = true;
            mRenderComplete = false;
            mFinishDrawingRunnable = finishDrawing;
            sGLThreadManager.notifyAll();
        }
    }
    public void surfaceCreated() {
        synchronized(sGLThreadManager) {
            if (GLConstant.LOG_THREADS) {
                Log.i("GLThread", "surfaceCreated tid=" + getId());
            }
            mHasSurface = true;
            mFinishedCreatingEglSurface = false;
            sGLThreadManager.notifyAll();
            while (mWaitingForSurface
                    && !mFinishedCreatingEglSurface
                    && !mExited) {
                try {
                    sGLThreadManager.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    public void surfaceDestroyed() {
        synchronized(sGLThreadManager) {
            if (GLConstant.LOG_THREADS) {
                Log.i("GLThread", "surfaceDestroyed tid=" + getId());
            }
            mHasSurface = false;
            sGLThreadManager.notifyAll();
            while((!mWaitingForSurface) && (!mExited)) {
                try {
                    sGLThreadManager.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    public void onPause() {
        synchronized (sGLThreadManager) {
            if (GLConstant.LOG_PAUSE_RESUME) {
                Log.i("GLThread", "onPause tid=" + getId());
            }
            mRequestPaused = true;
            sGLThreadManager.notifyAll();
            while ((! mExited) && (! mPaused)) {
                if (GLConstant.LOG_PAUSE_RESUME) {
                    Log.i("Main thread", "onPause waiting for mPaused.");
                }
                try {
                    sGLThreadManager.wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    public void onResume() {
        synchronized (sGLThreadManager) {
            if (GLConstant.LOG_PAUSE_RESUME) {
                Log.i("GLThread", "onResume tid=" + getId());
            }
            mRequestPaused = false;
            mRequestRender = true;
            mRenderComplete = false;
            sGLThreadManager.notifyAll();
            while ((! mExited) && mPaused && (!mRenderComplete)) {
                if (GLConstant.LOG_PAUSE_RESUME) {
                    Log.i("Main thread", "onResume waiting for !mPaused.");
                }
                try {
                    sGLThreadManager.wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    public void onWindowResize(int w, int h) {
        synchronized (sGLThreadManager) {
            mWidth = w;
            mHeight = h;
            mSizeChanged = true;
            mRequestRender = true;
            mRenderComplete = false;
            // If we are already on the GL thread, this means a client callback
            // has caused reentrancy, for example via updating the SurfaceView parameters.
            // We need to process the size change eventually though and update our EGLSurface.
            // So we set the parameters and return so they can be processed on our
            // next iteration.
            if (Thread.currentThread() == this) {
                return;
            }
            sGLThreadManager.notifyAll();
            // Wait for thread to react to resize and render a frame
            while (! mExited && !mPaused && !mRenderComplete
                    && ableToDraw()) {
                if (GLConstant.LOG_SURFACE) {
                    Log.i("Main thread", "onWindowResize waiting for render complete from tid=" + getId());
                }
                try {
                    sGLThreadManager.wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    public void requestExitAndWait() {
        // don't call this from GLThread thread or it is a guaranteed
        // deadlock!
        synchronized(sGLThreadManager) {
            mShouldExit = true;
            sGLThreadManager.notifyAll();
            while (! mExited) {
                try {
                    sGLThreadManager.wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    public void requestReleaseEglContextLocked() {
        mShouldReleaseEglContext = true;
        sGLThreadManager.notifyAll();
    }
    /**
     * Queue an "event" to be run on the GL rendering thread.
     * @param r the runnable to be run on the GL rendering thread.
     */
    public void queueEvent(Runnable r) {
        if (r == null) {
            throw new IllegalArgumentException("r must not be null");
        }
        synchronized(sGLThreadManager) {
            mEventQueue.add(r);
            sGLThreadManager.notifyAll();
        }
    }
    // Once the thread is started, all accesses to the following member
    // variables are protected by the sGLThreadManager monitor
    private boolean mShouldExit;
    boolean mExited;
    private boolean mRequestPaused;
    private boolean mPaused;
    private boolean mHasSurface;
    private boolean mSurfaceIsBad;
    private boolean mWaitingForSurface;
    private boolean mHaveEglContext;
    private boolean mHaveEglSurface;
    private boolean mFinishedCreatingEglSurface;
    private boolean mShouldReleaseEglContext;
    private int mWidth;
    private int mHeight;
    private int mRenderMode;
    private boolean mRequestRender;
    private boolean mWantRenderNotification;
    private boolean mRenderComplete;
    private ArrayList<Runnable> mEventQueue = new ArrayList<Runnable>();
    private boolean mSizeChanged = true;
    private Runnable mFinishDrawingRunnable = null;
    // End of member variables protected by the sGLThreadManager monitor.
    private EglHelper mEglHelper;
    /**
     * Set once at thread construction time, nulled out when the parent view is garbage
     * called. This weak reference allows the GLSurfaceView to be garbage collected while
     * the GLThread is still alive.
     */
    private WeakReference<GLSurfaceView> mGLSurfaceViewWeakRef;
    private static final GLThreadManager sGLThreadManager = new GLThreadManager();
}