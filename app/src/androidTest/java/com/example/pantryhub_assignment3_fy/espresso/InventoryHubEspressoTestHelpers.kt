package com.example.pantryhub_assignment3_fy.espresso

import android.view.View
import androidx.annotation.IdRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.PerformException
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pantryhub_assignment3_fy.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.hamcrest.Matcher
import org.junit.Assert.fail

/**
 * Shared Espresso helpers for the tab test suite.
 *
 * These helpers intentionally keep the tests assignment-friendly by focusing on
 * visible UI/navigation behaviour instead of live Firebase record content.
 */
object InventoryHubEspressoTestHelpers {

    /**
     * These UI tests assume the emulator is already authenticated and has already entered a store.
     * The production app starts at Firebase login, so the main tabs are only reachable after that.
     */
    fun ensureLoggedInShellReady() {
        if (isVisible(withId(R.id.bottomNavigation))) return
        if (isVisible(withId(R.id.loginButton))) {
            loginFromInstrumentationArgs()
        }
        waitForCondition(
            description = "main app shell after authentication",
            timeoutMs = 30000L
        ) {
            isVisible(withId(R.id.bottomNavigation)) ||
                isVisible(withId(R.id.createStoreButton)) ||
                isVisible(withId(R.id.joinStoreButton)) ||
                isVisible(withId(R.id.loginButton))
        }
        if (isVisible(withId(R.id.createStoreButton)) || isVisible(withId(R.id.joinStoreButton))) {
            fail(
                "Login worked, but this test account is on the store setup screen. " +
                    "Use a Firebase test account that has already created or joined a store."
            )
        }
        if (isVisible(withId(R.id.loginButton))) {
            waitForCondition(
                description = "main app shell after retrying login",
                timeoutMs = 30000L
            ) { isVisible(withId(R.id.bottomNavigation)) }
        }
        if (!isVisible(withId(R.id.bottomNavigation))) {
            fail(
                "Main tab shell was not available. If the login screen appears during Espresso, " +
                    "run with instrumentation args for a real Firebase test account, for example: " +
                    "-Pandroid.testInstrumentationRunnerArguments.testEmail=your@email.com " +
                    "-Pandroid.testInstrumentationRunnerArguments.testPassword=yourPassword"
            )
        }
    }

    /**
     * Opens one of the five main bottom navigation tabs by its visible title.
     */
    fun openTab(tabTitle: String) {
        onView(withText(tabTitle)).perform(click())
    }

    /**
     * Uses the actual bottom navigation item id so tests do not depend on ambiguous visible text.
     */
    fun openMainTab(@IdRes menuItemId: Int) {
        onView(withId(R.id.bottomNavigation)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isDisplayed()

            override fun getDescription(): String = "Select bottom navigation item $menuItemId"

            override fun perform(uiController: androidx.test.espresso.UiController, view: View) {
                (view as BottomNavigationView).selectedItemId = menuItemId
                uiController.loopMainThreadUntilIdle()
            }
        })
        waitForCondition(
            description = "bottom navigation item $menuItemId to become selected",
            timeoutMs = 3000L
        ) { isBottomNavItemSelected(menuItemId) }
        onView(isRoot()).perform(waitForUi(400))
    }

    /**
     * Reusable assertion for stable visible text checks.
     */
    fun assertTextVisible(text: String) {
        onView(withText(text)).check(matches(isDisplayed()))
    }

    /**
     * Scrolls inside ScrollView-based pages only when needed to reach lower content.
     */
    fun scrollToText(text: String) {
        onView(withText(text)).perform(scrollTo())
    }

    /**
     * Enters text safely into a field without relying on existing contents.
     */
    fun replaceFieldText(@IdRes viewId: Int, value: String) {
        try {
            onView(withId(viewId)).perform(scrollTo(), replaceText(value), closeSoftKeyboard())
        } catch (_: RuntimeException) {
            onView(withId(viewId)).perform(replaceText(value), closeSoftKeyboard())
        }
    }

    /**
     * Signs in through the real login UI when the app opens on Login instead of the saved shell.
     */
    fun loginFromInstrumentationArgs() {
        val args = InstrumentationRegistry.getArguments()
        val email = args.getString("testEmail").orEmpty()
        val password = args.getString("testPassword").orEmpty()
        if (email.isBlank() || password.isBlank()) {
            fail(
                "Login screen is shown, but no test credentials were provided. " +
                    "Pass -Pandroid.testInstrumentationRunnerArguments.testEmail and " +
                    "-Pandroid.testInstrumentationRunnerArguments.testPassword."
            )
        }
        replaceFieldText(R.id.emailEditText, email)
        replaceFieldText(R.id.passwordEditText, password)
        onView(withId(R.id.loginButton)).perform(scrollTo(), click())
        // Firebase login can take a few seconds on the emulator, so the caller waits for navigation.
        onView(isRoot()).perform(waitForUi(500))
    }

    /**
     * Some toolbar/buttons are optional by destination, so this helper clicks them only if shown.
     */
    fun clickIfVisible(matcher: Matcher<View>) {
        try {
            onView(matcher).perform(click())
        } catch (_: NoMatchingViewException) {
            // Intentionally ignored so tests can adapt to current optional UI.
        } catch (_: PerformException) {
            // Intentionally ignored for non-visible optional controls.
        }
    }

    /**
     * Returns a lightweight wait action for RecyclerView / async shell rendering without Thread.sleep.
     */
    fun waitForUi(delayMs: Long = 250L): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isDisplayed()

        override fun getDescription(): String = "Wait for $delayMs milliseconds."

        override fun perform(uiController: androidx.test.espresso.UiController, view: View) {
            uiController.loopMainThreadForAtLeast(delayMs)
        }
    }

    /**
     * Makes it easier to assert the app package if we ever need a smoke sanity check.
     */
    fun targetPackageName(): String =
        InstrumentationRegistry.getInstrumentation().targetContext.packageName

    private fun isVisible(matcher: Matcher<View>): Boolean {
        return try {
            onView(matcher).check(matches(isDisplayed()))
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun isBottomNavItemSelected(@IdRes menuItemId: Int): Boolean {
        return try {
            onView(withId(R.id.bottomNavigation)).check { view, _ ->
                check((view as BottomNavigationView).selectedItemId == menuItemId)
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun waitForCondition(
        description: String,
        timeoutMs: Long,
        predicate: () -> Boolean
    ) {
        val startTime = System.currentTimeMillis()
        while (!predicate()) {
            if (System.currentTimeMillis() - startTime >= timeoutMs) {
                fail("Timed out waiting for $description.")
            }
            onView(isRoot()).perform(waitForUi(300))
        }
    }
}
