package com.example.alarmwatcher

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BleScannerTest {
    @Test
    fun `scan returns an empty list when no bluetooth adapter is available`() =
        runTest {
            val context = mockk<Context>()
            val bluetoothManager = mockk<BluetoothManager>()
            every { context.getSystemService(BluetoothManager::class.java) } returns bluetoothManager
            every { bluetoothManager.adapter } returns null

            val result = BleScanner.scan(context, durationMs = 0L)

            assertTrue(result.isEmpty())
        }

    @Test
    fun `scan starts and stops the BLE scan and returns devices sorted by signal strength`() =
        runTest {
            val context = mockk<Context>()
            val bluetoothManager = mockk<BluetoothManager>()
            val bluetoothAdapter = mockk<BluetoothAdapter>()
            val scanner = mockk<BluetoothLeScanner>()
            val deviceA = mockk<BluetoothDevice>()
            val deviceB = mockk<BluetoothDevice>()
            val resultA = mockk<ScanResult>()
            val resultB = mockk<ScanResult>()

            every { context.getSystemService(BluetoothManager::class.java) } returns bluetoothManager
            every { bluetoothManager.adapter } returns bluetoothAdapter
            every { bluetoothAdapter.bluetoothLeScanner } returns scanner

            every { deviceA.address } returns "AA:AA:AA:AA:AA:AA"
            every { deviceA.name } returns "Bulb A"
            every { resultA.device } returns deviceA
            every { resultA.rssi } returns -80

            every { deviceB.address } returns "BB:BB:BB:BB:BB:BB"
            every { deviceB.name } returns "Bulb B"
            every { resultB.device } returns deviceB
            every { resultB.rssi } returns -40

            val callbackSlot = slot<ScanCallback>()
            every { scanner.startScan(capture(callbackSlot)) } answers {
                callbackSlot.captured.onScanResult(0, resultA)
                callbackSlot.captured.onScanResult(0, resultB)
            }
            every { scanner.stopScan(any<ScanCallback>()) } returns Unit

            val result = BleScanner.scan(context, durationMs = 0L)

            assertEquals(
                listOf(
                    BleScanner.Found("BB:BB:BB:BB:BB:BB", "Bulb B", -40),
                    BleScanner.Found("AA:AA:AA:AA:AA:AA", "Bulb A", -80),
                ),
                result,
            )
            verify(exactly = 1) { scanner.startScan(any<ScanCallback>()) }
            verify(exactly = 1) { scanner.stopScan(any<ScanCallback>()) }
        }
}
