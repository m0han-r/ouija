package ouija.app.ui.client

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import ouija.app.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import ouija.app.core.commands.Command
import ouija.app.core.effects.EffectManager
import ouija.app.core.realtime.RealtimeManager
import ouija.app.core.telemetry.Telemetry

@Composable
fun ClientScreen(
    roomCode: String,
    realtimeManager: RealtimeManager,
    effectManager: EffectManager,
    onRegenerateCode: () -> Unit,
    onNavigateToServer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val screenState by effectManager.screenEffectState.collectAsState()

    var showSecretMenu by remember { mutableStateOf(false) }
    var spellText by remember { mutableStateOf<String?>(null) }
    var spellSpeed by remember { mutableStateOf(1800L) }
    var spellTrigger by remember { mutableLongStateOf(0L) }

    val context = LocalContext.current

    // Screen shake animation when poltergeist effect or glitch is triggered
    val isGlitchActive = screenState.flashMode == "GLITCH"
    val isShakingActive = screenState.isShaking || isGlitchActive
    val shakeOffsetX = remember { Animatable(0f) }
    val shakeOffsetY = remember { Animatable(0f) }
    LaunchedEffect(isShakingActive) {
        if (isShakingActive) {
            val maxAmp = if (isGlitchActive) 14 else 16
            val animDuration = if (isGlitchActive) 120 else 30
            while (true) {
                shakeOffsetX.animateTo(
                    targetValue = (-maxAmp..maxAmp).random().toFloat(),
                    animationSpec = tween(durationMillis = animDuration, easing = LinearEasing)
                )
                shakeOffsetY.animateTo(
                    targetValue = (-maxAmp..maxAmp).random().toFloat(),
                    animationSpec = tween(durationMillis = animDuration, easing = LinearEasing)
                )
            }
        } else {
            shakeOffsetX.snapTo(0f)
            shakeOffsetY.snapTo(0f)
        }
    }

    // Enforce landscape orientation, 100% max volume, and cleanup on dispose
    DisposableEffect(Unit) {
        effectManager.ensureMaxVolume()
        val activity = context as? Activity
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        onDispose {
            activity?.requestedOrientation = originalOrientation
            effectManager.stopAll()
            realtimeManager.disconnect()
        }
    }

    // Join channel and send periodic heartbeats (continuous volume lock)
    LaunchedEffect(roomCode) {
        realtimeManager.joinRoom(roomCode, isController = false)
        while (true) {
            delay(2000L)
            effectManager.ensureMaxVolume()
            realtimeManager.sendTelemetry(
                Telemetry(
                    selectedLetter = spellText ?: ""
                )
            )
        }
    }

    // Execute incoming commands stream
    LaunchedEffect(Unit) {
        realtimeManager.incomingCommand.collect { cmd ->
            if (cmd is Command.Spell) {
                spellText = cmd.text
                spellSpeed = cmd.speedMs
                spellTrigger++
            }
            effectManager.executeCommand(cmd)
        }
    }



    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070403))
            .offset { IntOffset(shakeOffsetX.value.roundToInt(), shakeOffsetY.value.roundToInt()) }
    ) {
        // 100% Clean Ouija Board Canvas (Fills entire screen)
        OuijaBoardCanvas(
            spellText = spellText,
            spellSpeedMs = spellSpeed,
            spellTrigger = spellTrigger,
            isDimmed = screenState.isDimmed,
            onMoonTapped = {
                showSecretMenu = true
            },
            onPositionChanged = { normX, normY, letter ->
                realtimeManager.sendTelemetry(
                    Telemetry(
                        planchetteX = normX,
                        planchetteY = normY,
                        selectedLetter = letter
                    )
                )
            }
        )

        // Screen Flash / Glitch Overlay
        screenState.flashMode?.let { mode ->
            if (mode == "GLITCH") {
                GlitchScreenEffect()
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            when (mode) {
                                "BLACKOUT" -> Color.Black
                                "RED" -> Color(0xCCB71C1C)
                                else -> Color(0xDDFFFFFF) // STROBE
                            }
                        )
                )
            }
        }

        // Jump Scare / Video Overlay
        screenState.videoId?.let { vidId ->
            JumpScareVideoOverlay(
                videoId = vidId,
                effectManager = effectManager,
                onDismiss = { effectManager.dismissVideo() }
            )
        }

        // Fake UI / Screen Break Overlay
        screenState.fakeUiType?.let { type ->
            if (type == "SCREEN_CRACK") {
                ScreenBreakOverlay(onDismiss = { effectManager.dismissFakeUI() })
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xBB000000))
                        .clickable { effectManager.dismissFakeUI() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF212121), RoundedCornerShape(12.dp))
                            .border(2.dp, Color.Red, RoundedCornerShape(12.dp))
                            .padding(24.dp)
                    ) {
                        Text(
                            text = when (type) {
                                "BATTERY_LOW" -> "⚠️ Battery 1% — Shutting down..."
                                else -> "⚠️ UNKNOWN PRESENCE DETECTED NEARBY"
                            },
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Secret Admin Menu (Revealed only by secret 3-tap gesture)
        if (showSecretMenu) {
            AlertDialog(
                onDismissRequest = { showSecretMenu = false },
                title = { Text("Hidden Prank Menu", color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Victim Code: $roomCode", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Enter this code on your controller phone to trigger remote scares.", color = Color(0xAA887766), fontSize = 12.sp)
                    }
                },
                confirmButton = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onRegenerateCode() }) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Regenerate", tint = Color(0xFFD4AF37))
                        }
                        Button(
                            onClick = {
                                showSecretMenu = false
                                onNavigateToServer()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3E2723))
                        ) {
                            Icon(imageVector = Icons.Default.Build, contentDescription = "Controller", tint = Color(0xFFD4AF37))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("CONTROLLER", color = Color(0xFFD4AF37), fontSize = 12.sp)
                        }
                        TextButton(onClick = { showSecretMenu = false }) {
                            Text("CLOSE", color = Color(0xAA887766))
                        }
                    }
                },
                containerColor = Color(0xFF2A1C10)
            )
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun JumpScareVideoOverlay(
    videoId: String,
    effectManager: EffectManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isJumpScare2 = remember(videoId) {
        val normalized = videoId.lowercase().replace("-", "_").trim()
        normalized in listOf("jump_scare_2", "scare_2", "2", "vid_scare_02")
    }
    val rawResId = if (isJumpScare2) R.raw.jump_scare_2 else R.raw.jump_scare_1

    // State controlling the 5-second suspense black screen with silence for Jump Scare 2
    var isSilencePhase by remember(videoId) { mutableStateOf(isJumpScare2) }

    val exoPlayer = remember(context, rawResId) {
        ExoPlayer.Builder(context).build().apply {
            val uri = Uri.parse("android.resource://${context.packageName}/$rawResId")
            setMediaItem(MediaItem.fromUri(uri))
            volume = 1.0f
            prepare()
            playWhenReady = !isJumpScare2 // If Jump Scare 2, hold until 5s silence completes
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        onDismiss()
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    onDismiss()
                }
            })
        }
    }

    LaunchedEffect(videoId) {
        if (isJumpScare2) {
            isSilencePhase = true
            delay(5000L) // 5 seconds of black screen with complete silence
            isSilencePhase = false
            effectManager.ensureMaxVolume()
            exoPlayer.volume = 1.0f
            exoPlayer.play()
        } else {
            isSilencePhase = false
            effectManager.ensureMaxVolume()
            exoPlayer.volume = 1.0f
            exoPlayer.play()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(enabled = !isSilencePhase) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        // Video Player: Plays when silence phase completes (or immediately for Jump Scare 1)
        if (!isSilencePhase) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        player = exoPlayer
                    }
                }
            )
        }
    }
}


