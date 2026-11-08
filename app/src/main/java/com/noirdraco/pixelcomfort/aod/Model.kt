package com.noirdraco.pixelcomfort.aod

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AodAction { ON, OFF }

@Serializable
sealed interface Trigger {
    /** Stable identifier the sources emit while this trigger is fulfilled. */
    val key: String
    val label: String

    @Serializable
    @SerialName("bluetooth")
    data class Bluetooth(val address: String, val name: String) : Trigger {
        override val key: String get() = "bt:" + address.uppercase()
        override val label: String get() = name.ifBlank { address }
    }

    @Serializable
    @SerialName("wifi")
    data class Wifi(val ssid: String) : Trigger {
        override val key: String get() = "wifi:$ssid"
        override val label: String get() = ssid
    }
}

@Serializable
data class Rule(
    val id: String,
    val trigger: Trigger,
    val action: AodAction,
    val enabled: Boolean = true,
)

/**
 * What the engine remembers between evaluations.
 * [baseline] is the user's own AOD value, [expected] the value the engine last aimed for,
 * [controlling] whether a rule matched last time, [fingerprint] the relevant trigger/rule state.
 */
@Serializable
data class EngineState(
    val baseline: Boolean? = null,
    val expected: Boolean? = null,
    val controlling: Boolean = false,
    val overridden: Boolean = false,
    val fingerprint: String? = null,
)

sealed interface Status {
    data object Disabled : Status
    data object MissingPermission : Status
    data object Baseline : Status
    data class RuleActive(val ruleId: String) : Status
    data class Overridden(val ruleId: String?) : Status
}
