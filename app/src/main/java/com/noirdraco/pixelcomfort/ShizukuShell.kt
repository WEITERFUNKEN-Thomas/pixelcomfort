package com.noirdraco.pixelcomfort

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

data class ShellResult(val exitCode: Int, val out: String, val err: String) {
    val success: Boolean get() = exitCode == 0
}

/**
 * Fuehrt Shell-Kommandos ueber Shizuku aus (ADB-Rechte ohne Root).
 *
 * Wird nur fuer den einmaligen Rechte-Setup (appops/pm grant) benoetigt und als
 * Fallback, falls das Schreiben ueber ContentResolver nicht durchgreift.
 */
object ShizukuShell {

    /** Ist Shizuku vorhanden, verbunden und hat unsere App die Shizuku-Freigabe? */
    fun isReady(): Boolean = try {
        Shizuku.pingBinder() &&
            !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun isBinderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    /**
     * Fuehrt [command] als `sh -c "<command>"` aus.
     *
     * Shizuku.newProcess ist als @hide/@Deprecated markiert (Entfernung ab API 14
     * geplant), daher der Zugriff per Reflection.
     */
    fun run(command: String): ShellResult {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        val process = method.invoke(
            null,
            arrayOf("sh", "-c", command),
            null,
            null,
        ) as Process

        val out = process.inputStream.bufferedReader().use { it.readText() }
        val err = process.errorStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        return ShellResult(code, out.trim(), err.trim())
    }
}