@Composable
private fun GlitchScreenEffect(
    modifier: Modifier = Modifier
) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            frame++
            delay(100L) // Slowed down to ~10 FPS for deliberate analog VHS horror stutter
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val f = frame

            // 1. Slow Analog Color Decay & Strobe (deep blood-red / dark decay instead of rapid neon strobe)
            val strobeAlpha = when (f % 8) {
                0 -> 0.35f
                1 -> 0.15f
                4 -> 0.40f
                5 -> 0.20f
                else -> 0.08f
            }
            val strobeColor = when ((f / 3) % 3) {
                0 -> Color(0xFFB71C1C).copy(alpha = strobeAlpha) // Blood Crimson
                1 -> Color(0xFF004D40).copy(alpha = strobeAlpha * 0.8f) // Dark Eerie Teal
                else -> Color(0xFF311B92).copy(alpha = strobeAlpha * 0.7f) // Abyssal Violet
            }
            drawRect(color = strobeColor, size = size)

            // 2. Horizontal CRT Phosphor Scanline Grid
            val scanlineSpacing = 3.5.dp.toPx()
            var yPos = 0f
            while (yPos < h) {
                drawLine(
                    color = Color.Black.copy(alpha = 0.45f),
                    start = Offset(0f, yPos),
                    end = Offset(w, yPos),
                    strokeWidth = 1.dp.toPx()
                )
                yPos += scanlineSpacing
            }

            // 3. Chunky, Lingering Horizontal Tearing Bands (holds position for 2-3 frames)
            val stepFrame = f / 2 // Holds each slice position for 200ms
            val numSlices = 5
            for (i in 0 until numSlices) {
                val sliceY = ((i * 179 + stepFrame * 137) % 1000) / 1000f * h
                val sliceH = (24 + (stepFrame * 19 + i * 41) % 48).dp.toPx()
                val xShift = ((stepFrame * 67 + i * 113) % 100 - 50).dp.toPx()

                val sliceColor = when (i % 4) {
                    0 -> Color(0x88B71C1C) // Deep Crimson Displacement
                    1 -> Color(0x6600B4D8) // Cyan Chromatic Bleed
                    2 -> Color(0x77FFFFFF) // Voltage Sag / Exposure Blowout
                    else -> Color(0xCC000000) // Complete Video Dropout
                }
                drawRect(
                    color = sliceColor,
                    topLeft = Offset(xShift, sliceY),
                    size = Size(w, sliceH)
                )
            }

            // 4. Slow Rolling VHS V-Sync Tracking Bar (creeps deliberately down the screen)
            val trackingY = ((f * 12) % (h.toInt() + 180)).toFloat() - 90f
            val trackingHeight = 85.dp.toPx()
            drawRect(
                color = Color(0x55FFFFFF),
                topLeft = Offset(0f, trackingY),
                size = Size(w, trackingHeight)
            )
            // Heavy analog noise inside tracking bar
            for (j in 0 until 14) {
                val speckleX = ((j * 197 + (f / 2) * 163) % 1000) / 1000f * w
                val speckleY = trackingY + ((j * 43) % trackingHeight.toInt())
                val speckleW = (18 + (j * 23) % 45).dp.toPx()
                drawRect(
                    color = if (j % 2 == 0) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.85f),
                    topLeft = Offset(speckleX, speckleY),
                    size = Size(speckleW, 4.dp.toPx())
                )
            }

            // 5. Scattered Corrupted Digital Blocks (slow 300ms updates, matrix decay)
            val blockStep = f / 3 // Holds for 300ms
            for (b in 0 until 6) {
                val blockX = ((b * 241 + blockStep * 167) % 1000) / 1000f * w
                val blockY = ((b * 151 + blockStep * 229) % 1000) / 1000f * h
                val blockW = (25 + (b * 37) % 55).dp.toPx()
                val blockH = (16 + (b * 23) % 35).dp.toPx()
                val blockColor = if (b % 2 == 0) Color(0x99B71C1C) else Color(0x9900B4D8)
                drawRect(
                    color = blockColor,
                    topLeft = Offset(blockX, blockY),
                    size = Size(blockW, blockH)
                )
            }

            // 6. Subliminal Demonic Eye Stare (Prolonged 5-frame terrifying stare around frame 12-16)
            // Holds for ~500ms so the user unmistakably sees the eyes staring from the void!
            if (f in 12..16) {
                // Occult Crimson Glare
                drawRect(color = Color(0xDDB71C1C), size = size)

                // Glowing demonic eyes in center of screen
                val eyeCenterY = h * 0.48f
                val eyeSpacing = w * 0.13f
                val eyeRadius = 20.dp.toPx()

                // Left demonic eye
                drawCircle(
                    color = Color.Black,
                    radius = eyeRadius * 1.6f,
                    center = Offset(w * 0.5f - eyeSpacing, eyeCenterY)
                )
                drawCircle(
                    color = Color(0xFFFF1744),
                    radius = eyeRadius,
                    center = Offset(w * 0.5f - eyeSpacing, eyeCenterY)
                )
                drawCircle(
                    color = Color.Yellow,
                    radius = eyeRadius * 0.35f,
                    center = Offset(w * 0.5f - eyeSpacing, eyeCenterY)
                )

                // Right demonic eye
                drawCircle(
                    color = Color.Black,
                    radius = eyeRadius * 1.6f,
                    center = Offset(w * 0.5f + eyeSpacing, eyeCenterY)
                )
                drawCircle(
                    color = Color(0xFFFF1744),
                    radius = eyeRadius,
                    center = Offset(w * 0.5f + eyeSpacing, eyeCenterY)
                )
                drawCircle(
                    color = Color.Yellow,
                    radius = eyeRadius * 0.35f,
                    center = Offset(w * 0.5f + eyeSpacing, eyeCenterY)
                )
            }
        }
    }
}

