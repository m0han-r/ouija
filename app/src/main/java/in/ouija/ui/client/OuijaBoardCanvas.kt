package ouija.app.ui.client

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ouija.app.R
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

val CaptainHowdyFont = FontFamily(
    Font(R.font.captain_howdy, FontWeight.Normal)
)

@Composable
fun OuijaBoardCanvas(
    spellText: String? = null,
    spellSpeedMs: Long = 800L,
    isDimmed: Boolean = false,
    archConfig: BoardArchConfig = BoardArchConfig(),
    onPositionChanged: (Float, Float, String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val vibrator = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun triggerTick() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(12L)
            }
        } catch (_: Exception) {}
    }

    fun triggerArrival() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(30L)
            }
        } catch (_: Exception) {}
    }

    var boardWidth by remember { mutableFloatStateOf(1000f) }
    var boardHeight by remember { mutableFloatStateOf(1000f) }

    val planchetteX = remember { Animatable(500f) }
    val planchetteY = remember { Animatable(550f) }

    val density = LocalDensity.current
    val apertureOffsetY = remember(density) { with(density) { 4.dp.toPx() } }

    var lastHoveredTarget by remember { mutableStateOf<String?>(null) }

    // Candle and Atmospheric Infinite Animations
    val infiniteTransition = rememberInfiniteTransition(label = "OuijaAtmosphere")

    // Left Candle Animations
    val flameSway1 by infiniteTransition.animateFloat(
        initialValue = -2.2f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flameSway1"
    )
    val flameHeight1 by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flameHeight1"
    )
    val candleHaloAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.52f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 380, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "candleHaloAlpha1"
    )

    // Right Candle Animations (Phase-shifted for natural asymmetry)
    val flameSway2 by infiniteTransition.animateFloat(
        initialValue = 2.4f,
        targetValue = -2.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 390, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flameSway2"
    )
    val flameHeight2 by infiniteTransition.animateFloat(
        initialValue = 1.12f,
        targetValue = 0.86f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 290, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flameHeight2"
    )
    val candleHaloAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.50f,
        targetValue = 0.26f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "candleHaloAlpha2"
    )

    // Mist Layers
    val mistProgress1 by infiniteTransition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mistProgress1"
    )
    val mistProgress2 by infiniteTransition.animateFloat(
        initialValue = 1.3f,
        targetValue = -0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mistProgress2"
    )

    // Supernatural idle planchette wobble & breathing
    val idleWobbleX by infiniteTransition.animateFloat(
        initialValue = -2.0f,
        targetValue = 2.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleWobbleX"
    )
    val idleWobbleY by infiniteTransition.animateFloat(
        initialValue = -1.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleWobbleY"
    )
    val idleRotation by infiniteTransition.animateFloat(
        initialValue = -1.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleRotation"
    )

    // Map of letter targets calculated dynamically from archConfig
    val targets = remember(boardWidth, boardHeight, archConfig) {
        val list = mutableListOf<BoardPosition>()

        // Top Header Words (Straight, un-arched)
        list.add(BoardPosition("YES", archConfig.yesNormX, archConfig.yesNormY, rotation = 0f))
        list.add(BoardPosition("NO", archConfig.noNormX, archConfig.noNormY, rotation = 0f))

        val archCenterPxX = boardWidth * 0.5f

        // Arch 1: A - M (Upper Alphabet Arch)
        val arch1 = "ABCDEFGHIJKLM"
        val rx1 = boardWidth * archConfig.arch1RadiusXMultiplier
        val ry1 = boardHeight * archConfig.arch1RadiusYMultiplier
        val centerY1 = boardHeight * archConfig.arch1CenterYMultiplier
        val step1 = (archConfig.arch1EndAngle - archConfig.arch1StartAngle) / (arch1.length - 1)

        arch1.forEachIndexed { i, char ->
            val deg = archConfig.arch1StartAngle + (i * step1)
            val rad = Math.toRadians(deg)
            val pxX = archCenterPxX + (rx1 * cos(rad)).toFloat()
            val pxY = centerY1 + (ry1 * sin(rad)).toFloat()
            val rot = ((deg - 270.0) * archConfig.letterTiltFactor).toFloat()
            list.add(BoardPosition(char.toString(), pxX / boardWidth, pxY / boardHeight, rotation = rot))
        }

        // Arch 2: N - Z (Lower Alphabet Arch)
        val arch2 = "NOPQRSTUVWXYZ"
        val rx2 = boardWidth * archConfig.arch2RadiusXMultiplier
        val ry2 = boardHeight * archConfig.arch2RadiusYMultiplier
        val centerY2 = boardHeight * archConfig.arch2CenterYMultiplier
        val step2 = (archConfig.arch2EndAngle - archConfig.arch2StartAngle) / (arch2.length - 1)

        arch2.forEachIndexed { i, char ->
            val deg = archConfig.arch2StartAngle + (i * step2)
            val rad = Math.toRadians(deg)
            val pxX = archCenterPxX + (rx2 * cos(rad)).toFloat()
            val pxY = centerY2 + (ry2 * sin(rad)).toFloat()
            val rot = ((deg - 270.0) * archConfig.letterTiltFactor).toFloat()
            list.add(BoardPosition(char.toString(), pxX / boardWidth, pxY / boardHeight, rotation = rot))
        }

        // Number Arch: 1 2 3 4 5 6 7 8 9 0
        val numbers = "1234567890"
        val rx3 = boardWidth * archConfig.numbersRadiusXMultiplier
        val ry3 = boardHeight * archConfig.numbersRadiusYMultiplier
        val centerY3 = boardHeight * archConfig.numbersCenterYMultiplier
        val step3 = (archConfig.numbersEndAngle - archConfig.numbersStartAngle) / (numbers.length - 1)

        numbers.forEachIndexed { i, char ->
            val deg = archConfig.numbersStartAngle + (i * step3)
            val rad = Math.toRadians(deg)
            val pxX = archCenterPxX + (rx3 * cos(rad)).toFloat()
            val pxY = centerY3 + (ry3 * sin(rad)).toFloat()
            val rot = ((deg - 270.0) * archConfig.letterTiltFactor).toFloat()
            list.add(BoardPosition(char.toString(), pxX / boardWidth, pxY / boardHeight, rotation = rot))
        }

        // Bottom Left HELLO & Bottom Right GOODBYE (Straight, un-arched)
        list.add(BoardPosition("HELLO", archConfig.helloNormX, archConfig.helloNormY, rotation = 0f))
        list.add(BoardPosition("GOODBYE", archConfig.goodbyeNormX, archConfig.goodbyeNormY, rotation = 0f))

        list
    }

    // Auto-spelling animation handler with eerie non-linear easing and arrival haptics
    LaunchedEffect(spellText) {
        if (!spellText.isNullOrBlank()) {
            val upper = spellText.uppercase()
            val eerieEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.06f)

            for (char in upper) {
                val target = targets.find { it.name == char.toString() }
                    ?: if (char == ' ') targets.find { it.name == "GOODBYE" } else null

                if (target != null) {
                    val targetX = target.normX * boardWidth
                    val targetY = target.normY * boardHeight - apertureOffsetY // Center magnifying aperture over letter

                    val animTime = spellSpeedMs.toInt().coerceAtLeast(300)
                    launch {
                        planchetteX.animateTo(targetX, tween(animTime, easing = eerieEasing))
                    }
                    planchetteY.animateTo(targetY, tween(animTime, easing = eerieEasing))

                    triggerArrival()
                    onPositionChanged(target.normX, target.normY, target.name)
                    delay(400L)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDimmed) Color(0xFF070403) else Color(0xFF0F0805))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    scope.launch {
                        val newX = (planchetteX.value + dragAmount.x).coerceIn(50f, boardWidth - 50f)
                        val newY = (planchetteY.value + dragAmount.y).coerceIn(50f, boardHeight - 50f)
                        planchetteX.snapTo(newX)
                        planchetteY.snapTo(newY)

                        val currentApertureX = newX
                        val currentApertureY = newY + apertureOffsetY

                        val normX = currentApertureX / boardWidth
                        val normY = currentApertureY / boardHeight

                        val nearest = targets.minByOrNull {
                            val tx = it.normX * boardWidth
                            val ty = it.normY * boardHeight
                            val dx = tx - currentApertureX
                            val dy = ty - currentApertureY
                            dx * dx + dy * dy
                        }

                        val nearestDist = nearest?.let {
                            hypot(it.normX * boardWidth - currentApertureX, it.normY * boardHeight - currentApertureY)
                        } ?: Float.MAX_VALUE

                        if (nearestDist < 60f && nearest != null) {
                            if (lastHoveredTarget != nearest.name) {
                                lastHoveredTarget = nearest.name
                                triggerTick()
                            }
                        } else if (nearestDist > 85f) {
                            lastHoveredTarget = null
                        }

                        onPositionChanged(normX, normY, nearest?.name ?: "")
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            boardWidth = size.width
            boardHeight = size.height

            // 1. Antique Mahogany / Walnut Wood Board Layer
            drawWoodBoard(isDimmed)

            // 2. Animated Dual Candles (Bottom Left & Right Flanking the Board)
            val candle1Pos = Offset(size.width * archConfig.candleLeftNormX, size.height * archConfig.candleLeftNormY)
            val candle2Pos = Offset(size.width * archConfig.candleRightNormX, size.height * archConfig.candleRightNormY)

            drawCandle(
                position = candle1Pos,
                flameSway = flameSway1,
                flameHeight = flameHeight1,
                haloAlpha = candleHaloAlpha1,
                isDimmed = isDimmed
            )

            drawCandle(
                position = candle2Pos,
                flameSway = flameSway2,
                flameHeight = flameHeight2,
                haloAlpha = candleHaloAlpha2,
                isDimmed = isDimmed
            )

            // 3. Eerie Drifting Fog / Mist Layer
            drawEerieMist(mistProgress1, mistProgress2, isDimmed)

            // 4. Ornate Celestial Artwork & Borders
            drawCelestialArt(archConfig, isDimmed)

            // 5. Engraved Lettering with Captain Howdy Font rotated along the arches
            val currentPlanchetteX = planchetteX.value + idleWobbleX
            val currentPlanchetteY = planchetteY.value + idleWobbleY
            val apertureX = currentPlanchetteX
            val apertureY = currentPlanchetteY + apertureOffsetY

            drawTargetsAndLetters(
                targets = targets,
                apertureX = apertureX,
                apertureY = apertureY,
                archConfig = archConfig,
                textMeasurer = textMeasurer,
                isDimmed = isDimmed
            )

            // 6. Compact Carved Wooden Planchette with Enlarged Magnifying Glass Lens
            drawPlanchetteWithMagnifier(
                centerX = currentPlanchetteX,
                centerY = currentPlanchetteY,
                rotationDegrees = idleRotation,
                targets = targets,
                archConfig = archConfig,
                textMeasurer = textMeasurer,
                isDimmed = isDimmed
            )

            // 7. Dark Atmospheric Vignette
            drawVignette(isDimmed)
        }
    }
}

