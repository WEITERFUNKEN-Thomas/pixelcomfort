package com.noirdraco.pixelcomfort

import com.noirdraco.pixelcomfort.DnsAutomation.Event
import com.noirdraco.pixelcomfort.DnsAutomation.Network
import com.noirdraco.pixelcomfort.DnsAutomation.Outcome
import com.noirdraco.pixelcomfort.DnsAutomation.Security
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsAutomationTest {

    /** Ein "Geraet" im Speicher: die beiden Private-DNS-Einstellungen, schreibbar oder nicht. */
    private class FakeEnv(var mode: String?, var specifier: String?) : DnsAutomation.Env {
        override var enabled = true
        override var homeSsids = setOf(HOME)
        override var hostname = NAME
        var writable = true
        val writes = mutableListOf<String>()
        val events = mutableListOf<Event>()

        override fun readMode() = mode
        override fun readSpecifier() = specifier

        override fun writeMode(value: String): Outcome {
            if (!writable) return Outcome(false, "nope")
            writes += "mode=$value"
            mode = value
            return Outcome(true, "ok")
        }

        override fun writeSpecifier(value: String): Outcome {
            if (!writable) return Outcome(false, "nope")
            writes += "specifier=$value"
            specifier = value
            return Outcome(true, "ok")
        }

        override fun report(event: Event) {
            events += event
        }
    }

    private companion object {
        const val HOME = "Neuland 2"
        const val NAME = "dns.adguard-dns.com"
        val home = Network(isWifi = true, ssid = HOME, security = Security.SECURED)
        val mobile = Network(isWifi = false, ssid = null, security = Security.UNKNOWN)
    }

    @Test
    fun `zu Hause wird privates DNS ausgeschaltet`() {
        val env = FakeEnv("hostname", NAME)
        DnsAutomation(env).onNetwork(home)

        assertEquals("off", env.mode)
        assertEquals(NAME, env.specifier)
        assertEquals(listOf<Event>(Event.Home(HOME, success = true, message = "ok")), env.events)
    }

    @Test
    fun `unterwegs wird erst der Name und dann der Modus geschrieben`() {
        val env = FakeEnv("off", null)
        DnsAutomation(env).onNetwork(mobile)

        assertEquals(listOf("specifier=$NAME", "mode=hostname"), env.writes)
        assertEquals(listOf<Event>(Event.Away(NAME, success = true, message = "ok")), env.events)
    }

    @Test
    fun `ohne Netz gilt unterwegs`() {
        val env = FakeEnv("off", NAME)
        DnsAutomation(env).onNetwork(null)

        assertEquals("hostname", env.mode)
    }

    @Test
    fun `fremdes WLAN gilt als unterwegs`() {
        val env = FakeEnv("off", NAME)
        DnsAutomation(env).onNetwork(Network(isWifi = true, ssid = "Cafe", security = Security.SECURED))

        assertEquals("hostname", env.mode)
    }

    @Test
    fun `offenes WLAN mit dem Heim-Namen gilt als unterwegs`() {
        val env = FakeEnv("off", NAME)
        DnsAutomation(env).onNetwork(Network(isWifi = true, ssid = HOME, security = Security.OPEN))

        assertEquals("hostname", env.mode)
    }

    @Test
    fun `unbekannte Sicherheitsart gilt als unterwegs`() {
        val env = FakeEnv("off", NAME)
        DnsAutomation(env).onNetwork(Network(isWifi = true, ssid = HOME, security = Security.UNKNOWN))

        assertEquals("hostname", env.mode)
    }

    @Test
    fun `nicht lesbarer WLAN-Name gilt als unterwegs`() {
        val env = FakeEnv("off", NAME)
        DnsAutomation(env).onNetwork(Network(isWifi = true, ssid = null, security = Security.SECURED))

        assertEquals("hostname", env.mode)
    }

    @Test
    fun `stimmt der Wert schon, wird nichts geschrieben`() {
        val env = FakeEnv("hostname", NAME)
        val automation = DnsAutomation(env)
        automation.onNetwork(mobile)
        env.mode = "off"
        automation.onNetwork(home)
        env.writes.clear()
        env.events.clear()
        automation.onNetwork(home)

        assertTrue(env.writes.isEmpty())
        assertTrue(env.events.isEmpty())
    }

    @Test
    fun `unterwegs mit falschem Namen wird nur der Name korrigiert`() {
        val env = FakeEnv("hostname", "alt.example.org")
        env.hostname = NAME
        // Zu Hause wuerde "alt.example.org" uebernommen - hier geht es direkt um unterwegs.
        DnsAutomation(env).onNetwork(mobile)

        assertEquals(listOf("specifier=$NAME"), env.writes)
        assertEquals(Event.Away(NAME, success = true, message = "ok"), env.events.single())
    }

    @Test
    fun `zu Hause wird ein von Hand geaenderter Name gemerkt`() {
        val env = FakeEnv("hostname", "neu.example.org")
        DnsAutomation(env).onNetwork(home)

        assertEquals("neu.example.org", env.hostname)
        assertEquals("off", env.mode)
    }

    @Test
    fun `ein ungueltiger Name im System wird nicht gemerkt`() {
        val env = FakeEnv("hostname", "kaputt name")
        DnsAutomation(env).onNetwork(home)

        assertEquals(NAME, env.hostname)
    }

    @Test
    fun `Modus nicht gesetzt zaehlt nicht als aus`() {
        // null = Android-Standard "automatisch"
        val env = FakeEnv(null, null)
        DnsAutomation(env).onNetwork(home)

        assertEquals("off", env.mode)
    }

    @Test
    fun `Automatik aus - es passiert nichts`() {
        val env = FakeEnv("hostname", NAME)
        env.enabled = false
        DnsAutomation(env).onNetwork(home)

        assertEquals("hostname", env.mode)
        assertTrue(env.writes.isEmpty())
    }

    @Test
    fun `ensureAway traegt privates DNS ein, auch wenn die Automatik aus ist`() {
        val env = FakeEnv("off", NAME)
        env.enabled = false
        DnsAutomation(env).ensureAway()

        assertEquals("hostname", env.mode)
    }

    @Test
    fun `ohne gueltigen Namen wird unterwegs nichts geschrieben`() {
        val env = FakeEnv("off", null)
        env.hostname = ""
        DnsAutomation(env).onNetwork(mobile)

        assertTrue(env.writes.isEmpty())
        assertEquals("off", env.mode)
    }

    @Test
    fun `scheitert das Schreiben unterwegs, wird es gemeldet`() {
        val env = FakeEnv("off", NAME)
        env.writable = false
        DnsAutomation(env).onNetwork(mobile)

        assertEquals(Event.Away(NAME, success = false, message = "nope"), env.events.single())
        assertFalse(env.events.single().success)
    }

    // ---- Hilfsfunktionen ----

    @Test
    fun `gueltige DNS-Namen`() {
        listOf("dns.adguard-dns.com", "one.one.one.one", "a.b", "x-1.example.org", "DNS.Example.COM")
            .forEach { assertTrue(it, DnsAutomation.isValidHostname(it)) }
    }

    @Test
    fun `ungueltige DNS-Namen`() {
        listOf(
            "", "localhost", "dns adguard.com", "-dns.example.org", "dns-.example.org",
            "dns..example.org", ".example.org", "example.org.", "dns.example.org;reboot",
            "a".repeat(64) + ".org", ("a".repeat(60) + ".").repeat(5) + "org",
        ).forEach { assertFalse(it, DnsAutomation.isValidHostname(it)) }
    }

    @Test
    fun `WLAN-Name wird von Anfuehrungszeichen befreit`() {
        assertEquals(HOME, DnsAutomation.cleanSsid("\"Neuland 2\""))
        assertEquals(HOME, DnsAutomation.cleanSsid(HOME))
        assertNull(DnsAutomation.cleanSsid("<unknown ssid>"))
        assertNull(DnsAutomation.cleanSsid(""))
        assertNull(DnsAutomation.cleanSsid(null))
    }
}
