package com.dailysatori.ui.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.core.util.TimeUtils
import com.dailysatori.shared.db.Diary
import com.dailysatori.ui.component.appbar.AppTopBar
import com.dailysatori.ui.component.dialog.ConfirmDialog
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun DiaryScreen() {
    val viewModel: DiaryViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    var showEditor by remember { mutableStateOf(false) }
    var editingDiary by remember { mutableStateOf<Diary?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Diary?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editingDiary = null; showEditor = true },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建日记")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AppTopBar(
                title = "我的日记",
                showBack = false,
                actions = {
                    IconButton(onClick = { viewModel.toggleSearch() }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "筛选")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            state.tags.forEach { tag ->
                                DropdownMenuItem(
                                    text = { Text(tag.name ?: "") },
                                    onClick = { viewModel.filterByTag(tag.id); showMenu = false },
                                )
                            }
                        }
                    }
                },
            )

            if (state.isSearchVisible) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.search(it) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.xs),
                    placeholder = { Text("搜索日记...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                )
            }

            if (state.isLoading && state.diaries.isEmpty()) {
                LoadingIndicator()
            } else if (state.diaries.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Edit,
                    title = "暂无日记",
                    subtitle = "点击右下角 + 开始写日记",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    items(state.diaries, key = { it.id }) { diary ->
                        DiaryCardItem(
                            diary = diary,
                            onClick = {
                                editingDiary = diary
                                showEditor = true
                            },
                            onDelete = { showDeleteDialog = diary },
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        DiaryEditorSheet(
            existingDiary = editingDiary,
            onDismiss = { showEditor = false; editingDiary = null },
            onSave = { content, tags, mood ->
                if (editingDiary != null) {
                    viewModel.saveDiary(existingId = editingDiary!!.id, content = content, tags = tags, mood = mood)
                } else {
                    viewModel.saveDiary(content = content, tags = tags, mood = mood)
                }
                showEditor = false
                editingDiary = null
            },
        )
    }

    showDeleteDialog?.let { diary ->
        ConfirmDialog(
            title = "删除日记",
            message = "确定要删除这篇日记吗？",
            onConfirm = {
                viewModel.deleteDiary(diary.id)
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null },
        )
    }
}

@Composable
fun DiaryCardItem(diary: Diary, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.m),
    ) {
        Column(modifier = Modifier.padding(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = TimeUtils.formatDateTime(diary.created_at),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                val mood = diary.mood
                if (mood != null) {
                    Text(mood, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = diary.content ?: "",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            val tags = diary.tags
            if (!tags.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    tags.split(",").take(5).forEach { tag ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(tag.trim(), style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(28.dp),
                        )
                    }
                }
            }
        }
    }
}
