package com.vortix.app.domain.analytics

/** Pure thermal labels and indicative battery-chemistry stress factor. */
object BatteryThermalWearAnalyzer {
    data class Assessment(
        val temperatureCelsius: Double,
        val multiplier: Double,
        val label: String
    )

    fun assess(temperatureCelsius: Double?): Assessment? {
        val temperature = temperatureCelsius?.takeIf { it.isFinite() && it in -20.0..100.0 }
            ?: return null
        val (multiplier, label) = when {
            temperature < 30.0 -> 1.0 to "Normal"
            temperature < 35.0 -> 1.2 to "Hafif Artış"
            temperature < 40.0 -> 1.5 to "Hızlanmış"
            else -> 2.4 to "Yüksek Aşınma"
        }
        return Assessment(temperature, multiplier, label)
    }
}

/** Android PowerManager thermal-status values; API 29+ only. */
object SystemThermalStatus {
    const val NONE = 0
    const val LIGHT = 1
    const val MODERATE = 2
    const val SEVERE = 3
    const val CRITICAL = 4
    const val EMERGENCY = 5
    const val SHUTDOWN = 6

    fun label(status: Int?): String = when (status) {
        NONE -> "NONE"
        LIGHT -> "LIGHT"
        MODERATE -> "MODERATE"
        SEVERE -> "SEVERE"
        CRITICAL -> "CRITICAL"
        EMERGENCY -> "EMERGENCY"
        SHUTDOWN -> "SHUTDOWN"
        else -> "BİLİNMİYOR"
    }

    fun isWarning(status: Int?): Boolean = status != null && status in MODERATE..SHUTDOWN
}
