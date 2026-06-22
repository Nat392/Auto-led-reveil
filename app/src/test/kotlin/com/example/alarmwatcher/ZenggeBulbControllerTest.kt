package com.example.alarmwatcher

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ZenggeBulbControllerTest {
    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any<Throwable>()) } returns 0
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `buildPowerPacket computes the checksum for power on and power off`() {
        val powerOnPacket = ZenggeBulbController.buildPowerPacket(true)
        val powerOffPacket = ZenggeBulbController.buildPowerPacket(false)

        assertArrayEquals(
            byteArrayOf(
                0x00, 0x01, 0x80.toByte(), 0x00, 0x00, 0x0D, 0x0E, 0x0B,
                0x3B, 0x23, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x5E,
            ),
            powerOnPacket,
        )

        assertArrayEquals(
            byteArrayOf(
                0x00, 0x01, 0x80.toByte(), 0x00, 0x00, 0x0D, 0x0E, 0x0B,
                0x3B, 0x24, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x5F,
            ),
            powerOffPacket,
        )
    }

    @Test
    fun `buildScenePacket clamps rgb channels before checksum calculation`() {
        val scenePacket =
            ZenggeBulbController.buildScenePacket(
                red = 300,
                green = -20,
                blue = 128,
                white = 999,
            )

        assertArrayEquals(
            byteArrayOf(
                0x00, 0x01, 0x80.toByte(), 0x00, 0x00, 0x08, 0x09, 0x0B,
                0x31, 0xFF.toByte(), 0x00, 0x80.toByte(), 0x00, 0x00, 0x0F, 0xBF.toByte(),
            ),
            scenePacket,
        )
    }

    @Test
    fun `scaleScene clamps rgbw outputs and applies brightnessPercent`() {
        val scene =
            ZenggeBulbController.scaleScene(
                red = 300,
                green = -20,
                blue = 128,
                white = 999,
                brightnessPercent = 71,
            )

        assertEquals(181, scene.red)
        assertEquals(0, scene.green)
        assertEquals(91, scene.blue)
        assertEquals(181, scene.white)
    }

    @Test
    fun `applyScene - processus complet de connexion BLE et déconnexion après succès (Happy Path)`() =
        runTest {
            val context = mockk<Context>(relaxed = true)
            val bluetoothManager = mockk<BluetoothManager>()
            val bluetoothAdapter = mockk<BluetoothAdapter>()
            val bluetoothDevice = mockk<BluetoothDevice>(relaxed = true)
            val bluetoothGatt = mockk<BluetoothGatt>(relaxed = true)

            every { context.getSystemService(BluetoothManager::class.java) } returns bluetoothManager
            every { bluetoothManager.adapter } returns bluetoothAdapter
            every { bluetoothAdapter.getRemoteDevice(any<String>()) } returns bluetoothDevice

            val callbackSlot = slot<BluetoothGattCallback>()

            every { bluetoothDevice.connectGatt(any(), any(), capture(callbackSlot), any()) } answers {
                val cb = callbackSlot.captured
                cb.onConnectionStateChange(bluetoothGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
                bluetoothGatt
            }
            every { bluetoothDevice.connectGatt(any(), any(), capture(callbackSlot)) } answers {
                val cb = callbackSlot.captured
                cb.onConnectionStateChange(bluetoothGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
                bluetoothGatt
            }

            every { bluetoothGatt.discoverServices() } answers {
                callbackSlot.captured.onServicesDiscovered(bluetoothGatt, BluetoothGatt.GATT_SUCCESS)
                true
            }

            val service = mockk<BluetoothGattService>(relaxed = true)
            val characteristic = mockk<BluetoothGattCharacteristic>(relaxed = true)
            every { bluetoothGatt.services } returns listOf(service)
            every { service.getCharacteristic(any()) } returns characteristic
            every { characteristic.properties } returns BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
            every { characteristic.writeType } returns BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

            every { bluetoothGatt.writeCharacteristic(any(), any(), any()) } answers {
                callbackSlot.captured.onCharacteristicWrite(bluetoothGatt, characteristic, BluetoothGatt.GATT_SUCCESS)
                BluetoothGatt.GATT_SUCCESS
            }
            every { bluetoothGatt.writeCharacteristic(any()) } answers {
                callbackSlot.captured.onCharacteristicWrite(bluetoothGatt, characteristic, BluetoothGatt.GATT_SUCCESS)
                true
            }

            val result =
                ZenggeBulbController.applyScene(
                    context = context,
                    macAddress = "AA:BB:CC:DD:EE:FF",
                    red = 255,
                    green = 120,
                    blue = 0,
                    white = 0,
                    brightnessPercent = 100,
                )

            assertTrue(result, "La session doit réussir et résoudre sans bloquer la coroutine")
            verify(exactly = 1) { bluetoothGatt.discoverServices() }
            verify(exactly = 1) { bluetoothGatt.disconnect() }
            verify(exactly = 1) { bluetoothGatt.close() }
        }

    @Test
    fun `verifyBulbCharacteristic - confirme la compatibilite sans jamais ecrire de commande`() =
        runTest {
            val context = mockk<Context>(relaxed = true)
            val bluetoothManager = mockk<BluetoothManager>()
            val bluetoothAdapter = mockk<BluetoothAdapter>()
            val bluetoothDevice = mockk<BluetoothDevice>(relaxed = true)
            val bluetoothGatt = mockk<BluetoothGatt>(relaxed = true)

            every { context.getSystemService(BluetoothManager::class.java) } returns bluetoothManager
            every { bluetoothManager.adapter } returns bluetoothAdapter
            every { bluetoothAdapter.getRemoteDevice(any<String>()) } returns bluetoothDevice

            val callbackSlot = slot<BluetoothGattCallback>()

            every { bluetoothDevice.connectGatt(any(), any(), capture(callbackSlot), any()) } answers {
                val cb = callbackSlot.captured
                cb.onConnectionStateChange(bluetoothGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
                bluetoothGatt
            }
            every { bluetoothDevice.connectGatt(any(), any(), capture(callbackSlot)) } answers {
                val cb = callbackSlot.captured
                cb.onConnectionStateChange(bluetoothGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
                bluetoothGatt
            }

            every { bluetoothGatt.discoverServices() } answers {
                callbackSlot.captured.onServicesDiscovered(bluetoothGatt, BluetoothGatt.GATT_SUCCESS)
                true
            }

            val service = mockk<BluetoothGattService>(relaxed = true)
            val characteristic = mockk<BluetoothGattCharacteristic>(relaxed = true)
            every { bluetoothGatt.services } returns listOf(service)
            every { service.getCharacteristic(any()) } returns characteristic
            every { characteristic.properties } returns BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE

            val result = ZenggeBulbController.verifyBulbCharacteristic(context, "AA:BB:CC:DD:EE:FF")

            assertTrue(result, "La caractéristique RGBW doit être trouvée pour une ampoule compatible")
            verify(exactly = 1) { bluetoothGatt.discoverServices() }
            verify(exactly = 0) { bluetoothGatt.writeCharacteristic(any(), any(), any()) }
            verify(exactly = 0) { bluetoothGatt.writeCharacteristic(any()) }
            verify(exactly = 1) { bluetoothGatt.disconnect() }
            verify(exactly = 1) { bluetoothGatt.close() }
        }

    @Test
    fun `openSession - expire et interrompt proprement en cas de timeout Gatt Hardware`() =
        runTest {
            val context = mockk<Context>(relaxed = true)
            val bluetoothManager = mockk<BluetoothManager>()
            val bluetoothAdapter = mockk<BluetoothAdapter>()
            val bluetoothDevice = mockk<BluetoothDevice>(relaxed = true)
            val bluetoothGatt = mockk<BluetoothGatt>(relaxed = true)

            every { context.getSystemService(BluetoothManager::class.java) } returns bluetoothManager
            every { bluetoothManager.adapter } returns bluetoothAdapter
            every { bluetoothAdapter.getRemoteDevice("AA:BB:CC:DD:EE:FF") } returns bluetoothDevice

            every { bluetoothDevice.connectGatt(any(), any(), any(), any()) } returns bluetoothGatt
            every { bluetoothDevice.connectGatt(any(), any(), any()) } returns bluetoothGatt

            val session = ZenggeBulbController.openSession(context, "AA:BB:CC:DD:EE:FF")

            assertNull(session, "La session aurait dû crasher via la contrainte du 'withTimeoutOrNull'")
            verify(exactly = 1) { bluetoothGatt.disconnect() }
            verify(exactly = 1) { bluetoothGatt.close() }
        }

    @Test
    fun `openSession - retourne null sans side-effects pour une MAC Address absente ou invalide`() =
        runTest {
            val context = mockk<Context>(relaxed = true)
            val bluetoothManager = mockk<BluetoothManager>()
            val bluetoothAdapter = mockk<BluetoothAdapter>()

            every { context.getSystemService(BluetoothManager::class.java) } returns bluetoothManager
            every { bluetoothManager.adapter } returns bluetoothAdapter
            every { bluetoothAdapter.getRemoteDevice(any<String>()) } throws IllegalArgumentException("Invalid format")

            val sessionInvalid = ZenggeBulbController.openSession(context, "NOT_A_MAC")
            val sessionBlank = ZenggeBulbController.openSession(context, "   ")

            assertNull(sessionInvalid)
            assertNull(sessionBlank)
            verify(exactly = 0) { bluetoothAdapter.getRemoteDevice("   ") }
        }

    @Test
    fun `applyScene - sécurise et neutralise une SecurityException remontee par Android`() =
        runTest {
            val context = mockk<Context>(relaxed = true)
            val bluetoothManager = mockk<BluetoothManager>()
            val bluetoothAdapter = mockk<BluetoothAdapter>()

            every { context.getSystemService(BluetoothManager::class.java) } returns bluetoothManager
            every { bluetoothManager.adapter } returns bluetoothAdapter
            every {
                bluetoothAdapter.getRemoteDevice(any<String>())
            } throws SecurityException("Missing BLUETOOTH_CONNECT")

            val result =
                ZenggeBulbController.applyScene(
                    context,
                    "AA:BB:CC:DD:EE:FF",
                    255,
                    128,
                    0,
                    0,
                    100,
                )

            assertFalse(result, "Une exception non gérée devrait être avalée intelligemment en retournant false.")
        }
}
