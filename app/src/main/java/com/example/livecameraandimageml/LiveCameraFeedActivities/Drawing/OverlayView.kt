package com.example.objectdetectionlivefeed.Drawing

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import java.util.*

class OverlayView(
    context: Context?,
    attrs: AttributeSet?
) :
    View(context, attrs) {
    private val callbacks: MutableList<DrawCallback> =
        LinkedList()

    fun addCallback(callback: DrawCallback) {
        callbacks.add(callback)
    }

    @Synchronized
    override fun draw(canvas: Canvas) {
        for (callback in callbacks) {
            callback.drawCallback(canvas)
        }
    }

    /** Interface defining the callback for client classes.  */
    interface DrawCallback {
        fun drawCallback(canvas: Canvas?)
    }
}
