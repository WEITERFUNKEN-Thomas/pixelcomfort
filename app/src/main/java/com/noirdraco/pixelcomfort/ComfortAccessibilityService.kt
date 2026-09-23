package com.noirdraco.pixelcomfort

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Lauscht auf Fenster-Wechsel. Sobald eine der Ziel-Apps (Kamera/Fotos) in den
 * Vordergrund kommt, wird der aktuelle Comfort-View-Wert des Systems gemerkt und auf
 * AUS gesetzt. Beim Wechsel zu einer anderen App wird der gemerkte Wert exakt wieder
 * hergestellt. Andere Einstellungen werden nicht angefasst.
 *
 * Beim (Neu-)Verbinden des Dienstes wird bewusst NICHTS geschrieben: Ohne
 * canRetrieveWindowContent laesst sich die Vordergrund-App hier nicht ermitteln, und
 * ein blindes Wiederherstellen wuerde den Filter mitten in der Kamera wieder
 * einschalten. Der persistierte Zustand (suppressed/savedValue) wird stattdessen vom
 * naechsten Fenster-Event korrekt aufgeloest – auch nach einem Geraete-Neustart.
 */
class ComfortAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(
            TAG,
            "Dienst verbunden. Ziel-Packages=${Prefs.getPackages(this)}, " +
                "suppressed=${Prefs.getSuppressed(this)} (Aufloesung beim naechsten Fenster-Event)",
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()
        if (pkg.isNullOrBlank()) return
        // Overlays wie Benachrichtigungs-Shade / Tastatur / Dialoge / eigene App
        // ignorieren, damit sie ueber der Kamera keine vorzeitige Wiederherstellung
        // ausloesen.
        if (pkg == packageName || pkg in IGNORED_PACKAGES || pkg == currentImePackage()) return

        val isTarget = pkg in Prefs.getPackages(this)
        // Suppressed-Pruefung erst IM Task, damit schnell aufeinanderfolgende Events
        // (Kamera -> Launcher) seriell und ohne Doppel-Schreiben verarbeitet werden.
        // SettingsWorker ist prozessweit und wird mit der Kachel geteilt, damit
        // Automatik und manuelles Umschalten sich nicht ueberholen.
        SettingsWorker.submit {
            if (isTarget) {
                if (!Prefs.getSuppressed(this)) suppress(pkg)
            } else {
                if (Prefs.getSuppressed(this)) restore(pkg)
            }
        }
    }

    /** Aktuellen System-Wert merken und auf AUS setzen. */
    private fun suppress(triggerPkg: String) {
        val ns = Prefs.getNamespace(this)
        val key = Prefs.getKey(this)
        val current = SettingWriter.read(this, ns, key) ?: Prefs.getOn(this)
        // Zuerst merken, dann schreiben (Zustand bleibt korrekt, auch wenn Write scheitert).
        Prefs.setSuppressed(this, true, current)
        val res = SettingWriter.write(this, ns, key, Prefs.getOff(this))
        Log.i(TAG, "Ziel-App '$triggerPkg' -> $ns/$key AUS (gemerkt=$current) via ${res.method}, ok=${res.success}: ${res.message}")
    }

    /** Gemerkten System-Wert exakt wiederherstellen. */
    private fun restore(triggerPkg: String) {
        val ns = Prefs.getNamespace(this)
        val key = Prefs.getKey(this)
        val saved = Prefs.getSavedValue(this) ?: Prefs.getOn(this)
        val res = SettingWriter.write(this, ns, key, saved)
        Prefs.setSuppressed(this, false, null)
        Log.i(TAG, "App '$triggerPkg' -> $ns/$key Wiederherstellung auf $saved via ${res.method}, ok=${res.success}: ${res.message}")
    }

    /**
     * Package der aktiven Tastatur (DEFAULT_INPUT_METHOD = "pkg/.Klasse"). Live
     * gelesen statt fest verdrahtet, damit auch andere Tastaturen als Gboard nicht
     * als App-Wechsel zaehlen.
     */
    private fun currentImePackage(): String? =
        runCatching {
            Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')
        }.getOrNull()

    override fun onInterrupt() {}

    companion object {
        const val TAG = "PixelComfort"

        private val IGNORED_PACKAGES = setOf(
            "com.android.systemui",
            // Fallback, falls DEFAULT_INPUT_METHOD nicht lesbar ist
            "com.google.android.inputmethod.latin",
            // System-Dialoge (z. B. Chooser/Resolver aelterer Versionen)
            "android",
            // Teilen-Menue (Chooser) ab Android 14
            "com.android.intentresolver",
            // Berechtigungs-Dialoge (Kamera/Standort beim ersten Start)
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
        )

        fun componentName(ctx: Context): ComponentName =
            ComponentName(ctx, ComfortAccessibilityService::class.java)

        /** Ist unser Dienst in den Bedienungshilfen aktiviert? */
        fun isEnabled(ctx: Context): Boolean {
            val enabled = Settings.Secure.getInt(
                ctx.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0,
            )
            if (enabled != 1) return false
            val services = Settings.Secure.getString(
                ctx.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val expected = componentName(ctx).flattenToString()
            val expectedShort = componentName(ctx).flattenToShortString()
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(services)
            for (entry in splitter) {
                if (entry.equals(expected, ignoreCase = true) ||
                    entry.equals(expectedShort, ignoreCase = true)
                ) {
                    return true
                }
            }
            return false
        }
    }
}
