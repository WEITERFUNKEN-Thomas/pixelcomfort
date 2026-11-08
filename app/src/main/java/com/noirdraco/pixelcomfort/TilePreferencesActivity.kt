package com.noirdraco.pixelcomfort

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings

/**
 * Ziel beim langen Druecken auf eine eigene Kachel (QS_TILE_PREFERENCES gilt fuer die
 * ganze App). NFC oeffnet die NFC-Seite von Android, alle anderen Kacheln wie bisher
 * die App-Info. Ohne eigene Oberflaeche.
 */
class TilePreferencesActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tile = intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
        val target = if (tile?.className == NfcTileService::class.java.name) {
            Intent(Settings.ACTION_NFC_SETTINGS)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        }
        runCatching { startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        finish()
    }
}
