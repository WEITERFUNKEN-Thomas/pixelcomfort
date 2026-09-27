package com.noirdraco.pixelcomfort.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noirdraco.pixelcomfort.DisplayScale
import com.noirdraco.pixelcomfort.R
import com.noirdraco.pixelcomfort.ScaleMath
import com.noirdraco.pixelcomfort.SettingsWorker
import kotlin.math.roundToInt

/**
 * Gruppe "Anzeige": zwei Schieberegler (Anzeigegroesse = kleinste Breite, Schriftgroesse)
 * mit je einem Button fuer den Standardwert.
 *
 * Geschrieben wird erst beim Loslassen des Reglers. Beim Aendern startet Android die
 * Activity neu (Konfigurationswechsel); die Gruppe liest die Werte danach frisch ein.
 */
@Composable
internal fun ScaleGroup(refreshKey: Int, onResult: (DisplayScale.Result) -> Unit) {
    val context = LocalContext.current.applicationContext

    var state by remember { mutableStateOf<DisplayScale.State?>(null) }
    // Reglerstellung waehrend des Ziehens; null = Systemwert anzeigen.
    var widthDraft by remember { mutableStateOf<Int?>(null) }
    var fontDraft by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(refreshKey) {
        SettingsWorker.submit {
            state = DisplayScale.read(context)
            widthDraft = null
            fontDraft = null
        }
    }

    fun apply(block: () -> DisplayScale.Result) {
        SettingsWorker.submit {
            val result = try {
                block()
            } catch (t: Throwable) {
                DisplayScale.Result(false, t.message ?: t.javaClass.simpleName)
            }
            // onResult stoesst das Neu-Einlesen an (refreshKey).
            onResult(result)
        }
    }

    SettingsGroup(stringResource(R.string.display_title)) {
        val s = state

        // ---- Anzeigegroesse (kleinste Breite) ----
        val width = widthDraft ?: s?.widthDp
        val widthEnabled = s != null && s.shizukuReady && s.widthDp != null
        ScaleItem(
            title = stringResource(R.string.display_size),
            value = width?.let { "$it dp" } ?: "–",
            hint = when {
                s == null -> stringResource(R.string.loading)
                !widthEnabled -> stringResource(R.string.needs_shizuku)
                else -> stringResource(R.string.display_size_hint, s.defaultWidthDp?.toString() ?: "–")
            },
            sliderValue = ScaleMath.clampWidthDp(width ?: ScaleMath.MIN_WIDTH_DP).toFloat(),
            range = ScaleMath.MIN_WIDTH_DP.toFloat()..ScaleMath.MAX_WIDTH_DP.toFloat(),
            enabled = widthEnabled,
            onChange = { widthDraft = it.roundToInt() },
            onFinished = { widthDraft?.let { dp -> apply { DisplayScale.setWidthDp(context, dp) } } },
            onDefault = { apply { DisplayScale.resetWidth(context) } },
        )

        // ---- Schriftgroesse ----
        val font = fontDraft ?: s?.fontScale
        ScaleItem(
            title = stringResource(R.string.font_size),
            value = font?.let(::fontLabel) ?: "–",
            hint = if (s == null) {
                stringResource(R.string.loading)
            } else {
                stringResource(R.string.font_size_hint, fontLabel(ScaleMath.DEFAULT_FONT_SCALE))
            },
            sliderValue = (font ?: ScaleMath.DEFAULT_FONT_SCALE)
                .coerceIn(ScaleMath.MIN_FONT_SCALE, ScaleMath.MAX_FONT_SCALE),
            range = ScaleMath.MIN_FONT_SCALE..ScaleMath.MAX_FONT_SCALE,
            enabled = font != null,
            onChange = { fontDraft = ScaleMath.snapFontScale(it) },
            onFinished = { fontDraft?.let { scale -> apply { DisplayScale.setFontScale(context, scale) } } },
            onDefault = { apply { DisplayScale.setFontScale(context, ScaleMath.DEFAULT_FONT_SCALE) } },
        )
    }
}

@Composable
private fun ScaleItem(
    title: String,
    value: String,
    hint: String,
    sliderValue: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
    onDefault: () -> Unit,
) {
    SettingsItem {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = range,
            enabled = enabled,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDefault, enabled = enabled) {
                Text(stringResource(R.string.reset_default), maxLines = 1, softWrap = false)
            }
        }
    }
}
