package com.example.alarmwatcher.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.alarmwatcher.ui.LogExportStatus
import com.example.alarmwatcher.ui.LogExportViewModel

private const val LOG_EXPORT_HELP =
    "Pour des raisons de sécurité, Android limite l'accès aux journaux : seules les lignes " +
        "générées par cette application elle-même peuvent être récupérées, pas celles du " +
        "système ou des autres applications. Aucune autorisation particulière n'est requise."

@Composable
fun LogsScreen(
    modifier: Modifier = Modifier,
    viewModel: LogExportViewModel = viewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(status) {
        when (status) {
            LogExportStatus.SUCCESS -> {
                snackbarHostState.showSnackbar("Journaux envoyés sur Discord.")
                viewModel.acknowledgeResult()
            }
            LogExportStatus.FAILURE -> {
                snackbarHostState.showSnackbar("Échec de l'envoi des journaux. Vérifiez la connexion.")
                viewModel.acknowledgeResult()
            }
            else -> Unit
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Journaux",
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineMedium,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Export des journaux de l'application vers Discord",
                    style = MaterialTheme.typography.bodyMedium,
                )
                HelpDialogButton(explanation = LOG_EXPORT_HELP)
            }

            Button(
                onClick = { viewModel.exportLogs() },
                enabled = status != LogExportStatus.EXPORTING,
                modifier = Modifier.padding(top = 24.dp),
            ) {
                if (status == LogExportStatus.EXPORTING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Exporter les logs vers Discord")
                }
            }
        }
    }
}
