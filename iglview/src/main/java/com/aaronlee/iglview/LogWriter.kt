package com.aaronlee.iglview

import android.util.Log
import java.io.Writer

internal class LogWriter : Writer() {
    override fun close() {
        flushBuilder()
    }

    override fun flush() {
        flushBuilder()
    }

    override fun write(buf: CharArray, offset: Int, count: Int) {
        for (i in 0 until count) {
            val c = buf[offset + i]
            if (c == '\n') {
                flushBuilder()
            } else {
                mBuilder.append(c)
            }
        }
    }

    private fun flushBuilder() {
        if (mBuilder.length > 0) {
            Log.v("GLSurfaceView", mBuilder.toString())
            mBuilder.delete(0, mBuilder.length)
        }
    }

    private val mBuilder = StringBuilder()
}