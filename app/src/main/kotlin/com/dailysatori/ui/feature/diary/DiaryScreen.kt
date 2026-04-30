package com.dailysatori.ui.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dailysatori.shared.db.Diary
import com.dailysatori.ui.component.appbar.AppTopBar
import com.dailysatori.ui.component.card.DiaryCard
import com.dailysatori.ui.component.dialog.ConfirmDialog
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun DiaryScreen() {
    val viewModel: DiaryViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    var showEditor by remember { mutableStateOf(false) }
    var editingDiary by remember { mutableStateOf<Diary?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Diary?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(
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

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (state.isLoading && state.diaries.isEmpty()) {
                    LoadingIndicator()
                } else if (state.diaries.isEmpty()) {
                    EmptyState(
                        modifier = Modifier.align(Alignment.Center),
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
                            DiaryCard(
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

        FloatingActionButton(
            onClick = { editingDiary = null; showEditor = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.m),
            containerColor = MaterialTheme.colorScheme.primary,
        ) {
            Icon(Icons.Default.Add, contentDescription = "新建日记")
        }
    }

    if (showEditor) {
        DiaryEditorSheet(
            existingDiary = editingDiary,
            onDismiss = { showEditor = false; editingDiary = null },
            onSave = { content, tags, mood, images ->
                val existingId = editingDiary?.id
                viewModel.saveDiary(
                    existingId = existingId,
                    content = content,
                    tags = tags,
                    mood = mood,
                    images = images,
                )
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
