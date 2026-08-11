package com.example.businesscardscanner

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.businesscardscanner.camera.CameraUtils
import com.example.businesscardscanner.camera.CardDetector
import com.example.businesscardscanner.ocr.TextRecognitionManager
import com.example.businesscardscanner.viewmodel.CaptureSide
import com.example.businesscardscanner.viewmodel.CaptureMode
import com.example.businesscardscanner.viewmodel.CardFlowViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.guava.await
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CaptureFragment : Fragment() {
    interface FlowHost {
        fun onScanQrRequested()
        fun onQrScanDone()
        fun onCardScanDone()
    }

    private val flowViewModel: CardFlowViewModel by activityViewModels()
    private val stabilityEngine = com.example.businesscardscanner.camera.StabilityEngine()
    private var currentUri: Uri? = null
    private var flashEnabled = false
    private var previewReadyLogged = false
    private val startInGallery by lazy { arguments?.getBoolean(ARG_START_IN_GALLERY, false) == true }

    // CameraX specific variables
    private var cameraProvider: ProcessCameraProvider? = null
    private var analysisExecutor: ExecutorService? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null
    private var previewUseCase: Preview? = null
    private var camera: androidx.camera.core.Camera? = null

    private var _binding: com.example.businesscardscanner.databinding.FragmentCaptureBinding? = null
    private val binding get() = _binding!!

    private val galleryPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                currentUri = uri
                flowViewModel.setRawImageUri(uri)
                flowViewModel.setImageUri(uri)
                flowViewModel.setCaptureMode(CaptureMode.CARD)
                (activity as? FlowHost)?.onCardScanDone()
            }
        } else {
            Toast.makeText(requireContext(), "Gallery selection cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = com.example.businesscardscanner.databinding.FragmentCaptureBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (arguments?.getString(ARG_CAPTURE_SIDE) == CaptureSide.BACK.name) {
            flowViewModel.setCaptureSide(CaptureSide.BACK)
            binding.pillLabel.text = "Back"
        } else {
            flowViewModel.setCaptureSide(CaptureSide.FRONT)
            binding.pillLabel.text = "Front"
        }

        // 1. Instantiate PreviewView programmatically exactly like QrScanFragment
        val scanFrame = binding.scanFrame
        val previewView = PreviewView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        
        // Ensure PreviewView is at the very bottom of the FrameLayout hierarchy
        scanFrame.addView(previewView, 0)

        previewView.previewStreamState.observe(viewLifecycleOwner) { state ->
            Log.d(TAG, "Preview stream state=$state")
            if (!previewReadyLogged && state == PreviewView.StreamState.STREAMING) {
                previewReadyLogged = true
                Log.d(TAG, "PreviewView is streaming frames")
            }
        }

        // Tap to focus
        previewView.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                focusAtPoint(previewView, event.x, event.y)
                v.performClick()
            }
            true
        }

        binding.header.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.root.findViewById<View>(R.id.btnLeftAction)?.setOnClickListener {
            openGalleryPicker()
        }
        binding.root.findViewById<View>(R.id.btnDone)?.setOnClickListener {
            captureImage()
        }
        // Hide the right action button in the bottom navigation per user request
        binding.root.findViewById<View>(R.id.btnRightAction)?.visibility = View.INVISIBLE

        binding.btnSwitchQr.setOnClickListener {
            (activity as? FlowHost)?.onScanQrRequested()
        }
        binding.btnRefresh.setOnClickListener {
            flashEnabled = !flashEnabled
            val iconRes = if (flashEnabled) R.drawable.ic_flash_on else R.drawable.ic_flash_off
            binding.btnRefresh.setImageResource(iconRes)
            cameraProvider?.unbindAll()
            bindCamera(previewView, flashEnabled)
        }
        
        bindCamera(previewView, flashEnabled)
        
        if (startInGallery) {
            openGalleryPicker()
        }
    }

    private fun focusAtPoint(previewView: PreviewView, x: Float, y: Float) {
        val factory = previewView.meteringPointFactory
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE).build()
        camera?.cameraControl?.startFocusAndMetering(action)
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

            val previewResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(android.util.Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER))
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(previewResolutionSelector)
                .build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            this.previewUseCase = preview

            val captureResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(android.util.Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER))
                .build()

            val capture = ImageCapture.Builder()
                .setResolutionSelector(captureResolutionSelector)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            this.imageCapture = capture

            val analysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(android.util.Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER))
                .build()

            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(analysisResolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            this.imageAnalysis = analysis

            analysisExecutor?.shutdownNow()
            analysisExecutor = Executors.newSingleThreadExecutor()
            
            analysis.setAnalyzer(analysisExecutor!!) { imageProxy ->
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                try {
                    val cards = CardDetector.processImageProxy(imageProxy, rotationDegrees)
                    requireActivity().runOnUiThread {
                        if (!isAdded) return@runOnUiThread
                        val overlay = view?.findViewById<com.example.businesscardscanner.camera.CardOverlayView>(R.id.cardOverlay)
                        overlay?.updateCards(cards)
                        overlay?.onCardTapped = { selectedCard ->
                            Log.d(TAG, "User tapped a specific card! Capturing...")
                            captureImage()
                        }
                        
                        if (stabilityEngine.analyze(cards)) {
                            Log.d(TAG, "StabilityEngine triggered auto-capture!")
                            captureImage()
                        }
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Analyzer frame crashed", t)
                } finally {
                    imageProxy.close()
                }
            }

            val selector = CameraSelector.DEFAULT_BACK_CAMERA
            runCatching {
                provider.unbindAll()
                // 2. ONLY bind Preview + ImageAnalysis initially! Bypassing the 3-stream HAL limitation.
                camera = provider.bindToLifecycle(viewLifecycleOwner, selector, preview, analysis)
                Log.d(TAG, "bindToLifecycle success (Preview + Analysis ONLY) camera=$camera")
                camera?.cameraControl?.enableTorch(torchEnabled)
            }.onFailure {
                Log.e(TAG, "bindToLifecycle failed", it)
                flowViewModel.setError("Camera unavailable. Please retry.")
                Toast.makeText(requireContext(), "Camera failed to start", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun captureImage() {
        val capture = imageCapture ?: return
        val provider = cameraProvider ?: return
        val analysis = imageAnalysis ?: return
        val preview = previewUseCase ?: return

        val startedAt = SystemClock.elapsedRealtime()
        val appContext = requireContext().applicationContext
        
        viewLifecycleOwner.lifecycleScope.launch {
            Log.d(TAG, "Initiating dynamic swap: Unbinding Analysis, Binding Capture...")
            runCatching {
                provider.unbind(analysis)
                camera = provider.bindToLifecycle(viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, capture)
            }.onFailure {
                Log.e(TAG, "Dynamic bindToLifecycle for Capture failed", it)
                flowViewModel.setError("Capture bind failed.")
                return@launch
            }

            // Lock focus before snapping
            Log.d(TAG, "Locking focus before capture...")
            val cameraControl = camera?.cameraControl
            if (cameraControl != null) {
                val factory = androidx.camera.core.SurfaceOrientedMeteringPointFactory(1f, 1f)
                val action = FocusMeteringAction.Builder(factory.createPoint(0.5f, 0.5f), FocusMeteringAction.FLAG_AF).build()
                try {
                    cameraControl.startFocusAndMetering(action).await()
                } catch (e: Exception) {
                    Log.e(TAG, "Focus lock failed, proceeding anyway", e)
                }
            }
            
            Log.d(TAG, "captureImage requested uri=$currentUri capture=$capture")
            Log.d(PERF_TAG, "capture:ui_tap_to_capture_start elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            
            runCatching {
                val uri = CameraUtils.captureToMediaStore(appContext, capture)
                Log.d(TAG, "captureImage success savedUri=$uri")
                Log.d(PERF_TAG, "capture:callback_to_uri_ready elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
                currentUri = uri
                flowViewModel.setRawImageUri(uri)
                flowViewModel.setImageUri(uri)
                flowViewModel.setCaptureMode(CaptureMode.CARD)
                Log.d(PERF_TAG, "capture:ui_navigation_start elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
                (activity as? FlowHost)?.onCardScanDone()
            }.onFailure {
                Log.e(TAG, "captureImage failed", it)
                flowViewModel.setError("Capture failed. Please try again.")
                Toast.makeText(requireContext(), "Capture failed", Toast.LENGTH_SHORT).show()
            }
            
            // Restore Analysis flow in case the user navigates back to this fragment instance
            Log.d(TAG, "Restoring dynamic swap: Unbinding Capture, Binding Analysis...")
            runCatching {
                provider.unbind(capture)
                camera = provider.bindToLifecycle(viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, analysis)
            }.onFailure {
                Log.e(TAG, "Failed to restore analysis", it)
            }
        }
    }

    private suspend fun extractText(context: Context, uri: Uri): Boolean {
        return runCatching {
            Log.d(TAG, "OCR start uri=$uri")
            val result = TextRecognitionManager(context).analyze(uri)
            Log.d(TAG, "OCR raw length=${result.rawText.length} name=${result.name.value} company=${result.company.value} job=${result.jobTitle.value}")
            if (result.rawText.isBlank()) {
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        flowViewModel.setError("Couldn't read this card clearly. Try again or enter details manually.")
                        Toast.makeText(requireContext(), "Couldn't read this card clearly", Toast.LENGTH_SHORT).show()
                    }
                }
                false
            } else {
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        flowViewModel.setOcrResult(result)
                    }
                }
                true
            }
        }.getOrElse {
            Log.e(TAG, "OCR failed", it)
            withContext(Dispatchers.Main) {
                if (isAdded) {
                    flowViewModel.setError("OCR failed. Please retake the card photo.")
                    Toast.makeText(requireContext(), "OCR failed", Toast.LENGTH_SHORT).show()
                }
            }
            false
        }
    }

    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView unbinding camera")
        cameraProvider?.unbindAll()
        analysisExecutor?.shutdownNow()
        analysisExecutor = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_START_IN_GALLERY = "arg_start_in_gallery"
        private const val ARG_CAPTURE_SIDE = "arg_capture_side"
        private const val TAG = "CardScannerCamera"
        private const val PERF_TAG = "CardScannerPerf"

        fun newInstance(startInGallery: Boolean = false, captureSide: CaptureSide = CaptureSide.FRONT) = CaptureFragment().apply {
            arguments = Bundle().apply {
                putBoolean(ARG_START_IN_GALLERY, startInGallery)
                putString(ARG_CAPTURE_SIDE, captureSide.name)
            }
        }
    }
}
