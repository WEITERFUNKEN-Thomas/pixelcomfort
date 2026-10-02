package com.noirdraco.pixelcomfort

import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * EIN gemeinsamer Worker-Thread fuer alle Zugriffe auf die Comfort-Einstellung.
 *
 * Zwei Gruende:
 * 1. Shizuku-Aufrufe dauern 100-250 ms und duerfen nie auf dem Main-Thread laufen
 *    (weder im AccessibilityService noch in der Kachel).
 * 2. Automatik (suppress/restore) und manuelles Umschalten ueber die Kachel
 *    schreiben denselben Key. Ein gemeinsamer serieller Executor garantiert, dass
 *    sie sich nicht ueberholen - ein read-modify-write der Kachel kann also nicht
 *    mitten in ein suppress() der Automatik hineinlaufen.
 *
 * Bewusst ein Prozess-weites Singleton statt eines Executors pro Komponente: der
 * AccessibilityService kann neu gebunden werden und die Kachel wird unabhaengig
 * davon erzeugt und zerstoert - beide muessen trotzdem dieselbe Warteschlange
 * benutzen.
 */
object SettingsWorker {

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "pixelcomfort-settings").apply { isDaemon = true }
    }

    /**
     * Wohin eine Ausnahme aus einer Aufgabe geht. Im Test austauschbar (android.util.Log
     * gibt es dort nicht).
     */
    @Volatile
    internal var onError: (Throwable) -> Unit = { Log.e(ComfortAccessibilityService.TAG, "Fehler im Settings-Worker", it) }

    /**
     * Eine Ausnahme wird gefangen und geloggt: Liefe sie bis zum Thread durch, beendete
     * Android den ganzen Prozess - mit ihm die Bedienungshilfe, die zu Hause privates DNS
     * ausgeschaltet hat und es unterwegs erst nach dem Neustart des Dienstes wieder eintruege.
     */
    fun submit(task: () -> Unit) = executor.execute {
        try {
            task()
        } catch (t: Throwable) {
            runCatching { onError(t) }
        }
    }

    /**
     * Wie [submit], wartet aber bis zu [timeoutMs] auf das Ende - fuer Arbeit, die noch
     * erledigt sein muss, bevor der Prozess enden kann (Abmelden der Bedienungshilfe).
     */
    fun submitAndWait(timeoutMs: Long, task: () -> Unit) {
        runCatching { executor.submit(task).get(timeoutMs, TimeUnit.MILLISECONDS) }
    }
}
