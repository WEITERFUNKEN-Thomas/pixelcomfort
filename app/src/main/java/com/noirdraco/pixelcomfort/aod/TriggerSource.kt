package com.noirdraco.pixelcomfort.aod

import kotlinx.coroutines.flow.Flow

/** Emits the keys (see Trigger.key) of all triggers this source currently sees as fulfilled. */
interface TriggerSource {
    fun keys(): Flow<Set<String>>
}
