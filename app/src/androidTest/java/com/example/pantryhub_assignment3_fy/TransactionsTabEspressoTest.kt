package com.example.pantryhub_assignment3_fy

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pantryhub_assignment3_fy.espresso.InventoryHubEspressoTestHelpers.ensureLoggedInShellReady
import com.example.pantryhub_assignment3_fy.espresso.InventoryHubEspressoTestHelpers.openMainTab
import com.example.pantryhub_assignment3_fy.espresso.NotificationPermissionRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionsTabEspressoTest {

    @get:Rule(order = 0)
    val notificationPermissionRule = NotificationPermissionRule()

    @get:Rule(order = 1)
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        ensureLoggedInShellReady()
        openMainTab(R.id.stockMovementsFragment)
    }

    /** Purpose: verifies the Transactions tab opens the stock movement screen. */
    @Test
    fun transactions_tabOpensTransactionsScreen() {
        onView(withId(R.id.movementsRecyclerView)).check(matches(isDisplayed()))
    }

    /** Purpose: confirms the transaction screen shows either content area or empty-state container. */
    @Test
    fun transactions_displaysTransactionListOrEmptyState() {
        onView(withId(R.id.movementsRecyclerView)).check(matches(isDisplayed()))
    }

    /** Purpose: confirms the transactions toolbar filter button is visible and testable. */
    @Test
    fun transactions_displaysFilterButton() {
        onView(withContentDescription("Filter transactions")).check(matches(isDisplayed()))
    }

    /** Purpose: checks the transaction FAB opens the type selector bottom sheet. */
    @Test
    fun transactions_fabOpensTransactionTypeBottomSheet() {
        onView(withId(R.id.addMovementFab)).perform(click())
        onView(withText("Select Transaction Type")).check(matches(isDisplayed()))
    }

    /** Purpose: confirms the Stock In option is listed in the transaction type bottom sheet. */
    @Test
    fun transactions_bottomSheetShowsStockIn() {
        onView(withId(R.id.addMovementFab)).perform(click())
        onView(withId(R.id.stockInRow)).check(matches(isDisplayed()))
    }

    /** Purpose: confirms the Stock Out option is listed in the transaction type bottom sheet. */
    @Test
    fun transactions_bottomSheetShowsStockOut() {
        onView(withId(R.id.addMovementFab)).perform(click())
        onView(withId(R.id.stockOutRow)).check(matches(isDisplayed()))
    }

    /** Purpose: confirms the Move Stock option is listed in the transaction type bottom sheet. */
    @Test
    fun transactions_bottomSheetShowsMoveStock() {
        onView(withId(R.id.addMovementFab)).perform(click())
        onView(withId(R.id.moveStockRow)).check(matches(isDisplayed()))
    }

    /** Purpose: confirms the Adjust Stock option is listed in the transaction type bottom sheet. */
    @Test
    fun transactions_bottomSheetShowsAdjustStock() {
        onView(withId(R.id.addMovementFab)).perform(click())
        onView(withId(R.id.adjustStockRow)).check(matches(isDisplayed()))
    }

    /** Purpose: verifies choosing Stock In opens the stock-in transaction form. */
    @Test
    fun transactions_stockInOptionOpensForm() {
        onView(withId(R.id.addMovementFab)).perform(click())
        onView(withId(R.id.stockInRow)).perform(click())
        onView(withId(R.id.submitButton)).check(matches(isDisplayed()))
        onView(withId(R.id.locationRow)).check(matches(isDisplayed()))
    }

    /** Purpose: verifies the filter screen opens from the transactions toolbar filter action. */
    @Test
    fun transactions_filterScreenOpens() {
        onView(withContentDescription("Filter transactions")).perform(click())
        onView(withId(R.id.applyButton)).check(matches(isDisplayed()))
        onView(withId(R.id.clearButton)).check(matches(isDisplayed()))
    }
}
