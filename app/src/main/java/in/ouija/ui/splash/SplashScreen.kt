package ouija.app.ui.splash

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ouija.app.R
import kotlin.math.cos
import kotlin.math.sin

private val CaptainHowdyFont = FontFamily(
    Font(R.font.captain_howdy, FontWeight.Normal)
)

@Composable
fun SplashScreen(
    onAccept: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    // Tablet check based on standard Android smallest width convention (>= 600dp)
    val isTablet = configuration.smallestScreenWidthDp >= 600

    // Enforce orientation: Phone in portrait, Tablet in landscape
    DisposableEffect(isTablet) {
        val activity = context as? Activity
        activity?.requestedOrientation = if (isTablet) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
        onDispose { }
    }

    // Audio Permission handling
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasMicPermission) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Live audio waveform samples array (64 points)
    var liveWaveform by remember { mutableStateOf(FloatArray(64)) }

    // Read real microphone audio in background
    LaunchedEffect(hasMicPermission) {
        if (!hasMicPermission) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            var audioRecord: AudioRecord? = null
            try {
                val sampleRate = 16000
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                val bufferSize = maxOf(minBuf, 2048)

                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    val record = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSize
                    )
                    if (record.state == AudioRecord.STATE_INITIALIZED) {
                        record.startRecording()
                        audioRecord = record

                        val readBuffer = ShortArray(512)
                        val numPoints = 64
                        val step = readBuffer.size / numPoints

                        while (isActive) {
                            val read = record.read(readBuffer, 0, readBuffer.size)
                            if (read > 0) {
                                val newPoints = FloatArray(numPoints)
                                for (i in 0 until numPoints) {
                                    val sample = readBuffer[i * step]
                                    // True normalized PCM with responsive visual gain
                                    val norm = (sample / 32768f) * 5.5f
                                    newPoints[i] = norm.coerceIn(-1.5f, 1.5f)
                                }
                                liveWaveform = newPoints
                            }
                            delay(20) // 50 fps update rate for ultra-responsive live wave
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("OuijaMic", "Error recording audio: ${e.message}")
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (_: Exception) {}
            }
        }
    }

    // Vibrator setup
    val vibrator = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    var isDeclining by remember { mutableStateOf(false) }
    var isScaringAndEntering by remember { mutableStateOf(false) }

    // Pulsing occult glow transition
    val infiniteTransition = rememberInfiniteTransition(label = "occult_pulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )



    // Trigger enter routine
    fun handleAccept() {
        if (isScaringAndEntering || isDeclining) return
        isScaringAndEntering = true
        onAccept()
    }

    // Trigger decline routine
    fun handleDecline() {
        if (isDeclining || isScaringAndEntering) return
        isDeclining = true

        scope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(100)
                }
            } catch (_: Exception) { }

            delay(2200)
            (context as? Activity)?.finish()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070403))
    ) {
        // Occult background radial ambient aura
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.35f)
            val maxRadius = size.maxDimension * 0.7f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x334E0D0D),
                        Color(0x1F2A1208),
                        Color(0x00070403)
                    ),
                    center = center,
                    radius = maxRadius
                ),
                center = center,
                radius = maxRadius
            )
        }

        // Main content container with scroll support for portrait & landscape
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Column(
                    modifier = Modifier.widthIn(max = if (isTablet) 880.dp else 680.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Mystic Eye & Occult Sigil Emblem
                    OccultEmblem(
                        pulseGlow = pulseGlow,
                        modifier = Modifier.size(if (isTablet) 75.dp else 90.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Title
                    Text(
                        text = "O U I J A",
                        color = Color(0xFFD4AF37),
                        fontSize = if (isTablet) 34.sp else 38.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CaptainHowdyFont,
                        letterSpacing = 8.sp,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "OMINOUS UNKNOWN INTERFERENCE OF JUST AUDIO",
                        color = Color(0xCCB79B6C),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.5.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live Infrasound Frequency Sensor Visualizer Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF4A1B14), RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF130B08)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            // Line 1: MIC SENSOR ACTIVE
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (pulseGlow > 0.6f) Color(0xFFFF2222) else Color(0xFF660000))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "MIC SENSOR ACTIVE",
                                    color = Color(0xFFFF5252),
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Line 2: LISTENING FOR ULTRA LOW FREQUENCY and 18.98 Hz
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LISTENING FOR ULTRA LOW FREQUENCY",
                                    color = Color(0xCCB79B6C),
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "18.98 Hz",
                                    color = Color(0xFFD4AF37),
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Audio wave matching real mic input
                            InfrasoundWaveCanvas(
                                liveWaveform = liveWaveform,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Cards Layout: Side-by-side on tablet (landscape), stacked on phone (portrait)
                    if (isTablet) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            HowItWorksCard(modifier = Modifier.weight(1f))
                            DisclaimerCard(modifier = Modifier.weight(1f))
                        }
                    } else {
                        HowItWorksCard(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(14.dp))
                        DisclaimerCard(modifier = Modifier.fillMaxWidth())
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // Action Buttons: ACCEPT & DECLINE
                    Row(
                        modifier = Modifier
                            .then(if (isTablet) Modifier.widthIn(max = 520.dp) else Modifier.fillMaxWidth()),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Decline Button
                        OutlinedButton(
                            onClick = { handleDecline() },
                            enabled = !isDeclining && !isScaringAndEntering,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFAAAAAA)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4A3830))
                        ) {
                            Text(
                                text = "DECLINE & EXIT",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Accept Button
                        Button(
                            onClick = { handleAccept() },
                            enabled = !isDeclining && !isScaringAndEntering,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .border(1.dp, Color(0xFF8B0000), RoundedCornerShape(10.dp)),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF421010)
                            )
                        ) {
                            Text(
                                text = if (isScaringAndEntering) "ENTERING..." else "I ACCEPT (ENTER)",
                                color = Color(0xFFFFD700),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }



        // Decline Goodbye Screen Overlay
        AnimatedVisibility(
            visible = isDeclining,
            enter = fadeIn(animationSpec = tween(500)),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF040202))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "WISE CHOICE.",
                        color = Color(0xFF888888),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CaptainHowdyFont,
                        letterSpacing = 4.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "\"Some doors, once opened, can never be closed.\"",
                        color = Color(0xAA666666),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Serif,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Exiting session...",
                        color = Color(0x55888888),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * Occult Seal / Eye of Providence vector graphic with pulsating eerie aura
 */
