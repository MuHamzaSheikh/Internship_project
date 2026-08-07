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

    private var _binding: com.example.businesscardscanner.databinding.FragmentAfterQrScanBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = com.example.businesscardscanner.databinding.FragmentAfterQrScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        
        binding.btnSaveCard.setOnClickListener {
            (activity as? FlowHost)?.onAfterQrScanDone()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = AfterQrScanFragment()
    }
}
