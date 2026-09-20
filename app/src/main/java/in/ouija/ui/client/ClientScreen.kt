package ouija.app.ui.client

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ouija.app.core.commands.Command
import ouija.app.core.effects.EffectManager
import ouija.app.core.realtime.ConnectionState
import ouija.app.core.realtime.RealtimeManager
import ouija.app.core.telemetry.Telemetry

@Composable
fun ClientScreen(
    roomCode: String,
    realtimeManager: RealtimeManager,
    effectManager: EffectManager,
    onRegenerateCode: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connectionState by realtimeManager.connectionState.collectAsState()
    val incomingCommand by realtimeManager.incomingCommand.collectAsState()
    val screenState by effectManager.screenEffectState.collectAsState()

    var showDisclaimer by remember { mutableStateOf(true) }
    var questionText by remember { mutableStateOf("") }
    var spellText by remember { mutableStateOf<String?>(null) }
    var spellSpeed by remember { mutableStateOf(800L) }

    // Join channel
    LaunchedEffect(roomCode) {
        realtimeManager.joinRoom(roomCode, isController = false)
    }

    // Execute incoming commands
    LaunchedEffect(incomingCommand) {
        val cmd = incomingCommand
        if (cmd is Command) {
            if (cmd is Command.Spell) {
                spellText = cmd.text
                spellSpeed = cmd.speedMs
            }
            effectManager.executeCommand(cmd)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0B08))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1510))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(10.dp)
                                .height(10.dp)
                                .background(
                                    when (connectionState) {
                                        ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                                        ConnectionState.CONNECTING -> Color(0xFFFFC107)
                                        else -> Color(0xFFF44336)
                                    },
                                    shape = RoundedCornerShape(5.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ROOM: $roomCode",
                            color = Color(0xFFE6C280),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "Share code with controller",
                        color = Color(0xAA887766),
                        fontSize = 11.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onRegenerateCode) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Regenerate",
                            tint = Color(0xFFD4AF37)
                        )
                    }

                    Button(
                        onClick = {
                            effectManager.stopAll()
                            realtimeManager.disconnect()
                            onExit()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Exit", tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("STOP", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Ouija Board Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                OuijaBoardCanvas(
                    spellText = spellText,
                    spellSpeedMs = spellSpeed,
                    isDimmed = screenState.isDimmed,
                    onPositionChanged = { normX, normY, letter ->
                        realtimeManager.sendTelemetry(
                            Telemetry(
                                planchetteX = normX,
                                planchetteY = normY,
                                selectedLetter = letter,
                                latestQuestion = questionText
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
            }

            // Question Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1510))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = questionText,
                    onValueChange = { questionText = it },
                    placeholder = { Text("Ask the board a question...", color = Color(0xAA887766)) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFFE6C280),
                        unfocusedTextColor = Color(0xFFE6C280),
                        focusedBorderColor = Color(0xFFD4AF37),
                        unfocusedBorderColor = Color(0xFF554433)
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (questionText.isNotBlank()) {
                            realtimeManager.sendTelemetry(
                                Telemetry(latestQuestion = questionText)
                            )
                        }
                    },
                    modifier = Modifier.background(Color(0xFF3E2723), RoundedCornerShape(8.dp))
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = Color(0xFFD4AF37))
                }
            }
        }

        // Jump Scare / Video Overlay
        screenState.videoId?.let {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { effectManager.dismissVideo() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "👻 JUMP SCARE 👻",
                    color = Color.Red,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }
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

        // Disclaimer Dialog
        if (showDisclaimer) {
            AlertDialog(
                onDismissRequest = { showDisclaimer = false },
                title = { Text("Interactive Experience", color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "This app is an interactive Ouija board experience for entertainment. " +
                                "It includes audio, vibration, and visual effects. " +
                                "You can tap STOP anytime to immediately end all effects.",
                        color = Color.White
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showDisclaimer = false }) {
                        Text("ENTER BOARD", color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color(0xFF2A1C10)
            )
        }
    }
}
