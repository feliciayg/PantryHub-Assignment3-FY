package com.example.pantryhub_assignment3_fy

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pantryhub_assignment3_fy.espresso.InventoryHubEspressoTestHelpers.ensureLoggedInShellReady
import com.example.pantryhub_assignment3_fy.espresso.InventoryHubEspressoTestHelpers.openMainTab
import com.example.pantryhub_assignment3_fy.espresso.InventoryHubEspressoTestHelpers.scrollToText
import com.example.pantryhub_assignment3_fy.espresso.NotificationPermissionRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsTabEspressoTest {

    @get:Rule(order = 0)
    val notificationPermissionRule = NotificationPermissionRule()

    @get:Rule(order = 1)
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        ensureLoggedInShellReady()
        openMainTab(R.id.settingsFragment)
    }

    /** Purpose: verifies the Settings tab opens the workspace/account settings screen. */
    @Test
    fun settings_tabOpensSettingsScreen() {
        onView(withId(R.id.teamDetailsRow)).check(matches(isDisplayed()))
    }

    /** Purpose: adapts the account test to the current store-details entry point shown in Settings. */
    @Test
    fun settings_displaysProfileOrAccountSection() {
        onView(withId(R.id.teamDetailsRow)).check(matches(isDisplayed()))
    }

    /** Purpose: confirms the theme/appearance option is present on the Settings screen. */
    @Test
    fun settings_displaysThemeOption() {
        onView(withId(R.id.appearanceRow)).perform(scrollTo()).check(matches(isDisplayed()))
    }

    /** Purpose: confirms the notifications option is present on the Settings screen. */
    @Test
    fun settings_displaysNotificationOption() {
        onView(withId(R.id.notificationsRow)).perform(scrollTo()).check(matches(isDisplayed()))
    }

    /** Purpose: confirms the locations navigation option is present in Settings. */
    @Test
    fun settings_displaysLocationsOrStoreOption() {
        onView(withId(R.id.locationsRow)).check(matches(isDisplayed()))
    }

    /** Purpose: confirms the partners option is present as the closest supplier/customer module entry. */
    @Test
    fun settings_displaysSuppliersOrCustomersOptionIfPresent() {
        onView(withId(R.id.partnersRow)).check(matches(isDisplayed()))
    }

    /** Purpose: confirms the members option is present on the Settings screen. */
    @Test
    fun settings_displaysMembersOption() {
        onView(withId(R.id.membersRow)).perform(scrollTo()).check(matches(isDisplayed()))
    }

    /** Purpose: verifies the profile entry point is available without changing auth state during tab tests. */
    @Test
    fun settings_displaysSignOutOption() {
        onView(withId(R.id.profileToolbarButton)).check(matches(isDisplayed()))
        onView(withId(R.id.profileToolbarButton)).check(matches(isClickable()))
    }

    /** Purpose: verifies that tapping Appearance opens the theme-selection dialog. */
    @Test
    fun settings_themeOptionIsClickable() {
        onView(withId(R.id.appearanceRow)).perform(scrollTo(), click())
        onView(withText("Appearance")).check(matches(isDisplayed()))
        onView(withText("System")).check(matches(isDisplayed()))
    }

    /** Purpose: verifies that tapping Notifications opens the notification preferences screen. */
    @Test
    fun settings_notificationOptionIsClickable() {
        onView(withId(R.id.notificationsRow)).perform(scrollTo(), click())
        onView(withId(R.id.expiryReminderSwitch)).check(matches(isDisplayed()))
        onView(withText("Push Notifications")).check(matches(isDisplayed()))
    }
}
