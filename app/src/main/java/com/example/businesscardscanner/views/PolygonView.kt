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

    private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var points = mutableListOf<PointF>()
    private var draggedPoint: PointF? = null
    private val touchRadius = 80f // Radius to capture touch
    private val cornerRadius = 30f // Visual radius of the corner handles
    
    private var sourceBitmap: android.graphics.Bitmap? = null
    private var imageMatrix: android.graphics.Matrix? = null

    private val magBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    
    private val magCrosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4F6BFF") // PrimaryBlue
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    init {
        setWillNotDraw(false)
    }

    fun setMagnifierImage(bitmap: android.graphics.Bitmap, matrix: android.graphics.Matrix) {
        sourceBitmap = bitmap
        imageMatrix = matrix
        invalidate()
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

            val cx = points.map { it.x }.average().toFloat()
            val cy = points.map { it.y }.average().toFloat()
            val bracketLength = 40f

            for (point in points) {
                val pathBracket = android.graphics.Path()
                val dx = if (point.x < cx) bracketLength else -bracketLength
                val dy = if (point.y < cy) bracketLength else -bracketLength
                
                pathBracket.moveTo(point.x, point.y + dy)
                pathBracket.lineTo(point.x, point.y)
                pathBracket.lineTo(point.x + dx, point.y)
                
                canvas.drawPath(pathBracket, bracketPaint)
            }
        }
        
        draggedPoint?.let { dp ->
            sourceBitmap?.let { bmp ->
                imageMatrix?.let { mat ->
                    val magRadius = 150f
                    // Position magnifier above the finger, or below if too close to top
                    val magY = if (dp.y - magRadius - 100f < 0) dp.y + magRadius + 100f else dp.y - magRadius - 100f
                    val magX = dp.x

                    canvas.save()
                    val clipPath = android.graphics.Path().apply {
                        addCircle(magX, magY, magRadius, android.graphics.Path.Direction.CW)
                    }
                    canvas.clipPath(clipPath)
                    
                    val magMatrix = android.graphics.Matrix(mat)
                    magMatrix.postTranslate(magX - dp.x, magY - dp.y)
                    magMatrix.postScale(2f, 2f, magX, magY)
                    
                    canvas.drawBitmap(bmp, magMatrix, null)
                    canvas.restore()
                    
                    canvas.drawCircle(magX, magY, magRadius, magBorderPaint)
                    canvas.drawLine(magX - 20f, magY, magX + 20f, magY, magCrosshairPaint)
                    canvas.drawLine(magX, magY - 20f, magX, magY + 20f, magCrosshairPaint)
                }
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
