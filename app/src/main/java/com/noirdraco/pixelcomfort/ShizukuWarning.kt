package com.noirdraco.pixelcomfort

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * Benachrichtigung, wenn Shizuku nicht laeuft (z. B. nach einem Geraete-Neustart) oder
 * die Freigabe fehlt - dann kann die Automatik nicht schalten und wuerde sonst still
 * nichts tun.
 *
 * Es gibt hoechstens eine Warnung, bis Shizuku wieder bereit war: Wer sie wegwischt,
 * bekommt sie nicht bei jedem App-Wechsel erneut.
 */
object ShizukuWarning {

    private const val TAG = ComfortAccessibilityService.TAG
    private const val CHANNEL = "shizuku_warning"
    private const val ID = 1
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    @Volatile
    private var warned = false

    /** Darf die App ueberhaupt Benachrichtigungen zeigen? */
    fun allowed(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && manager(context).areNotificationsEnabled()
    }

    /** Aktuellen Zustand pruefen: warnen, wenn Shizuku nicht bereit ist, sonst aufraeumen. */
    fun check(context: Context) {
        if (ShizukuShell.isReady()) clear(context) else show(context)
    }

    fun show(context: Context) {
        if (warned) return
        val app = context.applicationContext
        if (!allowed(app)) {
            Log.w(TAG, "Shizuku nicht bereit, aber Benachrichtigungen sind nicht erlaubt")
            return
        }
        val running = ShizukuShell.isBinderAlive()
        val title = if (running) R.string.warning_no_permission_title else R.string.warning_not_running_title
        val text = if (running) R.string.warning_no_permission_text else R.string.warning_not_running_text

        manager(app).createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                app.getString(R.string.warning_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = app.getString(R.string.warning_channel_description) },
        )
        val notification = Notification.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_qs_comfort)
            .setContentTitle(app.getString(title))
            .setContentText(app.getString(text))
            .setStyle(Notification.BigTextStyle().bigText(app.getString(text)))
            .setContentIntent(tapIntent(app, shizukuRunning = running))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager(app).notify(ID, notification)
        warned = true
        Log.i(TAG, "Warnung gezeigt: ${app.getString(title)}")
    }

    /** Shizuku ist wieder bereit: Warnung entfernen, die naechste Stoerung meldet sich wieder. */
    fun clear(context: Context) {
        warned = false
        manager(context.applicationContext).cancel(ID)
    }

    /** Laeuft Shizuku nicht, fuehrt der Tipp dorthin; fehlt nur die Freigabe, in unsere App. */
    private fun tapIntent(context: Context, shizukuRunning: Boolean): PendingIntent {
        val shizuku = if (shizukuRunning) null else context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        val intent = shizuku ?: Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)
}
