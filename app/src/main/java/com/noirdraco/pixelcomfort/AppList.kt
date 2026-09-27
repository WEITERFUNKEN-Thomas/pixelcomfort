package com.noirdraco.pixelcomfort

/**
 * Reine Logik der App-Auswahl (Ziel-Apps der Automatik), ohne Android-Abhaengigkeit.
 */
object AppList {

    data class Entry(
        val packageName: String,
        val label: String,
        val selected: Boolean,
        /** false = ausgewaehlt, aber (nicht mehr) installiert - bleibt zum Abwaehlen sichtbar. */
        val installed: Boolean,
    )

    /**
     * @param installed Paketname -> Anzeigename aller startbaren Apps
     * @param selected aktuelle Auswahl
     * @param pinned Auswahl beim Oeffnen der Liste. Nur sie bestimmt, was oben steht,
     *   damit Zeilen beim Umschalten nicht unter dem Finger wegspringen.
     */
    fun build(
        installed: Map<String, String>,
        selected: Set<String>,
        pinned: Set<String>,
        query: String,
    ): List<Entry> {
        val q = query.trim()
        val missing = (pinned + selected).filter { it !in installed }
        val all = installed.map { (pkg, label) -> Entry(pkg, label, pkg in selected, installed = true) } +
            missing.map { Entry(it, it, it in selected, installed = false) }
        return all
            .filter {
                q.isEmpty() ||
                    it.label.contains(q, ignoreCase = true) ||
                    it.packageName.contains(q, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<Entry> { it.packageName in pinned }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
            )
    }

    fun toggle(selected: Set<String>, packageName: String, on: Boolean): Set<String> =
        if (on) selected + packageName else selected - packageName

    /** Bis zu zwei Namen (alphabetisch) und wie viele Apps darueber hinaus gewaehlt sind. */
    data class Summary(val names: List<String>, val more: Int)

    /** Zutaten fuer den Kurztext auf der Startseite; der Satz selbst steht in den Sprachdateien. */
    fun summary(labels: List<String>): Summary {
        val sorted = labels.sortedWith(String.CASE_INSENSITIVE_ORDER)
        return Summary(sorted.take(2), more = (sorted.size - 2).coerceAtLeast(0))
    }

    /** Format, in dem [Prefs] die Ziel-Packages ablegt. */
    fun serialize(packages: Set<String>): String = packages.sorted().joinToString(",")
}
