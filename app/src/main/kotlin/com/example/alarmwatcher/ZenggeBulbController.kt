package com.example.alarmwatcher

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object ZenggeBulbController {
    private const val TAG = "ZenggeBulbController"
    private const val CONNECT_TIMEOUT_MS = 12_000L
    private const val OP_TIMEOUT_MS = 5_000L

    private val UUID_STATE: UUID = UUID.fromString("0000ffe4-0000-1000-8000-00805f9b34fb")
    private val UUID_RED: UUID = UUID.fromString("0000ffe6-0000-1000-8000-00805f9b34fb")
    private val UUID_GREEN: UUID = UUID.fromString("0000ffe7-0000-1000-8000-00805f9b34fb")
    private val UUID_BLUE: UUID = UUID.fromString("0000ffe8-0000-1000-8000-00805f9b34fb")
    private val UUID_RGBW: UUID = UUID.fromString("0000ffe9-0000-1000-8000-00805f9b34fb")
    private val UUID_WHITE: UUID = UUID.fromString("0000ffea-0000-1000-8000-00805f9b34fb")

    fun applyScene(
        context: Context,
        macAddress: String,
        red: Int,
        green: Int,
        blue: Int,
        white: Int,
        brightnessPercent: Int
    ): Boolean {
        val adapter = getBluetoothAdapter(context) ?: run {
            Log.w(TAG, "Bluetooth adapter unavailable")
            return false
        }

        val normalizedMac = macAddress.trim()
        if (normalizedMac.isBlank()) {
            Log.w(TAG, "No bulb MAC configured")
            return false
        }

        val device = runCatching { adapter.getRemoteDevice(normalizedMac) }.getOrNull() ?: run {
            Log.w(TAG, "Invalid bulb MAC: $normalizedMac")
            return false
        }

        val callback = SessionCallback()
        val gatt = connect(device, context, callback)
        if (gatt == null) {
            DiscordCrashReporter.reportDebugBlocking(
                context = context,
                source = "ZenggeBulbController.applyScene",
                details = buildString {
                    appendLine("connect returned null for $normalizedMac")
                    appendLine("connectionStatus=${callback.connectionStatus}")
                    appendLine("connectionState=${callback.connectionState}")
                }
            )
            return false
        }

        return try {
            if (!discoverServices(gatt, callback)) {
                Log.w(TAG, "Service discovery failed")
                DiscordCrashReporter.reportDebugBlocking(
                    context = context,
                    source = "ZenggeBulbController.discoverServices",
                    details = buildString {
                        appendLine("Service discovery failed for $normalizedMac")
                        appendLine("servicesStatus=${callback.servicesStatus}")
                    }
                )
                return false
            }

            val scaled = scaleScene(red, green, blue, white, brightnessPercent)
            val success = if (scaled.white > 0) {
                powerOn(gatt, callback) &&
                    writeByte(gatt, callback, UUID_RED, byteArrayOf(scaled.red.toByte())) &&
                    writeByte(gatt, callback, UUID_GREEN, byteArrayOf(scaled.green.toByte())) &&
                    writeByte(gatt, callback, UUID_BLUE, byteArrayOf(scaled.blue.toByte())) &&
                    writeByte(gatt, callback, UUID_WHITE, byteArrayOf(scaled.white.toByte()))
            } else {
                powerOn(gatt, callback) && writeRgbPacket(gatt, callback, scaled.red, scaled.green, scaled.blue, scaled.white)
            }

            Log.i(TAG, "Applied scene to $normalizedMac success=$success red=${scaled.red} green=${scaled.green} blue=${scaled.blue} white=${scaled.white} brightness=$brightnessPercent")
            if (!success) {
                DiscordCrashReporter.reportDebugBlocking(
                    context = context,
                    source = "ZenggeBulbController.applyScene.failure",
                    details = buildString {
                        appendLine("Applied scene failure for $normalizedMac")
                        appendLine("red=${scaled.red} green=${scaled.green} blue=${scaled.blue} white=${scaled.white} brightness=$brightnessPercent")
                        appendLine("connectionStatus=${callback.connectionStatus}")
                        appendLine("connectionState=${callback.connectionState}")
                        appendLine("servicesStatus=${callback.servicesStatus}")
                        appendLine("lastWriteStatus=${callback.lastWriteStatus}")
                    }
                )
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply scene", e)
            DiscordCrashReporter.reportDebugBlocking(
                context = context,
                source = "ZenggeBulbController.applyScene.exception",
                details = buildString {
                    appendLine("Exception while applying scene to $normalizedMac")
                    appendLine("error=${e::class.java.name}")
                    appendLine("message=${e.message}")
                }
            )
            false
        } finally {
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
    }

    fun powerOff(context: Context, macAddress: String): Boolean {
        val adapter = getBluetoothAdapter(context) ?: return false
        val device = runCatching { adapter.getRemoteDevice(macAddress.trim()) }.getOrNull() ?: return false
        val callback = SessionCallback()
        val gatt = connect(device, context, callback) ?: return false
        return try {
            if (!discoverServices(gatt, callback)) return false
            val ok = writeRgbPacket(gatt, callback, 0, 0, 0, 0, powerOff = true)
            Log.i(TAG, "Power off result=$ok for $macAddress")
            ok
        } finally {
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
    }

    private fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return null
        return manager.adapter
    }

    private fun connect(
        device: BluetoothDevice,
        context: Context,
        callback: SessionCallback
    ): BluetoothGatt? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Missing BLUETOOTH_CONNECT permission")
                    return null
                }
            } catch (_: Exception) {
                return null
            }
        }

        val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } else {
            @Suppress("DEPRECATION")
            device.connectGatt(context, false, callback)
        }

        if (gatt == null) {
            Log.w(TAG, "connectGatt returned null")
            return null
        }

        if (!callback.connectionLatch.await(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            Log.w(TAG, "Timed out connecting to bulb")
            return null
        }

        if (callback.connectionState != BluetoothProfile.STATE_CONNECTED) {
            Log.w(TAG, "Bulb connection failed status=${callback.connectionStatus} state=${callback.connectionState}")
            return null
        }

        return gatt
    }

    private fun discoverServices(gatt: BluetoothGatt, callback: SessionCallback): Boolean {
        callback.resetServicesLatch()
        if (!gatt.discoverServices()) {
            return false
        }
        if (!callback.servicesLatch.await(OP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            return false
        }
        return callback.servicesStatus == BluetoothGatt.GATT_SUCCESS
    }

    private fun powerOn(gatt: BluetoothGatt, callback: SessionCallback): Boolean {
        return writeRgbPacket(gatt, callback, 0, 0, 0, 0, powerOn = true)
    }

    private fun writeRgbPacket(
        gatt: BluetoothGatt,
        callback: SessionCallback,
        red: Int,
        green: Int,
        blue: Int,
        white: Int,
        powerOn: Boolean = false,
        powerOff: Boolean = false
    ): Boolean {
        val characteristic = gatt.findCharacteristic(UUID_RGBW) ?: return false
        val payload = when {
            powerOff -> byteArrayOf(0xcc.toByte(), 0x24, 0x33)
            powerOn -> byteArrayOf(0xcc.toByte(), 0x23, 0x33)
            else -> byteArrayOf(
                0x56,
                red.toByte(),
                green.toByte(),
                blue.toByte(),
                white.toByte(),
                0x0f,
                0xaa.toByte()
            )
        }
        return writeCharacteristic(gatt, callback, characteristic, payload)
    }

    private fun writeByte(
        gatt: BluetoothGatt,
        callback: SessionCallback,
        uuid: UUID,
        payload: ByteArray
    ): Boolean {
        val characteristic = gatt.findCharacteristic(uuid) ?: return false
        return writeCharacteristic(gatt, callback, characteristic, payload)
    }

    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        callback: SessionCallback,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray
    ): Boolean {
        val originalWriteType = characteristic.writeType
        try {
            // First try: write with response (default)
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            characteristic.value = payload
            callback.resetWriteLatch()
            val started = runCatching { gatt.writeCharacteristic(characteristic) }.getOrDefault(false)
            if (!started) return false
            if (!callback.writeLatch.await(OP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) return false
            if (callback.lastWriteStatus == BluetoothGatt.GATT_SUCCESS) return true

            // Fallback: some bulbs expect write without response. Retry with NO_RESPONSE.
            Log.w(TAG, "Write with response failed (status=${callback.lastWriteStatus}), retrying with NO_RESPONSE")

            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            callback.resetWriteLatch()
            val started2 = runCatching { gatt.writeCharacteristic(characteristic) }.getOrDefault(false)
            if (!started2) return false
            // WRITE_TYPE_NO_RESPONSE typically does not invoke onCharacteristicWrite.
            return true
        } finally {
            // restore original
            characteristic.writeType = originalWriteType
        }
    }

    private fun BluetoothGatt.findCharacteristic(uuid: UUID): BluetoothGattCharacteristic? {
        services.forEach { service ->
            service.getCharacteristic(uuid)?.let { return it }
        }
        return null
    }

    private fun scaleScene(red: Int, green: Int, blue: Int, white: Int, brightnessPercent: Int): Scene {
        val clampedBrightness = brightnessPercent.coerceIn(0, 100)
        val scale = clampedBrightness / 100.0
        return Scene(
            red = (red.coerceIn(0, 255) * scale).toInt().coerceIn(0, 255),
            green = (green.coerceIn(0, 255) * scale).toInt().coerceIn(0, 255),
            blue = (blue.coerceIn(0, 255) * scale).toInt().coerceIn(0, 255),
            white = (white.coerceIn(0, 255) * scale).toInt().coerceIn(0, 255)
        )
    }

    private data class Scene(
        val red: Int,
        val green: Int,
        val blue: Int,
        val white: Int
    )

    private class SessionCallback : BluetoothGattCallback() {
        val connectionLatch = CountDownLatch(1)
        @Volatile var connectionState: Int = BluetoothProfile.STATE_DISCONNECTED
        @Volatile var connectionStatus: Int = BluetoothGatt.GATT_FAILURE
        @Volatile var servicesLatch = CountDownLatch(1)
        @Volatile var servicesStatus: Int = BluetoothGatt.GATT_FAILURE
        @Volatile var writeLatch = CountDownLatch(1)
        @Volatile var lastWriteStatus: Int = BluetoothGatt.GATT_FAILURE

        fun resetServicesLatch() {
            servicesLatch = CountDownLatch(1)
            servicesStatus = BluetoothGatt.GATT_FAILURE
        }

        fun resetWriteLatch() {
            writeLatch = CountDownLatch(1)
            lastWriteStatus = BluetoothGatt.GATT_FAILURE
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            connectionStatus = status
            connectionState = newState
            connectionLatch.countDown()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            servicesStatus = status
            servicesLatch.countDown()
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            lastWriteStatus = status
            writeLatch.countDown()
        }
    }
}