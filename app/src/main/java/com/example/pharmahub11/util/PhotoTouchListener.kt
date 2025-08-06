package com.example.pharmahub11.util

import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

class PhotoTouchListener(context: Context) : View.OnTouchListener {
    private val scaleGestureDetector: ScaleGestureDetector
    private var scaleFactor = 1.0f
    private var view: View? = null

    init {
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        view = v
        scaleGestureDetector.onTouchEvent(event)
        return true
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.1f, 10.0f)
            view?.apply {
                scaleX = scaleFactor
                scaleY = scaleFactor
            }
            return true
        }
    }
}