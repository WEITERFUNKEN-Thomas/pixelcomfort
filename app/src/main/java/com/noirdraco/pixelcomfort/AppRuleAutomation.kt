package com.noirdraco.pixelcomfort

/**
 * Eine Einstellung, die in bestimmten Apps einen festen Wert bekommt und danach zurueck
 * auf den vorherigen geht - fuer den Augenkomfort (in Kamera/Fotos aus) und fuers
 * automatische Drehen (in YouTube/Fotos an). Ohne Android-Abhaengigkeit (damit per
 * Unit-Test pruefbar). Der [ComfortAccessibilityService] liefert die Fenster-Wechsel und
 * die echte Umgebung ([Env]).
 *
 * Regeln:
 * - Ziel-App vorn: aktuellen Wert merken, dann [Env.forcedValue] schreiben. Steht er schon
 *   da, passiert nichts. Ist der Wert nicht lesbar (Shizuku laeuft nicht), wird NICHTS
 *   geschrieben - ein geratener Wert wuerde spaeter faelschlich "wiederhergestellt".
 * - Andere App vorn: gemerkten Wert zurueckschreiben. Scheitert das, bleibt er
 *   gemerkt und der naechste App-Wechsel versucht es erneut.
 * - Steht beim Verlassen nicht mehr [Env.forcedValue] da, wurde von Hand umgestellt:
 *   dann wird nichts zurueckgeschrieben.
 */
class AppRuleAutomation(private val env: Env) {

    interface Env {
        val forcedValue: String

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
        data class Forced(val pkg: String, val from: String, val success: Boolean, val message: String) : Event

        /** Nicht pausiert, weil der Wert nicht lesbar war. */
        data class Skipped(val pkg: String) : Event
        data class Restored(val pkg: String, val to: String, val success: Boolean, val message: String) : Event
    }

    /** Solange das Wiederherstellen scheitert, nur den ersten Fehlschlag melden. */
    private var restoreFailureReported = false

    /**
     * Ziel-App, fuer die "nicht lesbar" schon gemeldet wurde. Ein App-Start liefert oft
     * mehrere Fenster-Events; gemeldet wird nur das erste, bis eine andere App vorn ist.
     */
    private var skipReportedFor: String? = null

    fun onForeground(pkg: String, isTarget: Boolean) {
        if (isTarget) {
            if (!env.isSuppressed()) suppress(pkg)
        } else {
            skipReportedFor = null
            if (env.isSuppressed()) restore(pkg)
        }
    }

    /** Die Funktion wird abgeschaltet: ist die Regel gerade aktiv, sofort zurueckstellen. */
    fun release() {
        if (env.isSuppressed()) restore(pkg = "")
    }

    private fun suppress(pkg: String) {
        val current = env.read()
        if (current == null) {
            if (skipReportedFor != pkg) env.report(Event.Skipped(pkg))
            skipReportedFor = pkg
            return
        }
        skipReportedFor = null
        if (current == env.forcedValue) return
        // Zuerst merken, dann schreiben (Zustand bleibt korrekt, auch wenn Write scheitert).
        env.setState(true, current)
        restoreFailureReported = false
        val res = env.write(env.forcedValue)
        env.report(Event.Forced(pkg, current, res.success, res.message))
    }

    private fun restore(pkg: String) {
        val saved = env.savedValue()
        if (saved == null) {
            env.setState(false, null)
            return
        }
        // Waehrend die Ziel-App vorn war, von Hand umgestellt (z. B. ueber die
        // Schnelleinstellungen): diese Entscheidung gilt, nichts zurueckschreiben.
        // Nicht lesbar -> wie bisher zurueckschreiben.
        val current = env.read()
        if (current != null && current != env.forcedValue) {
            env.setState(false, null)
            restoreFailureReported = false
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
