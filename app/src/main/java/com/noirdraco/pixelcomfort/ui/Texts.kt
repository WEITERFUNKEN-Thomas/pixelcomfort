package com.noirdraco.pixelcomfort.ui

import android.content.Context
import android.content.res.Resources
import android.text.format.DateFormat
import com.noirdraco.pixelcomfort.AppList
import com.noirdraco.pixelcomfort.DnsAutomation
import com.noirdraco.pixelcomfort.DnsStatus
import com.noirdraco.pixelcomfort.History
import com.noirdraco.pixelcomfort.R
import com.noirdraco.pixelcomfort.ScaleMath
import java.util.Date
import java.util.Locale

/*
 * Aus sprachneutralen Werten der Logik werden hier Texte in der Sprache des Geraets.
 * Alle Funktionen nehmen Resources (in Composables: LocalResources.current).
 */

/** Uhrzeit so, wie das Geraet sie eingestellt hat (12/24 Stunden). */
internal fun timeText(context: Context, millis: Long): String =
    DateFormat.getTimeFormat(context).format(Date(millis))

/** Datum in der Schreibweise der Sprache. */
internal fun dateText(context: Context, millis: Long): String =
    DateFormat.getMediumDateFormat(context).format(Date(millis))

/** Schriftgroesse mit dem Dezimaltrenner der Sprache, z. B. "1,15" / "1.15". */
internal fun fontLabel(scale: Float): String = String.format(Locale.getDefault(), "%.2f", scale)

/** Grund eines Fehlschlags: uebersetzt, wenn es ein bekanntes Kuerzel ist. */
internal fun failureText(res: Resources, raw: String): String = when (raw) {
    History.Reason.SHIZUKU_DOWN -> res.getString(R.string.error_shizuku_down)
    History.Reason.SHIZUKU_PERMISSION -> res.getString(R.string.error_shizuku_permission)
    History.Reason.UNREADABLE -> res.getString(R.string.error_unreadable)
    else -> raw
}

/** "Fotos und Kamera", "Firefox, Fotos und 2 weitere", ... */
internal fun appsSummary(res: Resources, labels: List<String>): String {
    val s = AppList.summary(labels)
    return when {
        s.names.isEmpty() -> res.getString(R.string.apps_none)
        s.names.size == 1 -> s.names[0]
        s.more == 0 -> res.getString(R.string.apps_two, s.names[0], s.names[1])
        else -> res.getQuantityString(R.plurals.apps_more, s.more, s.names[0], s.names[1], s.more)
    }
}

internal fun historyTitle(res: Resources, e: History.Entry): String {
    val id = if (e.success) {
        when (e.kind) {
            History.Kind.PAUSE -> R.string.history_pause
            History.Kind.SKIP -> R.string.history_skip
            History.Kind.RESTORE -> R.string.history_restore
            History.Kind.SWITCH -> R.string.history_switch
            History.Kind.TILE -> R.string.history_tile
            History.Kind.WIDTH -> R.string.history_width
            History.Kind.FONT -> R.string.history_font
            History.Kind.DNS_HOME -> R.string.history_dns_home
            History.Kind.DNS_AWAY -> R.string.history_dns_away
        }
    } else {
        when (e.kind) {
            History.Kind.PAUSE -> R.string.history_pause_failed
            History.Kind.SKIP -> R.string.history_skip
            History.Kind.RESTORE -> R.string.history_restore_failed
            History.Kind.SWITCH -> R.string.history_switch_failed
            History.Kind.TILE -> R.string.history_tile_failed
            History.Kind.WIDTH -> R.string.history_width_failed
            History.Kind.FONT -> R.string.history_font_failed
            History.Kind.DNS_HOME -> R.string.history_dns_home_failed
            History.Kind.DNS_AWAY -> R.string.history_dns_away_failed
        }
    }
    // Texte ohne Platzhalter ignorieren das Argument.
    return res.getString(id, e.subject)
}

/** "An → Aus", nur der Zielwert wenn der Ausgangswert fehlt, null wenn es nichts zu zeigen gibt. */
internal fun historyChange(res: Resources, e: History.Entry): String? {
    val from = e.from?.let { valueText(res, e.kind, it) }
    val to = e.to?.let { valueText(res, e.kind, it) }
    return when {
        from != null && to != null -> "$from → $to"
        else -> to
    }
}

private fun valueText(res: Resources, kind: History.Kind, value: String): String = when (value) {
    History.ON -> res.getString(R.string.value_on)
    History.OFF -> res.getString(R.string.value_off)
    History.DEFAULT -> res.getString(R.string.reset_default)
    else -> if (kind == History.Kind.FONT) {
        ScaleMath.parseFontScale(value)?.let(::fontLabel) ?: value
    } else {
        value
    }
}

/** Was gerade gilt: "Zuhause (Home WiFi) · privates DNS aus", "Unterwegs · dns.example.org", ... */
internal fun dnsStateText(res: Resources, s: DnsStatus): String {
    val home = s.homeSsid
    return when (s.mode) {
        DnsAutomation.MODE_OFF ->
            if (home != null) res.getString(R.string.dns_state_home, home) else res.getString(R.string.dns_state_off)

        DnsAutomation.MODE_HOSTNAME -> {
            val name = s.specifier.orEmpty()
            if (s.enabled && home == null) res.getString(R.string.dns_state_away, name) else res.getString(R.string.dns_state_on, name)
        }

        // null = Android-Standard "automatisch"
        else -> res.getString(R.string.dns_state_auto)
    }
}
