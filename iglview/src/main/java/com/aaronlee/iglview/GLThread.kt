package com.aaronlee.iglview

import android.opengl.EGL14
import android.util.Log
import java.lang.ref.WeakReference
import java.util.*

/**
 * A generic GL Thread. Takes care of initializing EGL and GL. Delegates
 * to a Renderer instance to do the actual drawing. Can be configured to
 * render continuously or on request.
 *
 * All potentially blocking synchronization is done through the
 * sGLThreadManager object. This avoids multiple-lock ordering issues.
 *
 */
internal class GLThread(glSurfaceViewWeakRef: WeakReference<out IGLView?>) : Thread() {
    // Once the thread is started, all accesses to the following member
    // variables are protected by the sGLThreadManager monitor
    private var mShouldExit = false
    var mExited = false
    private var mRequestPaused = false
    private var mPaused = false
    private var mHasSurface = false
    private var mSurfaceIsBad = false
    private var mWaitingForSurface = false
    private var mHaveEglContext = false
    private var mHaveEglSurface = false
    private var mFinishedCreatingEglSurface = false
    private var mShouldReleaseEglContext = false
    private var mWidth = 0
    private var mHeight = 0
    private var mRenderMode: Int
    private var mRequestRender = true
    private var mWantRenderNotification: Boolean
    private var mRenderComplete = false
    private val mEventQueue = ArrayList<Runnable>()
    private var mSizeChanged = true
    private var mFinishDrawingRunnable: Runnable? = null

    // End of member variables protected by the sGLThreadManager monitor.
    private var mEglHelper: EglHelper? = null
    private val mGLSurfaceViewWeakRef: WeakReference<out IGLView?>
    override fun run() {
        name = "GLThread $id"
        if (GLConstant.LOG_THREADS) {
            Log.i("GLThread", "starting tid=$id")
        }
        try {
            guardedRun()
        } catch (e: InterruptedException) {
            // fall thru and exit normally
        } finally {
            sGLThreadManager.threadExiting(this)
        }
    }

    /*
     * This private method should only be called inside a
     * synchronized(sGLThreadManager) block.
     */
    private fun stopEglSurfaceLocked() {
        if (mHaveEglSurface) {
            mHaveEglSurface = false
            mEglHelper?.destroySurface()
        }
    }

    /*
     * This private method should only be called inside a
     * synchronized(sGLThreadManager) block.
     */
    private fun stopEglContextLocked() {
        if (mHaveEglContext) {
            mEglHelper?.finish()
            mHaveEglContext = false
            sGLThreadManager.releaseEglContextLocked(this)
        }
    }

