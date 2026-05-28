package com.dailysatori.ui.feature.diary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.dailysatori.shared.db.Diary
import com.dailysatori.ui.component.card.DiaryCard
import com.dailysatori.ui.component.dialog.ConfirmDialog
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.component.input.SearchBar
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(onMyClick: () -> Unit = {}) {
    val viewModel: DiaryViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    var showEditor by remember { mutableStateOf(false) }
    var editingDiary by remember { mutableStateOf<Diary?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Diary?>(null) }
    var showTagFilter by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.m)) {
            DiaryTopBar(
                onSearchClick = { viewModel.toggleSearch() },
                onFilterClick = { showTagFilter = true },
                isFilterActive = state.selectedTag != null,
            )

            if (state.isSearchVisible) {
                SearchBar(
                    query = state.searchQuery,
                    onQueryChange = { viewModel.search(it) },
                    onSearch = { viewModel.search(it) },
                    onClose = { viewModel.toggleSearch() },
                )
            }

            state.selectedTag?.let { selectedTag ->
                ActiveDiaryTagFilterChip(
                    tag = selectedTag,
                    onClear = { viewModel.filterByTag(null) },
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
                        contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.xxl),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        itemsIndexed(state.diaries, key = { _, diary -> diary.id }) { index, diary ->
                            if (index == 0 || diaryMonthKey(state.diaries[index - 1]) != diaryMonthKey(diary)) {
                                DiaryMonthHeader(state.diaries.filter { diaryMonthKey(it) == diaryMonthKey(diary) })
                            }
                            if (index == 0 || diaryDayKey(state.diaries[index - 1]) != diaryDayKey(diary)) {
                                DiaryDateHeader(diary)
                            }
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
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 22.dp).size(54.dp),
            containerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.surface,
            shape = CircleShape,
        ) {
            Icon(Icons.Default.Add, contentDescription = "新建日记", modifier = Modifier.size(30.dp))
        }
    }

    if (showTagFilter) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showTagFilter = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
        ) {
            DiaryTagFilterSheet(
                tags = state.availableTags,
                selectedTag = state.selectedTag,
                onTagSelected = { tag ->
                    viewModel.filterByTag(tag)
                    showTagFilter = false
                },
                onClear = { viewModel.filterByTag(null) },
                onClose = { showTagFilter = false },
            )
        }
    }

    if (showEditor) {
        BackHandler {
            showEditor = false
            editingDiary = null
        }
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

@Composable
private fun DiaryTopBar(
    onSearchClick: () -> Unit,
    onFilterClick: () -> Unit,
    isFilterActive: Boolean,
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(54.dp).padding(top = 2.dp, bottom = Spacing.m),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "我的日记",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.ExtraBold,
        )
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DiaryTopIconButton(onClick = onSearchClick) {
                Icon(Icons.Default.Search, contentDescription = "搜索", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            DiaryTopIconButton(onClick = onFilterClick) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = "筛选",
                    tint = if (isFilterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Box(modifier = Modifier.align(Alignment.CenterStart).width(70.dp))
    }
}

@Composable
private fun DiaryTopIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(34.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun DiaryMonthHeader(diaries: List<Diary>) {
    val firstDiary = diaries.firstOrNull()
    val monthTitle = firstDiary?.let(::diaryMonthTitle) ?: "${toChineseNumber(Calendar.getInstance().get(Calendar.MONTH) + 1)}月"
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs, bottom = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(monthTitle, style = MaterialTheme.typography.displayMedium.copy(fontSize = 30.sp, lineHeight = 30.sp), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        Text(
            text = diaryMonthSummary(diaries),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DiaryQuickStrip(diaries)
    }
}

@Composable
private fun DiaryQuickStrip(diaries: List<Diary>) {
    val mood = diaries.firstNotNullOfOrNull { it.mood?.takeIf { value -> value.isNotBlank() && value != "null" } }
    val tag = diaries.firstNotNullOfOrNull { diary ->
        diary.tags?.split(",")?.firstOrNull { it.trim().isNotBlank() && it.trim() != "null" }?.trim()
    }
    val pills = listOfNotNull("全部", "有照片".takeIf { diaries.any { !it.images.isNullOrBlank() && it.images != "null" } }, mood, tag?.let { "#$it" })
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        items(pills, key = { it }) { label -> DiaryQuickPill(label) }
    }
}

@Composable
private fun DiaryQuickPill(label: String) {
    Surface(shape = RoundedCornerShape(Radius.circular), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f), tonalElevation = 0.dp) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun DiaryDateHeader(diary: Diary) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.s, bottom = Spacing.xxs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = diaryDateTitle(diary),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = diaryRelativeDayLabel(diary),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun diaryMonthTitle(diary: Diary): String {
    val calendar = Calendar.getInstance().apply { time = Date(diary.created_at) }
    return "${toChineseNumber(calendar.get(Calendar.MONTH) + 1)}月"
}

private fun diaryMonthKey(diary: Diary): String = SimpleDateFormat("yyyy-MM", Locale.CHINA).format(Date(diary.created_at))

private fun diaryDayKey(diary: Diary): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(diary.created_at))

private fun diaryDateTitle(diary: Diary): String {
    val calendar = Calendar.getInstance().apply { time = Date(diary.created_at) }
    val month = toChineseNumber(calendar.get(Calendar.MONTH) + 1)
    val day = toChineseNumber(calendar.get(Calendar.DAY_OF_MONTH))
    val week = when (calendar.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "周一"
        Calendar.TUESDAY -> "周二"
        Calendar.WEDNESDAY -> "周三"
        Calendar.THURSDAY -> "周四"
        Calendar.FRIDAY -> "周五"
        Calendar.SATURDAY -> "周六"
        else -> "周日"
    }
    return "${month}月${day}日 · $week"
}

private fun diaryRelativeDayLabel(diary: Diary): String {
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(calendar.time)
    calendar.add(Calendar.DAY_OF_YEAR, -1)
    val beforeYesterday = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(calendar.time)
    return when (diaryDayKey(diary)) {
        today -> "今天"
        yesterday -> "昨天"
        beforeYesterday -> "前天"
        else -> ""
    }
}

private fun toChineseNumber(value: Int): String {
    val units = listOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十")
    return when (value) {
        in 0..10 -> units[value]
        in 11..19 -> "十${units[value % 10]}"
        in 20..99 -> "${units[value / 10]}十${if (value % 10 == 0) "" else units[value % 10]}"
        else -> value.toString()
    }
}

private fun diaryMonthSummary(diaries: List<Diary>): String {
    if (diaries.isEmpty()) return emptyMonthSentence()
    val tags = diaries.flatMap { diary ->
        diary.tags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() && it != "null" }.orEmpty()
    }.distinct().take(3)
    val tagText = tags.takeIf { it.isNotEmpty() }?.joinToString("、") ?: "一些普通但明亮的片刻"
    return "这个月的你把 $tagText 留了下来。照片负责记住画面，文字负责留下当时的心。"
}

private fun emptyMonthSentence(): String {
    val sentences = listOf(
        "这个月还没有留下文字。没关系，生活不是每天都要存档，偶尔只负责发光也很好。",
        "空白不是缺席，它只是给下一段故事留了点位置。",
        "这个月的纸页还很干净，等风、等光，也等你忽然想写的那一刻。",
    )
    val index = Calendar.getInstance().get(Calendar.MONTH) % sentences.size
    return sentences[index]
}

@Composable
private fun ActiveDiaryTagFilterChip(tag: String, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.m, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.circular),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Row(
                modifier = Modifier.padding(start = Spacing.s, end = Spacing.xxs, top = Spacing.xxs, bottom = Spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(
                    text = "#$tag",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "清除筛选", modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiaryTagFilterSheet(
    tags: List<String>,
    selectedTag: String?,
    onTagSelected: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.m, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text("按标签筛选", style = MaterialTheme.typography.titleMedium)
                Text("选择一个标签，只看相关日记", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onClose) { Text("关闭") }
        }

        if (selectedTag != null) {
            TextButton(onClick = onClear) { Text("清除当前筛选") }
        }

        if (tags.isEmpty()) {
            Text(
                text = "暂无标签",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.s),
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                tags.forEach { tag ->
                    FilterChip(
                        selected = selectedTag == tag,
                        onClick = { onTagSelected(tag) },
                        label = { Text("#$tag") },
                    )
                }
            }
        }
    }
}
