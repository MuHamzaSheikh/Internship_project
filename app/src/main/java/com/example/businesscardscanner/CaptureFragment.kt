package com.example.businesscardscanner

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.businesscardscanner.camera.CameraUtils
import com.example.businesscardscanner.ocr.TextRecognitionManager
import com.example.businesscardscanner.viewmodel.CaptureSide
import com.example.businesscardscanner.viewmodel.CaptureMode
import com.example.businesscardscanner.viewmodel.CardFlowViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CaptureFragment : Fragment() {
    interface FlowHost {
        fun onScanQrRequested()
        fun onQrScanDone()
        fun onCardScanDone()
    }

    private val flowViewModel: CardFlowViewModel by activityViewModels()
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var currentUri: Uri? = null
    private var flashEnabled = false
    private var cameraBindAttempt = 0
    private var previewReadyLogged = false
    private val startInGallery by lazy { arguments?.getBoolean(ARG_START_IN_GALLERY, false) == true }

    private val galleryPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                currentUri = uri
                flowViewModel.setImageUri(uri)
                flowViewModel.setCaptureMode(CaptureMode.CARD)
                if (extractText(uri)) {
                    (activity as? FlowHost)?.onCardScanDone()
                }
            }
        } else {
            Toast.makeText(requireContext(), "Gallery selection cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_capture, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (arguments?.getString(ARG_CAPTURE_SIDE) == CaptureSide.BACK.name) {
            flowViewModel.setCaptureSide(CaptureSide.BACK)
        } else {
            flowViewModel.setCaptureSide(CaptureSide.FRONT)
        }
        val previewContainer = view.findViewById<ViewGroup>(R.id.scanFrame)
        val previewView = PreviewView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
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
            if (!previewReadyLogged && state == PreviewView.StreamState.STREAMING) {
                previewReadyLogged = true
            }
        }
        previewContainer.removeAllViews()
        previewContainer.addView(previewView)
        previewContainer.addView(overlay)

        view.findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        view.findViewById<View>(R.id.btnGallery).setOnClickListener {
            openGalleryPicker()
        }
        view.findViewById<View>(R.id.btnDone).setOnClickListener {
            captureImage()
        }
        view.findViewById<View>(R.id.btnScanQR).setOnClickListener {
            (activity as? FlowHost)?.onScanQrRequested()
        }
        view.findViewById<ImageView>(R.id.btnRefresh).setOnClickListener {
            flashEnabled = !flashEnabled
            bindCamera(previewView)
        }
        bindCamera(previewView)
        if (startInGallery) {
            openGalleryPicker()
        }
    }

    private fun openGalleryPicker() {
        galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun bindCamera(previewView: PreviewView) {
        cameraBindAttempt++
        Log.d(TAG, "bindCamera attempt=$cameraBindAttempt flash=$flashEnabled lifecycle=${viewLifecycleOwner.lifecycle.currentState}")
        val providerFuture = ProcessCameraProvider.getInstance(requireContext())
        providerFuture.addListener({
            runCatching {
                val provider = providerFuture.get()
                cameraProvider = provider
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
                Log.d(TAG, "bindToLifecycle success camera=$camera preview=$preview imageCapture=$imageCapture")
                camera.cameraControl.enableTorch(flashEnabled)
            }.onFailure { error ->
                Log.e(TAG, "bindToLifecycle failed", error)
                flowViewModel.setError("Camera unavailable. Please retry.")
                Toast.makeText(requireContext(), "Camera unavailable", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun captureImage() {
        val capture = imageCapture ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            flowViewModel.setProcessing(true)
            Log.d(TAG, "captureImage requested uri=$currentUri capture=$capture")
            runCatching {
                val uri = CameraUtils.captureToMediaStore(requireContext(), capture)
                Log.d(TAG, "captureImage success savedUri=$uri")
                currentUri = uri
                flowViewModel.setImageUri(uri)
                flowViewModel.setCaptureMode(CaptureMode.CARD)
                if (extractText(uri)) {
                    (activity as? FlowHost)?.onCardScanDone()
                }
            }.onFailure {
                Log.e(TAG, "captureImage failed", it)
                flowViewModel.setError("Capture failed. Please try again.")
                Toast.makeText(requireContext(), "Capture failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun extractText(uri: Uri): Boolean {
        return runCatching {
            Log.d(TAG, "OCR start uri=$uri")
            val result = TextRecognitionManager(requireContext()).analyze(uri)
            Log.d(TAG, "OCR raw length=${result.rawText.length} name=${result.name.value} company=${result.company.value} job=${result.jobTitle.value}")
            if (result.rawText.isBlank()) {
                flowViewModel.setError("Couldn't read this card clearly. Try again or enter details manually.")
                Toast.makeText(requireContext(), "Couldn't read this card clearly", Toast.LENGTH_SHORT).show()
                false
            } else {
                flowViewModel.setOcrResult(result)
                true
            }
        }.getOrElse {
            Log.e(TAG, "OCR failed", it)
            flowViewModel.setError("OCR failed. Please retake the card photo.")
            Toast.makeText(requireContext(), "OCR failed", Toast.LENGTH_SHORT).show()
            false
        }
    }

    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView unbinding camera")
        cameraProvider?.unbindAll()
        super.onDestroyView()
    }

    companion object {
        private const val ARG_START_IN_GALLERY = "arg_start_in_gallery"
        private const val ARG_CAPTURE_SIDE = "arg_capture_side"
        private const val TAG = "CardScannerCamera"

        fun newInstance(startInGallery: Boolean = false, captureSide: CaptureSide = CaptureSide.FRONT) = CaptureFragment().apply {
            arguments = Bundle().apply {
                putBoolean(ARG_START_IN_GALLERY, startInGallery)
                putString(ARG_CAPTURE_SIDE, captureSide.name)
            }
        }
    }
}
