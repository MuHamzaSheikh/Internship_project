package com.example.businesscardscanner.camera

import android.os.SystemClock
import org.opencv.core.Point
import kotlin.math.pow
import kotlin.math.sqrt

class StabilityEngine(
    private val requiredStableFrames: Int = 8,
    private val varianceThresholdPx: Double = 15.0,
    private val areaVarianceThresholdRatio: Double = 0.05
) {
    private data class FrameData(
        val center: Point,
        val area: Double,
        val timestamp: Long
    )

    private val history = mutableListOf<FrameData>()
    private var lastTriggerTime = 0L
    private val cooldownMs = 2000L // 2 seconds cooldown after a stable trigger

    fun analyze(cards: List<Array<Point>>): Boolean {
        if (cards.isEmpty()) {
            history.clear()
            return false
        }

        // Just track the largest card for stability
        val mainCard = cards.maxByOrNull { computeArea(it) } ?: return false
        val center = computeCenter(mainCard)
        val area = computeArea(mainCard)

        history.add(FrameData(center, area, SystemClock.elapsedRealtime()))
        
        if (history.size > requiredStableFrames) {
            history.removeAt(0)
        }

        if (history.size < requiredStableFrames) {
            return false
        }

        if (SystemClock.elapsedRealtime() - lastTriggerTime < cooldownMs) {
            return false // Cooling down
        }

        val isStable = checkStability()
        if (isStable) {
            lastTriggerTime = SystemClock.elapsedRealtime()
            history.clear()
            return true
        }

        return false
    }

    private fun checkStability(): Boolean {
        val avgX = history.map { it.center.x }.average()
        val avgY = history.map { it.center.y }.average()
        val avgArea = history.map { it.area }.average()

        for (frame in history) {
            val dist = sqrt((frame.center.x - avgX).pow(2.0) + (frame.center.y - avgY).pow(2.0))
            if (dist > varianceThresholdPx) return false
            
            val areaDiffRatio = kotlin.math.abs(frame.area - avgArea) / avgArea
            if (areaDiffRatio > areaVarianceThresholdRatio) return false
        }

        return true
    }

    private fun computeCenter(points: Array<Point>): Point {
        var x = 0.0
        var y = 0.0
        for (p in points) {
            x += p.x
            y += p.y
        }
        return Point(x / points.size, y / points.size)
    }

    private fun computeArea(points: Array<Point>): Double {
        if (points.size != 4) return 0.0
        // Shoelace formula
        var area = 0.0
        var j = 3
        for (i in 0..3) {
            area += (points[j].x + points[i].x) * (points[j].y - points[i].y)
            j = i
        }
        return kotlin.math.abs(area / 2.0)
    }
}
