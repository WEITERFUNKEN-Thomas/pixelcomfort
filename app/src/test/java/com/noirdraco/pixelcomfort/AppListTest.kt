package com.noirdraco.pixelcomfort

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppListTest {

    private val installed = mapOf(
        "com.whatsapp" to "WhatsApp",
        "com.google.android.GoogleCamera" to "Kamera",
        "com.google.android.apps.photos" to "Fotos",
        "org.mozilla.firefox" to "Firefox",
    )

    private fun names(list: List<AppList.Entry>) = list.map { it.label }

    @Test
    fun `angepinnte Apps stehen oben, der Rest alphabetisch`() {
        val pinned = setOf("com.google.android.GoogleCamera", "com.google.android.apps.photos")
        val list = AppList.build(installed, selected = pinned, pinned = pinned, query = "")
        assertEquals(listOf("Fotos", "Kamera", "Firefox", "WhatsApp"), names(list))
    }

    @Test
    fun `Umschalten aendert die Reihenfolge nicht, nur den Zustand`() {
        val pinned = setOf("com.google.android.GoogleCamera")
        val list = AppList.build(installed, selected = setOf("com.whatsapp"), pinned = pinned, query = "")
        assertEquals(listOf("Kamera", "Firefox", "Fotos", "WhatsApp"), names(list))
        assertFalse(list.first { it.label == "Kamera" }.selected)
        assertTrue(list.first { it.label == "WhatsApp" }.selected)
    }

    @Test
    fun `Sortierung ignoriert Gross- und Kleinschreibung`() {
        val apps = mapOf("a" to "zebra", "b" to "Apfel", "c" to "banane")
        val list = AppList.build(apps, selected = emptySet(), pinned = emptySet(), query = "")
        assertEquals(listOf("Apfel", "banane", "zebra"), names(list))
    }

    @Test
    fun `ausgewaehlte, aber nicht installierte Apps bleiben sichtbar`() {
        val pinned = setOf("com.example.weg")
        val list = AppList.build(installed, selected = pinned, pinned = pinned, query = "")
        val gone = list.first()
        assertEquals("com.example.weg", gone.label)
        assertFalse(gone.installed)
        assertTrue(gone.selected)
    }

    @Test
    fun `Suche findet ueber Name oder Paketname, ohne Gross-Kleinschreibung`() {
        assertEquals(
            listOf("Firefox"),
            names(AppList.build(installed, emptySet(), emptySet(), query = "fire")),
        )
        assertEquals(
            listOf("Firefox"),
            names(AppList.build(installed, emptySet(), emptySet(), query = " MOZILLA ")),
        )
    }

    @Test
    fun `Auswahl setzen und entfernen`() {
        assertEquals(setOf("a", "b"), AppList.toggle(setOf("a"), "b", on = true))
        assertEquals(setOf("a"), AppList.toggle(setOf("a", "b"), "b", on = false))
    }

    @Test
    fun `Zusammenfassung nennt bis zu zwei Apps alphabetisch und zaehlt den Rest`() {
        assertEquals(AppList.Summary(emptyList(), more = 0), AppList.summary(emptyList()))
        assertEquals(AppList.Summary(listOf("Kamera"), more = 0), AppList.summary(listOf("Kamera")))
        assertEquals(AppList.Summary(listOf("Fotos", "Kamera"), more = 0), AppList.summary(listOf("Kamera", "Fotos")))
        assertEquals(
            AppList.Summary(listOf("Firefox", "Fotos"), more = 2),
            AppList.summary(listOf("Kamera", "Fotos", "WhatsApp", "Firefox")),
        )
    }

    @Test
    fun `Paketliste wird kommagetrennt und sortiert gespeichert`() {
        assertEquals("a.b,c.d", AppList.serialize(setOf("c.d", "a.b")))
        assertEquals("", AppList.serialize(emptySet()))
    }
}
