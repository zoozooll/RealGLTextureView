package com.aaronlee.iglview

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.TextureView
import java.lang.ref.WeakReference

class GLTextureView : TextureView, IGLView, TextureView.SurfaceTextureListener {
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

    constructor(context: Context, attrs: AttributeSet?) : super(
        context, attrs
    ) {
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
        surfaceTextureListener = this
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
        get() = Surface(surfaceTexture)
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
            mGLThread?.renderMode = (renderMode)
        }

    override fun requestRender() {
        mGLThread?.requestRender()
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

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        mGLThread?.surfaceCreated()
        mGLThread?.onWindowResize(width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        mGLThread?.onWindowResize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        mGLThread?.surfaceDestroyed()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (GLConstant.LOG_ATTACH_DETACH) {
            Log.d(TAG, "onAttachedToWindow reattach =$mDetached")
        }
        if (mDetached && mRenderer != null) {
            renderMode = mGLThread?.renderMode?:GLConstant.RENDERMODE_WHEN_DIRTY
            mGLThread = GLThread(mThisWeakRef)
            if (renderMode != GLConstant.RENDERMODE_CONTINUOUSLY) {
                mGLThread!!.renderMode = (renderMode)
            }
            mGLThread!!.start()
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
        private const val TAG = "GLTextureView"
    }
}