package com.dailysatori.ui.pages.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.dailysatori.ui.pages.PlaceholderScreen

data class TabItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val tabs = listOf(
    TabItem("Articles", Icons.Filled.Article, Icons.Outlined.Article),
    TabItem("Diary", Icons.Filled.Book, Icons.Outlined.Book),
    TabItem("Books", Icons.Filled.AutoStories, Icons.Outlined.AutoStories),
    TabItem("AI Chat", Icons.Filled.SmartToy, Icons.Outlined.SmartToy),
    TabItem("Summary", Icons.Filled.Person, Icons.Outlined.Person),
)

@Composable
fun HomeScreen() {
    val selectedIndex = rememberSaveable { mutableStateOf(0) }
    val initializedTabs = rememberSaveable { mutableSetOf(0) }
    initializedTabs.add(selectedIndex.value)

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                if (selectedIndex.value == index) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                        selected = selectedIndex.value == index,
                        onClick = { selectedIndex.value = index },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopStart,
        ) {
            when (selectedIndex.value) {
                0 -> PlaceholderScreen("Articles")
                1 -> PlaceholderScreen("Diary")
                2 -> PlaceholderScreen("Books")
                3 -> PlaceholderScreen("AI Chat")
                4 -> PlaceholderScreen("Weekly Summary")
            }
        }
    }
}
