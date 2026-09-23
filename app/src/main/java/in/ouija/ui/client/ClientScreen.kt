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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
    var secretTapCount by remember { mutableIntStateOf(0) }
    var spellText by remember { mutableStateOf<String?>(null) }
    var spellSpeed by remember { mutableStateOf(1800L) }
    var spellTrigger by remember { mutableLongStateOf(0L) }

    // Screen shake animation when poltergeist effect is triggered
    val shakeOffsetX = remember { Animatable(0f) }
    val shakeOffsetY = remember { Animatable(0f) }
    LaunchedEffect(screenState.isShaking) {
        if (screenState.isShaking) {
            while (true) {
                shakeOffsetX.animateTo(
                    targetValue = (-16..16).random().toFloat(),
                    animationSpec = tween(durationMillis = 35, easing = LinearEasing)
                )
                shakeOffsetY.animateTo(
                    targetValue = (-12..12).random().toFloat(),
                    animationSpec = tween(durationMillis = 35, easing = LinearEasing)
                )
            }
        } else {
            shakeOffsetX.snapTo(0f)
            shakeOffsetY.snapTo(0f)
        }
    }

    // Enforce landscape orientation and cleanup on dispose
    val context = LocalContext.current
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

    // Secret menu tap reset timer
    LaunchedEffect(secretTapCount) {
        if (secretTapCount > 0) {
            delay(2000L)
            secretTapCount = 0
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

        // Screen Flash overlay
        screenState.flashMode?.let { mode ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        when (mode) {
                            "BLACKOUT" -> Color.Black
                            "RED" -> Color(0xCCB71C1C)
                            "GLITCH" -> Color(0xAA4A148C)
                            else -> Color(0xDDFFFFFF) // STROBE
                        }
                    )
            )
        }

        // Secret Tap Area in Top-Left Corner (3 quick taps reveal hidden menu)
        Box(
            modifier = Modifier
                .size(60.dp)
                .align(Alignment.TopStart)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    secretTapCount++
                    if (secretTapCount >= 3) {
                        secretTapCount = 0
                        showSecretMenu = true
                    }
                }
        )

        // Jump Scare / Video Overlay
        screenState.videoId?.let { vidId ->
            JumpScareVideoOverlay(
                videoId = vidId,
                onDismiss = { effectManager.dismissVideo() }
            )
        }

        // Fake UI Overlay (e.g. Battery low / Cracked screen / Unknown presence)
        screenState.fakeUiType?.let { type ->
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
                            "SCREEN_CRACK" -> "⚡ CRITICAL SYSTEM ERROR"
                            else -> "⚠️ UNKNOWN PRESENCE DETECTED NEARBY"
                        },
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
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
