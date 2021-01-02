package com.aaronlee.iglview

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLDisplay
import android.opengl.EGLExt

internal abstract class BaseConfigChooser(
    configSpec: IntArray,
    protected var mEGLContextClientVersion: Int = 3
) : EGLConfigChooser {
    override fun chooseConfig(display: EGLDisplay?): EGLConfig {
        val num_config = IntArray(1)
        require(
            EGL14.eglChooseConfig(
                display, mConfigSpec, 0, null, 0, 0,
                num_config, 0
            )
        ) { "eglChooseConfig failed" }
        val numConfigs = num_config[0]
        require(numConfigs > 0) { "No configs match configSpec" }
        val configs = arrayOfNulls<EGLConfig>(numConfigs)
        require(
            EGL14.eglChooseConfig(
                display, mConfigSpec, 0, configs, 0, numConfigs,
                num_config, 0
            )
        ) { "eglChooseConfig#2 failed" }
        return chooseConfig(display, configs)
            ?: throw IllegalArgumentException("No config chosen")
    }

    abstract fun chooseConfig(
        display: EGLDisplay?,
        configs: Array<EGLConfig?>
    ): EGLConfig?

    protected var mConfigSpec: IntArray
    private fun filterConfigSpec(configSpec: IntArray): IntArray {
        if (mEGLContextClientVersion != 2 && mEGLContextClientVersion != 3) {
            return configSpec
        }
        /* We know none of the subclasses define EGL_RENDERABLE_TYPE.
         * And we know the configSpec is well formed.
         */
        val len = configSpec.size
        val newConfigSpec = IntArray(len + 2)
        System.arraycopy(configSpec, 0, newConfigSpec, 0, len - 1)
        newConfigSpec[len - 1] = EGL14.EGL_RENDERABLE_TYPE
        if (mEGLContextClientVersion == 2) {
            newConfigSpec[len] = EGL14.EGL_OPENGL_ES2_BIT /* EGL_OPENGL_ES2_BIT */
        } else {
            newConfigSpec[len] = EGLExt.EGL_OPENGL_ES3_BIT_KHR /* EGL_OPENGL_ES3_BIT_KHR */
        }
        newConfigSpec[len + 1] = EGL14.EGL_NONE
        return newConfigSpec
    }

    init {
        mConfigSpec = filterConfigSpec(configSpec)
    }
}