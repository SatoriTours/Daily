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
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

@Composable
fun DiaryEditorSheet(
    onDismiss: () -> Unit,
    onSave: (content: String, tags: String?, mood: String?) -> Unit,
    initialContent: String = "",
    initialTags: String? = null,
    initialMood: String? = null,
) {
    var content by remember { mutableStateOf(initialContent) }
    var tags by remember { mutableStateOf(initialTags ?: "") }
    var mood by remember { mutableStateOf(initialMood ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Write Diary") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    placeholder = { Text("Write your thoughts...") },
                    shape = RoundedCornerShape(Radius.s),
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Tags (comma separated)") },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.s),
                )
                OutlinedTextField(
                    value = mood,
                    onValueChange = { mood = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Mood") },
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
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
