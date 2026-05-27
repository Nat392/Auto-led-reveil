package com.example.alarmwatcher

import android.Manifest
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    // Tentative d'accorder les permissions critiques au démarrage du test.
    // Si l'environnement CI refuse, on continue silencieusement.

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        // L'?mulateur ne supportant pas de vrai mat?riel BLE,
        // on mock le ZenggeBulbController pour ?viter des crashes li?s au bluetooth
        mockkObject(ZenggeBulbController)
        coEvery { ZenggeBulbController.applyScene(any(), any(), any(), any(), any(), any(), any()) } returns true

        // Grant runtime permissions best-effort for CI emulators
        try {
            val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val pkg = InstrumentationRegistry.getInstrumentation().targetContext.packageName
            try {
                uiAutomation.grantRuntimePermission(pkg, Manifest.permission.BLUETOOTH_CONNECT)
            } catch (_: Throwable) {
            }
            try {
                uiAutomation.grantRuntimePermission(pkg, Manifest.permission.BLUETOOTH_SCAN)
            } catch (_: Throwable) {
            }
            try {
                uiAutomation.grantRuntimePermission(pkg, Manifest.permission.POST_NOTIFICATIONS)
            } catch (_: Throwable) {
            }
        } catch (_: Throwable) {
            // ignore: some test environments may not allow programmatic grants
        }
    }

    @After
    fun tearDown() {
        try {
            unmockkObject(ZenggeBulbController)
        } catch (_: Throwable) {
            // avoid failing if MockK runtime classes are missing
        }
    }

    @Test
    fun testMainActivityLaunchesAndDisplaysStatusText() {
        // V?rifie simplement que l'activity se lance sans crasher
        onView(withId(R.id.tvAlarmStatus)).check(matches(isDisplayed()))
        onView(withId(R.id.tvBleStatus)).check(matches(isDisplayed()))
    }
}
