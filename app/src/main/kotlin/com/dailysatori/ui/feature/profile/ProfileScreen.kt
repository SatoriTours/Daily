package com.dailysatori.ui.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dailysatori.R
import com.dailysatori.ui.component.settings.SettingsRow
import com.dailysatori.ui.theme.*

@Composable
internal fun ProfileLibrarySection(
    state: ProfileUiState,
    onFavorites: () -> Unit,
    onTasks: () -> Unit,
    onFailedTasks: () -> Unit,
) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surface) {
        Column {
            Text(stringResource(R.string.personal_settings_library), Modifier.padding(Spacing.m), style = MaterialTheme.typography.titleMedium)
            SettingsRow(Icons.Outlined.BookmarkBorder, stringResource(R.string.personal_settings_favorites),
                stringResource(R.string.management_count, state.favoriteCount), onFavorites)
            val progress = state.taskProgressLabel?.let { " · $it" }.orEmpty()
            SettingsRow(Icons.Outlined.TaskAlt, stringResource(R.string.personal_settings_tasks),
                stringResource(R.string.management_task_status, state.activeTaskCount, state.failedTaskCount) + progress, onTasks)
            if (state.failedTaskCount > 0) {
                TextButton(onClick = onFailedTasks, modifier = Modifier.padding(horizontal = Spacing.m)) {
                    Text(stringResource(R.string.management_failed_tasks), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
