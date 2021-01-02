package com.aaronlee.iglview

import android.opengl.EGLConfig
import android.opengl.EGLDisplay

/**
 * An interface for choosing an EGLConfig configuration from a list of
 * potential configurations.
 *
 *
 * This interface must be implemented by clients wishing to call
 * [GLSurfaceView.setEGLConfigChooser]
 */
interface EGLConfigChooser {
    /**
     * Choose a configuration from the list. Implementors typically
     * implement this method by calling
     * [android.opengl.EGL14.eglChooseConfig] and iterating through the results. Please consult the
     * EGL specification available from The Khronos Group to learn how to call eglChooseConfig.
     * @param display the current display.
     * @return the chosen configuration.
     */
    fun chooseConfig(display: EGLDisplay?): EGLConfig
}