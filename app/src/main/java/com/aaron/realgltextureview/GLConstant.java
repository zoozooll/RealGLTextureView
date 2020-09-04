package com.aaron.realgltextureview;

class GLConstant {
    /**
     * The renderer only renders
     * when the surface is created, or when {@link #requestRender} is called.
     */
    public final static int RENDERMODE_WHEN_DIRTY = 0;
    /**
     * The renderer is called
     * continuously to re-render the scene.
     */
    public final static int RENDERMODE_CONTINUOUSLY = 1;
    /**
     * Check glError() after every GL call and throw an exception if glError indicates
     * that an error has occurred. This can be used to help track down which OpenGL ES call
     * is causing an error.
     */
    public final static int DEBUG_CHECK_GL_ERROR = 1;
    /**
     * Log GL calls to the system log at "verbose" level with tag "GLSurfaceView".
     */
    public final static int DEBUG_LOG_GL_CALLS = 2;

    final static boolean LOG_ATTACH_DETACH = false;
    final static boolean LOG_THREADS = false;
    final static boolean LOG_PAUSE_RESUME = false;
    final static boolean LOG_SURFACE = false;
    final static boolean LOG_RENDERER = false;
    final static boolean LOG_RENDERER_DRAW_FRAME = false;
    final static boolean LOG_EGL = false;
}
