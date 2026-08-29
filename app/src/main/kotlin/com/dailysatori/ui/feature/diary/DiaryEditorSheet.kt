package com.dailysatori.ui.feature.diary

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.dailysatori.core.recording.DiaryRecordingState
import com.dailysatori.service.diary.DiaryAssistantFallbackRequiredException
import com.dailysatori.service.diary.DiaryAssistantService
import com.dailysatori.shared.db.Diary
import com.dailysatori.shared.db.Diary_attachment
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private data class DiaryEditorColors(
    val sheet: androidx.compose.ui.graphics.Color,
    val text: androidx.compose.ui.graphics.Color,
    val muted: androidx.compose.ui.graphics.Color,
    val line: androidx.compose.ui.graphics.Color,
    val chip: androidx.compose.ui.graphics.Color,
    val primary: androidx.compose.ui.graphics.Color,
    val primarySoft: androidx.compose.ui.graphics.Color,
)

@Composable
private fun diaryEditorColors(): DiaryEditorColors = DiaryEditorColors(
    sheet = MaterialTheme.colorScheme.surface,
    text = MaterialTheme.colorScheme.onSurface,
    muted = MaterialTheme.colorScheme.onSurfaceVariant,
    line = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
    chip = MaterialTheme.colorScheme.surfaceContainer,
    primary = MaterialTheme.colorScheme.primary,
    primarySoft = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
)