@Composable
private fun ScreenBreakOverlay(
    onDismiss: () -> Unit,
    showBlackBackground: Boolean = true
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Load photorealistic broken tempered glass texture (Gorilla Glass fracture)
    val glassBitmap = remember(isLandscape) {
        val resId = if (isLandscape) {
            R.drawable.cracked_glass_landscape
        } else {
            R.drawable.cracked_glass_portrait
        }
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeResource(context.resources, resId, options)?.asImageBitmap()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (showBlackBackground) Modifier.background(Color.Black) else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    ) {
        // Photorealistic Shattered Glass Render
        glassBitmap?.let { bitmap ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                val dstSize = IntSize(size.width.toInt(), size.height.toInt())

                // Pass 1: Subtle glass refraction chromatic aberration (cyan/cool tint shifted 1px)
                drawImage(
                    image = bitmap,
                    dstOffset = IntOffset(-1, -1),
                    dstSize = dstSize,
                    colorFilter = ColorFilter.tint(Color(0x3380DEEA), BlendMode.Modulate),
                    blendMode = BlendMode.Screen
                )

                // Pass 2: Base specular glass crack reflection (crystalline white fractures & pulverized impact core)
                drawImage(
                    image = bitmap,
                    dstOffset = IntOffset(0, 0),
                    dstSize = dstSize,
                    blendMode = BlendMode.Screen
                )
            }
        }
    }
}
