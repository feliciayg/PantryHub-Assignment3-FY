package com.example.pantryhub_assignment3_fy.espresso

import android.Manifest
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Grants the Android 13+ notification permission before ActivityScenario launches the app.
 *
 * This prevents the system permission dialog from stealing focus during Espresso navigation tests.
 */
class NotificationPermissionRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                grantNotificationPermissionIfNeeded()
                base.evaluate()
            }
        }
    }

    private fun grantNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        instrumentation.uiAutomation
            .executeShellCommand("pm grant $packageName ${Manifest.permission.POST_NOTIFICATIONS}")
            .close()
    }
}
