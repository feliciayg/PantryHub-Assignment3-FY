package com.example.pantryhub_assignment3_fy.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentSettingsBinding
import com.example.pantryhub_assignment3_fy.util.AppLogger
import com.example.pantryhub_assignment3_fy.util.AppPreferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Hub page for workspace management links and account entry points.
 */
class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupNavigationLinks()
        renderAppearanceSummary()
    }

    private fun setupNavigationLinks() {
        binding.teamDetailsRow.setOnClickListener { findNavController().navigate(R.id.action_settingsFragment_to_storeManagementFragment) }
        binding.locationsRow.setOnClickListener { findNavController().navigate(R.id.action_settingsFragment_to_branchesFragment) }
        binding.partnersRow.setOnClickListener { findNavController().navigate(R.id.action_settingsFragment_to_suppliersFragment) }
        binding.membersRow.setOnClickListener { findNavController().navigate(R.id.action_settingsFragment_to_membersFragment) }
        binding.notificationsRow.setOnClickListener { findNavController().navigate(R.id.action_settingsFragment_to_notificationPreferencesFragment) }
        binding.appearanceRow.setOnClickListener { showAppearanceDialog() }
    }

    override fun onResume() {
        super.onResume()
        renderAppearanceSummary()
    }

    private fun renderAppearanceSummary() {
        binding.appearanceValueTextView.text = when (AppPreferences.themeMode(requireContext())) {
            AppPreferences.THEME_LIGHT -> getString(R.string.theme_light)
            AppPreferences.THEME_DARK -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_system)
        }
    }

    /**
     * Appearance only has three local device choices, so a single-choice dialog keeps it quick.
     */
    private fun showAppearanceDialog() {
        val options = arrayOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        val modes = arrayOf(
            AppPreferences.THEME_SYSTEM,
            AppPreferences.THEME_LIGHT,
            AppPreferences.THEME_DARK
        )
        val selectedIndex = modes.indexOf(AppPreferences.themeMode(requireContext())).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.appearance)
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                AppPreferences.setThemeMode(requireContext(), modes[which])
                AppLogger.info(
                    area = "Settings",
                    event = "theme_changed",
                    message = "Theme preference changed.",
                    "theme" to modes[which]
                )
                AppPreferences.applySavedTheme(requireContext())
                renderAppearanceSummary()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
