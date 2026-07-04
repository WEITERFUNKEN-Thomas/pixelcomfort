package com.noirdraco.pixelcomfort

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirdraco.pixelcomfort.ui.theme.PixelComfortTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    // Wird erhoeht, um die Statusanzeige neu berechnen zu lassen.
    private val refresh = mutableIntStateOf(0)

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> refresh.intValue++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Throwable) {
        }
        enableEdgeToEdge()
        setContent {
            PixelComfortTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SetupScreen(
                        modifier = Modifier.padding(innerPadding),
                        refreshKey = refresh.intValue,
                        onRefresh = { refresh.intValue++ },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh.intValue++
    }

    override fun onDestroy() {
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }

    companion object {
        const val SHIZUKU_REQUEST_CODE = 1001
    }
}

@Composable
private fun SetupScreen(
    modifier: Modifier = Modifier,
    refreshKey: Int,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current

    var namespace by remember { mutableStateOf(Prefs.getNamespace(context)) }
    var key by remember { mutableStateOf(Prefs.getKey(context)) }
    var valueOff by remember { mutableStateOf(Prefs.getOff(context)) }
    var valueOn by remember { mutableStateOf(Prefs.getOn(context)) }
    var packages by remember { mutableStateOf(Prefs.getPackagesRaw(context)) }
    var lastAction by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<SetupStatus?>(null) }

    LaunchedEffect(refreshKey) {
        Thread { status = readStatus(context) }.start()
    }

    fun bg(block: () -> String) {
        Thread {
            val msg = try {
                block()
            } catch (t: Throwable) {
                "Fehler: ${t.message}"
            }
            lastAction = msg
            onRefresh()
        }.start()
    }

    fun saveConfig() {
        Prefs.saveConfig(context, namespace.trim(), key.trim(), valueOff.trim(), valueOn.trim(), packages.trim())
        lastAction = "Konfiguration gespeichert."
        onRefresh()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("PixelComfort", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Schaltet den Augenkomfort-Modus automatisch aus, wenn Kamera/Fotos geoeffnet sind, und stellt danach genau den vorherigen System-Wert wieder her.",
            style = MaterialTheme.typography.bodyMedium,
        )

        // ---- Augenkomfort-Modus: aktueller Zustand ----
        ComfortStatusCard(comfortOn = status?.comfortOn, loaded = status != null)

        // ---- Status ----
        SectionCard("Status") {
            val s = status
            if (s == null) {
                Text("… wird geladen", style = MaterialTheme.typography.bodySmall)
            } else {
                StatusRow("Shizuku verbunden", s.shizukuBinder)
                StatusRow("Shizuku-Freigabe erteilt", s.shizukuPermission)
                StatusRow("WRITE_SETTINGS (Settings.System)", s.canWriteSystem)
                StatusRow("WRITE_SECURE_SETTINGS", s.hasWriteSecure)
                StatusRow("Bedienungshilfe aktiv", s.accessibilityEnabled)
                if (s.advancedProtection == true) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "⚠ Erweiterter Schutz (Advanced Protection) ist aktiv. Android 17 deaktiviert dann Bedienungshilfen, die keine echten Barrierefreiheits-Tools sind – die Automatik funktioniert erst wieder, wenn du den erweiterten Schutz ausschaltest.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    "Aktueller System-Wert ${namespace}/${key} = ${s.currentValue ?: "–"}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            OutlinedButton(onClick = onRefresh) { Text("Status aktualisieren") }
        }

        // ---- Schritt 1: Shizuku & Rechte ----
        SectionCard("1. Berechtigungen einrichten") {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    lastAction = try {
                        Shizuku.requestPermission(MainActivity.SHIZUKU_REQUEST_CODE)
                        "Shizuku-Freigabe angefragt – im Shizuku-Dialog bestaetigen."
                    } catch (t: Throwable) {
                        "Shizuku nicht bereit: ${t.message}. Ist die Shizuku-App gestartet?"
                    }
                },
            ) { Text("Shizuku-Freigabe anfragen") }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val pkg = context.packageName
                    bg {
                        if (!ShizukuShell.isReady()) {
                            "Shizuku ist nicht bereit/freigegeben. Zuerst oben die Shizuku-Freigabe erteilen."
                        } else {
                            val res = ShizukuShell.run(
                                "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS; " +
                                    "appops set $pkg WRITE_SETTINGS allow; echo OK",
                            )
                            "pm grant / appops (exit=${res.exitCode})\n${res.out}${if (res.err.isNotEmpty()) "\n${res.err}" else ""}"
                        }
                    }
                },
            ) { Text("WRITE_SETTINGS + WRITE_SECURE_SETTINGS per Shizuku erteilen") }

            Text(
                "Hinweis: cv_enabled ist ein System-Key, den Fremd-Apps nicht direkt schreiben duerfen – das Umschalten laeuft daher automatisch ueber Shizuku. Shizuku muss dafuer nach jedem Neustart gestartet sein.",
                style = MaterialTheme.typography.bodySmall,
            )

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    )
                    runCatching { context.startActivity(intent) }
                },
            ) { Text("WRITE_SETTINGS manuell erlauben (Systemdialog)") }
        }

        // ---- Schritt 2: Bedienungshilfe ----
        SectionCard("2. Bedienungshilfe aktivieren") {
            Text(
                "Aktiviere „PixelComfort Auto-Aus“ unter Einstellungen › Bedienungshilfen.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                },
            ) { Text("Bedienungshilfen oeffnen") }
        }

        // ---- Schritt 3: Ziel-Apps ----
        SectionCard("3. Ziel-Apps") {
            OutlinedTextField(
                value = packages,
                onValueChange = { packages = it },
                label = { Text("Ziel-Packages (kommagetrennt)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { saveConfig() },
            ) { Text("Speichern") }
        }

        // ---- Schritt 4: Erweitert ----
        SectionCard("4. Erweitert (Settings-Key)") {
            Text(
                "Nur aendern, falls der Augenkomfort-Modus auf deinem Geraet anders heisst. Der gemerkte Wert (AN) stammt immer live aus dem System – „Wert AN“ dient nur dem Test-Button.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Namespace", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Prefs.NS_SECURE, Prefs.NS_SYSTEM, Prefs.NS_GLOBAL).forEach { ns ->
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
                label = { Text("Settings-Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = valueOff,
                    onValueChange = { valueOff = it },
                    label = { Text("Wert AUS") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = valueOn,
                    onValueChange = { valueOn = it },
                    label = { Text("Wert AN (nur Test)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { saveConfig() },
            ) { Text("Konfiguration speichern") }
        }

        // ---- Test ----
        SectionCard("5. Test") {
            Text(
                "Schreibt sofort den jeweiligen Wert (ContentResolver, sonst Shizuku).",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        saveConfig()
                        bg {
                            val r = SettingWriter.write(context, namespace.trim(), key.trim(), valueOn.trim())
                            "Comfort AN: ok=${r.success} via ${r.method}\n${r.message}"
                        }
                    },
                ) { Text("Comfort AN") }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        saveConfig()
                        bg {
                            val r = SettingWriter.write(context, namespace.trim(), key.trim(), valueOff.trim())
                            "Comfort AUS: ok=${r.success} via ${r.method}\n${r.message}"
                        }
                    },
                ) { Text("Comfort AUS") }
            }
        }

        if (lastAction.isNotEmpty()) {
            SectionCard("Letzte Aktion") {
                Text(
                    lastAction,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** Grosse, gut sichtbare Anzeige, ob der Augenkomfort-Modus gerade an oder aus ist. */
@Composable
private fun ComfortStatusCard(comfortOn: Boolean?, loaded: Boolean) {
    val onColor = Color(0xFFF0A000)   // warmes Amber = Filter aktiv
    val offColor = Color(0xFF3DA35D)  // gruen = aus (normale Farben)
    val dotColor = when (comfortOn) {
        true -> onColor
        false -> offColor
        null -> MaterialTheme.colorScheme.outline
    }
    val label = when {
        !loaded -> "wird geladen …"
        comfortOn == true -> "EINGESCHALTET"
        comfortOn == false -> "AUSGESCHALTET"
        else -> "unbekannt"
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(dotColor, CircleShape),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Augenkomfort-Modus", style = MaterialTheme.typography.labelLarge)
                Text(
                    label,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = dotColor,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            if (ok) "✓" else "✗",
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
        )
    }
}

private data class SetupStatus(
    val shizukuBinder: Boolean,
    val shizukuPermission: Boolean,
    val canWriteSystem: Boolean,
    val hasWriteSecure: Boolean,
    val accessibilityEnabled: Boolean,
    val advancedProtection: Boolean?,
    val currentValue: String?,
    val comfortOn: Boolean?,
)

private fun readStatus(context: android.content.Context): SetupStatus {
    val canWriteSystem = runCatching { Settings.System.canWrite(context) }.getOrDefault(false)
    val hasWriteSecure = context.checkSelfPermission(
        android.Manifest.permission.WRITE_SECURE_SETTINGS,
    ) == PackageManager.PERMISSION_GRANTED
    val current = SettingWriter.read(context, Prefs.getNamespace(context), Prefs.getKey(context))
    // AUS = exakt der konfigurierte Aus-Wert; alles andere (und der Live-Wert) = AN.
    val comfortOn: Boolean? = when (current) {
        null -> null
        Prefs.getOff(context) -> false
        else -> true
    }
    return SetupStatus(
        shizukuBinder = ShizukuShell.isBinderAlive(),
        shizukuPermission = ShizukuShell.hasPermission(),
        canWriteSystem = canWriteSystem,
        hasWriteSecure = hasWriteSecure,
        accessibilityEnabled = ComfortAccessibilityService.isEnabled(context),
        advancedProtection = readAdvancedProtection(context),
        currentValue = current,
        comfortOn = comfortOn,
    )
}

/**
 * Erweiterten Schutz (Advanced Protection Mode) erkennen. Nur informativ; wenn die
 * API auf dem Geraet nicht zugaenglich ist, wird null zurueckgegeben.
 */
private fun readAdvancedProtection(context: android.content.Context): Boolean? {
    return try {
        val mgr = context.getSystemService("advanced_protection") ?: return null
        val m = mgr.javaClass.getMethod("isAdvancedProtectionEnabled")
        m.invoke(mgr) as? Boolean
    } catch (_: Throwable) {
        null
    }
}
