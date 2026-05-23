package com.example.alarmwatcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import kotlin.math.pow

class ZenggeBulbControllerTest {

    @Test
    fun `buildPowerPacket computes the checksum for power on and power off`() {
        val powerOnPacket = ZenggeBulbController.buildPowerPacket(true)
        val powerOffPacket = ZenggeBulbController.buildPowerPacket(false)

        assertArrayEquals(
            byteArrayOf(
                0x00, 0x01, 0x80.toByte(), 0x00, 0x00, 0x0D, 0x0E, 0x0B,
                0x3B, 0x23, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x5E
            ),
            powerOnPacket
        )

        assertArrayEquals(
            byteArrayOf(
                0x00, 0x01, 0x80.toByte(), 0x00, 0x00, 0x0D, 0x0E, 0x0B,
                0x3B, 0x24, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x5F
            ),
            powerOffPacket
        )
    }

    @Test
    fun `buildScenePacket clamps rgb channels before checksum calculation`() {
        val scenePacket = ZenggeBulbController.buildScenePacket(
            red = 300,
            green = -20,
            blue = 128,
            white = 999
        )

        assertArrayEquals(
            byteArrayOf(
                0x00, 0x01, 0x80.toByte(), 0x00, 0x00, 0x08, 0x09, 0x0B,
                0x31, 0xFF.toByte(), 0x00, 0x80.toByte(), 0x00, 0x00, 0x0F, 0xBF.toByte()
            ),
            scenePacket
        )
    }

    @Test
    fun `scaleScene applies gamma and clamps rgbw outputs`() {
        val scene = ZenggeBulbController.scaleScene(
            red = 300,
            green = -20,
            blue = 128,
            white = 999,
            brightnessPercent = 50
        )

        val mapped = 0.5.pow(2.4)

        assertEquals(expectedChannel(300, mapped), scene.red)
        assertEquals(expectedChannel(-20, mapped), scene.green)
        assertEquals(expectedChannel(128, mapped), scene.blue)
        assertEquals(expectedChannel(999, mapped), scene.white)
    }

    private fun expectedChannel(input: Int, mapped: Double): Int {
        return (input.coerceIn(0, 255) * mapped).toInt().coerceIn(0, 255)
    }
}