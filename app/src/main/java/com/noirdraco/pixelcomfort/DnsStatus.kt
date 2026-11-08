package com.noirdraco.pixelcomfort

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager

/** Alles, was die Oberflaeche ueber die DNS-Automatik wissen muss. */
data class DnsStatus(
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
            return DnsStatus(
                enabled = Prefs.getDnsEnabled(context),
                hostname = Prefs.getDnsHostname(context),
                homeSsids = Prefs.getDnsHomeSsids(context),
                mode = PrivateDns.readMode(context),
                specifier = PrivateDns.readSpecifier(context),
                network = NetworkWatcher.current(context),
                locationPermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED,
                locationOn = context.getSystemService(LocationManager::class.java)?.isLocationEnabled == true,
                writeSecure = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
                    PackageManager.PERMISSION_GRANTED,
                serviceEnabled = ComfortAccessibilityService.isEnabled(context),
            )
        }
    }
}
