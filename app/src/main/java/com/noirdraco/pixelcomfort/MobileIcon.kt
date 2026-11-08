package com.noirdraco.pixelcomfort

import android.content.Context
import android.util.Log
import com.noirdraco.pixelcomfort.MobileIconAutomation.Event

/**
 * Die echte Umgebung der [MobileIconAutomation]: secure/icon_blacklist lesen und
 * schreiben, Verlauf fuehren.
 *
 * icon_blacklist ist ein @hide-Key. Schreiben geht mit WRITE_SECURE_SETTINGS ueber den
 * ContentResolver, Lesen meist nur per Shizuku - der [SettingWriter] faellt jeweils
 * selbst auf Shizuku zurueck.
 *
 * Alle Funktionen blockieren und gehoeren auf den [SettingsWorker].
 */
object MobileIcon {

    private const val TAG = ComfortAccessibilityService.TAG
    private const val KEY = "icon_blacklist"

    fun automation(context: Context) = MobileIconAutomation(Env(context.applicationContext))

    private class Env(private val ctx: Context) : MobileIconAutomation.Env {
        override val enabled: Boolean get() = Prefs.getHideMobileOnWifi(ctx)

        override fun read(): MobileIconAutomation.Current? =
            SettingWriter.readChecked(ctx, Prefs.NS_SECURE, KEY)?.let { MobileIconAutomation.Current(it.value) }

        override fun write(value: String?): MobileIconAutomation.Outcome {
            val res = if (value == null) {
                SettingWriter.delete(ctx, Prefs.NS_SECURE, KEY)
            } else {
                SettingWriter.write(ctx, Prefs.NS_SECURE, KEY, value)
            }
            return MobileIconAutomation.Outcome(res.success, "via ${res.method}: ${res.message}")
        }

        override fun report(event: Event) {
            val hidden = event is Event.Hidden
            val message = when (event) {
                is Event.Hidden -> event.message
                is Event.Shown -> event.message
            }
            Log.i(TAG, "Mobilfunk-Symbol -> ${if (hidden) "aus" else "ein"}geblendet, ok=${event.success} $message")
            HistoryStore.add(
                ctx,
                History.Entry(
                    time = System.currentTimeMillis(),
                    kind = if (hidden) History.Kind.MOBILE_HIDDEN else History.Kind.MOBILE_SHOWN,
                    success = event.success,
                    message = if (event.success) "" else ShizukuShell.explain(message),
                ),
            )
        }
    }
}