    @Throws(InterruptedException::class)
    private fun guardedRun() {
        mEglHelper = EglHelper(mGLSurfaceViewWeakRef)
        mHaveEglContext = false
        mHaveEglSurface = false
        mWantRenderNotification = false
        try {
            var createEglContext = false
            var createEglSurface = false
            var lostEglContext = false
            var sizeChanged = false
            var wantRenderNotification = false
            var doRenderNotification = false
            var askedToReleaseEglContext = false
            var w = 0
            var h = 0
            var event: Runnable? = null
            var finishDrawingRunnable: Runnable? = null
            while (true) {
                synchronized(sGLThreadManager) {
                    while (true) {
                        if (mShouldExit) {
                            return
                        }
                        if (!mEventQueue.isEmpty()) {
                            event = mEventQueue.removeAt(0)
                            break
                        }
                        // Update the pause state.
                        var pausing = false
                        if (mPaused != mRequestPaused) {
                            pausing = mRequestPaused
                            mPaused = mRequestPaused
                            sGLThreadManager.notifyAll()
                            if (GLConstant.LOG_PAUSE_RESUME) {
                                Log.i("GLThread", "mPaused is now $mPaused tid=$id")
                            }
                        }
                        // Do we need to give up the EGL context?
                        if (mShouldReleaseEglContext) {
                            if (GLConstant.LOG_SURFACE) {
                                Log.i("GLThread", "releasing EGL context because asked to tid=$id")
                            }
                            stopEglSurfaceLocked()
                            stopEglContextLocked()
                            mShouldReleaseEglContext = false
                            askedToReleaseEglContext = true
                        }
                        // Have we lost the EGL context?
                        if (lostEglContext) {
                            stopEglSurfaceLocked()
                            stopEglContextLocked()
                            lostEglContext = false
                        }
                        // When pausing, release the EGL surface:
                        if (pausing && mHaveEglSurface) {
                            if (GLConstant.LOG_SURFACE) {
                                Log.i("GLThread", "releasing EGL surface because paused tid=$id")
                            }
                            stopEglSurfaceLocked()
                        }
                        // When pausing, optionally release the EGL Context:
                        if (pausing && mHaveEglContext) {
                            val view = mGLSurfaceViewWeakRef.get()
                            val preserveEglContextOnPause = view?.isPreserveEGLContextOnPause == true
                            if (!preserveEglContextOnPause) {
                                stopEglContextLocked()
                                if (GLConstant.LOG_SURFACE) {
                                    Log.i(
                                        "GLThread",
                                        "releasing EGL context because paused tid=$id"
                                    )
                                }
                            }
                        }
                        // Have we lost the SurfaceView surface?
                        if (!mHasSurface && !mWaitingForSurface) {
                            if (GLConstant.LOG_SURFACE) {
                                Log.i("GLThread", "noticed surfaceView surface lost tid=$id")
                            }
                            if (mHaveEglSurface) {
                                stopEglSurfaceLocked()
                            }
                            mWaitingForSurface = true
                            mSurfaceIsBad = false
                            sGLThreadManager.notifyAll()
                        }
                        // Have we acquired the surface view surface?
                        if (mHasSurface && mWaitingForSurface) {
                            if (GLConstant.LOG_SURFACE) {
                                Log.i("GLThread", "noticed surfaceView surface acquired tid=$id")
                            }
                            mWaitingForSurface = false
                            sGLThreadManager.notifyAll()
                        }
                        if (doRenderNotification) {
                            if (GLConstant.LOG_SURFACE) {
                                Log.i("GLThread", "sending render notification tid=$id")
                            }
                            mWantRenderNotification = false
                            doRenderNotification = false
                            mRenderComplete = true
                            sGLThreadManager.notifyAll()
                        }
                        if (mFinishDrawingRunnable != null) {
                            finishDrawingRunnable = mFinishDrawingRunnable
                            mFinishDrawingRunnable = null
                        }
                        // Ready to draw?
                        if (readyToDraw()) {
                            // If we don't have an EGL context, try to acquire one.
                            if (!mHaveEglContext) {
                                if (askedToReleaseEglContext) {
                                    askedToReleaseEglContext = false
                                } else {
                                    try {
                                        mEglHelper!!.start()
                                    } catch (t: RuntimeException) {
                                        sGLThreadManager.releaseEglContextLocked(this)
                                        throw t
                                    }
                                    mHaveEglContext = true
                                    createEglContext = true
                                    sGLThreadManager.notifyAll()
                                }
                            }
                            if (mHaveEglContext && !mHaveEglSurface) {
                                mHaveEglSurface = true
                                createEglSurface = true
                                sizeChanged = true
                            }
                            if (mHaveEglSurface) {
                                if (mSizeChanged) {
                                    sizeChanged = true
                                    w = mWidth
                                    h = mHeight
                                    mWantRenderNotification = true
                                    if (GLConstant.LOG_SURFACE) {
                                        Log.i(
                                            "GLThread",
                                            "noticing that we want render notification tid="
                                                    + id
                                        )
                                    }
                                    // Destroy and recreate the EGL surface.
                                    createEglSurface = true
                                    mSizeChanged = false
                                }
                                mRequestRender = false
                                sGLThreadManager.notifyAll()
                                if (mWantRenderNotification) {
                                    wantRenderNotification = true
                                }
                                break
                            }
                        } else {
                            if (finishDrawingRunnable != null) {
                                Log.w(
                                    TAG, "Warning, !readyToDraw() but waiting for " +
                                            "draw finished! Early reporting draw finished."
                                )
                                finishDrawingRunnable!!.run()
                                finishDrawingRunnable = null
                            }
                        }
                        // By design, this is the only place in a GLThread thread where we wait().
                        if (GLConstant.LOG_THREADS) {
                            Log.i(
                                "GLThread", "waiting tid=" + id
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
                                        + " mRenderMode: " + mRenderMode
                            )
                        }
                        sGLThreadManager.wait()
                    }
                } // end of synchronized(sGLThreadManager)
                if (event != null) {
                    event!!.run()
                    event = null
                    continue
                }
                if (createEglSurface) {
                    if (GLConstant.LOG_SURFACE) {
                        Log.w("GLThread", "egl createSurface")
                    }
                    if (mEglHelper!!.createSurface() == true) {
                        synchronized(sGLThreadManager) {
                            mFinishedCreatingEglSurface = true
                            sGLThreadManager.notifyAll()
                        }
                    } else {
                        synchronized(sGLThreadManager) {
                            mFinishedCreatingEglSurface = true
                            mSurfaceIsBad = true
                            sGLThreadManager.notifyAll()
                        }
                        continue
                    }
                    createEglSurface = false
                }
                if (createEglContext) {
                    if (GLConstant.LOG_RENDERER) {
                        Log.w("GLThread", "onSurfaceCreated")
                    }
                    val view = mGLSurfaceViewWeakRef.get()
                    try {
                        view?.renderer?.onSurfaceCreated(mEglHelper!!.mEglConfig)
                    } finally {
                    }
                    createEglContext = false
                }
                if (sizeChanged) {
                    if (GLConstant.LOG_RENDERER) {
                        Log.w("GLThread", "onSurfaceChanged($w, $h)")
                    }
                    val view = mGLSurfaceViewWeakRef.get()
                    try {
                        view?.renderer?.onSurfaceChanged(w, h)
                    } finally {
                    }
                    sizeChanged = false
                }
                if (GLConstant.LOG_RENDERER_DRAW_FRAME) {
                    Log.w("GLThread", "onDrawFrame tid=$id")
                }
                val view = mGLSurfaceViewWeakRef.get()
                view?.run {
                    try {
                        renderer?.onDrawFrame()
                        finishDrawingRunnable.run {
                            run()
                            finishDrawingRunnable = null
                        }
                    } finally {
                    }
                }
                when (val swapError = mEglHelper!!.swap()) {
                    EGL14.EGL_SUCCESS -> {
                    }
                    EGL14.EGL_CONTEXT_LOST -> {
                        if (GLConstant.LOG_SURFACE) {
                            Log.i("GLThread", "egl context lost tid=$id")
                        }
                        lostEglContext = true
                    }
                    else -> {
                        // Other errors typically mean that the current surface is bad,
                        // probably because the SurfaceView surface has been destroyed,
                        // but we haven't been notified yet.
                        // Log the error to help developers understand why rendering stopped.
                        EglHelper.logEglErrorAsWarning(
                            "GLThread",
                            "eglSwapBuffers",
                            swapError
                        )
                        synchronized(sGLThreadManager) {
                            mSurfaceIsBad = true
                            sGLThreadManager.notifyAll()
                        }
                    }
                }
                if (wantRenderNotification) {
                    doRenderNotification = true
                    wantRenderNotification = false
                }
            }
        } finally {
            /*
             * clean-up everything...
             */
            synchronized(sGLThreadManager) {
                stopEglSurfaceLocked()
                stopEglContextLocked()
            }
        }
    }

