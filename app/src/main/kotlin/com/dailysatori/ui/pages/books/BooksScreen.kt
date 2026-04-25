package com.dailysatori.ui.pages.books

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.components.CustomCard
import com.dailysatori.ui.components.EmptyState
import com.dailysatori.ui.components.SAppBar
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(
    onSearchClick: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            SAppBar(
                title = "Books & Wisdom",
                onBack = null,
                showBack = false,
                actions = {
                    IconButton(onClick = { /* book filter */ }) {
                        Icon(Icons.Default.Menu, contentDescription = "Filter")
                    }
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Add, contentDescription = "Add Book")
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreHoriz, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Shuffle") },
                                onClick = { showMenu = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Refresh") },
                                onClick = { showMenu = false },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* quick diary */ },
                modifier = Modifier.padding(end = Spacing.m, bottom = Spacing.m),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = androidx.compose.ui.graphics.Color.White,
            ) {
                Icon(Icons.Default.EditNote, contentDescription = "Quick Journal")
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            EmptyState(
                icon = Icons.Default.AutoStories,
                title = "No viewpoints",
                subtitle = "Add a book to get started",
            )
        }
    }
}
