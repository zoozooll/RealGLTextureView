package com.aaronlee.iglview

/**
 * This class will choose a RGB_888 surface with
 * or without a depth buffer.
 *
 */
internal class SimpleEGLConfigChooser(withDepthBuffer: Boolean, eglContextVersion: Int) :
    ComponentSizeChooser(8, 8, 8, 0, if (withDepthBuffer) 16 else 0, 0, eglContextVersion)