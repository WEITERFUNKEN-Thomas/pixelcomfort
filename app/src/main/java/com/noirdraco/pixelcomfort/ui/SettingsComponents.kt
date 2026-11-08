package com.noirdraco.pixelcomfort.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noirdraco.pixelcomfort.R

/*
 * Bausteine im Stil von Pixel RSS: grosser fetter Titel, darunter Gruppen aus Karten.
 * Eine Gruppe hat aussen grosse (24 dp), zwischen den Karten kleine Ecken (6 dp) und
 * einen schmalen Spalt (3 dp). Hintergrund `surface`, Karten `surfaceContainerLow`.
 */

internal val GroupCorner = 24.dp
internal val RowCorner = 6.dp
internal val RowGap = 3.dp
internal val PagePadding = 16.dp

/**
 * Seitengeruest: oben der Kopf ([ScreenHead]), darunter der Inhalt.
 * [onBack] = null auf der Startseite.
 */
@Composable
internal fun SettingsScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    snackbar: SnackbarHostState? = null,
    subtitle: String? = null,
    subtitleColor: Color = Color.Unspecified,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        // Test-Tags als Ressourcen-ID, damit die Messlaeufe (:macrobenchmark) sie finden.
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            ScreenHead(
                title = title,
                onBack = onBack,
                subtitle = subtitle,
                subtitleColor = subtitleColor,
                actions = actions,
            )
        },
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
        content = content,
    )
}

/**
 * Kopf einer Seite wie in Pixel RSS: grosser fetter Titel links, davor der Weg zurueck,
 * rechts die Aktionen als getoente runde Knoepfe. Optional eine Zeile darunter
 * (Zustand, Uhrzeit des letzten Ereignisses).
 */
@Composable
internal fun ScreenHead(
    title: String,
    onBack: (() -> Unit)?,
    subtitle: String? = null,
    subtitleColor: Color = Color.Unspecified,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(start = if (onBack != null) 4.dp else PagePadding, end = 12.dp, top = 12.dp, bottom = 12.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (subtitleColor == Color.Unspecified) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        subtitleColor
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = actions,
        )
    }
}

/** Gruppe aus Karten mit optionaler kleiner Ueberschrift. */
@Composable
internal fun SettingsGroup(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column {
        if (title != null) GroupTitle(title)
        Column(
            modifier = Modifier.clip(RoundedCornerShape(GroupCorner)),
            verticalArrangement = Arrangement.spacedBy(RowGap),
            content = content,
        )
    }
}

@Composable
internal fun GroupTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** Kurzer Erklaertext unter einer Gruppe. */
@Composable
internal fun GroupFooter(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/** Eine Karte: Symbol, Titel, graue Unterzeile, rechts optional ein Bedienelement. */
@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: Int? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    shape: Shape = RoundedCornerShape(RowCorner),
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (leading != null) {
                leading()
            } else if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (summary != null) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

/**
 * Rundes getoentes Symbol vor einer Funktion auf der Startseite: in der Akzentfarbe,
 * solange die Funktion an ist.
 */
@Composable
internal fun IconBadge(icon: Int, active: Boolean, warning: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when {
        warning -> colors.errorContainer to colors.onErrorContainer
        active -> colors.primaryContainer to colors.onPrimaryContainer
        else -> colors.surfaceContainerHighest to colors.onSurfaceVariant
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(container),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
    }
}

/** Zeile mit freiem Inhalt (Regler, Eingabefelder). */
@Composable
internal fun SettingsItem(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(RowCorner),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

/**
 * Der auffaellige Hauptschalter oben auf einer Unterseite. Ist er an, liegt er in der
 * Auswahlfarbe wie eine gewaehlte Karte in Pixel RSS. [checked] = null heisst: Zustand
 * unbekannt, Schalter gesperrt.
 */
@Composable
internal fun MainSwitch(
    title: String,
    summary: String,
    checked: Boolean?,
    onCheckedChange: (Boolean) -> Unit,
) {
    val on = checked == true
    val shape = RoundedCornerShape(GroupCorner)
    val container = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val content = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(
        color = container,
        contentColor = content,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .toggleable(
                value = on,
                enabled = checked != null,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(summary, style = MaterialTheme.typography.bodyMedium)
            }
            // Die ganze Flaeche schaltet; der Switch selbst zeigt nur an.
            PixelSwitch(checked = on, onCheckedChange = null, enabled = checked != null)
        }
    }
}

/** Schalter wie in den Pixel-Einstellungen: eingeschaltet mit Haken im Knopf. */
@Composable
internal fun PixelSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        thumbContent = if (checked) {
            { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
        } else {
            null
        },
    )
}
