package com.dailysatori.ui.component.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.theme.*
import org.koin.compose.koinInject

@Composable
fun SettingsSearchEmptyState(modifier: Modifier = Modifier) {
    val i18n: I18nService = koinInject()
    Column(modifier.fillMaxSize().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Search, null, Modifier.size(IconSize.xl), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(i18n.t("settings_design.no_results"), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
