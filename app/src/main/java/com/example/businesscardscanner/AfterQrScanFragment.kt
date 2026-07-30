package com.example.businesscardscanner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class AfterQrScanFragment : Fragment() {

    interface FlowHost {
        fun onAfterQrScanDone()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_after_qr_scan, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.btnDone).setOnClickListener {
            (activity as? FlowHost)?.onAfterQrScanDone()
        }
        view.findViewById<View>(R.id.btnGallery).setOnClickListener {
            (activity as? CardWorkflowActivity)?.onGalleryRequested()
        }
        view.findViewById<View>(R.id.btnScanQR).setOnClickListener {
            (activity as? CardWorkflowActivity)?.onScanCardRequested()
        }
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    companion object {
        fun newInstance() = AfterQrScanFragment()
    }
}
