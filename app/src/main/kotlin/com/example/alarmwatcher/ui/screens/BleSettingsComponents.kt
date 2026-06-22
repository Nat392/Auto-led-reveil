package com.example.alarmwatcher.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.alarmwatcher.BlePermissionSupport
import com.example.alarmwatcher.BleScanner
import com.example.alarmwatcher.ZenggeBulbController
import kotlinx.coroutines.launch

private enum class BleTestState { IDLE, TESTING, SUCCESS, FAILURE }

/**
 * Champ de saisie d'une adresse MAC d'ampoule BLE, avec un bouton "Vérifier" qui effectue un test
 * silencieux de compatibilité ([ZenggeBulbController.verifyBulbCharacteristic], sans écrire de
 * commande, donc sans changer la couleur ou l'état de l'ampoule).
 */
@Composable
fun MacAddressField(
    label: SettingLabel,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value) }
    var state by remember { mutableStateOf(BleTestState.IDLE) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        SettingHeaderRow(label = label)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    text = input
                    state = BleTestState.IDLE
                    onValueChange(input)
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                enabled = text.isNotBlank() && state != BleTestState.TESTING,
                onClick = {
                    state = BleTestState.TESTING
                    scope.launch {
                        val ok = ZenggeBulbController.verifyBulbCharacteristic(context, text)
                        state = if (ok) BleTestState.SUCCESS else BleTestState.FAILURE
                    }
                },
            ) {
                Text(bleTestButtonLabel(state, default = "Vérifier"))
            }
        }
    }
}

private fun bleTestButtonLabel(
    state: BleTestState,
    default: String,
): String =
    when (state) {
        BleTestState.IDLE -> default
        BleTestState.TESTING -> "Test…"
        BleTestState.SUCCESS -> "✅ Détectée"
        BleTestState.FAILURE -> "❌ Réessayer"
    }

/**
 * Boîte de dialogue de scan BLE : liste les appareils à proximité, permet de tester
 * silencieusement leur compatibilité, puis d'assigner l'adresse MAC retenue à une zone.
 */
@Composable
fun BleScanDialog(
    onDismiss: () -> Unit,
    onAssign: (zoneLabel: String, macAddress: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasPermission by remember { mutableStateOf(BlePermissionSupport.hasBluetoothScanPermission(context)) }
    var devices by remember { mutableStateOf<List<BleScanner.Found>?>(null) }
    val testStates = remember { mutableStateMapOf<String, BleTestState>() }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasPermission = granted
        }

    LaunchedEffect(hasPermission) {
        if (hasPermission && devices == null) {
            devices = BleScanner.scan(context)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        title = { Text("Ampoules BLE à proximité") },
        text = {
            BleScanDialogBody(
                uiState = BleScanUiState(hasPermission, devices, testStates),
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.BLUETOOTH_SCAN) },
                onTest = { device ->
                    testStates[device.macAddress] = BleTestState.TESTING
                    scope.launch {
                        val ok = ZenggeBulbController.verifyBulbCharacteristic(context, device.macAddress)
                        testStates[device.macAddress] = if (ok) BleTestState.SUCCESS else BleTestState.FAILURE
                    }
                },
                onAssign = onAssign,
            )
        },
    )
}

private data class BleScanUiState(
    val hasPermission: Boolean,
    val devices: List<BleScanner.Found>?,
    val testStates: Map<String, BleTestState>,
)

@Composable
private fun BleScanDialogBody(
    uiState: BleScanUiState,
    onRequestPermission: () -> Unit,
    onTest: (BleScanner.Found) -> Unit,
    onAssign: (zoneLabel: String, macAddress: String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val (hasPermission, devices, testStates) = uiState
        when {
            !hasPermission -> {
                Text(
                    "La détection nécessite l'autorisation de rechercher les appareils Bluetooth à proximité.",
                )
                TextButton(onClick = onRequestPermission) { Text("Autoriser") }
            }
            devices == null -> Text("Recherche en cours…")
            devices.isEmpty() -> Text("Aucun appareil BLE détecté à proximité.")
            else -> {
                devices.forEach { device ->
                    BleScanResultRow(
                        device = device,
                        state = testStates[device.macAddress] ?: BleTestState.IDLE,
                        onTest = { onTest(device) },
                        onAssign = { zoneLabel -> onAssign(zoneLabel, device.macAddress) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BleScanResultRow(
    device: BleScanner.Found,
    state: BleTestState,
    onTest: () -> Unit,
    onAssign: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(text = device.name ?: "(appareil sans nom)", style = MaterialTheme.typography.bodyMedium)
        Text(text = "${device.macAddress} · RSSI ${device.rssi}", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onTest, enabled = state != BleTestState.TESTING) {
            Text(bleTestButtonLabel(state, default = "Tester"))
        }
        if (state == BleTestState.SUCCESS) {
            Row {
                listOf("Bureau", "Chambre", "Cuisine").forEach { zoneLabel ->
                    TextButton(onClick = { onAssign(zoneLabel) }) { Text(zoneLabel) }
                }
            }
        }
    }
}