// -----------------------------------------------------------------------------
// Canvas Drawing Passes
// -----------------------------------------------------------------------------

private fun DrawScope.drawWoodBoard(isDimmed: Boolean) {
    val centerColor = if (isDimmed) Color(0xFF1B0F09) else Color(0xFF331C10)
    val midColor = if (isDimmed) Color(0xFF100805) else Color(0xFF221108)
    val edgeColor = if (isDimmed) Color(0xFF070302) else Color(0xFF120703)

    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(centerColor, midColor, edgeColor),
            center = center,
            radius = size.maxDimension * 0.7f
        )
    )

    // Subtle horizontal woodgrain texture streaks
    val grainColor = if (isDimmed) Color(0x0C000000) else Color(0x184A2814)
    val grainCount = 14
    for (i in 0..grainCount) {
        val y = size.height * (i.toFloat() / grainCount)
        val path = Path().apply {
            moveTo(0f, y)
            cubicTo(
                size.width * 0.3f, y - 8f + (i % 3) * 6f,
                size.width * 0.7f, y + 8f - (i % 2) * 8f,
                size.width, y
            )
        }
        drawPath(
            path = path,
            color = grainColor,
            style = Stroke(width = (2.5f + (i % 4)).dp.toPx())
        )
    }

    // Outer and Inner Carved Board Borders
    val outerBorder = if (isDimmed) Color(0xFF382312) else Color(0xFF6B4523)
    val innerBorder = if (isDimmed) Color(0xFF23140A) else Color(0xFF4A2F17)
    val goldLine = if (isDimmed) Color(0xFF5A4423) else Color(0xFF9E7C3E)

    // Outer heavy rim
    drawRect(
        color = outerBorder,
        style = Stroke(width = 8.dp.toPx())
    )
    drawRect(
        color = innerBorder,
        topLeft = Offset(8.dp.toPx(), 8.dp.toPx()),
        size = Size(size.width - 16.dp.toPx(), size.height - 16.dp.toPx()),
        style = Stroke(width = 2.dp.toPx())
    )

    // Inset antique gold filigree pinstripe
    val inset = 18.dp.toPx()
    drawRect(
        color = goldLine,
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        style = Stroke(width = 1.5.dp.toPx())
    )

    // Corner Ornaments (4 decorative corner arcs)
    val cornerSize = 24.dp.toPx()
    drawCornerArc(Offset(inset, inset), cornerSize, 0f, goldLine)
    drawCornerArc(Offset(size.width - inset, inset), cornerSize, 90f, goldLine)
    drawCornerArc(Offset(size.width - inset, size.height - inset), cornerSize, 180f, goldLine)
    drawCornerArc(Offset(inset, size.height - inset), cornerSize, 270f, goldLine)
}

