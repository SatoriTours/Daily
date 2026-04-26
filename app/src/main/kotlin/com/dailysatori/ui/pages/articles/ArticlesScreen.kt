package com.dailysatori.ui.pages.articles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.components.*
import com.dailysatori.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticlesScreen(
    onArticleClick: (Long) -> Unit = {},
) {
    var isSearchVisible by remember { mutableStateOf(false) }
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            SAppBar(
                title = "Articles",
                onBack = null,
                showBack = false,
                actions = {
                    IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { /* calendar */ }) {
                        Icon(Icons.Outlined.CalendarToday, contentDescription = "Calendar")
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreHoriz, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Filter by Tags") },
                                leadingIcon = { Icon(Icons.Outlined.Tag, contentDescription = null) },
                                onClick = { showMenu = false },
                            )
                            DropdownMenuItem(
                                text = { Text(if (showFavoritesOnly) "Show All" else "Favorites Only") },
                                leadingIcon = {
                                    Icon(
                                        if (showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (showFavoritesOnly) MaterialTheme.colorScheme.error else LocalContentColor.current,
                                    )
                                },
                                onClick = { showFavoritesOnly = !showFavoritesOnly; showMenu = false },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isSearchVisible) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.xs),
                    placeholder = { Text("Search articles...") },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.s),
                )
            }
            if (showFavoritesOnly) {
                FilterIndicator(text = "Favorites only", onClear = { showFavoritesOnly = false })
            }
            EmptyState(
                icon = Icons.Default.Article,
                title = "No articles",
                subtitle = "Share a URL to save your first article",
            )
        }
    }
}
