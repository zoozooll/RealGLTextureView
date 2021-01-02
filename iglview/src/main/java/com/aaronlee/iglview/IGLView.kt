package com.aaronlee.iglview

interface IGLView {
    fun setEGLConfigChooser(needDepth: Boolean)
    fun setEGLConfigChooser(
        redSize: Int, greenSize: Int, blueSize: Int,
        alphaSize: Int, depthSize: Int, stencilSize: Int
    )

    var renderMode: Int
    fun requestRender()
    fun onPause()
    fun onResume()
    fun queueEvent(r: Runnable?)
    var eglConfigChooser: EGLConfigChooser?
    var eglContextClientVersion: Int
    var eglContextFactory: EGLContextFactory?
    var eglWindowSurfaceFactory: EGLWindowSurfaceFactory?
    val surfaceObject: Any?
    var debugFlags: Int
    var isPreserveEGLContextOnPause: Boolean
    var renderer: Renderer?
}