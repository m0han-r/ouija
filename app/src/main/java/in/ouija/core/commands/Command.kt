package ouija.app.core.commands

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class CommandPacket(
    val v: Int = 1,
    val cmd: String,
    val id: String = UUID.randomUUID().toString(),
    val ts: Long = System.currentTimeMillis(),
    val args: CommandArgs = CommandArgs()
)

@Serializable
data class CommandArgs(
    val pattern: List<Long> = emptyList(),
    val amplitude: Int = 255,
    val soundId: String = "",
    val sudden: Boolean = false,
    val videoId: String = "",
    val text: String = "",
    val speedMs: Long = 1800L,
    val targetX: Float = 0.5f,
    val targetY: Float = 0.5f,
    val durationMs: Long = 1000L,
    val mode: String = "STROBE",
    val pitch: Float = 0.6f,
    val type: String = "BATTERY_LOW",
    val dimmed: Boolean = true
)

sealed class Command {
    data class Vibrate(val pattern: List<Long> = listOf(0L, 800L, 120L, 1200L, 150L, 1500L, 100L, 800L), val amplitude: Int = 255) : Command()
    data class Sound(val soundId: String, val sudden: Boolean = false) : Command()
    data class Video(val videoId: String) : Command()
    data class Spell(val text: String, val speedMs: Long = 1800L) : Command()
    data class MovePlanchette(val targetX: Float, val targetY: Float) : Command()
    data class Flashlight(val durationMs: Long = 2000L) : Command()
    data class ScreenFlash(val mode: String) : Command() // STROBE, BLACKOUT, RED, GLITCH
    data class Shake(val durationMs: Long = 1000L) : Command()
    data class TTS(val text: String, val pitch: Float = 0.5f) : Command()
    data class FakeUI(val type: String) : Command() // BATTERY_LOW, SCREEN_CRACK, UNKNOWN_PRESENCE
    data class Dim(val dimmed: Boolean) : Command()
    object StopAll : Command()

    fun toPacket(): CommandPacket {
        return when (this) {
            is Vibrate -> CommandPacket(cmd = "vibrate", args = CommandArgs(pattern = pattern, amplitude = amplitude))
            is Sound -> CommandPacket(cmd = "sound", args = CommandArgs(soundId = soundId, sudden = sudden))
            is Video -> CommandPacket(cmd = "video", args = CommandArgs(videoId = videoId))
            is Spell -> CommandPacket(cmd = "spell", args = CommandArgs(text = text, speedMs = speedMs))
            is MovePlanchette -> CommandPacket(cmd = "move_planchette", args = CommandArgs(targetX = targetX, targetY = targetY))
            is Flashlight -> CommandPacket(cmd = "flashlight", args = CommandArgs(durationMs = durationMs))
            is ScreenFlash -> CommandPacket(cmd = "screen_flash", args = CommandArgs(mode = mode))
            is Shake -> CommandPacket(cmd = "shake", args = CommandArgs(durationMs = durationMs))
            is TTS -> CommandPacket(cmd = "tts", args = CommandArgs(text = text, pitch = pitch))
            is FakeUI -> CommandPacket(cmd = "fake_ui", args = CommandArgs(type = type))
            is Dim -> CommandPacket(cmd = "dim", args = CommandArgs(dimmed = dimmed))
            is StopAll -> CommandPacket(cmd = "stop_all")
        }
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }

        fun fromPacket(packet: CommandPacket): Command {
            return when (packet.cmd) {
                "vibrate" -> Vibrate(
                    pattern = packet.args.pattern.ifEmpty { listOf(0L, 300L, 100L, 600L) },
                    amplitude = if (packet.args.amplitude == 0) 255 else packet.args.amplitude
                )
                "sound" -> Sound(soundId = packet.args.soundId, sudden = packet.args.sudden)
                "video" -> Video(videoId = packet.args.videoId)
                "spell" -> Spell(text = packet.args.text, speedMs = packet.args.speedMs)
                "move_planchette" -> MovePlanchette(targetX = packet.args.targetX, targetY = packet.args.targetY)
                "flashlight" -> Flashlight(durationMs = packet.args.durationMs)
                "screen_flash" -> ScreenFlash(mode = packet.args.mode)
                "shake" -> Shake(durationMs = packet.args.durationMs)
                "tts" -> TTS(text = packet.args.text, pitch = packet.args.pitch)
                "fake_ui" -> FakeUI(type = packet.args.type)
                "dim" -> Dim(dimmed = packet.args.dimmed)
                "stop_all" -> StopAll
                else -> StopAll
            }
        }
    }
}
