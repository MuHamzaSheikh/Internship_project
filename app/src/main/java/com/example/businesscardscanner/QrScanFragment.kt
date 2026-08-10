package com.example.businesscardscanner

import android.content.Context
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.businesscardscanner.viewmodel.CaptureMode
import com.example.businesscardscanner.viewmodel.CardFlowViewModel
import com.example.businesscardscanner.ocr.BusinessCardOcrParser
import com.example.businesscardscanner.ocr.ImagePreprocessor
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator

class QrScanFragment : Fragment() {
    interface FlowHost {
        fun onQrScanDone()
        fun onScanCardRequested()
        fun onGalleryRequested()
    }

    private val flowViewModel: CardFlowViewModel by activityViewModels()
    private var cameraProvider: ProcessCameraProvider? = null
    private var flashEnabled = false
    private var scanned = false
    private var analyzerFrameCount = 0
    private var surfaceStreamingLogged = false
    private var analysisExecutor: ExecutorService? = null
    private var scanBeamAnimator: ObjectAnimator? = null
    private val startInGallery by lazy { arguments?.getBoolean(ARG_START_IN_GALLERY, false) == true }

    private val galleryPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching {
                    val bitmap = ImagePreprocessor.loadAndEnhance(requireContext(), uri)
                    val inputImage = InputImage.fromBitmap(bitmap, 0)
                    BarcodeScanning.getClient().process(inputImage).await().firstOrNull()?.rawValue
                }.onSuccess { barcode ->
                    if (!barcode.isNullOrBlank()) {
                        scanned = true
                        flowViewModel.setQrResult(barcode)
                        val parsed = BusinessCardOcrParser.parseQrPayload(barcode)
                        if (parsed.parsedValues.isNotEmpty()) {
                            flowViewModel.setOcrResult(parsed)
                        }
                        (activity as? FlowHost)?.onQrScanDone()
                    } else {
                        Toast.makeText(requireContext(), "No QR code detected. Try again or switch to card capture.", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure {
                    Toast.makeText(requireContext(), "Unable to read this image. Please pick another one.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(requireContext(), "Gallery selection cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private var _binding: com.example.businesscardscanner.databinding.FragmentQrScanBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = com.example.businesscardscanner.databinding.FragmentQrScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val previewContainer = binding.qrPreviewContainer
        val previewView = PreviewView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val overlay = ImageView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setImageResource(R.drawable.capture_corner_brackets)
            scaleType = ImageView.ScaleType.FIT_XY
            contentDescription = null
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        previewView.previewStreamState.observe(viewLifecycleOwner) { state ->
            Log.d(TAG, "Preview stream state=$state")
            if (!surfaceStreamingLogged && state == PreviewView.StreamState.STREAMING) {
                surfaceStreamingLogged = true
                Log.d(TAG, "PreviewView is streaming frames")
            }
        }
        previewContainer.removeAllViews()
        previewContainer.addView(previewView)
        previewContainer.addView(overlay)

        binding.root.findViewById<View>(R.id.btnLeftAction)?.setOnClickListener {
            flowViewModel.setCaptureMode(CaptureMode.QR)
            (activity as? FlowHost)?.onGalleryRequested()
        }
        binding.root.findViewById<View>(R.id.btnDone)?.setOnClickListener {
            if (scanned) {
                (activity as? FlowHost)?.onQrScanDone()
            } else {
                android.widget.Toast.makeText(requireContext(), "No QR code detected. Try again or switch to card capture.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        binding.root.findViewById<View>(R.id.btnRightAction)?.setOnClickListener {
            flowViewModel.setCaptureMode(CaptureMode.CARD)
            (activity as? FlowHost)?.onScanCardRequested()
        }
        binding.header.toolbarTitle.text = "Scanning QR code"
        binding.header.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.btnRefresh.setOnClickListener {
            cameraProvider?.unbindAll()
            flashEnabled = !flashEnabled
            val iconRes = if (flashEnabled) R.drawable.ic_flash_on else R.drawable.ic_flash_off
            binding.btnRefresh.setImageResource(iconRes)
            bindCamera(previewView, flashEnabled)
        }
        bindCamera(previewView, flashEnabled)
        if (startInGallery) {
            openGalleryPicker()
        }

        // Animated Scanning Beam
        val scanBeam = binding.root.findViewById<View>(R.id.scanBeam)
        scanBeam?.post {
            val parentHeight = previewContainer.height.toFloat()
            val beamHeight = scanBeam.height.toFloat()
            scanBeamAnimator = ObjectAnimator.ofFloat(scanBeam, "translationY", 0f, parentHeight - beamHeight).apply {
                duration = 2000
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()
                start()
            }
        }
    }

    private fun openGalleryPicker() {
        galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun bindCamera(previewView: PreviewView, torchEnabled: Boolean) {
        Log.d(TAG, "bindCamera requested flash=$torchEnabled lifecycle=${viewLifecycleOwner.lifecycle.currentState}")
        val providerFuture = ProcessCameraProvider.getInstance(requireContext())
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider
            val previewResolutionSelector = androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                .setResolutionStrategy(androidx.camera.core.resolutionselector.ResolutionStrategy(android.util.Size(1280, 720), androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER))
                .build()
            val preview = Preview.Builder()
                .setResolutionSelector(previewResolutionSelector)
                .build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                
            val analysisResolutionSelector = androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                .setResolutionStrategy(androidx.camera.core.resolutionselector.ResolutionStrategy(android.util.Size(640, 480), androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER))
                .build()
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(analysisResolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysisExecutor?.shutdownNow()
            analysisExecutor = Executors.newSingleThreadExecutor()
            analysis.setAnalyzer(analysisExecutor!!) { imageProxy ->
                analyzerFrameCount++
                val frameNo = analyzerFrameCount
                Log.d(TAG, "Analyzer frame #$frameNo rotation=${imageProxy.imageInfo.rotationDegrees} scanned=$scanned")
                try {
                    val mediaImage = imageProxy.image
                    if (mediaImage == null) {
                        Log.w(TAG, "Analyzer frame #$frameNo has null mediaImage")
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    if (scanned) {
                        Log.d(TAG, "Analyzer frame #$frameNo skipped because already scanned")
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    Log.d(TAG, "Analyzer frame #$frameNo submitting to BarcodeScanning")
                    BarcodeScanning.getClient().process(inputImage)
                        .addOnSuccessListener { barcodes ->
                            val barcode = barcodes.firstOrNull()?.rawValue
                            Log.d(TAG, "Analyzer frame #$frameNo barcodeResult=${barcode ?: "<null>"} count=${barcodes.size}")
                            if (!barcode.isNullOrBlank() && !scanned) {
                                scanned = true
                                flowViewModel.setQrResult(barcode)
                                val parsed = BusinessCardOcrParser.parseQrPayload(barcode)
                                if (parsed.parsedValues.isNotEmpty()) {
                                    flowViewModel.setOcrResult(parsed)
                                }
                                vibrate()
                                if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                                    (activity as? FlowHost)?.onQrScanDone()
                                }
                            }
                        }
                        .addOnFailureListener { error ->
                            Log.e(TAG, "Analyzer frame #$frameNo barcode scan failed", error)
                        }
                        .addOnCompleteListener {
                            Log.d(TAG, "Analyzer frame #$frameNo completed")
                            imageProxy.close()
                        }
                } catch (t: Throwable) {
                    Log.e(TAG, "Analyzer frame #$frameNo crashed", t)
                    imageProxy.close()
                }
            }
            val selector = CameraSelector.DEFAULT_BACK_CAMERA
            runCatching {
                provider.unbindAll()
                val camera = provider.bindToLifecycle(viewLifecycleOwner, selector, preview, analysis)
                Log.d(TAG, "bindToLifecycle success camera=$camera preview=$preview analysis=$analysis")
                camera.cameraControl.enableTorch(torchEnabled)
            }.onFailure {
                Log.e(TAG, "bindToLifecycle failed", it)
                Toast.makeText(requireContext(), "Camera failed to start", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun vibrate() {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val manager = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(120)
        }
    }

    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView unbinding camera and shutting down executor")
        scanBeamAnimator?.cancel()
        scanBeamAnimator = null
        cameraProvider?.unbindAll()
        analysisExecutor?.shutdownNow()
        analysisExecutor = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_START_IN_GALLERY = "arg_start_in_gallery"
        private const val TAG = "CardScannerCamera"

        fun newInstance(startInGallery: Boolean = false) = QrScanFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_START_IN_GALLERY, startInGallery) }
        }
    }
}
