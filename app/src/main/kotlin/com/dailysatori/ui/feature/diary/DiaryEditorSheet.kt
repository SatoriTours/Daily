package com.dailysatori.ui.feature.diary

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.content.FileProvider
import com.dailysatori.shared.db.Diary
import com.dailysatori.ui.theme.Height
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private fun sanitizeNull(value: String?): String {
    if (value == null || value == "null") return ""
    return value
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryEditorSheet(
    onDismiss: () -> Unit,
    onSave: (content: String, tags: String?, mood: String?, images: String?) -> Unit,
    existingDiary: Diary? = null,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { false },
    )
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var content by remember(existingDiary) {
        mutableStateOf(TextFieldValue(existingDiary?.content ?: ""))
    }
    var tagInput by remember { mutableStateOf("") }
    var showTagInput by remember { mutableStateOf(false) }
    var showMediaPicker by remember { mutableStateOf(false) }
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
    val tempVideoUri = remember {
        val dir = File(context.filesDir, "DailySatori/diary_images").apply { mkdirs() }
        val file = File(dir, "temp_video_${UUID.randomUUID()}.mp4")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success -> if (success) saveMedia(tempPhotoUri, ".jpg") }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo(),
    ) { success -> if (success) saveMedia(tempVideoUri, ".mp4") }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris: List<Uri> -> uris.forEach { uri -> saveMedia(uri, "") } }

    fun appendTag() {
        val tag = tagInput.trim()
        if (tag.isBlank()) return
        pushUndo()
        val text = content.text
        val needNewline = text.isNotBlank() && !text.endsWith("\n\n") && !text.endsWith("\n")
        val prefix = if (needNewline) "\n\n" else if (text.isNotBlank() && text.endsWith("\n")) "\n" else ""
        val tagText = "$prefix#$tag"
        content = content.copy(
            text = text + tagText,
            selection = TextRange(text.length + tagText.length),
        )
        tagInput = ""
        showTagInput = false
    }

    if (showMediaPicker) {
        AlertDialog(
            onDismissRequest = { showMediaPicker = false },
            title = { Text("添加媒体") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    MediaPickerButton("拍照") {
                        showMediaPicker = false; cameraLauncher.launch(tempPhotoUri)
                    }
                    MediaPickerButton("录像") {
                        showMediaPicker = false; videoLauncher.launch(tempVideoUri)
                    }
                    MediaPickerButton("从相册选择") {
                        showMediaPicker = false; galleryLauncher.launch("*/*")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMediaPicker = false }) { Text("取消") }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = {
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                if (!sheetState.isVisible) onDismiss()
            }
        },
        sheetState = sheetState,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.s))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
            ) {
                BasicTextField(
                    value = content,
                    onValueChange = { pushUndo(); content = it },
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        if (content.text.isEmpty()) {
                            Text(
                                text = "写点东西...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                        innerTextField()
                    },
                )
            }

            DiaryImageRow(images = images, onRemove = { images.remove(it) })

            if (showTagInput) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        modifier = Modifier.weight(1f).height(Height.input * 0.7f),
                        placeholder = { Text("输入标签") },
                        singleLine = true,
                        shape = RoundedCornerShape(Radius.s),
                    )
                    Text(
                        text = "添加",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (tagInput.isNotBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { appendTag() }.padding(horizontal = Spacing.xs),
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            DiaryEditorToolbar(
                onTitle = { insertLineStart("# ") },
                onBold = { insertFormat("**", "**") },
                onOrderedList = { insertLineStart("1. ") },
                onUnorderedList = { insertLineStart("- ") },
                onUndo = { performUndo() },
                onRedo = { performRedo() },
                onMedia = { showMediaPicker = true },
                onTag = { showTagInput = !showTagInput },
                onSave = {
                    onSave(content.text, null, null, images.joinToString(",").ifBlank { null })
                },
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                canSave = content.text.isNotBlank(),
            )

            Spacer(modifier = Modifier.height(Spacing.s))
        }
    }
}

@Composable
private fun MediaPickerButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
    }
}

