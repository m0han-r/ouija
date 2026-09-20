package ouija.app.ui.client

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

data class BoardPosition(val name: String, val normX: Float, val normY: Float)

@Composable
fun OuijaBoardCanvas(
    spellText: String? = null,
    spellSpeedMs: Long = 800L,
    isDimmed: Boolean = false,
    onPositionChanged: (Float, Float, String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()

    var boardWidth by remember { mutableStateOf(1000f) }
    var boardHeight by remember { mutableStateOf(1000f) }

    val planchetteX = remember { Animatable(500f) }
    val planchetteY = remember { Animatable(600f) }

    // Map of letter targets
    val targets = remember(boardWidth, boardHeight) {
        val list = mutableListOf<BoardPosition>()
        list.add(BoardPosition("YES", 0.25f, 0.18f))
        list.add(BoardPosition("NO", 0.75f, 0.18f))

        // Arch 1: A-M
        val arch1 = "ABCDEFGHIJKLM"
        arch1.forEachIndexed { i, char ->
            val angle = Math.toRadians(190.0 + (i * 12.5))
            val x = 0.5f + (0.38f * cos(angle)).toFloat()
            val y = 0.45f + (0.18f * sin(angle)).toFloat()
            list.add(BoardPosition(char.toString(), x, y))
        }

        // Arch 2: N-Z
        val arch2 = "NOPQRSTUVWXYZ"
        arch2.forEachIndexed { i, char ->
            val angle = Math.toRadians(190.0 + (i * 12.5))
            val x = 0.5f + (0.38f * cos(angle)).toFloat()
            val y = 0.62f + (0.18f * sin(angle)).toFloat()
            list.add(BoardPosition(char.toString(), x, y))
        }

        // Numbers 0-9
        val numbers = "1234567890"
        numbers.forEachIndexed { i, char ->
            val x = 0.15f + (i * 0.078f)
            val y = 0.78f
            list.add(BoardPosition(char.toString(), x, y))
        }

        list.add(BoardPosition("GOODBYE", 0.5f, 0.88f))
        list
    }

    // Auto-spelling animation handler
    LaunchedEffect(spellText) {
        if (!spellText.isNullOrBlank()) {
            val upper = spellText.uppercase()
            for (char in upper) {
                val target = targets.find { it.name == char.toString() }
                    ?: if (char == ' ') targets.find { it.name == "GOODBYE" } else null

                if (target != null) {
                    val targetX = target.normX * boardWidth
                    val targetY = target.normY * boardHeight

                    launch {
                        planchetteX.animateTo(targetX, tween(spellSpeedMs.toInt()))
                    }
                    planchetteY.animateTo(targetY, tween(spellSpeedMs.toInt()))

                    onPositionChanged(target.normX, target.normY, target.name)
                    delay(400L) // Pause at letter
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDimmed) Color(0xFF0A0808) else Color(0xFF1A120B))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    scope.launch {
                        val newX = (planchetteX.value + dragAmount.x).coerceIn(50f, boardWidth - 50f)
                        val newY = (planchetteY.value + dragAmount.y).coerceIn(50f, boardHeight - 50f)
                        planchetteX.snapTo(newX)
                        planchetteY.snapTo(newY)

                        val normX = newX / boardWidth
                        val normY = newY / boardHeight
                        val nearest = targets.minByOrNull {
                            val dx = it.normX - normX
                            val dy = it.normY - normY
                            dx * dx + dy * dy
                        }
                        onPositionChanged(normX, normY, nearest?.name ?: "")
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            boardWidth = size.width
            boardHeight = size.height

            drawBoardBackground(isDimmed)

            // Draw YES and NO
            val yesStyle = TextStyle(color = Color(0xFFD4AF37), fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            val noStyle = TextStyle(color = Color(0xFFD4AF37), fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)

            drawText(textMeasurer, "YES", Offset(size.width * 0.22f, size.height * 0.15f), style = yesStyle)
            drawText(textMeasurer, "NO", Offset(size.width * 0.72f, size.height * 0.15f), style = noStyle)

            // Draw targets
            val letterStyle = TextStyle(color = Color(0xFFE6C280), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif)
            targets.forEach { pos ->
                if (pos.name != "YES" && pos.name != "NO") {
                    drawText(
                        textMeasurer,
                        pos.name,
                        Offset(size.width * pos.normX - 12f, size.height * pos.normY - 18f),
                        style = if (pos.name == "GOODBYE") yesStyle else letterStyle
                    )
                }
            }

            // Draw Planchette
            drawPlanchette(
                centerX = planchetteX.value,
                centerY = planchetteY.value,
                isDimmed = isDimmed
            )
        }
    }
}

private fun DrawScope.drawBoardBackground(isDimmed: Boolean) {
    val strokeColor = if (isDimmed) Color(0xFF332211) else Color(0xFF8B5A2B)
    drawRect(
        color = strokeColor,
        style = Stroke(width = 8.dp.toPx())
    )
    drawRect(
        color = Color(0xFF3A2818),
        topLeft = Offset(12.dp.toPx(), 12.dp.toPx()),
        size = Size(size.width - 24.dp.toPx(), size.height - 24.dp.toPx()),
        style = Stroke(width = 2.dp.toPx())
    )
}

private fun DrawScope.drawPlanchette(centerX: Float, centerY: Float, isDimmed: Boolean) {
    val path = Path().apply {
        moveTo(centerX, centerY - 65f) // Top tip
        cubicTo(
            centerX + 45f, centerY - 20f,
            centerX + 55f, centerY + 40f,
            centerX + 35f, centerY + 65f
        )
        cubicTo(
            centerX + 15f, centerY + 75f,
            centerX - 15f, centerY + 75f,
            centerX - 35f, centerY + 65f
        )
        cubicTo(
            centerX - 55f, centerY + 40f,
            centerX - 45f, centerY - 20f,
            centerX, centerY - 65f
        )
        close()
    }

    // Planchette body
    drawPath(
        path = path,
        color = if (isDimmed) Color(0xFF806040) else Color(0xFFE8C89E)
    )
    drawPath(
        path = path,
        color = Color(0xFF4A321A),
        style = Stroke(width = 4f)
    )

    // Viewing hole / lens in the middle
    drawCircle(
        color = Color(0x801A120B),
        radius = 22f,
        center = Offset(centerX, centerY + 10f)
    )
    drawCircle(
        color = Color(0xFFD4AF37),
        radius = 22f,
        center = Offset(centerX, centerY + 10f),
        style = Stroke(width = 3f)
    )
}
