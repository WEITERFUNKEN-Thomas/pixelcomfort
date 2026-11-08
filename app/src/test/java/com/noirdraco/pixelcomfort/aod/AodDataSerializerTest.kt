package com.noirdraco.pixelcomfort.aod

import androidx.datastore.core.CorruptionException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class AodDataSerializerTest {
    @Test fun roundTripKeepsEverything() = runBlocking {
        val data = AodData(
            automation = false,
            rules = listOf(
                Rule("1", Trigger.Bluetooth("AA:BB:CC:DD:EE:FF", "Auto"), AodAction.OFF),
                Rule("2", Trigger.Wifi("Zuhause"), AodAction.ON, enabled = false),
            ),
            engine = EngineState(baseline = true, expected = false, controlling = true, fingerprint = "x"),
        )
        val out = ByteArrayOutputStream()
        AodDataSerializer.writeTo(data, out)
        assertEquals(data, AodDataSerializer.readFrom(ByteArrayInputStream(out.toByteArray())))
    }

    @Test fun brokenJsonIsReportedAsCorruption() {
        assertThrows(CorruptionException::class.java) {
            runBlocking { AodDataSerializer.readFrom(ByteArrayInputStream("{not json".toByteArray())) }
        }
    }

    @Test fun unknownFieldsAreIgnored() = runBlocking {
        val json = """{"automation":true,"rules":[],"future":1}"""
        assertEquals(AodData(), AodDataSerializer.readFrom(ByteArrayInputStream(json.toByteArray())))
    }

    @Test fun shouldRunNeedsAutomationAndAnEnabledRule() {
        val rule = Rule("1", Trigger.Wifi("Zuhause"), AodAction.ON)
        assertEquals(false, AodData().shouldRun)
        assertEquals(true, AodData(rules = listOf(rule)).shouldRun)
        assertEquals(false, AodData(rules = listOf(rule.copy(enabled = false))).shouldRun)
        assertEquals(false, AodData(automation = false, rules = listOf(rule)).shouldRun)
    }
}
