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

            // 2. Old Wood Deep Cuts, Gouges & Stress Fractures
            drawOldWoodDamageAndCuts(archConfig.woodDamageIntensity, isDimmed)

            // 3. Realistic Visceral Blood Splash & Splatter Effect (Impact Bursts, Fling Spatter, Drips)
            drawBloodSplashEffect(archConfig.bloodSplashIntensity, isDimmed)

            // 4. Ritual Candles with Static Wax-Blood Drips & Flickering Flames
            val candle1Pos = Offset(size.width * archConfig.candleLeftNormX, size.height * archConfig.candleLeftNormY)
            val candle2Pos = Offset(size.width * archConfig.candleRightNormX, size.height * archConfig.candleRightNormY)

            drawCandle(
                position = candle1Pos,
                flameSway = flameSway1,
                flameHeight = flameHeight1,
                haloAlpha = candleHaloAlpha1,
                bloodIntensity = archConfig.candleBloodIntensity,
                isDimmed = isDimmed
            )

            drawCandle(
                position = candle2Pos,
                flameSway = flameSway2,
                flameHeight = flameHeight2,
                haloAlpha = candleHaloAlpha2,
                bloodIntensity = archConfig.candleBloodIntensity,
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

private fun DrawScope.drawOldWoodDamageAndCuts(intensity: Float, isDimmed: Boolean) {
    if (intensity <= 0.01f) return
    val alphaMul = (if (isDimmed) 0.65f else 1.0f) * intensity.coerceIn(0f, 1f)

    // 1. Weathered Wood Grain Fractures (Meandering natural splits that follow wood grain)
    drawMeanderingGrainSplit(
        start = Offset(size.width * 0.07f, size.height * 0.38f),
        length = size.width * 0.22f,
        yVariance = 2.5.dp.toPx(),
        alphaMul = alphaMul
    )
    drawMeanderingGrainSplit(
        start = Offset(size.width * 0.35f, size.height * 0.65f),
        length = size.width * 0.32f,
        yVariance = 3.0.dp.toPx(),
        alphaMul = alphaMul
    )
    drawMeanderingGrainSplit(
        start = Offset(size.width * 0.70f, size.height * 0.44f),
        length = size.width * 0.24f,
        yVariance = 2.0.dp.toPx(),
        alphaMul = alphaMul
    )
    drawMeanderingGrainSplit(
        start = Offset(size.width * 0.16f, size.height * 0.78f),
        length = size.width * 0.28f,
        yVariance = 2.5.dp.toPx(),
        alphaMul = alphaMul
    )

    // 2. Chiseled Blade Gouges (Curved, razor-sharp tapered incisions with deep shadow & torn wood grain highlights)
    // Claw / Triple Slash in Upper-Left (between YES and Arch 1)
    drawTaperedBladeGouge(
        start = Offset(size.width * 0.11f, size.height * 0.22f),
        control = Offset(size.width * 0.17f, size.height * 0.28f),
        end = Offset(size.width * 0.23f, size.height * 0.33f),
        maxThicknessDp = 3.2f,
        alphaMul = alphaMul
    )
    drawTaperedBladeGouge(
        start = Offset(size.width * 0.13f, size.height * 0.20f),
        control = Offset(size.width * 0.19f, size.height * 0.26f),
        end = Offset(size.width * 0.25f, size.height * 0.31f),
        maxThicknessDp = 2.4f,
        alphaMul = alphaMul
    )
    drawTaperedBladeGouge(
        start = Offset(size.width * 0.10f, size.height * 0.25f),
        control = Offset(size.width * 0.15f, size.height * 0.30f),
        end = Offset(size.width * 0.20f, size.height * 0.34f),
        maxThicknessDp = 1.9f,
        alphaMul = alphaMul
    )

    // Deep Slashed Knife Gouge below NO (Upper-Right)
    drawTaperedBladeGouge(
        start = Offset(size.width * 0.88f, size.height * 0.24f),
        control = Offset(size.width * 0.82f, size.height * 0.32f),
        end = Offset(size.width * 0.77f, size.height * 0.39f),
        maxThicknessDp = 3.6f,
        alphaMul = alphaMul
    )
    // Crossing scratch across Cut 2
    drawTaperedBladeGouge(
        start = Offset(size.width * 0.78f, size.height * 0.29f),
        control = Offset(size.width * 0.83f, size.height * 0.33f),
        end = Offset(size.width * 0.86f, size.height * 0.38f),
        maxThicknessDp = 1.8f,
        alphaMul = alphaMul
    )

    // Blade drag scoring across lower margin (between HELLO and numbers)
    drawTaperedBladeGouge(
        start = Offset(size.width * 0.27f, size.height * 0.81f),
        control = Offset(size.width * 0.34f, size.height * 0.84f),
        end = Offset(size.width * 0.42f, size.height * 0.87f),
        maxThicknessDp = 2.8f,
        alphaMul = alphaMul
    )

    // Knife nick near GOODBYE
    drawTaperedBladeGouge(
        start = Offset(size.width * 0.74f, size.height * 0.80f),
        control = Offset(size.width * 0.71f, size.height * 0.83f),
        end = Offset(size.width * 0.68f, size.height * 0.86f),
        maxThicknessDp = 2.2f,
        alphaMul = alphaMul
    )

    // 3. Subtle edge gouges on the inner rim
    val edgeNicks = listOf(
        Pair(Offset(size.width * 0.06f, size.height * 0.48f), Offset(size.width * 0.08f, size.height * 0.50f)),
        Pair(Offset(size.width * 0.94f, size.height * 0.52f), Offset(size.width * 0.92f, size.height * 0.54f)),
        Pair(Offset(size.width * 0.52f, size.height * 0.20f), Offset(size.width * 0.54f, size.height * 0.21f)),
        Pair(Offset(size.width * 0.48f, size.height * 0.89f), Offset(size.width * 0.50f, size.height * 0.90f))
    )
    edgeNicks.forEach { (p1, p2) ->
        drawLine(
            color = Color(0xBB0A0402).copy(alpha = 0.75f * alphaMul),
            start = p1,
            end = p2,
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0x882E0509).copy(alpha = 0.65f * alphaMul),
            start = p1,
            end = p2,
            strokeWidth = 0.9.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawMeanderingGrainSplit(
    start: Offset,
    length: Float,
    yVariance: Float,
    alphaMul: Float
) {
    val steps = 8
    val dx = length / steps
    val shadowPath = Path()
    shadowPath.moveTo(start.x, start.y)

    var currX = start.x
    var currY = start.y
    val yOffsets = floatArrayOf(0f, 0.45f, -0.6f, 0.75f, -0.35f, 0.65f, -0.5f, 0.3f, 0f)

    for (i in 1..steps) {
        val nextX = start.x + i * dx
        val nextY = start.y + yOffsets[i % yOffsets.size] * yVariance
        val cpX = (currX + nextX) / 2f
        val cpY = currY + (yOffsets[(i + 2) % yOffsets.size] * yVariance * 0.4f)
        shadowPath.quadraticTo(cpX, cpY, nextX, nextY)
        currX = nextX
        currY = nextY
    }

    val splitColor = Color(0x33000000).copy(alpha = 0.35f * alphaMul)
    val highlightColor = Color(0x18FFFFFF).copy(alpha = 0.20f * alphaMul)

    drawPath(shadowPath, color = splitColor, style = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round))
    withTransform({ translate(0f, 1f) }) {
        drawPath(shadowPath, color = highlightColor, style = Stroke(width = 0.7.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawTaperedBladeGouge(
    start: Offset,
    control: Offset,
    end: Offset,
    maxThicknessDp: Float,
    alphaMul: Float
) {
    val steps = 14
    val maxThicknessPx = maxThicknessDp.dp.toPx()
    val leftPts = mutableListOf<Offset>()
    val rightPts = mutableListOf<Offset>()
    val centerPts = mutableListOf<Offset>()

    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val omt = 1f - t
        val bx = omt * omt * start.x + 2f * omt * t * control.x + t * t * end.x
        val by = omt * omt * start.y + 2f * omt * t * control.y + t * t * end.y
        centerPts.add(Offset(bx, by))

        val tx = 2f * omt * (control.x - start.x) + 2f * t * (end.x - control.x)
        val ty = 2f * omt * (control.y - start.y) + 2f * t * (end.y - control.y)
        val len = hypot(tx, ty).coerceAtLeast(0.001f)
        val nx = -ty / len
        val ny = tx / len

        val taper = sin(t * Math.PI.toFloat())
        val w = maxThicknessPx * taper * (0.35f + 0.65f * taper) * 0.5f

        leftPts.add(Offset(bx + nx * w, by + ny * w))
        rightPts.add(Offset(bx - nx * w, by - ny * w))
    }

    val gougePath = Path().apply {
        moveTo(leftPts.first().x, leftPts.first().y)
        for (i in 1 until leftPts.size) {
            lineTo(leftPts[i].x, leftPts[i].y)
        }
        for (i in rightPts.indices.reversed()) {
            lineTo(rightPts[i].x, rightPts[i].y)
        }
        close()
    }

    // 1. Deep carved furrow shadow
    val shadowColor = Color(0xF2060201).copy(alpha = 0.90f * alphaMul)
    drawPath(gougePath, color = shadowColor)

    // 2. Dried clotted blood crust seeped into the groove center
    val bloodInCut = Color(0xDD280306).copy(alpha = 0.82f * alphaMul)
    val bloodCenterPath = Path().apply {
        moveTo(centerPts.first().x, centerPts.first().y)
        for (i in 1 until centerPts.size) {
            lineTo(centerPts[i].x, centerPts[i].y)
        }
    }
    drawPath(bloodCenterPath, color = bloodInCut, style = Stroke(width = maxThicknessPx * 0.5f, cap = StrokeCap.Round))

    // 3. Torn raw wood fiber highlight along the top chiseled lip catching candlelight
    val highlightPath = Path().apply {
        moveTo(leftPts.first().x, leftPts.first().y)
        for (i in 1 until leftPts.size) {
            lineTo(leftPts[i].x, leftPts[i].y)
        }
    }
    val splinterColor = Color(0x40DEB670).copy(alpha = 0.45f * alphaMul)
    drawPath(highlightPath, color = splinterColor, style = Stroke(width = 0.8.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.drawBloodSplashEffect(intensity: Float, isDimmed: Boolean) {
    if (intensity <= 0.01f) return
    val alphaMul = (if (isDimmed) 0.70f else 1.0f) * intensity.coerceIn(0f, 1f)

    // -------------------------------------------------------------------------
    // Stain 1: Organic Clotted Impact Pool (Lower-Left: between Arch 2 curve and Candle)
    // -------------------------------------------------------------------------
    val splash1Center = Offset(size.width * 0.14f, size.height * 0.65f)
    drawOrganicBloodStain(
        center = splash1Center,
        baseRadius = 22.dp.toPx(),
        seed = 1.4f,
        alphaMul = alphaMul
    )

    // Organic satellite droplets thrown from impact 1
    val splash1Satellites = listOf(
        Pair(Offset(-28f, -22f), 2.6f),
        Pair(Offset(30f, -18f), 2.4f),
        Pair(Offset(38f, 14f), 1.9f),
        Pair(Offset(28f, 26f), 2.2f),
        Pair(Offset(-32f, 14f), 2.1f),
        Pair(Offset(-18f, -34f), 1.6f),
        Pair(Offset(22f, -38f), 1.7f),
        Pair(Offset(48f, -6f), 1.4f),
        Pair(Offset(-42f, -8f), 1.7f),
        Pair(Offset(14f, 44f), 2.0f),
        Pair(Offset(-14f, 32f), 1.5f)
    )
    drawMicroSpatterMist(splash1Center, splash1Satellites, alphaMul)

    // -------------------------------------------------------------------------
    // Stain 2: Directional Slasher Cast-Off / Fling Spatter
    // (Upper-Right: above Arch 2, angling down-right towards margin)
    // -------------------------------------------------------------------------
    val flingOrigin = Offset(size.width * 0.74f, size.height * 0.22f)
    val flingAngle = 0.64f // ~36.6 degrees down-right
    val flingDistances = listOf(14f, 36f, 62f, 88f, 114f)
    val flingSizes = listOf(Pair(4.2f, 2.0f), Pair(3.4f, 1.7f), Pair(2.8f, 1.4f), Pair(2.2f, 1.2f), Pair(1.7f, 1.0f))

    for (i in flingDistances.indices) {
        val dist = flingDistances[i].dp.toPx()
        val (len, wid) = flingSizes[i]
        val dropCenter = flingOrigin + Offset(
            dist * cos(flingAngle.toDouble()).toFloat(),
            dist * sin(flingAngle.toDouble()).toFloat()
        )
        drawDirectionalSpatter(
            center = dropCenter,
            angleRad = flingAngle,
            lengthDp = len,
            widthDp = wid,
            alphaMul = alphaMul
        )
    }

    // Fine spray mist droplets flanking the directional fling
    val flingMist = listOf(
        Pair(Offset(-12f, -6f), 1.3f),
        Pair(Offset(10f, 2f), 1.5f),
        Pair(Offset(32f, 10f), 1.4f),
        Pair(Offset(20f, 24f), 1.6f),
        Pair(Offset(44f, 22f), 1.3f),
        Pair(Offset(58f, 24f), 1.2f),
        Pair(Offset(66f, 38f), 1.4f),
        Pair(Offset(80f, 36f), 1.2f),
        Pair(Offset(94f, 46f), 1.1f),
        Pair(Offset(104f, 54f), 0.9f)
    )
    drawMicroSpatterMist(flingOrigin, flingMist, alphaMul)

    // -------------------------------------------------------------------------
    // Stain 3: Dried Blood Stain below the Numbers Row
    // (Lower-Center: roughly normX = 0.46f, normY = 0.83f)
    // -------------------------------------------------------------------------
    val splash3Center = Offset(size.width * 0.46f, size.height * 0.83f)
    drawOrganicBloodStain(
        center = splash3Center,
        baseRadius = 15.dp.toPx(),
        seed = 3.8f,
        alphaMul = alphaMul
    )
    val splash3Satellites = listOf(
        Pair(Offset(-18f, -14f), 2.0f),
        Pair(Offset(20f, -12f), 2.2f),
        Pair(Offset(24f, 10f), 1.7f),
        Pair(Offset(-22f, 12f), 1.8f),
        Pair(Offset(0f, 22f), 2.1f),
        Pair(Offset(-12f, -22f), 1.4f),
        Pair(Offset(14f, -20f), 1.5f)
    )
    drawMicroSpatterMist(splash3Center, splash3Satellites, alphaMul)

    // -------------------------------------------------------------------------
    // Stain 4: Occult Knife-Flick Droplet Trails (Upper-Left near Moon)
    // -------------------------------------------------------------------------
    val flickOrigin = Offset(size.width * 0.17f, size.height * 0.28f)
    drawOrganicBloodStain(
        center = flickOrigin,
        baseRadius = 10.dp.toPx(),
        seed = 5.2f,
        alphaMul = alphaMul
    )
    drawDirectionalSpatter(
        center = flickOrigin + Offset(16.dp.toPx(), 12.dp.toPx()),
        angleRad = 0.65f,
        lengthDp = 3.2f,
        widthDp = 1.6f,
        alphaMul = alphaMul
    )
    drawDirectionalSpatter(
        center = flickOrigin + Offset(30.dp.toPx(), 22.dp.toPx()),
        angleRad = 0.65f,
        lengthDp = 2.4f,
        widthDp = 1.3f,
        alphaMul = alphaMul
    )

    // -------------------------------------------------------------------------
    // Stain 5: Fine Micro-Droplets across the Board Margins
    // -------------------------------------------------------------------------
    val marginDroplets = listOf(
        Pair(Offset(size.width * 0.05f, size.height * 0.42f), 1.6f),
        Pair(Offset(size.width * 0.08f, size.height * 0.52f), 1.9f),
        Pair(Offset(size.width * 0.28f, size.height * 0.24f), 1.4f),
        Pair(Offset(size.width * 0.35f, size.height * 0.52f), 1.8f),
        Pair(Offset(size.width * 0.65f, size.height * 0.48f), 1.9f),
        Pair(Offset(size.width * 0.72f, size.height * 0.66f), 2.0f),
        Pair(Offset(size.width * 0.88f, size.height * 0.44f), 1.8f),
        Pair(Offset(size.width * 0.92f, size.height * 0.62f), 1.6f),
        Pair(Offset(size.width * 0.55f, size.height * 0.88f), 1.9f),
        Pair(Offset(size.width * 0.32f, size.height * 0.86f), 1.7f)
    )
    marginDroplets.forEach { (pos, rDp) ->
        drawCircle(
            color = Color(0x993B070C).copy(alpha = 0.70f * alphaMul),
            radius = rDp.dp.toPx(),
            center = pos
        )
    }
}

private fun DrawScope.drawOrganicBloodStain(
    center: Offset,
    baseRadius: Float,
    seed: Float,
    alphaMul: Float
) {
    val count = 16
    val step = (2.0 * Math.PI / count).toFloat()

    fun buildOrganicPoints(scale: Float, stretchX: Float, stretchY: Float): List<Offset> {
        val pts = mutableListOf<Offset>()
        for (i in 0 until count) {
            val angle = i * step
            val harmonic = 1.0f +
                0.24f * sin(i * 1.35f + seed) -
                0.16f * cos((i * 2.1f).toDouble()).toFloat() +
                0.10f * sin(i * 3.7f + 0.8f)
            val r = baseRadius * scale * harmonic
            val px = center.x + (r * cos(angle.toDouble())).toFloat() * stretchX
            val py = center.y + (r * sin(angle.toDouble())).toFloat() * stretchY
            pts.add(Offset(px, py))
        }
        return pts
    }

    fun makeSmoothPath(pts: List<Offset>): Path {
        val path = Path()
        val n = pts.size
        val firstMidX = (pts[0].x + pts[1].x) / 2f
        val firstMidY = (pts[0].y + pts[1].y) / 2f
        path.moveTo(firstMidX, firstMidY)
        for (i in 1 until n) {
            val next = (i + 1) % n
            val midX = (pts[i].x + pts[next].x) / 2f
            val midY = (pts[i].y + pts[next].y) / 2f
            path.quadraticTo(pts[i].x, pts[i].y, midX, midY)
        }
        val wrapMidX = (pts[0].x + pts[1].x) / 2f
        val wrapMidY = (pts[0].y + pts[1].y) / 2f
        path.quadraticTo(pts[0].x, pts[0].y, wrapMidX, wrapMidY)
        path.close()
        return path
    }

    // 1. Outer capillary soak ring absorbed into porous mahogany grain
    val soakPts = buildOrganicPoints(scale = 1.28f, stretchX = 1.22f, stretchY = 0.88f)
    val soakPath = makeSmoothPath(soakPts)
    drawPath(
        path = soakPath,
        color = Color(0x35280E08).copy(alpha = 0.38f * alphaMul)
    )

    // 2. Coagulated maroon body
    val bodyPts = buildOrganicPoints(scale = 1.0f, stretchX = 1.16f, stretchY = 0.90f)
    val bodyPath = makeSmoothPath(bodyPts)
    drawPath(
        path = bodyPath,
        color = Color(0xBB4E0A12).copy(alpha = 0.85f * alphaMul)
    )

    // 3. Dense clotted core
    val corePts = buildOrganicPoints(scale = 0.60f, stretchX = 1.10f, stretchY = 0.92f)
    val corePath = makeSmoothPath(corePts)
    drawPath(
        path = corePath,
        color = Color(0xF0180205).copy(alpha = 0.92f * alphaMul)
    )

    // 4. Capillary seepage tendrils running horizontally along wood grain
    val tendrilColor = Color(0x55380C10).copy(alpha = 0.50f * alphaMul)
    val leftTendrilY = center.y + baseRadius * 0.15f
    drawLine(
        color = tendrilColor,
        start = Offset(center.x - baseRadius * 1.2f, leftTendrilY),
        end = Offset(center.x - baseRadius * 1.65f, leftTendrilY),
        strokeWidth = 1.2.dp.toPx(),
        cap = StrokeCap.Round
    )
    val rightTendrilY = center.y - baseRadius * 0.20f
    drawLine(
        color = tendrilColor,
        start = Offset(center.x + baseRadius * 1.18f, rightTendrilY),
        end = Offset(center.x + baseRadius * 1.60f, rightTendrilY),
        strokeWidth = 1.0.dp.toPx(),
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawDirectionalSpatter(
    center: Offset,
    angleRad: Float,
    lengthDp: Float,
    widthDp: Float,
    alphaMul: Float
) {
    val lenPx = lengthDp.dp.toPx()
    val widPx = widthDp.dp.toPx()
    val deg = Math.toDegrees(angleRad.toDouble()).toFloat()

    withTransform({
        translate(center.x, center.y)
        rotate(deg, pivot = Offset.Zero)
    }) {
        // Outer soak shadow
        drawOval(
            color = Color(0x35280E08).copy(alpha = 0.40f * alphaMul),
            topLeft = Offset(-lenPx * 0.65f, -widPx * 0.70f),
            size = Size(lenPx * 1.30f, widPx * 1.40f)
        )

        // Main clotted body
        drawOval(
            color = Color(0xCC3E080E).copy(alpha = 0.88f * alphaMul),
            topLeft = Offset(-lenPx * 0.5f, -widPx * 0.5f),
            size = Size(lenPx, widPx)
        )

        // Dense core
        drawOval(
            color = Color(0xF5180205).copy(alpha = 0.94f * alphaMul),
            topLeft = Offset(-lenPx * 0.28f, -widPx * 0.32f),
            size = Size(lenPx * 0.65f, widPx * 0.64f)
        )

        // Tapered back tail pointing along flight trajectory
        val tailPath = Path().apply {
            moveTo(-lenPx * 0.45f, 0f)
            lineTo(-lenPx * 0.95f, 0f)
        }
        drawPath(
            tailPath,
            color = Color(0x9938070D).copy(alpha = 0.75f * alphaMul),
            style = Stroke(width = widPx * 0.4f, cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawMicroSpatterMist(
    origin: Offset,
    satellites: List<Pair<Offset, Float>>,
    alphaMul: Float
) {
    satellites.forEach { (offsetDp, rDp) ->
        val center = origin + Offset(offsetDp.x.dp.toPx(), offsetDp.y.dp.toPx())
        val rPx = rDp.dp.toPx()
        // Soft capillary soak
        drawCircle(
            color = Color(0x3025090C).copy(alpha = 0.40f * alphaMul),
            radius = rPx + 0.8.dp.toPx(),
            center = center
        )
        // Dark clotted bead
        drawCircle(
            color = Color(0xDD3B070D).copy(alpha = 0.85f * alphaMul),
            radius = rPx,
            center = center
        )
    }
}

private fun DrawScope.drawCandle(
    position: Offset,
    flameSway: Float,
    flameHeight: Float,
    haloAlpha: Float,
    bloodIntensity: Float,
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

    // Natural wax drip trails on the side of the candle
    val dripPath = Path().apply {
        moveTo(pillarLeft + 2.dp.toPx(), pillarTop)
        lineTo(pillarLeft + 2.dp.toPx(), pillarTop + 14.dp.toPx())
        cubicTo(
            pillarLeft + 2.dp.toPx(), pillarTop + 18.dp.toPx(),
            pillarLeft + 4.dp.toPx(), pillarTop + 18.dp.toPx(),
            pillarLeft + 4.dp.toPx(), pillarTop + 14.dp.toPx()
        )
        lineTo(pillarLeft + 4.dp.toPx(), pillarTop)
    }
    drawPath(dripPath, color = Color(0xFFFFFBF2))

    // -------------------------------------------------------------------------
    // STATIC NATURAL CANDLE BLOOD DRIPS (Hardened coagulated wax-blood trickles)
    // -------------------------------------------------------------------------
    if (bloodIntensity > 0.01f) {
        val bAlpha = bloodIntensity.coerceIn(0f, 1f)

        // A. Dried Blood & Melted Wax Crust around the Top Rim
        drawOval(
            color = Color(0xFF380407).copy(alpha = 0.95f * bAlpha),
            topLeft = Offset(pillarLeft + 1.dp.toPx(), pillarTop - 1.5.dp.toPx()),
            size = Size(candleWidth - 2.dp.toPx(), 4.5.dp.toPx())
        )

        // B. Primary Winding Trickle with Frozen Hanging Droplet (Front-Left)
        val trickle1Path = Path().apply {
            val startX = pillarLeft + candleWidth * 0.32f
            val startY = pillarTop
            moveTo(startX - 1.2.dp.toPx(), startY)
            cubicTo(
                startX - 1.4.dp.toPx(), startY + 8.dp.toPx(),
                startX - 0.6.dp.toPx(), startY + 16.dp.toPx(),
                startX + 0.5.dp.toPx(), startY + 24.dp.toPx()
            )
            val bulbX = startX + 0.7.dp.toPx()
            val bulbY = startY + 33.dp.toPx()
            val bulbR = 2.4.dp.toPx()
            cubicTo(
                bulbX - bulbR * 1.15f, bulbY - bulbR * 0.45f,
                bulbX - bulbR * 0.95f, bulbY + bulbR,
                bulbX, bulbY + bulbR
            )
            cubicTo(
                bulbX + bulbR * 0.95f, bulbY + bulbR,
                bulbX + bulbR * 1.15f, bulbY - bulbR * 0.45f,
                bulbX + 0.6.dp.toPx(), startY + 24.dp.toPx()
            )
            cubicTo(
                startX + 0.8.dp.toPx(), startY + 16.dp.toPx(),
                startX + 1.2.dp.toPx(), startY + 8.dp.toPx(),
                startX + 1.4.dp.toPx(), startY
            )
            close()
        }
        drawPath(trickle1Path, color = Color(0xEE4E080F).copy(alpha = 0.92f * bAlpha))
        val vein1Path = Path().apply {
            val startX = pillarLeft + candleWidth * 0.32f
            val startY = pillarTop
            moveTo(startX, startY)
            quadraticTo(startX - 0.5.dp.toPx(), startY + 14.dp.toPx(), startX + 0.7.dp.toPx(), startY + 31.dp.toPx())
        }
        drawPath(vein1Path, color = Color(0xF5240205).copy(alpha = 0.94f * bAlpha), style = Stroke(width = 1.0.dp.toPx(), cap = StrokeCap.Round))

        // Soft specular reflection catching flame light on the droplet
        drawCircle(
            color = Color(0x55FFAEB3).copy(alpha = 0.50f * bAlpha),
            radius = 0.9.dp.toPx(),
            center = Offset(pillarLeft + candleWidth * 0.32f + 0.2.dp.toPx(), pillarTop + 32.dp.toPx())
        )

        // C. Secondary Frozen Trickle (Right Flank)
        val trickle2Path = Path().apply {
            val startX = pillarLeft + candleWidth - 2.8.dp.toPx()
            val startY = pillarTop
            moveTo(startX - 0.9.dp.toPx(), startY)
            lineTo(startX - 0.7.dp.toPx(), startY + 18.dp.toPx())
            val bulbR = 1.5.dp.toPx()
            val bulbY = startY + 21.dp.toPx()
            cubicTo(
                startX - bulbR * 1.1f, bulbY - bulbR * 0.4f,
                startX - bulbR * 0.9f, bulbY + bulbR,
                startX, bulbY + bulbR
            )
            cubicTo(
                startX + bulbR * 0.9f, bulbY + bulbR,
                startX + bulbR * 1.1f, bulbY - bulbR * 0.4f,
                startX + 0.7.dp.toPx(), startY + 18.dp.toPx()
            )
            lineTo(startX + 0.9.dp.toPx(), startY)
            close()
        }
        drawPath(trickle2Path, color = Color(0xDD3E060A).copy(alpha = 0.88f * bAlpha))

        // D. Short Center Micro-Run
        drawLine(
            color = Color(0xAA380407).copy(alpha = 0.80f * bAlpha),
            start = Offset(pillarLeft + candleWidth * 0.58f, pillarTop),
            end = Offset(pillarLeft + candleWidth * 0.58f, pillarTop + 11.dp.toPx()),
            strokeWidth = 1.1.dp.toPx(),
            cap = StrokeCap.Round
        )

        // E. Coagulated Blood Gathering in the Candlestick Dish at the Base
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xF0260205).copy(alpha = 0.94f * bAlpha),
                    Color(0xCC3D070D).copy(alpha = 0.85f * bAlpha),
                    Color.Transparent
                ),
                center = baseCenter,
                radius = standWidth * 0.40f
            ),
            topLeft = Offset(baseCenter.x - standWidth * 0.36f, baseCenter.y - standHeight * 0.36f),
            size = Size(standWidth * 0.72f, standHeight * 0.72f)
        )
    }

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
