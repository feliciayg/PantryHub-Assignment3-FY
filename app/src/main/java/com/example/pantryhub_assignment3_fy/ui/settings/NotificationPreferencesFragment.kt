package com.example.pantryhub_assignment3_fy.ui.settings

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.data.repository.InventoryRepository
import com.example.pantryhub_assignment3_fy.data.repository.BranchRepository
import com.example.pantryhub_assignment3_fy.databinding.FragmentNotificationPreferencesBinding
import com.example.pantryhub_assignment3_fy.notification.InventoryReminderScheduler
import com.example.pantryhub_assignment3_fy.util.AppLogger
import com.example.pantryhub_assignment3_fy.util.AppPreferences
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Stores device-level notification and appearance preferences.
 */
class NotificationPreferencesFragment : Fragment() {
    private var _binding: FragmentNotificationPreferencesBinding? = null
    private val binding get() = _binding!!
    private val inventoryRepository = InventoryRepository()
    private val branchRepository = BranchRepository()
    private var syncingControls = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val binding = _binding ?: return@registerForActivityResult
        if (granted) {
            updateReminderPreference(enabled = true)
        } else {
            syncingControls = true
            binding.expiryReminderSwitch.isChecked = false
            syncingControls = false
            AppPreferences.setExpiryRemindersEnabled(requireContext(), false)
            Snackbar.make(binding.root, R.string.notification_permission_needed, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificationPreferencesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.locationRow.setOnClickListener { findNavController().navigate(R.id.notificationLocationsFragment) }
        binding.timeRow.setOnClickListener { findNavController().navigate(R.id.notificationTimeFragment) }
        renderSavedPreferences()
        setupReminderSwitch()
    }

    private fun renderSavedPreferences() {
        syncingControls = true
        binding.expiryReminderSwitch.isChecked = AppPreferences.expiryRemindersEnabled(requireContext())
        syncingControls = false
        renderNotificationRows()
    }

    override fun onResume() {
        super.onResume()
        renderNotificationRows()
    }

    private fun setupReminderSwitch() {
        binding.expiryReminderSwitch.setOnCheckedChangeListener { _, enabled ->
            if (syncingControls) return@setOnCheckedChangeListener
            if (enabled && !InventoryReminderScheduler.canPostNotifications(requireContext())) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                updateReminderPreference(enabled)
            }
        }
    }

    private fun renderNotificationRows() {
        val enabled = AppPreferences.expiryRemindersEnabled(requireContext())
        binding.locationRow.isEnabled = enabled
        binding.timeRow.isEnabled = enabled
        binding.locationRow.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.locationDivider.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.timeRow.visibility = if (enabled) View.VISIBLE else View.GONE

        lifecycleScope.launch {
            val branches = branchRepository.observeBranches().first().getOrDefault(emptyList())
            val selectedLocations = AppPreferences.notificationLocations(requireContext())
            binding.locationValueTextView.text = when {
                selectedLocations.contains(AppPreferences.LOCATION_ALL) ->
                    getString(R.string.notification_all_locations)
                selectedLocations.size == 1 ->
                    branches.firstOrNull { it.id == selectedLocations.first() }?.name
                        ?: getString(R.string.notification_location_summary_default)
                else -> getString(R.string.notification_locations_selected_count, selectedLocations.size)
            }
        }
        binding.timeValueTextView.text = AppPreferences.reminderHour(requireContext()).toReminderLabel()
    }

    private fun updateReminderPreference(enabled: Boolean) {
        AppPreferences.setExpiryRemindersEnabled(requireContext(), enabled)
        AppLogger.info(
            area = "Settings",
            event = "notification_setting_changed",
            message = "Expiry reminder preference changed.",
            "enabled" to enabled
        )
        renderNotificationRows()

        lifecycleScope.launch {
            val result = inventoryRepository.observeInventoryItems().first()
            result
                .onSuccess { inventoryItems ->
                    InventoryReminderScheduler.scheduleAll(requireContext(), inventoryItems)
                    Snackbar.make(
                        binding.root,
                        if (enabled) R.string.expiry_reminders_on else R.string.expiry_reminders_off,
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
                .onFailure {
                    Snackbar.make(binding.root, R.string.reminder_preference_saved, Snackbar.LENGTH_SHORT).show()
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
