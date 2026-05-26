package com.example.alarmwatcher

import android.Manifest
import android.os.Build
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
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

    // On accorde les permissions critiques pour ?viter la pop-up
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.POST_NOTIFICATIONS
    )

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        // L'?mulateur ne supportant pas de vrai mat?riel BLE,
        // on mock le ZenggeBulbController pour ?viter des crashes li?s au bluetooth
        mockkObject(ZenggeBulbController)
        coEvery { ZenggeBulbController.applyScene(any(), any(), any(), any(), any(), any(), any()) } returns true
    }

    @After
    fun tearDown() {
        unmockkObject(ZenggeBulbController)
    }

    @Test
    fun testMainActivityLaunchesAndDisplaysStatusText() {
        // V?rifie simplement que l'activity se lance sans crasher
        onView(withId(R.id.tvAlarmStatus)).check(matches(isDisplayed()))
        onView(withId(R.id.tvBleStatus)).check(matches(isDisplayed()))
    }
}