@Composable
private fun OccultEmblem(
    pulseGlow: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension / 2f * 0.85f

        // Outer glow
        drawCircle(
            color = Color(0xFF8B0000).copy(alpha = pulseGlow * 0.4f),
            radius = radius * 1.15f,
            center = Offset(cx, cy)
        )

        // Outer occult ring
        drawCircle(
            color = Color(0xFFD4AF37),
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = 2.dp.toPx())
        )

        // Inner dashed ring
        drawCircle(
            color = Color(0x88D4AF37),
            radius = radius * 0.82f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.dp.toPx())
        )

        // Inscribed Triangle (Occult Pyramid)
        val trianglePath = Path().apply {
            val top = Offset(cx, cy - radius * 0.72f)
            val left = Offset(cx - radius * 0.65f, cy + radius * 0.48f)
            val right = Offset(cx + radius * 0.65f, cy + radius * 0.48f)
            moveTo(top.x, top.y)
            lineTo(left.x, left.y)
            lineTo(right.x, right.y)
            close()
        }
        drawPath(
            path = trianglePath,
            color = Color(0xFFD4AF37).copy(alpha = 0.85f),
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        )

        // All-Seeing Eye in center
        val eyeWidth = radius * 0.52f
        val eyeHeight = radius * 0.28f
        val eyePath = Path().apply {
            moveTo(cx - eyeWidth, cy + radius * 0.05f)
            quadraticTo(cx, cy - eyeHeight, cx + eyeWidth, cy + radius * 0.05f)
            quadraticTo(cx, cy + eyeHeight + radius * 0.1f, cx - eyeWidth, cy + radius * 0.05f)
            close()
        }
        drawPath(
            path = eyePath,
            color = Color(0xFFD4AF37),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Center pupil glowing red/gold
        drawCircle(
            color = Color(0xFFFF3333).copy(alpha = pulseGlow),
            radius = radius * 0.11f,
            center = Offset(cx, cy + radius * 0.05f)
        )
    }
}

