package ouija.app.core.effects

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
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

    companion object {
        private const val TAG = "OuijaEffects"
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private var torchJob: Job? = null
    private var shakeJob: Job? = null
    private var flashJob: Job? = null

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val _screenEffectState = MutableStateFlow(ScreenEffectState())
    val screenEffectState: StateFlow<ScreenEffectState> = _screenEffectState.asStateFlow()

    private fun initTtsIfNeeded() {
        if (tts == null) {
            try {
                Log.d(TAG, "Initializing TextToSpeech engine...")
                tts = TextToSpeech(context.applicationContext, this)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize TTS: ${e.message}", e)
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            Log.d(TAG, "TextToSpeech initialized successfully")
            tts?.language = Locale.US
            tts?.setPitch(0.5f)
            tts?.setSpeechRate(0.8f)
            isTtsReady = true
        } else {
            Log.w(TAG, "TextToSpeech initialization failed with status: $status")
        }
    }

    fun executeCommand(command: Command) {
        Log.d(TAG, "Executing command effect: $command")
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
            val safeAmplitude = if (amplitude == 0) 255 else amplitude.coerceIn(1, 255)
            val timings = pattern.ifEmpty { listOf(0L, 300L, 100L, 600L) }.toLongArray()
            Log.d(TAG, "Triggering vibration (amplitude = $safeAmplitude, timings = ${timings.joinToString()})")

            @Suppress("DEPRECATION")
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val amplitudes = IntArray(timings.size) { if (it % 2 == 1) safeAmplitude else 0 }
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)

            try {
                vibrator?.vibrate(effect, audioAttributes)
            } catch (e: Exception) {
                Log.w(TAG, "AudioAttributes vibration failed, falling back to standard: ${e.message}")
                vibrator?.vibrate(effect)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing vibration: ${e.message}", e)
        }
    }

    private fun triggerFlashlight(durationMs: Long) {
        Log.d(TAG, "Triggering flashlight flicker for ${durationMs}ms")
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
                Log.e(TAG, "Error flickering flashlight: ${e.message}", e)
            }
        }
    }

    private fun triggerSound(soundId: String, sudden: Boolean) {
        Log.d(TAG, "Triggering sound: $soundId (sudden = $sudden)")
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
                Log.e(TAG, "Error playing sound: ${e.message}", e)
            }
        }
    }

    private fun triggerTTS(text: String, pitch: Float) {
        Log.d(TAG, "Triggering TTS: \"$text\" (pitch = $pitch)")
        initTtsIfNeeded()
        if (!isTtsReady || tts == null) {
            Log.w(TAG, "TTS requested but engine is not ready yet")
            return
        }
        try {
            tts?.setPitch(pitch.coerceIn(0.1f, 1.0f))
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "SCARE_TTS")
        } catch (e: Exception) {
            Log.e(TAG, "Error executing TTS: ${e.message}", e)
        }
    }

    private fun triggerScreenFlash(mode: String) {
        Log.d(TAG, "Triggering screen flash mode: $mode")
        flashJob?.cancel()
        _screenEffectState.value = _screenEffectState.value.copy(flashMode = mode)
        flashJob = scope.launch {
            delay(2000L)
            _screenEffectState.value = _screenEffectState.value.copy(flashMode = null)
        }
    }

    private fun triggerShake(durationMs: Long) {
        Log.d(TAG, "Triggering screen shake for ${durationMs}ms")
        shakeJob?.cancel()
        _screenEffectState.value = _screenEffectState.value.copy(isShaking = true)
        shakeJob = scope.launch {
            delay(durationMs)
            _screenEffectState.value = _screenEffectState.value.copy(isShaking = false)
        }
    }

    private fun triggerVideo(videoId: String) {
        Log.d(TAG, "Triggering jump scare video: $videoId")
        _screenEffectState.value = _screenEffectState.value.copy(videoId = videoId)
    }

    fun dismissVideo() {
        Log.d(TAG, "Dismissing jump scare video overlay")
        _screenEffectState.value = _screenEffectState.value.copy(videoId = null)
    }

    private fun triggerFakeUI(type: String) {
        Log.d(TAG, "Triggering fake UI overlay: $type")
        _screenEffectState.value = _screenEffectState.value.copy(fakeUiType = type)
    }

    fun dismissFakeUI() {
        Log.d(TAG, "Dismissing fake UI overlay")
        _screenEffectState.value = _screenEffectState.value.copy(fakeUiType = null)
    }

    private fun setDimmed(dimmed: Boolean) {
        Log.d(TAG, "Setting board dimming: $dimmed")
        _screenEffectState.value = _screenEffectState.value.copy(isDimmed = dimmed)
    }

    fun stopAll() {
        Log.d(TAG, "STOP ALL EFFECTS requested - halting all tasks")
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
