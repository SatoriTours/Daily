package com.dailysatori.ui.feature.diary

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.dailysatori.core.util.diaryMonthKey
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.dailysatori.ui.component.scaffold.AppScaffold
import org.koin.androidx.compose.koinViewModel
import com.dailysatori.core.recording.DiaryRecordingState
import com.dailysatori.core.recording.DiaryRecordingNotification
import com.dailysatori.core.recording.DiaryRecordingOpenRequest
import java.util.TimeZone

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(onMyClick: () -> Unit = {}, onDetailVisibilityChange: (Boolean) -> Unit = {}) {
    val viewModel: DiaryViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    var reviewMonthKey by rememberSaveable { mutableStateOf<String?>(null) }
    val today by remember { localDayTicker() }.collectAsState(initial = Clock.System.todayIn(kotlinx.datetime.TimeZone.currentSystemDefault()))
    val nowMillis = remember(today, TimeZone.getDefault().id) { System.currentTimeMillis() }
    DisposableEffect(reviewMonthKey != null) {
        onDetailVisibilityChange(reviewMonthKey != null)
        onDispose { onDetailVisibilityChange(false) }
    }
    val requestedDiaryId by DiaryRecordingOpenRequest.diaryId.collectAsState()
    var showEditor by remember { mutableStateOf(false) }
    var editingDiary by remember { mutableStateOf<Diary?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Diary?>(null) }
    var showTagFilter by remember { mutableStateOf(false) }
    var showCaptureMenu by remember { mutableStateOf(false) }
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
    val showAddDiaryButton by remember { derivedStateOf { !diaryListState.isScrollInProgress } }
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
        reviewMonthKey = null
        showEditor = true
        DiaryRecordingOpenRequest.consume(diaryId)
    }

    if (reviewMonthKey != null) {
        DiaryMonthReviewScreen(
            monthKey = checkNotNull(reviewMonthKey),
            diaries = state.diaries.filter { diaryMonthKey(it) == reviewMonthKey },
            summary = state.monthSummaries[reviewMonthKey], attachments = state.attachmentsByDiary, nowMillis = nowMillis,
            onBack = { reviewMonthKey = null }, onEdit = { editingDiary = it; showEditor = true },
            onDelete = { showDeleteDialog = it }, onRetryTranscription = viewModel::retryTranscription,
            onOpenTranscriptionSettings = onMyClick,
        )
    } else AppScaffold(
        title = "我的日记",
        showBack = false,
        isMainPage = true,
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
            AnimatedVisibility(
                visible = showAddDiaryButton,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                modifier = Modifier.padding(bottom = Height.navBar + Spacing.xl + Spacing.xs).size(IconSize.xxl),
            ) {
                MiniAddDiaryButton(
                    menuExpanded = showCaptureMenu,
                    onToggleMenu = { showCaptureMenu = !showCaptureMenu },
                    onDismissMenu = { showCaptureMenu = false },
                    onVoice = requestVoicePermissions,
                    onText = { editingDiary = null; showEditor = true },
                    onCapture = {},
                    onFile = {},
                )
            }
        },
    ) { scaffoldModifier ->
        Box(modifier = scaffoldModifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                        state = diaryListState,
                        contentPadding = PaddingValues(top = Spacing.s, bottom = Height.navBar + Spacing.xxl + Spacing.m),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        items(feedEntries, key = { it.diary.id }) { entry ->
                            val diary = entry.diary
                            if (entry.showMonthHeader && state.searchQuery.isBlank() && state.selectedTag == null) {
                                DiaryMonthHeader(
                                    diaries = checkNotNull(entry.monthDiaries),
                                    summary = state.monthSummaries[entry.monthKey],
                                    onReview = { reviewMonthKey = entry.monthKey },
                                )
                            }
                            DiaryCard(
                                diary = diary,
                                nowMillis = nowMillis,
                                attachments = state.attachmentsByDiary[diary.id].orEmpty(),
                                onEdit = {
                                    editingDiary = diary
                                    showEditor = true
                                },
                                onDelete = { showDeleteDialog = diary },
                                onRetryTranscription = viewModel::retryTranscription,
                                onOpenTranscriptionSettings = onMyClick,
                            )
                        }
                    }
                }
            }
        }
        }
    }

    BackHandler(enabled = showCaptureMenu) { showCaptureMenu = false }

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
                viewModel.deleteDiary(diary.id, recordingController::stop)
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null },
        )
    }
}

@Composable
private fun MiniAddDiaryButton(
    menuExpanded: Boolean,
    onToggleMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onVoice: () -> Unit,
    onText: () -> Unit,
    onCapture: () -> Unit,
    onFile: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onToggleMenu),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 6.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, contentDescription = "新建日记", modifier = Modifier.size(21.dp))
            }
        }
        DiaryCaptureMenu(
            expanded = menuExpanded,
            onDismissRequest = onDismissMenu,
            onVoice = onVoice,
            onText = onText,
            onCapture = onCapture,
            onFile = onFile,
        )
    }
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
