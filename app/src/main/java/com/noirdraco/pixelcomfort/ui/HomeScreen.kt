package com.noirdraco.pixelcomfort.ui

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Icon as TileIcon
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noirdraco.pixelcomfort.AutoRotate
import com.noirdraco.pixelcomfort.ComfortControl
import com.noirdraco.pixelcomfort.aod.AodMonitor
import com.noirdraco.pixelcomfort.aod.Status as AodStatus
import com.noirdraco.pixelcomfort.ComfortTileService
import com.noirdraco.pixelcomfort.DnsAutomation
import com.noirdraco.pixelcomfort.DnsStatus
import com.noirdraco.pixelcomfort.History
import com.noirdraco.pixelcomfort.MainActivity
import com.noirdraco.pixelcomfort.MobileIcon
import com.noirdraco.pixelcomfort.NetworkWatcher
import com.noirdraco.pixelcomfort.Prefs
import com.noirdraco.pixelcomfort.R
import com.noirdraco.pixelcomfort.NfcTileService
import com.noirdraco.pixelcomfort.RotateTileService
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

/**
 * Startseite als Uebersicht: oben Titel und was zuletzt passiert ist, darunter eine Karte
 * pro Funktion mit Schalter und Zustand. Antippen der Karte fuehrt zu den Details.
 * Solange etwas fehlt, steht die Einrichtung ganz oben.
 */
