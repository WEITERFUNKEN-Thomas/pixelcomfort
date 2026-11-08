package com.noirdraco.pixelcomfort

/**
 * Mobilfunk-Symbol in der Statusleiste nur zeigen, wenn kein WLAN verbunden ist. Ohne
 * Android-Abhaengigkeit (damit per Unit-Test pruefbar); den Netzwechsel liefert der
 * [NetworkWatcher], die echte Umgebung [MobileIcon].
 *
 * Die System-UI blendet jedes Symbol aus, dessen Name in secure/icon_blacklist steht
 * (kommagetrennt). Regeln:
 * - Nur der eigene Eintrag [SLOT] wird hinzugefuegt oder entfernt, alle anderen bleiben.
 * - Ist die Liste nicht lesbar, wird nichts geschrieben - sonst gingen andere Eintraege
 *   verloren.
 * - Geschrieben wird nur, was vom Soll abweicht.
 * - Bleibt nach dem Entfernen nichts uebrig, wird der Key geloescht (Android-Standard).
 */
class MobileIconAutomation(private val env: Env) {

    /** Inhalt der Liste; [value] = null heisst: nie gesetzt. */
    data class Current(val value: String?)

    interface Env {
        val enabled: Boolean

        /** null = nicht lesbar */
        fun read(): Current?

        /** null = Key loeschen */
        fun write(value: String?): Outcome
        fun report(event: Event)
    }

    data class Outcome(val success: Boolean, val message: String)

    sealed interface Event {
        val success: Boolean

        data class Hidden(override val success: Boolean, val message: String) : Event
        data class Shown(override val success: Boolean, val message: String) : Event
    }

    fun onNetwork(onWifi: Boolean) {
        if (!env.enabled) return
        apply(hidden = onWifi)
    }

    /** Symbol wieder zeigen, egal ob die Funktion an ist - wenn sie oder der Dienst endet. */
    fun ensureShown() = apply(hidden = false)

    private fun apply(hidden: Boolean) {
        val current = env.read()
        if (current == null) {
            env.report(event(hidden, false, History.Reason.UNREADABLE))
            return
        }
        val entries = parse(current.value)
        if ((SLOT in entries) == hidden) return
        val target = if (hidden) entries + SLOT else entries - SLOT
        val res = env.write(target.joinToString(",").ifEmpty { null })
        env.report(event(hidden, res.success, res.message))
    }

    private fun event(hidden: Boolean, success: Boolean, message: String): Event =
        if (hidden) Event.Hidden(success, message) else Event.Shown(success, message)

    companion object {
        /** Name des Mobilfunk-Symbols (Balken und 4G/5G) in der System-UI. */
        const val SLOT = "mobile"

        fun parse(raw: String?): List<String> =
            raw.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }
}
