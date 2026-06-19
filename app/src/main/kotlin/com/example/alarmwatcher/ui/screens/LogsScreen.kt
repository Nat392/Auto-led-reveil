package com.example.alarmwatcher.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.alarmwatcher.AppErrorLogStore
import com.example.alarmwatcher.ui.CatalogSendStatus
import com.example.alarmwatcher.ui.ErrorCatalogViewModel
import com.example.alarmwatcher.ui.LogExportStatus
import com.example.alarmwatcher.ui.LogExportViewModel
import java.text.DateFormat
import java.util.Date

private const val LOG_EXPORT_HELP =
    "Pour des raisons de sécurité, Android limite l'accès aux journaux : seules les lignes " +
        "générées par cette application elle-même peuvent être récupérées, pas celles du " +
        "système ou des autres applications. Aucune autorisation particulière n'est requise."

private const val CATALOG_HELP =
    "Le catalogue conserve les erreurs et avertissements de l'application même après que le " +
        "journal Android (logcat) ait été vidé. Sélectionnez une ou plusieurs entrées pour les " +
        "renvoyer manuellement sur Discord."

@Suppress("MagicNumber")
private val WarningLabelColor = Color(0xFFE67E22)

private data class LogsScreenState(
    val status: LogExportStatus,
    val sendStatus: CatalogSendStatus,
    val entries: List<AppErrorLogStore.Entry>,
    val selectedIds: Set<String>,
)

private data class LogsScreenActions(
    val onExportClick: () -> Unit,
    val onEntryClick: (AppErrorLogStore.Entry) -> Unit,
    val onToggleSelected: (String) -> Unit,
    val onSendSelected: () -> Unit,
    val onClearCatalog: () -> Unit,
)

@Composable
fun LogsScreen(
    modifier: Modifier = Modifier,
    viewModel: LogExportViewModel = viewModel(),
    catalogViewModel: ErrorCatalogViewModel = viewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val entries by catalogViewModel.entries.collectAsStateWithLifecycle()
    val selectedIds by catalogViewModel.selectedIds.collectAsStateWithLifecycle()
    val sendStatus by catalogViewModel.sendStatus.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var detailEntry by remember { mutableStateOf<AppErrorLogStore.Entry?>(null) }

    LaunchedEffect(Unit) {
        catalogViewModel.refresh()
    }

    LogExportStatusEffect(status, viewModel, snackbarHostState)
    CatalogSendStatusEffect(sendStatus, catalogViewModel, snackbarHostState)

    detailEntry?.let { entry ->
        ErrorDetailDialog(entry = entry, onDismiss = { detailEntry = null })
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val actions =
            LogsScreenActions(
                onExportClick = { viewModel.exportLogs() },
                onEntryClick = { detailEntry = it },
                onToggleSelected = { catalogViewModel.toggleSelection(it) },
                onSendSelected = { catalogViewModel.sendSelected() },
                onClearCatalog = { catalogViewModel.clearCatalog() },
            )
        LogsScreenBody(
            innerPadding = innerPadding,
            state = LogsScreenState(status, sendStatus, entries, selectedIds),
            actions = actions,
        )
    }
}

@Composable
private fun LogExportStatusEffect(
    status: LogExportStatus,
    viewModel: LogExportViewModel,
    snackbarHostState: SnackbarHostState,
) {
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
}

@Composable
private fun CatalogSendStatusEffect(
    sendStatus: CatalogSendStatus,
    catalogViewModel: ErrorCatalogViewModel,
    snackbarHostState: SnackbarHostState,
) {
    LaunchedEffect(sendStatus) {
        when (sendStatus) {
            CatalogSendStatus.SUCCESS -> {
                snackbarHostState.showSnackbar("Entrées sélectionnées envoyées sur Discord.")
                catalogViewModel.acknowledgeSendResult()
            }
            CatalogSendStatus.FAILURE -> {
                snackbarHostState.showSnackbar("Échec de l'envoi de la sélection. Vérifiez la connexion.")
                catalogViewModel.acknowledgeSendResult()
            }
            else -> Unit
        }
    }
}

@Composable
private fun LogsScreenBody(
    innerPadding: PaddingValues,
    state: LogsScreenState,
    actions: LogsScreenActions,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LogsScreenHeader(status = state.status, onExportClick = actions.onExportClick)
        LogsScreenCatalogSection(state = state, actions = actions)
    }
}

@Composable
private fun LogsScreenHeader(
    status: LogExportStatus,
    onExportClick: () -> Unit,
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
        onClick = onExportClick,
        enabled = status != LogExportStatus.EXPORTING,
        modifier = Modifier.padding(top = 24.dp, bottom = 24.dp),
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

    HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))
}

@Composable
private fun ColumnScope.LogsScreenCatalogSection(
    state: LogsScreenState,
    actions: LogsScreenActions,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Catalogue d'erreurs (${state.entries.size})",
            style = MaterialTheme.typography.titleMedium,
        )
        HelpDialogButton(explanation = CATALOG_HELP)
    }

    if (state.entries.isEmpty()) {
        Text(
            text = "Aucune erreur ni avertissement enregistré.",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
    ) {
        items(state.entries, key = { it.id }) { entry ->
            ErrorCatalogRow(
                entry = entry,
                selected = entry.id in state.selectedIds,
                onToggleSelected = { actions.onToggleSelected(entry.id) },
                onClick = { actions.onEntryClick(entry) },
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = actions.onSendSelected,
            enabled = state.selectedIds.isNotEmpty() && state.sendStatus != CatalogSendStatus.SENDING,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.sendStatus == CatalogSendStatus.SENDING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Envoyer la sélection sur Discord")
            }
        }

        OutlinedButton(
            onClick = actions.onClearCatalog,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Vider le catalogue")
        }
    }
}

@Composable
private fun ErrorCatalogRow(
    entry: AppErrorLogStore.Entry,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onClick: () -> Unit,
) {
    val isError = entry.level == AppErrorLogStore.Level.ERROR
    val labelColor = if (isError) MaterialTheme.colorScheme.error else WarningLabelColor

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = selected, onCheckedChange = { onToggleSelected() })
            AssistChip(
                onClick = onClick,
                label = { Text(if (isError) "ERREUR" else "AVERTISSEMENT") },
                colors = AssistChipDefaults.assistChipColors(labelColor = labelColor),
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(text = entry.title, style = MaterialTheme.typography.bodyLarge)
                val sentSuffix = if (entry.sentToDiscord) " — envoyé" else ""
                Text(
                    text = "${entry.source} — ${formatTime(entry.timestampMs)}$sentSuffix",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ErrorDetailDialog(
    entry: AppErrorLogStore.Entry,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.title) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            ) {
                Text("Source : ${entry.source}")
                Text("Date : ${formatTime(entry.timestampMs)}")
                if (entry.message.isNotBlank()) {
                    Text(modifier = Modifier.padding(top = 8.dp), text = entry.message)
                }
                if (!entry.stacktrace.isNullOrBlank()) {
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = entry.stacktrace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Fermer") }
        },
    )
}

private fun formatTime(timestampMs: Long): String {
    val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
    return formatter.format(Date(timestampMs))
}
