package `in`.ouija

import ouija.app.ui.client.BoardArchConfig
import ouija.app.ui.client.BoardPosition
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class BoardArchConfigTest {

    @Test
    fun verifyAmpleSpaceBetweenLetters() {
        val config = BoardArchConfig()
        val boardWidth = 2400f
        val boardHeight = 1080f

        val archCenterPxX = boardWidth * 0.5f

        // Arch 1: A - M
        val arch1 = "ABCDEFGHIJKLM"
        val rx1 = boardWidth * config.arch1RadiusXMultiplier
        val ry1 = boardHeight * config.arch1RadiusYMultiplier
        val centerY1 = boardHeight * config.arch1CenterYMultiplier
        val step1 = (config.arch1EndAngle - config.arch1StartAngle) / (arch1.length - 1)

        val pts1 = arch1.mapIndexed { i, char ->
            val deg = config.arch1StartAngle + (i * step1)
            val rad = Math.toRadians(deg)
            val pxX = archCenterPxX + (rx1 * cos(rad)).toFloat()
            val pxY = centerY1 + (ry1 * sin(rad)).toFloat()
            BoardPosition(char.toString(), pxX / boardWidth, pxY / boardHeight)
        }

        for (i in 0 until pts1.size - 1) {
            val p1 = pts1[i]
            val p2 = pts1[i + 1]
            val distPx = hypot((p2.normX - p1.normX) * boardWidth, (p2.normY - p1.normY) * boardHeight)
            println("Arch 1: ${p1.name} to ${p2.name} = ${distPx}px (normX: ${p1.normX} -> ${p2.normX})")
            assertTrue("Distance between ${p1.name} and ${p2.name} should be >= 100px", distPx >= 100f)
        }

        // Arch 2: N - Z
        val arch2 = "NOPQRSTUVWXYZ"
        val rx2 = boardWidth * config.arch2RadiusXMultiplier
        val ry2 = boardHeight * config.arch2RadiusYMultiplier
        val centerY2 = boardHeight * config.arch2CenterYMultiplier
        val step2 = (config.arch2EndAngle - config.arch2StartAngle) / (arch2.length - 1)

        val pts2 = arch2.mapIndexed { i, char ->
            val deg = config.arch2StartAngle + (i * step2)
            val rad = Math.toRadians(deg)
            val pxX = archCenterPxX + (rx2 * cos(rad)).toFloat()
            val pxY = centerY2 + (ry2 * sin(rad)).toFloat()
            BoardPosition(char.toString(), pxX / boardWidth, pxY / boardHeight)
        }

        for (i in 0 until pts2.size - 1) {
            val p1 = pts2[i]
            val p2 = pts2[i + 1]
            val distPx = hypot((p2.normX - p1.normX) * boardWidth, (p2.normY - p1.normY) * boardHeight)
            println("Arch 2: ${p1.name} to ${p2.name} = ${distPx}px")
            assertTrue("Distance between ${p1.name} and ${p2.name} should be >= 90px", distPx >= 90f)
        }

        // Numbers: 1 - 0
        val numbers = "1234567890"
        val rx3 = boardWidth * config.numbersRadiusXMultiplier
        val ry3 = boardHeight * config.numbersRadiusYMultiplier
        val centerY3 = boardHeight * config.numbersCenterYMultiplier
        val step3 = (config.numbersEndAngle - config.numbersStartAngle) / (numbers.length - 1)

        val pts3 = numbers.mapIndexed { i, char ->
            val deg = config.numbersStartAngle + (i * step3)
            val rad = Math.toRadians(deg)
            val pxX = archCenterPxX + (rx3 * cos(rad)).toFloat()
            val pxY = centerY3 + (ry3 * sin(rad)).toFloat()
            BoardPosition(char.toString(), pxX / boardWidth, pxY / boardHeight)
        }

        for (i in 0 until pts3.size - 1) {
            val p1 = pts3[i]
            val p2 = pts3[i + 1]
            val distPx = hypot((p2.normX - p1.normX) * boardWidth, (p2.normY - p1.normY) * boardHeight)
            println("Numbers: ${p1.name} to ${p2.name} = ${distPx}px")
            assertTrue("Distance between digits should be >= 90px", distPx >= 90f)
        }
    }
}
