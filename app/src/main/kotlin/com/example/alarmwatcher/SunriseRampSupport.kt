package com.example.alarmwatcher

internal object SunriseRampSupport {
    const val MAX_RAMP_STEPS = 30
    const val MIN_STEP_DELAY_MS = 250L

    data class Scene(
        val red: Int,
        val green: Int,
        val blue: Int
    )

    private val reportRgbTable = listOf(
        Scene(0, 0, 0),
        Scene(1, 0, 0),
        Scene(2, 0, 0),
        Scene(3, 0, 0),
        Scene(4, 0, 0),
        Scene(6, 1, 0),
        Scene(8, 2, 0),
        Scene(10, 3, 0),
        Scene(13, 5, 0),
        Scene(17, 7, 0),
        Scene(22, 10, 1),
        Scene(28, 14, 2),
        Scene(35, 18, 4),
        Scene(43, 23, 6),
        Scene(52, 29, 9),
        Scene(61, 36, 13),
        Scene(72, 44, 18),
        Scene(84, 53, 24),
        Scene(96, 62, 31),
        Scene(109, 73, 40),
        Scene(123, 84, 49),
        Scene(138, 97, 60),
        Scene(154, 111, 73),
        Scene(170, 126, 88),
        Scene(188, 142, 104),
        Scene(206, 159, 122),
        Scene(220, 177, 142),
        Scene(220, 196, 165),
        Scene(220, 216, 191),
        Scene(220, 237, 220),
        Scene(220, 240, 255)
    )

    fun reportSceneAtStep(step: Int, steps: Int): Scene {
        val lastIndex = reportRgbTable.lastIndex
        val mappedIndex = if (steps <= 0) {
            lastIndex
        } else {
            (step * lastIndex) / steps
        }
        return reportRgbTable[mappedIndex.coerceIn(0, lastIndex)]
    }
}