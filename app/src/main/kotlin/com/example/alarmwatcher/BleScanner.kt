package com.example.alarmwatcher

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import androidx.annotation.WorkerThread
import kotlinx.coroutines.delay

/**
 * Scanne les appareils BLE à proximité afin d'éviter de devoir recopier une adresse MAC depuis
 * une autre application : l'utilisateur choisit l'ampoule dans la liste, puis
 * [ZenggeBulbController.verifyBulbCharacteristic] confirme silencieusement la compatibilité.
 */
internal object BleScanner {
    private const val SCAN_DURATION_MS = 6_000L

    data class Found(
        val macAddress: String,
        val name: String?,
        val rssi: Int,
    )

    @SuppressLint("MissingPermission")
    @WorkerThread
    suspend fun scan(
        context: Context,
        durationMs: Long = SCAN_DURATION_MS,
    ): List<Found> {
        val scanner = resolveScanner(context) ?: return emptyList()

        val found = LinkedHashMap<String, Found>()
        val callback =
            object : ScanCallback() {
                override fun onScanResult(
                    callbackType: Int,
                    result: ScanResult,
                ) {
                    val device = result.device
                    found[device.address] =
                        Found(macAddress = device.address, name = device.name, rssi = result.rssi)
                }
            }

        scanner.startScan(callback)
        try {
            delay(durationMs)
        } finally {
            runCatching { scanner.stopScan(callback) }
        }
        return found.values.sortedByDescending { it.rssi }
    }

    private fun resolveScanner(context: Context): android.bluetooth.le.BluetoothLeScanner? {
        if (!BlePermissionSupport.hasBluetoothScanPermission(context)) return null
        return context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
    }
}
