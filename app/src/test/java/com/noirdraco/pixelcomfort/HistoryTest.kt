package com.noirdraco.pixelcomfort

import com.noirdraco.pixelcomfort.History.Entry
import com.noirdraco.pixelcomfort.History.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryTest {

    @Test
    fun `neuester Eintrag steht oben`() {
        val list = History.add(listOf(Entry(1, Kind.PAUSE)), Entry(2, Kind.RESTORE))
        assertEquals(listOf(2L, 1L), list.map { it.time })
    }

    @Test
    fun `aelteste Eintraege fallen ueber der Obergrenze raus`() {
        var list = emptyList<Entry>()
        for (t in 1L..5L) list = History.add(list, Entry(t, Kind.PAUSE), max = 3)
        assertEquals(listOf(5L, 4L, 3L), list.map { it.time })
    }

    @Test
    fun `Eintrag uebersteht Speichern und Laden unveraendert`() {
        val entry = Entry(
            time = 1790000000000,
            kind = Kind.PAUSE,
            subject = "Kamera",
            from = History.ON,
            to = History.OFF,
            success = false,
            message = "Shizuku nicht verfuegbar",
        )
        assertEquals(entry, History.decode(History.encode(entry)))
    }

    @Test
    fun `fehlende Werte bleiben nach dem Laden leer`() {
        val entry = Entry(time = 5, kind = Kind.SKIP, subject = "Fotos")
        val loaded = History.decode(History.encode(entry))!!
        assertNull(loaded.from)
        assertNull(loaded.to)
        assertEquals("", loaded.message)
    }

    @Test
    fun `Tabs und Zeilenumbrueche im Text zerstoeren das Format nicht`() {
        val entry = Entry(1, Kind.RESTORE, success = false, message = "Zeile 1\nZeile\t2")
        val line = History.encode(entry)
        assertEquals(1, line.lines().size)
        assertEquals("Zeile 1 Zeile 2", History.decode(line)!!.message)
    }

    @Test
    fun `kaputte Zeilen werden uebersprungen statt abzustuerzen`() {
        assertNull(History.decode(""))
        assertNull(History.decode("kein\tgueltiger\teintrag"))
        assertNull(History.decode("abc\tPAUSE\tx\t\t\t1\t"))
        assertNull(History.decode("1\tUNBEKANNT\tx\t\t\t1\t"))
    }

    @Test
    fun `Komfort-Wert wird sprachneutral als on oder off gespeichert`() {
        assertEquals(History.OFF, History.comfortToken("0", offValue = "0"))
        assertEquals(History.ON, History.comfortToken("1", offValue = "0"))
        assertNull(History.comfortToken(null, offValue = "0"))
    }

    @Test
    fun `alte deutsche Eintraege werden beim Laden auf die neutralen Werte umgestellt`() {
        val old = "5\tRESTORE\t\tAus\tAn\t1\t"
        val loaded = History.decode(old)!!
        assertEquals(History.OFF, loaded.from)
        assertEquals(History.ON, loaded.to)
        assertEquals(History.DEFAULT, History.decode("5\tWIDTH\t\t438 dp\tStandard\t1\t")!!.to)
    }

    @Test
    fun `andere Werte bleiben beim Laden unveraendert`() {
        val loaded = History.decode("5\tWIDTH\t\t438 dp\t427 dp\t1\t")!!
        assertEquals("438 dp", loaded.from)
        assertEquals("427 dp", loaded.to)
    }
}
