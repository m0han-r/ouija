package ouija.app.core.telemetry

import kotlinx.serialization.Serializable

@Serializable
data class Telemetry(
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val currentScreen: String = "OUIJA_BOARD",
    val planchetteX: Float = 0.5f,
    val planchetteY: Float = 0.5f,
    val selectedLetter: String = "",
    val latestQuestion: String = "",
    val isHandled: Boolean = true,
    val ambientLight: Float = 10.0f,
    val headphonesConnected: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
