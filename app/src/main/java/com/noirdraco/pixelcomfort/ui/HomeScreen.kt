package com.noirdraco.pixelcomfort.ui

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noirdraco.pixelcomfort.ComfortControl
import com.noirdraco.pixelcomfort.ComfortTileService
import com.noirdraco.pixelcomfort.History
import com.noirdraco.pixelcomfort.MainActivity
import com.noirdraco.pixelcomfort.R
import com.noirdraco.pixelcomfort.SettingsWorker
import com.noirdraco.pixelcomfort.SetupStatus
import com.noirdraco.pixelcomfort.ShizukuShell
import com.noirdraco.pixelcomfort.ShizukuWarning
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.concurrent.thread

@Composable
internal fun HomeScreen(
    refreshKey: Int,
    onRefresh: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val context = LocalContext.current
    val res = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf<SetupStatus?>(null) }
    // Schalterstellung direkt nach dem Antippen, bis der echte Wert gelesen ist.
    var pendingOn by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(refreshKey) {
        thread {
            status = SetupStatus.read(context.applicationContext)
            pendingOn = null
        }
    }

    // Darf von jedem Thread aus aufgerufen werden.
    fun say(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    SettingsScaffold(title = stringResource(R.string.app_name), snackbar = snackbar) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PagePadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val s = status

            MainSwitch(
                title = stringResource(R.string.comfort_title),
                summary = comfortSummary(res, s),
                checked = if (s?.comfortOn == null) null else pendingOn ?: s.comfortOn,
                onCheckedChange = { on ->
                    pendingOn = on
                    val app = context.applicationContext
                    SettingsWorker.submit {
                        val result = ComfortControl.set(app, on, ComfortControl.Source.SWITCH)
                        if (!result.success) {
                            val reason = failureText(res, ShizukuShell.explain(result.message))
                            say(res.getString(R.string.toggle_failed, reason))
                        }
                        onRefresh()
                    }
                },
            )

            if (s != null) SetupGroup(s, context, res, ::say, onRefresh)

            ScaleGroup(
                refreshKey = refreshKey,
                onResult = {
                    if (!it.success) say(failureText(res, it.message))
                    onRefresh()
                },
            )

            SettingsGroup(stringResource(R.string.more_title)) {
                SettingsRow(
                    title = stringResource(R.string.apps),
                    summary = s?.let { appsSummary(res, it.targetLabels) },
                    icon = R.drawable.ic_apps,
                    onClick = onOpenApps,
                )
                SettingsRow(
                    title = stringResource(R.string.history),
                    summary = s?.let { lastEntrySummary(context, res, it.lastEntry) },
                    icon = R.drawable.ic_history,
                    onClick = onOpenHistory,
                )
                SettingsRow(
                    title = stringResource(R.string.add_tile),
                    summary = stringResource(R.string.add_tile_summary),
                    icon = R.drawable.ic_tile,
                    onClick = { requestTile(context, res, ::say) },
                )
                SettingsRow(
                    title = stringResource(R.string.advanced),
                    summary = stringResource(R.string.advanced_summary),
                    icon = R.drawable.ic_tune,
                    onClick = onOpenAdvanced,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun comfortSummary(res: Resources, s: SetupStatus?): String = when {
    s == null -> res.getString(R.string.loading)
    s.comfortOn == null -> res.getString(R.string.comfort_unreadable)
    s.suppressed -> res.getString(R.string.comfort_paused)
    !s.comfortOn -> res.getString(R.string.value_off)
    s.ready && s.targetLabels.isNotEmpty() ->
        res.getString(R.string.comfort_on_paused_in, appsSummary(res, s.targetLabels))

    else -> res.getString(R.string.value_on)
}

/**
 * Einrichtung: solange etwas fehlt, eine Zeile pro Punkt - die Zeile selbst fuehrt
 * zur Loesung. Ist alles erledigt, schrumpft die Gruppe auf eine einzige Zeile.
 */
@Composable
private fun SetupGroup(
    s: SetupStatus,
    context: Context,
    res: Resources,
    say: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val ok = MaterialTheme.colorScheme.primary
    val problem = MaterialTheme.colorScheme.error

    // Abgelehnt (oder frueher dauerhaft abgelehnt): dann hilft nur noch die Systemseite.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) openNotificationSettings(context)
        onRefresh()
    }

    if (s.ready && s.notificationsAllowed) {
        SettingsGroup {
            SettingsRow(
                title = stringResource(R.string.setup_done_title),
                summary = stringResource(R.string.setup_done_summary),
                icon = R.drawable.ic_check_circle,
                iconTint = ok,
            )
        }
        return
    }

    SettingsGroup(stringResource(R.string.setup_title)) {
        if (s.advancedProtection == true) {
            SettingsRow(
                title = stringResource(R.string.apm_title),
                summary = stringResource(R.string.apm_summary),
                icon = R.drawable.ic_warning,
                iconTint = problem,
            )
        }
        when {
            !s.shizukuBinder -> SettingsRow(
                title = stringResource(R.string.shizuku),
                summary = stringResource(R.string.shizuku_not_running),
                icon = R.drawable.ic_key,
                iconTint = problem,
                onClick = {
                    val intent = context.packageManager.getLaunchIntentForPackage(ShizukuWarning.SHIZUKU_PACKAGE)
                    if (intent == null) {
                        say(res.getString(R.string.shizuku_not_installed))
                    } else {
                        runCatching { context.startActivity(intent) }
                    }
                },
            )

            !s.shizukuPermission -> SettingsRow(
                title = stringResource(R.string.shizuku),
                summary = stringResource(R.string.shizuku_no_permission),
                icon = R.drawable.ic_key,
                iconTint = problem,
                onClick = {
                    try {
                        Shizuku.requestPermission(MainActivity.SHIZUKU_REQUEST_CODE)
                    } catch (t: Throwable) {
                        say(res.getString(R.string.shizuku_not_ready, t.message.orEmpty()))
                    }
                },
            )

            else -> SettingsRow(
                title = stringResource(R.string.shizuku),
                summary = stringResource(R.string.shizuku_connected),
                icon = R.drawable.ic_key,
                iconTint = ok,
            )
        }
        if (s.accessibilityEnabled) {
            SettingsRow(
                title = stringResource(R.string.accessibility),
                summary = stringResource(R.string.accessibility_on),
                icon = R.drawable.ic_accessibility,
                iconTint = ok,
            )
        } else {
            SettingsRow(
                title = stringResource(R.string.accessibility),
                summary = stringResource(
                    R.string.accessibility_off,
                    stringResource(R.string.accessibility_service_label),
                ),
                icon = R.drawable.ic_accessibility,
                iconTint = problem,
                onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                },
            )
        }
        if (s.notificationsAllowed) {
            SettingsRow(
                title = stringResource(R.string.notifications),
                summary = stringResource(R.string.notifications_on),
                icon = R.drawable.ic_notifications,
                iconTint = ok,
            )
        } else {
            SettingsRow(
                title = stringResource(R.string.notifications),
                summary = stringResource(R.string.notifications_off),
                icon = R.drawable.ic_notifications,
                iconTint = problem,
                onClick = {
                    val needsRequest = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    if (needsRequest) {
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openNotificationSettings(context)
                    }
                },
            )
        }
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(intent) }
}

private fun lastEntrySummary(context: Context, res: Resources, entry: History.Entry?): String {
    if (entry == null) return res.getString(R.string.history_none)
    val day = Instant.ofEpochMilli(entry.time).atZone(ZoneId.systemDefault()).toLocalDate()
    val time = timeText(context, entry.time)
    val whenText = if (day == LocalDate.now()) time else "${dateText(context, entry.time)} $time"
    return "${historyTitle(res, entry)} · $whenText"
}

private fun requestTile(context: Context, res: Resources, say: (String) -> Unit) {
    val label = res.getString(R.string.qs_tile_label)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        say(res.getString(R.string.tile_manual_hint, label))
        return
    }
    val sbm = context.getSystemService(StatusBarManager::class.java)
    if (sbm == null) {
        say(res.getString(R.string.tile_unavailable))
        return
    }
    sbm.requestAddTileService(
        ComponentName(context, ComfortTileService::class.java),
        label,
        Icon.createWithResource(context, R.drawable.ic_qs_comfort),
        context.mainExecutor,
    ) { result ->
        when (result) {
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> say(res.getString(R.string.tile_added))
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> say(res.getString(R.string.tile_exists))
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> Unit
            else -> say(res.getString(R.string.tile_result, result))
        }
    }
}
