package com.noirdraco.pixelcomfort

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

/**
 * Schnelleinstellungs-Kachel zum manuellen Umschalten des Augenkomfort-Modus.
 *
 * Liest und schreibt denselben Key wie die Automatik und laeuft ueber denselben
 * seriellen [SettingsWorker], damit sich Kachel und Automatik nicht ueberholen.
 *
 * Zustaende der Kachel:
 * - AKTIV     = Augenkomfort ist an
 * - INAKTIV   = aus
 * - NICHT VERFUEGBAR = Shizuku laeuft nicht bzw. der Wert ist nicht lesbar
 *   (ohne Shizuku laesst sich cv_enabled weder lesen noch schreiben, siehe
 *   SettingWriter - dann waere ein klickbarer Schalter nur irrefuehrend).
 */
class ComfortTileService : TileService() {

    private val main = Handler(Looper.getMainLooper())

    /**
     * Nur zwischen onStartListening und onStopListening darf die Kachel
     * aktualisiert werden. Volatile, weil der Worker-Thread das Flag liest.
     */
    @Volatile
    private var listening = false

    override fun onStartListening() {
        super.onStartListening()
        listening = true
        // Der Wert kann sich seit dem letzten Blick geaendert haben (Automatik,
        // System-Einstellungen), also immer frisch lesen.
        SettingsWorker.submit { push(currentState()) }
    }

    override fun onStopListening() {
        listening = false
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        SettingsWorker.submit { toggle() }
    }

    /** Aktuellen Wert lesen, umschalten, Kachel nachfuehren. */
    private fun toggle() {
        val ns = Prefs.getNamespace(this)
        val key = Prefs.getKey(this)
        val off = Prefs.getOff(this)
        val on = Prefs.getOn(this)

        val current = SettingWriter.read(this, ns, key)
        if (current == null) {
            Log.w(TAG, "Kachel: $ns/$key nicht lesbar - laeuft Shizuku?")
            push(currentState())
            return
        }

        val target = if (current == off) on else off
        // Zieht bei aktiver Unterdrueckung auch den gemerkten Wert nach.
        val res = ComfortControl.write(this, target, ComfortControl.Source.TILE, from = current)

        Log.i(TAG, "Kachel -> $ns/$key von $current auf $target via ${res.method}, ok=${res.success}: ${res.message}")
        push(if (res.success) state(target) else currentState())
    }

    /** Liest den Live-Wert und leitet daraus den Kachel-Zustand ab. Worker-Thread! */
    private fun currentState(): TileState {
        if (!ShizukuShell.isReady()) return TileState(Tile.STATE_UNAVAILABLE, getString(R.string.tile_shizuku_not_ready))
        val value = SettingWriter.read(this, Prefs.getNamespace(this), Prefs.getKey(this))
            ?: return TileState(Tile.STATE_UNAVAILABLE, getString(R.string.tile_unreadable))
        return state(value)
    }

    private fun state(value: String): TileState =
        if (value == Prefs.getOff(this)) {
            TileState(Tile.STATE_INACTIVE, getString(R.string.value_off))
        } else {
            TileState(Tile.STATE_ACTIVE, getString(R.string.value_on))
        }

    /** Kachel auf dem Main-Thread aktualisieren. */
    private fun push(s: TileState) {
        main.post {
            if (!listening) return@post
            val tile = qsTile ?: return@post
            tile.state = s.state
            tile.label = getString(R.string.qs_tile_label)
            // Untertitel gibt es erst ab Android 10.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = s.subtitle
            tile.updateTile()
        }
    }

    private data class TileState(val state: Int, val subtitle: String)

    private companion object {
        const val TAG = ComfortAccessibilityService.TAG
    }
}
