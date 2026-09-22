package ouija.app.ui.client

/**
 * Represents a single target on the Ouija board (letter, number, or corner word).
 *
 * @property name The text of the target (e.g. "A", "7", "YES", "GOODBYE").
 * @property normX Normalized horizontal position on the board (0.0 = far left, 1.0 = far right).
 * @property normY Normalized vertical position on the board (0.0 = top edge, 1.0 = bottom edge).
 * @property rotation Angle in degrees to rotate the glyph along the arch curve.
 */
data class BoardPosition(
    val name: String,
    val normX: Float,
    val normY: Float,
    val rotation: Float = 0f
)

/**
 * =============================================================================
 *                      OUIJA BOARD CONFIGURATION GUIDE
 * =============================================================================
 *
 * This file controls all visual layout parameters of the Ouija board:
 * - Letter and number arch curvatures, widths, and apex heights
 * - Spacing between adjacent characters
 * - Letter rotation / tilt along the curve
 * - Placement of corner words (YES, NO, HELLO, GOODBYE)
 * - Placement of ritual candles and celestial artwork (Moon & Sun)
 * - Typography font sizes for the board and under the planchette magnifying glass
 *
 * HOW TO MODIFY:
 * 1. Directly change the default values in this file.
 * 2. OR instantiate a customized copy and pass it to OuijaBoardCanvas:
 *    OuijaBoardCanvas(archConfig = BoardArchConfig(letterFontSizeSp = 50f))
 * =============================================================================
 */
