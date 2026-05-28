package com.dailysatori.ui.feature.diary

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.dailysatori.shared.db.Diary
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private fun sanitizeNull(value: String?): String {
    if (value == null || value == "null") return ""
    return value
}

@Composable
fun DiaryEditorSheet(
    onDismiss: () -> Unit,
    onSave: (content: String, tags: String?, mood: String?, images: String?) -> Unit,
    existingDiary: Diary? = null,
) {
    val context = LocalContext.current

    var content by remember(existingDiary) {
        mutableStateOf(TextFieldValue(existingDiary?.content ?: ""))
    }
    var showMediaPicker by remember { mutableStateOf(false) }
    var showTagEditor by remember { mutableStateOf(false) }
    var showMoodEditor by remember { mutableStateOf(false) }
    var showMoreFormats by remember { mutableStateOf(false) }
    var tagsText by remember(existingDiary) { mutableStateOf(sanitizeNull(existingDiary?.tags)) }
    var moodText by remember(existingDiary) { mutableStateOf(sanitizeNull(existingDiary?.mood)) }
    val undoStack = remember { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember { mutableStateListOf<TextFieldValue>() }
    val images = remember(existingDiary) {
        val existingImages = existingDiary?.images
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() && it != "null" }
            ?: emptyList()
        mutableStateListOf<String>().apply { addAll(existingImages) }
    }

    fun pushUndo() {
        if (undoStack.size >= 50) undoStack.removeAt(0)
        undoStack.add(content)
        redoStack.clear()
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .padding(horizontal = Spacing.s),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .padding(bottom = Spacing.s),
                shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l),
                color = MaterialTheme.colorScheme.surface,
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
                        TextButton(onClick = onDismiss) { Text("取消") }
                        Text(text = if (existingDiary != null) "编辑日记" else "新建日记", style = MaterialTheme.typography.titleSmall)
                        TextButton(
                            enabled = content.text.isNotBlank(),
                            onClick = { onSave(content.text, tagsText.ifBlank { null }, moodText.ifBlank { null }, images.joinToString(",").ifBlank { null }) },
                        ) { Text("保存") }
                    }
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = diaryEditorMetaText(existingDiary, moodText),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(Radius.xl))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = Spacing.m, vertical = Spacing.s),
                    ) {
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            DiaryImageRow(images = images, onRemove = { images.remove(it) })
                            BasicTextField(
                                value = content,
                                onValueChange = { pushUndo(); content = it },
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    if (content.text.isEmpty()) {
                                        Text("写点东西...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    }
                                    innerTextField()
                                },
                            )
                            DiaryEditorTagRow(tagsText = tagsText, onAddTag = { showTagEditor = true })
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Box {
                        DiaryEditorToolbar(
                            onTitle = { insertLineStart("# ") },
                            onOrderedList = { insertLineStart("1. ") },
                            onUnorderedList = { insertLineStart("- ") },
                            onMedia = { showMediaPicker = true },
                            onTags = { showTagEditor = true },
                            onMood = { showMoodEditor = true },
                            onUndo = { performUndo() },
                            onRedo = { performRedo() },
                            onMore = { showMoreFormats = !showMoreFormats },
                            canUndo = undoStack.isNotEmpty(),
                            canRedo = redoStack.isNotEmpty(),
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
private fun MediaPickerButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DiaryEditorTagRow(tagsText: String, onAddTag: () -> Unit) {
    val tags = tagsText.split(",").map { it.trim() }.filter { it.isNotBlank() && it != "null" }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        item { DiaryEditorChip("+ 标签", onAddTag) }
        items(tags, key = { it }) { tag -> DiaryEditorChip("#$tag", onAddTag) }
    }
}

@Composable
private fun DiaryEditorChip(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(Radius.circular), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(text = text, modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun diaryEditorMetaText(existingDiary: Diary?, mood: String): String {
    val time = existingDiary?.created_at ?: System.currentTimeMillis()
    val dateText = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(time))
    return mood.takeIf { it.isNotBlank() }?.let { "$dateText · $it" } ?: dateText
}
