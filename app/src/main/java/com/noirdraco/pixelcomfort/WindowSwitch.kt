package com.noirdraco.pixelcomfort

/**
 * Ist ein TYPE_WINDOW_STATE_CHANGED wirklich ein App-Wechsel? Manche Apps melden nach dem
 * Verlassen noch ein spaetes Fenster (YouTube: ein FrameLayout knapp eine Sekunde nach dem
 * Launcher). Das darf die App nicht wieder "nach vorn" holen.
 *
 * Ein Fenster ohne Activity zaehlt deshalb NICHT, wenn seine App erst vor kurzem
 * ([lateWindowMs]) verlassen wurde. Sonst zaehlt es: Kommt YouTube aus dem Hintergrund
 * zurueck, meldet es nur ein FrameLayout und keine Activity.
 *
 * Apps, die PixelComfort nicht sehen darf (kein Startsymbol), und Ereignisse ohne
 * Klassennamen zaehlen immer als Wechsel. Nur Main-Thread.
 */
internal class WindowSwitch(private val lateWindowMs: Long = LATE_WINDOW_MS) {

    /** App, die zuletzt als Wechsel gezaehlt wurde. */
    private var front: String? = null

    /** Package -> Zeitpunkt, zu dem es verlassen wurde. */
    private val leftAt = HashMap<String, Long>()

    /** [timeMs]: Zeit des Ereignisses (uptimeMillis). */
    fun isAppSwitch(
        pkg: String,
        className: String?,
        packageVisible: Boolean,
        timeMs: Long,
        isActivity: (String) -> Boolean,
    ): Boolean {
        val counts = className.isNullOrBlank() || !packageVisible || isActivity(className) ||
            !leftRecently(pkg, timeMs)
        if (counts) moveToFront(pkg, timeMs)
        return counts
    }

    private fun leftRecently(pkg: String, timeMs: Long): Boolean {
        val left = leftAt[pkg] ?: return false
        return timeMs - left in 0 until lateWindowMs
    }

    private fun moveToFront(pkg: String, timeMs: Long) {
        val previous = front
        if (previous == pkg) return
        if (previous != null) leftAt[previous] = timeMs
        leftAt.remove(pkg)
        leftAt.entries.removeAll { timeMs - it.value >= lateWindowMs }
        front = pkg
    }

    companion object {
        /** YouTube meldete das spaete Fenster nach ~0,8 s; etwas Luft dazu. */
        const val LATE_WINDOW_MS = 1_500L
    }
}
