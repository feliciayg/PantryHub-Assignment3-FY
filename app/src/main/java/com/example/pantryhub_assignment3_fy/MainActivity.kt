package com.example.pantryhub_assignment3_fy

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.NavController
import androidx.navigation.ui.setupWithNavController
import com.example.pantryhub_assignment3_fy.databinding.ActivityMainBinding
import com.example.pantryhub_assignment3_fy.notification.InventoryReminderScheduler
import com.example.pantryhub_assignment3_fy.ui.common.ToolbarActionHost
import com.example.pantryhub_assignment3_fy.ui.shell.ShellViewModel
import com.example.pantryhub_assignment3_fy.ui.storage.InventoryItemDetailFragment
import com.example.pantryhub_assignment3_fy.util.AppPreferences
import com.google.android.material.snackbar.Snackbar

/**
 * Hosts the whole InventoryHub app.
 *
 * This activity owns the shared navigation shell: toolbar, bottom navigation,
 * system-bar spacing, theme setup, and notification deep links. Individual fragments
 * stay focused on their own page content.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private val shellViewModel: ShellViewModel by viewModels()
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Snackbar.make(binding.root, R.string.notification_permission_needed, Snackbar.LENGTH_SHORT).show()
        }
    }

    /**
     * Builds the shared app frame and connects Navigation Component to the toolbar,
     * bottom tabs, shell ViewModel, and notification entry point.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        AppPreferences.applySavedTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarAppearance()

        navController = (supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment).navController
        InventoryReminderScheduler.ensureNotificationChannel(this)
        requestNotificationPermissionIfNeeded()
        val mainDestinations = setOf(
            R.id.homeFragment,
            R.id.inventoryFragment,
            R.id.restockOrdersFragment,
            R.id.stockMovementsFragment,
            R.id.settingsFragment
        )
        val sharedHeaderSubDestinations = emptySet<Int>()
        val baseBottomNavigationHeight = resources.getDimensionPixelSize(R.dimen.bottom_navigation_height)
        var systemBarInsets = Insets.NONE

        fun applySystemBarInsets(showSharedAppBar: Boolean, showBottomNavigation: Boolean) {
            // Main pages and shared-header subpages use the shared app bar. Detail/auth pages receive
            // system-bar spacing directly on the NavHost so their own toolbars can handle layout.
            binding.appBarLayout.updatePadding(top = if (showSharedAppBar) systemBarInsets.top else 0)
            binding.bottomNavigation.updatePadding(bottom = if (showBottomNavigation) systemBarInsets.bottom else 0)
            binding.bottomNavigation.updateLayoutParams {
                height = baseBottomNavigationHeight + if (showBottomNavigation) systemBarInsets.bottom else 0
            }
            binding.navHostFragment.updatePadding(
                top = if (showSharedAppBar) 0 else systemBarInsets.top,
                bottom = if (showBottomNavigation) 0 else systemBarInsets.bottom
            )
        }

        val customHeaderDestinations = emptySet<Int>()

        fun updateSystemBarInsetsForCurrentDestination() {
            val destinationId = navController.currentDestination?.id
            val usesCustomHeader = destinationId in customHeaderDestinations
            applySystemBarInsets(
                showSharedAppBar = (destinationId in mainDestinations || destinationId in sharedHeaderSubDestinations) && !usesCustomHeader,
                showBottomNavigation = destinationId in mainDestinations
            )
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContent) { _, insets ->
            systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            updateSystemBarInsetsForCurrentDestination()
            WindowInsetsCompat.CONSUMED
        }

        binding.bottomNavigation.setupWithNavController(navController)
        binding.profileToolbarButton.setOnClickListener {
            if (navController.currentDestination?.id == R.id.settingsFragment) {
                navController.navigate(R.id.action_settingsFragment_to_profileFragment)
            }
        }
        binding.transactionsFilterButton.setOnClickListener {
            when (navController.currentDestination?.id) {
                R.id.inventoryFragment -> {
                    currentToolbarActionHost()?.onToolbarActionClick(binding.transactionsFilterButton)
                }
                R.id.stockMovementsFragment -> {
                    navController.navigate(R.id.action_stockMovementsFragment_to_transactionFilterFragment)
                }
                R.id.restockOrdersFragment -> {
                    currentToolbarActionHost()?.onToolbarActionClick(binding.transactionsFilterButton)
                }
            }
        }
        binding.archiveToolbarButton.setOnClickListener {
            if (navController.currentDestination?.id == R.id.inventoryFragment ||
                navController.currentDestination?.id == R.id.restockOrdersFragment
            ) {
                currentToolbarActionHost()?.onSecondaryToolbarActionClick(binding.archiveToolbarButton)
            }
        }
        binding.helpToolbarButton.setOnClickListener {
            if (navController.currentDestination?.id == R.id.homeFragment) {
                navController.navigate(R.id.action_homeFragment_to_helpFragment)
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isMainDestination = destination.id in mainDestinations
            val isSharedHeaderSubDestination = destination.id in sharedHeaderSubDestinations
            val usesCustomHeader = destination.id in customHeaderDestinations
            val showSharedAppBar = (isMainDestination || isSharedHeaderSubDestination) && !usesCustomHeader
            binding.appBarLayout.isVisible = showSharedAppBar
            binding.bottomNavigation.isVisible = isMainDestination
            binding.topAppBar.title = destination.label ?: getString(R.string.app_name)
            binding.transactionsFilterButton.isVisible =
                destination.id == R.id.inventoryFragment ||
                destination.id == R.id.stockMovementsFragment ||
                destination.id == R.id.restockOrdersFragment
            binding.archiveToolbarButton.isVisible =
                destination.id == R.id.inventoryFragment ||
                    destination.id == R.id.restockOrdersFragment
            binding.helpToolbarButton.isVisible = destination.id == R.id.homeFragment
            when (destination.id) {
                R.id.inventoryFragment -> {
                    binding.transactionsFilterButton.setImageResource(R.drawable.ic_more_vertical)
                    binding.transactionsFilterButton.contentDescription = getString(R.string.more_options)
                    binding.archiveToolbarButton.contentDescription = getString(R.string.view_archived_items)
                    binding.transactionsFilterButton.background = null
                    binding.transactionsFilterButton.setColorFilter(
                        ContextCompat.getColor(this, R.color.inventory_text_primary)
                    )
                    binding.transactionsFilterButton.isSelected = false
                    binding.archiveToolbarButton.background = null
                    binding.archiveToolbarButton.setColorFilter(
                        ContextCompat.getColor(this, R.color.inventory_text_primary)
                    )
                }
                R.id.restockOrdersFragment -> {
                    binding.transactionsFilterButton.setImageResource(R.drawable.ic_filter)
                    binding.transactionsFilterButton.contentDescription = getString(R.string.filters)
                    binding.archiveToolbarButton.contentDescription = getString(R.string.view_archived_purchases)
                    binding.transactionsFilterButton.setBackgroundResource(R.drawable.bg_icon_circle)
                    binding.archiveToolbarButton.background = null
                    binding.archiveToolbarButton.setColorFilter(
                        ContextCompat.getColor(this, R.color.inventory_text_primary)
                    )
                }
                R.id.stockMovementsFragment -> {
                    binding.transactionsFilterButton.setImageResource(R.drawable.ic_filter)
                    binding.transactionsFilterButton.contentDescription = getString(R.string.filter_transactions)
                    binding.transactionsFilterButton.setBackgroundResource(R.drawable.bg_icon_circle)
                }
            }
            binding.profileToolbarButton.isVisible = destination.id == R.id.settingsFragment
            if (isMainDestination) {
                binding.topAppBar.navigationIcon = null
                binding.topAppBar.setNavigationOnClickListener(null)
            } else if (isSharedHeaderSubDestination) {
                binding.topAppBar.setNavigationIcon(R.drawable.ic_back)
                binding.topAppBar.setNavigationOnClickListener {
                    navController.navigateUp()
                }
            }
            applySystemBarInsets(showSharedAppBar, isMainDestination)
            if (isMainDestination) shellViewModel.load()
        }
        handleInventoryReminderIntent(intent)

        shellViewModel.uiState.observe(this) { state ->
            state.details?.let { details ->
                val displayName = details.currentUser.displayName.ifBlank { details.currentUser.email }
                binding.profileToolbarInitialTextView.text = displayName.firstOrNull()?.uppercase() ?: "U"
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!navController.popBackStack()) {
                    finish()
                }
            }
        })
    }

    /**
     * Handles notification taps while the app is already open.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleInventoryReminderIntent(intent)
    }

    /**
     * Android 13+ requires runtime notification permission before reminders can appear.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (!AppPreferences.expiryRemindersEnabled(this)) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun applySystemBarAppearance() {
        // The theme setting can switch the app between light and dark mode, so system-bar icon
        // colors need to follow the active mode instead of staying fixed in XML.
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkMode
            isAppearanceLightNavigationBars = !isDarkMode
        }
    }

    /**
     * Reads the inventoryItem id from a reminder notification and navigates to its detail screen.
     */
    private fun handleInventoryReminderIntent(intent: Intent?) {
        val inventoryItemId = intent?.getStringExtra(InventoryReminderScheduler.EXTRA_INVENTORY_ITEM_ID).orEmpty()
        if (inventoryItemId.isBlank()) return

        // Notification taps jump directly to the inventoryItem detail screen. The detail screen
        // then reads the latest inventoryItem data from the current store through InventoryViewModel.
        navController.navigate(
            R.id.inventoryItemDetailFragment,
            Bundle().apply {
                putString(InventoryItemDetailFragment.ARG_INVENTORY_ITEM_ID, inventoryItemId)
            }
        )
        intent?.removeExtra(InventoryReminderScheduler.EXTRA_INVENTORY_ITEM_ID)
    }

    private fun currentToolbarActionHost(): ToolbarActionHost? {
        val navHost = supportFragmentManager.findFragmentById(R.id.navHostFragment) as? NavHostFragment
        return navHost?.childFragmentManager?.primaryNavigationFragment as? ToolbarActionHost
    }

}
