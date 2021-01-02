package com.aaronlee.iglview;

import android.opengl.EGLConfig;
import android.opengl.EGLDisplay;

/**
 * An interface for choosing an EGLConfig configuration from a list of
 * potential configurations.
 * <p>
 * This interface must be implemented by clients wishing to call
 * {@link GLSurfaceView#setEGLConfigChooser(EGLConfigChooser)}
 */
public interface EGLConfigChooser {
    /**
     * Choose a configuration from the list. Implementors typically
     * implement this method by calling
     * {@link android.opengl.EGL14#eglChooseConfig(EGLDisplay, int[], int, EGLConfig[], int, int, int[], int)} and iterating through the results. Please consult the
     * EGL specification available from The Khronos Group to learn how to call eglChooseConfig.
     * @param display the current display.
     * @return the chosen configuration.
     */
    EGLConfig chooseConfig(EGLDisplay display);
}

