@file:Suppress("DEPRECATION")

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
    private const val POWER_ON_SETTLE_MS = 800L
    private const val NO_RESPONSE_SETTLE_MS = 300L
    private const val GAMMA_EXP = 1.0

    private val UUID_RGBW_NEW: UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")
    private val UUID_RGBW_LEGACY: UUID = UUID.fromString("0000ffe9-0000-1000-8000-00805f9b34fb")

    fun openSession(context: Context, macAddress: String): BulbSession? {
        val adapter = getBluetoothAdapter(context) ?: run {
            Log.w(TAG, "Bluetooth adapter unavailable")
            return null
        }

        val normalizedMac = macAddress.trim()
        if (normalizedMac.isBlank()) {
            Log.w(TAG, "No bulb MAC configured")
            return null
        }

        val device = runCatching { adapter.getRemoteDevice(normalizedMac) }.getOrNull() ?: run {
            Log.w(TAG, "Invalid bulb MAC: $normalizedMac")
            return null
        }

        val callback = SessionCallback()
        val gatt = connect(device, context, callback) ?: return null
        if (!discoverServices(gatt, callback)) {
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
            return null
        }

        return BulbSession(gatt, callback)
    }

    fun applyScene(
        context: Context,
        macAddress: String,
        red: Int,
        green: Int,
        blue: Int,
        white: Int,
        brightnessPercent: Int
    ): Boolean {
        val session = openSession(context, macAddress) ?: return false
        return try {
            val scaled = scaleScene(red, green, blue, white, brightnessPercent)
            writeRgbPacket(
                gatt = session.gatt,
                callback = session.callback,
                red = scaled.red,
                green = scaled.green,
                blue = scaled.blue,
                white = scaled.white,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply scene", e)
            false
        } finally {
            session.close()
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
            ok
        } finally {
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
    }

    fun diagnosticApplyScene(
        context: Context,
        macAddress: String,
        red: Int,
        green: Int,
        blue: Int,
        white: Int
    ): String {
        val results = mutableListOf<String>()
        return try {
            val adapter = getBluetoothAdapter(context) ?: return "{\"error\":\"adapter_unavailable\"}"
            val device = adapter.getRemoteDevice(macAddress.trim())
            val callback = SessionCallback()
            val gatt = connect(device, context, callback) ?: return "{\"error\":\"connect_failed\"}"
            try {
                if (!discoverServices(gatt, callback)) return "{\"error\":\"discover_failed\"}"
                val characteristic = gatt.findCharacteristic() ?: return "{\"error\":\"char_not_found\"}"

                fun runAttempt(name: String, payload: ByteArray, forceType: Int? = null) {
                    val ok = writeCharacteristic(gatt, callback, characteristic, payload, forceType)
                    results.add("$name:${payload.toHexString()}:$ok:status=${callback.lastWriteStatus}")
                }

                runAttempt("power_on", buildPowerPacket(true))
                Thread.sleep(700)
                runAttempt("scene", buildScenePacket(red, green, blue, white))

                "{\"results\":[\"${results.joinToString("\",\"")}\"]}"
            } finally {
                gatt.disconnect()
                gatt.close()
            }
        } catch (e: Exception) {
            "{\"error\":\"${e.message}\"}"
        }
    }

    private fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return null
        return manager.adapter
    }

    class BulbSession internal constructor(
        internal val gatt: BluetoothGatt,
        internal val callback: SessionCallback
    ) : AutoCloseable {
        fun applyScene(red: Int, green: Int, blue: Int, white: Int): Boolean {
            return try {
                writeRgbPacket(
                    gatt = gatt,
                    callback = callback,
                    red = red,
                    green = green,
                    blue = blue,
                    white = white,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply scene", e)
                false
            }
        }

        override fun close() {
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
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

        var connected = false
        try {
            if (!callback.connectionLatch.await(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "Timed out connecting to bulb")
                return null
            }

            if (callback.connectionState != BluetoothProfile.STATE_CONNECTED) {
                Log.w(TAG, "Bulb connection failed status=${callback.connectionStatus} state=${callback.connectionState}")
                return null
            }

            connected = true
            return gatt
        } finally {
            if (!connected) {
                runCatching { gatt.disconnect() }
                runCatching { gatt.close() }
            }
        }
    }

    private fun discoverServices(gatt: BluetoothGatt, callback: SessionCallback): Boolean {
        callback.resetServicesLatch()
        if (!gatt.discoverServices()) {
            return false
        }

        if (!callback.servicesLatch.await(OP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            return false
        }

        if (callback.servicesStatus != BluetoothGatt.GATT_SUCCESS) {
            return false
        }
        return true
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
        powerOff: Boolean = false,
    ): Boolean {
        val characteristic = gatt.findCharacteristic() ?: return false
        return when {
            powerOff -> writeCharacteristic(
                gatt,
                callback,
                characteristic,
                buildPowerPacket(false),
                forceWriteType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
            powerOn -> writeCharacteristic(
                gatt,
                callback,
                characteristic,
                buildPowerPacket(true),
                forceWriteType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
            else -> tryAllSceneWrites(gatt, callback, characteristic, red, green, blue, white)
        }
    }

    private fun tryAllSceneWrites(
        gatt: BluetoothGatt,
        callback: SessionCallback,
        characteristic: BluetoothGattCharacteristic,
        red: Int,
        green: Int,
        blue: Int,
        white: Int,
    ): Boolean {
        val scene = buildScenePacket(red, green, blue, white)
        return writeCharacteristic(
            gatt = gatt,
            callback = callback,
            characteristic = characteristic,
            payload = scene,
            forceWriteType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        )
    }

    private fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }

    private fun buildPowerPacket(powerOn: Boolean): ByteArray {
        val payload = ByteArray(12)
        payload[0] = 0x3B.toByte()
        payload[1] = if (powerOn) 0x23.toByte() else 0x24.toByte()

        var sum = 0
        for (b in payload) {
            sum += (b.toInt() and 0xFF)
        }
        val checksum = (sum and 0xFF).toByte()

        val header = byteArrayOf(
            0x00.toByte(),
            0x01.toByte(),
            0x80.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x0D.toByte(),
            0x0E.toByte(),
            0x0B.toByte()
        )

        return header + payload + checksum
    }

    private fun buildScenePacket(red: Int, green: Int, blue: Int, white: Int): ByteArray {
        val payload = byteArrayOf(
            0x31.toByte(),
            red.coerceIn(0, 255).toByte(),
            green.coerceIn(0, 255).toByte(),
            blue.coerceIn(0, 255).toByte(),
            white.coerceIn(0, 255).toByte(),
            0x00.toByte(),
            0x0F.toByte()
        )
        var sum = 0
        for (b in payload) {
            sum += (b.toInt() and 0xFF)
        }
        val checksum = (sum and 0xFF).toByte()

        val header = byteArrayOf(
            0x00.toByte(),
            0x01.toByte(),
            0x80.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x08.toByte(),
            0x09.toByte(),
            0x0B.toByte()
        )

        return header + payload + checksum
    }

    @Suppress("DEPRECATION")
    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        callback: SessionCallback,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray,
        forceWriteType: Int? = null
    ): Boolean {
        val props = characteristic.properties
        val supportsWrite = (props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
        val supportsWriteNoResponse = (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
        val preferredWriteType = when {
            forceWriteType != null -> forceWriteType
            supportsWriteNoResponse -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            supportsWrite -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            else -> null
        }

        if (preferredWriteType == null) {
            callback.lastWriteStatus = BluetoothGatt.GATT_FAILURE
            return false
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = runCatching {
                gatt.writeCharacteristic(characteristic, payload, preferredWriteType)
            }.getOrDefault(BluetoothGatt.GATT_FAILURE)
            callback.lastWriteStatus = status
            if (status != BluetoothGatt.GATT_SUCCESS) {
                return false
            }
            val result = if (preferredWriteType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
                true
            } else {
                callback.writeLatch.await(OP_TIMEOUT_MS, TimeUnit.MILLISECONDS) &&
                    callback.lastWriteStatus == BluetoothGatt.GATT_SUCCESS
            }
            result
        } else {
            val originalWriteType = characteristic.writeType
            try {
                @Suppress("DEPRECATION")
                characteristic.writeType = preferredWriteType
                @Suppress("DEPRECATION")
                characteristic.value = payload
                callback.resetWriteLatch()
                @Suppress("DEPRECATION")
                val started = runCatching { gatt.writeCharacteristic(characteristic) }.getOrDefault(false)
                if (!started) {
                    return false
                }
                val result = if (preferredWriteType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
                    true
                } else {
                    callback.writeLatch.await(OP_TIMEOUT_MS, TimeUnit.MILLISECONDS) &&
                        callback.lastWriteStatus == BluetoothGatt.GATT_SUCCESS
                }
                result
            } finally {
                @Suppress("DEPRECATION")
                characteristic.writeType = originalWriteType
            }
        }
    }

    private fun describeWriteType(writeType: Int?): String = when (writeType) {
        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT -> "WRITE_TYPE_DEFAULT"
        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE -> "WRITE_TYPE_NO_RESPONSE"
        null -> "UNSUPPORTED"
        else -> "WRITE_TYPE_$writeType"
    }

    private fun BluetoothGatt.findCharacteristic(): BluetoothGattCharacteristic? {
        val uuidsToTry = listOf(UUID_RGBW_NEW, UUID_RGBW_LEGACY)
        for (uuid in uuidsToTry) {
            for (service in services) {
                val characteristic = service.getCharacteristic(uuid) ?: continue
                val props = characteristic.properties
                val supportsWrite = (props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                val supportsWriteNoResponse = (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                if (supportsWrite || supportsWriteNoResponse) {
                    return characteristic
                }
            }
        }
        return null
    }

    private fun settleForBulb(delayMs: Long): Boolean {
        return try {
            Thread.sleep(delayMs)
            true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    private fun scaleScene(red: Int, green: Int, blue: Int, white: Int, brightnessPercent: Int): Scene {
        val clampedBrightness = brightnessPercent.coerceIn(0, 100)
        val norm = clampedBrightness / 100.0
        val mapped = Math.pow(norm, GAMMA_EXP)
        return Scene(
            red = (red.coerceIn(0, 255) * mapped).toInt().coerceIn(0, 255),
            green = (green.coerceIn(0, 255) * mapped).toInt().coerceIn(0, 255),
            blue = (blue.coerceIn(0, 255) * mapped).toInt().coerceIn(0, 255),
            white = (white.coerceIn(0, 255) * mapped).toInt().coerceIn(0, 255)
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
            if (newState == BluetoothProfile.STATE_CONNECTED || newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectionLatch.countDown()
            }
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
