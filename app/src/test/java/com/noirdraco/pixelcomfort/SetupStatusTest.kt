package com.noirdraco.pixelcomfort

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupStatusTest {

    private fun status(shizuku: Boolean, comfortOn: Boolean?) = SetupStatus(
        shizukuBinder = shizuku,
        shizukuPermission = shizuku,
        canWriteSystem = true,
        hasWriteSecure = true,
        accessibilityEnabled = true,
        advancedProtection = false,
        currentValue = comfortOn?.let { if (it) "1" else "0" },
        comfortOn = comfortOn,
        suppressed = false,
        targetLabels = emptyList(),
        notificationsAllowed = true,
        lastEntry = null,
        dns = DnsStatus(
            supported = true, enabled = false, hostname = "", homeSsids = emptySet(),
            mode = null, specifier = null, network = null, locationPermission = false,
            locationOn = false, writeSecure = true, serviceEnabled = true,
        ),
    )

    @Test
    fun `Shizuku bereit, aber kein Wert - die Einstellung fehlt auf dem Geraet`() {
        assertTrue(status(shizuku = true, comfortOn = null).comfortSettingMissing)
    }

    @Test
    fun `ohne Shizuku ist ein fehlender Wert kein Beleg fuer eine fehlende Einstellung`() {
        assertFalse(status(shizuku = false, comfortOn = null).comfortSettingMissing)
    }

    @Test
    fun `lesbarer Wert - nichts fehlt`() {
        assertFalse(status(shizuku = true, comfortOn = true).comfortSettingMissing)
        assertFalse(status(shizuku = true, comfortOn = false).comfortSettingMissing)
    }
}