    fun ableToDraw(): Boolean {
        return mHaveEglContext && mHaveEglSurface && readyToDraw()
    }

    private fun readyToDraw(): Boolean {
        return (!mPaused && mHasSurface && !mSurfaceIsBad
                && mWidth > 0 && mHeight > 0
                && (mRequestRender || mRenderMode == GLConstant.RENDERMODE_CONTINUOUSLY))
    }

    var renderMode: Int
        get() {
            synchronized(sGLThreadManager) { return mRenderMode }
        }
        set(renderMode) {
            require(GLConstant.RENDERMODE_WHEN_DIRTY <= renderMode && renderMode <= GLConstant.RENDERMODE_CONTINUOUSLY) { "renderMode" }
            synchronized(sGLThreadManager) {
                mRenderMode = renderMode
                sGLThreadManager.notifyAll()
            }
        }

    fun requestRender() {
        synchronized(sGLThreadManager) {
            mRequestRender = true
            sGLThreadManager.notifyAll()
        }
    }

    fun requestRenderAndNotify(finishDrawing: Runnable?) {
        synchronized(sGLThreadManager) {

            // If we are already on the GL thread, this means a client callback
            // has caused reentrancy, for example via updating the SurfaceView parameters.
            // We will return to the client rendering code, so here we don't need to
            // do anything.
            if (currentThread() === this) {
                return
            }
            mWantRenderNotification = true
            mRequestRender = true
            mRenderComplete = false
            mFinishDrawingRunnable = finishDrawing
            sGLThreadManager.notifyAll()
        }
    }