data class BoardArchConfig(

    // -------------------------------------------------------------------------
    // 1. UPPER ALPHABET ARCH: "A" through "M" (13 Letters)
    // -------------------------------------------------------------------------

    /**
     * Horizontal width spread for A-M, relative to screen width.
     * - INCREASE (e.g. 0.62f): Spreads letters wider across the screen, adding more space between them.
     * - DECREASE (e.g. 0.50f): Pulls letters closer to the center, reducing space between them.
     * - Default: 0.58f (sweeps across ~75% of screen width).
     */
    val arch1RadiusXMultiplier: Float = 0.58f,

    /**
     * Vertical curve depth for A-M, relative to screen height.
     * - INCREASE (e.g. 0.55f): Makes the arch steeper (ends curve down further).
     * - DECREASE (e.g. 0.38f): Flattens the arch (ends stay closer to the top apex).
     * - Default: 0.47f (gentle, shallow arch with ~11% height drop).
     */
    val arch1RadiusYMultiplier: Float = 0.47f,

    /**
     * Center Y coordinate of the Arch 1 curve, relative to screen height.
     * Controls where the apex (highest point at letter G) sits on the board:
     * Apex Y = (arch1CenterYMultiplier - arch1RadiusYMultiplier) = 0.73 - 0.47 = 0.26 (26% from top).
     * - INCREASE (e.g. 0.78f): Pushes the entire A-M arch lower down.
     * - DECREASE (e.g. 0.68f): Pulls the entire A-M arch higher up towards YES/NO.
     * - Default: 0.73f.
     */
    val arch1CenterYMultiplier: Float = 0.73f,

    /**
     * Starting angle for letter "A" on the left (in degrees, 270° = straight up).
     * - LOWER (e.g. 224°): Pushes "A" further to the left.
     * - HIGHER (e.g. 235°): Pulls "A" closer to the center.
     * - Default: 230.0°.
     */
    val arch1StartAngle: Double = 230.0,

    /**
     * Ending angle for letter "M" on the right (in degrees, 270° = straight up).
     * - HIGHER (e.g. 316°): Pushes "M" further to the right.
     * - LOWER (e.g. 305°): Pulls "M" closer to the center.
     * - Default: 310.0°.
     */
    val arch1EndAngle: Double = 310.0,


    // -------------------------------------------------------------------------
    // 2. LOWER ALPHABET ARCH: "N" through "Z" (13 Letters)
    // -------------------------------------------------------------------------

    /**
     * Horizontal width spread for N-Z, relative to screen width.
     * - INCREASE (e.g. 0.58f): Spreads N-Z wider, increasing space between letters.
     * - DECREASE (e.g. 0.48f): Pulls N-Z narrower.
     * - Default: 0.54f (~70% screen width span).
     */
    val arch2RadiusXMultiplier: Float = 0.54f,

    /**
     * Vertical curve depth for N-Z, relative to screen height.
     * - Controls the steepness of the N-Z arch curve.
     * - Default: 0.47f.
     */
    val arch2RadiusYMultiplier: Float = 0.47f,

    /**
     * Center Y coordinate of Arch 2 curve, relative to screen height.
     * Apex Y = (arch2CenterYMultiplier - arch2RadiusYMultiplier) = 0.92 - 0.47 = 0.45 (45% from top).
     * Controls the vertical gap between A-M and N-Z:
     * - INCREASE (e.g. 0.96f): Moves N-Z lower down, increasing gap below A-M.
     * - DECREASE (e.g. 0.88f): Moves N-Z higher up, closer to A-M.
     * - Default: 0.92f.
     */
    val arch2CenterYMultiplier: Float = 0.92f,

    /**
     * Starting angle for letter "N" on the left.
     * - Default: 230.0°.
     */
    val arch2StartAngle: Double = 230.0,

    /**
     * Ending angle for letter "Z" on the right.
     * - Default: 310.0°.
     */
    val arch2EndAngle: Double = 310.0,


    // -------------------------------------------------------------------------
    // 3. NUMBER ARCH: "1 2 3 4 5 6 7 8 9 0" (10 Digits)
    // -------------------------------------------------------------------------

    /**
     * Horizontal width spread for digits 1-0, relative to screen width.
     * - INCREASE (e.g. 0.52f): Spreads digits wider, increasing space between numbers.
     * - DECREASE (e.g. 0.40f): Pulls digits closer together.
     * - Default: 0.46f (~52% screen width span).
     */
    val numbersRadiusXMultiplier: Float = 0.46f,

    /**
     * Vertical curve depth for the number arch, relative to screen height.
     * - Default: 0.35f (very gentle, subtle curve).
     */
    val numbersRadiusYMultiplier: Float = 0.35f,

    /**
     * Center Y coordinate of the number arch, relative to screen height.
     * Apex Y = (numbersCenterYMultiplier - numbersRadiusYMultiplier) = 1.01 - 0.35 = 0.66 (66% from top).
     * Controls vertical gap between letters (N-Z) and numbers (1-0):
     * - INCREASE (e.g. 1.06f): Pushes numbers lower down towards HELLO/GOODBYE.
     * - DECREASE (e.g. 0.96f): Pulls numbers higher up towards N-Z.
     * - Default: 1.01f.
     */
    val numbersCenterYMultiplier: Float = 1.01f,

    /**
     * Starting angle for digit "1" on the left.
     * - Default: 236.0°.
     */
    val numbersStartAngle: Double = 236.0,

    /**
     * Ending angle for digit "0" on the right.
     * - Default: 304.0°.
     */
    val numbersEndAngle: Double = 304.0,


    // -------------------------------------------------------------------------
    // 4. LETTER ROTATION & TILT
    // -------------------------------------------------------------------------

    /**
     * Controls how much characters rotate / tilt to follow the curve:
     * - 0.0f: Completely upright (0° rotation for all letters).
     * - 0.50f: Subtle, elegant curve tilt (max ±20° at outer letters A, M, N, Z). [RECOMMENDED]
     * - 1.0f: Full tangent tilt (letters strictly perpendicular to arc radius).
     * - Default: 0.50f.
     */
    val letterTiltFactor: Float = 0.50f,


    // -------------------------------------------------------------------------
    // 5. CORNER WORDS POSITIONS (Normalized: 0.0 to 1.0)
    // -------------------------------------------------------------------------

    /** Normalized X position for "YES" (top-left). Default: 0.15f */
    val yesNormX: Float = 0.15f,
    /** Normalized Y position for "YES" (top-left). Default: 0.14f */
    val yesNormY: Float = 0.14f,

    /** Normalized X position for "NO" (top-right). Default: 0.85f */
    val noNormX: Float = 0.85f,
    /** Normalized Y position for "NO" (top-right). Default: 0.14f */
    val noNormY: Float = 0.14f,

    /** Normalized X position for "HELLO" (bottom-left). Default: 0.18f */
    val helloNormX: Float = 0.18f,
    /** Normalized Y position for "HELLO" (bottom-left). Default: 0.88f */
    val helloNormY: Float = 0.88f,

    /**
     * Normalized X position for "GOODBYE" (bottom-right).
     * Note: Sized at 0.78f to account for 7 letters vs 5 letters in HELLO,
     * maintaining equal visual clearance to the right candle. Default: 0.78f
     */
    val goodbyeNormX: Float = 0.78f,
    /** Normalized Y position for "GOODBYE" (bottom-right). Default: 0.88f */
    val goodbyeNormY: Float = 0.88f,


    // -------------------------------------------------------------------------
    // 6. RITUAL CANDLES POSITIONS (Normalized: 0.0 to 1.0)
    // -------------------------------------------------------------------------

    /** Normalized X position for left ritual candle base. Default: 0.06f */
    val candleLeftNormX: Float = 0.06f,
    /** Normalized Y position for left ritual candle base (bottom ledge). Default: 0.88f */
    val candleLeftNormY: Float = 0.88f,

    /** Normalized X position for right ritual candle base. Default: 0.94f */
    val candleRightNormX: Float = 0.94f,
    /** Normalized Y position for right ritual candle base (bottom ledge). Default: 0.88f */
    val candleRightNormY: Float = 0.88f,


    // -------------------------------------------------------------------------
    // 7. CELESTIAL ARTWORK POSITIONS (Normalized: 0.0 to 1.0)
    // -------------------------------------------------------------------------

    /**
     * Normalized Y position for Crescent Moon with Star (top-left).
     * Horizontally aligned in a straight vertical column with candleLeftNormX.
     * Default: 0.16f.
     */
    val moonNormY: Float = 0.16f,

    /**
     * Normalized Y position for Radiant Sun with Rays (top-right).
     * Horizontally aligned in a straight vertical column with candleRightNormX.
     * Default: 0.16f.
     */
    val sunNormY: Float = 0.16f,


    // -------------------------------------------------------------------------
    // 8. BOARD TYPOGRAPHY FONT SIZES (in sp)
    // -------------------------------------------------------------------------

    /** Font size for alphabet letters "A" through "Z" on the board. Default: 46f */
    val letterFontSizeSp: Float = 46f,

    /** Font size for digits "1" through "0" on the board. Default: 38f */
    val numberFontSizeSp: Float = 38f,

    /** Font size for "YES" and "NO" (matched to HELLO). Default: 36f */
    val headerFontSizeSp: Float = 36f,

    /** Font size for "HELLO" and "GOODBYE". Default: 36f */
    val footerFontSizeSp: Float = 36f,


    // -------------------------------------------------------------------------
    // 9. PLANCHETTE MAGNIFYING LENS OPTICAL SIZES (in sp)
    // -------------------------------------------------------------------------

    /** Font size when an alphabet letter is seen inside the 48dp lens. Default: 32f */
    val magnifiedLetterFontSizeSp: Float = 32f,

    /** Font size when a digit is seen inside the 48dp lens. Default: 30f */
    val magnifiedNumberFontSizeSp: Float = 30f,

    /** Font size when YES/NO is seen inside the 48dp lens. Default: 20f */
    val magnifiedHeaderFontSizeSp: Float = 20f,

    /** Font size when HELLO/GOODBYE is seen inside the 48dp lens. Default: 20f */
    val magnifiedFooterFontSizeSp: Float = 20f
)
