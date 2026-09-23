package com.noirdraco.pixelcomfort

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

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
     * Obergrenze fuer einen Shizuku-Aufruf. Normal sind 100-250 ms; haengt ein
     * Aufruf, darf er den seriellen [SettingsWorker] nicht dauerhaft blockieren.
     */
    private const val TIMEOUT_MS = 5_000L

    /**
     * Fuehrt [args] direkt als Prozess aus - OHNE Shell. Argumente werden also nie
     * interpretiert (kein `;`, `$()`, Leerzeichen-Splitting); Werte aus der UI
     * koennen so keine weiteren Kommandos einschleusen.
     *
     * Shizuku.newProcess ist als @hide/@Deprecated markiert (Entfernung ab API 14
     * geplant), daher der Zugriff per Reflection.
     */
    fun run(vararg args: String): ShellResult {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        val process = method.invoke(null, arrayOf(*args), null, null) as ShizukuRemoteProcess

        // stdout und stderr parallel lesen: Nacheinander gelesen koennte ein volles
        // stderr-Puffer den Prozess blockieren, waehrend wir noch auf stdout warten.
        val out = StringBuilder()
        val err = StringBuilder()
        val readers = listOf(
            thread(name = "shizuku-stdout") {
                runCatching { out.append(process.inputStream.bufferedReader().use { it.readText() }) }
            },
            thread(name = "shizuku-stderr") {
                runCatching { err.append(process.errorStream.bufferedReader().use { it.readText() }) }
            },
        )

        // NICHT Process.waitFor(timeout): Das pollt exitValue() und erwartet dabei eine
        // IllegalThreadStateException - ueber Binder kommt aber ein anderer Typ an,
        // die Schleife bricht mit "process hasn't exited" ab. waitForTimeout wartet
        // stattdessen serverseitig.
        if (!process.waitForTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            // Zerstoeren schliesst auch die Streams, damit enden die Leser-Threads.
            process.destroy()
            readers.forEach { it.join(500) }
            throw TimeoutException("Shizuku-Aufruf nach $TIMEOUT_MS ms abgebrochen: ${args.joinToString(" ")}")
        }
        readers.forEach { it.join(TIMEOUT_MS) }
        return ShellResult(process.exitValue(), out.toString().trim(), err.toString().trim())
    }

    /** Nur fuer feste, im Code stehende Kommandos - niemals mit Nutzereingaben. */
    fun runShell(command: String): ShellResult = run("sh", "-c", command)
}
