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
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.businesscardscanner.databinding.FragmentCaptureDoneBinding
import com.example.businesscardscanner.ocr.DocumentScanner
import com.example.businesscardscanner.ocr.ImagePreprocessor
import com.example.businesscardscanner.viewmodel.CaptureMode
import com.example.businesscardscanner.viewmodel.CaptureSide
import com.example.businesscardscanner.viewmodel.CardFlowViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.core.Point
import java.io.File
import java.io.FileOutputStream

class CaptureDoneFragment : Fragment() {

    private var _binding: FragmentCaptureDoneBinding? = null
    private val binding get() = _binding!!

    private val flowViewModel: CardFlowViewModel by activityViewModels()

    private var rawBitmap: Bitmap? = null
    private var imageMatrix = Matrix()
    private var detectedCorners: Array<Point>? = null
    
    // History for Undo/Redo
    private val history = mutableListOf<List<PointF>>()
    private var historyIndex = -1
    
    private var selectedFilter = "Auto"

    interface FlowHost {
        fun onCaptureDoneNext()
        fun onCaptureBackRequested()
        fun onSkipBackCapture()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCaptureDoneBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val uri = flowViewModel.state.value.rawImageUri ?: flowViewModel.state.value.imageUri
        if (uri == null) {
            Toast.makeText(requireContext(), "No image found", Toast.LENGTH_SHORT).show()
            requireActivity().onBackPressedDispatcher.onBackPressed()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            loadRawBitmap(uri)
        }

        binding.header.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.btnUndo.setOnClickListener { undo() }
        binding.btnRedo.setOnClickListener { redo() }

        // Bottom Actions
        binding.root.findViewById<View>(R.id.btnLeftAction)?.setOnClickListener {
            // Before & After Preview (Toggle original/cropped view or just show raw)
            Toast.makeText(requireContext(), "Compare preview", Toast.LENGTH_SHORT).show()
        }