private fun sanitizeNull(value: String?): String {
    if (value == null || value == "null") return ""
    return value
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiaryEditorSheet(
    onDismiss: () -> Unit,
    onSave: (content: String, tags: String?, mood: String?, images: String?) -> Unit,
    existingDiary: Diary? = null,
    recordingState: DiaryRecordingState? = null,
    onPauseResumeRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    attachments: List<Diary_attachment> = emptyList(),
    onDeleteAttachment: (Long) -> Unit = {},
    onRetryTranscription: (Long) -> Unit = {},
    onOpenTranscriptionSettings: () -> Unit = {},
    assistantService: DiaryAssistantService = koinInject(),
) {
    val context = LocalContext.current
    val editorColors = diaryEditorColors()

    var content by remember(existingDiary) {
        mutableStateOf(TextFieldValue(existingDiary?.content ?: ""))
    }
    val editorScrollState = rememberScrollState()
    var showMediaPicker by remember { mutableStateOf(false) }
    var showTagEditor by remember { mutableStateOf(false) }
    var showTagEntry by remember { mutableStateOf(false) }
    var showMoodEditor by remember { mutableStateOf(false) }
    var showMoreFormats by remember { mutableStateOf(false) }
    var attachmentToDelete by remember { mutableStateOf<Diary_attachment?>(null) }
    var tagsText by remember(existingDiary) { mutableStateOf(sanitizeNull(existingDiary?.tags)) }
    var moodText by remember(existingDiary) { mutableStateOf(sanitizeNull(existingDiary?.mood)) }
    val undoStack = remember { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember { mutableStateListOf<TextFieldValue>() }
    val assistantCache = remember { DiaryAssistantSessionCache() }
    val assistantRequestGate = remember { DiaryAssistantRequestGate() }
    val assistantScope = rememberCoroutineScope()
    var assistantJob by remember { mutableStateOf<Job?>(null) }
    var pendingPastedUrl by remember { mutableStateOf<String?>(null) }
    var assistantPreview by remember { mutableStateOf<DiaryAssistantPreviewState?>(null) }
    val images = remember(existingDiary) {
        val existingImages = existingDiary?.images
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() && it != "null" }
            ?: emptyList()
        mutableStateListOf<String>().apply { addAll(existingImages) }
    }

    LaunchedEffect(content.text, content.selection) {
        if (content.selection.collapsed && content.selection.end == content.text.length) {
            withFrameNanos { }
            editorScrollState.scrollTo(editorScrollState.maxValue)
        }
    }

    val canUseAssistant = !content.selection.collapsed &&
        content.text.substring(content.selection.min, content.selection.max).isNotBlank()

    fun pushUndo() {
        if (undoStack.size >= 50) undoStack.removeAt(0)
        undoStack.add(content)
        redoStack.clear()
    }

    fun assistantSnapshot(): DiaryAssistantSelectionSnapshot {
        val selection = TextRange(content.selection.min, content.selection.max)
        return DiaryAssistantSelectionSnapshot(
            text = content.text,
            selection = selection,
            selectedText = content.text.substring(selection.start, selection.end),
        )
    }

    fun cancelAssistantPreview() {
        assistantRequestGate.invalidate()
        assistantJob?.cancel()
        assistantJob = null
        assistantPreview = null
    }

    fun startAssistant(
        snapshot: DiaryAssistantSelectionSnapshot,
        url: String? = null,
        allowModelKnowledgeFallback: Boolean = false,
    ) {
        assistantRequestGate.invalidate()
        assistantJob?.cancel()
        assistantJob = null
        val requestGeneration = assistantRequestGate.begin()
        assistantPreview = DiaryAssistantPreviewState.Loading(snapshot, url, allowModelKnowledgeFallback)
        val nextJob = assistantScope.launch(start = CoroutineStart.LAZY) {
            val runningJob = coroutineContext[Job]
            try {
                val cached = url?.let { assistantCache[it] }
                val result = cached ?: assistantService.run(
                    snapshot.toDiaryAssistantRequest(url, allowModelKnowledgeFallback),
                )
                if (!assistantRequestGate.isCurrent(requestGeneration) || assistantJob !== runningJob) return@launch
                if (url != null && cached == null) assistantCache[url] = result
                assistantPreview = DiaryAssistantPreviewState.Ready(
                    snapshot, url, allowModelKnowledgeFallback, result, result.content,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: DiaryAssistantFallbackRequiredException) {
                if (assistantRequestGate.isCurrent(requestGeneration) && assistantJob === runningJob) {
                    assistantPreview = DiaryAssistantPreviewState.FallbackConsent(snapshot, url)
                }
            } catch (error: Exception) {
                if (assistantRequestGate.isCurrent(requestGeneration) && assistantJob === runningJob) {
                    assistantPreview = DiaryAssistantPreviewState.Failure(
                        snapshot = snapshot,
                        url = url,
                        allowModelKnowledgeFallback = allowModelKnowledgeFallback,
                        message = error.message ?: "生成失败，请稍后重试",
                    )
                }
            } finally {
                if (assistantJob === runningJob) assistantJob = null
            }
        }
        assistantJob = nextJob
        nextJob.start()
    }

    fun insertAssistantPreview() {
        val ready = assistantPreview as? DiaryAssistantPreviewState.Ready ?: return
        val updated = insertDiaryAssistantResult(content, ready.snapshot, ready.draft)
        pushUndo()
        content = updated
        cancelAssistantPreview()
    }

    fun replaceAssistantPreview() {
        val ready = assistantPreview as? DiaryAssistantPreviewState.Ready ?: return
        if (!canReplaceDiaryAssistantSelection(content, ready.snapshot)) return
        val updated = replaceDiaryAssistantSelection(content, ready.snapshot, ready.draft)
        pushUndo()
        content = updated
        cancelAssistantPreview()
    }

    DisposableEffect(Unit) {
        onDispose {
            assistantRequestGate.invalidate()
            assistantJob?.cancel()
            assistantJob = null
            assistantCache.clear()
        }
    }

    fun performUndo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(content)
            content = undoStack.removeAt(undoStack.lastIndex)
        }
    }

    fun performRedo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(content)
            content = redoStack.removeAt(redoStack.lastIndex)
        }
    }

    fun insertFormat(prefix: String, suffix: String = "") {
        pushUndo()
        val text = content.text
        val sel = content.selection
        val selected = text.substring(sel.start, sel.end)
        val replacement = prefix + selected + suffix
        val newText = text.substring(0, sel.start) + replacement + text.substring(sel.end)
        val cursorPos = sel.start + replacement.length
        content = content.copy(text = newText, selection = TextRange(cursorPos))
    }

    fun insertLineStart(prefix: String) {
        pushUndo()
        val text = content.text
        val cursorPos = content.selection.start
        val lineStart = text.lastIndexOf('\n', cursorPos - 1) + 1
        val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
        val newCursor = cursorPos + prefix.length
        content = content.copy(text = newText, selection = TextRange(newCursor))
    }

    fun insertBlock(block: String) {
        pushUndo()
        val text = content.text
        val prefix = when {
            text.isBlank() -> ""
            text.endsWith("\n\n") -> ""
            text.endsWith("\n") -> "\n"
            else -> "\n\n"
        }
        val inserted = "$prefix$block\n"
        content = content.copy(
            text = text + inserted,
            selection = TextRange(text.length + inserted.length),
        )
    }

    fun saveMedia(uri: Uri, ext: String) {
        try {
            val mimeType = context.contentResolver.getType(uri)
            val actualExt = when {
                mimeType?.startsWith("video/") == true -> ".mp4"
                mimeType?.startsWith("image/") == true -> ".${ext.ifBlank { "jpg" }}"
                ext.isNotBlank() -> ext
                else -> ".jpg"
            }
            val fileName = "diary_${UUID.randomUUID()}$actualExt"
            val diaryImagesDir = File(context.filesDir, "DailySatori/diary_images").apply { mkdirs() }
            val destFile = File(diaryImagesDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            images.add("diary_images/$fileName")
        } catch (_: Exception) { }
    }

    val tempPhotoUri = remember {
        val dir = File(context.filesDir, "DailySatori/diary_images").apply { mkdirs() }
        val file = File(dir, "temp_photo_${UUID.randomUUID()}.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success -> if (success) saveMedia(tempPhotoUri, ".jpg") }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris: List<Uri> -> uris.forEach { uri -> saveMedia(uri, "") } }

    if (showMediaPicker) {
        AlertDialog(
            onDismissRequest = { showMediaPicker = false },
            shape = RoundedCornerShape(Radius.xl),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            iconContentColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("添加媒体") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    MediaPickerButton("拍照") {
                        showMediaPicker = false; cameraLauncher.launch(tempPhotoUri)
                    }
                    MediaPickerButton("从相册选择") {
                        showMediaPicker = false; galleryLauncher.launch("image/*")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMediaPicker = false }) { Text("取消") }
            },
        )
    }

    if (showTagEditor) {
        DiaryTextEditDialog(
            title = "编辑标签",
            value = tagsText,
            placeholder = "用逗号分隔，例如：生活,散步",
            onValueChange = { tagsText = it },
            onDismiss = { showTagEditor = false },
        )
    }

    if (showMoodEditor) {
        DiaryTextEditDialog(
            title = "编辑心情",
            value = moodText,
            placeholder = "例如：平静",
            onValueChange = { moodText = it },
            onDismiss = { showMoodEditor = false },
        )
    }

    attachmentToDelete?.let { attachment ->
        AlertDialog(
            onDismissRequest = { attachmentToDelete = null },
            title = { Text(if (attachment.kind == "audio") "删除录音" else "删除附件") },
            text = { Text("删除后无法恢复，确定继续吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAttachment(attachment.id)
                        attachmentToDelete = null
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { attachmentToDelete = null }) { Text("取消") }
            },
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.36f))
                .padding(horizontal = Spacing.s),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .padding(bottom = Spacing.s),
                shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l),
                color = editorColors.sheet,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.m, vertical = Spacing.s),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismiss) { Text("取消", color = editorColors.primary) }
                        DiaryEditorMetaRow(
                            dateText = diaryEditorDateText(existingDiary),
                            mood = moodText,
                            colors = editorColors,
                            onMood = { showMoodEditor = true },
                        )
                        TextButton(
                            enabled = content.text.isNotBlank(),
                            onClick = { onSave(content.text, tagsText.ifBlank { null }, moodText.ifBlank { null }, images.joinToString(",").ifBlank { null }) },
                        ) {
                            Text(
                                "保存",
                                color = if (content.text.isNotBlank()) editorColors.primary else editorColors.muted.copy(alpha = 0.44f),
                            )
                        }
                    }
                    recordingState?.let {
                        DiaryRecordingControls(
                            state = it,
                            onPauseResume = onPauseResumeRecording,
                            onStop = onStopRecording,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    DiaryAttachmentList(
                        attachments = attachments,
                        onDelete = { attachmentToDelete = it },
                        onRetryTranscription = onRetryTranscription,
                        onOpenTranscriptionSettings = onOpenTranscriptionSettings,
                    )
                    Spacer(modifier = Modifier.height(Spacing.s))
                    DiaryEditorTagRow(
                        tagsText = tagsText,
                        showAddEntry = showTagEntry,
                        colors = editorColors,
                        onAddTag = { showTagEditor = true },
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    DiaryAssistantEditorViewport {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = Spacing.s),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(editorScrollState)
                                    .imeNestedScroll(),
                                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                            ) {
                                DiaryImageRow(images = images, onRemove = { images.remove(it) })
                                pendingPastedUrl?.let { url ->
                                    DiaryPastedUrlPrompt(
                                        url = url,
                                        onExtractConfirmed = {
                                            pendingPastedUrl = null
                                            startAssistant(assistantSnapshot(), url)
                                        },
                                        onDismiss = { pendingPastedUrl = null },
                                    )
                                }
                                BasicTextField(
                                    value = content,
                                    onValueChange = { updated ->
                                        val pastedUrl = detectNewlyPastedDiaryUrl(content.text, updated.text)
                                        pushUndo()
                                        content = updated
                                        if (pastedUrl != null) pendingPastedUrl = pastedUrl
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 260.dp),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = editorColors.text),
                                    cursorBrush = SolidColor(editorColors.primary),
                                    decorationBox = { innerTextField ->
                                        if (content.text.isEmpty()) {
                                            Text("写点东西...", style = MaterialTheme.typography.bodyMedium, color = editorColors.muted.copy(alpha = 0.62f))
                                        }
                                        innerTextField()
                                    },
                                )
                            }
                        }

                        assistantPreview?.let { preview ->
                            DiaryAssistantPreviewSheet(
                                state = preview,
                                canReplaceSelection = canReplaceDiaryAssistantSelection(content, preview.snapshot),
                                onDraftChange = { draft ->
                                    val ready = assistantPreview as? DiaryAssistantPreviewState.Ready
                                    if (ready != null) assistantPreview = ready.copy(draft = draft)
                                },
                                onRetry = { startAssistant(preview.snapshot, preview.url, preview.allowFallbackForRetry()) },
                                onAllowFallback = { startAssistant(preview.snapshot, preview.url, true) },
                                onCancel = { cancelAssistantPreview() },
                                onInsert = { insertAssistantPreview() },
                                onReplace = { replaceAssistantPreview() },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Box(modifier = Modifier.navigationBarsPadding().imePadding()) {
                        DiaryEditorToolbar(
                            onTitle = { insertLineStart("# ") },
                            onOrderedList = { insertLineStart("1. ") },
                            onUnorderedList = { insertLineStart("- ") },
                            onMedia = { showMediaPicker = true },
                            onTags = { showTagEntry = true },
                            onMood = { showMoodEditor = true },
                            onAssistant = { startAssistant(assistantSnapshot()) },
                            onUndo = { performUndo() },
                            onRedo = { performRedo() },
                            onMore = { showMoreFormats = !showMoreFormats },
                            canUndo = undoStack.isNotEmpty(),
                            canRedo = redoStack.isNotEmpty(),
                            canUseAssistant = canUseAssistant,
                        )
                        DiaryMoreFormatMenu(
                            expanded = showMoreFormats,
                            onDismiss = { showMoreFormats = false },
                            onBold = { insertFormat("**", "**"); showMoreFormats = false },
                            onItalic = { insertFormat("*", "*"); showMoreFormats = false },
                            onQuote = { insertLineStart("> "); showMoreFormats = false },
                            onTaskList = { insertLineStart("- [ ] "); showMoreFormats = false },
                            onDivider = { insertBlock("---"); showMoreFormats = false },
                            onLink = { insertFormat("[", "](url)"); showMoreFormats = false },
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.s))
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.DiaryAssistantEditorViewport(
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        content = content,
    )
}

private fun DiaryAssistantPreviewState.allowFallbackForRetry(): Boolean = when (this) {
    is DiaryAssistantPreviewState.Loading -> allowModelKnowledgeFallback
    is DiaryAssistantPreviewState.Failure -> allowModelKnowledgeFallback
    is DiaryAssistantPreviewState.Ready -> allowModelKnowledgeFallback
    is DiaryAssistantPreviewState.FallbackConsent -> false
}

@Composable
private fun DiaryPastedUrlPrompt(
    url: String,
    onExtractConfirmed: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = url,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onDismiss) { Text("忽略") }
            TextButton(onClick = onExtractConfirmed) { Text("提取核心内容") }
        }
    }
}

