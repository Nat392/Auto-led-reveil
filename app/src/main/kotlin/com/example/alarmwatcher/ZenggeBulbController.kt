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
    private const val POWER_ON_SETTLE_MS = 500L
    private const val NO_RESPONSE_SETTLE_MS = 200L
    private const val GAMMA_EXP = 0.5 // sqrt mapping for perceptual ramp

    private val UUID_RGBW_NEW: UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")
    private val UUID_RGBW_LEGACY: UUID = UUID.fromString("0000ffe9-0000-1000-8000-00805f9b34fb")
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

        Log.d(TAG, "applyScene start mac=$normalizedMac requested r=$red g=$green b=$blue w=$white brightness=$brightnessPercent")
        return try {
            if (!discoverServices(gatt, callback, context)) {
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

            Log.d(TAG, "Stabilisation de la puce BLE...")
            settleForBulb(1000L)

            val scaled = scaleScene(red, green, blue, white, brightnessPercent)
            val testScene = Scene(red = 255, green = 255, blue = 0, white = 0)
            Log.d(TAG, "Scaled scene values r=${scaled.red} g=${scaled.green} b=${scaled.blue} w=${scaled.white}")
            Log.d(TAG, "Temporary test scene forced r=${testScene.red} g=${testScene.green} b=${testScene.blue} w=${testScene.white}")
            Log.d(TAG, "Powering on bulb and applying scene")
            val success = powerOn(gatt, callback, context) &&
                settleForBulb(POWER_ON_SETTLE_MS) &&
                powerOn(gatt, callback, context) &&
                settleForBulb(100L) &&
                writeRgbPacket(
                    gatt,
                    callback,
                    testScene.red,
                    testScene.green,
                    testScene.blue,
                    testScene.white,
                    macAddress = normalizedMac,
                    context = context
                )

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
                        appendLine("connectionStatus=${callback.connectionStatus}")
                        appendLine("connectionState=${callback.connectionState}")
                        appendLine("servicesStatus=${callback.servicesStatus}")
                        appendLine("lastWriteStatus=${callback.lastWriteStatus}")
                }
            )
            false
        } finally {
            runCatching { Thread.sleep(500) }
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
            if (!discoverServices(gatt, callback, context)) return false
            val ok = writeRgbPacket(gatt, callback, 0, 0, 0, 0, powerOff = true, context = context)
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

        DiscordCrashReporter.reportDebugBlocking(
            context = context,
            source = "ZenggeBulbController.connect",
            details = buildString {
                appendLine("[Zengge BLE Connect]")
                appendLine("MAC: ${device.address}")
                appendLine("State: ${callback.connectionState}")
                appendLine("Status: ${callback.connectionStatus}")
            }
        )

        return gatt
    }

    private fun discoverServices(gatt: BluetoothGatt, callback: SessionCallback, context: Context): Boolean {
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

        val servicesDump = buildString {
            gatt.services.forEach { service ->
                appendLine("service=${service.uuid}")
                service.characteristics.forEach { characteristic ->
                    val props = characteristic.properties
                    val canWrite = (props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                    val canWriteNoResponse = (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                    appendLine(
                        "  char=${characteristic.uuid} props=$props WRITE=$canWrite WRITE_NR=$canWriteNoResponse writeType=${characteristic.writeType}"
                    )
                }
            }
        }

        Log.d(TAG, "Services discovered:\n$servicesDump")
        DiscordCrashReporter.reportDebugBlocking(
            context = context,
            source = "ZenggeBulbController.discoverServices",
            details = servicesDump
        )
        return true
    }

    private fun powerOn(gatt: BluetoothGatt, callback: SessionCallback, context: Context? = null): Boolean {
        return writeRgbPacket(gatt, callback, 0, 0, 0, 0, powerOn = true, context = context)
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
        macAddress: String = "",
        context: Context? = null
    ): Boolean {
        val characteristic = gatt.findCharacteristic() ?: return false
        return when {
            powerOff -> writeCharacteristic(gatt, callback, characteristic, byteArrayOf(0x71.toByte(), 0x24, 0x0F, 0xA4.toByte()), context)
            powerOn -> writeCharacteristic(gatt, callback, characteristic, byteArrayOf(0x71.toByte(), 0x23, 0x0F, 0xA3.toByte()), context)
            else -> tryVendorPayloads(gatt, callback, characteristic, red, green, blue, white, macAddress, context)
        }
    }
    
    private fun tryVendorPayloads(
        gatt: BluetoothGatt,
        callback: SessionCallback,
        characteristic: BluetoothGattCharacteristic,
        red: Int,
        green: Int,
        blue: Int,
        white: Int,
        macAddress: String = "",
        context: Context? = null
    ): Boolean {
        val payload = buildVendorScenePacket(255, 0, 0, 0)
        Log.d(TAG, "Writing vendor payload: ${payload.toHexString()}")
        return writeCharacteristic(gatt, callback, characteristic, payload, context)
    }

    private fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }

    private fun buildVendorScenePacket(
        red: Int,
        green: Int,
        blue: Int,
        white: Int
    ): ByteArray {
        val tmpl = byteArrayOf(
            0x31,
            red.coerceIn(0, 255).toByte(),
            green.coerceIn(0, 255).toByte(),
            blue.coerceIn(0, 255).toByte(),
            white.coerceIn(0, 255).toByte(),
            0x0F
        )
        val checksum = (tmpl.sumOf { it.toInt() and 0xFF } and 0xFF).toByte()
        return tmpl + checksum
    }

    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        callback: SessionCallback,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray,
        context: Context? = null
    ): Boolean {
        val props = characteristic.properties
        val supportsWrite = (props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
        val supportsWriteNoResponse = (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
        val preferredWriteType = when {
            supportsWrite -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            supportsWriteNoResponse -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else -> null
        }
        val writeTypeLabel = describeWriteType(preferredWriteType)

        Log.d(
            TAG,
            "writeCharacteristic uuid=${characteristic.uuid} props=$props supportsWrite=$supportsWrite supportsWriteNR=$supportsWriteNoResponse preferredWriteType=$preferredWriteType payload=${payload.toHexString()}"
        )

        if (preferredWriteType == null) {
            Log.w(TAG, "Characteristic ${characteristic.uuid} does not advertise write support")
            callback.lastWriteStatus = BluetoothGatt.GATT_FAILURE
            return false
        }

        fun reportWrite(stage: String, started: Boolean?, result: Boolean? = null) {
            if (context == null) return
            DiscordCrashReporter.reportDebugBlocking(
                context = context,
                source = "ZenggeBulbController.writeCharacteristic",
                details = buildString {
                    appendLine("[Zengge BLE Write]")
                    appendLine("Stage: $stage")
                    appendLine("Characteristic: ${characteristic.uuid}")
                    appendLine("WriteType: $writeTypeLabel")
                    appendLine("Payload Hex: ${payload.toHexString()}")
                    if (started != null) {
                        appendLine("Started: $started")
                    }
                    if (result != null) {
                        appendLine("Result: $result")
                    }
                    appendLine("Last Write Status: ${callback.lastWriteStatus}")
                }
            )
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            reportWrite(stage = "before-write", started = null)
            val status = runCatching {
                gatt.writeCharacteristic(
                    characteristic,
                    payload,
                    preferredWriteType
                )
            }.getOrDefault(BluetoothGatt.GATT_FAILURE)
            callback.lastWriteStatus = status
            Log.d(TAG, "writeCharacteristic() started status=$status writeType=$preferredWriteType")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "writeCharacteristic() rejected: $status writeType=$preferredWriteType")
                reportWrite(stage = "rejected", started = false, result = false)
                return false
            }
            val result = if (preferredWriteType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
                Thread.sleep(300)
                true
            } else {
                callback.writeLatch.await(OP_TIMEOUT_MS, TimeUnit.MILLISECONDS) &&
                    callback.lastWriteStatus == BluetoothGatt.GATT_SUCCESS
            }
            reportWrite(stage = "after-write", started = true, result = result)
            result
        } else {
            val originalWriteType = characteristic.writeType
            try {
                val legacyWriteType = preferredWriteType
                @Suppress("DEPRECATION")
                characteristic.writeType = legacyWriteType
                @Suppress("DEPRECATION")
                characteristic.value = payload
                callback.resetWriteLatch()
                reportWrite(stage = "before-write", started = null)
                val started = runCatching { gatt.writeCharacteristic(characteristic) }.getOrDefault(false)
                Log.d(TAG, "legacy write started=$started writeType=$legacyWriteType payload=${payload.toHexString()}")
                if (!started) {
                    reportWrite(stage = "rejected", started = false, result = false)
                    return false
                }
                val result = if (legacyWriteType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
                    Thread.sleep(300)
                    true
                } else {
                    val awaited = callback.writeLatch.await(OP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    Log.d(TAG, "legacy write awaited=$awaited lastWriteStatus=${callback.lastWriteStatus}")
                    awaited && callback.lastWriteStatus == BluetoothGatt.GATT_SUCCESS
                }
                reportWrite(stage = "after-write", started = started, result = result)
                result
            } finally {
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
        services.forEach { service ->
            uuidsToTry.forEach { uuid ->
                service.getCharacteristic(uuid)?.let {
                    Log.d(TAG, "Found write characteristic uuid=$uuid in service=${service.uuid}")
                    return it
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
        // Use a non-linear mapping to make low brightness values more perceptible.
        val norm = clampedBrightness / 100.0
        val mapped = Math.pow(norm, GAMMA_EXP)
        Log.d(TAG, "scaleScene brightness=$clampedBrightness norm=$norm mapped=$mapped GAMMA_EXP=$GAMMA_EXP")
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