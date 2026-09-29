package com.noirdraco.pixelcomfort

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.noirdraco.pixelcomfort.DnsAutomation.Event

/**
 * Zugriff auf die System-Einstellung "Privates DNS" (global/private_dns_mode und
 * private_dns_specifier) und die echte Umgebung der [DnsAutomation].
 *
 * Anders als cv_enabled sind das oeffentlich schreibbare Keys: mit WRITE_SECURE_SETTINGS
 * (einmalig per Shizuku erteilt) geht es direkt ueber den ContentResolver, Shizuku muss
 * dafuer nicht laufen. Der [SettingWriter] faellt trotzdem auf Shizuku zurueck.
 *
 * Alle Funktionen blockieren und gehoeren auf den [SettingsWorker].
 */
object PrivateDns {

    private const val TAG = ComfortAccessibilityService.TAG
    private const val KEY_MODE = "private_dns_mode"
    private const val KEY_SPECIFIER = "private_dns_specifier"

    /** WLAN-Name und Sicherheitsart zusammen gibt es erst ab Android 12. */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun readMode(context: Context): String? = SettingWriter.read(context, Prefs.NS_GLOBAL, KEY_MODE)
    fun readSpecifier(context: Context): String? = SettingWriter.read(context, Prefs.NS_GLOBAL, KEY_SPECIFIER)

    fun automation(context: Context) = DnsAutomation(Env(context.applicationContext))

    private class Env(private val ctx: Context) : DnsAutomation.Env {
        override val enabled: Boolean get() = Prefs.getDnsEnabled(ctx)
        override val homeSsids: Set<String> get() = Prefs.getDnsHomeSsids(ctx)
        override var hostname: String
            get() = Prefs.getDnsHostname(ctx)
            set(value) {
                Log.i(TAG, "DNS: von Hand geaenderten Namen uebernommen: $value")
                Prefs.setDnsHostname(ctx, value)
            }

        override fun readMode() = readMode(ctx)
        override fun readSpecifier() = readSpecifier(ctx)
        override fun writeMode(value: String) = write(KEY_MODE, value)
        override fun writeSpecifier(value: String) = write(KEY_SPECIFIER, value)

        private fun write(key: String, value: String): DnsAutomation.Outcome {
            val res = SettingWriter.write(ctx, Prefs.NS_GLOBAL, key, value)
            return DnsAutomation.Outcome(res.success, "via ${res.method}: ${res.message}")
        }

        override fun report(event: Event) {
            val entry = when (event) {
                is Event.Home -> {
                    Log.i(TAG, "DNS: Heim-WLAN '${event.ssid}' -> privates DNS aus, ok=${event.success} ${event.message}")
                    History.Entry(
                        System.currentTimeMillis(), History.Kind.DNS_HOME, event.ssid,
                        success = event.success,
                        message = if (event.success) "" else ShizukuShell.explain(event.message),
                    )
                }

                is Event.Away -> {
                    Log.i(TAG, "DNS: unterwegs -> privates DNS '${event.hostname}', ok=${event.success} ${event.message}")
                    History.Entry(
                        System.currentTimeMillis(), History.Kind.DNS_AWAY, event.hostname,
                        success = event.success,
                        message = if (event.success) "" else ShizukuShell.explain(event.message),
                    )
                }
            }
            HistoryStore.add(ctx, entry)
            // Nur das Einschalten ist sicherheitsrelevant: scheitert es, laeuft DNS unverschluesselt.
            if (event is Event.Away) {
                if (event.success) Warning.clear(ctx) else Warning.show(ctx)
            }
        }
    }

    /**
     * Benachrichtigung, wenn privates DNS unterwegs nicht eingetragen werden konnte.
     * Hoechstens eine, bis es wieder geklappt hat.
     */
    private object Warning {
        private const val CHANNEL = "dns_warning"
        private const val ID = 2

        @Volatile
        private var warned = false

        fun show(context: Context) {
            if (warned) return
            if (!ShizukuWarning.allowed(context)) {
                Log.w(TAG, "DNS: Einschalten gescheitert, Benachrichtigungen sind aber nicht erlaubt")
                return
            }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.dns_warning_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.dns_warning_channel_description) },
            )
            val text = context.getString(R.string.dns_warning_text)
            val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val notification = Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_dns)
                .setContentTitle(context.getString(R.string.dns_warning_title))
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(
                    PendingIntent.getActivity(
                        context, ID, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
            nm.notify(ID, notification)
            warned = true
        }

        fun clear(context: Context) {
            warned = false
            context.getSystemService(NotificationManager::class.java).cancel(ID)
        }
    }
}
