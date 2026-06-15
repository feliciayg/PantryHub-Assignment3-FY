package com.example.pantryhub_assignment3_fy

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pantryhub_assignment3_fy.espresso.InventoryHubEspressoTestHelpers.ensureLoggedInShellReady
import com.example.pantryhub_assignment3_fy.espresso.InventoryHubEspressoTestHelpers.openMainTab
import com.example.pantryhub_assignment3_fy.espresso.InventoryHubEspressoTestHelpers.replaceFieldText
import com.example.pantryhub_assignment3_fy.espresso.InventoryHubEspressoTestHelpers.waitForUi
import com.example.pantryhub_assignment3_fy.espresso.NotificationPermissionRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItemsTabEspressoTest {

    @get:Rule(order = 0)
    val notificationPermissionRule = NotificationPermissionRule()

    @get:Rule(order = 1)
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        ensureLoggedInShellReady()
        openMainTab(R.id.inventoryFragment)
        onView(withId(R.id.bottomNavigation)).perform(waitForUi(400))
        onView(withId(R.id.searchLayout)).perform(waitForUi(250))
    }

    /** Purpose: verifies the Items tab opens the inventory screen successfully. */
    @Test
    fun items_tabOpensItemsScreen() {
        onView(withId(R.id.inventoryRecyclerView)).check(matches(isDisplayed()))
    }

    /** Purpose: confirms the location header chip is visible for inventory scoping. */
    @Test
    fun items_displaysLocationHeader() {
        onView(withId(R.id.locationSelector)).check(matches(isDisplayed()))
    }

    /** Purpose: confirms the inventory search field is visible for item lookup. */
    @Test
    fun items_displaysSearchField() {
        onView(withId(R.id.searchLayout)).check(matches(isDisplayed()))
    }

    /** Purpose: checks the toolbar more-options button is visible for CSV/archive actions. */
    @Test
    fun items_displaysCsvMenuButton() {
        onView(withContentDescription("More options")).check(matches(isDisplayed()))
    }

    /** Purpose: confirms the group by control is available on the inventory screen. */
    @Test
    fun items_displaysGroupByButton() {
        onView(withId(R.id.groupButton)).check(matches(isDisplayed()))
    }

    /** Purpose: adapts the old filter test to the current in-stock chip filter UI. */
    @Test
    fun items_displaysInStockChipFilter() {
        onView(withId(R.id.inStockChip)).check(matches(isDisplayed()))
        onView(withId(R.id.inStockChip)).check(matches(isNotChecked()))
    }

    /** Purpose: confirms the sort button remains accessible on the inventory screen. */
    @Test
    fun items_displaysSortButton() {
        onView(withId(R.id.sortButton)).check(matches(isDisplayed()))
    }

    /** Purpose: checks that the add button opens the new item form screen. */
    @Test
    fun items_addButtonOpensNewItemForm() {
        onView(withId(R.id.addInventoryItemFab)).perform(click())
        onView(withId(R.id.formHeader)).check(matches(isDisplayed()))
        onView(withText("New Item")).check(matches(isDisplayed()))
    }

    /** Purpose: confirms important new-item form fields are shown for item creation. */
    @Test
    fun items_newItemFormShowsRequiredFields() {
        onView(withId(R.id.addInventoryItemFab)).perform(click())
        onView(withId(R.id.nameEditText)).check(matches(isDisplayed()))
        onView(withId(R.id.categoryRow)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.locationRow)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.quantityRow)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.unitRow)).perform(scrollTo()).check(matches(isDisplayed()))
    }

    /** Purpose: verifies the inventory search field accepts typed user input. */
    @Test
    fun items_searchFieldAcceptsText() {
        replaceFieldText(R.id.searchEditText, "Milk")
        onView(withId(R.id.searchEditText)).check(matches(withText("Milk")))
    }
}
