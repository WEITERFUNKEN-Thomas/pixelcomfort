package com.noirdraco.pixelcomfort.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noirdraco.pixelcomfort.R
import kotlinx.coroutines.launch

/** Anzeigegroesse und Schriftgroesse - frueher auf der Startseite, jetzt eine eigene Seite. */
@Composable
internal fun DisplayScreen(refreshKey: Int, onRefresh: () -> Unit, onBack: () -> Unit) {
    val res = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    SettingsScaffold(title = stringResource(R.string.display_title), onBack = onBack, snackbar = snackbar) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PagePadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScaleGroup(
                refreshKey = refreshKey,
                onResult = {
                    if (!it.success) {
                        // Kommt vom Worker-Thread.
                        scope.launch {
                            snackbar.currentSnackbarData?.dismiss()
                            snackbar.showSnackbar(failureText(res, it.message))
                        }
                    }
                    onRefresh()
                },
            )
            GroupFooter(stringResource(R.string.display_footer))
            Spacer(Modifier.height(24.dp))
        }
    }
}
