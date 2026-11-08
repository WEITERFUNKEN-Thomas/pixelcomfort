package com.noirdraco.pixelcomfort

import com.noirdraco.pixelcomfort.MobileIconAutomation.Current
import com.noirdraco.pixelcomfort.MobileIconAutomation.Event
import com.noirdraco.pixelcomfort.MobileIconAutomation.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileIconAutomationTest {

    /** Ein "Geraet" im Speicher: die Liste der ausgeblendeten Symbole (secure/icon_blacklist). */
    private class FakeEnv(var list: String?) : MobileIconAutomation.Env {
        override var enabled = true
        var readable = true
        var writable = true
        val writes = mutableListOf<String?>()
        val events = mutableListOf<Event>()

        override fun read(): Current? = if (readable) Current(list) else null

        override fun write(value: String?): Outcome {
            if (!writable) return Outcome(false, "nope")
            writes += value
            list = value
            return Outcome(true, "ok")
        }

        override fun report(event: Event) {
            events += event
        }
    }

    @Test
    fun `im WLAN wird das Mobilfunk-Symbol ausgeblendet, andere Eintraege bleiben`() {
        val env = FakeEnv("headset,alarm_clock,rotate")
        MobileIconAutomation(env).onNetwork(onWifi = true)

        assertEquals("headset,alarm_clock,rotate,mobile", env.list)
        assertEquals(listOf<Event>(Event.Hidden(success = true, message = "ok")), env.events)
    }

    @Test
    fun `ohne WLAN wird das Mobilfunk-Symbol wieder gezeigt, andere Eintraege bleiben`() {
        val env = FakeEnv("mobile,data_connection,headset,rotate")
        MobileIconAutomation(env).onNetwork(onWifi = false)

        assertEquals("data_connection,headset,rotate", env.list)
        assertEquals(listOf<Event>(Event.Shown(success = true, message = "ok")), env.events)
    }

    @Test
    fun `schon ausgeblendet - nichts wird geschrieben`() {
        val env = FakeEnv("headset,mobile")
        MobileIconAutomation(env).onNetwork(onWifi = true)

        assertTrue(env.writes.isEmpty())
        assertTrue(env.events.isEmpty())
    }

    @Test
    fun `schon sichtbar - nichts wird geschrieben`() {
        val env = FakeEnv("headset,rotate")
        MobileIconAutomation(env).onNetwork(onWifi = false)

        assertTrue(env.writes.isEmpty())
        assertTrue(env.events.isEmpty())
    }

    @Test
    fun `nie gesetzte Liste - im WLAN steht danach nur das Mobilfunk-Symbol drin`() {
        val env = FakeEnv(null)
        MobileIconAutomation(env).onNetwork(onWifi = true)

        assertEquals("mobile", env.list)
    }

    @Test
    fun `war es der einzige Eintrag, wird die Liste geloescht statt leer geschrieben`() {
        val env = FakeEnv("mobile")
        MobileIconAutomation(env).onNetwork(onWifi = false)

        assertEquals(listOf<String?>(null), env.writes)
        assertNull(env.list)
    }

    @Test
    fun `Leerzeichen und leere Eintraege werden beim Schreiben bereinigt`() {
        val env = FakeEnv(" headset, ,rotate ,")
        MobileIconAutomation(env).onNetwork(onWifi = true)

        assertEquals("headset,rotate,mobile", env.list)
    }

    @Test
    fun `nicht lesbar - nichts wird geschrieben, damit andere Eintraege nicht verloren gehen`() {
        val env = FakeEnv("headset,rotate").apply { readable = false }
        MobileIconAutomation(env).onNetwork(onWifi = true)

        assertTrue(env.writes.isEmpty())
        assertEquals(listOf<Event>(Event.Hidden(success = false, message = History.Reason.UNREADABLE)), env.events)
    }

    @Test
    fun `scheitert das Schreiben, wird es gemeldet`() {
        val env = FakeEnv("headset").apply { writable = false }
        MobileIconAutomation(env).onNetwork(onWifi = true)

        assertEquals(listOf<Event>(Event.Hidden(success = false, message = "nope")), env.events)
    }

    @Test
    fun `ausgeschaltet - nichts passiert`() {
        val env = FakeEnv("headset").apply { enabled = false }
        MobileIconAutomation(env).onNetwork(onWifi = true)

        assertTrue(env.writes.isEmpty())
    }

    @Test
    fun `ensureShown zeigt das Symbol auch bei ausgeschalteter Funktion wieder`() {
        val env = FakeEnv("mobile,headset").apply { enabled = false }
        MobileIconAutomation(env).ensureShown()

        assertEquals("headset", env.list)
    }
}
