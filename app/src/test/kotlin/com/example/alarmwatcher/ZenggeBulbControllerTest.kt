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
    fun `scaleScene clamps rgbw outputs and ignores brightnessPercent`() {
        val scene = ZenggeBulbController.scaleScene(
            red = 300,
            green = -20,
            blue = 128,
            white = 999,
            brightnessPercent = 0
        )

        assertEquals(255, scene.red)
        assertEquals(0, scene.green)
        assertEquals(128, scene.blue)
        assertEquals(255, scene.white)
    }
}