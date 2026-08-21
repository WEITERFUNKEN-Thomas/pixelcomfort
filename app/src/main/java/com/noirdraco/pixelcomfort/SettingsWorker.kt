package com.noirdraco.pixelcomfort

import java.util.concurrent.Executors

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

    fun submit(task: () -> Unit) = executor.execute(task)
}
