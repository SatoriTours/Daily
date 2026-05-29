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
import androidx.compose.foundation.layout.widthIn
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
import com.dailysatori.ui.component.scaffold.AppScaffold
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

    AppScaffold(
        title = "我的日记",
        showBack = false,
        actions = {
            IconButton(onClick = { viewModel.toggleSearch() }) {
                Icon(Icons.Default.Search, contentDescription = "搜索", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { showTagFilter = true }) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = "筛选",
                    tint = if (state.selectedTag != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editingDiary = null; showEditor = true },
                modifier = Modifier.size(54.dp),
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface,
                shape = CircleShape,
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建日记", modifier = Modifier.size(30.dp))
            }
        },
    ) { scaffoldModifier ->
        Box(modifier = scaffoldModifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.m),
        ) {
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
                        contentPadding = PaddingValues(top = Spacing.s, bottom = Spacing.xxl),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        itemsIndexed(state.diaries, key = { _, diary -> diary.id }) { index, diary ->
                            val hasMonthHeader = index == 0 || diaryMonthKey(state.diaries[index - 1]) != diaryMonthKey(diary)
                            val dayDiaryCount = state.diaries.count { diaryDayKey(it) == diaryDayKey(diary) }
                            if (hasMonthHeader) {
                                DiaryMonthHeader(
                                    diaries = state.diaries.filter { diaryMonthKey(it) == diaryMonthKey(diary) },
                                    summary = state.monthSummaries[diaryMonthKey(diary)],
                                )
                            }
                            if (index == 0 || diaryDayKey(state.diaries[index - 1]) != diaryDayKey(diary)) {
                                DiaryDateHeader(diary = diary, dayDiaryCount = dayDiaryCount, hasMonthHeader = hasMonthHeader)
                            }
                            DiaryCard(
                                diary = diary,
                                onEdit = {
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
private fun DiaryMonthHeader(diaries: List<Diary>, summary: String?) {
    val firstDiary = diaries.firstOrNull()
    val monthTitle = firstDiary?.let(::diaryMonthTitle) ?: "${toChineseNumber(Calendar.getInstance().get(Calendar.MONTH) + 1)}月"
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs, bottom = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(monthTitle, style = MaterialTheme.typography.displayMedium.copy(fontSize = 30.sp, lineHeight = 30.sp), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        Text(
            text = summary?.takeIf { it.isNotBlank() } ?: diaryMonthSummary(diaries),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DiaryMonthMeta(diaries)
    }
}

@Composable
private fun DiaryMonthMeta(diaries: List<Diary>) {
    val imageCount = diaries.sumOf { diary ->
        diary.images?.split(",")?.count { it.trim().isNotBlank() && it.trim() != "null" } ?: 0
    }
    val latest = diaries.maxOfOrNull { it.updated_at ?: it.created_at }?.let { latestTime ->
        " · 最近更新 ${diaryMonthDayLabel(latestTime)}"
    }.orEmpty()
    val imageText = if (imageCount > 0) " · $imageCount 张照片" else ""
    Text(
        text = "${diaries.size} 篇日记$imageText$latest",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
    )
}

@Composable
private fun DiaryDateHeader(diary: Diary, dayDiaryCount: Int, hasMonthHeader: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = if (hasMonthHeader) Spacing.s else Spacing.m, bottom = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = diaryDateDayNumber(diary),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 27.sp, lineHeight = 27.sp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(text = diaryDateMonthLabel(diary), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                Text(text = diaryDateWeekLabel(diary), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
        }
        Box(modifier = Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)))
        Surface(shape = RoundedCornerShape(Radius.circular), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)) {
            Text(
                text = diaryDateCountLabel(diary, dayDiaryCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.widthIn(min = 42.dp).padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
    }
}

private fun diaryMonthTitle(diary: Diary): String {
    val calendar = Calendar.getInstance().apply { time = Date(diary.created_at) }
    return "${toChineseNumber(calendar.get(Calendar.MONTH) + 1)}月"
}

private fun diaryMonthKey(diary: Diary): String = SimpleDateFormat("yyyy-MM", Locale.CHINA).format(Date(diary.created_at))

private fun diaryDayKey(diary: Diary): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(diary.created_at))

private fun diaryMonthDayLabel(time: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = time }
    return "${calendar.get(Calendar.MONTH) + 1} 月 ${calendar.get(Calendar.DAY_OF_MONTH)} 日"
}

private fun diaryDateDayNumber(diary: Diary): String {
    val calendar = Calendar.getInstance().apply { time = Date(diary.created_at) }
    return calendar.get(Calendar.DAY_OF_MONTH).toString()
}

private fun diaryDateMonthLabel(diary: Diary): String {
    val calendar = Calendar.getInstance().apply { time = Date(diary.created_at) }
    return "${toChineseNumber(calendar.get(Calendar.MONTH) + 1)}月"
}

private fun diaryDateWeekLabel(diary: Diary): String {
    val calendar = Calendar.getInstance().apply { time = Date(diary.created_at) }
    val week = when (calendar.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "周一"
        Calendar.TUESDAY -> "周二"
        Calendar.WEDNESDAY -> "周三"
        Calendar.THURSDAY -> "周四"
        Calendar.FRIDAY -> "周五"
        Calendar.SATURDAY -> "周六"
        else -> "周日"
    }
    return week
}

private fun diaryDateCountLabel(diary: Diary, dayDiaryCount: Int): String {
    val relative = diaryRelativeDayLabel(diary)
    val count = "$dayDiaryCount 篇"
    return if (relative.isBlank()) count else "$relative · $count"
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
