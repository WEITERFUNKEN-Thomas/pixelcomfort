package com.noirdraco.pixelcomfort

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.noirdraco.pixelcomfort.DnsAutomation.Network
import com.noirdraco.pixelcomfort.DnsAutomation.Security

/**
 * Meldet der [DnsAutomation] jeden Wechsel des Standard-Netzes. Das System ruft den
 * Callback von sich aus auf - es wird nie abgefragt, kein Timer, kein Wakelock.
 *
 * Laeuft im [ComfortAccessibilityService] (der ohnehin dauerhaft laeuft und nach einem
 * Neustart vom System gestartet wird). Die Oberflaeche ruft [restart] nach jeder
 * Aenderung an der Konfiguration oder den Berechtigungen: das neu registrierte Callback
 * bekommt sofort den aktuellen Stand, jetzt mit den neuen Rechten.
 *
 * Den WLAN-Namen liefert Android nur mit FLAG_INCLUDE_LOCATION_INFO und
 * Standort-Berechtigung; ohne sie ist die SSID leer - dann gilt "unterwegs".
 */
object DnsWatcher {

    private const val TAG = ComfortAccessibilityService.TAG

    private var appContext: Context? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var locationReceiver: BroadcastReceiver? = null

    /** Zuletzt gemeldetes Netz; so loesen staendige Signalstaerke-Updates nichts aus. */
    private var last: Any? = Unset

    private object Unset

    @Synchronized
    fun start(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || callback != null) return
        val ctx = context.applicationContext
        val cb = Callback()
        try {
            ctx.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(cb)
        } catch (t: Throwable) {
            Log.e(TAG, "DNS: Netz-Callback nicht registrierbar", t)
            return
        }
        appContext = ctx
        callback = cb
        last = Unset
        // Standort wieder an: der WLAN-Name ist erst dann lesbar, das Netz-Callback
        // meldet sich deswegen aber nicht von selbst.
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = restart()
        }
        runCatching {
            ContextCompat.registerReceiver(
                ctx, receiver, IntentFilter(LocationManager.MODE_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            locationReceiver = receiver
        }
        Log.i(TAG, "DNS: Netzwechsel werden beobachtet")
    }

    @Synchronized
    fun stop() {
        val cb = callback ?: return
        runCatching { appContext?.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) }
        locationReceiver?.let { r -> runCatching { appContext?.unregisterReceiver(r) } }
        locationReceiver = null
        callback = null
    }

    /** Neu registrieren, falls der Dienst laeuft - das System liefert dann sofort den aktuellen Stand. */
    @Synchronized
    fun restart() {
        val ctx = appContext ?: return
        if (callback == null) return
        stop()
        start(ctx)
    }

    @Synchronized
    private fun emit(cb: Callback, network: Network?) {
        // Veraltetes Callback (nach restart) ignorieren.
        if (cb !== callback || network == last) return
        last = network
        val ctx = appContext ?: return
        Log.d(TAG, "DNS: Standard-Netz $network")
        SettingsWorker.submit { PrivateDns.automation(ctx).onNetwork(network) }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private class Callback : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
        override fun onCapabilitiesChanged(network: android.net.Network, caps: NetworkCapabilities) {
            emit(this, snapshot(caps))
        }

        override fun onLost(network: android.net.Network) {
            emit(this, null)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun snapshot(caps: NetworkCapabilities): Network {
        val info = caps.transportInfo as? WifiInfo
        return fromWifiInfo(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && info != null, info)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun fromWifiInfo(isWifi: Boolean, info: WifiInfo?): Network =
        if (!isWifi || info == null) {
            Network(isWifi = false, ssid = null, security = Security.UNKNOWN)
        } else {
            Network(isWifi = true, ssid = DnsAutomation.cleanSsid(info.ssid), security = security(info.currentSecurityType))
        }

    /**
     * Fuer die Oberflaeche (App im Vordergrund): das aktuelle Netz. Die synchrone
     * getNetworkCapabilities-Abfrage schwaerzt den WLAN-Namen immer, deshalb kommt er
     * hier aus dem WifiManager.
     */
    @SuppressLint("MissingPermission")
    fun current(context: Context): Network? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val active = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(active) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return fromWifiInfo(false, null)
        @Suppress("DEPRECATION")
        val info = runCatching { context.getSystemService(WifiManager::class.java).connectionInfo }.getOrNull()
        return fromWifiInfo(true, info)
    }

    /**
     * Gesichert = Passwort oder Zertifikat noetig. OWE ist zwar verschluesselt, aber
     * ohne Passwort - wie offen. WEP gilt als geknackt.
     */
    @SuppressLint("InlinedApi") // DPP ab API 33: Konstante wird eingebettet, aeltere Geraete liefern sie nie
    @RequiresApi(Build.VERSION_CODES.S)
    private fun security(type: Int): Security = when (type) {
        WifiInfo.SECURITY_TYPE_PSK,
        WifiInfo.SECURITY_TYPE_SAE,
        WifiInfo.SECURITY_TYPE_EAP,
        WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE,
        WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT,
        WifiInfo.SECURITY_TYPE_WAPI_PSK,
        WifiInfo.SECURITY_TYPE_WAPI_CERT,
        WifiInfo.SECURITY_TYPE_PASSPOINT_R1_R2,
        WifiInfo.SECURITY_TYPE_PASSPOINT_R3,
        WifiInfo.SECURITY_TYPE_DPP,
        -> Security.SECURED

        WifiInfo.SECURITY_TYPE_OPEN,
        WifiInfo.SECURITY_TYPE_OWE,
        WifiInfo.SECURITY_TYPE_WEP,
        WifiInfo.SECURITY_TYPE_OSEN,
        -> Security.OPEN

        else -> Security.UNKNOWN
    }
}
