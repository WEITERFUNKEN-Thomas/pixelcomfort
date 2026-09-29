package com.noirdraco.pixelcomfort.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.noirdraco.pixelcomfort.DnsAutomation
import com.noirdraco.pixelcomfort.DnsStatus
import com.noirdraco.pixelcomfort.DnsWatcher
import com.noirdraco.pixelcomfort.Prefs
import com.noirdraco.pixelcomfort.PrivateDns
import com.noirdraco.pixelcomfort.R
import com.noirdraco.pixelcomfort.SettingsWorker
import kotlinx.coroutines.launch
import kotlin.concurrent.thread

/**
 * Privates DNS je nach WLAN: zu Hause aus, sonst an. Jede Aenderung gilt sofort und
 * wird gleich auf das aktuelle Netz angewendet.
 */
@Composable
internal fun DnsScreen(refreshKey: Int, onRefresh: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val res = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf<DnsStatus?>(null) }
    // Eingabe im Feld; null = gespeicherten Namen anzeigen.
    var hostnameDraft by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshKey) {
        thread { status = DnsStatus.read(app) }
    }

    fun say(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    /**
     * Konfiguration hat sich geaendert: gleich auf das aktuelle Netz anwenden (die App
     * ist vorn, der WLAN-Name also lesbar) und den Beobachter neu registrieren, damit
     * er neue Berechtigungen mitbekommt.
     */
    fun apply() {
        DnsWatcher.restart()
        SettingsWorker.submit {
            PrivateDns.automation(app).onNetwork(DnsWatcher.current(app))
            onRefresh()
        }
    }

    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        // Abgelehnt (oder frueher dauerhaft abgelehnt): dann hilft nur die App-Seite der Systemeinstellungen.
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] != true) openAppSettings(context)
        apply()
    }

    SettingsScaffold(title = stringResource(R.string.dns_title), onBack = onBack, snackbar = snackbar) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PagePadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val s = status
            // Beim ersten Mal den Namen aus den System-Einstellungen vorschlagen.
            val suggested = s?.hostname?.ifEmpty { s.specifier?.takeIf(DnsAutomation::isValidHostname).orEmpty() }
            val draft = hostnameDraft ?: suggested.orEmpty()
            val draftValid = DnsAutomation.isValidHostname(draft.trim())
            val canEnable = s != null && s.supported && s.homeSsids.isNotEmpty() && draftValid

            MainSwitch(
                title = stringResource(R.string.dns_switch_title),
                summary = when {
                    s == null -> stringResource(R.string.loading)
                    !s.supported -> stringResource(R.string.dns_unsupported)
                    s.enabled -> dnsStateText(res, s)
                    canEnable -> stringResource(R.string.dns_switch_hint)
                    else -> stringResource(R.string.dns_switch_needs)
                },
                checked = when {
                    s == null || !s.supported -> null
                    s.enabled -> true
                    canEnable -> false
                    else -> null
                },
                onCheckedChange = { on ->
                    if (on) {
                        Prefs.setDnsHostname(app, draft.trim())
                        hostnameDraft = null
                        Prefs.setDnsEnabled(app, true)
                        apply()
                    } else {
                        Prefs.setDnsEnabled(app, false)
                        // Nicht ausgeschaltet zurueckbleiben lassen.
                        SettingsWorker.submit {
                            PrivateDns.automation(app).ensureAway()
                            onRefresh()
                        }
                    }
                },
            )

            if (s == null || !s.supported) return@Column

            if (!s.setupComplete) SetupRows(s, context, res, ::say, onRefresh = ::apply, askLocation = {
                askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            })

            SettingsGroup(stringResource(R.string.dns_home_title)) {
                s.homeSsids.sorted().forEach { ssid ->
                    SettingsRow(
                        title = ssid,
                        summary = stringResource(
                            if (ssid == s.homeSsid) R.string.dns_home_connected_remove else R.string.dns_home_remove,
                        ),
                        icon = R.drawable.ic_wifi,
                        iconTint = if (ssid == s.homeSsid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = {
                            Prefs.setDnsHomeSsids(app, Prefs.getDnsHomeSsids(app) - ssid)
                            apply()
                        },
                    )
                }
                AddCurrentRow(s, onAdd = { ssid ->
                    Prefs.setDnsHomeSsids(app, Prefs.getDnsHomeSsids(app) + ssid)
                    apply()
                })
            }
            GroupFooter(stringResource(R.string.dns_home_footer))

            SettingsGroup(stringResource(R.string.dns_hostname_title)) {
                SettingsItem {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { hostnameDraft = it },
                        label = { Text(stringResource(R.string.dns_hostname_label)) },
                        isError = draft.isNotBlank() && !draftValid,
                        supportingText = if (draft.isNotBlank() && !draftValid) {
                            { Text(stringResource(R.string.dns_hostname_invalid)) }
                        } else {
                            null
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        modifier = Modifier.align(Alignment.End),
                        enabled = draftValid && draft.trim() != s.hostname,
                        onClick = {
                            Prefs.setDnsHostname(app, draft.trim())
                            hostnameDraft = null
                            say(res.getString(R.string.saved))
                            apply()
                        },
                    ) { Text(stringResource(R.string.save)) }
                }
            }
            GroupFooter(stringResource(R.string.dns_hostname_footer))

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Was fehlt, damit die Automatik zu Hause ausschalten kann - jede Zeile fuehrt zur Loesung. */
@Composable
private fun SetupRows(
    s: DnsStatus,
    context: Context,
    res: Resources,
    say: (String) -> Unit,
    onRefresh: () -> Unit,
    askLocation: () -> Unit,
) {
    val problem = MaterialTheme.colorScheme.error
    SettingsGroup(stringResource(R.string.setup_title)) {
        if (!s.serviceEnabled) {
            SettingsRow(
                title = stringResource(R.string.accessibility),
                summary = stringResource(R.string.accessibility_off, stringResource(R.string.accessibility_service_label)),
                icon = R.drawable.ic_accessibility,
                iconTint = problem,
                onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } },
            )
        }
        if (!s.locationPermission) {
            SettingsRow(
                title = stringResource(R.string.dns_location),
                summary = stringResource(R.string.dns_location_missing),
                icon = R.drawable.ic_location,
                iconTint = problem,
                onClick = askLocation,
            )
        }
        if (!s.locationOn) {
            SettingsRow(
                title = stringResource(R.string.dns_location_off_title),
                summary = stringResource(R.string.dns_location_off),
                icon = R.drawable.ic_location,
                iconTint = problem,
                onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } },
            )
        }
        if (!s.writeSecure) {
            SettingsRow(
                title = stringResource(R.string.adv_write_secure),
                summary = stringResource(R.string.perm_missing),
                icon = R.drawable.ic_key,
                iconTint = problem,
                onClick = { grantViaShizuku(context, res, say, onRefresh) },
            )
        }
    }
}

/** "Aktuelles WLAN hinzufuegen" - gesperrt mit Begruendung, wenn es nicht als Zuhause taugt. */
@Composable
private fun AddCurrentRow(s: DnsStatus, onAdd: (String) -> Unit) {
    val n = s.network
    val ssid = n?.ssid
    val blocked: String? = when {
        n == null || !n.isWifi -> stringResource(R.string.dns_home_add_none)
        ssid == null -> stringResource(R.string.dns_home_add_unknown)
        n.security != DnsAutomation.Security.SECURED -> stringResource(R.string.dns_home_add_open, ssid)
        ssid in s.homeSsids -> stringResource(R.string.dns_home_add_exists, ssid)
        else -> null
    }
    SettingsRow(
        title = stringResource(R.string.dns_home_add),
        summary = blocked ?: ssid,
        icon = R.drawable.ic_add,
        iconTint = if (blocked == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = if (blocked == null && ssid != null) {
            { onAdd(ssid) }
        } else {
            null
        },
    )
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
    runCatching { context.startActivity(intent) }
}
