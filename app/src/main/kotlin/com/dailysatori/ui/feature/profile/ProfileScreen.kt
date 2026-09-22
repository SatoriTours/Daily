package com.dailysatori.ui.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Task
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.R
import com.dailysatori.ui.component.settings.SettingsRow
import com.dailysatori.ui.component.settings.SettingsScaffold
import com.dailysatori.ui.component.settings.SettingsSectionCard
import com.dailysatori.ui.theme.*
import org.koin.androidx.compose.koinViewModel

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onFavorites: () -> Unit,
    onExternalFavorites: () -> Unit,
    onRemoteNews: () -> Unit,
    onTasks: () -> Unit,
    onFailedTasks: () -> Unit,
    onSettings: () -> Unit,
    onPrivacy: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScaffold(title = stringResource(R.string.my_space_settings), onBack = onBack) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            item { SettingsSectionCard(stringResource(R.string.management_content)) {
                SettingsRow(Icons.Default.Bookmark, stringResource(R.string.management_favorites), stringResource(R.string.management_count, state.favoriteCount), onFavorites)
                SettingsRow(Icons.Default.CloudSync, stringResource(R.string.management_external), stringResource(R.string.management_external_count, state.externalFavoriteCount, state.enabledExternalSourceCount), onExternalFavorites)
                SettingsRow(Icons.Default.CloudSync, stringResource(R.string.management_news),
                    if (state.enabledRemoteNewsSourceCount == 0L) stringResource(R.string.management_no_sources)
                    else stringResource(R.string.management_news_count, state.remoteNewsArticleCount, state.enabledRemoteNewsSourceCount), onRemoteNews)
            } }
            item { SettingsSectionCard(stringResource(R.string.management_running)) {
                val progress = state.taskProgressLabel?.let { " · $it" }.orEmpty()
                SettingsRow(Icons.Default.Task, stringResource(R.string.management_tasks), stringResource(R.string.management_task_status, state.activeTaskCount, state.failedTaskCount) + progress, onTasks)
                if (state.failedTaskCount > 0) TextButton(onClick = onFailedTasks) {
                    Text(stringResource(R.string.management_failed_tasks), color = MaterialTheme.colorScheme.error)
                }
            } }
            item { SettingsSectionCard(stringResource(R.string.management_app)) {
                SettingsRow(Icons.Default.Settings, stringResource(R.string.management_general), stringResource(R.string.management_general_hint), onSettings)
                SettingsRow(Icons.Default.DataUsage, stringResource(R.string.management_privacy), stringResource(R.string.management_privacy_hint), onPrivacy)
            } }
        }
    }
}
