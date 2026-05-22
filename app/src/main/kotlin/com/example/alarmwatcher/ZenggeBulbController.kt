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
            DiscordCrashReporter.reportDebugBlocking(
                context = context,
                source = "ZenggeBulbController.applyScene.adapterMissing",
                details = "Bluetooth adapter unavailable"
            )
            return false
        }

        val normalizedMac = macAddress.trim()
        if (normalizedMac.isBlank()) {
            Log.w(TAG, "No bulb MAC configured")
            DiscordCrashReporter.reportDebugBlocking(
                context = context,
                source = "ZenggeBulbController.applyScene.macMissing",
                details = "No bulb MAC configured"
            )
            return false
        }

        val device = runCatching { adapter.getRemoteDevice(normalizedMac) }.getOrNull() ?: run {
            Log.w(TAG, "Invalid bulb MAC: $normalizedMac")
            DiscordCrashReporter.reportDebugBlocking(
                context = context,
                source = "ZenggeBulbController.applyScene.macInvalid",
                details = "Invalid bulb MAC: $normalizedMac"
            )
            return false
        }

        val callback = SessionCallback()
        val gatt = connect(device, context, callback) ?: run {
            DiscordCrashReporter.reportDebugBlocking(
                context = context,
                source = "ZenggeBulbController.applyScene.connectFailed",
                details = buildString {
                    appendLine("connect returned null")
                    appendLine("mac=$normalizedMac")
                    appendLine("connectionStatus=${callback.connectionStatus}")
                    appendLine("connectionState=${callback.connectionState}")
                }
            )
            return false
        }

        return try {
            if (!discoverServices(gatt, callback, context)) {
                return false
            }

            Log.d(TAG, "Waiting for BLE settle")
            DiscordCrashReporter.reportDebugBlocking(
                context = context,
                source = "ZenggeBulbController.applyScene.beforeSettle",
                details = buildString {
                    appendLine("Waiting for BLE settle")
                    appendLine("mac=$normalizedMac")
                }
            )
            settleForBulb(1000L)

            val scaled = scaleScene(red, green, blue, white, brightnessPercent)
            DiscordCrashReporter.reportDebugBlocking(
                context = context,
                source = "ZenggeBulbController.applyScene.scaled",
                details = buildString {
                    appendLine("Scaled scene")
                    appendLine("mac=$normalizedMac")
                    appendLine("scaledR=${scaled.red} scaledG=${scaled.green} scaledB=${scaled.blue} scaledW=${scaled.white}")
                    appendLine("brightnessPercent=$brightnessPercent")
                }
            )

            val success = writeRgbPacket(
                gatt = gatt,
                callback = callback,
                red = scaled.red,
                green = scaled.green,
                blue = scaled.blue,
                white = scaled.white,
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
            if (!discoverServices(gatt, callback, context)) return "{\"error\":\"discover_failed\"}"
            val characteristic = gatt.findCharacteristic() ?: return "{\"error\":\"char_not_found\"}"

            fun runAttempt(name: String, payload: ByteArray, forceType: Int? = null) {
                val ok = writeCharacteristic(gatt, callback, characteristic, payload, context, forceType)
                results.add("$name:${payload.toHexString()}:$ok:status=${callback.lastWriteStatus}")
            }

            runAttempt("power_on", buildPowerPacket(true))
            Thread.sleep(700)
            runAttempt("scene", buildScenePacket(red, green, blue, white))

            gatt.disconnect()
            gatt.close()
            "{\"results\":[\"${results.joinToString("\",\"")}\"]}"
        } catch (e: Exception) {
            "{\"error\":\"${e.message}\"}"
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
        DiscordCrashReporter.reportDebugBlocking(
            context = context,
            source = "ZenggeBulbController.connect.entry",
            details = buildString {
                appendLine("connect entry")
                appendLine("mac=${device.address}")
                appendLine("sdk=${Build.VERSION.SDK_INT}")
            }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Missing BLUETOOTH_CONNECT permission")
                    DiscordCrashReporter.reportDebugBlocking(
                        context = context,
                        source = "ZenggeBulbController.connect.permissionDenied",
                        details = "Missing BLUETOOTH_CONNECT permission"
                    )
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
            DiscordCrashReporter.reportDebugBlocking(
                context = context,
                source = "ZenggeBulbController.connect.failed",
                details = buildString {
                    appendLine("Connection failed")
                    appendLine("mac=${device.address}")
                    appendLine("status=${callback.connectionStatus}")
                    appendLine("state=${callback.connectionState}")
                }
            )
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
            DiscordCrashReporter.reportDebugBlocking(
                context = context,
                source = "ZenggeBulbController.discoverServices.startFailed",
                details = "discoverServices() returned false"
            )
            return false
        }

        if (!callback.servicesLatch.await(OP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            DiscordCrashReporter.reportDebugBlocking(
                context = context,
                source = "ZenggeBulbController.discoverServices.timeout",
                details = "Timed out waiting for services discovery"
            )
            return false
        }

        if (callback.servicesStatus != BluetoothGatt.GATT_SUCCESS) {
            DiscordCrashReporter.reportDebugBlocking(
                context = context,
                source = "ZenggeBulbController.discoverServices.failed",
                details = buildString {
                    appendLine("Service discovery failed")
                    appendLine("status=${callback.servicesStatus}")
                }
            )
            return false
        }

        val servicesDump = buildString {
            gatt.services.forEach { service ->
                appendLine("service=${service.uuid}")
                service.characteristics.forEach { characteristic ->
                    val props = characteristic.properties
                    val canWrite = (props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                    val canWriteNoResponse = (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                    appendLine("  char=${characteristic.uuid} props=$props WRITE=$canWrite WRITE_NR=$canWriteNoResponse writeType=${characteristic.writeType}")
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
            powerOff -> writeCharacteristic(
                gatt,
                callback,
                characteristic,
                buildPowerPacket(false),
                context,
                forceWriteType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
            powerOn -> writeCharacteristic(
                gatt,
                callback,
                characteristic,
                buildPowerPacket(true),
                context,
                forceWriteType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
            else -> tryAllSceneWrites(gatt, callback, characteristic, red, green, blue, white, macAddress, context)
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
        macAddress: String = "",
        context: Context? = null
    ): Boolean {
        val scene = buildScenePacket(red, green, blue, white)
        Log.d(TAG, "Writing Zengge scene payload mac=$macAddress: ${scene.toHexString()}")
        return writeCharacteristic(
            gatt = gatt,
            callback = callback,
            characteristic = characteristic,
            payload = scene,
            context = context,
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

    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        callback: SessionCallback,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray,
        context: Context? = null,
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
        val writeTypeLabel = describeWriteType(preferredWriteType)

        Log.d(
            TAG,
            "writeCharacteristic uuid=${characteristic.uuid} props=$props supportsWrite=$supportsWrite supportsWriteNR=$supportsWriteNoResponse preferredWriteType=$preferredWriteType payload=${payload.toHexString()}"
        )
        context?.let {
            DiscordCrashReporter.reportDebugBlocking(
                context = it,
                source = "ZenggeBulbController.writeCharacteristic.entry",
                details = buildString {
                    appendLine("writeCharacteristic entry")
                    appendLine("uuid=${characteristic.uuid}")
                    appendLine("props=$props")
                    appendLine("supportsWrite=$supportsWrite")
                    appendLine("supportsWriteNR=$supportsWriteNoResponse")
                    appendLine("preferredWriteType=$preferredWriteType")
                    appendLine("payload=${payload.toHexString()}")
                }
            )
        }

        if (preferredWriteType == null) {
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
                    if (started != null) appendLine("Started: $started")
                    if (result != null) appendLine("Result: $result")
                    appendLine("Last Write Status: ${callback.lastWriteStatus}")
                }
            )
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            reportWrite(stage = "before-write", started = null)
            val status = runCatching {
                gatt.writeCharacteristic(characteristic, payload, preferredWriteType)
            }.getOrDefault(BluetoothGatt.GATT_FAILURE)
            callback.lastWriteStatus = status
            if (status != BluetoothGatt.GATT_SUCCESS) {
                reportWrite(stage = "rejected", started = false, result = false)
                return false
            }
            val result = if (preferredWriteType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
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
                @Suppress("DEPRECATION")
                characteristic.writeType = preferredWriteType
                @Suppress("DEPRECATION")
                characteristic.value = payload
                callback.resetWriteLatch()
                reportWrite(stage = "before-write", started = null)
                val started = runCatching { gatt.writeCharacteristic(characteristic) }.getOrDefault(false)
                if (!started) {
                    reportWrite(stage = "rejected", started = false, result = false)
                    return false
                }
                val result = if (preferredWriteType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
                    true
                } else {
                    callback.writeLatch.await(OP_TIMEOUT_MS, TimeUnit.MILLISECONDS) &&
                        callback.lastWriteStatus == BluetoothGatt.GATT_SUCCESS
                }
                reportWrite(stage = "after-write", started = started, result = result)
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
                    Log.d(TAG, "Found preferred write char uuid=$uuid in service=${service.uuid}")
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
