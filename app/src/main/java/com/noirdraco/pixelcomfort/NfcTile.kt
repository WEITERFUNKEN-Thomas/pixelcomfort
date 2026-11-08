package com.noirdraco.pixelcomfort

import android.nfc.NfcAdapter
import android.service.quicksettings.Tile

/**
 * Was die NFC-Kachel zeigt - ohne Android-Objekte, damit es per Unit-Test pruefbar ist.
 * Die Texte waehlt [NfcTileService] anhand von [Subtitle].
 */
object NfcTile {

    enum class Subtitle { ON, ON_SECURE, OFF, TURNING_ON, TURNING_OFF, NO_NFC, SHIZUKU_NOT_READY }

    data class View(val state: Int, val subtitle: Subtitle)

    /**
     * @param adapterState [NfcAdapter.STATE_OFF] bis [NfcAdapter.STATE_TURNING_OFF], null = kein NFC
     * @param secure Secure NFC ist an (wird nur angezeigt, nie umgeschaltet)
     * @param canSwitch Umschalten ist moeglich (offizielle API erlaubt oder Shizuku bereit)
     */
    fun view(adapterState: Int?, secure: Boolean, canSwitch: Boolean): View = when {
        adapterState == null -> View(Tile.STATE_UNAVAILABLE, Subtitle.NO_NFC)
        !canSwitch -> View(Tile.STATE_UNAVAILABLE, Subtitle.SHIZUKU_NOT_READY)
        // Zwischenzustaende zeigen schon das Ziel, damit die Kachel nicht zurueckspringt.
        adapterState == NfcAdapter.STATE_TURNING_ON -> View(Tile.STATE_ACTIVE, Subtitle.TURNING_ON)
        adapterState == NfcAdapter.STATE_TURNING_OFF -> View(Tile.STATE_INACTIVE, Subtitle.TURNING_OFF)
        adapterState == NfcAdapter.STATE_ON -> View(Tile.STATE_ACTIVE, if (secure) Subtitle.ON_SECURE else Subtitle.ON)
        else -> View(Tile.STATE_INACTIVE, Subtitle.OFF)
    }

    /** Zwischenzustand? Dann ignoriert die Kachel weitere Klicks. */
    fun isTransition(adapterState: Int?): Boolean =
        adapterState == NfcAdapter.STATE_TURNING_ON || adapterState == NfcAdapter.STATE_TURNING_OFF
}
