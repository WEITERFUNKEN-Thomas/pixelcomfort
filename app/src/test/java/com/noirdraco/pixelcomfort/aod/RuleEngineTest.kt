package com.noirdraco.pixelcomfort.aod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {
    private val car = Trigger.Bluetooth("aa:bb:cc:dd:ee:ff", "Auto")
    private val buds = Trigger.Bluetooth("11:22:33:44:55:66", "Kopfhörer")
    private val home = Trigger.Wifi("Zuhause")

    private val carOff = Rule("car", car, AodAction.OFF)
    private val homeOn = Rule("home", home, AodAction.ON)

    private fun input(
        rules: List<Rule>,
        active: Set<Trigger> = emptySet(),
        aod: Boolean,
        automation: Boolean = true,
    ) = RuleEngine.Input(automation, rules, active.map { it.key }.toSet(), aod)

    /** Car rule applied while AOD was on: engine wrote "off" and now controls. */
    private fun controlling(): EngineState {
        val r = RuleEngine.evaluate(input(listOf(carOff), setOf(car), aod = true), EngineState())
        assertEquals(false, r.write)
        return r.state
    }

    /** User switched AOD back on while the car rule was active. */
    private fun overridden(): EngineState {
        val r = RuleEngine.evaluate(input(listOf(carOff), setOf(car), aod = true), controlling())
        assertTrue(r.state.overridden)
        return r.state
    }

    @Test fun bluetoothKeyIsUppercased() {
        assertEquals("bt:AA:BB:CC:DD:EE:FF", car.key)
    }

    @Test fun firstRunCapturesBaselineWithoutWriting() {
        val r = RuleEngine.evaluate(input(emptyList(), aod = true), EngineState())
        assertNull(r.write)
        assertEquals(true, r.state.baseline)
        assertEquals(Status.Baseline, r.status)
        assertFalse(r.state.controlling)
    }

    @Test fun matchingRuleWritesItsTarget() {
        val r = RuleEngine.evaluate(input(listOf(carOff), setOf(car), aod = true), EngineState())
        assertEquals(false, r.write)
        assertEquals(Status.RuleActive("car"), r.status)
        assertEquals(true, r.state.baseline)
        assertTrue(r.state.controlling)
    }

    @Test fun topmostMatchingRuleWins() {
        val r = RuleEngine.evaluate(input(listOf(homeOn, carOff), setOf(car, home), aod = false), EngineState())
        assertEquals(true, r.write)
        assertEquals(Status.RuleActive("home"), r.status)
    }

    @Test fun disabledRuleIsIgnored() {
        val r = RuleEngine.evaluate(input(listOf(carOff.copy(enabled = false)), setOf(car), aod = true), EngineState())
        assertNull(r.write)
        assertEquals(Status.Baseline, r.status)
    }

    @Test fun noWriteWhenAlreadyAtTarget() {
        val r = RuleEngine.evaluate(input(listOf(carOff), setOf(car), aod = false), EngineState())
        assertNull(r.write)
        assertEquals(Status.RuleActive("car"), r.status)
    }

    @Test fun observingOwnWriteIsANoop() {
        val state = controlling()
        val r = RuleEngine.evaluate(input(listOf(carOff), setOf(car), aod = false), state)
        assertNull(r.write)
        assertEquals(state, r.state)
        assertEquals(Status.RuleActive("car"), r.status)
    }

    @Test fun baselineIsRestoredWhenRuleEnds() {
        val r = RuleEngine.evaluate(input(listOf(carOff), emptySet(), aod = false), controlling())
        assertEquals(true, r.write)
        assertEquals(Status.Baseline, r.status)
        assertFalse(r.state.controlling)
    }

    @Test fun manualChangeWithoutActiveRuleBecomesBaseline() {
        val first = RuleEngine.evaluate(input(listOf(carOff), aod = true), EngineState()).state
        val r = RuleEngine.evaluate(input(listOf(carOff), aod = false), first)
        assertNull(r.write)
        assertEquals(false, r.state.baseline)
        assertFalse(r.state.overridden)
    }

    @Test fun manualChangeDuringRuleOverridesAndKeepsBaseline() {
        val r = RuleEngine.evaluate(input(listOf(carOff), setOf(car), aod = true), controlling())
        assertNull(r.write)
        assertEquals(Status.Overridden("car"), r.status)
        assertEquals(true, r.state.baseline)
        val again = RuleEngine.evaluate(input(listOf(carOff), setOf(car), aod = true), r.state)
        assertNull(again.write)
        assertEquals(Status.Overridden("car"), again.status)
    }

    @Test fun overrideIsClearedWhenRuleTriggerChanges() {
        val gone = RuleEngine.evaluate(input(listOf(carOff), emptySet(), aod = true), overridden())
        assertFalse(gone.state.overridden)
        assertNull(gone.write)
        assertEquals(Status.Baseline, gone.status)
        val back = RuleEngine.evaluate(input(listOf(carOff), setOf(car), aod = true), gone.state)
        assertEquals(false, back.write)
    }

    @Test fun overrideIsClearedWhenRulesAreEdited() {
        val edited = listOf(carOff.copy(action = AodAction.ON))
        val r = RuleEngine.evaluate(input(edited, setOf(car), aod = true), overridden())
        assertFalse(r.state.overridden)
        assertEquals(Status.RuleActive("car"), r.status)
    }

    @Test fun unrelatedTriggerDoesNotClearOverride() {
        val r = RuleEngine.evaluate(input(listOf(carOff), setOf(car, buds), aod = true), overridden())
        assertTrue(r.state.overridden)
        assertNull(r.write)
    }

    @Test fun automationOffRestoresBaselineAndResets() {
        val r = RuleEngine.evaluate(input(listOf(carOff), setOf(car), aod = false, automation = false), controlling())
        assertEquals(true, r.write)
        assertEquals(EngineState(), r.state)
        assertEquals(Status.Disabled, r.status)
    }

    @Test fun automationOffWhileOverriddenDoesNotWrite() {
        val r = RuleEngine.evaluate(input(listOf(carOff), setOf(car), aod = true, automation = false), overridden())
        assertNull(r.write)
        assertEquals(EngineState(), r.state)
    }

    @Test fun automationOffWithoutControlDoesNotWrite() {
        val idle = RuleEngine.evaluate(input(listOf(carOff), aod = true), EngineState()).state
        val r = RuleEngine.evaluate(input(listOf(carOff), aod = false, automation = false), idle)
        assertNull(r.write)
    }
}
