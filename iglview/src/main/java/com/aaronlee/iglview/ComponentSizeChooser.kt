package com.aaronlee.iglview

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLDisplay

/**
 * Choose a configuration with exactly the specified r,g,b,a sizes,
 * and at least the specified depth and stencil sizes.
 */
internal open class ComponentSizeChooser(
    redSize: Int, greenSize: Int, blueSize: Int,
    alphaSize: Int, depthSize: Int, stencilSize: Int, eglContextVersion: Int
) : BaseConfigChooser(
    intArrayOf(
        EGL14.EGL_RED_SIZE, redSize,
        EGL14.EGL_GREEN_SIZE, greenSize,
        EGL14.EGL_BLUE_SIZE, blueSize,
        EGL14.EGL_ALPHA_SIZE, alphaSize,
        EGL14.EGL_DEPTH_SIZE, depthSize,
        EGL14.EGL_STENCIL_SIZE, stencilSize,
        EGL14.EGL_NONE
    ), eglContextVersion
) {
    private val mValue: IntArray

    // Subclasses can adjust these values:
    protected var mRedSize: Int
    protected var mGreenSize: Int
    protected var mBlueSize: Int
    protected var mAlphaSize: Int
    protected var mDepthSize: Int
    protected var mStencilSize: Int
    override fun chooseConfig(
        display: EGLDisplay?,
        configs: Array<EGLConfig?>
    ): EGLConfig? {
        for (config in configs) {
            val d = findConfigAttrib(
                display, config,
                EGL14.EGL_DEPTH_SIZE, 0
            )
            val s = findConfigAttrib(
                display, config,
                EGL14.EGL_STENCIL_SIZE, 0
            )
            if (d >= mDepthSize && s >= mStencilSize) {
                val r = findConfigAttrib(
                    display, config,
                    EGL14.EGL_RED_SIZE, 0
                )
                val g = findConfigAttrib(
                    display, config,
                    EGL14.EGL_GREEN_SIZE, 0
                )
                val b = findConfigAttrib(
                    display, config,
                    EGL14.EGL_BLUE_SIZE, 0
                )
                val a = findConfigAttrib(
                    display, config,
                    EGL14.EGL_ALPHA_SIZE, 0
                )
                if (r == mRedSize && g == mGreenSize
                    && b == mBlueSize && a == mAlphaSize
                ) {
                    return config
                }
            }
        }
        return null
    }

    private fun findConfigAttrib(
        display: EGLDisplay?,
        config: EGLConfig?, attribute: Int, defaultValue: Int
    ): Int {
        return if (EGL14.eglGetConfigAttrib(display, config, attribute, mValue, 0)) {
            mValue[0]
        } else defaultValue
    }

    init {
        mValue = IntArray(1)
        mRedSize = redSize
        mGreenSize = greenSize
        mBlueSize = blueSize
        mAlphaSize = alphaSize
        mDepthSize = depthSize
        mStencilSize = stencilSize
    }
}