    fun surfaceCreated() {
        synchronized(sGLThreadManager) {
            if (GLConstant.LOG_THREADS) {
                Log.i("GLThread", "surfaceCreated tid=$id")
            }
            mHasSurface = true
            mFinishedCreatingEglSurface = false
            sGLThreadManager.notifyAll()
            while (mWaitingForSurface
                && !mFinishedCreatingEglSurface
                && !mExited
            ) {
                try {
                    sGLThreadManager.wait()
                } catch (e: InterruptedException) {
                    currentThread().interrupt()
                }
            }
        }
    }

    fun surfaceDestroyed() {
        synchronized(sGLThreadManager) {
            if (GLConstant.LOG_THREADS) {
                Log.i("GLThread", "surfaceDestroyed tid=$id")
            }
            mHasSurface = false
            sGLThreadManager.notifyAll()
            while (!mWaitingForSurface && !mExited) {
                try {
                    sGLThreadManager.wait()
                } catch (e: InterruptedException) {
                    currentThread().interrupt()
                }
            }
        }
    }

    fun onPause() {
        synchronized(sGLThreadManager) {
            if (GLConstant.LOG_PAUSE_RESUME) {
                Log.i("GLThread", "onPause tid=$id")
            }
            mRequestPaused = true
            sGLThreadManager.notifyAll()
            while (!mExited && !mPaused) {
                if (GLConstant.LOG_PAUSE_RESUME) {
                    Log.i("Main thread", "onPause waiting for mPaused.")
                }
                try {
                    sGLThreadManager.wait()
                } catch (ex: InterruptedException) {
                    currentThread().interrupt()
                }
            }
        }
    }

    fun onResume() {
        synchronized(sGLThreadManager) {
            if (GLConstant.LOG_PAUSE_RESUME) {
                Log.i("GLThread", "onResume tid=$id")
            }
            mRequestPaused = false
            mRequestRender = true
            mRenderComplete = false
            sGLThreadManager.notifyAll()
            while (!mExited && mPaused && !mRenderComplete) {
                if (GLConstant.LOG_PAUSE_RESUME) {
                    Log.i("Main thread", "onResume waiting for !mPaused.")
                }
                try {
                    sGLThreadManager.wait()
                } catch (ex: InterruptedException) {
                    currentThread().interrupt()
                }
            }
        }
    }

    fun onWindowResize(w: Int, h: Int) {
        synchronized(sGLThreadManager) {
            mWidth = w
            mHeight = h
            mSizeChanged = true
            mRequestRender = true
            mRenderComplete = false
            // If we are already on the GL thread, this means a client callback
            // has caused reentrancy, for example via updating the SurfaceView parameters.
            // We need to process the size change eventually though and update our EGLSurface.
            // So we set the parameters and return so they can be processed on our
            // next iteration.
            if (currentThread() === this) {
                return
            }
            sGLThreadManager.notifyAll()
            // Wait for thread to react to resize and render a frame
            while (!mExited && !mPaused && !mRenderComplete
                && ableToDraw()
            ) {
                if (GLConstant.LOG_SURFACE) {
                    Log.i("Main thread", "onWindowResize waiting for render complete from tid=$id")
                }
                try {
                    sGLThreadManager.wait()
                } catch (ex: InterruptedException) {
                    currentThread().interrupt()
                }
            }
        }
    }

    fun requestExitAndWait() {
        // don't call this from GLThread thread or it is a guaranteed
        // deadlock!
        synchronized(sGLThreadManager) {
            mShouldExit = true
            sGLThreadManager.notifyAll()
            while (!mExited) {
                try {
                    sGLThreadManager.wait()
                } catch (ex: InterruptedException) {
                    currentThread().interrupt()
                }
            }
        }
    }

    fun requestReleaseEglContextLocked() {
        mShouldReleaseEglContext = true
        sGLThreadManager.notifyAll()
    }

    /**
     * Queue an "event" to be run on the GL rendering thread.
     * @param r the runnable to be run on the GL rendering thread.
     */
    fun queueEvent(r: Runnable?) {
        requireNotNull(r) { "r must not be null" }
        synchronized(sGLThreadManager) {
            mEventQueue.add(r)
            sGLThreadManager.notifyAll()
        }
    }

    companion object {
        private const val TAG = "GLThread"
        private val sGLThreadManager = GLThreadManager()
    }

    init {
        mRenderMode = GLConstant.RENDERMODE_CONTINUOUSLY
        mWantRenderNotification = false
        mGLSurfaceViewWeakRef = glSurfaceViewWeakRef
    }
}