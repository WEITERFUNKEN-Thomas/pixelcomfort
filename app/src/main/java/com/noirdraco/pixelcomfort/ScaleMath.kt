package com.noirdraco.pixelcomfort

import kotlin.math.roundToInt

/**
 * Reine Rechen- und Parse-Logik fuer die Anzeige-Skalierung, ohne Android-Abhaengigkeit
 * (damit per Unit-Test pruefbar). Der Geraetezugriff liegt in [DisplayScale].
 *
 * Umrechnung: dp = kuerzere Seite in px * 160 / Dichte. Beide Richtungen runden
 * kaufmaennisch, so wie das System die kleinste Breite meldet (480 dpi auf 1280 px
 * sind 426,67 dp und werden als 427 dp angezeigt).
 */
object ScaleMath {

    /**
     * Grenzen bewusst enger als technisch moeglich: ausserhalb davon wird die
     * Oberflaeche so gross oder klein, dass sich das Geraet kaum noch bedienen laesst.
     */
    const val MIN_WIDTH_DP = 320
    const val MAX_WIDTH_DP = 600

    const val MIN_FONT_SCALE = 0.85f
    const val MAX_FONT_SCALE = 2.0f
    const val DEFAULT_FONT_SCALE = 1.0f

    /** Schrittweite der Schriftgroesse in Hundertsteln (0,05). */
    private const val FONT_STEP_HUNDREDTHS = 5

    private const val DENSITY_MEDIUM = 160

    data class Size(val width: Int, val height: Int) {
        val shortSide: Int get() = minOf(width, height)
    }

    data class Density(val physical: Int, val override: Int?) {
        val current: Int get() = override ?: physical
    }

    private val SIZE_PHYSICAL = Regex("""Physical size:\s*(\d+)x(\d+)""")
    private val SIZE_OVERRIDE = Regex("""Override size:\s*(\d+)x(\d+)""")
    private val DENSITY_PHYSICAL = Regex("""Physical density:\s*(\d+)""")
    private val DENSITY_OVERRIDE = Regex("""Override density:\s*(\d+)""")

    /** Ausgabe von `wm size`. Ein Override (geaenderte Aufloesung) hat Vorrang. */
    fun parseWmSize(output: String): Size? {
        val m = SIZE_OVERRIDE.find(output) ?: SIZE_PHYSICAL.find(output) ?: return null
        return Size(m.groupValues[1].toInt(), m.groupValues[2].toInt())
    }

    /** Ausgabe von `wm density`. */
    fun parseWmDensity(output: String): Density? {
        val physical = DENSITY_PHYSICAL.find(output)?.groupValues?.get(1)?.toInt() ?: return null
        val override = DENSITY_OVERRIDE.find(output)?.groupValues?.get(1)?.toInt()
        return Density(physical, override)
    }

    fun widthDp(size: Size, density: Int): Int =
        (size.shortSide * DENSITY_MEDIUM / density.toFloat()).roundToInt()

    fun densityFor(size: Size, widthDp: Int): Int =
        (size.shortSide * DENSITY_MEDIUM / widthDp.toFloat()).roundToInt()

    fun clampWidthDp(widthDp: Int): Int = widthDp.coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP)

    fun snapFontScale(scale: Float): Float =
        hundredths(scale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)) / 100f

    /** Immer mit Punkt (unabhaengig von der Sprache), so wie das System den Wert ablegt. */
    fun formatFontScale(scale: Float): String {
        val h = hundredths(scale)
        val fraction = (h % 100).toString().padStart(2, '0').trimEnd('0').ifEmpty { "0" }
        return "${h / 100}.$fraction"
    }

    fun parseFontScale(raw: String?): Float? = raw?.trim()?.toFloatOrNull()

    private fun hundredths(scale: Float): Int =
        (scale * 100 / FONT_STEP_HUNDREDTHS).roundToInt() * FONT_STEP_HUNDREDTHS
}
