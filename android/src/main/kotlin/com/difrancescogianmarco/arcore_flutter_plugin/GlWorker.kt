package com.difrancescogianmarco.arcore_flutter_plugin

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.os.Handler
import android.os.HandlerThread
import android.util.Log

class GLWorker(private val sharedContext: EGLContext) {

    private val TAG = "GLWorker"
    private lateinit var eglDisplay: EGLDisplay
    private lateinit var eglContext: EGLContext
    private lateinit var eglSurface: EGLSurface
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler

    fun start() {
        handlerThread = HandlerThread("GLBackgroundThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        handler.post {
            initGL()
        }
    }

    private fun initGL() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) throw RuntimeException("❌ No EGL display")

        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

        val attribList = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, 0x00000040,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
        val config = configs[0]!!

        // Create shared EGL context
        eglContext = EGL14.eglCreateContext(
            eglDisplay,
            config,
            sharedContext,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE),
            0
        )

        if (eglContext == EGL14.EGL_NO_CONTEXT) throw RuntimeException("❌ Could not create EGL context")

        // Create offscreen surface
        eglSurface = EGL14.eglCreatePbufferSurface(
            eglDisplay,
            config,
            intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE),
            0
        )

        if (eglSurface == EGL14.EGL_NO_SURFACE) throw RuntimeException("❌ Could not create EGL surface")

        // Make context current
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("❌ eglMakeCurrent failed")
        }

        Log.d(TAG, "✅ EGL context initialized on background thread")
    }

    fun post(task: () -> Unit) {
        handler.post {
            task()
        }
    }

    fun release() {
        handler.post {
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
        }
        handlerThread.quitSafely()
    }
}