@Composable
internal fun HomeScreen(
    refreshKey: Int,
    onRefresh: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenRotateApps: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDns: () -> Unit,
    onOpenDisplay: () -> Unit,
    onOpenAod: () -> Unit,
) {
    val context = LocalContext.current
    val res = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf<SetupStatus?>(null) }
    // Schalterstellung direkt nach dem Antippen, bis der echte Wert gelesen ist.
    var pendingOn by remember { mutableStateOf<Boolean?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    // Always-On-Display-Regeln: eigene Datenhaltung, live beobachtet.
    val aodRepo = remember { AodMonitor.repository(context.applicationContext) }
    val aodData by aodRepo.data.collectAsState(initial = null)
    val aodStatus by AodMonitor.status.collectAsState()
    val aodController = remember { AodMonitor.controller(context.applicationContext) }
    val aodOn by remember { aodController.observe() }.collectAsState(initial = aodController.read())

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

    val s = status
    val complete = s != null && s.ready && s.notificationsAllowed

    SettingsScaffold(
        title = stringResource(R.string.app_name),
        snackbar = snackbar,
        subtitle = when {
            s == null -> stringResource(R.string.loading)
            !complete -> stringResource(R.string.home_setup_needed)
            else -> s.lastEntry?.let { lastEntrySummary(context, res, it) } ?: stringResource(R.string.setup_done_title)
        },
        subtitleColor = if (s != null && !complete) MaterialTheme.colorScheme.error else Color.Unspecified,
        actions = {
            FilledTonalIconButton(onClick = onOpenHistory, modifier = Modifier.testTag("history")) {
                Icon(painterResource(R.drawable.ic_history), contentDescription = stringResource(R.string.history))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_options))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_tile_named, stringResource(R.string.qs_tile_label))) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_qs_comfort), contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            requestTile(context, res, ComfortTileService::class.java, R.string.qs_tile_label, R.drawable.ic_qs_comfort, ::say)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_tile_named, stringResource(R.string.rotate_tile_label))) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_screen_rotation), contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            requestTile(context, res, RotateTileService::class.java, R.string.rotate_tile_label, R.drawable.ic_screen_rotation, ::say)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_tile_named, stringResource(R.string.nfc_tile_label))) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_nfc), contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            requestTile(context, res, NfcTileService::class.java, R.string.nfc_tile_label, R.drawable.ic_nfc, ::say)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.advanced)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_tune), contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onOpenAdvanced()
                        },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("home_list")
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PagePadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (s != null && !complete) SetupGroup(s, context, res, ::say, onRefresh)

            // Freiwillig: genauer erkennen, welche App vorn ist (zaehlt nicht zur Einrichtung).
            if (s != null && s.usageHint) {
                SettingsGroup {
                    SettingsRow(
                        title = stringResource(R.string.usage_access),
                        summary = stringResource(R.string.usage_hint_summary),
                        icon = R.drawable.ic_key,
                        onClick = {
                            if (s.shizukuReady) {
                                grantViaShizuku(context, res, ::say, onRefresh)
                            } else {
                                openUsageAccessSettings(context)
                            }
                        },
                    )
                }
            }

            SettingsGroup {
                // ---- Augenkomfort: Schalter = Filter von Hand, Karte = Apps ----
                val comfortOn = if (s?.comfortOn == null) null else pendingOn ?: s.comfortOn
                FeatureRow(
                    tag = "feature_comfort",
                    title = stringResource(R.string.comfort_title),
                    summary = comfortSummary(res, s),
                    icon = R.drawable.ic_qs_comfort,
                    active = comfortOn == true,
                    warning = s != null && s.comfortOn == null,
                    checked = comfortOn,
                    onClick = onOpenApps,
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

                // ---- Privates DNS: Schalter = Automatik, Karte = Einrichtung ----
                val dns = s?.dns
                val dnsReady = dns != null &&
                    dns.homeSsids.isNotEmpty() && DnsAutomation.isValidHostname(dns.hostname)
                FeatureRow(
                    tag = "feature_dns",
                    title = stringResource(R.string.dns_title),
                    summary = dns?.let { dnsSummary(res, it) },
                    icon = R.drawable.ic_dns,
                    active = dns?.enabled == true,
                    warning = dns != null && dns.enabled && !dns.setupComplete,
                    checked = when {
                        dns == null -> null
                        dns.enabled -> true
                        dnsReady -> false
                        // Noch nicht eingerichtet: erst die Seite, dann der Schalter.
                        else -> null
                    },
                    onClick = onOpenDns,
                    onCheckedChange = { on -> setDnsAutomation(context.applicationContext, on, onRefresh) },
                )

                // ---- Mobilfunk-Symbol: keine eigene Seite, die Karte schaltet ----
                val hideMobile = s?.hideMobileOnWifi
                FeatureRow(
                    tag = "feature_mobile_icon",
                    title = stringResource(R.string.hide_mobile_title),
                    summary = s?.let { mobileIconSummary(res, it) },
                    icon = R.drawable.ic_signal,
                    active = hideMobile == true,
                    warning = s != null && s.hideMobileOnWifi && !s.accessibilityEnabled,
                    checked = hideMobile,
                    onClick = s?.let { st -> { setHideMobile(context, !st.hideMobileOnWifi, st.accessibilityEnabled, onRefresh) } },
                    onCheckedChange = s?.let { st -> { on -> setHideMobile(context, on, st.accessibilityEnabled, onRefresh) } },
                )

                // ---- Automatisch drehen: Schalter = Funktion, Karte = Apps ----
                FeatureRow(
                    tag = "feature_rotate",
                    title = stringResource(R.string.rotate_title),
                    summary = s?.let { rotateSummary(res, it) },
                    icon = R.drawable.ic_screen_rotation,
                    active = s?.rotateEnabled == true,
                    warning = s != null && s.rotateEnabled && (s.rotateLabels.isEmpty() || !s.accessibilityEnabled),
                    checked = s?.rotateEnabled,
                    onClick = onOpenRotateApps,
                    onCheckedChange = { on -> AutoRotate.setEnabled(context, on, onRefresh) },
                )

                // ---- Always-On-Display: Schalter = Automatik, Karte = Regeln ----
                val aod = aodData
                FeatureRow(
                    tag = "feature_aod",
                    title = stringResource(R.string.aod_title),
                    summary = if (aod == null || s == null) null else aodStateText(res, aod, aodStatus, aodOn, s.accessibilityEnabled),
                    icon = R.drawable.ic_aod,
                    active = aod != null && aod.automation && aod.rules.any { it.enabled },
                    warning = aod != null && aod.automation && aod.rules.any { it.enabled } &&
                        (s?.accessibilityEnabled == false || aodStatus == AodStatus.MissingPermission),
                    checked = aod?.automation,
                    onClick = onOpenAod,
                    onCheckedChange = { on -> scope.launch { aodRepo.setAutomation(on) } },
                )

                // ---- Anzeige: nur eine Seite, kein Schalter ----
                FeatureRow(
                    tag = "feature_display",
                    title = stringResource(R.string.display_title),
                    summary = stringResource(R.string.display_summary),
                    icon = R.drawable.ic_tune,
                    active = false,
                    hasSwitch = false,
                    onClick = onOpenDisplay,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Karte einer Funktion auf der Startseite: getoentes Symbol, Titel, Zustand und rechts
 * der Schalter - oder ein Pfeil, wenn es nur eine Seite gibt ([hasSwitch] = false).
 * [checked] = null heisst: Zustand unbekannt oder noch nicht schaltbar, Schalter gesperrt.
 */
@Composable
private fun FeatureRow(
    tag: String,
    title: String,
    summary: String?,
    icon: Int,
    active: Boolean,
    warning: Boolean = false,
    hasSwitch: Boolean = true,
    checked: Boolean? = null,
    onClick: (() -> Unit)?,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    SettingsRow(
        modifier = Modifier.testTag(tag),
        title = title,
        summary = summary ?: stringResource(R.string.loading),
        onClick = onClick,
        leading = { IconBadge(icon, active = active, warning = warning) },
        trailing = {
            if (hasSwitch) {
                PixelSwitch(
                    checked = checked == true,
                    onCheckedChange = onCheckedChange,
                    enabled = checked != null && onCheckedChange != null,
                )
            } else {
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

private fun comfortSummary(res: Resources, s: SetupStatus?): String = when {
    s == null -> res.getString(R.string.loading)
    s.comfortSettingMissing -> res.getString(R.string.comfort_missing)
    s.comfortOn == null -> res.getString(R.string.comfort_unreadable)
    s.suppressed -> res.getString(R.string.comfort_paused)
    !s.comfortOn -> res.getString(R.string.value_off)
    s.ready && s.targetLabels.isNotEmpty() ->
        res.getString(R.string.comfort_on_paused_in, appsSummary(res, s.targetLabels))

    else -> res.getString(R.string.value_on)
}

private fun mobileIconSummary(res: Resources, s: SetupStatus): String = when {
    !s.hideMobileOnWifi -> res.getString(R.string.hide_mobile_summary_off)
    !s.accessibilityEnabled ->
        res.getString(R.string.hide_mobile_needs_service, res.getString(R.string.accessibility_service_label))

    else -> res.getString(R.string.hide_mobile_summary_on)
}

/**
 * Einschalten wirkt sofort auf das aktuelle Netz - aber nur, wenn die Bedienungshilfe
 * laeuft: ohne sie meldet niemand den naechsten Netzwechsel, das Symbol bliebe sonst
 * auch unterwegs ausgeblendet. Startet sie spaeter, gleicht der [NetworkWatcher] selbst ab.
 * Ausschalten zeigt das Symbol immer sofort wieder.
 */
private fun setHideMobile(context: Context, on: Boolean, serviceRunning: Boolean, onRefresh: () -> Unit) {
    val app = context.applicationContext
    Prefs.setHideMobileOnWifi(app, on)
    SettingsWorker.submit {
        val automation = MobileIcon.automation(app)
        if (!on) {
            automation.ensureShown()
        } else if (serviceRunning) {
            automation.onNetwork(onWifi = NetworkWatcher.current(app)?.wifiTransport == true)
        }
        onRefresh()
    }
}

private fun rotateSummary(res: Resources, s: SetupStatus): String = when {
    !s.rotateEnabled -> res.getString(R.string.rotate_summary_off)
    s.rotateLabels.isEmpty() -> res.getString(R.string.rotate_summary_no_apps)
    !s.accessibilityEnabled ->
        res.getString(R.string.hide_mobile_needs_service, res.getString(R.string.accessibility_service_label))

    else -> res.getString(R.string.rotate_summary_on, appsSummary(res, s.rotateLabels))
}

private fun dnsSummary(res: Resources, s: DnsStatus): String = when {
    !s.enabled -> res.getString(R.string.dns_summary_off)
    !s.setupComplete -> res.getString(R.string.dns_summary_setup)
    else -> dnsStateText(res, s)
}

/**
 * Einrichtung: solange etwas fehlt, eine Zeile pro Punkt - die Zeile selbst fuehrt
 * zur Loesung. Ist alles erledigt, verschwindet die Gruppe, der Kopf sagt es dann.
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
                    val needsRequest = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
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

/**
 * "11:23 · Mobilfunk-Symbol ausgeblendet (WLAN)" - wie "Aktualisiert 13:03" in Pixel RSS.
 * Die Zeit steht vorn, damit sie auch bei langem Text sichtbar bleibt.
 */
private fun lastEntrySummary(context: Context, res: Resources, entry: History.Entry): String {
    val day = Instant.ofEpochMilli(entry.time).atZone(ZoneId.systemDefault()).toLocalDate()
    val time = timeText(context, entry.time)
    val whenText = if (day == LocalDate.now()) time else "${dateText(context, entry.time)} $time"
    return "$whenText · ${historyTitle(res, entry)}"
}

private fun requestTile(
    context: Context,
    res: Resources,
    service: Class<*>,
    labelRes: Int,
    iconRes: Int,
    say: (String) -> Unit,
) {
    val label = res.getString(labelRes)
    val sbm = context.getSystemService(StatusBarManager::class.java)
    if (sbm == null) {
        say(res.getString(R.string.tile_unavailable))
        return
    }
    sbm.requestAddTileService(
        ComponentName(context, service),
        label,
        TileIcon.createWithResource(context, iconRes),
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
