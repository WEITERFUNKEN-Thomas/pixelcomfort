package com.noirdraco.pixelcomfort

import com.noirdraco.pixelcomfort.ComfortAutomation.Event
import com.noirdraco.pixelcomfort.ComfortAutomation.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComfortAutomationTest {

    /** Ein "Geraet" im Speicher: eine Einstellung, die sich lesen/schreiben laesst - oder nicht. */
    private class FakeEnv(var value: String?) : ComfortAutomation.Env {
        override val offValue = "0"
        var reachable = true
        var suppressed = false
        var saved: String? = null
        val events = mutableListOf<Event>()
        val writes = mutableListOf<String>()

        override fun read(): String? = if (reachable) value else null

        override fun write(value: String): Outcome {
            if (!reachable) return Outcome(false, "Shizuku nicht verfuegbar")
            writes += value
            this.value = value
            return Outcome(true, "ok")
        }

        override fun isSuppressed() = suppressed
        override fun savedValue() = saved
        override fun setState(suppressed: Boolean, savedValue: String?) {
            this.suppressed = suppressed
            this.saved = savedValue
        }

        override fun report(event: Event) {
            events += event
        }
    }

    private val camera = "com.google.android.GoogleCamera"
    private val launcher = "com.google.android.apps.nexuslauncher"

    @Test
    fun `Ziel-App schaltet aus und merkt sich den vorherigen Wert`() {
        val env = FakeEnv("1")
        ComfortAutomation(env).onForeground(camera, isTarget = true)

        assertEquals("0", env.value)
        assertTrue(env.suppressed)
        assertEquals("1", env.saved)
        assertEquals(listOf<Event>(Event.Paused(camera, from = "1", success = true, message = "ok")), env.events)
    }

    @Test
    fun `Verlassen der Ziel-App stellt den gemerkten Wert wieder her`() {
        val env = FakeEnv("1")
        val automation = ComfortAutomation(env)
        automation.onForeground(camera, isTarget = true)
        automation.onForeground(launcher, isTarget = false)

        assertEquals("1", env.value)
        assertFalse(env.suppressed)
        assertNull(env.saved)
        assertEquals(Event.Restored(launcher, to = "1", success = true, message = "ok"), env.events.last())
    }

    @Test
    fun `war der Filter schon aus, passiert gar nichts`() {
        val env = FakeEnv("0")
        val automation = ComfortAutomation(env)
        automation.onForeground(camera, isTarget = true)
        automation.onForeground(launcher, isTarget = false)

        assertEquals("0", env.value)
        assertTrue(env.writes.isEmpty())
        assertTrue(env.events.isEmpty())
        assertFalse(env.suppressed)
    }

    @Test
    fun `zweite Ziel-App in Folge schreibt nicht noch einmal`() {
        val env = FakeEnv("1")
        val automation = ComfortAutomation(env)
        automation.onForeground(camera, isTarget = true)
        automation.onForeground("com.google.android.apps.photos", isTarget = true)

        assertEquals(listOf("0"), env.writes)
        assertEquals("1", env.saved)
    }

    @Test
    fun `nicht lesbarer Wert - es wird nicht pausiert und nichts gemerkt`() {
        val env = FakeEnv("1").apply { reachable = false }
        ComfortAutomation(env).onForeground(camera, isTarget = true)

        assertFalse(env.suppressed)
        assertNull(env.saved)
        assertEquals(listOf<Event>(Event.Skipped(camera)), env.events)
    }

    @Test
    fun `nach uebersprungenem Pausieren wird beim Verlassen nichts eingeschaltet`() {
        // Der alte Fehler: Filter war aus, Shizuku weg -> "1" gemerkt -> spaeter eingeschaltet.
        val env = FakeEnv("0").apply { reachable = false }
        val automation = ComfortAutomation(env)
        automation.onForeground(camera, isTarget = true)
        env.reachable = true
        automation.onForeground(launcher, isTarget = false)

        assertEquals("0", env.value)
        assertTrue(env.writes.isEmpty())
    }

    @Test
    fun `scheitert das Wiederherstellen, bleibt der gemerkte Wert erhalten`() {
        val env = FakeEnv("1")
        val automation = ComfortAutomation(env)
        automation.onForeground(camera, isTarget = true)
        env.reachable = false
        automation.onForeground(launcher, isTarget = false)

        assertTrue(env.suppressed)
        assertEquals("1", env.saved)
        assertEquals(
            Event.Restored(launcher, to = "1", success = false, message = "Shizuku nicht verfuegbar"),
            env.events.last(),
        )
    }

    @Test
    fun `gescheitertes Wiederherstellen wird beim naechsten App-Wechsel nachgeholt`() {
        val env = FakeEnv("1")
        val automation = ComfortAutomation(env)
        automation.onForeground(camera, isTarget = true)
        env.reachable = false
        automation.onForeground(launcher, isTarget = false)
        env.reachable = true
        automation.onForeground("com.whatsapp", isTarget = false)

        assertEquals("1", env.value)
        assertFalse(env.suppressed)
        assertEquals(Event.Restored("com.whatsapp", to = "1", success = true, message = "ok"), env.events.last())
    }

    @Test
    fun `wiederholtes Scheitern wird nur einmal gemeldet`() {
        val env = FakeEnv("1")
        val automation = ComfortAutomation(env)
        automation.onForeground(camera, isTarget = true)
        env.reachable = false
        automation.onForeground(launcher, isTarget = false)
        automation.onForeground("com.whatsapp", isTarget = false)
        automation.onForeground(launcher, isTarget = false)

        assertEquals(1, env.events.count { it is Event.Restored && !it.success })
    }

    @Test
    fun `Rueckkehr in die Ziel-App nach gescheitertem Wiederherstellen behaelt den gemerkten Wert`() {
        val env = FakeEnv("1")
        val automation = ComfortAutomation(env)
        automation.onForeground(camera, isTarget = true)
        env.reachable = false
        automation.onForeground(launcher, isTarget = false)
        env.reachable = true
        automation.onForeground(camera, isTarget = true)
        automation.onForeground(launcher, isTarget = false)

        assertEquals("1", env.value)
        assertFalse(env.suppressed)
    }

    @Test
    fun `scheitert das Ausschalten, wird das gemeldet`() {
        val env = object : ComfortAutomation.Env by FakeEnv("1") {
            override fun write(value: String) = Outcome(false, "kaputt")
        }
        val events = mutableListOf<Event>()
        val recording = object : ComfortAutomation.Env by env {
            override fun report(event: Event) {
                events += event
            }
        }
        ComfortAutomation(recording).onForeground(camera, isTarget = true)

        assertEquals(listOf<Event>(Event.Paused(camera, from = "1", success = false, message = "kaputt")), events)
    }

    @Test
    fun `ohne aktive Pause loest eine normale App nichts aus`() {
        val env = FakeEnv("1")
        ComfortAutomation(env).onForeground(launcher, isTarget = false)

        assertTrue(env.writes.isEmpty())
        assertTrue(env.events.isEmpty())
    }
}
