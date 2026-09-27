package com.noirdraco.pixelcomfort

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Konfiguration und Laufzeit-Zustand. Wird von der MainActivity (Setup) und vom
 * AccessibilityService gemeinsam genutzt.
 *
 * Bewusst simpel: Es wird genau EINE Einstellung verwaltet – der Augenkomfort-Modus
 * (Comfort View). Die App liest immer den aktuellen System-Wert, merkt ihn, schaltet
 * fuer die Ziel-Apps auf AUS und stellt danach exakt den gemerkten Wert wieder her.
 * Andere Comfort-Einstellungen (Dynamisch, Intensitaet) werden NICHT angefasst.
 *
 * Default aus der adb-Recherche (Pixel 10 Pro, Android 17): system/cv_enabled (1/0).
 */
object Prefs {
    private const val FILE = "pixelcomfort_prefs"

    /**
     * Laufzeit-Zustand (suppressed/saved_value) liegt in einer eigenen Datei, die in
     * backup_rules/data_extraction_rules vom Backup ausgeschlossen ist. Sonst wuerde
     * ein auf ein neues Geraet zurueckgespielter "suppressed=true"-Stand dort beim
     * ersten Fenster-Wechsel einen fremden Wert "wiederherstellen".
     */
    private const val STATE_FILE = "pixelcomfort_state"

    const val NS_SECURE = "secure"
    const val NS_SYSTEM = "system"
    const val NS_GLOBAL = "global"

    const val DEFAULT_NAMESPACE = NS_SYSTEM
    const val DEFAULT_KEY = "cv_enabled"
    const val DEFAULT_OFF = "0"
    const val DEFAULT_ON = "1"
    const val DEFAULT_PACKAGES =
        "com.google.android.GoogleCamera,com.google.android.apps.photos"

    private const val K_NAMESPACE = "namespace"
    private const val K_KEY = "key"
    private const val K_OFF = "value_off"
    private const val K_ON = "value_on"
    private const val K_PACKAGES = "packages"
    private const val K_SUPPRESSED = "suppressed"
    private const val K_SAVED_VALUE = "saved_value"

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun state(context: Context): SharedPreferences {
        val st = context.applicationContext.getSharedPreferences(STATE_FILE, Context.MODE_PRIVATE)
        migrateState(sp(context), st)
        return st
    }

    /** Bis v1.1 lag der Laufzeit-Zustand in der Konfigurationsdatei -> einmalig umziehen. */
    private fun migrateState(from: SharedPreferences, to: SharedPreferences) {
        if (!from.contains(K_SUPPRESSED) && !from.contains(K_SAVED_VALUE)) return
        to.edit {
            putBoolean(K_SUPPRESSED, from.getBoolean(K_SUPPRESSED, false))
            putString(K_SAVED_VALUE, from.getString(K_SAVED_VALUE, null))
        }
        from.edit {
            remove(K_SUPPRESSED)
            remove(K_SAVED_VALUE)
        }
    }

    // ---- Konfiguration ----

    fun getNamespace(c: Context) = sp(c).getString(K_NAMESPACE, DEFAULT_NAMESPACE) ?: DEFAULT_NAMESPACE
    fun getKey(c: Context) = sp(c).getString(K_KEY, DEFAULT_KEY) ?: DEFAULT_KEY
    fun getOff(c: Context) = sp(c).getString(K_OFF, DEFAULT_OFF) ?: DEFAULT_OFF
    fun getOn(c: Context) = sp(c).getString(K_ON, DEFAULT_ON) ?: DEFAULT_ON
    fun getPackagesRaw(c: Context) = sp(c).getString(K_PACKAGES, DEFAULT_PACKAGES) ?: DEFAULT_PACKAGES

    fun getPackages(c: Context): Set<String> =
        getPackagesRaw(c).split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    fun saveConfig(
        c: Context,
        namespace: String,
        key: String,
        off: String,
        on: String,
        packages: String,
    ) {
        sp(c).edit {
            putString(K_NAMESPACE, namespace)
            putString(K_KEY, key)
            putString(K_OFF, off)
            putString(K_ON, on)
            putString(K_PACKAGES, packages)
        }
    }

    /** Nur die Ziel-Apps speichern (App-Auswahl), Rest der Konfiguration bleibt. */
    fun setPackages(c: Context, packages: Set<String>) {
        sp(c).edit { putString(K_PACKAGES, AppList.serialize(packages)) }
    }

    // ---- Laufzeit-Zustand ----

    fun getSuppressed(c: Context): Boolean = state(c).getBoolean(K_SUPPRESSED, false)
    fun getSavedValue(c: Context): String? = state(c).getString(K_SAVED_VALUE, null)

    fun setSuppressed(c: Context, suppressed: Boolean, savedValue: String?) {
        state(c).edit {
            putBoolean(K_SUPPRESSED, suppressed)
            putString(K_SAVED_VALUE, savedValue)
        }
    }

    /**
     * Nur den gemerkten Wert korrigieren, ohne das suppressed-Flag anzufassen.
     * Wird gebraucht, wenn per Kachel von Hand umgeschaltet wird, waehrend eine
     * Ziel-App vorn ist - sonst wuerde restore() diese Entscheidung ueberschreiben.
     */
    fun setSavedValue(c: Context, savedValue: String) {
        state(c).edit { putString(K_SAVED_VALUE, savedValue) }
    }
}