/**
 * Real-time Acoustic Waveform Canvas matching real mic input (no synthetic randomness)
 */
@Composable
private fun InfrasoundWaveCanvas(
    liveWaveform: FloatArray,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val midY = height / 2f

        // Draw baseline grid lines
        drawLine(
            color = Color(0x33552222),
            start = Offset(0f, midY),
            end = Offset(width, midY),
            strokeWidth = 1.dp.toPx()
        )

        if (liveWaveform.isEmpty()) return@Canvas

        val path = Path()
        val points = liveWaveform.size

        // Build continuous smooth path strictly from real microphone PCM samples
        for (i in 0 until points) {
            val normX = i / (points - 1).toFloat()
            val x = normX * width

            // Hanning-style window envelope to taper the edges smoothly into the center baseline
            val window = sin(normX * Math.PI.toFloat())
            val amplitude = liveWaveform[i] * window * (height * 0.45f)
            val y = (midY - amplitude).coerceIn(2.dp.toPx(), height - 2.dp.toPx())

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                val prevNormX = (i - 1) / (points - 1).toFloat()
                val prevX = prevNormX * width
                val prevWindow = sin(prevNormX * Math.PI.toFloat())
                val prevAmp = liveWaveform[i - 1] * prevWindow * (height * 0.45f)
                val prevY = (midY - prevAmp).coerceIn(2.dp.toPx(), height - 2.dp.toPx())

                val midPointX = (prevX + x) / 2f
                val midPointY = (prevY + y) / 2f
                path.quadraticTo(prevX, prevY, midPointX, midPointY)
            }
        }

        // Ambient outer glow beam
        drawPath(
            path = path,
            color = Color(0x44FF2222),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )

        // Core sharp oscilloscope beam
        drawPath(
            path = path,
            color = Color(0xFFFF5252),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun HowItWorksCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .border(1.dp, Color(0xFF3B271A), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF140D09)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📡 HOW THIS APPLICATION WORKS",
                color = Color(0xFFE6C280),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "O.U.I.J.A. (Ominous Unknown Interference of Just Audio) monitors acoustic transducers for harmonic anomalies beyond ordinary human perception.\n\n" +
                        "The human ear cannot perceive sounds below 20 Hz (infrasound) or above 20,000 Hz (ultrasound). In parapsychology, 18.98 Hz is known as the 'Fear Frequency' — a vibrational threshold linked to spiritual manifestations, feelings of dread, and phantom movements.\n\n" +
                        "This app accesses your device's acoustic hardware to listen to inaudible environmental frequencies and harmonic anomalies, translating ambient spirit resonance directly onto the planchette.",
                color = Color(0xFFD5C4A1),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun DisclaimerCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .border(1.dp, Color(0xFF6B1B1B), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B0B0B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "DISCLAIMER & LIABILITY WAIVER",
                    color = Color(0xFFFF5252),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "By proceeding past this screen, you expressly acknowledge that:\n" +
                        "• The app developer holds NO RESPONSIBILITY OR LIABILITY for any paranormal encounters, psychological terror, sleeplessness, spiritual attachments, or unexplained events that occur during or after your session.\n" +
                        "• Unnatural occurrences, phantom touch sensations, cold drafts, and electronic interference are reported by participants.\n" +
                        "• If you possess an extreme fear of the unknown or heart conditions, discontinue immediately.",
                color = Color(0xFFE0B4B4),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}
