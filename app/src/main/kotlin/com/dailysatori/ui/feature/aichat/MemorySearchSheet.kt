package com.dailysatori.ui.feature.aichat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.MemoryRepository
import com.dailysatori.service.memory.MemoryExtractService
import com.dailysatori.shared.db.Memory_entry
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorySearchSheet(onDismiss: () -> Unit) {
    val memoryRepo = koinInject<MemoryRepository>()
    val extractService = koinInject<MemoryExtractService>()
    val articleRepo = koinInject<ArticleRepository>()
    val diaryRepo = koinInject<DiaryRepository>()
    val bookRepo = koinInject<BookRepository>()
    val viewpointRepo = koinInject<BookViewpointRepository>()
    var searchQuery by remember { mutableStateOf("") }
    var memories by remember { mutableStateOf<List<Memory_entry>>(emptyList()) }
    var isRebuilding by remember { mutableStateOf(false) }
    var rebuildProgress by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(searchQuery) {
        withContext(Dispatchers.IO) {
            memories = if (searchQuery.isBlank()) {
                memoryRepo.getAllSync()
            } else {
                memoryRepo.search(searchQuery, 50)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.m)) {
            Text("记忆搜索", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(Spacing.s))

            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索记忆...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(Radius.m),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )

            if (isRebuilding && rebuildProgress.isNotBlank()) {
                Spacer(modifier = Modifier.height(Spacing.s))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(rebuildProgress, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(Spacing.s))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${memories.size} 条记忆",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        isRebuilding = true
                        scope.launch(Dispatchers.IO) {
                            extractService.rebuildAll(
                                articleRepo, diaryRepo, bookRepo, viewpointRepo,
                                onProgress = { rebuildProgress = it },
                            )
                            memories = memoryRepo.getAllSync()
                            isRebuilding = false
                        }
                    },
                    enabled = !isRebuilding,
                ) {
                    Text("重建全部记忆")
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(400.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                contentPadding = PaddingValues(vertical = Spacing.s),
            ) {
                items(memories, key = { it.id }) { memory ->
                    MemoryEntryCard(memory)
                }
            }

            Spacer(modifier = Modifier.height(Spacing.m))
        }
    }
}

@Composable
private fun MemoryEntryCard(memory: Memory_entry) {
    Surface(
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemoryTypeChip(memory.type)
                Spacer(modifier = Modifier.width(Spacing.s))
                Text(
                    memory.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                memory.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MemoryTypeChip(type: String) {
    val (label, color) = when (type) {
        "core" -> "核心" to MaterialTheme.colorScheme.primary
        "content" -> "内容" to MaterialTheme.colorScheme.secondary
        "chat" -> "对话" to MaterialTheme.colorScheme.tertiary
        else -> type to MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = RoundedCornerShape(Radius.xs),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
