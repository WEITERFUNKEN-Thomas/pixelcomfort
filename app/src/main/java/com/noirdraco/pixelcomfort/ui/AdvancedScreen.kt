package com.noirdraco.pixelcomfort.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import com.noirdraco.pixelcomfort.Prefs
import com.noirdraco.pixelcomfort.R
import com.noirdraco.pixelcomfort.SetupStatus
import com.noirdraco.pixelcomfort.ShizukuShell
import kotlinx.coroutines.launch
import kotlin.concurrent.thread

/** Alles Technische: welche Einstellung geschaltet wird, Berechtigungen, Rohwert. */
@Composable
internal fun AdvancedScreen(refreshKey: Int, onRefresh: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val res = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var namespace by rememberSaveable { mutableStateOf(Prefs.getNamespace(context)) }
    var key by rememberSaveable { mutableStateOf(Prefs.getKey(context)) }
    var valueOff by rememberSaveable { mutableStateOf(Prefs.getOff(context)) }
    var valueOn by rememberSaveable { mutableStateOf(Prefs.getOn(context)) }
    var status by remember { mutableStateOf<SetupStatus?>(null) }

    LaunchedEffect(refreshKey) {
        thread { status = SetupStatus.read(context.applicationContext) }
    }

    // Darf von jedem Thread aus aufgerufen werden.
    fun say(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    fun grant() = grantViaShizuku(context, res, ::say, onRefresh)

    SettingsScaffold(title = stringResource(R.string.advanced), onBack = onBack, snackbar = snackbar) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PagePadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val s = status

            SettingsGroup(stringResource(R.string.adv_setting)) {
                SettingsItem {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOf(Prefs.NS_SYSTEM, Prefs.NS_SECURE, Prefs.NS_GLOBAL).forEach { ns ->
                            FilterChip(
                                selected = namespace == ns,
                                onClick = { namespace = ns },
                                label = { Text(ns) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text(stringResource(R.string.adv_key)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = valueOff,
                            onValueChange = { valueOff = it },
                            label = { Text(stringResource(R.string.adv_value_off)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = valueOn,
                            onValueChange = { valueOn = it },
                            label = { Text(stringResource(R.string.adv_value_on)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Button(
                        modifier = Modifier.align(Alignment.End),
                        onClick = {
                            Prefs.saveConfig(
                                context,
                                namespace.trim(),
                                key.trim(),
                                valueOff.trim(),
                                valueOn.trim(),
                                Prefs.getPackagesRaw(context),
                            )
                            say(res.getString(R.string.saved))
                            onRefresh()
                        },
                    ) { Text(stringResource(R.string.save)) }
                }
            }
            GroupFooter(stringResource(R.string.adv_setting_footer))

            SettingsGroup(stringResource(R.string.adv_permissions)) {
                SettingsRow(
                    title = stringResource(R.string.adv_write_settings),
                    summary = permissionSummary(res, "WRITE_SETTINGS", s?.canWriteSystem),
                    icon = if (s?.canWriteSystem == true) R.drawable.ic_check_circle else R.drawable.ic_key,
                    iconTint = tint(s?.canWriteSystem),
                    onClick = { grant() },
                )
                SettingsRow(
                    title = stringResource(R.string.adv_write_secure),
                    summary = permissionSummary(res, "WRITE_SECURE_SETTINGS", s?.hasWriteSecure),
                    icon = if (s?.hasWriteSecure == true) R.drawable.ic_check_circle else R.drawable.ic_key,
                    iconTint = tint(s?.hasWriteSecure),
                    onClick = { grant() },
                )
                SettingsRow(
                    title = stringResource(R.string.adv_system_dialog),
                    summary = stringResource(R.string.adv_system_dialog_summary),
                    icon = R.drawable.ic_tune,
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            "package:${context.packageName}".toUri(),
                        )
                        runCatching { context.startActivity(intent) }
                    },
                )
            }
            GroupFooter(stringResource(R.string.adv_permissions_footer))

            SettingsGroup(stringResource(R.string.adv_diagnostics)) {
                SettingsRow(
                    title = stringResource(R.string.adv_current_value),
                    summary = "${Prefs.getNamespace(context)}/${Prefs.getKey(context)} = ${s?.currentValue ?: "–"}",
                    onClick = onRefresh,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun permissionSummary(res: Resources, name: String, granted: Boolean?): String = when (granted) {
    null -> name
    true -> res.getString(R.string.perm_granted, name)
    false -> res.getString(R.string.perm_missing)
}

@Composable
private fun tint(granted: Boolean?) =
    if (granted == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

/**
 * WRITE_SECURE_SETTINGS und den WRITE_SETTINGS-AppOp einmalig per Shizuku erteilen.
 * Beide bleiben danach auch ohne Shizuku erhalten. Laeuft auf einem eigenen Thread.
 *
 * Zwei getrennte Aufrufe statt einer Shell-Zeile: sonst zaehlte nur der Exit-Code des
 * letzten Kommandos, ein gescheitertes `pm grant` fiele nicht auf. Erfolg wird zum
 * Schluss am echten Rechte-Stand geprueft.
 */
internal fun grantViaShizuku(context: Context, res: Resources, say: (String) -> Unit, onRefresh: () -> Unit) {
    val pkg = context.packageName
    thread {
        val msg = try {
            if (!ShizukuShell.isReady()) {
                failureText(res, ShizukuShell.explain("Shizuku not ready"))
            } else {
                val errors = listOf(
                    ShizukuShell.run("pm", "grant", pkg, Manifest.permission.WRITE_SECURE_SETTINGS),
                    ShizukuShell.run("appops", "set", pkg, "WRITE_SETTINGS", "allow"),
                ).filterNot { it.success }.map { it.err.ifEmpty { "exit=${it.exitCode}" } }
                val granted = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
                    PackageManager.PERMISSION_GRANTED && Settings.System.canWrite(context)
                when {
                    errors.isNotEmpty() -> res.getString(R.string.failed, errors.joinToString("; "))
                    !granted -> res.getString(R.string.failed, "permissions not granted after pm grant/appops set")
                    else -> res.getString(R.string.perms_granted)
                }
            }
        } catch (t: Throwable) {
            res.getString(R.string.error_generic, t.message.orEmpty())
        }
        say(msg)
        onRefresh()
    }
}
