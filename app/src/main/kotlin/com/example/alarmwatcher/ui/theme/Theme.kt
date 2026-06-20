package com.example.alarmwatcher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AlarmWatcherDarkColorScheme =
    darkColorScheme(
        primary = AlarmWatcherPrimary,
        secondary = AlarmWatcherSecondary,
        error = AlarmWatcherError,
        background = AlarmWatcherBackground,
        surface = AlarmWatcherSurface,
        onBackground = AlarmWatcherOnBackground,
        onSurface = AlarmWatcherOnSurface,
    )

private val AlarmWatcherLightColorScheme =
    lightColorScheme(
        primary = AlarmWatcherPrimary,
        secondary = AlarmWatcherSecondary,
        error = AlarmWatcherError,
    )

@Composable
fun AlarmWatcherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) AlarmWatcherDarkColorScheme else AlarmWatcherLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
