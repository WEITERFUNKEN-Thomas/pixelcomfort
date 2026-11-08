package com.noirdraco.pixelcomfort.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.noirdraco.pixelcomfort.ComfortAccessibilityService
import com.noirdraco.pixelcomfort.NetworkWatcher
import com.noirdraco.pixelcomfort.R
import com.noirdraco.pixelcomfort.aod.AodAction
import com.noirdraco.pixelcomfort.aod.AodData
import com.noirdraco.pixelcomfort.aod.AodMonitor
import com.noirdraco.pixelcomfort.aod.Rule
import com.noirdraco.pixelcomfort.aod.Status
import com.noirdraco.pixelcomfort.aod.Trigger
import com.noirdraco.pixelcomfort.aod.WifiSsid
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.UUID

/** Was die Seite an Berechtigungen kennen muss. */
internal data class AodPerms(
    val secure: Boolean,
    val bluetooth: Boolean,
    val location: Boolean,
    val service: Boolean,
) {
    companion object {
        fun read(context: Context): AodPerms {
            fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
            return AodPerms(
                secure = granted(Manifest.permission.WRITE_SECURE_SETTINGS),
                bluetooth = granted(Manifest.permission.BLUETOOTH_CONNECT),
                location = granted(Manifest.permission.ACCESS_FINE_LOCATION),
                service = ComfortAccessibilityService.isEnabled(context),
            )
        }
    }
}

/**
 * Always-On-Display je nach Bluetooth-Gerät oder WLAN (aus PixelAwakeLink): Hauptschalter,
 * fehlende Berechtigungen, Regelliste zum Sortieren und das Blatt zum Anlegen/Bearbeiten.
 */
@Composable
internal fun AodScreen(refreshKey: Int, onRefresh: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val repo = remember { AodMonitor.repository(app) }
    val data by repo.data.collectAsState(initial = null)
    val status by AodMonitor.status.collectAsState()
    val aodOn by remember { AodMonitor.controller(app).observe() }.collectAsState(initial = AodMonitor.controller(app).read())
    var perms by remember { mutableStateOf(AodPerms.read(app)) }
    LaunchedEffect(refreshKey) { perms = AodPerms.read(app) }

    fun say(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    // WRITE_SECURE_SETTINGS per Shizuku erteilen (wie auf der DNS-Seite), danach Quellen neu.
    val grantSecure = {
        grantViaShizuku(app, res, ::say) {
            AodMonitor.restart()
            onRefresh()
        }
    }

    // Nach einer neuen Berechtigung die Quellen neu registrieren lassen.
    val askPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { !it }) openAppDetails(context)
        AodMonitor.restart()
        perms = AodPerms.read(app)
        onRefresh()
    }
    val askBluetooth = { askPermissions.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT)) }
    val askLocation = {
        askPermissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }

    // Lokale Reihenfolge während des Ziehens; gespeichert wird beim Loslassen.
    val stored = data?.rules.orEmpty()
    var rules by remember(stored) { mutableStateOf(stored) }
    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = rules.indexOfFirst { it.id == from.key }
        val toIndex = rules.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) rules = rules.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }

    SettingsScaffold(title = stringResource(R.string.aod_title), onBack = onBack, snackbar = snackbar) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = PagePadding, end = PagePadding, bottom = 24.dp),
        ) {
            item(key = "switch") {
                val d = data
                MainSwitch(
                    title = stringResource(R.string.aod_automation),
                    summary = if (d == null) stringResource(R.string.loading) else aodStateText(res, d, status, aodOn, perms.service),
                    checked = d?.automation,
                    onCheckedChange = { on -> scope.launch { repo.setAutomation(on) } },
                )
                Spacer(Modifier.height(16.dp))
            }

            val d = data ?: return@LazyColumn
            val needsBluetooth = !perms.bluetooth && d.rules.any { it.trigger is Trigger.Bluetooth }
            val needsLocation = !perms.location && d.rules.any { it.trigger is Trigger.Wifi }
            if (!perms.service || !perms.secure || needsBluetooth || needsLocation) {
                item(key = "setup") {
                    AodSetup(context, perms, needsBluetooth, needsLocation, askBluetooth, askLocation, grantSecure)
                    Spacer(Modifier.height(16.dp))
                }
            }

            item(key = "rules_title") { GroupTitle(stringResource(R.string.aod_rules_title)) }
            if (rules.isEmpty()) {
                item(key = "empty") {
                    SettingsRow(title = stringResource(R.string.aod_rules_empty), shape = rowShape(0, 2))
                    Spacer(Modifier.height(RowGap))
                }
            }
            itemsIndexed(rules, key = { _, rule -> rule.id }) { index, rule ->
                ReorderableItem(reorder, key = rule.id) {
                    SettingsRow(
                        title = rule.trigger.label,
                        summary = stringResource(if (rule.action == AodAction.ON) R.string.aod_action_on else R.string.aod_action_off),
                        icon = if (rule.trigger is Trigger.Wifi) R.drawable.ic_wifi else R.drawable.ic_bluetooth,
                        shape = rowShape(index, rules.size + 1),
                        onClick = { editingId = rule.id; sheetOpen = true },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PixelSwitch(
                                    checked = rule.enabled,
                                    onCheckedChange = { on -> scope.launch { repo.setRuleEnabled(rule.id, on) } },
                                )
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier.draggableHandle(onDragStopped = { scope.launch { repo.setOrder(rules.map { it.id }) } }),
                                ) {
                                    Icon(painterResource(R.drawable.ic_drag_handle), contentDescription = stringResource(R.string.aod_reorder))
                                }
                            }
                        },
                    )
                    Spacer(Modifier.height(RowGap))
                }
            }
            item(key = "add") {
                SettingsRow(
                    title = stringResource(R.string.aod_add_rule),
                    icon = R.drawable.ic_add,
                    iconTint = MaterialTheme.colorScheme.primary,
                    shape = rowShape(maxOf(rules.size, 1), maxOf(rules.size, 1) + 1),
                    onClick = { editingId = null; sheetOpen = true },
                )
                Spacer(Modifier.height(8.dp))
                GroupFooter(stringResource(R.string.aod_rules_footer))
            }
        }
    }

    if (sheetOpen) {
        val editing = data?.rules?.firstOrNull { it.id == editingId }
        val bonded = remember(perms.bluetooth) { AodMonitor.bluetooth(app).bondedDevices() }
        val currentSsid = remember(perms.location) { NetworkWatcher.current(app)?.ssid }
        RuleSheet(
            rule = editing,
            bonded = bonded,
            currentSsid = currentSsid,
            perms = perms,
            onRequestBluetooth = askBluetooth,
            onRequestLocation = askLocation,
            onSave = { rule -> scope.launch { repo.upsertRule(rule) }; sheetOpen = false },
            onDelete = { id -> scope.launch { repo.deleteRule(id) }; sheetOpen = false },
            onDismiss = { sheetOpen = false },
        )
    }
}

