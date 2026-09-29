package com.noirdraco.pixelcomfort

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.noirdraco.pixelcomfort.ui.AdvancedScreen
import com.noirdraco.pixelcomfort.ui.AppsScreen
import com.noirdraco.pixelcomfort.ui.DnsScreen
import com.noirdraco.pixelcomfort.ui.HistoryScreen
import com.noirdraco.pixelcomfort.ui.HomeScreen
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
                // Fuenf Seiten - dafuer braucht es keine Navigations-Bibliothek.
                var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
                val onRefresh: () -> Unit = { refresh.intValue++ }
                val toHome: () -> Unit = {
                    screen = Screen.HOME
                    // Auswahl/Konfiguration kann sich geaendert haben.
                    onRefresh()
                }
                BackHandler(enabled = screen != Screen.HOME, onBack = toHome)

                when (screen) {
                    Screen.HOME -> HomeScreen(
                        refreshKey = refresh.intValue,
                        onRefresh = onRefresh,
                        onOpenApps = { screen = Screen.APPS },
                        onOpenAdvanced = { screen = Screen.ADVANCED },
                        onOpenHistory = { screen = Screen.HISTORY },
                        onOpenDns = { screen = Screen.DNS },
                    )

                    Screen.HISTORY -> HistoryScreen(onBack = toHome)

                    Screen.APPS -> AppsScreen(onBack = toHome)

                    Screen.DNS -> DnsScreen(
                        refreshKey = refresh.intValue,
                        onRefresh = onRefresh,
                        onBack = toHome,
                    )

                    Screen.ADVANCED -> AdvancedScreen(
                        refreshKey = refresh.intValue,
                        onRefresh = onRefresh,
                        onBack = toHome,
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

    private enum class Screen { HOME, APPS, DNS, HISTORY, ADVANCED }

    companion object {
        const val SHIZUKU_REQUEST_CODE = 1001
    }
}