        binding.root.findViewById<View>(R.id.btnDone)?.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                cropAndProceed()
            }
        }

        binding.root.findViewById<View>(R.id.btnRightAction)?.setOnClickListener {
            // Retake
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        
        binding.polygonView.setOnTouchListener { v, event ->
            // Let the PolygonView handle the touch first
            val handled = v.onTouchEvent(event)
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                saveStateToHistory()
            }
            handled
        }

        binding.filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            selectedFilter = when (checkedIds.first()) {
                R.id.chipAuto -> "Auto"
                R.id.chipSharpen -> "Sharpen"
                R.id.chipBW -> "BW"
                R.id.chipHighContrast -> "Contrast"
                R.id.chipBWScan -> "BW_Scan"
                R.id.chipShadowRemove -> "Shadow_Remove"
                R.id.chipMagicColor -> "Magic_Color"
                else -> "Original"
            }
            updatePreviewWithFilter()
        }
    }

    private fun updatePreviewWithFilter() {
        val bitmap = rawBitmap ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val previewBitmap = if (selectedFilter == "Original") {
                bitmap
            } else {
                applyFilterToBitmap(bitmap, selectedFilter)
            }
            withContext(Dispatchers.Main) {
                binding.imgRawPreview.setImageBitmap(previewBitmap)
            }
        }
    }

    private fun applyFilterToBitmap(original: Bitmap, filterType: String): Bitmap {
        if (filterType == "Auto") {
            return ImagePreprocessor.applyAdaptiveEnhancement(original)
        }

        val mat = org.opencv.core.Mat()
        org.opencv.android.Utils.bitmapToMat(original, mat)
        val resultMat = org.opencv.core.Mat()

        when (filterType) {
            "Sharpen" -> {
                val blur = org.opencv.core.Mat()
                org.opencv.imgproc.Imgproc.GaussianBlur(mat, blur, org.opencv.core.Size(0.0, 0.0), 3.0)
                org.opencv.core.Core.addWeighted(mat, 1.5, blur, -0.5, 0.0, resultMat)
                blur.release()
            }
            "BW" -> {
                org.opencv.imgproc.Imgproc.cvtColor(mat, resultMat, org.opencv.imgproc.Imgproc.COLOR_RGBA2GRAY)
                org.opencv.imgproc.Imgproc.cvtColor(resultMat, resultMat, org.opencv.imgproc.Imgproc.COLOR_GRAY2RGBA)
            }
            "BW_Scan" -> {
                val gray = org.opencv.core.Mat()
                org.opencv.imgproc.Imgproc.cvtColor(mat, gray, org.opencv.imgproc.Imgproc.COLOR_RGBA2GRAY)
                org.opencv.imgproc.Imgproc.GaussianBlur(gray, gray, org.opencv.core.Size(5.0, 5.0), 0.0)
                org.opencv.imgproc.Imgproc.adaptiveThreshold(
                    gray, resultMat, 255.0,
                    org.opencv.imgproc.Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    org.opencv.imgproc.Imgproc.THRESH_BINARY, 21, 10.0
                )
                org.opencv.imgproc.Imgproc.cvtColor(resultMat, resultMat, org.opencv.imgproc.Imgproc.COLOR_GRAY2RGBA)
                gray.release()
            }
            "Shadow_Remove" -> {
                val rgb = org.opencv.core.Mat()
                org.opencv.imgproc.Imgproc.cvtColor(mat, rgb, org.opencv.imgproc.Imgproc.COLOR_RGBA2RGB)
                val bg = org.opencv.core.Mat()
                val kernel = org.opencv.imgproc.Imgproc.getStructuringElement(org.opencv.imgproc.Imgproc.MORPH_ELLIPSE, org.opencv.core.Size(21.0, 21.0))
                org.opencv.imgproc.Imgproc.dilate(rgb, bg, kernel)
                org.opencv.imgproc.Imgproc.GaussianBlur(bg, bg, org.opencv.core.Size(21.0, 21.0), 0.0)
                val diff = org.opencv.core.Mat()
                org.opencv.core.Core.divide(rgb, bg, diff, 255.0)
                org.opencv.imgproc.Imgproc.cvtColor(diff, resultMat, org.opencv.imgproc.Imgproc.COLOR_RGB2RGBA)
                rgb.release()
                bg.release()
                kernel.release()
                diff.release()
            }
            "Magic_Color" -> {
                val hsv = org.opencv.core.Mat()
                org.opencv.imgproc.Imgproc.cvtColor(mat, hsv, org.opencv.imgproc.Imgproc.COLOR_RGBA2RGB)
                org.opencv.imgproc.Imgproc.cvtColor(hsv, hsv, org.opencv.imgproc.Imgproc.COLOR_RGB2HSV)
                val channels = java.util.ArrayList<org.opencv.core.Mat>()
                org.opencv.core.Core.split(hsv, channels)
                val sChannel = channels[1]
                val vChannel = channels[2]
                val sMask = org.opencv.core.Mat()
                org.opencv.imgproc.Imgproc.threshold(sChannel, sMask, 80.0, 255.0, org.opencv.imgproc.Imgproc.THRESH_BINARY_INV)
                val vMask = org.opencv.core.Mat()
                org.opencv.imgproc.Imgproc.threshold(vChannel, vMask, 180.0, 255.0, org.opencv.imgproc.Imgproc.THRESH_BINARY)
                val bgMask = org.opencv.core.Mat()
                org.opencv.core.Core.bitwise_and(sMask, vMask, bgMask)
                vChannel.setTo(org.opencv.core.Scalar(255.0), bgMask)
                org.opencv.core.Core.merge(channels, hsv)
                org.opencv.imgproc.Imgproc.cvtColor(hsv, resultMat, org.opencv.imgproc.Imgproc.COLOR_HSV2RGB)
                org.opencv.imgproc.Imgproc.cvtColor(resultMat, resultMat, org.opencv.imgproc.Imgproc.COLOR_RGB2RGBA)
                hsv.release()
                channels.forEach { it.release() }
                sMask.release()
                vMask.release()
                bgMask.release()
            }
            "Contrast" -> {
                mat.convertTo(resultMat, -1, 1.5, 20.0)
            }
            else -> {
                mat.copyTo(resultMat)
            }
        }

        val resultBitmap = Bitmap.createBitmap(resultMat.cols(), resultMat.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(resultMat, resultBitmap)
        mat.release()
        resultMat.release()
        return resultBitmap
    }

    private suspend fun loadRawBitmap(uri: Uri) {
        try {
            val context = requireContext().applicationContext
            if (!org.opencv.android.OpenCVLoader.initDebug()) {
                Log.e(TAG, "OpenCV init failed")
            }

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
            detectedCorners = DocumentScanner.detectCorners(bitmap)

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
            val insetX = scaledWidth * 0.1f
            val insetY = scaledHeight * 0.1f
            points.add(PointF(dx + insetX, dy + insetY))
            points.add(PointF(dx + scaledWidth - insetX, dy + insetY))
            points.add(PointF(dx + scaledWidth - insetX, dy + scaledHeight - insetY))
            points.add(PointF(dx + insetX, dy + scaledHeight - insetY))
        }

        binding.polygonView.setPoints(points)
        history.clear()
        saveStateToHistory()
    }

    private fun saveStateToHistory() {
        val currentPoints = binding.polygonView.getPoints().map { PointF(it.x, it.y) }
        
        // Remove redo history if we make a new edit
        if (historyIndex < history.size - 1) {
            history.subList(historyIndex + 1, history.size).clear()
        }
        
        history.add(currentPoints)
        historyIndex = history.size - 1
        updateUndoRedoButtons()
    }

    private fun undo() {
        if (historyIndex > 0) {
            historyIndex--
            binding.polygonView.setPoints(history[historyIndex].map { PointF(it.x, it.y) })
            updateUndoRedoButtons()
        }
    }

    private fun redo() {
        if (historyIndex < history.size - 1) {
            historyIndex++
            binding.polygonView.setPoints(history[historyIndex].map { PointF(it.x, it.y) })
            updateUndoRedoButtons()
        }
    }

    private fun updateUndoRedoButtons() {
        binding.btnUndo.alpha = if (historyIndex > 0) 1.0f else 0.5f
        binding.btnRedo.alpha = if (historyIndex < history.size - 1) 1.0f else 0.5f
        
        binding.btnUndo.isEnabled = historyIndex > 0
        binding.btnRedo.isEnabled = historyIndex < history.size - 1
    }

    private suspend fun cropAndProceed() {
        val bitmap = rawBitmap ?: return
        val points = binding.polygonView.getPoints()
        if (points.size != 4) return

        withContext(Dispatchers.Main) {
            binding.loadingOverlay.visibility = View.VISIBLE
        }

        val inverseMatrix = Matrix()
        imageMatrix.invert(inverseMatrix)

        val bitmapCorners = Array(4) { Point() }
        for (i in 0 until 4) {
            val unmapped = FloatArray(2)
            inverseMatrix.mapPoints(unmapped, floatArrayOf(points[i].x, points[i].y))
            bitmapCorners[i] = Point(unmapped[0].toDouble().coerceIn(0.0, bitmap.width.toDouble()), unmapped[1].toDouble().coerceIn(0.0, bitmap.height.toDouble()))
        }

        var cropped = try {
            DocumentScanner.cropByCorners(bitmap, bitmapCorners)
        } catch (e: Exception) {
            Log.e(TAG, "Crop failed", e)
            bitmap
        }

        if (selectedFilter != "Original") {
            cropped = applyFilterToBitmap(cropped, selectedFilter)
        }

        if (cropped.height > cropped.width) {
            val matrix = Matrix().apply { postRotate(-90f) }
            cropped = Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
        }

        val file = File(requireContext().cacheDir, "cropped_card_${System.currentTimeMillis()}.jpg")
        try {
            FileOutputStream(file).use { out ->
                cropped.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            val croppedUri = Uri.fromFile(file)
            
            withContext(Dispatchers.Main) {
                flowViewModel.setImageUri(croppedUri)
                if (flowViewModel.state.value.captureSide == CaptureSide.FRONT) {
                    showBackCaptureDialog()
                } else {
                    (activity as? FlowHost)?.onCaptureDoneNext()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cropped image", e)
            withContext(Dispatchers.Main) {
                binding.loadingOverlay.visibility = View.GONE
            }
        }
    }

    private fun showBackCaptureDialog() {
        binding.loadingOverlay.visibility = View.GONE
        val dialogBinding = com.example.businesscardscanner.databinding.DialogBackSideCaptureBinding.inflate(LayoutInflater.from(requireContext()))
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.btnSkip.setOnClickListener {
            dialog.dismiss()
            (activity as? FlowHost)?.onSkipBackCapture()
        }

        dialogBinding.btnCaptureBack.setOnClickListener {
            dialog.dismiss()
            (activity as? FlowHost)?.onCaptureBackRequested()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "CaptureDoneFragment"
        
        fun newInstance(needsAutoProcessing: Boolean = false) = CaptureDoneFragment().apply {
            arguments = Bundle().apply {
                putBoolean("arg_needs_auto", needsAutoProcessing)
            }
        }
    }
}