/** Zustand in einem Satz - für den Hauptschalter hier und die Karte auf der Startseite. */
internal fun aodStateText(res: Resources, data: AodData, status: Status, aodOn: Boolean, serviceRunning: Boolean): String = when {
    !data.automation -> res.getString(R.string.aod_summary_off)
    data.rules.none { it.enabled } -> res.getString(R.string.aod_summary_no_rules)
    !serviceRunning -> res.getString(R.string.hide_mobile_needs_service, res.getString(R.string.accessibility_service_label))
    else -> when (status) {
        is Status.RuleActive -> res.getString(
            if (aodOn) R.string.aod_status_rule_on else R.string.aod_status_rule_off,
            data.rules.firstOrNull { it.id == status.ruleId }?.trigger?.label.orEmpty(),
        )
        is Status.Overridden -> res.getString(R.string.aod_status_overridden)
        Status.MissingPermission -> res.getString(R.string.aod_status_missing_permission)
        Status.Baseline -> res.getString(if (aodOn) R.string.aod_status_baseline_on else R.string.aod_status_baseline_off)
        // Die Bedienungshilfe ist an, der Abgleich steht aber noch aus.
        Status.Disabled -> res.getString(R.string.loading)
    }
}

/** Was fehlt - jede Zeile führt zur Lösung. */
@Composable
private fun AodSetup(
    context: Context,
    perms: AodPerms,
    needsBluetooth: Boolean,
    needsLocation: Boolean,
    askBluetooth: () -> Unit,
    askLocation: () -> Unit,
    grantSecure: () -> Unit,
) {
    val problem = MaterialTheme.colorScheme.error
    SettingsGroup(stringResource(R.string.setup_title)) {
        if (!perms.service) {
            SettingsRow(
                title = stringResource(R.string.accessibility),
                summary = stringResource(R.string.accessibility_off, stringResource(R.string.accessibility_service_label)),
                icon = R.drawable.ic_accessibility,
                iconTint = problem,
                onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } },
            )
        }
        if (!perms.secure) {
            SettingsRow(
                title = stringResource(R.string.adv_write_secure),
                summary = stringResource(R.string.perm_missing),
                icon = R.drawable.ic_key,
                iconTint = problem,
                onClick = grantSecure,
            )
        }
        if (needsBluetooth) {
            SettingsRow(
                title = stringResource(R.string.aod_bluetooth_title),
                summary = stringResource(R.string.aod_bluetooth_text),
                icon = R.drawable.ic_bluetooth,
                iconTint = problem,
                onClick = askBluetooth,
            )
        }
        if (needsLocation) {
            SettingsRow(
                title = stringResource(R.string.aod_location_title),
                summary = stringResource(R.string.aod_location_text),
                icon = R.drawable.ic_location,
                iconTint = problem,
                onClick = askLocation,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleSheet(
    rule: Rule?,
    bonded: List<Trigger.Bluetooth>,
    currentSsid: String?,
    perms: AodPerms,
    onRequestBluetooth: () -> Unit,
    onRequestLocation: () -> Unit,
    onSave: (Rule) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var isWifi by remember { mutableStateOf(rule?.trigger is Trigger.Wifi) }
    var device by remember { mutableStateOf(rule?.trigger as? Trigger.Bluetooth) }
    var ssid by remember { mutableStateOf((rule?.trigger as? Trigger.Wifi)?.ssid.orEmpty()) }
    var action by remember { mutableStateOf(rule?.action ?: AodAction.ON) }

    // Das Gerät der Regel zuerst: sichtbar ohne Scrollen und auch dann, wenn es entkoppelt wurde.
    val devices = remember(bonded, rule) {
        (listOfNotNull(rule?.trigger as? Trigger.Bluetooth) + bonded).distinctBy { it.key }
    }
    val trigger: Trigger? = if (isWifi) ssid.trim().takeIf(WifiSsid::isValid)?.let(Trigger::Wifi) else device

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(if (rule == null) R.string.aod_add_rule else R.string.aod_edit_rule),
                style = MaterialTheme.typography.headlineSmall,
            )
            Choice(
                labels = listOf(stringResource(R.string.aod_type_bluetooth), stringResource(R.string.aod_type_wifi)),
                selected = if (isWifi) 1 else 0,
                onSelect = { isWifi = it == 1 },
            )

            if (isWifi) {
                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    label = { Text(stringResource(R.string.aod_ssid_label)) },
                    singleLine = true,
                    isError = ssid.isNotEmpty() && !WifiSsid.isValid(ssid.trim()),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (perms.location) {
                    if (currentSsid != null && currentSsid != ssid) {
                        AssistChip(onClick = { ssid = currentSsid }, label = { Text(stringResource(R.string.aod_use_current_wifi, currentSsid)) })
                    }
                } else {
                    Text(stringResource(R.string.aod_location_text), style = MaterialTheme.typography.bodySmall)
                    FilledTonalButton(onClick = onRequestLocation) { Text(stringResource(R.string.aod_allow)) }
                }
            } else if (!perms.bluetooth) {
                Text(stringResource(R.string.aod_bluetooth_text), style = MaterialTheme.typography.bodySmall)
                FilledTonalButton(onClick = onRequestBluetooth) { Text(stringResource(R.string.aod_allow)) }
            } else if (devices.isEmpty()) {
                Text(stringResource(R.string.aod_no_bonded))
            } else {
                // Eigener Scrollbereich, damit eine lange Liste die Knöpfe nicht aus dem Bild schiebt.
                Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    devices.forEach { candidate ->
                        val selected = candidate.key == device?.key
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(selected, role = Role.RadioButton) { device = candidate }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Text(candidate.label, Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }

            Choice(
                labels = listOf(stringResource(R.string.aod_action_on), stringResource(R.string.aod_action_off)),
                selected = if (action == AodAction.ON) 0 else 1,
                onSelect = { action = if (it == 0) AodAction.ON else AodAction.OFF },
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (rule != null) {
                    TextButton(onClick = { onDelete(rule.id) }) { Text(stringResource(R.string.aod_delete)) }
                }
                Button(
                    enabled = trigger != null,
                    onClick = { onSave(Rule(rule?.id ?: UUID.randomUUID().toString(), trigger!!, action, rule?.enabled ?: true)) },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Choice(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selected,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index, labels.size),
            ) { Text(label) }
        }
    }
}

private fun openAppDetails(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
    runCatching { context.startActivity(intent) }
}
