package com.example.businesscardscanner.ocr

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object DocumentScanner {
    private const val TAG = "CardScannerDocScan"

    private data class ContourCandidate(
        val points: Array<Point>,
        val area: Double,
        val aspectRatio: Double,
        val rectangularity: Double,
        val solidity: Double,
        val score: Double,
        val isStrict: Boolean
    )

    fun autoCrop(bitmap: Bitmap, cacheDir: File? = null): Bitmap {
        val corners = detectCorners(bitmap, cacheDir)
        return if (corners != null && corners.size == 4) {
            val cropped = cropByCorners(bitmap, corners)
            if (cacheDir != null) {
                try {
                    val file = File(cacheDir, "debug_warped.jpg")
                    FileOutputStream(file).use { out ->
                        cropped.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    Log.d(TAG, "Saved debug_warped.jpg")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save debug_warped.jpg", e)
                }
            }
            cropped
        } else {
            Log.d(TAG, "autoCrop: No plausible 4-point contour found, returning original bitmap")
            bitmap
        }
    }

    private fun findContourCandidates(originalMat: Mat, cacheDir: File? = null): List<ContourCandidate> {
        Log.d(TAG, "findContourCandidates: pipeline invoked")

        // Step 1 - Preprocess
        val grayMat = Mat()
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
        
        val blurredMat = Mat()
        Imgproc.GaussianBlur(grayMat, blurredMat, Size(5.0, 5.0), 0.0)

        val edgesMat = Mat()
        Imgproc.Canny(blurredMat, edgesMat, 75.0, 200.0)

        if (cacheDir != null) {
            try {
                val edgeBitmap = Bitmap.createBitmap(edgesMat.cols(), edgesMat.rows(), Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(edgesMat, edgeBitmap)
                val file = File(cacheDir, "debug_canny.jpg")
                FileOutputStream(file).use { out ->
                    edgeBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                Log.d(TAG, "Saved debug_canny.jpg")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save debug_canny.jpg", e)
            }
        }

        // Step 2 - Find Contours
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edgesMat, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        contours.sortByDescending { Imgproc.contourArea(it) }

        var bestCandidate: ContourCandidate? = null
        val totalArea = originalMat.cols() * originalMat.rows().toDouble()
        
        val candidates = mutableListOf<ContourCandidate>()

        for ((index, contour) in contours.withIndex()) {
            val contour2f = MatOfPoint2f(*contour.toArray())
            val area = Imgproc.contourArea(contour)
            
            // Initial filter: must be at least 15% of the frame
            if (area < totalArea * 0.15) {
                continue
            }
            
            val perimeter = Imgproc.arcLength(contour2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(contour2f, approx, 0.02 * perimeter, true)
            
            val isStrict = approx.toArray().size == 4
            val pointsToUse = if (isStrict) {
                approx.toArray()
            } else {
                val rect = Imgproc.minAreaRect(contour2f)
                val pointsArray = arrayOfNulls<Point>(4)
                rect.points(pointsArray)
                pointsArray.filterNotNull().toTypedArray()
            }
            
            // Border Rejection: If any point is within 1% of the edges, it's likely the camera frame
            val marginX = originalMat.cols() * 0.01
            val marginY = originalMat.rows() * 0.01
            var touchesBorder = false
            for (p in pointsToUse) {
                if (p.x <= marginX || p.x >= originalMat.cols() - marginX || p.y <= marginY || p.y >= originalMat.rows() - marginY) {
                    touchesBorder = true
                    break
                }
            }
            if (touchesBorder) continue
            
            // Recompute minAreaRect specifically to gauge dimensions for Aspect Ratio & Rectangularity
            val rect = Imgproc.minAreaRect(contour2f)
            val boxArea = rect.size.area()
            
            val width = rect.size.width
            val height = rect.size.height
            val aspectRatio = if (width > 0 && height > 0) {
                max(width, height) / minOf(width, height)
            } else 0.0
            
            val rectangularity = if (boxArea > 0) area / boxArea else 0.0
            
            // Solidity: Area / ConvexHullArea
            val hull = MatOfPoint()
            val hullInt = org.opencv.core.MatOfInt()
            Imgproc.convexHull(contour, hullInt)
            val contourArray = contour.toArray()
            val hullPoints = hullInt.toArray().map { contourArray[it] }.toTypedArray()
            hull.fromArray(*hullPoints)
            val hullArea = Imgproc.contourArea(hull)
            val solidity = if (hullArea > 0) area / hullArea else 0.0
            hull.release()
            hullInt.release()
            
            // Reject non-solid or concave shapes
            if (solidity < 0.85) continue
            
            // Reject standard paper size (A4 ~1.414) and out-of-bounds ratios for business cards
            if (aspectRatio < 1.45 || aspectRatio > 2.0) {
                continue
            }
            
            // Scoring
            val targetRatio = 1.67
            val ratioDeviation = kotlin.math.abs(aspectRatio - targetRatio)
            val aspectRatioScore = max(0.0, 1.0 - ratioDeviation)
            
            val rectScore = rectangularity.coerceIn(0.0, 1.0)
            
            val sizeRatio = area / totalArea
            var sizeScore = sizeRatio
            // Penalize massive contours that likely represent the entire image boundary
            if (sizeRatio > 0.95) {
                sizeScore = 0.0
            }
            
            var score = (aspectRatioScore * 0.3) + (rectScore * 0.3) + (solidity * 0.3) + (sizeScore * 0.1)
            if (isStrict) {
                score += 0.1 // 10% bonus for being a true 4-point polygon
            }
            
            val candidate = ContourCandidate(pointsToUse, area, aspectRatio, rectangularity, solidity, score, isStrict)
            candidates.add(candidate)
            
            Log.d(TAG, "Candidate $index: isStrict=$isStrict, areaRatio=${String.format("%.2f", sizeRatio)}, aspectRatio=${String.format("%.2f", aspectRatio)}, rect=${String.format("%.2f", rectangularity)} => score=${String.format("%.3f", score)}")
        }

        // Return candidates sorted by score
        val sortedCandidates = candidates.sortedByDescending { it.score }
        
        grayMat.release()
        blurredMat.release()
        edgesMat.release()
        hierarchy.release()

        return sortedCandidates
    }

    fun detectMultipleCardsFromMat(mat: Mat): List<Array<Point>> {
        val candidates = findContourCandidates(mat)
        return candidates.filter { it.score > 0.4 }.take(3).map { orderPointsForPerspectiveTransform(it.points) }
    }

    fun detectMultipleCards(bitmap: Bitmap): List<Array<Point>> {
        val originalMat = Mat()
        Utils.bitmapToMat(bitmap, originalMat)
        val candidates = findContourCandidates(originalMat, null)
        originalMat.release()
        return candidates.filter { it.score > 0.4 }.take(3).map { orderPointsForPerspectiveTransform(it.points) }
    }

    fun detectCorners(bitmap: Bitmap, cacheDir: File? = null): Array<Point>? {
        val originalMat = Mat()
        Utils.bitmapToMat(bitmap, originalMat)
        val candidates = findContourCandidates(originalMat, cacheDir)
        originalMat.release()
        val bestCandidate = candidates.firstOrNull()

        if (bestCandidate == null) {
            Log.d(TAG, "No valid contour found for document. Fallback failed as well.")
            return null
        }

        Log.d(TAG, "Selected best candidate with score=${String.format("%.3f", bestCandidate.score)}, isStrict=${bestCandidate.isStrict}")
        val points = bestCandidate.points
        // Step 3 - Order Points
        val orderedPoints = orderPointsForPerspectiveTransform(points)
        return orderedPoints
    }

    fun cropByCorners(bitmap: Bitmap, corners: Array<Point>): Bitmap {
        val originalMat = Mat()
        Utils.bitmapToMat(bitmap, originalMat)
        
        val orderedPoints = orderPointsForPerspectiveTransform(corners)
        val tl = orderedPoints[0]
        val tr = orderedPoints[1]
        val br = orderedPoints[2]
        val bl = orderedPoints[3]

        // Step 4 - Compute Destination Rectangle
        val widthA = sqrt((br.x - bl.x).pow(2.0) + (br.y - bl.y).pow(2.0))
        val widthB = sqrt((tr.x - tl.x).pow(2.0) + (tr.y - tl.y).pow(2.0))
        val maxWidth = max(widthA, widthB).toInt()

        val heightA = sqrt((tr.x - br.x).pow(2.0) + (tr.y - br.y).pow(2.0))
        val heightB = sqrt((tl.x - bl.x).pow(2.0) + (tl.y - bl.y).pow(2.0))
        val maxHeight = max(heightA, heightB).toInt()

        val isLandscape = maxWidth > maxHeight
        val finalWidth: Int
        val finalHeight: Int
        
        // Enforce true physical aspect ratio (1.67) to prevent perspective stretching
        if (isLandscape) {
            finalWidth = max(maxWidth, (maxHeight * 1.67).toInt())
            finalHeight = (finalWidth / 1.67).toInt()
        } else {
            finalHeight = max(maxHeight, (maxWidth * 1.67).toInt())
            finalWidth = (finalHeight / 1.67).toInt()
        }

        val srcMat = MatOfPoint2f(*orderedPoints)
        val dstMat = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(finalWidth - 1.0, 0.0),
            Point(finalWidth - 1.0, finalHeight - 1.0),
            Point(0.0, finalHeight - 1.0)
        )

        // Step 5 - Compute and Apply Transform
        val transformMatrix = Imgproc.getPerspectiveTransform(srcMat, dstMat)
        val warpedMat = Mat()
        Imgproc.warpPerspective(originalMat, warpedMat, transformMatrix, Size(finalWidth.toDouble(), finalHeight.toDouble()))

        val croppedBitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(warpedMat, croppedBitmap)

        originalMat.release()
        warpedMat.release()
        srcMat.release()
        dstMat.release()
        transformMatrix.release()

        return croppedBitmap
    }

    private fun orderPointsForPerspectiveTransform(pts: Array<Point>): Array<Point> {
        // Use mathematical Sum/Difference to robustly order points regardless of severe rotation
        // Sum of x+y will be smallest at top-left, largest at bottom-right
        // Difference x-y will be smallest at bottom-left, largest at top-right
        
        val sums = pts.map { it.x + it.y }
        val diffs = pts.map { it.x - it.y }
        
        val tl = pts[sums.indexOf(sums.minOrNull())]
        val br = pts[sums.indexOf(sums.maxOrNull())]
        val tr = pts[diffs.indexOf(diffs.maxOrNull())]
        val bl = pts[diffs.indexOf(diffs.minOrNull())]

        return arrayOf(tl, tr, br, bl)
    }
}