@Composable
private fun DiaryEditorMetaRow(
    dateText: String,
    mood: String,
    colors: DiaryEditorColors,
    onMood: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.CalendarToday,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = colors.muted,
            )
            Text(
                text = dateText,
                style = MaterialTheme.typography.labelMedium,
                color = colors.muted,
            )
        }
        Surface(
            onClick = onMood,
            shape = RoundedCornerShape(Radius.circular),
            color = if (mood.isBlank()) {
                colors.chip
            } else {
                colors.primarySoft
            },
        ) {
            Text(
                text = mood.ifBlank { "心情" },
                modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
                style = MaterialTheme.typography.labelMedium,
                color = if (mood.isBlank()) {
                    colors.muted
                } else {
                    colors.primary
                },
            )
        }
    }
}

@Composable
private fun MediaPickerButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DiaryEditorTagRow(
    tagsText: String,
    showAddEntry: Boolean,
    colors: DiaryEditorColors,
    onAddTag: () -> Unit,
) {
    val tags = tagsText.split(",").map { it.trim() }.filter { it.isNotBlank() && it != "null" }
    if (tags.isNotEmpty() || showAddEntry) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            if (showAddEntry) item { DiaryEditorAddTagChip(colors, onAddTag) }
            items(tags, key = { it }) { tag -> DiaryEditorTagChip("#$tag", colors, onAddTag) }
        }
    }
}

