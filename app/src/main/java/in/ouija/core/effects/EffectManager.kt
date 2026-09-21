package ouija.app.core.effects

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ouija.app.core.commands.Command
import java.util.Locale

data class ScreenEffectState(
    val flashMode: String? = null, // STROBE, BLACKOUT, RED, GLITCH
    val isShaking: Boolean = false,
    val isDimmed: Boolean = false,
    val videoId: String? = null,
    val fakeUiType: String? = null // BATTERY_LOW, SCREEN_CRACK, UNKNOWN_PRESENCE
)

class EffectManager(
    private val context: Context
) : TextToSpeech.OnInitListener {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var torchJob: Job? = null
    private var shakeJob: Job? = null
    private var flashJob: Job? = null

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val _screenEffectState = MutableStateFlow(ScreenEffectState())
    val screenEffectState: StateFlow<ScreenEffectState> = _screenEffectState.asStateFlow()

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            // TTS initialization fallback
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setPitch(0.5f)
            tts?.setSpeechRate(0.8f)
            isTtsReady = true
        }
    }

    fun executeCommand(command: Command) {
        when (command) {
            is Command.Vibrate -> triggerVibration(command.pattern, command.amplitude)
            is Command.Flashlight -> triggerFlashlight(command.durationMs)
            is Command.Sound -> triggerSound(command.soundId, command.sudden)
            is Command.TTS -> triggerTTS(command.text, command.pitch)
            is Command.ScreenFlash -> triggerScreenFlash(command.mode)
            is Command.Shake -> triggerShake(command.durationMs)
            is Command.Video -> triggerVideo(command.videoId)
            is Command.FakeUI -> triggerFakeUI(command.type)
            is Command.Dim -> setDimmed(command.dimmed)
            is Command.StopAll -> stopAll()
            else -> {}
        }
    }

    private fun triggerVibration(pattern: List<Long>, amplitude: Int) {
        try {
            val safeAmplitude = amplitude.coerceIn(1, 255)
            val timings = pattern.ifEmpty { listOf(0L, 300L, 100L, 600L) }.toLongArray()

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val amplitudes = IntArray(timings.size) { if (it % 2 == 1) safeAmplitude else 0 }
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibratorManager?.defaultVibrator?.vibrate(effect, audioAttributes)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                val amplitudes = IntArray(timings.size) { if (it % 2 == 1) safeAmplitude else 0 }
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator?.vibrate(effect, audioAttributes)
            }
        } catch (e: Exception) {
            // Safe vibration handling
        }
    }

    private fun triggerFlashlight(durationMs: Long) {
        torchJob?.cancel()
        torchJob = scope.launch(Dispatchers.IO) {
            try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                val cameraId = cameraManager?.cameraIdList?.firstOrNull() ?: return@launch
                val endTime = System.currentTimeMillis() + durationMs

                var state = false
                while (System.currentTimeMillis() < endTime) {
                    state = !state
                    cameraManager.setTorchMode(cameraId, state)
                    delay((50..150).random().toLong())
                }
                cameraManager.setTorchMode(cameraId, false)
            } catch (e: Exception) {
                // Flashlight fallback
            }
        }
    }

    private fun triggerSound(soundId: String, sudden: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                val safeCap = (maxVol * 0.7f).toInt() // 70% safety cap

                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, safeCap, 0)

                val toneType = when (soundId.lowercase()) {
                    "scream", "loud" -> ToneGenerator.TONE_CDMA_HIGH_L
                    "whisper", "knock" -> ToneGenerator.TONE_PROP_BEEP
                    "door" -> ToneGenerator.TONE_SUP_ERROR
                    else -> ToneGenerator.TONE_PROP_PROMPT
                }

                val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
                toneGen.startTone(toneType, if (sudden) 1000 else 600)
                delay(1200)
                toneGen.release()
            } catch (e: Exception) {
                // Safe sound fallback
            }
        }
    }

    private fun triggerTTS(text: String, pitch: Float) {
        if (!isTtsReady || tts == null) return
        try {
            tts?.setPitch(pitch.coerceIn(0.1f, 1.0f))
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "SCARE_TTS")
        } catch (e: Exception) {
            // TTS fallback
        }
    }

    private fun triggerScreenFlash(mode: String) {
        flashJob?.cancel()
        _screenEffectState.value = _screenEffectState.value.copy(flashMode = mode)
        flashJob = scope.launch {
            delay(2000L)
            _screenEffectState.value = _screenEffectState.value.copy(flashMode = null)
        }
    }

    private fun triggerShake(durationMs: Long) {
        shakeJob?.cancel()
        _screenEffectState.value = _screenEffectState.value.copy(isShaking = true)
        shakeJob = scope.launch {
            delay(durationMs)
            _screenEffectState.value = _screenEffectState.value.copy(isShaking = false)
        }
    }

    private fun triggerVideo(videoId: String) {
        _screenEffectState.value = _screenEffectState.value.copy(videoId = videoId)
    }

    fun dismissVideo() {
        _screenEffectState.value = _screenEffectState.value.copy(videoId = null)
    }

    private fun triggerFakeUI(type: String) {
        _screenEffectState.value = _screenEffectState.value.copy(fakeUiType = type)
    }

    fun dismissFakeUI() {
        _screenEffectState.value = _screenEffectState.value.copy(fakeUiType = null)
    }

    private fun setDimmed(dimmed: Boolean) {
        _screenEffectState.value = _screenEffectState.value.copy(isDimmed = dimmed)
    }

    fun stopAll() {
        torchJob?.cancel()
        shakeJob?.cancel()
        flashJob?.cancel()

        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = cameraManager?.cameraIdList?.firstOrNull()
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, false)
            }
        } catch (e: Exception) {
            // Ignore camera reset error
        }

        try {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.cancel()
        } catch (e: Exception) {
            // Ignore vibrator error
        }

        try {
            tts?.stop()
        } catch (e: Exception) {
            // Ignore tts error
        }

        _screenEffectState.value = ScreenEffectState()
    }

    fun release() {
        stopAll()
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            // Ignore tts shutdown error
        }
    }
}
