package com.example.alarmwatcher

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN
    )

    @Test
    fun useAppContext() {
        // Obtenir le contexte de l'application sous test
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Vérifier que le packageName correspond bien, 
        // avec ".debug" car applicationIdSuffix est utilisé en Debug
        assertEquals("com.example.alarmwatcher.debug", appContext.packageName)
    }
}
