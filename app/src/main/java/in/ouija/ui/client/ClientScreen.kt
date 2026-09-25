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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    // Enforce landscape orientation and cleanup on dispose
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        onDispose {
            activity?.requestedOrientation = originalOrientation
            effectManager.stopAll()
            realtimeManager.disconnect()
        }
    }

    // Join channel and send periodic heartbeats
    LaunchedEffect(roomCode) {
        realtimeManager.joinRoom(roomCode, isController = false)
        while (true) {
            delay(2000L)
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
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isJumpScare2 = remember(videoId) {
        val normalized = videoId.lowercase().replace("-", "_").trim()
        normalized in listOf("jump_scare_2", "scare_2", "2", "vid_scare_02")
    }
    val rawResId = if (isJumpScare2) R.raw.jump_scare_2 else R.raw.jump_scare_1

    // State controlling the 5-second electronic failure blackout exclusively for Jump Scare 2
    var isBlackoutPhase by remember(videoId) { mutableStateOf(isJumpScare2) }
    var glitchAlpha by remember(videoId) { mutableFloatStateOf(0f) }

    LaunchedEffect(videoId) {
        if (isJumpScare2) {
            isBlackoutPhase = true
            // Initial electronic power failure flicker in the first ~130ms
            glitchAlpha = 0.35f
            delay(40L)
            glitchAlpha = 0.05f
            delay(40L)
            glitchAlpha = 0.5f
            delay(50L)
            glitchAlpha = 0f // Complete dead electronic black screen
            delay(4870L) // Remaining time to make exactly 5.0 seconds
            isBlackoutPhase = false
        } else {
            isBlackoutPhase = false
        }
    }

    val exoPlayer = remember(context, rawResId, isBlackoutPhase) {
        if (!isBlackoutPhase) {
            ExoPlayer.Builder(context).build().apply {
                val uri = Uri.parse("android.resource://${context.packageName}/$rawResId")
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
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
        } else {
            null
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer?.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(enabled = !isBlackoutPhase) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        if (!isBlackoutPhase && exoPlayer != null) {
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
        } else {
            // Electronic failure dead screen overlay (5 seconds)
            if (glitchAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF202020).copy(alpha = glitchAlpha))
                )
            }
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
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onDismiss() }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Impact center (slightly off-center for natural realism)
            val cx = w * 0.44f
            val cy = h * 0.52f

            // 1. Amoled Liquid Bleed (Dark ink pool radiating from fracture core)
            drawCircle(
                color = Color(0xF208030B),
                radius = 70.dp.toPx(),
                center = Offset(cx, cy)
            )
            drawCircle(
                color = Color(0xD0150522),
                radius = 110.dp.toPx(),
                center = Offset(cx + 8f, cy - 6f)
            )

            // 2. Dead Pixel / OLED Display Malfunction Lines
            // Bright vertical green laser line
            drawLine(
                color = Color(0xD900FF66),
                start = Offset(cx, 0f),
                end = Offset(cx, h),
                strokeWidth = 1.5.dp.toPx()
            )
            // Bright horizontal magenta glitch line
            drawLine(
                color = Color(0xD9FF007F),
                start = Offset(0f, cy),
                end = Offset(w, cy),
                strokeWidth = 1.5.dp.toPx()
            )

            // 3. Dense Impact Shatter Core (Crushed white powdered glass)
            drawCircle(
                color = Color(0xEEFFFFFF),
                radius = 12.dp.toPx(),
                center = Offset(cx, cy)
            )
            drawCircle(
                color = Color(0x99FFFFFF),
                radius = 28.dp.toPx(),
                center = Offset(cx, cy)
            )

            // 4. Primary Radial Fractures (16 jagged spiderweb spokes reaching screen edges)
            val spokeAngles = listOf(
                12.0, 35.0, 58.0, 82.0, 105.0, 130.0, 155.0, 178.0,
                202.0, 225.0, 248.0, 272.0, 295.0, 318.0, 338.0, 355.0
            )

            val spokeNodes = mutableListOf<List<Offset>>()

            for (angleDeg in spokeAngles) {
                val rad = Math.toRadians(angleDeg)
                val cosA = Math.cos(rad).toFloat()
                val sinA = Math.sin(rad).toFloat()
                val maxReach = Math.max(w, h) * 0.85f

                val nodes = mutableListOf<Offset>()
                nodes.add(Offset(cx, cy))

                val steps = 6
                var currentX = cx
                var currentY = cy

                for (s in 1..steps) {
                    val stepDist = (maxReach / steps) * s
                    val jitterAngle = rad + Math.PI / 2.0
                    val jitter = ((s * 47) % 31 - 15).dp.toPx() * (s / 3f)

                    val targetX = cx + (stepDist * cosA) + (Math.cos(jitterAngle).toFloat() * jitter)
                    val targetY = cy + (stepDist * sinA) + (Math.sin(jitterAngle).toFloat() * jitter)

                    // Draw shadow underneath for depth
                    drawLine(
                        color = Color(0xCC000000),
                        start = Offset(currentX, currentY) + Offset(1.5f, 2f),
                        end = Offset(targetX, targetY) + Offset(1.5f, 2f),
                        strokeWidth = 3.5.dp.toPx()
                    )

                    // Chromatic aberration fringe (glass refraction)
                    drawLine(
                        color = if (s % 2 == 0) Color(0x6600E5FF) else Color(0x66FF1744),
                        start = Offset(currentX, currentY) + Offset(-1f, -1f),
                        end = Offset(targetX, targetY) + Offset(-1f, -1f),
                        strokeWidth = 2.dp.toPx()
                    )

                    // Bright specular white glass fracture highlight
                    drawLine(
                        color = Color(0xF5FFFFFF),
                        start = Offset(currentX, currentY),
                        end = Offset(targetX, targetY),
                        strokeWidth = if (s <= 2) 2.2.dp.toPx() else 1.4.dp.toPx()
                    )

                    currentX = targetX
                    currentY = targetY
                    nodes.add(Offset(targetX, targetY))
                }
                spokeNodes.add(nodes)
            }

            // 5. Concentric Spiderweb Cross-Cracks connecting spokes
            for (level in 1..4) {
                for (s in 0 until spokeNodes.size) {
                    val nextSpoke = (s + 1) % spokeNodes.size
                    val p1 = spokeNodes[s].getOrNull(level) ?: continue
                    val p2 = spokeNodes[nextSpoke].getOrNull(level) ?: continue

                    // Shadow
                    drawLine(
                        color = Color(0xAA000000),
                        start = p1 + Offset(1f, 1.5f),
                        end = p2 + Offset(1f, 1.5f),
                        strokeWidth = 2.dp.toPx()
                    )
                    // Crack highlight
                    drawLine(
                        color = Color(0xD8FFFFFF),
                        start = p1,
                        end = p2,
                        strokeWidth = 1.2.dp.toPx()
                    )
                }
            }

            // 6. Scattered Micro Glass Splinters around impact point
            for (m in 0 until 24) {
                val dist = (15 + (m * 29) % 95).dp.toPx()
                val ang = Math.toRadians((m * 53.0) % 360.0)
                val mx = cx + (Math.cos(ang).toFloat() * dist)
                val my = cy + (Math.sin(ang).toFloat() * dist)
                val sLen = (6 + (m * 17) % 18).dp.toPx()

                drawLine(
                    color = Color.White.copy(alpha = 0.9f),
                    start = Offset(mx, my),
                    end = Offset(mx + sLen * 0.7f, my + sLen * 0.5f),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }
    }
}
