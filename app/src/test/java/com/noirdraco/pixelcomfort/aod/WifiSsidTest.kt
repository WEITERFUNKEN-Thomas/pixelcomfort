package com.noirdraco.pixelcomfort.aod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiSsidTest {
    @Test fun quotesAreStripped() = assertEquals("Zuhause", WifiSsid.clean("\"Zuhause\""))
    @Test fun unquotedIsKept() = assertEquals("Zuhause", WifiSsid.clean("Zuhause"))
    @Test fun unknownIsNull() = assertNull(WifiSsid.clean("<unknown ssid>"))
    @Test fun nullAndEmptyAreNull() {
        assertNull(WifiSsid.clean(null))
        assertNull(WifiSsid.clean(""))
        assertNull(WifiSsid.clean("\"\""))
    }
    @Test fun validityIsLimitedTo32Bytes() {
        assertTrue(WifiSsid.isValid("a".repeat(32)))
        assertFalse(WifiSsid.isValid("a".repeat(33)))
        assertFalse(WifiSsid.isValid("ä".repeat(17)))
        assertFalse(WifiSsid.isValid("   "))
    }
}
