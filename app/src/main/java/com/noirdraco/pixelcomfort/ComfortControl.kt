package com.noirdraco.pixelcomfort

import android.content.Context
import android.util.Log

/**
 * Manuelles Schalten des Augenkomfort-Modus - gemeinsam fuer die Kachel und den
 * Hauptschalter in der App. Blockiert (Shizuku), gehoert auf den [SettingsWorker].
 */
object ComfortControl {

    private const val TAG = ComfortAccessibilityService.TAG

    /** Woher das Schalten kam - landet so im Verlauf. */
    enum class Source(val kind: History.Kind) {
        SWITCH(History.Kind.SWITCH),
        TILE(History.Kind.TILE),
    }

    private fun read(context: Context): String? =
        SettingWriter.read(context, Prefs.getNamespace(context), Prefs.getKey(context))

    /** true = an, false = aus, null = nicht lesbar (Shizuku laeuft nicht). */
    fun isOn(context: Context): Boolean? {
        val value = read(context) ?: return null
        // AUS = exakt der konfigurierte Aus-Wert; alles andere = AN.
        return value != Prefs.getOff(context)
    }

    fun set(context: Context, on: Boolean, source: Source): SettingWriter.WriteResult =
        write(context, if (on) Prefs.getOn(context) else Prefs.getOff(context), source)

    /** @param from Wert vor dem Schalten, falls der Aufrufer ihn schon gelesen hat */
    fun write(
        context: Context,
        value: String,
        source: Source,
        from: String? = read(context),
    ): SettingWriter.WriteResult {
        val res = SettingWriter.write(context, Prefs.getNamespace(context), Prefs.getKey(context), value)
        if (res.success && Prefs.getSuppressed(context)) {
            // Eine Ziel-App ist gerade vorn, die Automatik haelt den Filter unten und
            // wuerde beim Verlassen den gemerkten Wert zurueckschreiben. Diese
            // bewusste Handentscheidung soll das ueberleben, also den gemerkten Wert
            // mitziehen statt sie spaeter still zu ueberschreiben.
            Prefs.setSavedValue(context, value)
            Log.i(TAG, "Von Hand waehrend aktiver Unterdrueckung: gemerkter Wert auf $value nachgezogen")
        }
        val off = Prefs.getOff(context)
        HistoryStore.add(
            context,
            History.Entry(
                time = System.currentTimeMillis(),
                kind = source.kind,
                from = History.comfortToken(from, off),
                to = History.comfortToken(value, off),
                success = res.success,
                message = if (res.success) "" else ShizukuShell.explain(res.message),
            ),
        )
        if (!res.success) ShizukuWarning.check(context)
        return res
    }
}
