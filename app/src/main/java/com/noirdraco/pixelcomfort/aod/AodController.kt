package com.noirdraco.pixelcomfort.aod

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** The only place that touches a system setting: one key, two values. */
class AodController(private val context: Context) {
    private val resolver get() = context.contentResolver

    fun canWrite(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun read(): Boolean = Settings.Secure.getInt(resolver, KEY, 0) == 1

    fun write(on: Boolean): Boolean = try {
        Settings.Secure.putInt(resolver, KEY, if (on) 1 else 0)
    } catch (e: SecurityException) {
        false
    }

    fun observe(): Flow<Boolean> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(read())
            }
        }
        resolver.registerContentObserver(Settings.Secure.getUriFor(KEY), false, observer)
        trySend(read())
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.distinctUntilChanged()

    private companion object {
        const val KEY = "doze_always_on"
    }
}
