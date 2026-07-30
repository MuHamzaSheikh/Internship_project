package com.example.businesscardscanner

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.activity.viewModels
import com.example.businesscardscanner.viewmodel.CaptureMode
import com.example.businesscardscanner.viewmodel.CardFlowViewModel

class CardWorkflowActivity : AppCompatActivity(),
    QrScanFragment.FlowHost,
    AfterQrScanFragment.FlowHost,
    CaptureFragment.FlowHost,
    CaptureDoneFragment.FlowHost,
    CardEditFragment.FlowHost {

    private val flowViewModel: CardFlowViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card_workflow)

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
        showStep(AfterQrScanFragment.newInstance())
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
        showStep(CaptureDoneFragment.newInstance())
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
        flowViewModel.setCaptureSide(com.example.businesscardscanner.viewmodel.CaptureSide.FRONT)
        showStep(CardEditFragment.newInstance(intent.getStringExtra(EXTRA_CARD_ID)))
    }

    override fun onCaptureDoneNext() {
        showStep(CardEditFragment.newInstance(intent.getStringExtra(EXTRA_CARD_ID)))
    }

    // ==========================
    // Card Edit Fragment Callback
    // ==========================

    override fun onFinishFlow() {
        finish()
    }

    // ==========================
    // Navigation Helpers
    // ==========================

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
        CAPTURE_DONE,
        CARD_EDIT
    }

    companion object {

        private const val EXTRA_START_STEP = "extra_start_step"
        private const val EXTRA_CARD_ID = "extra_card_id"

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
