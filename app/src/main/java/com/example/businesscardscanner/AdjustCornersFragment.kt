package com.example.businesscardscanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.businesscardscanner.ocr.DocumentScanner
import com.example.businesscardscanner.ocr.ImagePreprocessor
import com.example.businesscardscanner.viewmodel.CardFlowViewModel
import com.example.businesscardscanner.views.PolygonView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.core.Point
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class AdjustCornersFragment : Fragment() {

    interface FlowHost {
        fun onCornersAdjusted()
    }

    private val flowViewModel: CardFlowViewModel by activityViewModels()

    private var rawBitmap: Bitmap? = null
    private var imageMatrix = Matrix()

    private var _binding: com.example.businesscardscanner.databinding.FragmentAdjustCornersBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = com.example.businesscardscanner.databinding.FragmentAdjustCornersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val uri = flowViewModel.state.value.rawImageUri ?: flowViewModel.state.value.imageUri
        if (uri == null) {
            Toast.makeText(requireContext(), "No image found", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            loadRawBitmap(uri)
        }

        binding.btnConfirm.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                cropAndProceed()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private suspend fun loadRawBitmap(uri: Uri) {
        try {
            val context = requireContext().applicationContext
            if (!org.opencv.android.OpenCVLoader.initDebug()) {
                Log.e(TAG, "OpenCV init failed")
            }
            
            // Load bitmap respecting orientation, but do not scale down excessively to preserve crop quality
            var bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return
            
            val orientation = context.contentResolver.openInputStream(uri)?.use { 
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
            } ?: ExifInterface.ORIENTATION_UNDEFINED

            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees != 0f) {
                val matrix = Matrix().apply { postRotate(degrees) }
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }

            rawBitmap = bitmap

            // Detect corners in background
            val detectedCorners = DocumentScanner.detectCorners(bitmap)

            withContext(Dispatchers.Main) {
                if (isAdded) {
                    binding.imgRawPreview.setImageBitmap(bitmap)
                    binding.imgRawPreview.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            if (binding.imgRawPreview.width > 0 && binding.imgRawPreview.height > 0) {
                                binding.imgRawPreview.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                setupPolygon(detectedCorners)
                            }
                        }
                    })
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load image", e)
        }
    }

    private fun setupPolygon(detectedCorners: Array<Point>?) {
        val bitmap = rawBitmap ?: return
        val imageWidth = bitmap.width.toFloat()
        val imageHeight = bitmap.height.toFloat()

        val viewWidth = binding.imgRawPreview.width.toFloat()
        val viewHeight = binding.imgRawPreview.height.toFloat()

        val scaleX = viewWidth / imageWidth
        val scaleY = viewHeight / imageHeight
        val scale = scaleX.coerceAtMost(scaleY)

        val scaledWidth = imageWidth * scale
        val scaledHeight = imageHeight * scale
        val dx = (viewWidth - scaledWidth) / 2f
        val dy = (viewHeight - scaledHeight) / 2f

        imageMatrix.reset()
        imageMatrix.postScale(scale, scale)
        imageMatrix.postTranslate(dx, dy)

        val points = mutableListOf<PointF>()
        if (detectedCorners != null && detectedCorners.size == 4) {
            detectedCorners.forEach { pt ->
                val mapped = FloatArray(2) { 0f }
                imageMatrix.mapPoints(mapped, floatArrayOf(pt.x.toFloat(), pt.y.toFloat()))
                points.add(PointF(mapped[0], mapped[1]))
            }
        } else {
            // Default inset rectangle
            val insetX = scaledWidth * 0.1f
            val insetY = scaledHeight * 0.1f
            points.add(PointF(dx + insetX, dy + insetY)) // TL
            points.add(PointF(dx + scaledWidth - insetX, dy + insetY)) // TR
            points.add(PointF(dx + scaledWidth - insetX, dy + scaledHeight - insetY)) // BR
            points.add(PointF(dx + insetX, dy + scaledHeight - insetY)) // BL
        }

        binding.polygonView.setPoints(points)
    }

    private suspend fun cropAndProceed() {
        val bitmap = rawBitmap ?: return
        val points = binding.polygonView.getPoints()
        if (points.size != 4) return

        val inverseMatrix = Matrix()
        imageMatrix.invert(inverseMatrix)

        val bitmapCorners = Array(4) { Point() }
        for (i in 0 until 4) {
            val unmapped = FloatArray(2)
            inverseMatrix.mapPoints(unmapped, floatArrayOf(points[i].x, points[i].y))
            bitmapCorners[i] = Point(unmapped[0].toDouble().coerceIn(0.0, bitmap.width.toDouble()), unmapped[1].toDouble().coerceIn(0.0, bitmap.height.toDouble()))
        }

        val startedAt = SystemClock.elapsedRealtime()
        Log.d("CardScannerDocScan", "cropAndProceed invoked")

        var cropped = try {
            DocumentScanner.cropByCorners(bitmap, bitmapCorners)
        } catch (e: Exception) {
            Log.e(TAG, "Crop failed", e)
            bitmap
        }

        // Force Landscape Orientation
        if (cropped.height > cropped.width) {
            val matrix = Matrix().apply { postRotate(-90f) }
            cropped = Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
        }

        // Save cropped to a new URI
        val file = File(requireContext().cacheDir, "cropped_card_${System.currentTimeMillis()}.jpg")
        try {
            FileOutputStream(file).use { out ->
                cropped.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            val croppedUri = Uri.fromFile(file)
            Log.d("CardScannerDocScan", "Saved cropped image to $croppedUri")
            
            withContext(Dispatchers.Main) {
                flowViewModel.setImageUri(croppedUri)
                (activity as? FlowHost)?.onCornersAdjusted()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cropped image", e)
        }
    }

    companion object {
        private const val TAG = "AdjustCornersFragment"
        fun newInstance() = AdjustCornersFragment()
    }
}
