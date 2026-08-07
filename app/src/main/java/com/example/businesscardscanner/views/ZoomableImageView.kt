package com.example.businesscardscanner.views

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max
import kotlin.math.min

class ZoomableImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val imageMatrix = Matrix()
    private val matrixValues = FloatArray(9)

    private var minScale = 1f
    private var maxScale = 5f
    private var currentScale = 1f

    private var viewWidth = 0
    private var viewHeight = 0
    private var imageWidth = 0f
    private var imageHeight = 0f

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())

    init {
        super.setScaleType(ScaleType.MATRIX)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h
        fitToScreen()
    }

    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        if (drawable != null) {
            imageWidth = drawable.intrinsicWidth.toFloat()
            imageHeight = drawable.intrinsicHeight.toFloat()
            fitToScreen()
        }
    }

    private fun fitToScreen() {
        if (viewWidth == 0 || viewHeight == 0 || imageWidth == 0f || imageHeight == 0f) return

        val scaleX = viewWidth / imageWidth
        val scaleY = viewHeight / imageHeight
        minScale = min(scaleX, scaleY)
        currentScale = minScale

        val redundantXSpace = viewWidth - (minScale * imageWidth)
        val redundantYSpace = viewHeight - (minScale * imageHeight)
        
        imageMatrix.reset()
        imageMatrix.postScale(minScale, minScale)
        imageMatrix.postTranslate(redundantXSpace / 2, redundantYSpace / 2)
        setImageMatrix(imageMatrix)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        if (currentScale > minScale) {
            parent?.requestDisallowInterceptTouchEvent(true)
        } else {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    fun resetZoom() {
        if (currentScale > minScale) {
            fitToScreen()
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            var scaleFactor = detector.scaleFactor
            val origScale = currentScale
            currentScale *= scaleFactor
            
            if (currentScale > maxScale * minScale) {
                currentScale = maxScale * minScale
                scaleFactor = currentScale / origScale
            } else if (currentScale < minScale) {
                currentScale = minScale
                scaleFactor = currentScale / origScale
            }

            if (imageWidth * currentScale <= viewWidth || imageHeight * currentScale <= viewHeight) {
                imageMatrix.postScale(scaleFactor, scaleFactor, viewWidth / 2f, viewHeight / 2f)
            } else {
                imageMatrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
            }
            
            fixTranslation()
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            imageMatrix.postTranslate(-distanceX, -distanceY)
            fixTranslation()
            return true
        }
        
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (currentScale > minScale) {
                fitToScreen()
            } else {
                val targetScale = minScale * 2f
                val scaleFactor = targetScale / currentScale
                currentScale = targetScale
                imageMatrix.postScale(scaleFactor, scaleFactor, e.x, e.y)
                fixTranslation()
            }
            return true
        }
    }

    private fun fixTranslation() {
        imageMatrix.getValues(matrixValues)
        val transX = matrixValues[Matrix.MTRANS_X]
        val transY = matrixValues[Matrix.MTRANS_Y]
        
        val fixTransX = getFixTrans(transX, viewWidth.toFloat(), imageWidth * currentScale)
        val fixTransY = getFixTrans(transY, viewHeight.toFloat(), imageHeight * currentScale)
        
        if (fixTransX != 0f || fixTransY != 0f) {
            imageMatrix.postTranslate(fixTransX, fixTransY)
        }
        setImageMatrix(imageMatrix)
    }

    private fun getFixTrans(trans: Float, viewSize: Float, contentSize: Float): Float {
        val minTrans: Float
        val maxTrans: Float
        
        if (contentSize <= viewSize) {
            minTrans = (viewSize - contentSize) / 2
            maxTrans = (viewSize - contentSize) / 2
        } else {
            minTrans = viewSize - contentSize
            maxTrans = 0f
        }
        
        if (trans < minTrans) return -trans + minTrans
        if (trans > maxTrans) return -trans + maxTrans
        return 0f
    }
}
