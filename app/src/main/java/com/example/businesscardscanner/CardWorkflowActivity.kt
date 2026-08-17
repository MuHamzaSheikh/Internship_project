package com.example.businesscardscanner

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.businesscardscanner.viewmodel.CaptureMode
import com.example.businesscardscanner.viewmodel.CardFlowViewModel

class CardWorkflowActivity : AppCompatActivity(),
    QrScanFragment.FlowHost,
    AfterQrScanFragment.FlowHost,
    CaptureFragment.FlowHost,
    CaptureDoneFragment.FlowHost,
    CardEditFragment.FlowHost {

    private val flowViewModel: CardFlowViewModel by viewModels()
    private var processingDialog: androidx.appcompat.app.AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = com.example.businesscardscanner.databinding.ActivityCardWorkflowBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            flowViewModel.state.collect { state ->
                if (state.isProcessing) {
                    showProcessingDialog()
                } else {
                    hideProcessingDialog()
                }
            }
        }

        if (savedInstanceState == null) {

            when (intent.getStringExtra(EXTRA_START_STEP)
                ?: StartStep.QR_SCAN.name) {

                StartStep.CARD_EDIT.name -> {
                    showInitialStep(CardEditFragment.newInstance(intent.getStringExtra(EXTRA_CARD_ID)))
                }

                StartStep.CAPTURE_DONE.name -> {
                    showInitialStep(CaptureDoneFragment.newInstance())
                }

                StartStep.CAPTURE.name -> {
                    showInitialStep(CaptureFragment.newInstance())
                }

                StartStep.AFTER_QR.name -> {
                    showInitialStep(AfterQrScanFragment.newInstance())
                }

                else -> {
                    showInitialStep(QrScanFragment.newInstance())
                }
            }
        }
    }

    // ==========================
    // QR Scan Fragment Callbacks
    // ==========================

    override fun onGalleryRequested() {
        when (flowViewModel.state.value.captureMode) {
            CaptureMode.QR -> showStep(QrScanFragment.newInstance(startInGallery = true))
            CaptureMode.CARD -> showStep(CaptureFragment.newInstance(startInGallery = true))
        }
    }

    override fun onQrScanDone() {
        showStep(CardEditFragment.newInstance(intent.getStringExtra(EXTRA_CARD_ID)))
    }

    override fun onScanCardRequested() {
        flowViewModel.setCaptureMode(CaptureMode.CARD)
        showStep(CaptureFragment.newInstance())
    }

    // ===============================
    // After QR Scan Fragment Callback
    // ===============================

    override fun onAfterQrScanDone() {
        flowViewModel.setCaptureMode(CaptureMode.CARD)
        showStep(CaptureFragment.newInstance())
    }

    // ==========================
    // Capture Fragment Callback
    // ==========================

    override fun onScanQrRequested() {
        flowViewModel.setCaptureMode(CaptureMode.QR)
        showStep(QrScanFragment.newInstance())
    }

    override fun onCardScanDone() {
        Log.d(PERF_TAG, "capture:workflow_navigation_to_capture_done")
        showStep(CaptureDoneFragment.newInstance(needsAutoProcessing = true))
    }

    // ==============================
    // Capture Done Fragment Callback
    // ==============================

    override fun onCaptureBackRequested() {
        flowViewModel.setCaptureMode(CaptureMode.CARD)
        flowViewModel.setCaptureSide(com.example.businesscardscanner.viewmodel.CaptureSide.BACK)
        showStep(CaptureFragment.newInstance(captureSide = com.example.businesscardscanner.viewmodel.CaptureSide.BACK))
    }

    override fun onSkipBackCapture() {
        flowViewModel.setRetaking(false, false) // Reset state
        flowViewModel.setCaptureSide(com.example.businesscardscanner.viewmodel.CaptureSide.FRONT)
        triggerOcrForBothSides()
    }

    override fun onCaptureDoneNext() {
        flowViewModel.setRetaking(false, false) // Reset state
        triggerOcrForBothSides()
    }
    
    private fun triggerOcrForBothSides() {
        val state = flowViewModel.state.value
        val frontUri = state.frontImageUri ?: state.ocrUri ?: state.imageUri
        val backUri = state.backImageUri

        if (frontUri == null && backUri == null) {
            showStep(CardEditFragment.newInstance(intent.getStringExtra(EXTRA_CARD_ID)))
            return
        }

        flowViewModel.setProcessing(true)
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val appContext = applicationContext
            var frontResult: com.example.businesscardscanner.ocr.OcrResult? = null
            var backResult: com.example.businesscardscanner.ocr.OcrResult? = null

            if (frontUri != null) {
                Log.d(PERF_TAG, "extractText: starting OCR for front uri=$frontUri")
                frontResult = kotlin.runCatching {
                    com.example.businesscardscanner.ocr.TextRecognitionManager(appContext).analyze(frontUri)
                }.onFailure {
                    Log.e(PERF_TAG, "extractText: Exception caught during front OCR analysis", it)
                }.getOrNull()
            }

            if (backUri != null) {
                Log.d(PERF_TAG, "extractText: starting OCR for back uri=$backUri")
                backResult = kotlin.runCatching {
                    com.example.businesscardscanner.ocr.TextRecognitionManager(appContext).analyze(backUri)
                }.onFailure {
                    Log.e(PERF_TAG, "extractText: Exception caught during back OCR analysis", it)
                }.getOrNull()
            }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (frontResult != null && frontResult.rawText.isNotBlank()) {
                    flowViewModel.setOcrResult(frontResult, com.example.businesscardscanner.viewmodel.CaptureSide.FRONT)
                }
                if (backResult != null && backResult.rawText.isNotBlank()) {
                    flowViewModel.setOcrResult(backResult, com.example.businesscardscanner.viewmodel.CaptureSide.BACK)
                }

                if ((frontResult == null || frontResult.rawText.isBlank()) && (backResult == null || backResult.rawText.isBlank())) {
                    flowViewModel.setError("Couldn't read this card clearly.")
                }
                
                flowViewModel.setProcessing(false)
                showStep(CardEditFragment.newInstance(intent.getStringExtra(EXTRA_CARD_ID)))
            }
        }
    }

    // ==========================
    // Card Edit Fragment Callback
    // ==========================

    override fun onFinishFlow() {
        finish()
    }

    override fun onRetakeRequested(side: com.example.businesscardscanner.viewmodel.CaptureSide, retakeBoth: Boolean) {
        flowViewModel.setCaptureMode(CaptureMode.CARD)
        flowViewModel.setRetaking(isRetaking = true, bothSides = retakeBoth)
        flowViewModel.setCaptureSide(side)
        showStep(CaptureFragment.newInstance(captureSide = side))
    }

    // ==========================
    // Navigation Helpers
    // ==========================

    private fun showProcessingDialog() {
        if (processingDialog == null) {
            val view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_processing, null)
            processingDialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setView(view)
                .setCancelable(false)
                .create()
            processingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        if (processingDialog?.isShowing == false) {
            processingDialog?.show()
        }
    }

    private fun hideProcessingDialog() {
        if (processingDialog?.isShowing == true) {
            processingDialog?.dismiss()
        }
    }

    private fun showStep(fragment: Fragment) {

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.workflowContainer, fragment)
            .commit()
    }

    private fun showInitialStep(fragment: Fragment) {

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.workflowContainer, fragment)
            .commit()
    }

    // ==========================
    // Workflow Steps
    // ==========================

    enum class StartStep {
        QR_SCAN,
        AFTER_QR,
        CAPTURE,
        ADJUST_CORNERS,
        CAPTURE_DONE,
        CARD_EDIT
    }

    companion object {

        private const val EXTRA_START_STEP = "extra_start_step"
        private const val EXTRA_CARD_ID = "extra_card_id"
        private const val PERF_TAG = "CardScannerPerf"

        fun createIntent(
            context: Context,
            startStep: StartStep = StartStep.QR_SCAN,
            cardId: String? = null
        ): Intent {

            return Intent(context, CardWorkflowActivity::class.java).apply {

                putExtra(
                    EXTRA_START_STEP,
                    startStep.name
                )
                if (cardId != null) putExtra(EXTRA_CARD_ID, cardId)
            }
        }
    }
}
