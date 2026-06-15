package com.example.pantryhub_assignment3_fy.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.data.repository.BranchRepository
import com.example.pantryhub_assignment3_fy.databinding.FragmentNotificationLocationsBinding
import com.example.pantryhub_assignment3_fy.util.AppPreferences
import com.google.android.material.checkbox.MaterialCheckBox
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NotificationLocationsFragment : Fragment() {
    private var _binding: FragmentNotificationLocationsBinding? = null
    private val binding get() = _binding!!
    private val branchRepository = BranchRepository()
    private val selectedLocations = linkedSetOf<String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificationLocationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        selectedLocations += AppPreferences.notificationLocations(requireContext())
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.saveButton.setOnClickListener { saveSelections() }
        renderRows()
    }

    private fun renderRows() {
        binding.locationRowsContainer.removeAllViews()
        addCheckboxRow(
            id = AppPreferences.LOCATION_ALL,
            label = getString(R.string.notification_all_locations),
            checked = selectedLocations.contains(AppPreferences.LOCATION_ALL)
        )
        lifecycleScope.launch {
            val branches = branchRepository.observeBranches().first().getOrDefault(emptyList())
            branches.forEach { branch ->
                addCheckboxRow(
                    id = branch.id,
                    label = branch.name,
                    checked = selectedLocations.contains(branch.id)
                )
            }
        }
    }

    private fun addCheckboxRow(id: String, label: String, checked: Boolean) {
        val checkBox = MaterialCheckBox(requireContext()).apply {
            text = label
            isChecked = checked
            setTextColor(resources.getColor(R.color.inventory_text_primary, null))
            setOnCheckedChangeListener { _, isChecked ->
                if (id == AppPreferences.LOCATION_ALL) {
                    if (isChecked) {
                        selectedLocations.clear()
                        selectedLocations += AppPreferences.LOCATION_ALL
                        binding.locationRowsContainer.children.filterIsInstance<MaterialCheckBox>()
                            .forEach { box -> if (box !== this) box.isChecked = false }
                    } else if (selectedLocations.isEmpty()) {
                        selectedLocations += AppPreferences.LOCATION_ALL
                        this.isChecked = true
                    }
                } else {
                    selectedLocations.remove(AppPreferences.LOCATION_ALL)
                    if (isChecked) selectedLocations += id else selectedLocations -= id
                    val allLocationsBox = binding.locationRowsContainer.children
                        .filterIsInstance<MaterialCheckBox>()
                        .firstOrNull { it.tag == AppPreferences.LOCATION_ALL }
                    allLocationsBox?.isChecked = selectedLocations.contains(AppPreferences.LOCATION_ALL)
                    if (selectedLocations.isEmpty()) {
                        selectedLocations += AppPreferences.LOCATION_ALL
                        allLocationsBox?.isChecked = true
                    }
                }
            }
            tag = id
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.page_horizontal_padding)
                marginEnd = resources.getDimensionPixelSize(R.dimen.page_horizontal_padding)
            }
            setPadding(
                resources.getDimensionPixelSize(R.dimen.space_sm),
                resources.getDimensionPixelSize(R.dimen.space_md),
                resources.getDimensionPixelSize(R.dimen.space_sm),
                resources.getDimensionPixelSize(R.dimen.space_md)
            )
        }
        binding.locationRowsContainer.addView(checkBox)
        binding.locationRowsContainer.addView(View(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, resources.getDimensionPixelSize(R.dimen.secondary_button_stroke_width))
            setBackgroundColor(resources.getColor(R.color.inventory_outline, null))
        })
    }

    private fun saveSelections() {
        AppPreferences.setNotificationLocations(requireContext(), selectedLocations)
        findNavController().navigateUp()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
