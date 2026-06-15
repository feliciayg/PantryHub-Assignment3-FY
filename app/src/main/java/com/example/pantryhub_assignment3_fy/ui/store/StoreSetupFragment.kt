package com.example.pantryhub_assignment3_fy.ui.store

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentStoreSetupBinding

class StoreSetupFragment : Fragment() {
    private var _binding: FragmentStoreSetupBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStoreSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.createStoreButton.setOnClickListener {
            findNavController().navigate(R.id.action_storeSetupFragment_to_createStoreFragment)
        }
        binding.joinStoreButton.setOnClickListener {
            findNavController().navigate(R.id.action_storeSetupFragment_to_joinStoreFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