private fun DrawScope.drawCornerArc(origin: Offset, arcRadius: Float, rotationAngle: Float, color: Color) {
    withTransform({
        translate(origin.x, origin.y)
        rotate(rotationAngle, pivot = Offset.Zero)
    }) {
        val path = Path().apply {
            moveTo(0f, arcRadius)
            cubicTo(arcRadius * 0.4f, arcRadius * 0.6f, arcRadius * 0.6f, arcRadius * 0.4f, arcRadius, 0f)
        }
        drawPath(path, color = color, style = Stroke(width = 1.5.dp.toPx()))
        drawCircle(color = color, radius = 2.5.dp.toPx(), center = Offset(arcRadius * 0.5f, arcRadius * 0.5f))
    }
}

private fun DrawScope.drawCandle(
    position: Offset,
    flameSway: Float,
    flameHeight: Float,
    haloAlpha: Float,
    isDimmed: Boolean
) {
    val candleWidth = 14.dp.toPx()
    val candleHeight = 52.dp.toPx()
    val haloMultiplier = if (isDimmed) 0.35f else 1.0f

    val baseCenter = position
    val topCenter = Offset(position.x, position.y - candleHeight)

    // 1. Candlelight Halo (Radial gradient cast on the board)
    val haloRadius = size.width * 0.26f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFD54F).copy(alpha = 0.38f * haloAlpha * haloMultiplier),
                Color(0xFFFF9800).copy(alpha = 0.20f * haloAlpha * haloMultiplier),
                Color(0xFFE65100).copy(alpha = 0.08f * haloAlpha * haloMultiplier),
                Color.Transparent
            ),
            center = topCenter - Offset(0f, 15.dp.toPx()),
            radius = haloRadius
        ),
        blendMode = BlendMode.Screen
    )

    // 2. Brass Candlestick Stand Base
    val standWidth = 28.dp.toPx()
    val standHeight = 10.dp.toPx()
    drawOval(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF8B6B32), Color(0xFF5A411B), Color(0xFF33220A)),
            startY = baseCenter.y - standHeight / 2f,
            endY = baseCenter.y + standHeight / 2f
        ),
        topLeft = Offset(baseCenter.x - standWidth / 2f, baseCenter.y - standHeight / 2f),
        size = Size(standWidth, standHeight)
    )

    // 3. Candle Pillar Body with Wax Gradient
    val pillarLeft = topCenter.x - candleWidth / 2f
    val pillarTop = topCenter.y
    val pillarRect = Size(candleWidth, candleHeight)

    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color(0xFFEFE7D8), Color(0xFFFFFBF2), Color(0xFFD5C4AA), Color(0xFFA8957C)),
            startX = pillarLeft,
            endX = pillarLeft + candleWidth
        ),
        topLeft = Offset(pillarLeft, pillarTop),
        size = pillarRect
    )

    // Dripping wax trails on the side of the candle
    val dripPath = Path().apply {
        moveTo(pillarLeft + 2.dp.toPx(), pillarTop)
        lineTo(pillarLeft + 2.dp.toPx(), pillarTop + 16.dp.toPx())
        cubicTo(
            pillarLeft + 2.dp.toPx(), pillarTop + 20.dp.toPx(),
            pillarLeft + 5.dp.toPx(), pillarTop + 20.dp.toPx(),
            pillarLeft + 5.dp.toPx(), pillarTop + 16.dp.toPx()
        )
        lineTo(pillarLeft + 5.dp.toPx(), pillarTop)
    }
    drawPath(dripPath, color = Color(0xFFFFFBF2))

    // Curved wax rim at the top of the candle
    drawOval(
        color = Color(0xFFDFD1BC),
        topLeft = Offset(pillarLeft, pillarTop - 3.dp.toPx()),
        size = Size(candleWidth, 6.dp.toPx())
    )

    // 4. Black Charred Wick
    val wickStart = Offset(topCenter.x, topCenter.y)
    val wickEnd = Offset(topCenter.x + flameSway * 0.5f, topCenter.y - 7.dp.toPx())
    drawLine(
        color = Color(0xFF1E150F),
        start = wickStart,
        end = wickEnd,
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
    )

    // 5. Animated Flickering Flame
    val flameBase = wickEnd
    val flameTip = Offset(
        topCenter.x + flameSway * 3.5f,
        flameBase.y - (20.dp.toPx() * flameHeight)
    )
    val flameHalfWidth = 6.dp.toPx()

    // Outer warm amber flame
    val outerFlamePath = Path().apply {
        moveTo(flameTip.x, flameTip.y)
        cubicTo(
            flameBase.x + flameHalfWidth * 1.5f, flameBase.y - 8.dp.toPx(),
            flameBase.x + flameHalfWidth, flameBase.y,
            flameBase.x, flameBase.y
        )
        cubicTo(
            flameBase.x - flameHalfWidth, flameBase.y,
            flameBase.x - flameHalfWidth * 1.5f, flameBase.y - 8.dp.toPx(),
            flameTip.x, flameTip.y
        )
        close()
    }
    drawPath(
        path = outerFlamePath,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFFF5722), Color(0xFFFF9800), Color(0xFFFFC107)),
            startY = flameTip.y,
            endY = flameBase.y
        )
    )

    // Inner radiant yellow/white core
    val innerFlameTip = Offset(
        topCenter.x + flameSway * 2f,
        flameBase.y - (14.dp.toPx() * flameHeight)
    )
    val innerFlamePath = Path().apply {
        moveTo(innerFlameTip.x, innerFlameTip.y)
        cubicTo(
            flameBase.x + flameHalfWidth * 0.7f, flameBase.y - 4.dp.toPx(),
            flameBase.x + flameHalfWidth * 0.5f, flameBase.y,
            flameBase.x, flameBase.y
        )
        cubicTo(
            flameBase.x - flameHalfWidth * 0.5f, flameBase.y,
            flameBase.x - flameHalfWidth * 0.7f, flameBase.y - 4.dp.toPx(),
            innerFlameTip.x, innerFlameTip.y
        )
        close()
    }
    drawPath(
        path = innerFlamePath,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFFFFDE7), Color(0xFFFFF59D)),
            startY = innerFlameTip.y,
            endY = flameBase.y
        )
    )

    // Blue flame base droplet
    drawCircle(
        color = Color(0xBB3949AB),
        radius = 2.5.dp.toPx(),
        center = Offset(flameBase.x, flameBase.y - 1.5.dp.toPx())
    )
}

