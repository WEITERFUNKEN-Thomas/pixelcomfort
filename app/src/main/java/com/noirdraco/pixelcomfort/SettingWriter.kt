package com.noirdraco.pixelcomfort

import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * Liest und schreibt einen Settings-Wert in einem der drei Namespaces.
 *
 * Schreib-Strategie: zuerst direkt ueber [android.content.ContentResolver]
 * (Settings.System/Secure/Global). Wenn das eine SecurityException wirft oder der
 * Wert nach dem Schreiben nicht wirklich anliegt, wird automatisch auf einen
 * Shizuku-Shell-Write (`settings put ...`) zurueckgefallen.
 *
 * Hintergrund: cv_enabled liegt im "system"-Namespace. Ob eine Fremd-App diesen
 * konkreten System-Key ueber ContentResolver schreiben darf, ist je nach Android-
 * Build nicht garantiert – der Shizuku-Fallback macht das Schreiben trotzdem sicher.
 */
object SettingWriter {

    private const val TAG = ComfortAccessibilityService.TAG

    enum class Method { CONTENT_RESOLVER, SHIZUKU, NONE }

    data class WriteResult(
        val success: Boolean,
        val method: Method,
        val message: String,
    )

    private val VALID_NAMESPACES = setOf(Prefs.NS_SYSTEM, Prefs.NS_SECURE, Prefs.NS_GLOBAL)

    /** Settings-Keys: Buchstaben, Ziffern, `_`, `.`, `-` - aber kein fuehrendes `-` (Option). */
    private val KEY_PATTERN = Regex("^[A-Za-z0-9_.][A-Za-z0-9_.-]*$")

    /** Werte: nicht leer, ohne Leer-/Steuerzeichen. */
    private val VALUE_PATTERN = Regex("^\\S+$")

    /**
     * Prueft Namespace/Key/Wert aus der Konfiguration, bevor sie an Shizuku gehen.
     * Die Shell ist zwar schon aussen vor (Argument-Array), aber so landen auch keine
     * unsinnigen Keys oder als Option missdeutbare Argumente beim settings-Kommando.
     */
    private fun validate(namespace: String, key: String, value: String? = null): String? = when {
        namespace !in VALID_NAMESPACES -> "Invalid namespace '$namespace'"
        !KEY_PATTERN.matches(key) -> "Invalid settings key '$key'"
        value != null && !VALUE_PATTERN.matches(value) -> "Invalid value '$value'"
        else -> null
    }

    fun read(context: Context, namespace: String, key: String): String? {
        validate(namespace, key)?.let {
            Log.w(TAG, "read abgelehnt: $it")
            return null
        }
        // 1) ContentResolver. Fuer nicht-oeffentliche System-Keys (z.B. cv_enabled)
        //    liefert das null, weil Fremd-Apps sie nicht lesen duerfen.
        val cr = context.contentResolver
        val crValue = try {
            when (namespace) {
                Prefs.NS_SYSTEM -> Settings.System.getString(cr, key)
                Prefs.NS_SECURE -> Settings.Secure.getString(cr, key)
                Prefs.NS_GLOBAL -> Settings.Global.getString(cr, key)
                else -> null
            }
        } catch (t: Throwable) {
            // Erwartbar fuer nicht-oeffentliche @hide-Keys (z.B. cv_*) -> Shizuku liest.
            Log.d(TAG, "read($namespace/$key) via ContentResolver nicht moeglich -> Shizuku")
            null
        }
        if (crValue != null) return crValue

        // 2) Shizuku-Fallback
        if (ShizukuShell.isReady()) {
            return try {
                val v = ShizukuShell.run("settings", "get", namespace, key).out.trim()
                if (v.isEmpty() || v == "null") null else v
            } catch (t: Throwable) {
                Log.w(TAG, "read($namespace/$key) via Shizuku fehlgeschlagen", t)
                null
            }
        }
        return null
    }

    fun write(context: Context, namespace: String, key: String, value: String): WriteResult {
        validate(namespace, key, value)?.let { return WriteResult(false, Method.NONE, it) }

        // 1) Versuch ueber ContentResolver
        val crError: String? = try {
            val ok = putViaContentResolver(context, namespace, key, value)
            if (ok && read(context, namespace, key) == value) {
                return WriteResult(true, Method.CONTENT_RESOLVER, "ContentResolver: $key=$value")
            }
            if (!ok) "putString returned false" else "value not applied after write"
        } catch (t: Throwable) {
            // Erwartbar fuer nicht-oeffentliche System-Keys wie cv_enabled -> Shizuku uebernimmt.
            val msg = t.javaClass.simpleName + (t.message?.let { ": $it" } ?: "")
            Log.d(TAG, "ContentResolver-Write nicht moeglich ($msg) -> Shizuku-Fallback")
            msg
        }

        // 2) Fallback ueber Shizuku-Shell
        if (ShizukuShell.isReady()) {
            return try {
                val res = ShizukuShell.run("settings", "put", namespace, key, value)
                val readBack = read(context, namespace, key)
                if (readBack == value) {
                    WriteResult(true, Method.SHIZUKU, "Shizuku shell (ContentResolver fallback): $key=$value")
                } else {
                    WriteResult(
                        false,
                        Method.SHIZUKU,
                        "Shizuku write had no effect (exit=${res.exitCode}, err=${res.err}, read=$readBack)",
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Shizuku-Write fehlgeschlagen", t)
                WriteResult(false, Method.SHIZUKU, "Shizuku error: ${t.message}")
            }
        }

        return WriteResult(
            false,
            Method.NONE,
            "ContentResolver failed ($crError) and Shizuku not available",
        )
    }

    private fun putViaContentResolver(
        context: Context,
        namespace: String,
        key: String,
        value: String,
    ): Boolean {
        val cr = context.contentResolver
        return when (namespace) {
            Prefs.NS_SYSTEM -> Settings.System.putString(cr, key, value)
            Prefs.NS_SECURE -> Settings.Secure.putString(cr, key, value)
            Prefs.NS_GLOBAL -> Settings.Global.putString(cr, key, value)
            else -> false
        }
    }
}
