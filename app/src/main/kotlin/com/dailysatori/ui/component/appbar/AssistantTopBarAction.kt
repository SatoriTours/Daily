package com.dailysatori.ui.component.appbar

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dailysatori.R
import com.dailysatori.ui.theme.Spacing

val LocalAssistantLauncher = staticCompositionLocalOf<(() -> Unit)?> { null }

/** App-level overlay entry; omitted inside the assistant itself and in standalone component previews. */
@Composable
fun AssistantTopBarAction() {
    val open = LocalAssistantLauncher.current ?: return
    IconButton(onClick = open, modifier = Modifier.size(Spacing.xxl)) {
        Icon(Icons.Default.AutoAwesome, stringResource(R.string.assistant_open), tint = MaterialTheme.colorScheme.primary)
    }
}
