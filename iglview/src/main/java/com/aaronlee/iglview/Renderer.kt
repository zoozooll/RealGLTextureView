package com.aaronlee.iglview

import android.opengl.EGLConfig

/**
 * A generic renderer interface.
 *
 *
 * The renderer is responsible for making OpenGL calls to render a frame.
 *
 *
 * GLSurfaceView clients typically create their own classes that implement
 * this interface, and then call [GLSurfaceView.setRenderer] to
 * register the renderer with the GLSurfaceView.
 *
 *
 *
 * <div class="special reference">
 * <h3>Developer Guides</h3>
</div> *
 * For more information about how to use OpenGL, read the
 * [OpenGL]({@docRoot}guide/topics/graphics/opengl.html) developer guide.
 *
 *
 * <h3>Threading</h3>
 * The renderer will be called on a separate thread, so that rendering
 * performance is decoupled from the UI thread. Clients typically need to
 * communicate with the renderer from the UI thread, because that's where
 * input events are received. Clients can communicate using any of the
 * standard Java techniques for cross-thread communication, or they can
 * use the [GLSurfaceView.queueEvent] convenience method.
 *
 *
 * <h3>EGL Context Lost</h3>
 * There are situations where the EGL rendering context will be lost. This
 * typically happens when device wakes up after going to sleep. When
 * the EGL context is lost, all OpenGL resources (such as textures) that are
 * associated with that context will be automatically deleted. In order to
 * keep rendering correctly, a renderer must recreate any lost resources
 * that it still needs. The [.onSurfaceCreated] method
 * is a convenient place to do this.
 *
 *
 */
interface Renderer {
    /**
     * Called when the surface is created or recreated.
     *
     *
     * Called when the rendering thread
     * starts and whenever the EGL context is lost. The EGL context will typically
     * be lost when the Android device awakes after going to sleep.
     *
     *
     * Since this method is called at the beginning of rendering, as well as
     * every time the EGL context is lost, this method is a convenient place to put
     * code to create resources that need to be created when the rendering
     * starts, and that need to be recreated when the EGL context is lost.
     * Textures are an example of a resource that you might want to create
     * here.
     *
     *
     * Note that when the EGL context is lost, all OpenGL resources associated
     * with that context will be automatically deleted. You do not need to call
     * the corresponding "glDelete" methods such as glDeleteTextures to
     * manually delete these lost resources.
     *
     *
     * @param config the EGLConfig of the created surface. Can be used
     * to create matching pbuffers.
     */
    fun onSurfaceCreated(config: EGLConfig?)

    /**
     * Called when the surface changed size.
     *
     *
     * Called after the surface is created and whenever
     * the OpenGL ES surface size changes.
     *
     *
     * Typically you will set your viewport here. If your camera
     * is fixed then you could also set your projection matrix here:
     * <pre class="prettyprint">
     * void onSurfaceChanged(GL10 gl, int width, int height) {
     * gl.glViewport(0, 0, width, height);
     * // for a fixed camera, set the projection too
     * float ratio = (float) width / height;
     * gl.glMatrixMode(GL10.GL_PROJECTION);
     * gl.glLoadIdentity();
     * gl.glFrustumf(-ratio, ratio, -1, 1, 1, 10);
     * }
    </pre> *
     * @param width width of eglsurface
     * @param height height of eglsurface
     */
    fun onSurfaceChanged(width: Int, height: Int)

    /**
     * Called to draw the current frame.
     *
     *
     * This method is responsible for drawing the current frame.
     *
     *
     * test if the interface supports GL11 or higher interfaces.
     */
    fun onDrawFrame()
    fun onSurfaceDestroy()
}