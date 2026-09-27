package com.noirdraco.pixelcomfort.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.noirdraco.pixelcomfort.AppList
import com.noirdraco.pixelcomfort.Prefs
import com.noirdraco.pixelcomfort.R
import kotlin.concurrent.thread

private class InstalledApps(val labels: Map<String, String>, val icons: Map<String, ImageBitmap>)

/** Groesse, in der die App-Symbole gerastert werden (px). */
private const val ICON_PX = 96

/** Auswahl der Apps, in denen der Augenkomfort pausiert. Jede Aenderung gilt sofort. */
@Composable
internal fun AppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var apps by remember { mutableStateOf<InstalledApps?>(null) }
    var selected by remember { mutableStateOf(Prefs.getPackages(context)) }
    // Auswahl beim Oeffnen: bestimmt, was oben steht (Zeilen springen beim Umschalten nicht).
    val pinned = remember { Prefs.getPackages(context) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        thread { apps = loadInstalledApps(context.applicationContext) }
    }

    SettingsScaffold(title = stringResource(R.string.apps), onBack = onBack) { padding ->
        val loaded = apps
        if (loaded == null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = PagePadding)) {
                GroupFooter(stringResource(R.string.apps_loading))
            }
            return@SettingsScaffold
        }
        val entries = AppList.build(loaded.labels, selected, pinned, query)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = PagePadding, end = PagePadding, bottom = 24.dp),
        ) {
            item(key = "intro") {
                GroupFooter(stringResource(R.string.apps_intro))
                Spacer(Modifier.height(16.dp))
                SearchField(query, onChange = { query = it })
                Spacer(Modifier.height(16.dp))
            }
            if (entries.isEmpty()) {
                item(key = "empty") { GroupFooter(stringResource(R.string.apps_empty)) }
            }
            itemsIndexed(entries, key = { _, e -> e.packageName }) { index, entry ->
                val toggle = { on: Boolean ->
                    selected = AppList.toggle(selected, entry.packageName, on)
                    Prefs.setPackages(context, selected)
                }
                SettingsRow(
                    title = entry.label,
                    summary = if (entry.installed) null else stringResource(R.string.app_not_installed),
                    shape = rowShape(index, entries.size),
                    onClick = { toggle(!entry.selected) },
                    leading = { AppIcon(loaded.icons[entry.packageName]) },
                    trailing = { Switch(checked = entry.selected, onCheckedChange = toggle) },
                )
                Spacer(Modifier.height(RowGap))
            }
        }
    }
}

@Composable
private fun AppIcon(icon: ImageBitmap?) {
    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
    } else {
        Icon(
            painterResource(R.drawable.ic_apps),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp).padding(8.dp),
        )
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(stringResource(R.string.apps_search)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Alle startbaren Apps ausser uns selbst. Sichtbar durch den <queries>-Eintrag im
 * Manifest (MAIN/LAUNCHER) - eine Berechtigung ist dafuer nicht noetig.
 */
private fun loadInstalledApps(context: Context): InstalledApps {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val labels = mutableMapOf<String, String>()
    val icons = mutableMapOf<String, ImageBitmap>()
    for (info in pm.queryIntentActivities(launcher, 0)) {
        val pkg = info.activityInfo.packageName
        if (pkg == context.packageName || pkg in labels) continue
        labels[pkg] = info.loadLabel(pm).toString()
        runCatching { info.loadIcon(pm).toBitmap(ICON_PX, ICON_PX).asImageBitmap() }
            .onSuccess { icons[pkg] = it }
    }
    return InstalledApps(labels, icons)
}
