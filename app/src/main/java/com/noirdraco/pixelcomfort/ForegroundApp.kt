package com.noirdraco.pixelcomfort

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process

/**
 * Welche App ist vorn? Die Fenster-Ereignisse der Bedienungshilfe sagen das nicht sicher
 * (spaete Fenster, Fenster ohne Activity). Android fuehrt aber selbst Buch: das letzte
 * ACTIVITY_RESUMED in den Nutzungsdaten. Am Pixel stand es bei jedem Fenster-Ereignis schon
 * 150-370 ms vorher fest, eine Abfrage dauert wenige ms.
 *
 * Braucht "Zugriff auf Nutzungsdaten" (GET_USAGE_STATS). Fehlt er, entscheidet wie bisher
 * die Heuristik aus [WindowSwitch]. Gelesen wird nur das letzte Ereignis, nichts gespeichert.
 */
internal object ForegroundApp {

    /** So weit zurueck wird gesucht. Ohne Treffer entscheidet die Heuristik. */
    private const val LOOKBACK_MS = 60_000L

    /**
     * Welche App gilt nach einem Fenster-Ereignis von [eventPkg] als vorn? null = kein
     * App-Wechsel. [resumed]: laut Android zuletzt geoeffnete App (null = kein Zugriff oder
     * kein Treffer). [heuristicSwitch]: Urteil von [WindowSwitch].
     */
    fun decide(
        eventPkg: String,
        resumed: String?,
        heuristicSwitch: Boolean,
        isIgnored: (String) -> Boolean,
    ): String? = when {
        resumed != null -> resumed.takeUnless(isIgnored)
        heuristicSwitch -> eventPkg
        else -> null
    }

    fun hasAccess(context: Context): Boolean = runCatching {
        val ops = context.getSystemService(AppOpsManager::class.java)
        when (ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT ->
                context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED

            else -> false
        }
    }.getOrDefault(false)

    /** Zuletzt geoeffnete App laut Android, null ohne Zugriff oder Treffer. Blockiert kurz. */
    fun lastResumed(context: Context): String? {
        if (!hasAccess(context)) return null
        return runCatching {
            val usm = context.getSystemService(UsageStatsManager::class.java)
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - LOOKBACK_MS, now)
            val event = UsageEvents.Event()
            var last: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last = event.packageName
            }
            last
        }.getOrNull()
    }
}
