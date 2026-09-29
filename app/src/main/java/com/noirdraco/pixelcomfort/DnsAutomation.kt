package com.noirdraco.pixelcomfort

/**
 * Die Entscheidungen der DNS-Automatik, ohne Android-Abhaengigkeit (damit per Unit-Test
 * pruefbar). Der [DnsWatcher] liefert die Netzwechsel, [PrivateDns] die echte Umgebung.
 *
 * Regeln:
 * - Zu Hause (gesichertes WLAN aus der Liste): privates DNS aus, damit das Heimnetz-DNS
 *   (AdGuard Home) greift. Ein von Hand geaenderter DNS-Name wird vorher gemerkt.
 * - Sonst - auch ohne Netz oder wenn der WLAN-Name nicht lesbar ist: privates DNS mit
 *   dem gemerkten Namen an. Im Zweifel also immer AN.
 * - Geschrieben wird nur, was vom Soll abweicht.
 */
class DnsAutomation(private val env: Env) {

    enum class Security {
        /** Mit Passwort bzw. Zertifikat (WPA2/WPA3/Enterprise). */
        SECURED,

        /** Offen oder OWE: jeder kann so ein Netz mit beliebigem Namen aufmachen. */
        OPEN,
        UNKNOWN,
    }

    /** Das aktive Standard-Netz; null = kein Netz. */
    data class Network(val isWifi: Boolean, val ssid: String?, val security: Security)

    interface Env {
        val enabled: Boolean
        val homeSsids: Set<String>

        /** Gemerkter DNS-Name fuer unterwegs. */
        var hostname: String

        /** null = nicht gesetzt bzw. nicht lesbar */
        fun readMode(): String?
        fun readSpecifier(): String?
        fun writeMode(value: String): Outcome
        fun writeSpecifier(value: String): Outcome
        fun report(event: Event)
    }

    data class Outcome(val success: Boolean, val message: String)

    sealed interface Event {
        val success: Boolean

        /** Zu Hause: privates DNS ausgeschaltet. */
        data class Home(val ssid: String, override val success: Boolean, val message: String) : Event

        /** Unterwegs: privates DNS mit [hostname] eingetragen. */
        data class Away(val hostname: String, override val success: Boolean, val message: String) : Event
    }

    fun onNetwork(network: Network?) {
        if (!env.enabled) return
        val home = homeSsid(network, env.homeSsids)
        if (home != null) goHome(home) else goAway()
    }

    /**
     * Privates DNS eintragen, unabhaengig vom Netz - wenn die Automatik oder der Dienst
     * endet, darf es nicht ausgeschaltet zurueckbleiben.
     */
    fun ensureAway() = goAway()

    private fun goHome(ssid: String) {
        val mode = env.readMode()
        if (mode == MODE_HOSTNAME) {
            val current = env.readSpecifier()
            if (current != null && isValidHostname(current) && current != env.hostname) env.hostname = current
        }
        if (mode == MODE_OFF) return
        val res = env.writeMode(MODE_OFF)
        env.report(Event.Home(ssid, res.success, res.message))
    }

    private fun goAway() {
        val name = env.hostname
        // Ohne gueltigen Namen wuerde "hostname" jede DNS-Aufloesung lahmlegen.
        if (!isValidHostname(name)) return
        val specifierOk = env.readSpecifier() == name
        val modeOk = env.readMode() == MODE_HOSTNAME
        if (specifierOk && modeOk) return

        // Erst der Name, dann der Modus - sonst waere kurz der alte Name aktiv.
        var res = Outcome(true, "")
        if (!specifierOk) {
            res = env.writeSpecifier(name)
            if (!res.success) {
                env.report(Event.Away(name, false, res.message))
                return
            }
        }
        if (!modeOk) res = env.writeMode(MODE_HOSTNAME)
        env.report(Event.Away(name, res.success, res.message))
    }

    companion object {
        const val MODE_OFF = "off"
        const val MODE_HOSTNAME = "hostname"

        private const val MAX_HOSTNAME = 253
        private val LABEL = Regex("^[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?$")

        /** SSID des Heim-WLANs, wenn [network] eins ist - sonst null. */
        fun homeSsid(network: Network?, homeSsids: Set<String>): String? {
            if (network == null || !network.isWifi || network.security != Security.SECURED) return null
            val ssid = network.ssid ?: return null
            return ssid.takeIf { it in homeSsids }
        }

        /** Mindestens zwei Labels, nur Buchstaben/Ziffern/Bindestrich, keine Leerzeichen. */
        fun isValidHostname(name: String): Boolean {
            if (name.isEmpty() || name.length > MAX_HOSTNAME) return false
            val labels = name.split('.')
            return labels.size >= 2 && labels.all { LABEL.matches(it) }
        }

        /** Android liefert die SSID in Anfuehrungszeichen, oder "<unknown ssid>" ohne Berechtigung. */
        fun cleanSsid(raw: String?): String? {
            if (raw == null || raw == UNKNOWN_SSID) return null
            val s = if (raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"')) raw.substring(1, raw.length - 1) else raw
            return s.ifEmpty { null }
        }

        private const val UNKNOWN_SSID = "<unknown ssid>"
    }
}