@Composable
private fun DiaryEditorAddTagChip(colors: DiaryEditorColors, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(30.dp),
        shape = RoundedCornerShape(Radius.circular),
        color = colors.chip,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = colors.primary,
            )
            Text(
                text = "标签",
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary,
            )
        }
    }
}

@Composable
private fun DiaryEditorTagChip(text: String, colors: DiaryEditorColors, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(30.dp),
        shape = RoundedCornerShape(Radius.circular),
        color = colors.chip,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = Spacing.s),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = colors.muted,
            )
        }
    }
}

@Composable
private fun DiaryMoreFormatMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onQuote: () -> Unit,
    onTaskList: () -> Unit,
    onDivider: () -> Unit,
    onLink: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("加粗") }, onClick = onBold)
        DropdownMenuItem(text = { Text("斜体") }, onClick = onItalic)
        DropdownMenuItem(text = { Text("引用") }, onClick = onQuote)
        DropdownMenuItem(text = { Text("任务") }, onClick = onTaskList)
        DropdownMenuItem(text = { Text("分割线") }, onClick = onDivider)
        DropdownMenuItem(text = { Text("链接") }, onClick = onLink)
    }
}

@Composable
private fun DiaryTextEditDialog(
    title: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.xl),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = value, onValueChange = onValueChange, placeholder = { Text(placeholder) }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("确定") } },
    )
}

private fun diaryEditorDateText(existingDiary: Diary?): String {
    val time = existingDiary?.created_at ?: System.currentTimeMillis()
    return SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(time))
}
