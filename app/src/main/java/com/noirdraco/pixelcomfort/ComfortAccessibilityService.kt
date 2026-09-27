package com.noirdraco.pixelcomfort

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.noirdraco.pixelcomfort.ComfortAutomation.Event
import rikka.shizuku.Shizuku

/**
 * Lauscht auf Fenster-Wechsel. Sobald eine der Ziel-Apps (Kamera/Fotos) in den
 * Vordergrund kommt, wird der aktuelle Comfort-View-Wert des Systems gemerkt und auf
 * AUS gesetzt. Beim Wechsel zu einer anderen App wird der gemerkte Wert exakt wieder
 * hergestellt. Andere Einstellungen werden nicht angefasst. Die Entscheidungen selbst
 * stehen in [ComfortAutomation].
 *
 * Beim (Neu-)Verbinden des Dienstes wird bewusst NICHTS geschrieben: Ohne
 * canRetrieveWindowContent laesst sich die Vordergrund-App hier nicht ermitteln, und
 * ein blindes Wiederherstellen wuerde den Filter mitten in der Kamera wieder
 * einschalten. Der persistierte Zustand (suppressed/savedValue) wird stattdessen vom
 * naechsten Fenster-Event korrekt aufgeloest – auch nach einem Geraete-Neustart.
 *
 * Nebenbei wacht der Dienst ueber Shizuku: Das System startet ihn nach einem
 * Geraete-Neustart ohnehin, also prueft er kurz danach, ob Shizuku laeuft
 * ([ShizukuWarning]).
 */
class ComfortAccessibilityService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private val automation = ComfortAutomation(ServiceEnv())

    private val startupCheck = Runnable { ShizukuWarning.check(this) }

    private val shizukuConnected = Shizuku.OnBinderReceivedListener {
        if (ShizukuShell.isReady()) ShizukuWarning.clear(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(
            TAG,
            "Dienst verbunden. Ziel-Packages=${Prefs.getPackages(this)}, " +
                "suppressed=${Prefs.getSuppressed(this)} (Aufloesung beim naechsten Fenster-Event)",
        )
        runCatching { Shizuku.addBinderReceivedListenerSticky(shizukuConnected) }
        main.removeCallbacks(startupCheck)
        main.postDelayed(startupCheck, STARTUP_CHECK_DELAY_MS)
    }

    override fun onDestroy() {
        main.removeCallbacks(startupCheck)
        runCatching { Shizuku.removeBinderReceivedListener(shizukuConnected) }
        super.onDestroy()
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
        SettingsWorker.submit { automation.onForeground(pkg, isTarget) }
    }

    /** Die echte Umgebung der Automatik: Settings, gemerkter Zustand, Log, Verlauf, Warnung. */
    private inner class ServiceEnv : ComfortAutomation.Env {
        private val ctx: Context get() = this@ComfortAccessibilityService
        private val ns get() = Prefs.getNamespace(ctx)
        private val key get() = Prefs.getKey(ctx)

        override val offValue: String get() = Prefs.getOff(ctx)

        override fun read(): String? = SettingWriter.read(ctx, ns, key)

        override fun write(value: String): ComfortAutomation.Outcome {
            val res = SettingWriter.write(ctx, ns, key, value)
            return ComfortAutomation.Outcome(res.success, "via ${res.method}: ${res.message}")
        }

        override fun isSuppressed(): Boolean = Prefs.getSuppressed(ctx)
        override fun savedValue(): String? = Prefs.getSavedValue(ctx)
        override fun setState(suppressed: Boolean, savedValue: String?) =
            Prefs.setSuppressed(ctx, suppressed, savedValue)

        override fun report(event: Event) {
            val off = offValue
            val now = System.currentTimeMillis()
            val entry = when (event) {
                is Event.Paused -> {
                    Log.i(TAG, "Ziel-App '${event.pkg}' -> $ns/$key AUS (gemerkt=${event.from}) ok=${event.success} ${event.message}")
                    History.Entry(
                        now, History.Kind.PAUSE, label(event.pkg),
                        from = History.comfortToken(event.from, off),
                        to = History.OFF,
                        success = event.success,
                        message = if (event.success) "" else ShizukuShell.explain(event.message),
                    )
                }

                is Event.Skipped -> {
                    Log.w(TAG, "Ziel-App '${event.pkg}' -> $ns/$key nicht lesbar, es wird NICHT pausiert")
                    History.Entry(
                        now, History.Kind.SKIP, label(event.pkg),
                        success = false,
                        message = ShizukuShell.explain(History.Reason.UNREADABLE),
                    )
                }

                is Event.Restored -> {
                    Log.i(TAG, "App '${event.pkg}' -> $ns/$key Wiederherstellung auf ${event.to} ok=${event.success} ${event.message}")
                    History.Entry(
                        now, History.Kind.RESTORE,
                        from = History.OFF,
                        to = History.comfortToken(event.to, off),
                        success = event.success,
                        message = if (event.success) "" else ShizukuShell.explain(event.message),
                    )
                }
            }
            HistoryStore.add(ctx, entry)
            if (!entry.success) ShizukuWarning.check(ctx)
        }

        private fun label(pkg: String): String =
            runCatching { packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString() }
                .getOrDefault(pkg)
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

        /**
         * Wartezeit nach dem Start des Dienstes, bevor Shizuku geprueft wird. Nach einem
         * Geraete-Neustart braucht ein automatisch startendes Shizuku einen Moment.
         */
        private const val STARTUP_CHECK_DELAY_MS = 90_000L

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
