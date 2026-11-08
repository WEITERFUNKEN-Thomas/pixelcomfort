package com.noirdraco.pixelcomfort

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.TileService
import android.util.Log

/**
 * Schnelleinstellungs-Kachel "NFC": schaltet NFC an und aus. Secure NFC bleibt
 * unberuehrt und steht nur in der Unterzeile ("An · Sicher").
 *
 * Umschalten: erst die offizielle API ([NfcAdapter.enable]/[NfcAdapter.disable], braucht
 * WRITE_SECURE_SETTINGS, laut Doku aber nur fuer System-Apps), sonst `svc nfc` ueber
 * Shizuku. Lehnt Android die API einmal ab, merkt sich das der Prozess.
 *
 * Zustand: [NfcAdapter.isEnabled]; solange die Kachel sichtbar ist, liefert
 * [NfcAdapter.ACTION_ADAPTER_STATE_CHANGED] auch die Zwischenzustaende.
 * Lange druecken oeffnet die NFC-Seite von Android ([TilePreferencesActivity]).
 */
class NfcTileService : TileService() {

    private val main = Handler(Looper.getMainLooper())

    /** Alles Folgende nur auf dem Main-Thread. */
    private var listening = false

    /** Zwischenzustand beim Umschalten (TURNING_ON/OFF), sonst null. */
    private var transition: Int? = null

    /** Kommt kein Broadcast (z. B. Umschalten scheitert still), nicht ewig "Wird ..." zeigen. */
    private val clearTransition = Runnable {
        transition = null
        update()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val s = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF)
            setTransition(s.takeIf { NfcTile.isTransition(it) })
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        listening = true
        // Geschuetzter System-Broadcast (sendet der NFC-Dienst, nicht "system"), darum
        // EXPORTED - fremde Apps koennen ihn ohnehin nicht senden.
        registerReceiver(receiver, IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED), RECEIVER_EXPORTED)
        update()
    }

    override fun onStopListening() {
        listening = false
        runCatching { unregisterReceiver(receiver) }
        super.onStopListening()
    }

    override fun onDestroy() {
        main.removeCallbacks(clearTransition)
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val adapter = adapter() ?: return
        if (transition != null || !canSwitch()) return

        val on = !adapter.isEnabled
        setTransition(if (on) NfcAdapter.STATE_TURNING_ON else NfcAdapter.STATE_TURNING_OFF)
        SettingsWorker.submit {
            val ok = switch(adapter, on)
            if (!ok) main.post { setTransition(null) }
        }
    }

    /** Worker-Thread. enable/disable gibt es erst ab Android 16, davor nur Shizuku. */
    private fun switch(adapter: NfcAdapter, on: Boolean): Boolean {
        if (!apiDenied && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            val res = runCatching { if (on) adapter.enable() else adapter.disable() }
            if (res.getOrNull() == true) {
                Log.i(TAG, "Kachel NFC -> ${onOff(on)} via API")
                return true
            }
            // SecurityException = keine System-App; false = Android verweigert (z. B. Flugmodus).
            if (res.exceptionOrNull() is SecurityException) apiDenied = true
            Log.i(TAG, "Kachel NFC: API ${res.exceptionOrNull()?.toString() ?: "lieferte false"} - nehme Shizuku")
        }
        if (!ShizukuShell.isReady()) {
            Log.w(TAG, "Kachel NFC -> ${onOff(on)}: Shizuku nicht bereit")
            return false
        }
        val res = runCatching { ShizukuShell.run("svc", "nfc", if (on) "enable" else "disable") }
        val ok = res.getOrNull()?.success == true
        Log.i(TAG, "Kachel NFC -> ${onOff(on)} via Shizuku, ok=$ok: ${res.getOrNull()?.let { it.err.ifEmpty { it.out } } ?: res.exceptionOrNull()}")
        return ok
    }

    private fun setTransition(state: Int?) {
        transition = state
        main.removeCallbacks(clearTransition)
        if (state != null) main.postDelayed(clearTransition, TRANSITION_TIMEOUT_MS)
        update()
    }

    private fun update() {
        if (!listening) return
        val tile = qsTile ?: return
        val adapter = adapter()
        val state = adapter?.let { transition ?: if (it.isEnabled) NfcAdapter.STATE_ON else NfcAdapter.STATE_OFF }
        val secure = adapter != null && runCatching { adapter.isSecureNfcSupported && adapter.isSecureNfcEnabled }.getOrDefault(false)
        val view = NfcTile.view(state, secure, canSwitch())
        tile.state = view.state
        tile.label = getString(R.string.nfc_tile_label)
        tile.subtitle = getString(
            when (view.subtitle) {
                NfcTile.Subtitle.ON -> R.string.value_on
                NfcTile.Subtitle.ON_SECURE -> R.string.nfc_tile_on_secure
                NfcTile.Subtitle.OFF -> R.string.value_off
                NfcTile.Subtitle.TURNING_ON -> R.string.nfc_tile_turning_on
                NfcTile.Subtitle.TURNING_OFF -> R.string.nfc_tile_turning_off
                NfcTile.Subtitle.NO_NFC -> R.string.nfc_tile_missing
                NfcTile.Subtitle.SHIZUKU_NOT_READY -> R.string.tile_shizuku_not_ready
            },
        )
        tile.updateTile()
    }

    private fun adapter(): NfcAdapter? = NfcAdapter.getDefaultAdapter(this)

    private fun canSwitch(): Boolean =
        (!apiDenied && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) || ShizukuShell.isReady()

    private fun onOff(on: Boolean) = if (on) "an" else "aus"

    private companion object {
        const val TAG = ComfortAccessibilityService.TAG
        const val TRANSITION_TIMEOUT_MS = 5_000L

        /** Offizielle API einmal mit SecurityException abgelehnt - gilt bis Prozessende. */
        @Volatile
        var apiDenied = false
    }
}
