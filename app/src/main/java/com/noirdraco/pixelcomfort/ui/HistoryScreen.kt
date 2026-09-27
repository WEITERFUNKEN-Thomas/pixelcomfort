package com.noirdraco.pixelcomfort.ui

import android.content.Context
import android.content.res.Resources
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noirdraco.pixelcomfort.History
import com.noirdraco.pixelcomfort.HistoryStore
import com.noirdraco.pixelcomfort.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.concurrent.thread

/** Was die App zuletzt geschaltet hat, neueste Eintraege oben, nach Tagen gruppiert. */
@Composable
internal fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val res = LocalResources.current
    var entries by remember { mutableStateOf<List<History.Entry>?>(null) }

    LaunchedEffect(Unit) {
        thread { entries = HistoryStore.load(context) }
    }

    SettingsScaffold(
        title = stringResource(R.string.history),
        onBack = onBack,
        actions = {
            TextButton(
                enabled = !entries.isNullOrEmpty(),
                onClick = {
                    HistoryStore.clear(context)
                    entries = emptyList()
                },
            ) { Text(stringResource(R.string.history_clear)) }
        },
    ) { padding ->
        val loaded = entries
        if (loaded.isNullOrEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = PagePadding)) {
                GroupFooter(
                    stringResource(if (loaded == null) R.string.history_loading else R.string.history_empty),
                )
            }
            return@SettingsScaffold
        }

        val zone = ZoneId.systemDefault()
        val days = loaded.groupBy { Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = PagePadding, end = PagePadding, bottom = 24.dp),
        ) {
            days.forEach { (day, dayEntries) ->
                item(key = "day-$day") { GroupTitle(dayLabel(context, res, day, dayEntries.first().time)) }
                dayEntries.forEachIndexed { index, entry ->
                    item(key = "${entry.time}-${entry.kind}-$index-$day") {
                        val time = timeText(context, entry.time)
                        SettingsRow(
                            title = historyTitle(res, entry),
                            summary = summary(res, time, entry),
                            icon = if (entry.success) icon(entry.kind) else R.drawable.ic_warning,
                            iconTint = if (entry.success) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            shape = rowShape(index, dayEntries.size),
                        )
                        Spacer(Modifier.height(if (index == dayEntries.size - 1) 16.dp else RowGap))
                    }
                }
            }
        }
    }
}

private fun summary(res: Resources, time: String, entry: History.Entry): String = listOfNotNull(
    time,
    historyChange(res, entry),
    entry.message.ifEmpty { null }?.let { failureText(res, it) },
).joinToString(" · ")

private fun dayLabel(context: Context, res: Resources, day: LocalDate, millis: Long): String = when (day) {
    LocalDate.now() -> res.getString(R.string.today)
    LocalDate.now().minusDays(1) -> res.getString(R.string.yesterday)
    else -> dateText(context, millis)
}

private fun icon(kind: History.Kind): Int = when (kind) {
    History.Kind.PAUSE, History.Kind.SKIP, History.Kind.RESTORE -> R.drawable.ic_qs_comfort
    History.Kind.SWITCH -> R.drawable.ic_qs_comfort
    History.Kind.TILE -> R.drawable.ic_tile
    History.Kind.WIDTH, History.Kind.FONT -> R.drawable.ic_tune
}

/** Erste und letzte Zeile bekommen die grossen Aussen-Ecken der Gruppe. */
internal fun rowShape(index: Int, count: Int): RoundedCornerShape {
    val top = if (index == 0) GroupCorner else RowCorner
    val bottom = if (index == count - 1) GroupCorner else RowCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}