private fun DrawScope.drawEerieMist(mistProgress1: Float, mistProgress2: Float, isDimmed: Boolean) {
    val mistAlpha = if (isDimmed) 0.035f else 0.07f

    // Mist Layer 1: Left to right
    val m1X = size.width * mistProgress1
    val m1Y = size.height * 0.48f + (sin(mistProgress1 * 6.28f) * 20f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF88B0B8).copy(alpha = mistAlpha), Color.Transparent),
            center = Offset(m1X, m1Y),
            radius = size.width * 0.45f
        ),
        blendMode = BlendMode.Screen
    )

    // Mist Layer 2: Right to left
    val m2X = size.width * mistProgress2
    val m2Y = size.height * 0.65f + (cos(mistProgress2 * 6.28f) * 25f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF7A9FA6).copy(alpha = mistAlpha * 0.85f), Color.Transparent),
            center = Offset(m2X, m2Y),
            radius = size.width * 0.50f
        ),
        blendMode = BlendMode.Screen
    )
}

private fun DrawScope.drawCelestialArt(archConfig: BoardArchConfig, isDimmed: Boolean) {
    val gold = if (isDimmed) Color(0xFF6B532C) else Color(0xFFB58E4A)
    val darkCarve = Color(0x770E0704)

    // Top-Left Celestial: Crescent Moon with Star (aligned straight with left candle)
    val moonCenter = Offset(size.width * archConfig.candleLeftNormX, size.height * archConfig.moonNormY)
    val moonRadius = 18.dp.toPx()

    // Engraved shadow
    drawCircle(color = darkCarve, radius = moonRadius, center = moonCenter + Offset(1.5f, 2f))
    drawCircle(color = gold, radius = moonRadius, center = moonCenter)
    val cutoutColor = if (isDimmed) Color(0xFF1B0F09) else Color(0xFF28150C)
    drawCircle(color = cutoutColor, radius = moonRadius * 0.85f, center = moonCenter + Offset(moonRadius * 0.45f, -moonRadius * 0.15f))

    // Tiny 4-pointed star beside moon
    val starCenter = moonCenter + Offset(moonRadius * 0.9f, moonRadius * 0.6f)
    val starSize = 5.dp.toPx()
    drawLine(gold, starCenter - Offset(starSize, 0f), starCenter + Offset(starSize, 0f), strokeWidth = 1.5f)
    drawLine(gold, starCenter - Offset(0f, starSize), starCenter + Offset(0f, starSize), strokeWidth = 1.5f)

    // Top-Right Celestial: Radiant Sun (aligned straight with right candle)
    val sunCenter = Offset(size.width * archConfig.candleRightNormX, size.height * archConfig.sunNormY)
    val sunRadius = 14.dp.toPx()

    drawCircle(color = darkCarve, radius = sunRadius, center = sunCenter + Offset(1.5f, 2f))
    drawCircle(color = gold, radius = sunRadius, center = sunCenter, style = Stroke(width = 2.dp.toPx()))
    drawCircle(color = gold, radius = sunRadius * 0.5f, center = sunCenter)

    // 8 Sun rays
    for (i in 0 until 8) {
        val angle = Math.toRadians(i * 45.0)
        val r1 = sunRadius * 1.3f
        val r2 = sunRadius * 1.8f
        val p1 = sunCenter + Offset((r1 * cos(angle)).toFloat(), (r1 * sin(angle)).toFloat())
        val p2 = sunCenter + Offset((r2 * cos(angle)).toFloat(), (r2 * sin(angle)).toFloat())
        drawLine(gold, p1, p2, strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawTargetsAndLetters(
    targets: List<BoardPosition>,
    apertureX: Float,
    apertureY: Float,
    archConfig: BoardArchConfig,
    textMeasurer: TextMeasurer,
    isDimmed: Boolean
) {
    val hoverRadius = 70f

    val baseGold = if (isDimmed) Color(0xFF806236) else Color(0xFFC7A15C)
    val glowGold = Color(0xFFFFF6D1)
    val engravedDark = Color(0xFF0A0503)

    targets.forEach { pos ->
        val targetX = pos.normX * size.width
        val targetY = pos.normY * size.height

        val dist = hypot(targetX - apertureX, targetY - apertureY)
        val proximity = (1f - (dist / hoverRadius)).coerceIn(0f, 1f)

        // Spectral golden bloom when aperture is near letter
        if (proximity > 0f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFD54F).copy(alpha = 0.55f * proximity),
                        Color(0xFFE67E22).copy(alpha = 0.25f * proximity),
                        Color.Transparent
                    ),
                    center = Offset(targetX, targetY),
                    radius = 38.dp.toPx() * (0.8f + proximity * 0.4f)
                ),
                blendMode = BlendMode.Screen
            )
        }

        val isWord = pos.name == "YES" || pos.name == "NO" || pos.name == "HELLO" || pos.name == "GOODBYE"
        val isNumber = pos.name in "1234567890"
        val baseFontSize = when {
            pos.name == "YES" || pos.name == "NO" -> archConfig.headerFontSizeSp.sp
            pos.name == "HELLO" || pos.name == "GOODBYE" -> archConfig.footerFontSizeSp.sp
            isNumber -> archConfig.numberFontSizeSp.sp
            else -> archConfig.letterFontSizeSp.sp // Configurable A-Z alphabet size
        }

        val dynamicFontSize = baseFontSize * (1f + proximity * 0.10f)
        val letterColor = lerp(baseGold, glowGold, proximity)

        // 1. Engraved Shadow Text using Captain Howdy Font
        val shadowStyle = TextStyle(
            color = engravedDark,
            fontSize = dynamicFontSize,
            fontWeight = FontWeight.Normal,
            fontFamily = CaptainHowdyFont
        )
        val shadowLayout = textMeasurer.measure(pos.name, shadowStyle)

        // 2. Foreground Letter Text using Captain Howdy Font
        val textStyle = TextStyle(
            color = letterColor,
            fontSize = dynamicFontSize,
            fontWeight = FontWeight.Normal,
            fontFamily = CaptainHowdyFont
        )
        val textLayout = textMeasurer.measure(pos.name, textStyle)

        // Draw rotated with arch tangent
        withTransform({
            translate(targetX, targetY)
            rotate(pos.rotation, pivot = Offset.Zero)
        }) {
            drawText(
                shadowLayout,
                topLeft = Offset(
                    -shadowLayout.size.width / 2f + 1.8f,
                    -shadowLayout.size.height / 2f + 2.2f
                )
            )
            drawText(
                textLayout,
                topLeft = Offset(
                    -textLayout.size.width / 2f,
                    -textLayout.size.height / 2f
                )
            )
        }
    }
}

