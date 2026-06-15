package com.example.pantryhub_assignment3_fy

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pantryhub_assignment3_fy.espresso.InventoryHubEspressoTestHelpers.ensureLoggedInShellReady
import com.example.pantryhub_assignment3_fy.espresso.InventoryHubEspressoTestHelpers.openMainTab
import com.example.pantryhub_assignment3_fy.espresso.InventoryHubEspressoTestHelpers.waitForUi
import com.example.pantryhub_assignment3_fy.espresso.NotificationPermissionRule
import com.google.android.material.tabs.TabLayout
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PurchasesTabEspressoTest {

    @get:Rule(order = 0)
    val notificationPermissionRule = NotificationPermissionRule()

    @get:Rule(order = 1)
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        ensureLoggedInShellReady()
        openMainTab(R.id.restockOrdersFragment)
        onView(withId(R.id.bottomNavigation)).perform(waitForUi(400))
        onView(withId(R.id.statusTabLayout)).perform(waitForUi(250))
    }

    /** Purpose: verifies the Purchases tab opens the purchase orders screen. */
    @Test
    fun purchases_tabOpensPurchasesScreen() {
        onView(withId(R.id.purchaseRecyclerView)).check(matches(isDisplayed()))
    }

    /** Purpose: confirms the purchase status tabs are visible for order filtering. */
    @Test
    fun purchases_displaysPurchaseTabs() {
        onView(withId(R.id.statusTabLayout)).check(matches(isDisplayed()))
    }

    /** Purpose: confirms the All purchase status tab is present. */
    @Test
    fun purchases_displaysAllTab() {
        assertPurchaseStatusTabExists("All")
    }

    /** Purpose: confirms the Draft purchase status tab is present. */
    @Test
    fun purchases_displaysDraftTab() {
        assertPurchaseStatusTabExists("Draft")
    }

    /** Purpose: confirms the Ordered purchase status tab is present. */
    @Test
    fun purchases_displaysOrderedTab() {
        assertPurchaseStatusTabExists("Ordered")
    }

    /** Purpose: confirms the Partially Received purchase status tab is present. */
    @Test
    fun purchases_displaysPartiallyReceivedTab() {
        assertPurchaseStatusTabExists("Partially Received")
    }

    /** Purpose: confirms the Received purchase status tab is present. */
    @Test
    fun purchases_displaysReceivedTab() {
        assertPurchaseStatusTabExists("Received")
    }

    /** Purpose: checks that the add purchase button opens the new purchase form. */
    @Test
    fun purchases_addButtonOpensNewPurchaseForm() {
        onView(withId(R.id.addPurchaseFab)).perform(click())
        onView(withId(R.id.titleTextView)).check(matches(isDisplayed()))
        onView(withId(R.id.supplierRow)).check(matches(isDisplayed()))
    }

    /** Purpose: confirms the main purchase form sections are visible for creating an order. */
    @Test
    fun purchases_newPurchaseFormShowsSupplierLocationDateItems() {
        onView(withId(R.id.addPurchaseFab)).perform(click())
        onView(withId(R.id.supplierRow)).check(matches(isDisplayed()))
        onView(withId(R.id.locationRow)).check(matches(isDisplayed()))
        onView(withId(R.id.itemActionsRow)).check(matches(isDisplayed()))
        onView(withId(R.id.orderDateRow)).perform(scrollTo()).check(matches(isDisplayed()))
    }

    /** Purpose: verifies the purchase filter UI opens from the shared toolbar filter action. */
    @Test
    fun purchases_filterButtonOpensFilterUi() {
        onView(withId(R.id.transactionsFilterButton)).perform(click())
        onView(withId(R.id.filterRowsContainer)).check(matches(isDisplayed()))
    }

    private fun assertPurchaseStatusTabExists(label: String) {
        onView(withId(R.id.statusTabLayout)).check { view, _ ->
            val tabLayout = view as TabLayout
            val tabLabels = (0 until tabLayout.tabCount)
                .mapNotNull { index -> tabLayout.getTabAt(index)?.text?.toString() }
            assertTrue(
                "Expected purchase status tab '$label' in $tabLabels",
                label in tabLabels
            )
        }
    }
}
