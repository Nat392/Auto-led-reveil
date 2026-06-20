package com.example.alarmwatcher.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun DashboardScreen(
    alarmStatusText: String,
    bleStatusText: String,
    onTriggerNightMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentModifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)

    Column(modifier = contentModifier) {
        Text(
            text = "Alarm Watcher",
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineMedium,
        )

        Button(
            onClick = onTriggerNightMode,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
            Text("Déclencher le Mode Soirée (Ambre/Rouge)")
        }

        StatusCard(
            title = "Synchronisation Android",
            titleColor = MaterialTheme.colorScheme.primary,
            body = alarmStatusText,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        StatusCard(
            title = "Modules LED (Zengge BLE)",
            titleColor = MaterialTheme.colorScheme.secondary,
            body = bleStatusText,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun StatusCard(
    title: String,
    titleColor: Color,
    body: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                color = titleColor,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Text(
                text = body,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
