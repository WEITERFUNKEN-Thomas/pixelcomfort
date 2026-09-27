package com.noirdraco.pixelcomfort

/**
 * Reine Logik des Verlaufs (Eintraege, Format), ohne Android-Abhaengigkeit. Die Texte
 * entstehen erst bei der Anzeige (ui/HistoryText.kt), damit der Verlauf der Sprache folgt.
 * Gespeichert wird ueber den [HistoryStore].
 */
object History {

    const val MAX_ENTRIES = 100

    enum class Kind {
        /** Automatik hat fuer eine Ziel-App ausgeschaltet. */
        PAUSE,

        /** Automatik hat nicht pausiert, weil der Wert nicht lesbar war. */
        SKIP,

        /** Automatik hat den gemerkten Wert zurueckgeschrieben. */
        RESTORE,

        /** Von Hand ueber den Hauptschalter in der App. */
        SWITCH,

        /** Von Hand ueber die Schnelleinstellungs-Kachel. */
        TILE,
        WIDTH,
        FONT,
    }

    /**
     * @param subject App, um die es ging (nur PAUSE/SKIP)
     * @param from/to sprachneutral: [ON], [OFF], [DEFAULT], "438 dp", "1.15"
     * @param message Grund bei einem Fehlschlag: ein Kuerzel aus [Reason] oder Technik-Text
     */
    data class Entry(
        val time: Long,
        val kind: Kind,
        val subject: String = "",
        val from: String? = null,
        val to: String? = null,
        val success: Boolean = true,
        val message: String = "",
    )

    /** Gruende, fuer die es einen uebersetzten Text gibt. */
    object Reason {
        const val SHIZUKU_DOWN = "shizuku_down"
        const val SHIZUKU_PERMISSION = "shizuku_permission"
        const val UNREADABLE = "unreadable"
    }

    /** Neuester Eintrag zuerst; ueber [max] fallen die aeltesten raus. */
    fun add(entries: List<Entry>, entry: Entry, max: Int = MAX_ENTRIES): List<Entry> =
        (listOf(entry) + entries).take(max)

    // ---- Format: eine Zeile pro Eintrag, Felder durch Tab getrennt ----

    private const val SEP = '\t'
    private const val FIELDS = 7
    private val BREAKS = Regex("[\\t\\r\\n]+")

    private fun clean(s: String?): String = s.orEmpty().replace(BREAKS, " ")

    fun encode(e: Entry): String = listOf(
        e.time.toString(),
        e.kind.name,
        clean(e.subject),
        clean(e.from),
        clean(e.to),
        if (e.success) "1" else "0",
        clean(e.message),
    ).joinToString(SEP.toString())

    /** null bei einer unlesbaren Zeile - sie wird dann einfach uebersprungen. */
    fun decode(line: String): Entry? {
        val f = line.split(SEP)
        if (f.size != FIELDS) return null
        val time = f[0].toLongOrNull() ?: return null
        val kind = Kind.entries.firstOrNull { it.name == f[1] } ?: return null
        return Entry(
            time = time,
            kind = kind,
            subject = f[2],
            from = f[3].ifEmpty { null }?.let(::neutral),
            to = f[4].ifEmpty { null }?.let(::neutral),
            success = f[5] == "1",
            message = f[6],
        )
    }

    // ---- Sprachneutrale Werte (die Texte dazu stehen in den Sprachdateien) ----

    const val ON = "on"
    const val OFF = "off"

    /** Zurueck auf den Standardwert des Geraets. */
    const val DEFAULT = "default"

    /** AUS = exakt der konfigurierte Aus-Wert; alles andere = AN. */
    fun comfortToken(value: String?, offValue: String): String? = when (value) {
        null -> null
        offValue -> OFF
        else -> ON
    }

    /** Bis zur Einfuehrung von Englisch standen die Werte auf Deutsch im Verlauf. */
    private fun neutral(value: String): String = when (value) {
        "An" -> ON
        "Aus" -> OFF
        "Standard" -> DEFAULT
        else -> value
    }
}
