package com.noirdraco.pixelcomfort

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupStatusTest {

    private fun status(
        shizuku: Boolean = true,
        comfortOn: Boolean? = true,
        usageAccess: Boolean = true,
        accessibility: Boolean = true,
        targets: List<String> = emptyList(),
        rotate: Boolean = false,
    ) = SetupStatus(
        shizukuBinder = shizuku,
        shizukuPermission = shizuku,
        canWriteSystem = true,
        hasWriteSecure = true,
        accessibilityEnabled = accessibility,
        advancedProtection = false,
        currentValue = comfortOn?.let { if (it) "1" else "0" },
        comfortOn = comfortOn,
        suppressed = false,
        targetLabels = targets,
        notificationsAllowed = true,
        lastEntry = null,
        dns = DnsStatus(
            enabled = false, hostname = "", homeSsids = emptySet(),
            mode = null, specifier = null, network = null, locationPermission = false,
            locationOn = false, writeSecure = true, serviceEnabled = true,
        ),
        hideMobileOnWifi = false,
        rotateEnabled = rotate,
        rotateLabels = emptyList(),
        usageAccess = usageAccess,
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

    @Test
    fun `ohne Nutzungsdaten Hinweis, sobald Augenkomfort-Apps oder Drehen genutzt werden`() {
        assertTrue(status(usageAccess = false, targets = listOf("Kamera")).usageHint)
        assertTrue(status(usageAccess = false, rotate = true).usageHint)
    }

    @Test
    fun `kein Nutzungsdaten-Hinweis, wenn erteilt, nichts genutzt oder die Bedienungshilfe aus ist`() {
        assertFalse(status(usageAccess = true, targets = listOf("Kamera"), rotate = true).usageHint)
        assertFalse(status(usageAccess = false).usageHint)
        assertFalse(status(usageAccess = false, accessibility = false, rotate = true).usageHint)
    }
}
