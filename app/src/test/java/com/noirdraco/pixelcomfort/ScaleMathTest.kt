package com.noirdraco.pixelcomfort

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScaleMathTest {

    // ---- wm size ----

    @Test
    fun `wm size ohne Override liefert die physische Groesse`() {
        assertEquals(ScaleMath.Size(1280, 2856), ScaleMath.parseWmSize("Physical size: 1280x2856"))
    }

    @Test
    fun `wm size mit Override liefert die Override-Groesse`() {
        val out = "Physical size: 1280x2856\nOverride size: 1080x2410"
        assertEquals(ScaleMath.Size(1080, 2410), ScaleMath.parseWmSize(out))
    }

    @Test
    fun `wm size mit unlesbarer Ausgabe liefert null`() {
        assertNull(ScaleMath.parseWmSize("Error: no display"))
    }

    // ---- wm density ----

    @Test
    fun `wm density mit Override liefert beide Werte`() {
        val out = "Physical density: 480\nOverride density: 468"
        assertEquals(ScaleMath.Density(physical = 480, override = 468), ScaleMath.parseWmDensity(out))
    }

    @Test
    fun `wm density ohne Override hat keinen Override-Wert`() {
        assertEquals(
            ScaleMath.Density(physical = 480, override = null),
            ScaleMath.parseWmDensity("Physical density: 480"),
        )
    }

    @Test
    fun `aktive Dichte ist der Override, sonst die physische`() {
        assertEquals(468, ScaleMath.Density(480, 468).current)
        assertEquals(480, ScaleMath.Density(480, null).current)
    }

    @Test
    fun `wm density mit unlesbarer Ausgabe liefert null`() {
        assertNull(ScaleMath.parseWmDensity(""))
    }

    // ---- Umrechnung dp <-> dpi ----

    @Test
    fun `Standard-Dichte 480 auf 1280 px Breite sind gerundet 427 dp`() {
        // 426,67 dp -> 427, so wie das System es anzeigt
        assertEquals(427, ScaleMath.widthDp(ScaleMath.Size(1280, 2856), 480))
    }

    @Test
    fun `468 dpi auf 1280 px Breite sind gerundet 438 dp`() {
        // 437,61 dp -> 438
        assertEquals(438, ScaleMath.widthDp(ScaleMath.Size(1280, 2856), 468))
    }

    @Test
    fun `427 dp auf 1280 px Breite ergeben die Standard-Dichte 480`() {
        assertEquals(480, ScaleMath.densityFor(ScaleMath.Size(1280, 2856), 427))
    }

    @Test
    fun `eingestellte Breite wird nach dem Schreiben wieder genauso angezeigt`() {
        val size = ScaleMath.Size(1280, 2856)
        for (dp in listOf(320, 360, 400, 427, 437, 438, 450)) {
            assertEquals(dp, ScaleMath.widthDp(size, ScaleMath.densityFor(size, dp)))
        }
    }

    @Test
    fun `im Querformat zaehlt die kuerzere Seite`() {
        assertEquals(438, ScaleMath.widthDp(ScaleMath.Size(2856, 1280), 468))
        assertEquals(480, ScaleMath.densityFor(ScaleMath.Size(2856, 1280), 427))
    }

    @Test
    fun `Breite wird auf den erlaubten Bereich begrenzt`() {
        assertEquals(ScaleMath.MIN_WIDTH_DP, ScaleMath.clampWidthDp(100))
        assertEquals(ScaleMath.MAX_WIDTH_DP, ScaleMath.clampWidthDp(5000))
        assertEquals(437, ScaleMath.clampWidthDp(437))
    }

    // ---- Schriftgroesse ----

    @Test
    fun `Schriftgroesse rastet auf 0,05-Schritte ein`() {
        assertEquals(1.15f, ScaleMath.snapFontScale(1.1499999f), 0.0001f)
        assertEquals(1.15f, ScaleMath.snapFontScale(1.17f), 0.0001f)
        assertEquals(1.20f, ScaleMath.snapFontScale(1.18f), 0.0001f)
    }

    @Test
    fun `Schriftgroesse wird auf den erlaubten Bereich begrenzt`() {
        assertEquals(ScaleMath.MIN_FONT_SCALE, ScaleMath.snapFontScale(0.1f), 0.0001f)
        assertEquals(ScaleMath.MAX_FONT_SCALE, ScaleMath.snapFontScale(9f), 0.0001f)
    }

    @Test
    fun `Schriftgroesse wird mit Punkt und ohne ueberfluessige Stellen geschrieben`() {
        assertEquals("1.15", ScaleMath.formatFontScale(1.15f))
        assertEquals("1.0", ScaleMath.formatFontScale(1.0f))
        assertEquals("0.85", ScaleMath.formatFontScale(0.85f))
        assertEquals("1.3", ScaleMath.formatFontScale(1.3f))
    }

    @Test
    fun `Schriftgroesse aus dem System wird gelesen`() {
        assertEquals(1.15f, ScaleMath.parseFontScale("1.15")!!, 0.0001f)
        assertNull(ScaleMath.parseFontScale("null"))
        assertNull(ScaleMath.parseFontScale(null))
    }

    // ---- Plus/Minus ----

    @Test
    fun `Anzeigegroesse aendert sich je Tipp um 1 dp und bleibt in den Grenzen`() {
        val pixel = ScaleMath.Size(1280, 2856)
        assertEquals(434, ScaleMath.stepWidthDp(pixel, 433, +1))
        assertEquals(432, ScaleMath.stepWidthDp(pixel, 433, -1))
        assertEquals(ScaleMath.MAX_WIDTH_DP, ScaleMath.stepWidthDp(pixel, ScaleMath.MAX_WIDTH_DP, +1))
        assertEquals(ScaleMath.MIN_WIDTH_DP, ScaleMath.stepWidthDp(pixel, ScaleMath.MIN_WIDTH_DP, -1))
        // Ohne bekannte Groesse einfach 1 dp.
        assertEquals(434, ScaleMath.stepWidthDp(null, 433, +1))
    }

    @Test
    fun `Plus und Minus ueberspringen dp-Werte, die Android nicht einstellen kann`() {
        // 1080 px: 541 dp ergibt dieselbe Dichte (319 dpi) wie 542 dp.
        val size = ScaleMath.Size(1080, 2400)
        assertEquals(542, ScaleMath.stepWidthDp(size, 540, +1))
        assertEquals(540, ScaleMath.stepWidthDp(size, 542, -1))
    }

    @Test
    fun `Schriftgroesse aendert sich je Tipp um 0,05 und bleibt in den Grenzen`() {
        assertEquals(1.2f, ScaleMath.stepFontScale(1.15f, +1), 0.0001f)
        assertEquals(1.1f, ScaleMath.stepFontScale(1.15f, -1), 0.0001f)
        assertEquals(ScaleMath.MAX_FONT_SCALE, ScaleMath.stepFontScale(ScaleMath.MAX_FONT_SCALE, +1), 0.0001f)
        assertEquals(ScaleMath.MIN_FONT_SCALE, ScaleMath.stepFontScale(ScaleMath.MIN_FONT_SCALE, -1), 0.0001f)
        // Krumme Systemwerte rasten auf den naechsten Schritt ein.
        assertEquals(1.2f, ScaleMath.stepFontScale(1.13f, +1), 0.0001f)
    }
}
