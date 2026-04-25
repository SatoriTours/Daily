package com.dailysatori.ui.pages.books

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.dailysatori.ui.components.SAppBar
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookSearchScreen(
    onBack: () -> Unit = {},
) {
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            SAppBar(title = "Search Books", onBack = onBack)
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(Spacing.m),
                placeholder = { Text("Enter book title...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(Radius.s),
            )
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Search results will appear here", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
