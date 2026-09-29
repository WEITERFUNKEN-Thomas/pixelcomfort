package com.noirdraco.pixelcomfort

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/** Alles, was die Oberflaeche ueber den Zustand der App wissen muss. */
data class SetupStatus(
    val shizukuBinder: Boolean,
    val shizukuPermission: Boolean,
    val canWriteSystem: Boolean,
    val hasWriteSecure: Boolean,
    val accessibilityEnabled: Boolean,
    val advancedProtection: Boolean?,
    val currentValue: String?,
    val comfortOn: Boolean?,
    /** Die Automatik haelt den Filter gerade fuer eine Ziel-App unten. */
    val suppressed: Boolean,
    /** Anzeigenamen der Ziel-Apps (Paketname, falls nicht installiert). */
    val targetLabels: List<String>,
    /** Darf die App die Shizuku-Warnung zeigen? */
    val notificationsAllowed: Boolean,
    val lastEntry: History.Entry?,
    val dns: DnsStatus,
) {
    val shizukuReady: Boolean get() = shizukuBinder && shizukuPermission

    /** Automatik einsatzbereit? */
    val ready: Boolean get() = shizukuReady && accessibilityEnabled && advancedProtection != true

    companion object {
        /** Blockiert (Shizuku-Read) - nicht auf dem Main-Thread aufrufen. */
        fun read(context: Context): SetupStatus {
            val pm = context.packageManager
            return SetupStatus(
                shizukuBinder = ShizukuShell.isBinderAlive(),
                shizukuPermission = ShizukuShell.hasPermission(),
                canWriteSystem = runCatching { Settings.System.canWrite(context) }.getOrDefault(false),
                hasWriteSecure = context.checkSelfPermission(
                    android.Manifest.permission.WRITE_SECURE_SETTINGS,
                ) == PackageManager.PERMISSION_GRANTED,
                accessibilityEnabled = ComfortAccessibilityService.isEnabled(context),
                advancedProtection = readAdvancedProtection(context),
                currentValue = SettingWriter.read(context, Prefs.getNamespace(context), Prefs.getKey(context)),
                comfortOn = ComfortControl.isOn(context),
                suppressed = Prefs.getSuppressed(context),
                targetLabels = Prefs.getPackages(context).map { pkg ->
                    runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }.getOrDefault(pkg)
                },
                notificationsAllowed = ShizukuWarning.allowed(context),
                lastEntry = HistoryStore.load(context).firstOrNull(),
                dns = DnsStatus.read(context),
            )
        }

        /**
         * Erweiterten Schutz (Advanced Protection Mode) erkennen. Nur informativ; wenn
         * die API auf dem Geraet nicht zugaenglich ist, wird null zurueckgegeben.
         */
        private fun readAdvancedProtection(context: Context): Boolean? =
            try {
                val mgr = context.getSystemService("advanced_protection")
                mgr?.javaClass?.getMethod("isAdvancedProtectionEnabled")?.invoke(mgr) as? Boolean
            } catch (_: Throwable) {
                null
            }
    }
}
