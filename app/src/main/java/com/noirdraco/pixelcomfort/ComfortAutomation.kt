package com.noirdraco.pixelcomfort

/**
 * Die Entscheidungen der Automatik, ohne Android-Abhaengigkeit (damit per Unit-Test
 * pruefbar). Der [ComfortAccessibilityService] liefert die Fenster-Wechsel und die
 * echte Umgebung ([Env]).
 *
 * Regeln:
 * - Ziel-App vorn: aktuellen Wert merken, dann ausschalten. War der Filter schon aus,
 *   passiert nichts. Ist der Wert nicht lesbar (Shizuku laeuft nicht), wird NICHT
 *   pausiert - ein geratener Wert wuerde spaeter faelschlich "wiederhergestellt".
 * - Andere App vorn: gemerkten Wert zurueckschreiben. Scheitert das, bleibt er
 *   gemerkt und der naechste App-Wechsel versucht es erneut.
 */
class ComfortAutomation(private val env: Env) {

    interface Env {
        val offValue: String

        /** null = nicht lesbar */
        fun read(): String?
        fun write(value: String): Outcome
        fun isSuppressed(): Boolean
        fun savedValue(): String?
        fun setState(suppressed: Boolean, savedValue: String?)
        fun report(event: Event)
    }

    data class Outcome(val success: Boolean, val message: String)

    sealed interface Event {
        data class Paused(val pkg: String, val from: String, val success: Boolean, val message: String) : Event

        /** Nicht pausiert, weil der Wert nicht lesbar war. */
        data class Skipped(val pkg: String) : Event
        data class Restored(val pkg: String, val to: String, val success: Boolean, val message: String) : Event
    }

    /** Solange das Wiederherstellen scheitert, nur den ersten Fehlschlag melden. */
    private var restoreFailureReported = false

    fun onForeground(pkg: String, isTarget: Boolean) {
        if (isTarget) {
            if (!env.isSuppressed()) suppress(pkg)
        } else {
            if (env.isSuppressed()) restore(pkg)
        }
    }

    private fun suppress(pkg: String) {
        val current = env.read()
        if (current == null) {
            env.report(Event.Skipped(pkg))
            return
        }
        if (current == env.offValue) return
        // Zuerst merken, dann schreiben (Zustand bleibt korrekt, auch wenn Write scheitert).
        env.setState(true, current)
        restoreFailureReported = false
        val res = env.write(env.offValue)
        env.report(Event.Paused(pkg, current, res.success, res.message))
    }

    private fun restore(pkg: String) {
        val saved = env.savedValue()
        if (saved == null) {
            env.setState(false, null)
            return
        }
        val res = env.write(saved)
        if (res.success) {
            env.setState(false, null)
            restoreFailureReported = false
            env.report(Event.Restored(pkg, saved, true, res.message))
        } else if (!restoreFailureReported) {
            restoreFailureReported = true
            env.report(Event.Restored(pkg, saved, false, res.message))
        }
    }
}
