package com.noirdraco.pixelcomfort

/**
 * Ist ein TYPE_WINDOW_STATE_CHANGED wirklich ein App-Wechsel? Manche Apps melden nach dem
 * Verlassen noch ein spaetes Fenster (YouTube: ein FrameLayout knapp eine Sekunde nach dem
 * Launcher). Das darf die App nicht wieder "nach vorn" holen.
 *
 * Darum zaehlt bei sichtbaren Apps nur ein Fenster, das eine Activity der App ist. Apps, die
 * PixelComfort nicht sehen darf (kein Startsymbol), und Ereignisse ohne Klassennamen bleiben
 * beim bisherigen Verhalten: Sie zaehlen als Wechsel.
 */
internal object WindowSwitch {
    fun isAppSwitch(className: String?, packageVisible: Boolean, isActivity: (String) -> Boolean): Boolean {
        if (className.isNullOrBlank() || !packageVisible) return true
        return isActivity(className)
    }
}
