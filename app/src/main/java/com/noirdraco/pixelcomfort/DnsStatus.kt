package com.noirdraco.pixelcomfort

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build

/** Alles, was die Oberflaeche ueber die DNS-Automatik wissen muss. */
data class DnsStatus(
    val supported: Boolean,
    val enabled: Boolean,
    /** Gemerkter DNS-Name fuer unterwegs (leer = noch keiner). */
    val hostname: String,
    val homeSsids: Set<String>,
    /** Aktuelle System-Werte. */
    val mode: String?,
    val specifier: String?,
    /** Aktuelles Netz; null = keins. */
    val network: DnsAutomation.Network?,
    val locationPermission: Boolean,
    val locationOn: Boolean,
    val writeSecure: Boolean,
    val serviceEnabled: Boolean,
) {
    /** WLAN-Name, wenn wir gerade zu Hause sind. */
    val homeSsid: String? get() = DnsAutomation.homeSsid(network, homeSsids)

    /** Ohne das kann die Automatik zu Hause nicht ausschalten (oder gar nicht schalten). */
    val setupComplete: Boolean get() = locationPermission && locationOn && writeSecure && serviceEnabled

    companion object {
        /** Blockiert (Settings-Reads) - nicht auf dem Main-Thread aufrufen. */
        fun read(context: Context): DnsStatus {
            val supported = PrivateDns.supported
            return DnsStatus(
                supported = supported,
                enabled = Prefs.getDnsEnabled(context),
                hostname = Prefs.getDnsHostname(context),
                homeSsids = Prefs.getDnsHomeSsids(context),
                mode = if (supported) PrivateDns.readMode(context) else null,
                specifier = if (supported) PrivateDns.readSpecifier(context) else null,
                network = DnsWatcher.current(context),
                locationPermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED,
                locationOn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                    context.getSystemService(LocationManager::class.java)?.isLocationEnabled == true,
                writeSecure = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
                    PackageManager.PERMISSION_GRANTED,
                serviceEnabled = ComfortAccessibilityService.isEnabled(context),
            )
        }
    }
}
