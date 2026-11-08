package com.noirdraco.pixelcomfort

import android.nfc.NfcAdapter
import android.service.quicksettings.Tile
import com.noirdraco.pixelcomfort.NfcTile.Subtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcTileTest {

    @Test
    fun ohneNfcNichtVerfuegbar() {
        assertEquals(NfcTile.View(Tile.STATE_UNAVAILABLE, Subtitle.NO_NFC), NfcTile.view(null, secure = false, canSwitch = true))
    }

    @Test
    fun ohneUmschaltwegNichtVerfuegbar() {
        assertEquals(
            NfcTile.View(Tile.STATE_UNAVAILABLE, Subtitle.SHIZUKU_NOT_READY),
            NfcTile.view(NfcAdapter.STATE_ON, secure = true, canSwitch = false),
        )
    }

    @Test
    fun anMitUndOhneSecureNfc() {
        assertEquals(NfcTile.View(Tile.STATE_ACTIVE, Subtitle.ON), NfcTile.view(NfcAdapter.STATE_ON, secure = false, canSwitch = true))
        assertEquals(NfcTile.View(Tile.STATE_ACTIVE, Subtitle.ON_SECURE), NfcTile.view(NfcAdapter.STATE_ON, secure = true, canSwitch = true))
    }

    @Test
    fun ausZeigtSecureNfcNicht() {
        assertEquals(NfcTile.View(Tile.STATE_INACTIVE, Subtitle.OFF), NfcTile.view(NfcAdapter.STATE_OFF, secure = true, canSwitch = true))
    }

    @Test
    fun zwischenzustaendeZeigenDasZiel() {
        assertEquals(NfcTile.View(Tile.STATE_ACTIVE, Subtitle.TURNING_ON), NfcTile.view(NfcAdapter.STATE_TURNING_ON, secure = false, canSwitch = true))
        assertEquals(NfcTile.View(Tile.STATE_INACTIVE, Subtitle.TURNING_OFF), NfcTile.view(NfcAdapter.STATE_TURNING_OFF, secure = true, canSwitch = true))
        assertTrue(NfcTile.isTransition(NfcAdapter.STATE_TURNING_ON))
        assertTrue(NfcTile.isTransition(NfcAdapter.STATE_TURNING_OFF))
        assertFalse(NfcTile.isTransition(NfcAdapter.STATE_ON))
        assertFalse(NfcTile.isTransition(null))
    }
}
