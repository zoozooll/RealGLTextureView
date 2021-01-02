package com.aaronlee.iglview

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.lang.ref.WeakReference

class GLSurfaceView : SurfaceView, SurfaceHolder.Callback2, IGLView {
    private val mThisWeakRef: WeakReference<out IGLView?> = WeakReference(this)
    private var mGLThread: GLThread? = null
    private var mRenderer: Renderer? = null
    private var mDetached = false
    private var mEGLConfigChooser: EGLConfigChooser? = null
    private var mEGLContextFactory: EGLContextFactory? = null
    private var mEGLWindowSurfaceFactory: EGLWindowSurfaceFactory? = null
    override var debugFlags = 0
    private var mEGLContextClientVersion = 0
    override var isPreserveEGLContextOnPause = false

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    @Throws(Throwable::class)
    protected fun finalize() {
        try {
            mGLThread?.requestExitAndWait()
        } finally {
//            super.finalize()
        }
    }

    private fun init() {
        val holder = holder
        holder.addCallback(this)
    }

    override var eglConfigChooser: EGLConfigChooser?
        get() = mEGLConfigChooser
        set(configChooser) {
            checkRenderThreadState()
            mEGLConfigChooser = configChooser
        }
    override var eglContextClientVersion: Int
        get() = mEGLContextClientVersion
        set(version) {
            checkRenderThreadState()
            mEGLContextClientVersion = version
        }
    override var eglContextFactory: EGLContextFactory?
        get() = mEGLContextFactory
        set(factory) {
            checkRenderThreadState()
            mEGLContextFactory = factory
        }
    override var eglWindowSurfaceFactory: EGLWindowSurfaceFactory?
        get() = mEGLWindowSurfaceFactory
        set(factory) {
            checkRenderThreadState()
            mEGLWindowSurfaceFactory = factory
        }
    override val surfaceObject: Any?
        get() = holder
    override var renderer: Renderer?
        get() = mRenderer
        set(renderer) {
            checkRenderThreadState()
            if (mEGLConfigChooser == null) {
                mEGLConfigChooser = SimpleEGLConfigChooser(true, mEGLContextClientVersion)
            }
            if (mEGLContextFactory == null) {
                mEGLContextFactory = DefaultContextFactory()
            }
            if (mEGLWindowSurfaceFactory == null) {
                mEGLWindowSurfaceFactory = DefaultWindowSurfaceFactory()
            }
            mRenderer = renderer
            mGLThread = GLThread(mThisWeakRef)
            mGLThread!!.start()
        }

    override fun setEGLConfigChooser(needDepth: Boolean) {
        eglConfigChooser = SimpleEGLConfigChooser(needDepth, mEGLContextClientVersion)
    }

    override fun setEGLConfigChooser(
        redSize: Int, greenSize: Int, blueSize: Int,
        alphaSize: Int, depthSize: Int, stencilSize: Int
    ) {
        eglConfigChooser = ComponentSizeChooser(
            redSize, greenSize,
            blueSize, alphaSize, depthSize, stencilSize, mEGLContextClientVersion
        )
    }

    override var renderMode: Int
        get() = mGLThread?.renderMode?:GLConstant.RENDERMODE_WHEN_DIRTY
        set(renderMode) {
            mGLThread?.renderMode = renderMode
        }

    override fun requestRender() {
        mGLThread?.requestRender()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        mGLThread?.surfaceCreated()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // Surface will be destroyed when we return
        mGLThread?.surfaceDestroyed()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        mGLThread?.onWindowResize(w, h)
    }

    override fun surfaceRedrawNeededAsync(holder: SurfaceHolder, finishDrawing: Runnable) {
        mGLThread?.requestRenderAndNotify(finishDrawing)
    }

    @Deprecated("")
    override fun surfaceRedrawNeeded(holder: SurfaceHolder) {
        // Since we are part of the framework we know only surfaceRedrawNeededAsync
        // will be called.
    }

    override fun onPause() {
        mGLThread?.onPause()
    }

    override fun onResume() {
        mGLThread?.onResume()
    }

    override fun queueEvent(r: Runnable?) {
        mGLThread?.queueEvent(r)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (GLConstant.LOG_ATTACH_DETACH) {
            Log.d(TAG, "onAttachedToWindow reattach =$mDetached")
        }
        if (mDetached && mRenderer != null) {
            val renderMode = mGLThread?.renderMode?:GLConstant.RENDERMODE_CONTINUOUSLY
            mGLThread = GLThread(mThisWeakRef)
            if (renderMode != GLConstant.RENDERMODE_CONTINUOUSLY) {
                mGLThread?.renderMode = renderMode
            }
            mGLThread?.start()
        }
        mDetached = false
    }

    override fun onDetachedFromWindow() {
        if (GLConstant.LOG_ATTACH_DETACH) {
            Log.d(TAG, "onDetachedFromWindow")
        }
        mGLThread?.requestExitAndWait()
        mDetached = true
        super.onDetachedFromWindow()
    }

    private fun checkRenderThreadState() {
        check(mGLThread == null) { "setRenderer has already been called for this instance." }
    }

    companion object {
        private const val TAG = "GLSurfaceView"
    }
}