package com.noirdraco.pixelcomfort

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

/**
 * Schnelleinstellungs-Kachel "Drehen in Apps": schaltet die Regel ein und aus, wie der
 * Schalter auf der Startseite ([AutoRotate.setEnabled]). Die Android-Einstellung
 * "Automatisch drehen" selbst fasst sie nicht an.
 *
 * Liest nur die eigene Konfiguration (SharedPreferences, im Speicher) - das geht auf dem
 * Main-Thread. Das Zurueckstellen beim Ausschalten laeuft auf dem [SettingsWorker].
 */
class RotateTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        update()
    }

    override fun onClick() {
        super.onClick()
        val on = !Prefs.getRotateEnabled(this)
        Log.i(ComfortAccessibilityService.TAG, "Kachel Drehen -> ${if (on) "an" else "aus"}")
        AutoRotate.setEnabled(this, on)
        update()
    }

    private fun update() {
        val tile = qsTile ?: return
        val on = Prefs.getRotateEnabled(this)
        val apps = Prefs.getRotatePackages(this)
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.rotate_tile_label)
        tile.subtitle = when {
            !on -> getString(R.string.value_off)
            apps.isEmpty() -> getString(R.string.rotate_tile_no_apps)
            apps.size == 1 -> appLabel(apps.first())
            else -> resources.getQuantityString(R.plurals.rotate_tile_apps, apps.size, apps.size)
        }
        tile.updateTile()
    }
}