private fun DrawScope.drawPlanchetteWithMagnifier(
    centerX: Float,
    centerY: Float,
    rotationDegrees: Float,
    targets: List<BoardPosition>,
    archConfig: BoardArchConfig,
    textMeasurer: TextMeasurer,
    isDimmed: Boolean
) {
    withTransform({
        translate(centerX, centerY)
        rotate(rotationDegrees, pivot = Offset.Zero)
    }) {
        // Compact carved wooden Ouija heart/triangle geometry (76dp wide x 98dp tall)
        val pTipY = -54.dp.toPx()
        val pShoulderX = 35.dp.toPx()
        val pShoulderY = -12.dp.toPx()
        val pHipX = 38.dp.toPx()
        val pHipY = 18.dp.toPx()
        val pLobeX = 27.dp.toPx()
        val pLobeY = 46.dp.toPx()
        val pBottomIndentY = 35.dp.toPx()

        val planchettePath = Path().apply {
            moveTo(0f, pTipY)
            // Right lobe curve
            cubicTo(pShoulderX * 0.65f, pTipY + 14.dp.toPx(), pShoulderX, pShoulderY, pHipX, pHipY)
            cubicTo(pHipX + 2.dp.toPx(), pHipY + 14.dp.toPx(), pLobeX + 5.dp.toPx(), pLobeY, pLobeX, pLobeY)
            cubicTo(pLobeX - 12.dp.toPx(), pLobeY, 12.dp.toPx(), pBottomIndentY + 4.dp.toPx(), 0f, pBottomIndentY)
            // Left lobe curve
            cubicTo(-12.dp.toPx(), pBottomIndentY + 4.dp.toPx(), -pLobeX + 12.dp.toPx(), pLobeY, -pLobeX, pLobeY)
            cubicTo(-pLobeX - 5.dp.toPx(), pLobeY, -pHipX - 2.dp.toPx(), pHipY + 14.dp.toPx(), -pHipX, pHipY)
            cubicTo(-pShoulderX, pShoulderY, -pShoulderX * 0.65f, pTipY + 14.dp.toPx(), 0f, pTipY)
            close()
        }

        // 1. Planchette Elevated Drop-Shadow
        val shadowOffset = Offset(8.dp.toPx(), 12.dp.toPx())
        withTransform({
            translate(shadowOffset.x, shadowOffset.y)
        }) {
            drawPath(path = planchettePath, color = Color(0x66000000))
            drawPath(path = planchettePath, color = Color(0x33000000), style = Stroke(width = 5.dp.toPx()))
        }

        // 2. Planchette Body: Rich polished vintage walnut wood gradient
        val bodyBrush = if (isDimmed) {
            Brush.radialGradient(
                colors = listOf(Color(0xFF5A3A1C), Color(0xFF3E240F), Color(0xFF221105)),
                center = Offset(0f, -6.dp.toPx()),
                radius = 60.dp.toPx()
            )
        } else {
            Brush.radialGradient(
                colors = listOf(Color(0xFF9E6534), Color(0xFF75451D), Color(0xFF4A2A10)),
                center = Offset(0f, -10.dp.toPx()),
                radius = 65.dp.toPx()
            )
        }
        drawPath(path = planchettePath, brush = bodyBrush)

        // Subtle woodgrain texture streaks across the planchette
        val grainColor = if (isDimmed) Color(0x181B0E05) else Color(0x20FFFFFF)
        drawLine(grainColor, Offset(-24.dp.toPx(), -18.dp.toPx()), Offset(24.dp.toPx(), -21.dp.toPx()), strokeWidth = 1.dp.toPx())
        drawLine(grainColor, Offset(-32.dp.toPx(), 8.dp.toPx()), Offset(32.dp.toPx(), 5.dp.toPx()), strokeWidth = 1.dp.toPx())
        drawLine(grainColor, Offset(-25.dp.toPx(), 26.dp.toPx()), Offset(25.dp.toPx(), 24.dp.toPx()), strokeWidth = 1.dp.toPx())

        // 3. Beveled outer edge
        drawPath(
            path = planchettePath,
            color = if (isDimmed) Color(0xFF1E1005) else Color(0xFF361C0A),
            style = Stroke(width = 3.dp.toPx())
        )

        // 4. Inset carved groove contour with subtle golden filigree
        val innerContour = Path().apply {
            val s = 0.82f
            moveTo(0f, pTipY * s + 5.dp.toPx())
            cubicTo(pShoulderX * 0.65f * s, (pTipY + 14.dp.toPx()) * s, pShoulderX * s, pShoulderY * s, pHipX * s, pHipY * s)
            cubicTo((pHipX + 2.dp.toPx()) * s, (pHipY + 14.dp.toPx()) * s, (pLobeX + 5.dp.toPx()) * s, pLobeY * s, pLobeX * s, pLobeY * s)
            cubicTo((pLobeX - 12.dp.toPx()) * s, pLobeY * s, 12.dp.toPx() * s, (pBottomIndentY + 4.dp.toPx()) * s, 0f, pBottomIndentY * s)
            cubicTo(-12.dp.toPx() * s, (pBottomIndentY + 4.dp.toPx()) * s, (-pLobeX + 12.dp.toPx()) * s, pLobeY * s, -pLobeX * s, pLobeY * s)
            cubicTo((-pLobeX - 5.dp.toPx()) * s, pLobeY * s, (-pHipX - 2.dp.toPx()) * s, (pHipY + 14.dp.toPx()) * s, -pHipX * s, pHipY * s)
            cubicTo(-pShoulderX * s, pShoulderY * s, -pShoulderX * 0.65f * s, (pTipY + 14.dp.toPx()) * s, 0f, pTipY * s + 5.dp.toPx())
            close()
        }
        drawPath(
            path = innerContour,
            color = if (isDimmed) Color(0x44261408) else Color(0x66C9A25D),
            style = Stroke(width = 1.2.dp.toPx())
        )

        // 5. Three Pegged Sliding Brass Feet
        val pegColor = if (isDimmed) Color(0xFF7A5C28) else Color(0xFFD4AF37)
        val pegHighlight = Color(0xFFFFF0B3)
        val pegRadius = 3.dp.toPx()
        val pegPositions = listOf(
            Offset(0f, -38.dp.toPx()),
            Offset(-22.dp.toPx(), 34.dp.toPx()),
            Offset(22.dp.toPx(), 34.dp.toPx())
        )
        pegPositions.forEach { peg ->
            drawCircle(Color(0x77000000), radius = pegRadius + 1.dp.toPx(), center = peg + Offset(1f, 1.5f))
            drawCircle(pegColor, radius = pegRadius, center = peg)
            drawCircle(pegHighlight, radius = 1.0.dp.toPx(), center = peg - Offset(0.8f, 0.8f))
        }

        // ---------------------------------------------------------------------
        // 6. ENLARGED MAGNIFYING GLASS APERTURE (Radius = 24dp, Diameter = 48dp)
        // ---------------------------------------------------------------------
        val lensCenter = Offset(0f, 4.dp.toPx())
        val lensRadius = 24.dp.toPx()

        val lensCirclePath = Path().apply {
            addOval(Rect(center = lensCenter, radius = lensRadius))
        }

        // Clip everything inside the lens aperture circle for optical magnification
        clipPath(lensCirclePath) {
            // Lens glass dark base with green/cyan tint
            drawCircle(
                color = Color(0xFF140D07),
                radius = lensRadius,
                center = lensCenter
            )

            // Calculate lens center in board coordinates
            val apertureWorldX = centerX
            val apertureWorldY = centerY + 4.dp.toPx()

            // Find all targets within the lens field of view
            targets.forEach { pos ->
                val targetWorldX = pos.normX * size.width
                val targetWorldY = pos.normY * size.height

                val dx = targetWorldX - apertureWorldX
                val dy = targetWorldY - apertureWorldY
                val dist = hypot(dx, dy)

                // If within lens viewing proximity
                if (dist < lensRadius * 1.5f) {
                    val magnifiedFontSize = when {
                        pos.name == "YES" || pos.name == "NO" -> archConfig.magnifiedHeaderFontSizeSp.sp
                        pos.name == "HELLO" -> archConfig.magnifiedFooterFontSizeSp.sp
                        pos.name == "GOODBYE" -> (archConfig.magnifiedFooterFontSizeSp * 0.78f).sp
                        pos.name in "1234567890" -> archConfig.magnifiedNumberFontSizeSp.sp
                        else -> archConfig.magnifiedLetterFontSizeSp.sp // Contained cleanly inside the 48dp circle with generous margin
                    }

                    // Optical magnification position relative to lens center
                    val magnifiedPos = lensCenter + Offset(dx * 0.7f, dy * 0.7f)

                    withTransform({
                        translate(magnifiedPos.x, magnifiedPos.y)
                        rotate(pos.rotation, pivot = Offset.Zero)
                    }) {
                        // Ethereal golden glow
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xAAFFE082), Color(0x44FFB300), Color.Transparent),
                                center = Offset.Zero,
                                radius = lensRadius * 1.2f
                            ),
                            center = Offset.Zero,
                            radius = lensRadius * 1.2f
                        )

                        // Measure styles
                        val magStyle = TextStyle(
                            color = Color(0xFFFFFDE7),
                            fontSize = magnifiedFontSize,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CaptainHowdyFont
                        )
                        val magLayout = textMeasurer.measure(pos.name, magStyle)

                        val shadowLayout = textMeasurer.measure(
                            pos.name,
                            magStyle.copy(color = Color(0xCC000000))
                        )
                        drawText(
                            textLayoutResult = shadowLayout,
                            topLeft = Offset(-shadowLayout.size.width / 2f + 2f, -shadowLayout.size.height / 2f + 2f)
                        )
                        drawText(
                            textLayoutResult = magLayout,
                            topLeft = Offset(-magLayout.size.width / 2f, -magLayout.size.height / 2f)
                        )
                    }
                }
            }

            // Spherical glass convex shading (radial gradient darker at lens rim)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x11FFFFFF),
                        Color(0x221B3A32),
                        Color(0x660B1C17),
                        Color(0x99050C0A)
                    ),
                    center = lensCenter,
                    radius = lensRadius
                ),
                radius = lensRadius,
                center = lensCenter
            )
        }

        // 7. Glass Glare Reflection (Top-left arc highlight)
        val glarePath = Path().apply {
            moveTo(lensCenter.x - lensRadius * 0.70f, lensCenter.y - lensRadius * 0.20f)
            cubicTo(
                lensCenter.x - lensRadius * 0.60f, lensCenter.y - lensRadius * 0.70f,
                lensCenter.x - lensRadius * 0.20f, lensCenter.y - lensRadius * 0.80f,
                lensCenter.x + lensRadius * 0.20f, lensCenter.y - lensRadius * 0.70f
            )
        }
        drawPath(
            path = glarePath,
            color = Color(0x77FFFFFF),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // 8. Heavy Brass Bezel Rim surrounding the enlarged lens
        drawCircle(
            color = if (isDimmed) Color(0xFF5E431E) else Color(0xFF9E7736),
            radius = lensRadius + 1.8.dp.toPx(),
            center = lensCenter,
            style = Stroke(width = 4.dp.toPx())
        )
        // Inner golden highlight ring
        drawCircle(
            color = if (isDimmed) Color(0xFF8A652B) else Color(0xFFE8C878),
            radius = lensRadius,
            center = lensCenter,
            style = Stroke(width = 1.4.dp.toPx())
        )

        // Fine golden reticle crosshair in the lens
        val reticleColor = Color(0x44D4AF37)
        drawLine(reticleColor, lensCenter - Offset(10.dp.toPx(), 0f), lensCenter + Offset(10.dp.toPx(), 0f), strokeWidth = 1f)
        drawLine(reticleColor, lensCenter - Offset(0f, 10.dp.toPx()), lensCenter + Offset(0f, 10.dp.toPx()), strokeWidth = 1f)
    }
}

private fun DrawScope.drawVignette(isDimmed: Boolean) {
    val cornerBlack = if (isDimmed) Color(0xFF030202) else Color(0xF2050302)
    val midShadow = if (isDimmed) Color(0xCC050302) else Color(0x88080503)

    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                midShadow,
                cornerBlack
            ),
            center = center,
            radius = size.maxDimension * 0.74f
        ),
        blendMode = BlendMode.Multiply
    )
}
