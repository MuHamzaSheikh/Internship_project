package com.example.businesscardscanner.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.opencv.core.Point

class CardOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onCardTapped: ((Array<Point>) -> Unit)? = null

    private val paint = Paint().apply {
        color = Color.parseColor("#00FF00") // Green for high confidence
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
        strokeJoin = Paint.Join.ROUND
    }
    
    private val fillPaint = Paint().apply {
        color = Color.parseColor("#4000FF00") // Semi-transparent green
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var currentCards: List<Array<Point>> = emptyList()
    private val path = Path()

    fun updateCards(cards: List<Array<Point>>) {
        this.currentCards = cards
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (currentCards.isEmpty()) return

        for (card in currentCards) {
            if (card.size != 4) continue

            path.reset()
            // Map coordinates from standard 640x480 logic to this View's dimensions
            // Usually involves some matrix transform, but for now we draw raw scaled points.
            // We assume points are mapped relative to the View bounds already or we map them here.
            
            // To do mapping correctly, we need the original Image proxy dimensions vs View dimensions.
            // For now, let's just draw them directly (assuming mapping was done or will be done)
            path.moveTo(card[0].x.toFloat(), card[0].y.toFloat())
            path.lineTo(card[1].x.toFloat(), card[1].y.toFloat())
            path.lineTo(card[2].x.toFloat(), card[2].y.toFloat())
            path.lineTo(card[3].x.toFloat(), card[3].y.toFloat())
            path.close()

            canvas.drawPath(path, fillPaint)
            canvas.drawPath(path, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val tapPoint = Point(event.x.toDouble(), event.y.toDouble())
            for (card in currentCards.reversed()) { // Check top-most drawn card first
                if (isPointInPolygon(tapPoint, card)) {
                    onCardTapped?.invoke(card)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun isPointInPolygon(point: Point, polygon: Array<Point>): Boolean {
        var isInside = false
        var i = 0
        var j = polygon.size - 1
        while (i < polygon.size) {
            val xi = polygon[i].x
            val yi = polygon[i].y
            val xj = polygon[j].x
            val yj = polygon[j].y
            
            val intersect = ((yi > point.y) != (yj > point.y)) &&
                    (point.x < (xj - xi) * (point.y - yi) / (yj - yi) + xi)
            if (intersect) isInside = !isInside
            j = i++
        }
        return isInside
    }
}
