package com.noirdraco.pixelcomfort

import android.content.Context
import android.util.Log

/**
 * Anzeige-Skalierung von Hand: kleinste Breite (dp) und Schriftgroesse.
 *
 * - Kleinste Breite laeuft ueber `wm density` per Shizuku. Nur den Settings-Wert
 *   display_density_forced zu schreiben reicht nicht, er wird erst nach einem
 *   Neustart uebernommen.
 * - Schriftgroesse ist der oeffentliche Key system/font_scale und geht ueber den
 *   [SettingWriter] (ContentResolver, sonst Shizuku).
 *
 * Alle Funktionen blockieren (Shizuku) und gehoeren auf den [SettingsWorker].
 */
object DisplayScale {

    private const val TAG = ComfortAccessibilityService.TAG
    private const val KEY_FONT_SCALE = "font_scale"

    /** Nicht lesbare Werte sind null (z. B. Breite ohne Shizuku). */
    data class State(
        val widthDp: Int?,
        val defaultWidthDp: Int?,
        val fontScale: Float?,
        val shizukuReady: Boolean,
    )

    /** @param message bei einem Fehlschlag: Kuerzel aus [History.Reason] oder Technik-Text */
    data class Result(val success: Boolean, val message: String)

    fun read(context: Context): State {
        val ready = ShizukuShell.isReady()
        val size = if (ready) readSize() else null
        val density = if (ready) readDensity() else null
        val font = ScaleMath.parseFontScale(SettingWriter.read(context, Prefs.NS_SYSTEM, KEY_FONT_SCALE))
            // Key noch nie gesetzt -> das System rechnet mit dem Wert der Konfiguration
            ?: context.resources.configuration.fontScale
        return State(
            widthDp = if (size != null && density != null) ScaleMath.widthDp(size, density.current) else null,
            defaultWidthDp = if (size != null && density != null) ScaleMath.widthDp(size, density.physical) else null,
            fontScale = font,
            shizukuReady = ready,
        )
    }

    fun setWidthDp(context: Context, widthDp: Int): Result {
        val dp = ScaleMath.clampWidthDp(widthDp)
        return recordWidth(context, to = "$dp dp") { size -> applyWidthDp(size, dp) }
    }

    /** Zurueck auf die Dichte des Geraets (Override entfernen). */
    fun resetWidth(context: Context): Result =
        recordWidth(context, to = History.DEFAULT) { applyResetWidth() }

    /** Fuehrt [change] aus und haelt das Ergebnis mit dem Wert davor im Verlauf fest. */
    private fun recordWidth(context: Context, to: String, change: (ScaleMath.Size?) -> Result): Result {
        val size = if (ShizukuShell.isReady()) readSize() else null
        val before = size?.let { s -> readDensity()?.let { ScaleMath.widthDp(s, it.current) } }
        val result = change(size)
        HistoryStore.add(
            context,
            History.Entry(
                time = System.currentTimeMillis(),
                kind = History.Kind.WIDTH,
                from = before?.let { "$it dp" },
                to = to,
                success = result.success,
                message = if (result.success) "" else ShizukuShell.explain(result.message),
            ),
        )
        return result
    }

    private fun applyWidthDp(size: ScaleMath.Size?, dp: Int): Result {
        if (!ShizukuShell.isReady()) return Result(false, ShizukuShell.explain("Shizuku not ready"))
        if (size == null) return Result(false, "Screen size not readable (wm size)")
        val target = ScaleMath.densityFor(size, dp)
        return try {
            val res = ShizukuShell.run("wm", "density", target.toString())
            val now = readDensity()?.current
            val ok = now == target
            Log.i(TAG, "Kleinste Breite -> $dp dp ($target dpi), exit=${res.exitCode}, gelesen=$now, ok=$ok")
            if (ok) {
                Result(true, "")
            } else {
                Result(false, "wm density had no effect (exit=${res.exitCode}, err=${res.err}, read=$now)")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "wm density fehlgeschlagen", t)
            Result(false, "Shizuku error: ${t.message}")
        }
    }

    private fun applyResetWidth(): Result {
        if (!ShizukuShell.isReady()) return Result(false, ShizukuShell.explain("Shizuku not ready"))
        return try {
            val res = ShizukuShell.run("wm", "density", "reset")
            val density = readDensity()
            val ok = density != null && density.override == null
            Log.i(TAG, "Kleinste Breite -> Standard, exit=${res.exitCode}, gelesen=$density, ok=$ok")
            if (ok) {
                Result(true, "")
            } else {
                Result(false, "wm density reset had no effect (exit=${res.exitCode}, err=${res.err})")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "wm density reset fehlgeschlagen", t)
            Result(false, "Shizuku error: ${t.message}")
        }
    }

    fun setFontScale(context: Context, scale: Float): Result {
        val snapped = ScaleMath.snapFontScale(scale)
        val value = ScaleMath.formatFontScale(snapped)
        val before = ScaleMath.parseFontScale(SettingWriter.read(context, Prefs.NS_SYSTEM, KEY_FONT_SCALE))
        val res = SettingWriter.write(context, Prefs.NS_SYSTEM, KEY_FONT_SCALE, value)
        Log.i(TAG, "Schriftgroesse -> $value via ${res.method}, ok=${res.success}: ${res.message}")
        HistoryStore.add(
            context,
            History.Entry(
                time = System.currentTimeMillis(),
                kind = History.Kind.FONT,
                from = before?.let(ScaleMath::formatFontScale),
                to = value,
                success = res.success,
                message = if (res.success) "" else ShizukuShell.explain(res.message),
            ),
        )
        return Result(res.success, if (res.success) "" else res.message)
    }

    private fun readSize(): ScaleMath.Size? =
        runCatching { ScaleMath.parseWmSize(ShizukuShell.run("wm", "size").out) }
            .onFailure { Log.w(TAG, "wm size fehlgeschlagen", it) }
            .getOrNull()

    private fun readDensity(): ScaleMath.Density? =
        runCatching { ScaleMath.parseWmDensity(ShizukuShell.run("wm", "density").out) }
            .onFailure { Log.w(TAG, "wm density fehlgeschlagen", it) }
            .getOrNull()
}
