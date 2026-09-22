package com.dailysatori.ui.feature.settings.reminder

import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dailysatori.R
import com.dailysatori.data.repository.ReminderProfile
import com.dailysatori.service.reminder.ReminderProfileKind
import com.dailysatori.ui.component.settings.SettingsScaffold as AppScaffold
import com.dailysatori.ui.theme.*
import org.koin.androidx.compose.koinViewModel

@Composable
fun ReminderProfileManagementScreen(onBack: () -> Unit, viewModel: ReminderSettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    BackHandler(onBack = onBack)
    AppScaffold(title = stringResource(R.string.reminder_profiles_section), onBack = onBack) { modifier ->
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            state.profiles.forEach { ProfileCard(it, viewModel) }
            Button(onClick = { viewModel.editProfile() }, modifier = Modifier.fillMaxWidth().heightIn(min = Height.button)) { Text(stringResource(R.string.reminder_new_custom_profile)) }
        }
    }
    state.editor?.let { ProfileEditorDialog(it, state, viewModel) }
}

@Composable
private fun ProfileCard(profile: ReminderProfile, viewModel: ReminderSettingsViewModel) {
    val displayName = profile.localizedName()
    Card(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.m), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(displayName, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.reminder_profile_summary, displayName, profile.snapshot.daytimeDismissalBackoffMinutes.joinToString(" / ")))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                TextButton(onClick = { viewModel.editProfile(profile, duplicate = true, displayName = displayName) }) { Text(stringResource(R.string.reminder_action_duplicate)) }
                if (profile.kind == ReminderProfileKind.CUSTOM) {
                    TextButton(onClick = { viewModel.editProfile(profile) }) { Text(stringResource(R.string.reminder_action_edit)) }
                    TextButton(onClick = { viewModel.deleteProfile(profile) }) { Text(stringResource(R.string.reminder_action_delete)) }
                }
            }
        }
    }
}
