package com.aaron.realgltextureview;

/**
 * This class will choose a RGB_888 surface with
 * or without a depth buffer.
 *
 */
class SimpleEGLConfigChooser extends ComponentSizeChooser {
    public SimpleEGLConfigChooser(boolean withDepthBuffer, int eglContextVersion) {
        super(8, 8, 8, 0, withDepthBuffer ? 16 : 0, 0, eglContextVersion);
    }
}