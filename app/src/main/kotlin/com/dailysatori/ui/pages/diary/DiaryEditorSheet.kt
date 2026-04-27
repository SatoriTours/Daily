package com.dailysatori.ui.pages.diary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailysatori.shared.db.Diary
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

@Composable
fun DiaryEditorSheet(
    onDismiss: () -> Unit,
    onSave: (content: String, tags: String?, mood: String?) -> Unit,
    existingDiary: Diary? = null,
) {
    var content by remember(existingDiary) { mutableStateOf(existingDiary?.content ?: "") }
    var tags by remember(existingDiary) { mutableStateOf(existingDiary?.tags ?: "") }
    var mood by remember(existingDiary) { mutableStateOf(existingDiary?.mood ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingDiary != null) "编辑日记" else "写日记") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    placeholder = { Text("写点东西...") },
                    shape = RoundedCornerShape(Radius.s),
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("标签（逗号分隔）") },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.s),
                )
                OutlinedTextField(
                    value = mood,
                    onValueChange = { mood = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("心情") },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.s),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(content, tags.ifBlank { null }, mood.ifBlank { null }) },
                enabled = content.isNotBlank(),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
