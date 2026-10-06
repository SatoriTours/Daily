package com.dailysatori.ui.feature.diary

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.dailysatori.R
import com.dailysatori.ui.feature.profile.localDayTicker
import kotlinx.datetime.Clock
import kotlinx.datetime.todayIn
import com.dailysatori.shared.db.Diary
import com.dailysatori.ui.component.card.DiaryCard
import com.dailysatori.ui.component.dialog.ConfirmDialog
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.component.input.SearchBar
import com.dailysatori.ui.theme.*
import androidx.core.content.ContextCompat
import com.dailysatori.ui.component.scaffold.AppScaffold
import org.koin.androidx.compose.koinViewModel
import com.dailysatori.core.recording.DiaryRecordingState
import com.dailysatori.core.recording.DiaryRecordingNotification
import com.dailysatori.core.recording.DiaryRecordingOpenRequest
import java.util.TimeZone

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(onMyClick: () -> Unit = {}) {
    val tagViewModel: DiaryTagViewModel = koinViewModel()
    val tagState by tagViewModel.state.collectAsState()
    val i18n: com.dailysatori.service.i18n.I18nService = org.koin.compose.koinInject()
    var showTagSettings by remember { mutableStateOf(false) }
    val viewModel: DiaryViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val today by remember { localDayTicker() }.collectAsState(initial = Clock.System.todayIn(kotlinx.datetime.TimeZone.currentSystemDefault()))
    val nowMillis = remember(today, TimeZone.getDefault().id) { System.currentTimeMillis() }
    val requestedDiaryId by DiaryRecordingOpenRequest.diaryId.collectAsState()
    var showEditor by remember { mutableStateOf(false) }
    var editingDiary by remember { mutableStateOf<Diary?>(null) }
    var editingTag by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(showEditor, editingDiary?.id) {
        tagViewModel.observeEditor(editingDiary?.id.takeIf { showEditor })
    }
    var showDeleteDialog by remember { mutableStateOf<Diary?>(null) }
    var showTagFilter by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val recordingController = remember(context) { DiaryRecordingController(context) }
    var showNotificationSettingsAction by remember { mutableStateOf(false) }
    val startVoiceDiary: () -> Unit = {
        viewModel.setError(null)
        viewModel.createVoiceDiary { diaryId, attachmentId ->
            recordingController.start(diaryId, attachmentId)
        }
    }
    val startVoiceDiaryIfNotificationsVisible: () -> Unit = {
        if (DiaryRecordingNotification.canShow(context)) {
            showNotificationSettingsAction = false
            startVoiceDiary()
        } else {
            showNotificationSettingsAction = true
            viewModel.setError("录音通知已关闭，无法在后台或锁屏时提示录音状态")
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val microphoneGranted = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            grants[Manifest.permission.POST_NOTIFICATIONS] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (microphoneGranted && notificationGranted) startVoiceDiaryIfNotificationsVisible()
        else viewModel.setError("需要麦克风和通知权限才能开始语音日记")
    }
    val requestVoicePermissions: () -> Unit = {
        val missingPermissions = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (missingPermissions.isEmpty()) startVoiceDiaryIfNotificationsVisible()
        else permissionLauncher.launch(missingPermissions.toTypedArray())
    }
    val diaryListState = rememberLazyListState()
    val newestDiaryId = state.diaries.firstOrNull()?.id
    val timeZoneId = TimeZone.getDefault().id
    val feedEntries = remember(state.diaries, timeZoneId) { buildDiaryFeedEntries(state.diaries) }
    var recordingWasActive by remember { mutableStateOf(false) }
    LaunchedEffect(newestDiaryId) {
        if (newestDiaryId != null) diaryListState.animateScrollToItem(0)
    }
    LaunchedEffect(state.recordingState) {
        val recordingIsActive = state.recordingState !is DiaryRecordingState.Idle
        if (recordingWasActive && state.recordingState is DiaryRecordingState.Idle && newestDiaryId != null) {
            diaryListState.animateScrollToItem(0)
        }
        recordingWasActive = recordingIsActive
    }
    LaunchedEffect(requestedDiaryId, state.diaries) {
        val diaryId = requestedDiaryId ?: return@LaunchedEffect
        val requestedDiary = state.diaries.firstOrNull { it.id == diaryId }
            ?: viewModel.getDiaryById(diaryId)
        if (requestedDiary == null) {
            DiaryRecordingOpenRequest.consume(diaryId)
            return@LaunchedEffect
        }
        editingDiary = requestedDiary
        editingTag = null
        showEditor = true
        DiaryRecordingOpenRequest.consume(diaryId)
    }

    AppScaffold(
        title = stringResource(R.string.diary_feed_title),
        showBack = false,
        isMainPage = true,
        actions = {
            IconButton(onClick = { viewModel.toggleSearch() }) {
                Icon(Icons.Default.Search, contentDescription = "搜索", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(IconSize.m))
            }
            IconButton(onClick = { showTagFilter = true }) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = "筛选",
                    tint = if (state.selectedTag != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.m),
                )
            }
        },
    ) { scaffoldModifier ->
        Column(
            modifier = scaffoldModifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = Spacing.m),
        ) {
            if (state.recordingState !is DiaryRecordingState.Idle) {
                DiaryRecordingControls(
                    state = state.recordingState,
                    onPauseResume = { recordingController.pauseResume(state.recordingState is DiaryRecordingState.Paused) },
                    onStop = recordingController::stop,
                    onOpenDiary = {
                        state.recordingState.diaryId?.let { id ->
                            editingDiary = state.diaries.firstOrNull { it.id == id }
                            editingTag = null
                            showEditor = editingDiary != null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.error?.let { error ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f).padding(vertical = Spacing.xs),
                    )
                    if (showNotificationSettingsAction) {
                        TextButton(onClick = { DiaryRecordingNotification.openSettings(context) }) {
                            Text("打开通知设置")
                        }
                    }
                }
            }
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

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = diaryListState,
                contentPadding = PaddingValues(top = Spacing.s, bottom = Height.navBar + Spacing.xxl + Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                item(key = "capture") {
                    DiaryWelcomePanel(
                        onText = { editingDiary = null; showEditor = true },
                        onVoice = requestVoicePermissions,
                        voiceEnabled = state.recordingState is DiaryRecordingState.Idle,
                    )
                }
                if (state.isLoading && state.diaries.isEmpty()) {
                    item(key = "loading") { LoadingIndicator() }
                } else if (state.diaries.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Default.Edit,
                            title = stringResource(R.string.diary_feed_empty_title),
                            subtitle = stringResource(R.string.diary_feed_empty_hint),
                        )
                    }
                }
                feedEntries.forEach { entry ->
                    if (entry.showMonthHeader && state.searchQuery.isBlank() && state.selectedTag == null) {
                        item(key = "month-${entry.monthKey}") {
                            DiaryMonthHeader(
                                diaries = checkNotNull(entry.monthDiaries),
                                summary = state.monthSummaries[entry.monthKey],
                            )
                        }
                    }
                    item(key = entry.diary.id) {
                        val diary = entry.diary
                        DiaryCard(
                            diary = diary.copy(tags = com.dailysatori.service.diary.parseDiaryTags(diary.tags)
                                .map(tagState.vocabulary::canonical).distinct().joinToString(",")),
                            nowMillis = nowMillis,
                            attachments = state.attachmentsByDiary[diary.id].orEmpty(),
                            onEdit = {
                                editingTag = null
                                editingDiary = diary
                                showEditor = true
                            },
                            onDelete = { showDeleteDialog = diary },
                            onRetryTranscription = viewModel::retryTranscription,
                            onTagClick = { tag -> editingDiary = diary; editingTag = tag; showEditor = true },
                        )
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
                onManage = { showTagFilter = false; showTagSettings = true },
            )
        }
    }

    if (showTagSettings) {
        ModalBottomSheet(onDismissRequest = { showTagSettings = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                Text(i18n.t("diary_tags.title"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { showTagSettings = false }) { Text(i18n.t("diary_tags.done")) }
            }
            DiaryTagSettingsContent(tagViewModel)
        }
    }

    if (showEditor) {
        BackHandler {
            showEditor = false
            editingDiary = null
            editingTag = null
        }
        DiaryEditorSheet(
            existingDiary = editingDiary,
            tagVocabulary = tagState.vocabulary.copy(names = tagState.vocabulary.names.sortedByDescending { tagState.counts[it] ?: 0 }),
            initialTagState = tagState.provenance[editingDiary?.id],
            latestTags = tagState.editorTags[editingDiary?.id],
            onGenerateTags = tagViewModel::generate,
            tagStatus = tagState.taskStatuses[editingDiary?.id],
            initialTagToEdit = editingTag,
            recordingState = state.recordingState.takeIf {
                editingDiary?.id == state.recordingState.diaryId && it !is DiaryRecordingState.Idle
            },
            onPauseResumeRecording = {
                recordingController.pauseResume(state.recordingState is DiaryRecordingState.Paused)
            },
            onStopRecording = recordingController::stop,
            attachments = state.attachmentsByDiary[editingDiary?.id ?: -1L].orEmpty(),
            onDeleteAttachment = viewModel::deleteAttachment,
            onRetryTranscription = viewModel::retryTranscription,
            onOpenTranscriptionSettings = onMyClick,
            onDismiss = { showEditor = false; editingDiary = null; editingTag = null },
            onSave = { content, tags, mood, images, tagDraft ->
                val existingId = editingDiary?.id
                viewModel.saveDiary(
                    existingId = existingId,
                    content = content,
                    tags = tags,
                    mood = mood,
                    images = images,
                    tagDraft = tagDraft,
                )
                showEditor = false
                editingDiary = null
                editingTag = null
            },
        )
    }

    showDeleteDialog?.let { diary ->
        ConfirmDialog(
            title = "删除日记",
            message = "确定要删除这篇日记吗？",
            onConfirm = {
                viewModel.deleteDiary(diary.id, recordingController::stop)
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null },
        )
    }
}

@Composable
private fun ActiveDiaryTagFilterChip(tag: String, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
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
                IconButton(onClick = onClear, modifier = Modifier.size(IconSize.l)) {
                    Icon(Icons.Default.Close, contentDescription = "清除筛选", modifier = Modifier.size(IconSize.xs))
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
    onManage: () -> Unit,
) {
    val i18n: com.dailysatori.service.i18n.I18nService = org.koin.compose.koinInject()
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

        TextButton(onClick = onManage) { Text(i18n.t("diary_tags.title")) }
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
