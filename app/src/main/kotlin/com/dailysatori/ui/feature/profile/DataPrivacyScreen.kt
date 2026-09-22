package com.dailysatori.ui.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dailysatori.R
import com.dailysatori.ui.component.settings.SettingsScaffold
import com.dailysatori.ui.component.settings.SettingsSectionCard
import com.dailysatori.ui.theme.*

@Composable
fun DataPrivacyScreen(onBack: () -> Unit) {
    SettingsScaffold(title = stringResource(R.string.management_privacy), onBack = onBack) { modifier ->
        Column(
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            PrivacySection(R.string.management_storage, R.string.management_storage_hint)
            PrivacySection(R.string.management_external_data, R.string.management_external_data_hint)
            PrivacySection(R.string.management_data, R.string.management_backup_hint)
        }
    }
}

@Composable
private fun PrivacySection(title: Int, body: Int) {
    SettingsSectionCard(stringResource(title)) {
        Text(stringResource(body), Modifier.padding(Spacing.m), style = MaterialTheme.typography.bodyMedium)
    }
}
