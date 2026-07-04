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

    fun read(context: Context, namespace: String, key: String): String? {
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
                val v = ShizukuShell.run("settings get $namespace $key").out.trim()
                if (v.isEmpty() || v == "null") null else v
            } catch (t: Throwable) {
                Log.w(TAG, "read($namespace/$key) via Shizuku fehlgeschlagen", t)
                null
            }
        }
        return null
    }

    fun write(context: Context, namespace: String, key: String, value: String): WriteResult {
        // 1) Versuch ueber ContentResolver
        val crError: String? = try {
            val ok = putViaContentResolver(context, namespace, key, value)
            if (ok && read(context, namespace, key) == value) {
                return WriteResult(true, Method.CONTENT_RESOLVER, "ContentResolver: $key=$value")
            }
            if (!ok) "putString lieferte false" else "Wert liegt nach dem Schreiben nicht an"
        } catch (t: Throwable) {
            // Erwartbar fuer nicht-oeffentliche System-Keys wie cv_enabled -> Shizuku uebernimmt.
            val msg = t.javaClass.simpleName + (t.message?.let { ": $it" } ?: "")
            Log.d(TAG, "ContentResolver-Write nicht moeglich ($msg) -> Shizuku-Fallback")
            msg
        }

        // 2) Fallback ueber Shizuku-Shell
        if (ShizukuShell.isReady()) {
            return try {
                val res = ShizukuShell.run("settings put $namespace $key $value")
                val readBack = read(context, namespace, key)
                if (readBack == value) {
                    WriteResult(true, Method.SHIZUKU, "Shizuku-Shell (ContentResolver-Fallback): $key=$value")
                } else {
                    WriteResult(
                        false,
                        Method.SHIZUKU,
                        "Shizuku-Write ohne Wirkung (exit=${res.exitCode}, err=${res.err}, gelesen=$readBack)",
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Shizuku-Write fehlgeschlagen", t)
                WriteResult(false, Method.SHIZUKU, "Shizuku-Fehler: ${t.message}")
            }
        }

        return WriteResult(
            false,
            Method.NONE,
            "ContentResolver fehlgeschlagen ($crError) und Shizuku nicht verfuegbar",
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
