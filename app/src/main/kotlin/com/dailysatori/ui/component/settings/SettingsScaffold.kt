package com.dailysatori.ui.component.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dailysatori.ui.component.scaffold.AppScaffold

/** Shared page surface and safe editor actions for settings, including nested editors. */
@Composable
fun SettingsScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    showBack: Boolean = onBack != null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    // Scope the grouped-page background to settings; reading pages keep their existing theme.
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(background = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        AppScaffold(
            title = title,
            onBack = onBack,
            showBack = showBack,
            actions = actions,
            snackbarHost = snackbarHost,
            floatingActionButton = floatingActionButton,
            bottomBar = {
                if (bottomBar != null) Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.navigationBarsPadding().imePadding()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        bottomBar()
                    }
                }
            },
            content = content,
        )
    }
}
