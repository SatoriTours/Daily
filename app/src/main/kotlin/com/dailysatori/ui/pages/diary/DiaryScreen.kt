package com.dailysatori.ui.pages.diary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.components.EmptyState
import com.dailysatori.ui.components.SAppBar
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen() {
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SAppBar(
                title = "我的日记",
                onBack = null,
                showBack = false,
                actions = {
                    IconButton(onClick = { showSearchBar = !showSearchBar }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { /* tags filter */ }) {
                        Icon(Icons.Outlined.Tag, contentDescription = "Tags")
                    }
                    IconButton(onClick = { /* calendar */ }) {
                        Icon(Icons.Outlined.CalendarToday, contentDescription = "Calendar")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showEditor = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
            ) {
                Icon(Icons.Default.Edit, contentDescription = "New Diary")
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            EmptyState(
                icon = Icons.Outlined.CalendarToday,
                title = "No diaries",
                subtitle = "Tap the + button to write your first diary",
            )
        }
    }

    if (showEditor) {
        DiaryEditorSheet(
            onDismiss = { showEditor = false },
            onSave = { content, tags, mood ->
                showEditor = false
            },
        )
    }
}
