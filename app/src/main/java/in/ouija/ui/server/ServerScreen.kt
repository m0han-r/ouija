package ouija.app.ui.server

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ouija.app.core.commands.Command
import ouija.app.core.realtime.ConnectionState
import ouija.app.core.realtime.RealtimeManager
import ouija.app.core.utils.CodeGenerator

@Composable
fun ServerScreen(
    realtimeManager: RealtimeManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var inputCode by remember { mutableStateOf(CodeGenerator.getLastConnectedCode(context)) }
    var answerText by remember { mutableStateOf("") }

    val connectionState by realtimeManager.connectionState.collectAsState()
    val isPeerConnected by realtimeManager.isPeerConnected.collectAsState()
    val telemetry by realtimeManager.incomingTelemetry.collectAsState()

    val haptic = LocalHapticFeedback.current
    var lastTriggeredLabel by remember { mutableStateOf<String?>(null) }

    fun triggerScare(command: Command, label: String? = null) {
        try {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } catch (_: Exception) {}
        if (label != null) {
            lastTriggeredLabel = label
        }
        realtimeManager.sendCommand(command)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0B08))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFFD4AF37))
            }
            Text(
                text = "CONTROLLER CONSOLE",
                color = Color(0xFFD4AF37),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Code Entry & Connection Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1510)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("CONNECT TO VICTIM'S PHONE", color = Color(0xFFE6C280), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = { if (it.length <= 5) inputCode = it.uppercase() },
                        placeholder = { Text("5-LETTER CODE", color = Color(0xAA887766)) },
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

                    Button(
                        onClick = {
                            if (inputCode.length == 5) {
                                CodeGenerator.saveLastConnectedCode(context, inputCode)
                                realtimeManager.joinRoom(inputCode, isController = true)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3E2723))
                    ) {
                        Text("CONNECT", color = Color(0xFFD4AF37))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .height(10.dp)
                            .background(
                                when {
                                    connectionState == ConnectionState.CONNECTED && isPeerConnected -> Color(0xFF4CAF50)
                                    connectionState == ConnectionState.CONNECTED -> Color(0xFFFFC107)
                                    else -> Color(0xFFF44336)
                                },
                                shape = RoundedCornerShape(5.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            connectionState == ConnectionState.CONNECTED && isPeerConnected -> "VICTIM ONLINE"
                            connectionState == ConnectionState.CONNECTED -> "WAITING FOR VICTIM..."
                            connectionState == ConnectionState.CONNECTING -> "CONNECTING..."
                            else -> "DISCONNECTED"
                        },
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live Telemetry View
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1510)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("LIVE TELEMETRY", color = Color(0xFFD4AF37), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Asked Question: ${telemetry?.latestQuestion?.ifBlank { "None" } ?: "Waiting..."}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Planchette Position: ${telemetry?.selectedLetter?.ifBlank { "Idle" } ?: "Idle"}",
                    color = Color(0xFFE6C280),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Answer Console
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1510)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("OUIJA ANSWER CONSOLE", color = Color(0xFFD4AF37), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = answerText,
                        onValueChange = { answerText = it.uppercase() },
                        placeholder = { Text("SPELL MESSAGE (E.G. YES)", color = Color(0xAA887766)) },
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
                            if (answerText.isNotBlank()) {
                                triggerScare(Command.Spell(answerText), "SPELL: $answerText")
                            }
                        },
                        modifier = Modifier.background(Color(0xFF3E2723), RoundedCornerShape(8.dp))
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Spell", tint = Color(0xFFD4AF37))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Buttons (Corner Words: YES, NO, HELLO, GOODBYE)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("YES", "NO", "HELLO", "GOODBYE").forEach { quick ->
                        Button(
                            onClick = { triggerScare(Command.Spell(quick), "SPELL: $quick") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1C10))
                        ) {
                            Text(
                                text = quick,
                                color = Color(0xFFD4AF37),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Custom Phrase Button: KILL VIJAY
                Button(
                    onClick = { triggerScare(Command.Spell("KILL VIJAY"), "SPELL: KILL VIJAY") },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3E1A1A))
                ) {
                    Text(
                        text = "🗡️ KILL VIJAY",
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Scare Panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1510)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("SCARE CONTROL PANEL", color = Color(0xFFF44336), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                lastTriggeredLabel?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "TRIGGERED: $it",
                        color = Color(0xFF81C784),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }


                Spacer(modifier = Modifier.height(12.dp))

                // Grid of Scare Triggers
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Row 1: Jump Scare Videos
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScareButton("👻 JUMP SCARE 1", Modifier.weight(1f)) {
                            triggerScare(Command.Video("jump_scare_1"), "JUMP SCARE 1")
                        }
                        ScareButton("👻 JUMP SCARE 2", Modifier.weight(1f)) {
                            triggerScare(Command.Video("jump_scare_2"), "JUMP SCARE 2")
                        }
                    }

                    // Row 2: Visual Terror (Screen Glitch & Screen Break)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScareButton("📺 SCREEN GLITCH", Modifier.weight(1f)) {
                            triggerScare(Command.ScreenFlash("GLITCH"), "SCREEN GLITCH")
                        }
                        ScareButton("💥 SCREEN BREAK", Modifier.weight(1f)) {
                            triggerScare(Command.FakeUI("SCREEN_CRACK"), "SCREEN BREAK")
                        }
                    }

                    // Row 3: Vocal / Entity SFX
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScareButton("🔊 SCREAM", Modifier.weight(1f)) {
                            triggerScare(Command.Sound("scream", sudden = true), "SCREAM")
                        }
                        ScareButton("🫁 BREATH", Modifier.weight(1f)) {
                            triggerScare(Command.Sound("breath", sudden = true), "BREATH")
                        }
                    }

                    // Row 4: Atmospheric Environmental SFX
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScareButton("🚪 DOOR CREAK", Modifier.weight(1f)) {
                            triggerScare(Command.Sound("door_creak", sudden = true), "DOOR CREAK")
                        }
                        ScareButton("💧 WATER DROP", Modifier.weight(1f)) {
                            triggerScare(Command.Sound("water_drop", sudden = true), "WATER DROP")
                        }
                    }

                    // Row 5: Ghostly Whispers (TTS) & Kill Vijay Audio
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScareButton("🗣️ CREEPY TTS", Modifier.weight(1f)) {
                            triggerScare(Command.TTS("I see you in the dark...", pitch = 0.4f), "CREEPY TTS")
                        }
                        ScareButton("🗡️ KILL VIJAY", Modifier.weight(1f)) {
                            triggerScare(Command.Sound("kill_vijay", sudden = true), "KILL VIJAY")
                        }
                    }

                    // Row 6: Physical Disturbances (Torch & Tremor)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScareButton("⚡ TORCH FLICKER", Modifier.weight(1f)) {
                            triggerScare(Command.Flashlight(2500L), "TORCH FLICKER")
                        }
                        ScareButton("📳 HEAVY VIBRATE", Modifier.weight(1f)) {
                            triggerScare(Command.Vibrate(listOf(0L, 800L, 120L, 1200L, 150L, 1500L, 100L, 800L), 255), "HEAVY VIBRATE")
                        }
                    }
                }



                Spacer(modifier = Modifier.height(16.dp))

                // Panic Stop Button
                Button(
                    onClick = { realtimeManager.sendCommand(Command.StopAll) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                ) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = "Stop", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PANIC STOP ALL EFFECTS", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ScareButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.border(1.dp, Color(0xFF552222), RoundedCornerShape(8.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1010)),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFFFF8A80),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
