package com.noirdraco.pixelcomfort.aod

import androidx.datastore.core.DataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule as JUnitRule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RuleRepositoryTest {
    @get:JUnitRule val tmp = TemporaryFolder()

    private fun repo() = RuleRepository(
        DataStoreFactory.create(AodDataSerializer) { tmp.newFile("app_data.json").also { it.delete() } }
    )

    private val a = Rule("a", Trigger.Wifi("A"), AodAction.ON)
    private val b = Rule("b", Trigger.Wifi("B"), AodAction.OFF)
    private val c = Rule("c", Trigger.Wifi("C"), AodAction.ON)

    @Test fun upsertAppendsThenReplacesInPlace() = runBlocking {
        val repo = repo()
        repo.upsertRule(a); repo.upsertRule(b)
        repo.upsertRule(a.copy(action = AodAction.OFF))
        assertEquals(listOf(a.copy(action = AodAction.OFF), b), repo.data.first().rules)
    }

    @Test fun deleteAndEnableToggle() = runBlocking {
        val repo = repo()
        repo.upsertRule(a); repo.upsertRule(b)
        repo.setRuleEnabled("b", false)
        repo.deleteRule("a")
        assertEquals(listOf(b.copy(enabled = false)), repo.data.first().rules)
    }

    @Test fun setOrderReordersAndKeepsUnknownRules() = runBlocking {
        val repo = repo()
        repo.upsertRule(a); repo.upsertRule(b); repo.upsertRule(c)
        repo.setOrder(listOf("c", "a", "ghost"))
        assertEquals(listOf(c, a, b), repo.data.first().rules)
    }

    @Test fun firstRuleTurnsAutomationOn() = runBlocking {
        val repo = repo()
        assertEquals(false, repo.data.first().automation)
        repo.upsertRule(a)
        assertEquals(true, repo.data.first().automation)
    }

    @Test fun laterRulesKeepAutomationOff() = runBlocking {
        val repo = repo()
        repo.upsertRule(a)
        repo.setAutomation(false)
        repo.upsertRule(b)
        repo.upsertRule(a.copy(action = AodAction.OFF))
        assertEquals(false, repo.data.first().automation)
    }

    @Test fun automationAndEngineStateArePersisted() = runBlocking {
        val repo = repo()
        repo.setAutomation(true)
        repo.updateEngine(EngineState(baseline = true))
        val data = repo.data.first()
        assertEquals(true, data.automation)
        assertEquals(EngineState(baseline = true), data.engine)
    }
}
