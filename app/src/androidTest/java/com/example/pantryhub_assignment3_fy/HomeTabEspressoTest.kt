package com.example.pantryhub_assignment3_fy

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pantryhub_assignment3_fy.espresso.InventoryHubEspressoTestHelpers.ensureLoggedInShellReady
import com.example.pantryhub_assignment3_fy.espresso.InventoryHubEspressoTestHelpers.assertTextVisible
import com.example.pantryhub_assignment3_fy.espresso.InventoryHubEspressoTestHelpers.openMainTab
import com.example.pantryhub_assignment3_fy.espresso.InventoryHubEspressoTestHelpers.waitForUi
import com.example.pantryhub_assignment3_fy.espresso.NotificationPermissionRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matchers.allOf

@RunWith(AndroidJUnit4::class)
class HomeTabEspressoTest {

    @get:Rule(order = 0)
    val notificationPermissionRule = NotificationPermissionRule()

    @get:Rule(order = 1)
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        ensureLoggedInShellReady()
        openMainTab(R.id.homeFragment)
        onView(withId(R.id.bottomNavigation)).perform(waitForUi(400))
        onView(withId(R.id.searchLayout)).perform(waitForUi(250))
    }

    /** Purpose: verifies the main dashboard shell is reachable after launch for logged-in users. */
    @Test
    fun home_displaysDashboardAfterLaunch() {
        onView(withId(R.id.contentScrollView)).check(matches(isDisplayed()))
    }

    /** Purpose: confirms the first overview carousel contains the Today Summary card content. */
    @Test
    fun home_displaysTodaySummaryCard() {
        assertTextVisible("Today Summary")
    }

    /** Purpose: confirms the Home tab keeps the global item search entry visible. */
    @Test
    fun home_displaysSearchBar() {
        onView(withId(R.id.searchLayout)).check(matches(isDisplayed()))
    }

    /** Purpose: ensures the Add Item shortcut is present for quick item creation from Home. */
    @Test
    fun home_displaysAddItemShortcut() {
        onView(homeShortcut("Add Item")).check(matches(isDisplayed()))
    }

    /** Purpose: ensures the Stock In shortcut is visible from the Home quick actions. */
    @Test
    fun home_displaysStockInShortcut() {
        onView(homeShortcut("Stock In")).check(matches(isDisplayed()))
    }

    /** Purpose: ensures the Stock Out shortcut is visible from the Home quick actions. */
    @Test
    fun home_displaysStockOutShortcut() {
        onView(homeShortcut("Stock Out")).check(matches(isDisplayed()))
    }

    /** Purpose: ensures the Move Stock shortcut is visible from the Home quick actions. */
    @Test
    fun home_displaysMoveStockShortcut() {
        onView(homeShortcut("Move Stock")).check(matches(isDisplayed()))
    }

    /** Purpose: ensures the Adjust Stock shortcut is visible from the Home quick actions. */
    @Test
    fun home_displaysAdjustStockShortcut() {
        onView(homeShortcut("Adjust Stock")).check(matches(isDisplayed()))
    }

    /** Purpose: confirms the alerts section is still rendered in the Home overview. */
    @Test
    fun home_displaysAlertsSection() {
        onView(withText("Alerts")).perform(scrollTo())
        assertTextVisible("Alerts")
    }

    /** Purpose: checks that a Home shortcut actually navigates to its expected form screen. */
    @Test
    fun home_shortcutNavigatesToCorrectScreenOrSheet() {
        onView(homeShortcut("Add Item")).perform(click())
        onView(withId(R.id.formHeader)).perform(waitForUi())
        assertTextVisible("New Item")
    }

    private fun homeShortcut(label: String) = allOf(
        withContentDescription(label),
        isClickable()
    )
}
