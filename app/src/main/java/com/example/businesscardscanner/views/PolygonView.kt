package com.example.businesscardscanner.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class PolygonView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4F6BFF") // PrimaryBlue
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#334F6BFF") // PrimaryBlue with 20% alpha
        style = Paint.Style.FILL
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4F6BFF")
        style = Paint.Style.FILL
    }

    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private var points = mutableListOf<PointF>()
    private var draggedPoint: PointF? = null
    private val touchRadius = 80f // Radius to capture touch
    private val cornerRadius = 30f // Visual radius of the corner handles

    init {
        setWillNotDraw(false)
    }

    fun setPoints(newPoints: List<PointF>) {
        points = newPoints.toMutableList()
        invalidate()
    }

    fun getPoints(): List<PointF> {
        return points
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size == 4) {
            val path = android.graphics.Path()
            path.moveTo(points[0].x, points[0].y)
            path.lineTo(points[1].x, points[1].y)
            path.lineTo(points[2].x, points[2].y)
            path.lineTo(points[3].x, points[3].y)
            path.close()

            canvas.drawPath(path, fillPaint)
            canvas.drawPath(path, paint)

            for (point in points) {
                canvas.drawCircle(point.x, point.y, cornerRadius, cornerPaint)
                canvas.drawCircle(point.x, point.y, cornerRadius, pointerPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (points.size != 4) return super.onTouchEvent(event)

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                draggedPoint = points.minByOrNull { hypot((it.x - x).toDouble(), (it.y - y).toDouble()) }
                if (draggedPoint != null) {
                    val dist = hypot((draggedPoint!!.x - x).toDouble(), (draggedPoint!!.y - y).toDouble())
                    if (dist > touchRadius) {
                        draggedPoint = null
                    }
                }
                if (draggedPoint != null) return true
            }
            MotionEvent.ACTION_MOVE -> {
                draggedPoint?.let {
                    it.x = max(0f, min(width.toFloat(), x))
                    it.y = max(0f, min(height.toFloat(), y))
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggedPoint = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
