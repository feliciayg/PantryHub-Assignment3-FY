package com.example.pantryhub_assignment3_fy.ui.storage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.pantryhub_assignment3_fy.databinding.FragmentBarcodeScannerPrototypeBinding
import kotlin.random.Random

class BarcodeScannerPrototypeFragment : Fragment() {
    private var _binding: FragmentBarcodeScannerPrototypeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBarcodeScannerPrototypeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.closeButton.setOnClickListener { findNavController().popBackStack() }
        binding.useSampleBarcodeButton.setOnClickListener {
            findNavController().previousBackStackEntry
                ?.savedStateHandle
                ?.set(RESULT_BARCODE, generateSampleBarcode())
            findNavController().popBackStack()
        }
    }

    private fun generateSampleBarcode(): String {
        val body = buildString {
            repeat(10) { append(Random.nextInt(0, 10)) }
        }
        return "955$body"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_BARCODE = "barcodeScannerPrototypeResult"
    }
}
