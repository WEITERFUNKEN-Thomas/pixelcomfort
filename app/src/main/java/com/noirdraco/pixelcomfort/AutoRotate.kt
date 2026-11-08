package com.noirdraco.pixelcomfort

import android.content.Context
import android.util.Log
import com.noirdraco.pixelcomfort.AppRuleAutomation.Event

/**
 * Automatisch drehen nur in ausgewaehlten Apps: system/accelerometer_rotation ist in diesen
 * Apps 1, sonst der vorherige Wert (meist 0 = Hochformat). Die Regeln stehen in der
 * [AppRuleAutomation], hier die echte Umgebung.
 *
 * Der Key ist oeffentlich: Lesen geht immer, Schreiben mit WRITE_SETTINGS (einmalig per
 * Shizuku erteilt) direkt ueber den ContentResolver - Shizuku muss dafuer nicht laufen.
 *
 * Alle Funktionen blockieren und gehoeren auf den [SettingsWorker].
 */
object AutoRotate {

    private const val TAG = ComfortAccessibilityService.TAG
    private const val KEY = "accelerometer_rotation"
    private const val ON = "1"
    private const val OFF = "0"

    fun automation(context: Context) = AppRuleAutomation(Env(context.applicationContext))

    /**
     * Regel ein- oder ausschalten - gemeinsam fuer Startseite und Kachel. Einschalten wirkt
     * beim naechsten App-Wechsel. Ausschalten stellt sofort zurueck, falls gerade eine der
     * Apps vorn ist und das Drehen deshalb an ist.
     */
    fun setEnabled(context: Context, on: Boolean, onDone: () -> Unit = {}) {
        val app = context.applicationContext
        Prefs.setRotateEnabled(app, on)
        SettingsWorker.submit {
            if (!on) automation(app).release()
            onDone()
        }
    }

    private class Env(private val ctx: Context) : AppRuleAutomation.Env {
        override val forcedValue: String = ON

        override fun read(): String? = SettingWriter.read(ctx, Prefs.NS_SYSTEM, KEY)

        override fun write(value: String): AppRuleAutomation.Outcome {
            val res = SettingWriter.write(ctx, Prefs.NS_SYSTEM, KEY, value)
            return AppRuleAutomation.Outcome(res.success, "via ${res.method}: ${res.message}")
        }

        override fun isSuppressed(): Boolean = Prefs.getRotateActive(ctx)
        override fun savedValue(): String? = Prefs.getRotateSaved(ctx)
        override fun setState(suppressed: Boolean, savedValue: String?) =
            Prefs.setRotateState(ctx, suppressed, savedValue)

        override fun report(event: Event) {
            val now = System.currentTimeMillis()
            val entry = when (event) {
                is Event.Forced -> {
                    Log.i(TAG, "Drehen: '${event.pkg}' -> $KEY=$ON (gemerkt=${event.from}) ok=${event.success} ${event.message}")
                    History.Entry(
                        now, History.Kind.ROTATE_ON, ctx.appLabel(event.pkg),
                        success = event.success,
                        message = if (event.success) "" else ShizukuShell.explain(event.message),
                    )
                }

                is Event.Skipped -> {
                    Log.w(TAG, "Drehen: '${event.pkg}' -> $KEY nicht lesbar, nichts geschrieben")
                    History.Entry(
                        now, History.Kind.ROTATE_ON, ctx.appLabel(event.pkg),
                        success = false,
                        message = History.Reason.UNREADABLE,
                    )
                }

                is Event.Restored -> {
                    Log.i(TAG, "Drehen: zurueck auf $KEY=${event.to} ok=${event.success} ${event.message}")
                    History.Entry(
                        now, History.Kind.ROTATE_RESTORE,
                        from = History.ON,
                        to = History.comfortToken(event.to, OFF),
                        success = event.success,
                        message = if (event.success) "" else ShizukuShell.explain(event.message),
                    )
                }
            }
            HistoryStore.add(ctx, entry)
        }
    }
}
