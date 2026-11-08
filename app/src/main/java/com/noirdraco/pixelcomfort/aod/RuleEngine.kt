package com.noirdraco.pixelcomfort.aod


/** Pure decision logic: no Android, no I/O. */
object RuleEngine {
    data class Input(
        val automation: Boolean,
        val rules: List<Rule>,
        val activeKeys: Set<String>,
        val currentAod: Boolean,
    )

    /** [write] is the value to write to the AOD setting, or null to leave it alone. */
    data class Result(val write: Boolean?, val state: EngineState, val status: Status)

    fun evaluate(input: Input, state: EngineState): Result {
        if (!input.automation) {
            val restore = state.baseline
                ?.takeIf { state.controlling && !state.overridden && it != input.currentAod }
            return Result(restore, EngineState(), Status.Disabled)
        }

        val fingerprint = fingerprint(input)
        var baseline = state.baseline ?: input.currentAod
        var overridden = state.overridden

        // A value we did not aim for means the user changed the setting.
        val manualChange = state.expected != null && state.expected != input.currentAod
        if (manualChange) {
            if (state.controlling) overridden = true else baseline = input.currentAod
        }
        if (state.fingerprint != null && state.fingerprint != fingerprint) overridden = false

        val match = input.rules.firstOrNull { it.enabled && it.trigger.key in input.activeKeys }

        if (overridden) {
            val next = EngineState(baseline, input.currentAod, controlling = true, overridden = true, fingerprint)
            return Result(null, next, Status.Overridden(match?.id))
        }

        val target = match?.let { it.action == AodAction.ON } ?: baseline
        val next = EngineState(baseline, target, controlling = match != null, overridden = false, fingerprint)
        val status = if (match != null) Status.RuleActive(match.id) else Status.Baseline
        return Result(target.takeIf { it != input.currentAod }, next, status)
    }

    /** Only triggers used by enabled rules count, so unrelated devices never clear an override. */
    private fun fingerprint(input: Input): String {
        val enabled = input.rules.filter { it.enabled }
        val relevant = enabled.map { it.trigger.key }.filter { it in input.activeKeys }.distinct().sorted()
        val rules = enabled.joinToString("|") { "${it.id},${it.action},${it.trigger.key}" }
        return relevant.joinToString("|") + "#" + rules
    }
}
