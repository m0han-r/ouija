package ouija.app.core.effects

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
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
import kotlinx.coroutines.withContext
import ouija.app.R
import ouija.app.core.commands.Command
import java.util.Locale
import java.util.Random

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
    private var activeMediaPlayer: MediaPlayer? = null
    private var glitchAudioTrack: AudioTrack? = null

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var pendingTts: Pair<String, Float>? = null

    init {
        initTtsIfNeeded()
    }

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
            pendingTts?.let { (text, pitch) ->
                pendingTts = null
                triggerTTS(text, pitch)
            }
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
            // Default: Extended 4.7s violent paranormal tremor waveform
            val timings = pattern.ifEmpty { listOf(0L, 800L, 120L, 1200L, 150L, 1500L, 100L, 800L) }.toLongArray()
            Log.d(TAG, "Triggering prolonged vibration (amplitude = $safeAmplitude, timings = ${timings.joinToString()})")

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
            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                } catch (_: Exception) {
                    VibrationEffect.createWaveform(timings, -1)
                }
            } else {
                null
            }

            try {
                if (effect != null) {
                    vibrator?.vibrate(effect, audioAttributes)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(timings, -1)
                }
            } catch (e: Exception) {
                Log.w(TAG, "AudioAttributes vibration failed, falling back to standard: ${e.message}")
                if (effect != null) {
                    vibrator?.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(timings, -1)
                }
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
        scope.launch(Dispatchers.Main) {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                val targetVol = if (sudden || soundId.equals("scream", ignoreCase = true)) {
                    (maxVol * 0.95f).toInt().coerceAtLeast(1)
                } else {
                    (maxVol * 0.75f).toInt().coerceAtLeast(1)
                }
                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)

                val resId = when (soundId.lowercase().replace(" ", "_").replace("-", "_")) {
                    "scream", "loud" -> R.raw.scream
                    "water_drop", "water", "drip", "droplet" -> R.raw.water_drop
                    "breath", "choking", "whisper", "breathing" -> R.raw.breath
                    "door_creak", "door", "creak", "creaky_door" -> R.raw.door_creak
                    else -> null
                }

                if (resId != null) {
                    try {
                        activeMediaPlayer?.stop()
                        activeMediaPlayer?.release()
                    } catch (_: Exception) {}

                    val mp = MediaPlayer.create(context, resId)
                    activeMediaPlayer = mp
                    mp?.setOnCompletionListener {
                        it.release()
                        if (activeMediaPlayer == it) activeMediaPlayer = null
                    }
                    mp?.start()
                } else {
                    withContext(Dispatchers.IO) {
                        val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
                        toneGen.startTone(ToneGenerator.TONE_PROP_PROMPT, 600)
                        delay(700)
                        toneGen.release()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing sound: ${e.message}", e)
            }
        }
    }

    private fun triggerTTS(text: String, pitch: Float) {
        Log.d(TAG, "Triggering TTS: \"$text\" (pitch = $pitch)")
        initTtsIfNeeded()
        if (!isTtsReady || tts == null) {
            Log.w(TAG, "TTS requested but engine is not ready yet, queuing pending request")
            pendingTts = Pair(text, pitch)
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

        if (mode == "GLITCH") {
            // Play slow, heavy, atmospheric tape-drag & sub-bass horror drone
            playGlitchNoise(3800L)
            // Trigger slow, heavy, ominous tremor vibration
            triggerVibration(
                listOf(0L, 450L, 180L, 650L, 200L, 900L, 250L, 600L),
                255
            )
        }

        flashJob = scope.launch {
            val duration = if (mode == "GLITCH") 3800L else 2000L
            delay(duration)
            _screenEffectState.value = _screenEffectState.value.copy(flashMode = null)
            if (mode == "GLITCH") {
                stopGlitchNoise()
            }
        }
    }

    private fun playGlitchNoise(durationMs: Long) {
        scope.launch(Dispatchers.IO) {
            try {
                stopGlitchNoise()
                val sampleRate = 44100
                val totalSamples = ((sampleRate * durationMs) / 1000).toInt()
                val buffer = ShortArray(totalSamples)
                val random = Random()

                // Synthesize slow, atmospheric horror drone & tape-speed failure:
                // 1. Slow cursed tape drag / pitch dive (240Hz glides down to 46Hz with analog wow/flutter)
                // 2. Heavy subterranean bass drone (42Hz with slow 1.2Hz breathing LFO)
                // 3. Eerie two-tone beating bells (110Hz & 113.8Hz creating 3.8Hz acoustic beats)
                // 4. Sparse high-voltage electrical arc discharges & Geiger crackles
                // 5. Analog saturation & soft clipping
                var phaseTape = 0.0
                var phaseDrone = 0.0
                var phaseChime1 = 0.0
                var phaseChime2 = 0.0

                for (i in 0 until totalSamples) {
                    val tSec = i.toDouble() / sampleRate
                    val progress = tSec / (durationMs / 1000.0)

                    // 1. Cursed tape drag (pitch dives from 240Hz to 46Hz)
                    val tapeFreq = 46.0 + 194.0 * Math.exp(-progress * 2.2) + 8.0 * Math.sin(2.0 * Math.PI * 0.8 * tSec)
                    phaseTape += 2.0 * Math.PI * tapeFreq / sampleRate
                    val tapeVoice = Math.sin(phaseTape) + 0.45 * Math.sin(phaseTape * 2.0) + 0.2 * Math.sin(phaseTape * 3.0)

                    // 2. Heavy subterranean bass drone (42Hz with breathing LFO)
                    val droneLfo = 0.65 + 0.35 * Math.sin(2.0 * Math.PI * 1.2 * tSec)
                    phaseDrone += 2.0 * Math.PI * 42.0 / sampleRate
                    val drone = Math.sin(phaseDrone) * droneLfo

                    // 3. Eerie two-tone beating bells (slow psychoacoustic beat)
                    phaseChime1 += 2.0 * Math.PI * 110.0 / sampleRate
                    phaseChime2 += 2.0 * Math.PI * 113.8 / sampleRate
                    val chimePeriod = tSec % 1.6
                    val chimeEnv = Math.exp(-chimePeriod * 1.8)
                    val chime = (Math.sin(phaseChime1) + Math.sin(phaseChime2)) * 0.4 * chimeEnv

                    // 4. Sparse high-voltage electrical arc discharges
                    val sparkChance = if (Math.sin(tSec * 2.5) > 0.1) 0.025 else 0.003
                    val spark = if (random.nextDouble() < sparkChance) (random.nextDouble() * 2.0 - 1.0) * 0.9 else 0.0

                    // 5. Smooth fade in / out envelope
                    val fadeIn = (tSec / 0.10).coerceIn(0.0, 1.0)
                    val fadeOut = ((durationMs / 1000.0 - tSec) / 0.40).coerceIn(0.0, 1.0)
                    val masterEnv = fadeIn * fadeOut

                    // 6. Analog saturation & soft clipping
                    val rawMix = (tapeVoice * 0.42 + drone * 0.48 + chime * 0.28 + spark * 0.35) * masterEnv
                    val saturated = Math.tanh(rawMix * 1.7)
                    buffer[i] = (saturated * 31500.0).toInt().toShort()
                }

                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.9f).toInt(), 0)

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                glitchAudioTrack = track
                track.write(buffer, 0, buffer.size)
                track.play()
            } catch (e: Exception) {
                Log.e(TAG, "Error playing glitch noise: ${e.message}", e)
            }
        }
    }

    private fun stopGlitchNoise() {
        try {
            glitchAudioTrack?.stop()
            glitchAudioTrack?.release()
            glitchAudioTrack = null
        } catch (_: Exception) {}
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

        if (type == "SCREEN_CRACK") {
            // Play explosive glass shatter sound
            playGlassBreakSound()
            // Violent mechanical shockwave vibration
            triggerVibration(
                listOf(0L, 70L, 30L, 200L, 40L, 100L),
                255
            )
            // Instant violent jolt
            triggerShake(350L)
        }
    }

    private fun playGlassBreakSound() {
        scope.launch(Dispatchers.IO) {
            try {
                val sampleRate = 44100
                val durationMs = 1200L
                val totalSamples = ((sampleRate * durationMs) / 1000).toInt()
                val buffer = ShortArray(totalSamples)
                val random = Random()

                // Acoustic Glass Shatter Synthesis:
                // 1. Violent Initial Impact Pop & Shockwave (0 - 35ms)
                // 2. High-Frequency Glass Resonant Modes (2850Hz, 3620Hz, 4480Hz, 6100Hz)
                // 3. Dense Granular Debris & Fragment Scatter (30ms - 1000ms)
                var phaseR1 = 0.0
                var phaseR2 = 0.0
                var phaseR3 = 0.0
                var phaseR4 = 0.0

                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate

                    // 1. Shockwave impact pop (steep bass transient + clipped burst)
                    val impactPop = if (t < 0.04) {
                        val env = (1.0 - t / 0.04)
                        Math.sin(2.0 * Math.PI * 85.0 * t) * env * 1.5 + (random.nextDouble() * 2.0 - 1.0) * env * 1.2
                    } else 0.0

                    // 2. Ringing glass resonance modes
                    phaseR1 += 2.0 * Math.PI * 2850.0 / sampleRate
                    phaseR2 += 2.0 * Math.PI * 3620.0 / sampleRate
                    phaseR3 += 2.0 * Math.PI * 4480.0 / sampleRate
                    phaseR4 += 2.0 * Math.PI * 6100.0 / sampleRate

                    val ringEnv1 = Math.exp(-t * 7.5)
                    val ringEnv2 = Math.exp(-t * 11.0)
                    val ringEnv3 = Math.exp(-t * 16.0)

                    val glassRing = (Math.sin(phaseR1) * 0.45 + Math.sin(phaseR2) * 0.35) * ringEnv1 +
                            Math.sin(phaseR3) * 0.25 * ringEnv2 +
                            Math.sin(phaseR4) * 0.20 * ringEnv3

                    // 3. Granular crackles & shards scattering
                    val debrisEnv = Math.exp(-t * 4.0)
                    val debrisChance = (0.25 * debrisEnv).coerceIn(0.001, 0.4)
                    val debris = if (random.nextDouble() < debrisChance) {
                        (random.nextDouble() * 2.0 - 1.0) * 0.8 * debrisEnv
                    } else 0.0

                    val mix = (impactPop * 0.7 + glassRing * 0.65 + debris * 0.45).coerceIn(-1.0, 1.0)
                    buffer[i] = (mix * 31500.0).toInt().toShort()
                }

                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.95f).toInt(), 0)

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, buffer.size)
                track.play()
            } catch (e: Exception) {
                Log.e(TAG, "Error playing glass break sound: ${e.message}", e)
            }
        }
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
            activeMediaPlayer?.stop()
            activeMediaPlayer?.release()
            activeMediaPlayer = null
        } catch (_: Exception) {}

        stopGlitchNoise()

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
