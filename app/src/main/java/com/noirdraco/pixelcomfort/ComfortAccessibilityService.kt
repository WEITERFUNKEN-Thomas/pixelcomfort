package com.noirdraco.pixelcomfort

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.noirdraco.pixelcomfort.AppRuleAutomation.Event
import com.noirdraco.pixelcomfort.aod.AodMonitor
import rikka.shizuku.Shizuku

/**
 * Lauscht auf Fenster-Wechsel. Sobald eine der Ziel-Apps (Kamera/Fotos) in den
 * Vordergrund kommt, wird der aktuelle Comfort-View-Wert des Systems gemerkt und auf
 * AUS gesetzt. Beim Wechsel zu einer anderen App wird der gemerkte Wert exakt wieder
 * hergestellt. Andere Einstellungen werden nicht angefasst. Die Entscheidungen selbst
 * stehen in [AppRuleAutomation]. Welche App wirklich vorn ist, sagt [ForegroundApp]
 * (Nutzungsdaten); ohne diesen Zugriff entscheidet [WindowSwitch].
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
    private val automation = AppRuleAutomation(ServiceEnv())

    private val startupCheck = Runnable { ShizukuWarning.check(this) }

    /** Filtert spaete Fenster schon verlassener Apps ([WindowSwitch]). Nur Main-Thread. */
    private val windowSwitch = WindowSwitch()

    /** Package -> fuer uns sichtbar? Nur Main-Thread. */
    private val visibleCache = HashMap<String, Boolean>()

    /** "pkg/Klasse" -> ist eine Activity? Nur Main-Thread. */
    private val activityCache = HashMap<String, Boolean>()

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
        // Das System meldet sofort das aktuelle Netz; die DNS-Automatik stimmt sich
        // dann selbst ab (Schreiben beim Verbinden ist hier richtig, s. DnsAutomation).
        NetworkWatcher.start(this)
        // Always-On-Display-Regeln (Bluetooth/WLAN) laufen in diesem Dienst mit.
        AodMonitor.start(this)
        SettingsWorker.submit { restoreLegacyRotation() }
    }

    /**
     * "Automatisch drehen in Apps" gab es von 2.0 bis 2.0.1. War es beim Update gerade fuer
     * eine App eingeschaltet, stellt niemand mehr zurueck - das passiert hier einmalig.
     * Steht nicht mehr 1 da, wurde von Hand umgestellt: dann bleibt es dabei.
     */
    private fun restoreLegacyRotation() {
        val saved = Prefs.takeLegacyRotateRestore(this) ?: return
        if (SettingWriter.read(this, Prefs.NS_SYSTEM, LEGACY_ROTATE_KEY) != "1") return
        val res = SettingWriter.write(this, Prefs.NS_SYSTEM, LEGACY_ROTATE_KEY, saved)
        Log.i(TAG, "Drehen (entfernt): zurueck auf $LEGACY_ROTATE_KEY=$saved ok=${res.success} ${res.message}")
    }

    /**
     * Die Bedienungshilfe wird abgeschaltet: privates DNS darf dann nicht ausgeschaltet
     * zurueckbleiben, sonst waere es unterwegs aus, ohne dass jemand es wieder eintraegt.
     * Ebenso kommt das Mobilfunk-Symbol zurueck. DNS zuerst - es ist sicherheitsrelevant.
     */
    override fun onUnbind(intent: Intent?): Boolean {
        NetworkWatcher.stop()
        val ctx = applicationContext
        val dns = Prefs.getDnsEnabled(ctx)
        val icon = Prefs.getHideMobileOnWifi(ctx)
        if (dns || icon) {
            SettingsWorker.submitAndWait(UNBIND_TIMEOUT_MS) {
                if (dns) PrivateDns.automation(ctx).ensureAway()
                if (icon) MobileIcon.automation(ctx).ensureShown()
            }
        }
        AodMonitor.stopAndRestore(ctx, UNBIND_TIMEOUT_MS)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        main.removeCallbacks(startupCheck)
        AodMonitor.stop()
        NetworkWatcher.stop()
        runCatching { Shizuku.removeBinderReceivedListener(shizukuConnected) }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()
        if (pkg.isNullOrBlank()) return
        Log.d(TAG, "Fenster: $pkg / ${event.className}")
        // Overlays wie Benachrichtigungs-Shade / Tastatur / Dialoge / eigene App
        // ignorieren, damit sie ueber der Kamera keine vorzeitige Wiederherstellung
        // ausloesen.
        if (isIgnored(pkg)) return
        val cls = event.className?.toString()
        val time = event.eventTime.takeIf { it > 0 } ?: SystemClock.uptimeMillis()
        // Immer fragen, damit WindowSwitch den Verlauf kennt - auch wenn Android antwortet.
        val heuristicSwitch = isAppSwitch(pkg, cls, time)

        // Alles Weitere auf dem Worker: die Nutzungsdaten-Abfrage blockiert kurz, und die
        // Suppressed-Pruefung muss IM Task passieren, damit schnell aufeinanderfolgende Events
        // (Kamera -> Launcher) seriell und ohne Doppel-Schreiben verarbeitet werden.
        // SettingsWorker ist prozessweit und wird mit der Kachel geteilt, damit
        // Automatik und manuelles Umschalten sich nicht ueberholen.
        SettingsWorker.submit {
            val resumed = ForegroundApp.lastResumed(this)
            val front = ForegroundApp.decide(pkg, resumed, heuristicSwitch, ::isIgnored)
            when {
                front == null && resumed == null ->
                    Log.d(TAG, "Spaetes Fenster einer eben verlassenen App, kein App-Wechsel: $pkg / $cls")

                front == null -> Log.d(TAG, "Vorn laut Android: $resumed (ignoriert), Fenster von $pkg")
                front != pkg -> Log.d(TAG, "Vorn laut Android: $front, Fenster von $pkg / $cls")
            }
            if (front != null) automation.onForeground(front, front in Prefs.getPackages(this))
        }
    }

    /** Overlays wie Shade, Tastatur, Dialoge und die eigene App sind kein App-Wechsel. */
    private fun isIgnored(pkg: String): Boolean =
        pkg == packageName || pkg in IGNORED_PACKAGES || pkg == currentImePackage()

    /** Die echte Umgebung der Automatik: Settings, gemerkter Zustand, Log, Verlauf, Warnung. */
    private inner class ServiceEnv : AppRuleAutomation.Env {
        private val ctx: Context get() = this@ComfortAccessibilityService
        private val ns get() = Prefs.getNamespace(ctx)
        private val key get() = Prefs.getKey(ctx)

        override val forcedValue: String get() = Prefs.getOff(ctx)

        override fun read(): String? = SettingWriter.read(ctx, ns, key)

        override fun write(value: String): AppRuleAutomation.Outcome {
            val res = SettingWriter.write(ctx, ns, key, value)
            return AppRuleAutomation.Outcome(res.success, "via ${res.method}: ${res.message}")
        }

        override fun isSuppressed(): Boolean = Prefs.getSuppressed(ctx)
        override fun savedValue(): String? = Prefs.getSavedValue(ctx)
        override fun setState(suppressed: Boolean, savedValue: String?) =
            Prefs.setSuppressed(ctx, suppressed, savedValue)

        override fun report(event: Event) {
            val off = forcedValue
            val now = System.currentTimeMillis()
            val entry = when (event) {
                is Event.Forced -> {
                    Log.i(TAG, "Ziel-App '${event.pkg}' -> $ns/$key AUS (gemerkt=${event.from}) ok=${event.success} ${event.message}")
                    History.Entry(
                        now, History.Kind.PAUSE, ctx.appLabel(event.pkg),
                        from = History.comfortToken(event.from, off),
                        to = History.OFF,
                        success = event.success,
                        message = if (event.success) "" else ShizukuShell.explain(event.message),
                    )
                }

                is Event.Skipped -> {
                    Log.w(TAG, "Ziel-App '${event.pkg}' -> $ns/$key nicht lesbar, es wird NICHT pausiert")
                    History.Entry(
                        now, History.Kind.SKIP, ctx.appLabel(event.pkg),
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
    }

    /** Siehe [WindowSwitch]. Fragt den PackageManager einmal je Package bzw. Fenster-Klasse. */
    private fun isAppSwitch(pkg: String, cls: String?, time: Long): Boolean {
        val pm = packageManager
        val visible = visibleCache.getOrPut(pkg) {
            runCatching { pm.getApplicationInfo(pkg, 0) }.isSuccess
        }
        return windowSwitch.isAppSwitch(pkg, cls, visible, time) { c ->
            activityCache.getOrPut("$pkg/$c") {
                runCatching { pm.getActivityInfo(ComponentName(pkg, c), 0) }.isSuccess
            }
        }
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

        /** So lange darf das Eintragen von privatem DNS beim Abmelden hoechstens dauern. */
        private const val UNBIND_TIMEOUT_MS = 3_000L

        private const val LEGACY_ROTATE_KEY = "accelerometer_rotation"

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
