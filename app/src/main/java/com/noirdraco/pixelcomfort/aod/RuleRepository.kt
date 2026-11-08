package com.noirdraco.pixelcomfort.aod

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow

class RuleRepository(private val store: DataStore<AodData>) {
    val data: Flow<AodData> get() = store.data

    suspend fun setAutomation(on: Boolean) {
        store.updateData { it.copy(automation = on) }
    }

    suspend fun upsertRule(rule: Rule) {
        store.updateData { data ->
            val index = data.rules.indexOfFirst { it.id == rule.id }
            val rules = if (index < 0) data.rules + rule
            else data.rules.toMutableList().also { it[index] = rule }
            // Wer die erste Regel anlegt, will die Automatik nutzen.
            data.copy(rules = rules, automation = data.automation || data.rules.isEmpty())
        }
    }

    suspend fun deleteRule(id: String) {
        store.updateData { data -> data.copy(rules = data.rules.filterNot { it.id == id }) }
    }

    suspend fun setRuleEnabled(id: String, enabled: Boolean) {
        store.updateData { data ->
            data.copy(rules = data.rules.map { if (it.id == id) it.copy(enabled = enabled) else it })
        }
    }

    /** Rules named in [ids] come first in that order; rules not named keep their relative order after them. */
    suspend fun setOrder(ids: List<String>) {
        store.updateData { data ->
            val byId = data.rules.associateBy { it.id }
            data.copy(rules = ids.mapNotNull(byId::get) + data.rules.filter { it.id !in ids })
        }
    }

    suspend fun updateEngine(state: EngineState) {
        store.updateData { it.copy(engine = state) }
    }
}
