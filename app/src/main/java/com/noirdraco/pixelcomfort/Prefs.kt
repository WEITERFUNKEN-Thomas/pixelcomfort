package com.noirdraco.pixelcomfort

import android.content.Context
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

    // ---- Laufzeit-Zustand ----

    fun getSuppressed(c: Context): Boolean = sp(c).getBoolean(K_SUPPRESSED, false)
    fun getSavedValue(c: Context): String? = sp(c).getString(K_SAVED_VALUE, null)

    fun setSuppressed(c: Context, suppressed: Boolean, savedValue: String?) {
        sp(c).edit {
            putBoolean(K_SUPPRESSED, suppressed)
            putString(K_SAVED_VALUE, savedValue)
        }
    }
}
