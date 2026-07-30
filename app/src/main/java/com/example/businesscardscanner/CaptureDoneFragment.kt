package com.example.businesscardscanner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import coil.load
import com.example.businesscardscanner.viewmodel.CaptureSide
import com.example.businesscardscanner.viewmodel.CaptureMode
import com.example.businesscardscanner.viewmodel.CardFlowViewModel

class CaptureDoneFragment : Fragment() {

    private val flowViewModel: CardFlowViewModel by activityViewModels()

    interface FlowHost {
        fun onCaptureDoneNext()
        fun onCaptureBackRequested()
        fun onSkipBackCapture()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_capture_done, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val uri = flowViewModel.state.value.imageUri
        view.findViewById<ImageView>(R.id.imgCapturePreview).load(uri ?: R.drawable.card_reference) {
            crossfade(true)
        }
        view.findViewById<View>(R.id.btnDone).setOnClickListener {
            if (flowViewModel.state.value.captureSide == CaptureSide.FRONT) {
                showBackCaptureDialog()
            } else {
                (activity as? FlowHost)?.onCaptureDoneNext()
            }
        }
        view.findViewById<View>(R.id.btnScanQR).setOnClickListener {
            flowViewModel.setCaptureMode(CaptureMode.CARD)
            (activity as? CardWorkflowActivity)?.onScanCardRequested()
        }
        view.findViewById<View>(R.id.btnGallery).setOnClickListener {
            flowViewModel.setCaptureMode(CaptureMode.CARD)
            (activity as? CardWorkflowActivity)?.onGalleryRequested()
        }
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun showBackCaptureDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.back_capture_prompt_title)
            .setMessage(R.string.back_capture_prompt_message)
            .setPositiveButton(R.string.capture_back) { _, _ ->
                (activity as? FlowHost)?.onCaptureBackRequested()
            }
            .setNegativeButton(R.string.skip_back_capture) { _, _ ->
                (activity as? FlowHost)?.onSkipBackCapture()
            }
            .setCancelable(false)
            .show()
    }

    companion object {
        fun newInstance() = CaptureDoneFragment()
    }
}
