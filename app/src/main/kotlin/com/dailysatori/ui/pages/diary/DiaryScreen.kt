package com.dailysatori.ui.pages.diary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.dailysatori.shared.db.Diary
import com.dailysatori.ui.components.*
import com.dailysatori.ui.theme.*
import com.dailysatori.viewmodel.DiaryViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen() {
    val viewModel: DiaryViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    var showEditor by remember { mutableStateOf(false) }
    var editingDiary by remember { mutableStateOf<Diary?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Diary?>(null) }

    Scaffold(
        topBar = {
            SAppBar(
                title = "我的日记",
                showBack = false,
                actions = {
                    IconButton(onClick = { viewModel.toggleSearch() }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
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
        },
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
                    text = formatTime(diary.created_at),
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

private fun formatTime(epochMs: Long): String {
    val instant = java.time.Instant.ofEpochMilli(epochMs)
    val localDate = java.time.LocalDate.ofInstant(instant, java.time.ZoneId.systemDefault())
    return "${localDate.year}-${localDate.monthValue.toString().padStart(2, '0')}-${localDate.dayOfMonth.toString().padStart(2, '0')} ${String.format("%02d:%02d", localDate.atStartOfDay(java.time.ZoneId.systemDefault()).hour, localDate.atStartOfDay(java.time.ZoneId.systemDefault()).minute)}"
}
